package com.pocket.xiaoli.desktop.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.data.Todo
import com.pocket.xiaoli.desktop.data.dayOfWeekLabel
import com.pocket.xiaoli.desktop.logic.Knowledge
import com.pocket.xiaoli.desktop.logic.courseEndMinute
import com.pocket.xiaoli.desktop.logic.courseOccursInWeek
import com.pocket.xiaoli.desktop.logic.courseStartMinute
import com.pocket.xiaoli.desktop.logic.coursesOnDay
import com.pocket.xiaoli.desktop.logic.findUpcomingCourse
import com.pocket.xiaoli.desktop.logic.minuteText
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * 鲤鱼能用的工具（OpenAI 函数调用格式）。
 *
 * ## 为什么工具是「必须调用」而不是「可以调用」
 *
 * 校园问答的失败模式不是"答得不好"，而是**答得像真的但事实是错的**（编一个不存在的
 * 办公电话）。把「知识库 / 课表 / 校历 / 待办」都做成工具，模型每一次事实性陈述都能
 * 回溯到一次工具调用 —— 提示词里明说：没调用就等于没依据。
 */
object PocketTools {

    /** 系统提示词：与移动端 `AssistantLlmConfig.systemPrompt` 同一套纪律。 */
    const val SYSTEM_PROMPT: String =
        "你是「鲤鱼」，烟台理工学院的校园助手，运行在用户的桌面客户端里。\n" +
            "规则：\n" +
            "1. 凡涉及学校规定、办事流程、部门联系方式、校历时间、住宿、缴费、图书馆等事实性问题，" +
            "必须先调用工具（knowledge_search / knowledge_catalog）再回答；工具没查到就明说「知识库里没收录」，" +
            "不要凭记忆编造学校信息。\n" +
            "2. 引用知识库时要标注编号，例如 [K1]、[K2]，编号来自工具返回的片段。\n" +
            "3. 涉及课表、待办、校历的问题，分别用 schedule_* / todos_* / calendar_events 工具拿真实数据，不要猜。\n" +
            "4. 回答用中文，先给结论再给要点，要点不超过 6 条；能落地的动作给具体步骤。\n" +
            "5. 涉及时效（政策、金额、截止时间）的答案，提醒用户以学校官网最新通知为准。\n" +
            "6. 不要自称 AI 模型，你是校园助手鲤鱼。"

    private fun schema(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()): JsonObject {
        val properties = JsonObject()
        props.forEach { (name, spec) -> properties.add(name, spec) }
        return JsonObject().apply {
            addProperty("type", "object")
            add("properties", properties)
            add("required", JsonArray().apply { required.forEach { add(it) } })
        }
    }

    private fun stringProp(description: String): JsonObject = JsonObject().apply {
        addProperty("type", "string")
        addProperty("description", description)
    }

    private fun intProp(description: String): JsonObject = JsonObject().apply {
        addProperty("type", "integer")
        addProperty("description", description)
    }

    private fun str(args: JsonObject, key: String): String =
        args.get(key)?.takeIf { !it.isJsonNull }?.asString.orEmpty()

    fun build(): List<ToolSpec> = listOf(

        ToolSpec(
            name = "knowledge_search",
            description = "检索烟台理工学院校园知识库（教务、住宿、缴费、图书馆、办事流程、联系方式等）。每个事实性回答前都应该用。",
            parameters = schema("query" to stringProp("检索词，例如「VPN 申请」「宿舍报修」「补考」"), required = listOf("query")),
            label = "查校园知识库",
        ) { args ->
            Knowledge.formatForModel(Knowledge.search(str(args, "query"), topK = 5))
        },

        ToolSpec(
            name = "knowledge_catalog",
            description = "列出知识库收录的分类与文档标题，用来判断某个问题该不该查、查什么词。",
            parameters = schema(),
            label = "看知识库目录",
        ) {
            Knowledge.catalog()
        },

        ToolSpec(
            name = "schedule_day",
            description = "查某一天的课表。不传日期时查今天。",
            parameters = schema("date" to stringProp("日期 yyyy-MM-dd；不传=今天")),
            label = "查课表",
        ) { args ->
            val raw = str(args, "date")
            val date = if (raw.isBlank()) LocalDate.now() else runCatching { LocalDate.parse(raw) }.getOrDefault(LocalDate.now())
            val data = Store.data
            val week = Store.currentWeek(date)
            val courses = coursesOnDay(data.activeCourses, data.slots, date, week)
            val head = "$date（${dayOfWeekLabel(date.dayOfWeek.value)}，${if (week <= 0) "未开学" else "第 $week 周"}）"
            if (courses.isEmpty()) {
                "$head 没有课。"
            } else {
                buildString {
                    append(head).append(" 共 ").append(courses.size).append(" 门课：\n")
                    courses.forEach { course ->
                        append("- ").append(course.name)
                        if (course.room.isNotBlank()) append(" @ ").append(course.room)
                        append("（").append(minuteText(courseStartMinute(course, data.slots)))
                        append("-").append(minuteText(courseEndMinute(course, data.slots))).append("）")
                        if (course.teacher.isNotBlank()) append(" 老师：").append(course.teacher)
                        append("\n")
                    }
                    data.activeTerm?.let {
                        append("学期：").append(it.displayName).append("，总 ").append(it.totalWeeks).append(" 周")
                    }
                }
            }
        },

        ToolSpec(
            name = "schedule_next",
            description = "查「下一节课」：今天还没上完的课，或之后第一节课。",
            parameters = schema(),
            label = "查下一节课",
        ) {
            val data = Store.data
            val week = Store.currentWeek()
            val upcoming = findUpcomingCourse(data.activeCourses, data.slots, week, LocalDate.now(), LocalTime.now())
            if (upcoming == null) {
                "接下来 7 天没有课（或不在教学周内）。"
            } else {
                val startMinute = upcoming.startAt.hour * 60 + upcoming.startAt.minute
                "${upcoming.dayLabel} ${minuteText(startMinute)}-${upcoming.endTime}：${upcoming.course.name}" +
                    (if (upcoming.course.room.isNotBlank()) " @ ${upcoming.course.room}" else "") +
                    (if (upcoming.course.teacher.isNotBlank()) "（${upcoming.course.teacher}）" else "")
            }
        },

        ToolSpec(
            name = "schedule_week",
            description = "查整周课表概览（周一到周日，每门课的时间与地点）。",
            parameters = schema(),
            label = "查整周课表",
        ) {
            val data = Store.data
            val week = Store.currentWeek()
            val term = data.activeTerm
            when {
                term == null -> "还没有设置学期。"
                week <= 0 -> "还没开学（学期 ${term.startDate} 开始）。"
                else -> buildString {
                    append("第 ").append(week).append(" 周（共 ").append(term.totalWeeks).append(" 周）\n")
                    (1..7).forEach { day ->
                        val courses = data.activeCourses
                            .filter { it.dayOfWeek == day && courseOccursInWeek(it, week, term.totalWeeks) }
                            .sortedBy { courseStartMinute(it, data.slots) }
                        if (courses.isNotEmpty()) {
                            append(dayOfWeekLabel(day)).append("：")
                            append(courses.joinToString("；") { course ->
                                minuteText(courseStartMinute(course, data.slots)) + " " + course.name +
                                    (if (course.room.isNotBlank()) "@${course.room}" else "")
                            })
                            append("\n")
                        }
                    }
                }
            }
        },

        ToolSpec(
            name = "todos_list",
            description = "列出用户当前的待办事项。",
            parameters = schema(),
            label = "看待办",
        ) {
            val todos = Store.data.todos
            if (todos.isEmpty()) {
                "待办列表是空的。"
            } else {
                todos.joinToString("\n") { todo ->
                    val state = if (todo.done) "✅" else "⬜"
                    val due = if (todo.due.isNotBlank()) "（截止 ${todo.due}）" else ""
                    val priority = when (todo.priority) {
                        2 -> "【紧急】"
                        1 -> "【重要】"
                        else -> ""
                    }
                    "$state $priority${todo.title}$due" +
                        (if (todo.courseName.isNotBlank()) " #${todo.courseName}" else "")
                }
            }
        },

        ToolSpec(
            name = "todo_add",
            description = "帮用户新增一条待办。只有用户明确要求新增时才用。",
            parameters = schema(
                "title" to stringProp("待办标题"),
                "due" to stringProp("截止日期 yyyy-MM-dd，可空"),
                "priority" to intProp("0 普通 / 1 重要 / 2 紧急"),
                required = listOf("title"),
            ),
            label = "新增待办",
        ) { args ->
            val title = str(args, "title").trim()
            if (title.isEmpty()) {
                "标题为空，没有新增。"
            } else {
                val due = str(args, "due")
                val priority = args.get("priority")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                val todo = Todo(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    due = due,
                    priority = priority.coerceIn(0, 2),
                    createdAt = System.currentTimeMillis(),
                )
                Store.update { it.copy(todos = listOf(todo) + it.todos) }
                "已新增待办：$title" + (if (due.isNotBlank()) "（截止 $due）" else "")
            }
        },

        ToolSpec(
            name = "calendar_events",
            description = "查校历：开学、放假、考试周、假期等事件。可传月份过滤。",
            parameters = schema("month" to stringProp("月份 yyyy-MM，可空")),
            label = "查校历",
        ) { args ->
            val calendar = Assets.calendar
            if (calendar == null) {
                "没有校历数据。"
            } else {
                val month = str(args, "month")
                val events = calendar.events.filter { month.isBlank() || it.start.startsWith(month) }
                if (events.isEmpty()) {
                    "该时间段没有校历事件。"
                } else {
                    buildString {
                        append(calendar.academicYear).append(" 学年校历\n")
                        events.forEach { event ->
                            append("- ").append(event.start)
                            if (event.end != event.start) append(" ~ ").append(event.end)
                            append(" ").append(event.title).append("（").append(event.kind).append("）\n")
                        }
                    }
                }
            }
        },
    )
}
