package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.CalendarEvent
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.Todo
import java.time.LocalDate

/** 全局搜索的命中类型。顺序即同分时的展示顺序。 */
enum class SearchKind { COURSE, TODO, KNOWLEDGE, CALENDAR, PAGE }

/** 可搜索的页面。别名用来让「设置」「校历」这类词也能命中。 */
data class SearchPage(
    val index: Int,
    val title: String,
    val hint: String,
    val keywords: List<String> = emptyList(),
)

/**
 * 一条命中。模型对象直接挂在结果上，由 UI 决定「打开哪个编辑器 / 切到哪一段」，
 * 搜索层不碰导航状态。
 */
data class SearchHit(
    val kind: SearchKind,
    val title: String,
    val subtitle: String,
    val score: Int,
    /** 页面命中：目标 tab */
    val page: Int? = null,
    /** 服务页命中：0 服务入口 / 1 成绩 / 2 考试 / 3 校历 / 4 知识库 / 5 快问快答 */
    val campusSection: Int? = null,
    val course: Course? = null,
    val todo: Todo? = null,
    val knowledgeId: String? = null,
    val knowledgeQuery: String? = null,
    /** 校历命中：跳到事件开始的那一天 */
    val calendarStartEpochDay: Long? = null,
)

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 全局搜索：课程 / 待办 / 知识库 / 校历 / 页面。
 *
 * 排序规则刻意保持简单可解释：**标题完全等于 > 前缀 > 包含 > 只在副字段命中**，
 * 同分按类型（课程 > 待办 > 知识库 > 校历 > 页面）再按标题。知识库内部的排序
 * 直接沿用共享检索算法的名次，不在这里二次发明权重。
 *
 * [knowledgeSearch] 可注入，单测不需要加载 assets。
 */
fun searchAll(
    query: String,
    pages: List<SearchPage> = emptyList(),
    courses: List<Course> = emptyList(),
    todos: List<Todo> = emptyList(),
    events: List<CalendarEvent> = emptyList(),
    knowledgeSearch: (String, Int) -> List<Knowledge.Hit> = { _, _ -> emptyList() },
    limit: Int = 24,
): List<SearchHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<SearchHit>()

    courses.forEach { course ->
        val primary = maxOf(
            textScore(q, course.name, 100, 80, 60),
            textScore(q, course.shortName, 96, 76, 56),
        )
        val secondary = maxOf(
            textScore(q, course.teacher, 48, 44, 40),
            textScore(q, course.room, 46, 42, 38),
            textScore(q, course.note, 44, 40, 36),
        )
        val score = maxOf(primary, secondary)
        if (score > 0) {
            val weekday = WEEKDAYS.getOrElse(course.dayOfWeek - 1) { "周?" }
            val slots = if (course.startSlot == course.endSlot) "第${course.startSlot}节" else "第${course.startSlot}-${course.endSlot}节"
            hits += SearchHit(
                kind = SearchKind.COURSE,
                title = course.name,
                subtitle = listOf("课程", "$weekday $slots", course.room).filter { it.isNotBlank() }.joinToString(" · "),
                score = score,
                course = course,
            )
        }
    }

    todos.forEach { todo ->
        val score = maxOf(
            textScore(q, todo.title, 100, 80, 60),
            textScore(q, todo.note, 44, 40, 36),
            textScore(q, todo.courseName, 42, 38, 34),
        )
        if (score > 0) {
            hits += SearchHit(
                kind = SearchKind.TODO,
                title = todo.title,
                subtitle = listOf(
                    "待办",
                    if (todo.done) "已完成" else "未完成",
                    todo.due.takeIf { it.isNotBlank() }?.let { "截止 $it" }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                score = score,
                todo = todo,
            )
        }
    }

    // 知识库检索按 chunk 返回，同一文档可能命中多段：按 doc id 去重，只留最高名次
    val seenKnowledge = mutableSetOf<String>()
    var knowledgeRank = 0
    knowledgeSearch(q, 12).forEach { hit ->
        if (!seenKnowledge.add(hit.doc.id)) return@forEach
        val titleScore = textScore(q, hit.doc.title, 88, 70, 0)
        hits += SearchHit(
            kind = SearchKind.KNOWLEDGE,
            title = hit.doc.title,
            subtitle = listOf("知识库", hit.doc.category).filter { it.isNotBlank() }.joinToString(" · "),
            // 标题精确/前缀命中给高分；其余按检索名次压到用户数据（课程/待办）同分段之下。
            // 末尾的名次加成保证同分时仍保留共享检索给出的顺序。
            score = maxOf(titleScore, (58 - knowledgeRank * 3).coerceAtLeast(30)) +
                (6 - knowledgeRank).coerceAtLeast(0),
            knowledgeId = hit.doc.id,
            knowledgeQuery = q,
        )
        knowledgeRank++
    }

    events.forEach { event ->
        val score = maxOf(
            textScore(q, event.title, 92, 74, 58),
            textScore(q, event.short, 90, 72, 56),
            textScore(q, event.kind, 50, 46, 42),
        )
        if (score > 0) {
            hits += SearchHit(
                kind = SearchKind.CALENDAR,
                title = event.title,
                subtitle = listOf("校历", "${event.start} → ${event.end}", event.kind).joinToString(" · "),
                score = score,
                campusSection = 3,
                calendarStartEpochDay = runCatching { LocalDate.parse(event.start).toEpochDay() }.getOrNull(),
            )
        }
    }

    pages.forEach { page ->
        val primary = textScore(q, page.title, 90, 76, 62)
        val alias = page.keywords.maxOfOrNull { textScore(q, it, 88, 72, 58) } ?: 0
        val score = maxOf(primary, alias)
        if (score > 0) {
            hits += SearchHit(
                kind = SearchKind.PAGE,
                title = page.title,
                subtitle = "页面 · ${page.hint}",
                score = score,
                page = page.index,
            )
        }
    }

    return hits
        .sortedWith(
            compareByDescending<SearchHit> { it.score }
                .thenBy { it.kind.ordinal }
                .thenBy { it.title },
        )
        .take(limit)
}

private fun textScore(query: String, text: String, exact: Int, prefix: Int, contains: Int): Int {
    if (text.isBlank()) return 0
    val value = text.trim()
    return when {
        value.equals(query, ignoreCase = true) -> exact
        value.startsWith(query, ignoreCase = true) -> prefix
        value.contains(query, ignoreCase = true) -> contains
        else -> 0
    }
}
