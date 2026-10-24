package com.pocket.xiaoli.data.agent

import com.pocket.xiaoli.data.knowledge.AssistantCard
import com.pocket.xiaoli.data.knowledge.AssistantSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Agent 一轮对话对外暴露的事件。UI 按顺序消费即可。 */
sealed interface AgentEvent {
    /** 正文增量，追加显示。 */
    data class TextDelta(val text: String) : AgentEvent

    /**
     * 思考链增量。
     *
     * 与 [TextDelta] 严格分开：思考链**不是回答**，界面上放在可以默认收起的折叠栏里，
     * 也绝不落库（它没有长期价值，而且会白占上下文预算）。
     * 服务商不回 `reasoning_content` 时整条链路收不到任何该事件 ——
     * 界面的思考栏自然就不会出现，不需要额外判断。
     */
    data class ReasoningDelta(val text: String) : AgentEvent

    /** 模型决定调用工具，展示"正在查…"用。 */
    data class ToolStarted(val name: String, val displayName: String) : AgentEvent

    /**
     * 工具返回，[preview] 是结果首行摘要，用于界面上给个反馈。
     *
     * [failed] 表示这次执行**真的失败了**（未知工具 / 执行体抛异常），
     * 而不是"查到了但没数据"。必须区分：查不到是正常业务结果（今天没课），
     * 执行失败是异常，界面上两者的配色与图标应当不同。
     * 文案判定放在这里而不是界面层 —— 界面不该认识工具返回的文本格式。
     */
    data class ToolFinished(
        val name: String,
        val displayName: String,
        val preview: String,
        val failed: Boolean = false,
        /**
         * 该工具产出的**结构化卡片**（只给界面用，不落库、不进上下文）。
         *
         * 放在事件上而不是让界面回头解析 [preview]：preview 是给模型读的散文，
         * 从它反解数字等于两套口径。详见 `AssistantCard`。
         */
        val card: AssistantCard? = null,
        /**
         * 该工具真实返回的**出处**（目前只有校园知识库有）。
         *
         * 与 [card] 同一纪律：只给界面、不落库、不进上下文。界面据此渲染来源条，
         * **不解析模型正文** —— 否则模型编一个 `[K9]` 就会被配一条假出处。
         */
        val sources: List<AssistantSource> = emptyList(),
    ) : AgentEvent

    /** 本轮结束的最终回答（纯文本，已含模型对工具结果的解读）。 */
    data class Answer(val text: String) : AgentEvent

    /**
     * 出错，[message] 可直接展示。
     *
     * [detail] 是同一次失败的**完整原文**（含 HTTP 状态、服务商返回体、异常类名）。
     * 为什么两段都要：`message` 是给人看的短句，`detail` 是给"我到底遇到什么了"看的原文。
     * 岱宗盒子把原始异常直接甩在气泡里、还不限行数，长报错会把整屏吃掉；
     * 这里分开放，界面默认只显示短句，原文收进「详情」。
     */
    data class Error(val message: String, val detail: String = "") : AgentEvent
}

/**
 * Agent 主循环：**请求 → 流式接收 → 判断是否要调工具 → 执行 → 结果回灌 → 再请求**。
 *
 * 终止条件（四者任一）：
 * 1. 模型不再请求工具 → 正常结束，产出 [AgentEvent.Answer]
 * 2. 达到 [MAX_TOOL_ROUNDS] → **摘掉工具再请求一次**（熔断降级），让模型用已查到的结果收尾；
 *    那次也失败才退回 [FALLBACK_ANSWER]
 * 3. 回答被长度截断 → 追加「继续」再请求，最多 [MAX_CONTINUATIONS] 次，增量拼回同一条回答
 * 4. 出错 / 被取消 → 产出 [AgentEvent.Error] 或直接结束
 *
 * 这一层**不碰网络细节**（[AssistantStreamSource] 负责）、**不碰业务取数**（[AssistantToolRegistry] 负责），
 * 只做编排与三条硬不变量：
 *
 * - **不变量 A**：assistant 消息带 N 个 tool_calls，后面就必须紧跟 N 条 role=tool 的回应，
 *   每条 `tool_call_id` 严格对应。少一条或顺序错，OpenAI 兼容服务端会直接 400。
 *   所以工具失败也必须产出回应文本，不能跳过；熔断后模型仍请求的工具一律**不执行也不落库**。
 * - **不变量 B**：裁剪上下文时，绝不能把 assistant(tool_calls) 与它的 tool 回应拆散。
 *   因此按"轮"裁剪，不按单条消息裁剪。
 * - **不变量 C**：续写与熔断的「提示语」只进**请求体**，不进 [turn] ——
 *   它们是机器给模型的指令，落库后下一轮会被当成用户原话再发一遍。
 */
class AssistantAgentLoop(
    private val streamSource: AssistantStreamSource,
    private val registry: AssistantToolRegistry,
) {

    /**
     * 执行一轮完整对话。
     *
     * [history] 是此前的消息（不含本轮用户输入）；本方法会把用户输入与本轮产生的
     * 全部消息（assistant / tool）追加进去，通过 [AgentEvent.Answer] 之外的返回值交给调用方落库。
     *
     * 返回完整的本轮新增消息列表，便于持久化。
     */
    fun run(
        userInput: String,
        history: List<ChatMessage>,
        onMessagesChanged: (List<ChatMessage>) -> Unit = {},
        /**
         * **只发给模型、不进聊天记录**的本地快照。
         *
         * 主动气泡点进来时会带上"用户点的是哪件事"（哪场考试、余额是从哪个缓存读的）。
         * 它必须留在请求体里、不能进 [turn]：进了就会出现在用户自己的气泡里，
         * 而且会跟着历史**永远**发下去（下一轮模型看到的就不再是用户的原话）。
         */
        llmAnnex: String? = null,
    ): Flow<AgentEvent> = flow {
        // 本轮新增的消息（不含历史），结束后交给调用方落库
        val turn = mutableListOf<ChatMessage>()
        turn += ChatMessage(ChatRole.USER, content = userInput)

        var rounds = 0
        var lastAnswerText = ""
        var budgetExhausted = false

        while (true) {
            // 工具预算用尽 → 熔断降级：不再提供工具，让模型用已经查到的结果作答。
            // 直接返回兜底文案等于"查了四轮然后告诉用户我停了"，是最差的一种收尾。
            if (rounds >= MAX_TOOL_ROUNDS) budgetExhausted = true

            // 主动气泡带进来的本地快照只挂在第一条用户消息上（见 withAnnex）
            val baseTurn = withAnnex(turn, llmAnnex)
            val requestTurn = if (budgetExhausted) {
                baseTurn + ChatMessage(ChatRole.USER, content = TOOL_BUDGET_NOTE)
            } else {
                baseTurn
            }
            val messages = AssistantContextBuilder.build(history, requestTurn)
                // 内部标记只服务于界面渲染错误卡片，绝不能进请求体（模型会把它当正文模仿）
                .map { it.withoutErrorMark() }
            val request = LlmRequest(
                messages = messages,
                toolsJson = if (budgetExhausted) null else registry.toolDefinitionsJson,
            )

            var completed: ChatMessage? = null
            var failure: String? = null
            var truncated = false

            streamSource.stream(request).collect { event ->
                when (event) {
                    is LlmStreamEvent.TextDelta -> {
                        emit(AgentEvent.TextDelta(event.text))
                    }
                    is LlmStreamEvent.ReasoningDelta -> emit(AgentEvent.ReasoningDelta(event.text))
                    is LlmStreamEvent.Completed -> {
                        completed = event.message
                        truncated = event.truncated
                    }
                    is LlmStreamEvent.Failed -> failure = event.message
                }
            }

            val failureText = failure
            if (failureText != null) {
                if (budgetExhausted) {
                    // 熔断后的最后一问也失败：屏幕上已经有前面查到的内容，
                    // 这里不再弹错误卡片打断，直接用已有文本收尾。
                    emit(AgentEvent.Answer(lastAnswerText.ifBlank { FALLBACK_ANSWER }))
                    return@flow
                }
                emit(AgentEvent.Error(message = shortReasonFor(failureText), detail = failureText))
                return@flow
            }

            var message = completed ?: run {
                emit(AgentEvent.Error("模型没有返回内容，请重试。"))
                return@flow
            }

            // 长度续写：把「被截断」当成"再说一次继续"的信号，而不是让用户自己发现回答少了半截。
            // 续写请求也摘掉工具 —— 模型此刻该做的是接着写，而不是再查一轮。
            // 带 tool_calls 的 assistant 消息不能拿来续写：它必须先配齐 tool 回应（不变量 A），
            // 否则续写请求本身就是非法的。
            var continuations = 0
            while (
                truncated &&
                continuations < MAX_CONTINUATIONS &&
                message.toolCalls.isEmpty() &&
                message.content.isNotBlank()
            ) {
                continuations++
                val contMessages = AssistantContextBuilder.build(
                    history,
                    withAnnex(turn, llmAnnex) + message +
                        ChatMessage(ChatRole.USER, content = CONTINUE_NOTE),
                ).map { it.withoutErrorMark() }
                var cont: ChatMessage? = null
                var contFailed = false
                streamSource.stream(LlmRequest(messages = contMessages, toolsJson = null)).collect { event ->
                    when (event) {
                        is LlmStreamEvent.TextDelta -> emit(AgentEvent.TextDelta(event.text))
                        is LlmStreamEvent.ReasoningDelta -> emit(AgentEvent.ReasoningDelta(event.text))
                        is LlmStreamEvent.Completed -> {
                            cont = event.message
                            truncated = event.truncated
                        }
                        is LlmStreamEvent.Failed -> contFailed = true
                    }
                }
                val addition = cont?.content.orEmpty()
                if (contFailed || addition.isBlank()) break
                message = message.copy(content = message.content + addition)
            }

            // 熔断后模型仍可能越权请求工具：一律不执行、不落库。
            // 落库会留下「带 tool_calls 却没有 tool 回应」的行，下一轮请求直接 400（不变量 A）。
            val calls = if (budgetExhausted) emptyList() else message.toolCalls
            if (budgetExhausted) message = message.copy(toolCalls = emptyList())

            // 记录 assistant 消息（即使 content 为空、只有 tool_calls，也要落）
            turn += message
            onMessagesChanged(history + turn)
            lastAnswerText = message.content.ifBlank { lastAnswerText }

            if (calls.isEmpty()) {
                // 正常终止：模型直接回答（或熔断轮里它按要求收尾）
                val answer = message.content.ifBlank {
                    lastAnswerText.ifBlank { "（模型没有给出内容）" }
                }
                emit(AgentEvent.Answer(answer))
                break
            }

            // 逐个执行工具，并按不变量 A 为**每一个** call 产出 role=tool 回应
            rounds++
            calls.forEach { call ->
                val display = displayNameOf(call.name)
                emit(AgentEvent.ToolStarted(call.name, display))
                // registry.execute 内部已把异常转成文本，这里拿到的永远是可回灌的字符串
                val result = registry.execute(call.name, call.argumentsJson)
                emit(
                    AgentEvent.ToolFinished(
                        name = call.name,
                        displayName = display,
                        preview = previewOf(result.text),
                        failed = isToolFailure(result.text),
                        card = result.card,
                        sources = result.sources,
                    )
                )
                turn += ChatMessage(
                    role = ChatRole.TOOL,
                    // ⚠️ 回灌给模型的**只有文本**。卡片是界面的事，进了上下文既浪费 token，
                    // 也会让模型以为用户能看见它 —— 那会诱导它去引用卡片里没有的东西。
                    content = result.text,
                    toolCallId = call.id,
                    toolName = call.name,
                )
            }
            onMessagesChanged(history + turn)
        }
    }

    private fun displayNameOf(toolName: String): String = when (toolName) {
        "get_today_courses" -> "今日课程"
        "get_next_course" -> "下一节课"
        "get_pending_todos" -> "待办作业"
        "get_grades" -> "成绩"
        "get_exams" -> "考试安排"
        "get_weather" -> "天气"
        "get_academic_progress" -> "修课进度"
        "find_classroom" -> "教室位置"
        "find_campus_service" -> "校园服务"
        "search_knowledge" -> "校园知识库"
        else -> toolName
    }

    /** 取结果首行做界面反馈，超长截断。 */
    private fun previewOf(result: String): String {
        val firstLine = result.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
        return if (firstLine.length > 40) firstLine.take(40) + "…" else firstLine
    }

    /**
     * 判断工具结果是不是「执行失败」。
     *
     * 只看 [AssistantToolRegistry.execute] 那两条**唯一**的失败产出（未知工具 / 执行体抛异常）。
     * 刻意不按「没找到 / 还没有 / 没有课」这类业务空结果判定 ——
     * 那是正常回答（今天没课不是错误），误标红色会让用户以为查询坏了。
     */
    private fun isToolFailure(result: String): Boolean =
        TOOL_FAILURE_MARKERS.any { result.contains(it) }

    /**
     * 从完整报错里取一句可读的短文案，作为错误卡片的正文。
     *
     * [AssistantLlmClient] 已经把 HTTP 状态与网络异常翻译成中文了，这里只做
     * **取第一行 + 限长**：卡片上不该出现多行原文（那正是岱宗盒子的问题）。
     * 取不到就退回原文首行，保证永远不会给用户一张空卡片。
     */
    private fun shortReasonFor(detail: String): String {
        val first = detail.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (first.isEmpty()) return "模型请求失败，请重试。"
        return if (first.length > 80) first.take(80) + "…" else first
    }

    /**
     * 给**请求体**里的第一条用户消息附上本地快照（见 [run] 的 `llmAnnex`）。
     *
     * 只改 index 0 —— `turn` 的第一条恒为本轮用户输入（见 `run` 的第一行），
     * 后面的 assistant / tool 消息一条都不能动：它们的顺序与配对是不变量 A 约束的。
     * 快照本身保持"附注"形态（前缀一行标记），模型能看出这是机器给的上下文而非用户原话。
     */
    private fun withAnnex(turn: List<ChatMessage>, annex: String?): List<ChatMessage> {
        val snapshot = annex?.takeIf { it.isNotBlank() } ?: return turn
        if (turn.isEmpty() || turn[0].role != ChatRole.USER) return turn
        return turn.mapIndexed { index, message ->
            if (index == 0) {
                message.copy(content = message.content + "\n\n[本地快照]\n" + snapshot)
            } else {
                message
            }
        }
    }

    companion object {
        /** 最多几轮工具调用。4 轮足够覆盖"查课表→再查教室"这类链式需求，再多基本是模型绕圈。 */
        const val MAX_TOOL_ROUNDS = 4

        /** 被长度截断后最多续写几次。两次足够把一条正常回答写完，再多就是服务端额度有问题。 */
        const val MAX_CONTINUATIONS = 2

        /** 回答被截断时追加给模型的续写指令。只进请求体，不落库（不变量 C）。 */
        const val CONTINUE_NOTE =
            "（系统提示）上一条回答因长度限制被截断，请接着最后一句继续写完，不要重复已经说过的内容。"

        /** 工具预算用尽时追加给模型的收尾指令。只进请求体，不落库（不变量 C）。 */
        const val TOOL_BUDGET_NOTE =
            "（系统提示）工具调用次数已达上限。请直接根据上面已经查到的结果回答用户，" +
                "不要再请求调用工具；信息确实不足时，如实说明缺什么。"

        /** 熔断后的最后一问也失败时的兜底文案。 */
        const val FALLBACK_ANSWER =
            "这个问题需要查的东西有点多，我先停一下。可以问得更具体一点，比如「今天有什么课」。"

        /**
         * [AssistantToolRegistry.execute] 的失败文案特征。
         *
         * ⚠️ 这两条字符串与 `AssistantToolRegistry` 里的产出**必须保持一致** ——
         * 改那边的文案就要同步改这里，否则界面上失败会被显示成成功（静默失效）。
         * 之所以不复用常量：那两处是拼在句子里的字面量，抽常量会让文案可读性变差。
         */
        private val TOOL_FAILURE_MARKERS = listOf("执行失败：", "错误：不存在名为")
    }
}
