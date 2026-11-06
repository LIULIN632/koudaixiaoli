package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.desktop.data.AppData
import java.time.LocalDate
import java.time.LocalDateTime

/** 一条待弹的上课提醒。 */
data class ClassReminder(
    val courseId: String,
    val courseName: String,
    val room: String,
    val startMinuteOfDay: Int,
    val minutesUntilStart: Int,
    val remindDate: String,
) {
    /** 去重键：同一门课的同一天同一节只提醒一次（重启应用会再提醒一次，可接受）。 */
    val key: String get() = "$courseId|$remindDate|$startMinuteOfDay"
}

/**
 * 当前时刻该不该提醒上课。
 *
 * 纯函数、时钟由调用方注入，方便单测：
 * - 只看**今天**的课（不跨午夜）；
 * - 提前窗口是 `1..leadMinutes`，已经开课（0 或负数）不提醒；
 * - 同一时刻多门课取最近的一门。
 */
fun pendingClassReminder(
    data: AppData,
    now: LocalDateTime,
    leadMinutes: Int,
): ClassReminder? {
    if (leadMinutes <= 0) return null
    val term = data.activeTerm ?: return null
    val today = now.toLocalDate()
    val week = termWeekOf(term, today)
    if (week <= 0) return null
    val nowMinute = now.hour * 60 + now.minute
    val dateText = today.toString()

    return coursesOnDay(data.activeCourses, data.slots, today, week)
        .mapNotNull { course ->
            val start = courseStartMinute(course, data.slots)
            val until = start - nowMinute
            if (until in 1..leadMinutes) {
                ClassReminder(
                    courseId = course.id,
                    courseName = course.name,
                    room = course.room,
                    startMinuteOfDay = start,
                    minutesUntilStart = until,
                    remindDate = dateText,
                )
            } else {
                null
            }
        }
        .minByOrNull { it.minutesUntilStart }
}

/** 一条待办到期提醒（同一天的多条待办合并成一条）。 */
data class TodoReminder(
    val key: String,
    val titles: List<String>,
) {
    val count: Int get() = titles.size

    val message: String get() =
        if (count == 1) "今天截止：${titles.first()}" else "今天有 $count 项待办截止"
}

/**
 * 今天到期、还没完成的待办。
 *
 * 待办只有日期没有时刻，所以统一在**当天 [hourThreshold] 点之后**提醒一次：
 * 应用当时没开也没关系，之后启动时 ticker 会补上；同一天只提醒一次。
 * 已逾期不在这里提醒（首页本来就标红，避免每天刷屏）。
 */
fun pendingTodoReminder(
    data: AppData,
    now: LocalDateTime,
    hourThreshold: Int = 9,
): TodoReminder? {
    if (now.hour < hourThreshold) return null
    val today = now.toLocalDate()
    val dueTodos = data.todos
        .filter { !it.done && it.title.isNotBlank() && it.due.isNotBlank() }
        .filter { runCatching { LocalDate.parse(it.due.trim()) }.getOrNull() == today }
    if (dueTodos.isEmpty()) return null
    val titles = dueTodos
        .sortedWith(compareByDescending<com.pocket.xiaoli.desktop.data.Todo> { it.priority }.thenBy { it.title })
        .map { it.title }
    val ids = dueTodos.map { it.id }.sorted()
    return TodoReminder(key = "todo|$today|${ids.joinToString(",")}", titles = titles)
}

/** 一条考试提醒（同一场考试同一天只提醒一次）。 */
data class ExamReminder(
    val examId: String,
    val courseName: String,
    val date: String,
    val daysLeft: Long,
    val time: String,
    val location: String,
) {
    /**
     * 去重键：提前告知一次、考试当天再一次。
     *
     * 键里只分「ahead / day」两档，所以窗口内不会每天念一遍；
     * 重启应用会再来一次，与上课提醒同一口径（提醒状态存在内存，不落盘）。
     */
    val key: String get() = "exam|$examId|${if (daysLeft == 0L) "day" else "ahead"}|$date"

    val message: String get() = buildString {
        append(courseName)
        append(' ')
        append(ExamCountdown.countdownText(daysLeft))
        append("开考")
        val where = listOf(time, location).filter { it.isNotBlank() }.joinToString(" ")
        if (where.isNotBlank()) append(" · ").append(where)
    }
}

/**
 * 未来 [leadDays] 天内（含今天）没考完的考试，取最近的一场。
 *
 * 阈值与窗口都来自共享核心 `ExamCountdown`，与手机端首页卡片、主动气泡同一个数 ——
 * 两端不会出现"手机说还有 3 天、桌面一声不吭"。已过去的不在这里提醒（考试页自己会标）。
 */
fun pendingExamReminder(
    data: AppData,
    now: LocalDateTime,
    leadDays: Int = ExamCountdown.SOON_DAYS,
): ExamReminder? {
    if (leadDays <= 0) return null
    val today = now.toLocalDate()
    return data.exams
        .filter { !it.completed && it.courseName.isNotBlank() }
        .mapNotNull { exam ->
            val days = ExamCountdown.daysLeft(exam.date, today) ?: return@mapNotNull null
            if (days < 0 || days > leadDays) return@mapNotNull null
            ExamReminder(
                examId = exam.id,
                courseName = exam.courseName,
                date = exam.date,
                daysLeft = days,
                time = exam.schedule.time,
                location = exam.schedule.location,
            )
        }
        .minByOrNull { it.daysLeft }
}
