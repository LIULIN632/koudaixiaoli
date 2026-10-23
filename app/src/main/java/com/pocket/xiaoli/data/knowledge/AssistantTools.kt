package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.data.academic.AcademicSnapshotCodec
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.service.CampusServiceRegistry
import com.pocket.xiaoli.data.weather.WeatherRepository
import com.pocket.xiaoli.data.weather.weatherReport
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.util.isCourseInWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** 只读工具执行器：直接读 Repository，不写任何数据。 */
object AssistantTools {

    /** 知识库一次返回的命中条数上限。 */
    private const val KNOWLEDGE_TOP_K = 5

    suspend fun run(container: AppContainer, tool: AssistantTool, query: String): ToolOutcome = when (tool) {
        // 本地成绩存档：命中课程名给该课成绩，否则给整体统计
        AssistantTool.MY_GRADES -> AssistantFormatters.grades(container.savedGradeDao.getAll().first(), query)

        // 培养方案进度：读「修课情况」留下的本地快照（**不联网** —— 教务查询要登录+验证码）
        AssistantTool.ACADEMIC_PROGRESS -> AssistantFormatters.academicProgress(
            AcademicSnapshotCodec.decode(container.academicSnapshotDao.get()),
            query,
        )

        // 教室位置：用房间号反查楼栋/楼层
        AssistantTool.CAMPUS_LOCATION -> text(AssistantFormatters.rooms(container.roomCells.value, query))
        AssistantTool.CAMPUS_SERVICE -> text(AssistantFormatters.services(CampusServiceRegistry.catalogFor(container.selectedCampus.value), query))

        // 校园知识库：⚠️ 过滤必须在**每次检索时**现算"今天"。
        // 若改成加载时过滤一次，Android 进程跨天后仍会把过期通知当现行安排答出来。
        AssistantTool.SEARCH_KNOWLEDGE -> {
            val raw = container.knowledgeStore.ensureLoaded()
            val usable = ExpiryPolicy.apply(raw, LocalDate.now())
            text(
                AssistantFormatters.knowledge(
                    hits = KeywordRetriever.search(query, usable, topK = KNOWLEDGE_TOP_K),
                    totalDocuments = usable.size,
                    query = query,
                )
            )
        }

        // 考试安排：与首页待办同一份数据（本地 exams 表）
        AssistantTool.EXAMS -> text(exams(container, query))

        // 天气：用户主动问，允许联网。30 分钟内的缓存直接命中（首页通常已经拉过一次），
        // 因此这里的"联网"不会因为用户连问几句就变成连发请求。
        AssistantTool.WEATHER -> when (val result = container.weatherRepository.load()) {
            is WeatherRepository.Result.Success -> text(weatherReport(result.snapshot))
            is WeatherRepository.Result.Failure -> text(weatherFailure(result))
        }

        AssistantTool.TODAY_COURSES,
        AssistantTool.NEXT_COURSE,
        AssistantTool.TODOS -> {
            val schemeId = container.activeSchemeId.value
            val courses = container.repository.getCoursesByScheme(schemeId).first()
            val slots = container.repository.allTimeSlots.first()
            val week = container.autoCurrentWeek()
            text(when (tool) {
                AssistantTool.TODAY_COURSES -> todayCourses(courses, slots, week, container.roomCells.value)
                AssistantTool.NEXT_COURSE -> nextCourse(courses, slots, week, container.roomCells.value)
                else -> pendingTodos(container, schemeId)
            })
        }
    }

    /**
     * 只有文本、没有卡片的工具结果。
     *
     * ⚠️ 目前**只有成绩**产卡片（见 `AssistantCard`）。其余六个工具的排版价值主要在列表本身，
     * 而列表已经在文本里了 —— 硬给它们套卡片只是把同一段字装进一个框。
     * 将来要给「今日课 / 待办 / 考试」也做卡与表，改这里 + `AssistantCard` 即可，
     * 通路（`ToolOutcome` → `AgentEvent` → `AssistantUiMessage`）不用再动。
     */
    private fun text(value: String) = ToolOutcome(text = value)

    /**
     * 天气失败的两种说法。
     *
     * 有缓存时**带上缓存**并明确标注"可能已过时"：用户问"要带伞吗"，
     * 40 分钟前的天气也比"查不到"有用。但必须让模型知道它不是实时的，
     * 否则它会把旧数据说成"现在正在下雨"。
     *
     * 完全没数据时把"不要编造"写进结果 —— 模型最容易在这里凭常识补一个温度。
     */
    private fun weatherFailure(result: WeatherRepository.Result.Failure): String {
        val cached = result.cached
            ?: return "暂时拿不到天气：${result.message}。不要编造天气数据，请如实告诉用户稍后再试。"
        return "获取最新天气失败（${result.message}），以下是上次的缓存，可能已经过时：\n" +
            weatherReport(cached)
    }

    private fun todayCourses(
        courses: List<CourseEntity>,
        slots: List<TimeSlotEntity>,
        week: Int,
        roomCells: List<com.pocket.xiaoli.data.map.RoomCell>,
    ): String {
        val today = LocalDate.now().dayOfWeek.value
        val slotMap = slots.associateBy { it.slot }
        val list = courses
            .filter { it.dayOfWeek == today && isCourseInWeek(it, week) }
            .sortedBy { it.startSlot }
            .map {
                val start = slotMap[it.startSlot]?.startTime.orEmpty()
                val end = slotMap[it.endSlot]?.endTime.orEmpty()
                "· ${it.startSlot}-${it.endSlot}节 ${it.name}" +
                    (if (start.isNotBlank() || end.isNotBlank()) "（$start-$end）" else "") +
                    (if (it.room.isNotBlank()) " @${it.room}" else "") +
                        (AssistantFormatters.roomBrief(roomCells, it.room)?.let { loc -> "（$loc）" } ?: "")
            }
        return if (list.isEmpty()) "今天没有课。"
        else "今天的课：" + "\n" + list.joinToString("\n")
    }

    private fun nextCourse(
        courses: List<CourseEntity>,
        slots: List<TimeSlotEntity>,
        week: Int,
        roomCells: List<com.pocket.xiaoli.data.map.RoomCell>,
    ): String {
        val today = LocalDate.now().dayOfWeek.value
        val slotMap = slots.associateBy { it.slot }
        val active = courses.filter { isCourseInWeek(it, week) }
        for (offset in 0..7) {
            val day = ((today - 1 + offset) % 7) + 1
            val first = active.filter { it.dayOfWeek == day }.minByOrNull { it.startSlot }
            if (first != null) {
                val start = slotMap[first.startSlot]?.startTime.orEmpty()
                val end = slotMap[first.endSlot]?.endTime.orEmpty()
                val whenText = when (offset) {
                    0 -> "今天"
                    1 -> "明天"
                    else -> "${offset} 天后"
                }
                return "下一节课：$whenText ${first.name}" +
                    (if (start.isNotBlank() || end.isNotBlank()) "（$start-$end）" else "") +
                    (if (first.room.isNotBlank()) " @${first.room}" else "") +
                        (AssistantFormatters.roomBrief(roomCells, first.room)?.let { loc -> "（$loc）" } ?: "")
            }
        }
        return "接下来 7 天没有找到课程。"
    }

    /**
     * 考试查询。
     *
     * 与 [pendingTodos] 一致：默认列未完成的；传了课程名就只答那一门
     * （用户问「高数什么时候考」时不该把整张考试表都倒出来）。
     *
     * 数据来自本地 `exams` 表（用户粘贴通知、确认后落库），
     * 所以为空时要提示**去哪录入**，而不是说"没有考试"——
     * 那会让用户以为系统查过了。
     */
    private suspend fun exams(container: AppContainer, query: String): String {
        val schemeId = container.activeSchemeId.value
        val all = container.repository.getExamsByScheme(schemeId).first()
        if (all.isEmpty()) {
            return "本地还没有考试安排。考试信息需要从教务通知录入：" +
                "「服务 → 考试安排」→ 粘贴通知内容 → 确认识别结果。"
        }

        val pending = all.filterNot { it.completed }
        val q = query.trim()
        val matched = if (q.isBlank()) pending else {
            pending.filter { it.courseName.contains(q, ignoreCase = true) }
        }
        if (matched.isEmpty()) {
            return if (q.isBlank()) "考试都已标记完成。"
            else "没有找到与「$q」匹配的考试。当前未完成的考试有：" +
                pending.joinToString("、") { it.courseName }
        }

        val lines = matched
            .sortedBy { it.date.ifBlank { "9999-99-99" } }
            .map { e ->
                buildString {
                    append("· ").append(e.courseName)
                    if (e.date.isNotBlank()) append("  ").append(e.date)
                    if (e.time.isNotBlank()) append(" ").append(e.time)
                    if (e.location.isNotBlank()) append("  ").append(e.location)
                    if (e.seat.isNotBlank()) append("（座位 ").append(e.seat).append("）")
                }
            }
        val head = if (q.isBlank()) "未完成的考试（${matched.size} 场）：" else "「$q」的考试："
        return head + "\n" + lines.joinToString("\n")
    }

    private suspend fun pendingTodos(container: AppContainer, schemeId: Long): String {
        val tasks = container.repository.getTasksByScheme(schemeId).first().filterNot { it.completed }
        val homework = container.repository.getAllHomework().first()
            .filter { (it.schemeId == schemeId || it.schemeId == 0L) && !it.completed }
        val lines = buildList {
            homework.sortedBy { it.dueDate.ifBlank { "9999-99-99" } }.forEach {
                add("· 作业：${it.courseName} ${it.content}".trim() +
                    (if (it.dueDate.isNotBlank()) "（${it.dueDate} 截止）" else ""))
            }
            tasks.sortedBy { it.sortOrder }.forEach { add("· 待办：${it.name}") }
        }
        return if (lines.isEmpty()) "当前没有未完成的作业或待办。"
        else "未完成事项（${lines.size} 项）：" + "\n" + lines.joinToString("\n")
    }
}
