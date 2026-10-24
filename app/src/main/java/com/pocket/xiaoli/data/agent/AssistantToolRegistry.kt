package com.pocket.xiaoli.data.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.data.knowledge.AssistantTool
import com.pocket.xiaoli.data.knowledge.AssistantTools
import com.pocket.xiaoli.data.knowledge.ToolOutcome
import com.pocket.xiaoli.di.AppContainer
import kotlinx.coroutines.CancellationException

/**
 * 一个可被 LLM 调用的工具。
 *
 * [tool] 与既有的 [AssistantTool] 枚举一一对应 —— 枚举即稳定 id，
 * 这样新增工具时只需在枚举里加一项、在这里补一条 spec，两边不会漂移。
 */
data class AssistantToolSpec(
    val tool: AssistantTool,
    /** 下发给模型的 function.name：snake_case，英文，模型对英文函数名更稳。 */
    val name: String,
    /** 给模型看的说明：写清「返回什么、参数怎么填、什么情况下用」。 */
    val description: String,
    /** 参数 JSON Schema。手写而非反射生成：可控、无运行期开销、缺省语义能写进说明。 */
    val parameters: JsonObject,
    /**
     * 执行体。入参是模型给的参数对象；返回值含**给模型读的文本**与**只给界面用的卡片**。
     *
     * ⚠️ 返回 [ToolOutcome] 而不是 `String` 是 2026-10 的改造：原先界面只能拿到一句散文，
     * 于是"把绩点/均分/挂科数做成三格 stat"这种需求在界面上**做不到**（数字在字符串里）。
     * 见 [ToolOutcome] 的说明 —— 文本与卡片必须同源。
     */
    val handler: suspend (JsonObject) -> ToolOutcome,
)

/**
 * 工具注册表：schema 出口 + 执行入口。
 *
 * 设计要点（都是从岱宗盒子那套踩坑结论提炼后独立实现的）：
 * - **schema 一次性序列化**后用 `by lazy` 缓存，每轮请求复用，不重复拼 JSON
 * - **任何工具执行失败都不抛穿**，一律降级为文本回灌给模型 ——
 *   模型看到错误文本能自己纠正（换个参数重试或如实告知用户），
 *   而抛异常会直接打断对话循环，用户只看到一个失败
 * - **[CancellationException] 必须原样抛出**，否则协程取消会被吞掉，
 *   用户点了"停止"界面却还在转
 */
class AssistantToolRegistry(private val specs: List<AssistantToolSpec>) {

    /** 下发给模型的 tools 数组。惰性缓存，整个会话只拼一次。 */
    val toolDefinitionsJson: String by lazy {
        val arr = JsonArray()
        specs.forEach { spec ->
            arr.add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", spec.name)
                    addProperty("description", spec.description)
                    add("parameters", spec.parameters)
                })
            })
        }
        arr.toString()
    }

    val toolNames: List<String> get() = specs.map { it.name }

    /**
     * 执行一次工具调用。
     *
     * 三种失败都转成给模型读的文本（卡片为 null）：未知工具名、参数不是合法 JSON、执行体抛异常。
     * 失败路径**不带卡片** —— 界面不该给一个"查询失败"画一张数据卡。
     *
     * 回灌结果统一截到 [MAX_TOOL_RESULT_CHARS]：知识检索/服务目录这类工具会把整段
     * 原文吐出来，一次就把 12k 的上下文预算吃掉大半，后面模型没地方说话，
     * 表现成"回答突然变短/开始复读"。截断按行边界切，不留半句话。
     */
    suspend fun execute(name: String, argumentsJson: String): ToolOutcome {
        val spec = specs.firstOrNull { it.name == name }
            ?: return ToolOutcome("错误：不存在名为 $name 的工具。可用工具：${toolNames.joinToString("、")}。")

        val args = runCatching { JsonParser.parseString(argumentsJson).asJsonObject }
            .getOrNull()
            ?: JsonObject() // 模型给了非法 JSON：当空参数处理，让工具自己去判断缺什么

        val outcome = try {
            spec.handler(args)
        } catch (e: CancellationException) {
            throw e // 取消必须穿透
        } catch (e: Exception) {
            ToolOutcome("工具 $name 执行失败：${e.message ?: e.javaClass.simpleName}。可以换个方式再问，或直接告诉用户查询不可用。")
        }
        return outcome.copy(text = truncateResult(outcome.text))
    }

    /** 按行边界截断；至少保留一半内容，避免只剩一行标题。 */
    private fun truncateResult(text: String): String {
        if (text.length <= MAX_TOOL_RESULT_CHARS) return text
        val head = text.take(MAX_TOOL_RESULT_CHARS)
        val cut = head.lastIndexOf('\n').takeIf { it >= MAX_TOOL_RESULT_CHARS / 2 } ?: head.length
        return head.substring(0, cut).trimEnd() + "\n…[结果过长已截断]"
    }

    companion object {
        /** 单条工具结果回灌给模型的上限（字符）。4000 约等于 2~3k token，留得下两三条结果。 */
        const val MAX_TOOL_RESULT_CHARS = 4_000
    }
}

/**
 * 把既有的 [AssistantTools.run] 包装成 LLM 工具。
 *
 * **这是本次改造的核心取巧点**：既有 7 个工具都是
 * `run(container, tool, query): String` 形态，靠 query 字符串做内部匹配
 * （比如成绩工具用课名匹配、教室工具用房间号匹配）。
 *
 * 所以适配层只需把结构化参数**还原成 query 文本**，业务实现一行都不用改：
 * - 零改动接入，风险最低
 * - 将来某个工具需要真正的结构化参数（如"哪个教室"独立成参），
 *   直接在 spec 里写真参数 + 单独 handler，不必再迁就 query 字符串
 *
 * 这些工具全部只读（见 [AssistantTools] 的注释：不写任何数据），
 * 因此不需要任何确认流程，模型可自由调用。
 */
object AssistantToolBindings {

    fun build(container: AppContainer): AssistantToolRegistry =
        AssistantToolRegistry(listOf(
            legacySpec(
                container = container,
                tool = AssistantTool.TODAY_COURSES,
                description = "查询今天（当前星期几、当前教学周）的全部课程，含节次、起止时间、教室与楼栋位置。" +
                    "用户问「今天有什么课」「今天几节课」「今天要不要上课」时调用。无参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.NEXT_COURSE,
                description = "查询从今天起的下一节课（会往后找最多 7 天），含是今天/明天/几天后、节次、时间、教室。" +
                    "用户问「下节课是什么」「下一节课在哪」「接下来有课吗」时调用。无参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.TODOS,
                description = "查询当前未完成的作业与待办事项，含课程名、内容、截止日期。" +
                    "用户问「有什么作业」「还有哪些待办」「作业截止到什么时候」时调用。无参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.MY_GRADES,
                description = "查询本地已存档的成绩。传入课名会返回该课成绩，不传或传空则返回整体统计（门数、平均分、平均绩点、不及格门数）。" +
                    "用户问「高数多少分」「我绩点多少」「有没有挂科」时调用。" +
                    "注意：数据来自用户本地存档，若为空需提示用户先去「服务 → 成绩查询」存档。",
                paramName = "course",
                paramDescription = "课程名或简称，例如「高等数学」「高数」。想查整体统计就不要传这个参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.EXAMS,
                description = "查询已录入的考试安排（日期、时间、考场、座位号）。传入课程名只答那一门，不传则列出全部未完成的考试。" +
                    "用户问「我什么时候考试」「考试安排」「高数在哪考」「还有几门没考」时调用。" +
                    "数据来自用户从教务通知录入的本地记录；若为空，需提示用户去「服务 → 考试安排」粘贴通知录入。",
                paramName = "course",
                paramDescription = "课程名或简称，例如「高等数学」「高数」。想查全部考试就不要传这个参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.WEATHER,
                description = "查询烟台当前天气、未来 12 小时与今明两天的预报：现象、温度、体感、降水概率、风、紫外线。" +
                    "用户问「今天天气怎么样」「现在多少度」「要带伞吗」「明天冷不冷」「明天穿什么」时调用。无参数。" +
                    "数据来自 Open-Meteo 的**烟台市**坐标，不是校区实测 —— 回答时不要把它说成教学楼或校园里的实测数据。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.ACADEMIC_PROGRESS,
                description = "查询培养方案（毕业学分）进度：总要求/已获/还差多少学分、哪些课程类型还缺学分、" +
                    "哪些课还没修、某门课修了没（含成绩与建议修读学年学期）。" +
                    "用户问「我还差多少学分」「哪些课没修」「学分修够了吗」「形势与政策修了没」「离毕业还差什么」时调用。" +
                    "数据来自用户上次在「服务 → 修课情况」查询时留下的本地快照（不会联网）；" +
                    "若回答里说没有数据，需提示用户去那一页查一次。",
                paramName = "course",
                paramDescription = "课程名或简称，例如「形势与政策」「高数」。只想看总体进度/缺的课就不要传这个参数。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.CAMPUS_LOCATION,
                description = "用房间号反查教室位置，返回楼栋、楼层、教室类型与容量。" +
                    "用户问「A101 在哪」「3104 怎么走」「XX 教室在几楼」时调用。",
                paramName = "room",
                paramDescription = "教室/房间号，例如「A101」「3104」。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.CAMPUS_SERVICE,
                description = "在校园服务目录里查找服务入口（校园卡、电费、洗衣、热水、图书馆、空教室、选课、评教等），" +
                    "返回服务名、服务商与打开渠道。" +
                    "用户问「电费在哪交」「校园卡怎么绑定」「洗衣用什么 App」「图书馆入口」时调用。",
                paramName = "service",
                paramDescription = "服务关键词，例如「电费」「校园卡」「洗衣」「图书馆」。",
            ),
            legacySpec(
                container = container,
                tool = AssistantTool.SEARCH_KNOWLEDGE,
                description = "查询校园知识库：学校规章制度（补考重修、学分绩点、请假规定）、" +
                    "场馆开放时间（图书馆、食堂、体育馆）、办事流程（奖学金申请、医保报销、转专业）、" +
                    "以及近期通知（放假安排、临时闭馆）。" +
                    "用户问「图书馆几点开门」「补考怎么报名」「放假怎么安排」「奖学金要什么条件」" +
                    "这类**学校规定/校园事实**问题时调用。传入完整明确的检索词。" +
                    "返回内容带 [K1] 这样的编号与更新时间，回答时请据此说明来源；" +
                    "若返回「知识库暂无内容」或「没有查到」，如实告知并建议官方入口，**不要编造**。",
                paramName = "query",
                paramDescription = "检索词，要完整明确，例如「图书馆开放时间」「补考报名流程」「国庆放假安排」。",
            ),
        ))

    /**
     * 为既有工具生成 spec。
     *
     * [paramName] 为空表示该工具无参数（课表类工具由服务端直接判断"今天/下一节"）。
     */
    private fun legacySpec(
        container: AppContainer,
        tool: AssistantTool,
        description: String,
        paramName: String? = null,
        paramDescription: String? = null,
    ): AssistantToolSpec = AssistantToolSpec(
        tool = tool,
        name = tool.functionName,
        description = description,
        parameters = if (paramName == null) {
            emptyParameters()
        } else {
            stringParameters(paramName, paramDescription.orEmpty())
        },
        handler = { args ->
            // 结构化参数 → query 文本，交回既有实现。参数缺失时传空串，
            // 既有实现会走"整体统计 / 未命中文案"分支，行为可预期。
            val query = paramName?.let { args.stringOrNull(it).orEmpty() }.orEmpty()
            AssistantTools.run(container, tool, query)
        },
    )

    private fun emptyParameters(): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject())
        // 刻意不写 required：全部可选，让模型能渐进式查询
    }

    private fun stringParameters(name: String, description: String): JsonObject = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add(name, JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", description)
            })
        })
    }
}
