package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.course.UpcomingCandidate
import com.pocket.xiaoli.core.course.pickUpcomingCourse
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.Term
import com.pocket.xiaoli.desktop.data.TimeSlot
import com.pocket.xiaoli.desktop.data.parseClock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 第几周（1 起）。开学前返回 0，教学周之后返回 > totalWeeks 的数值（假期）。 */
fun termWeekOf(term: Term, date: LocalDate): Int {
    val start = runCatching { LocalDate.parse(term.startDate) }.getOrNull() ?: return 0
    // 口径在共享核心 WeekCalendarCore：不足 7 天算第 1 周，开学前没有周次（这里显示成 0）
    return com.pocket.xiaoli.core.calendar.WeekCalendarCore.weekOf(start.toEpochDay(), date.toEpochDay()) ?: 0
}

/** 某周第一天的日期。 */
fun weekStartDate(term: Term, week: Int): LocalDate {
    val start = runCatching { LocalDate.parse(term.startDate) }.getOrDefault(LocalDate.now())
    return start.plusWeeks((week - 1).toLong())
}

/** 解析 "1-16,18" 这类周次表达式；非法片段跳过。 */
fun parseWeeks(spec: String): Set<Int> = com.pocket.xiaoli.core.parseWeekSpec(spec)

/** 课程在整学期里出现的周次集合（已按单双周过滤）。 */
fun courseWeeks(course: Course, totalWeeks: Int = 20): Set<Int> {
    return com.pocket.xiaoli.core.weekSet(course.weekType, course.weeks, totalWeeks)
}

fun courseOccursInWeek(course: Course, week: Int, totalWeeks: Int = 20): Boolean {
    if (week <= 0) return false
    return week in courseWeeks(course, totalWeeks)
}

/** 周次的人类可读描述，例如「1-16 周」「单周」「3-12 周（单）」。 */
fun weeksLabel(course: Course, totalWeeks: Int): String {
    val parity = when (course.weekType) {
        "odd" -> "（单）"
        "even" -> "（双）"
        else -> ""
    }
    val weeks = parseWeeks(course.weeks)
    if (weeks.isEmpty()) return if (parity.isEmpty()) "全学期" else "全学期$parity"
    // 教务给的是「1,2,3,…,16」这种逐周列表，原样显示就是一串数字噪音；覆盖整学期直接说全学期。
    if (totalWeeks > 0 && weeks.size >= totalWeeks) return "全学期$parity"
    return "${compressWeekRuns(weeks)} 周$parity"
}

/** 连续周次并成区间：{1,2,3,7,9,10} → 「1-3、7、9-10」。 */
internal fun compressWeekRuns(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return ""
    return buildString {
        var runStart = sorted.first()
        var prev = runStart
        sorted.drop(1).forEach { week ->
            if (week == prev + 1) {
                prev = week
            } else {
                appendWeekRun(runStart, prev)
                append('、')
                runStart = week
                prev = week
            }
        }
        appendWeekRun(runStart, prev)
    }
}

private fun StringBuilder.appendWeekRun(start: Int, end: Int) {
    if (start == end) append(start) else append("$start-$end")
}

fun slotStartMinute(slotIndex: Int, slots: List<TimeSlot>): Int {
    val slot = slots.firstOrNull { it.index == slotIndex } ?: return 8 * 60
    return parseClock(slot.start) ?: (8 * 60)
}

fun slotEndMinute(slotIndex: Int, slots: List<TimeSlot>): Int {
    val slot = slots.firstOrNull { it.index == slotIndex } ?: return 8 * 60 + 45
    return parseClock(slot.end) ?: (8 * 60 + 45)
}

/** 课程在当天的起止分钟（agenda 用自己的分钟数）。 */
fun courseStartMinute(course: Course, slots: List<TimeSlot>): Int =
    if (course.isAgenda && course.startMinute >= 0) course.startMinute else slotStartMinute(course.startSlot, slots)

fun courseEndMinute(course: Course, slots: List<TimeSlot>): Int =
    if (course.isAgenda && course.endMinute >= 0) course.endMinute else slotEndMinute(course.endSlot, slots)

fun minuteText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * 课程起始节次：优先用自己的 `startSlot`；只有 agenda（自由时间）才退化成按分钟反推。
 *
 * 反推这一步是必要的：日程块可能落在两节课之间（比如 12:30 的班会），没有节次可用。
 */
fun courseStartSlot(course: Course, slots: List<TimeSlot>): Int =
    if (course.startSlot > 0) {
        course.startSlot
    } else {
        val minute = courseStartMinute(course, slots)
        slots.firstOrNull { parseClock(it.start) == minute }?.index ?: 1
    }

fun courseEndSlot(course: Course, slots: List<TimeSlot>): Int =
    if (course.endSlot > 0) {
        course.endSlot
    } else {
        val minute = courseEndMinute(course, slots)
        slots.lastOrNull { parseClock(it.end) == minute }?.index ?: courseStartSlot(course, slots)
    }

/** 占几格（网格渲染用；至少 1）。 */
fun courseSpan(course: Course, slots: List<TimeSlot>): Int =
    (courseEndSlot(course, slots) - courseStartSlot(course, slots) + 1).coerceAtLeast(1)

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** 「下一节课」的结果。 */
data class UpcomingCourse(
    val course: Course,
    /** 0 = 今天，1 = 明天 … */
    val dayOffset: Int,
    val startTime: String,
    val endTime: String,
    val startAt: LocalDateTime,
) {
    val dayLabel: String
        get() = when (dayOffset) {
            0 -> "今天"
            1 -> "明天"
            2 -> "后天"
            else -> "$dayOffset 天后"
        }
}

/**
 * 找「下一节课」：今天还没上完的最早一节；今天没有了就往后找 7 天（跳过没课的日子）。
 *
 * 移植自 Android 端 `findUpcomingCourse` —— 判据是**结束时间缺失视为未结束**，
 * 这样正在上的课会显示成「正在上」而不是被跳过。
 */
fun findUpcomingCourse(
    courses: List<Course>,
    slots: List<TimeSlot>,
    week: Int,
    today: LocalDate,
    now: LocalTime,
    isOffDay: (LocalDate) -> Boolean = { it.dayOfWeek.value >= 6 },
): UpcomingCourse? {
    if (week <= 0) return null
    // 规则在共享层；这里做两件事：把 Course 翻成判定用最少数，把命中下标补回时间。
    val candidates = courses.map { course ->
        UpcomingCandidate(
            dayOfWeek = course.dayOfWeek,
            startSlot = courseStartSlot(course, slots),
            startMinute = courseStartMinute(course, slots),
            endMinute = courseEndMinute(course, slots),
            occursThisWeek = courseOccursInWeek(course, week),
        )
    }
    val pick = pickUpcomingCourse(
        candidates = candidates,
        todayDayOfWeek = today.dayOfWeek.value,
        nowMinute = now.hour * 60 + now.minute,
        isOffDay = { offset -> isOffDay(today.plusDays(offset.toLong())) },
    ) ?: return null

    val course = courses[pick.index]
    val date = today.plusDays(pick.dayOffset.toLong())
    val startMin = courseStartMinute(course, slots)
    val endMin = courseEndMinute(course, slots)
    return UpcomingCourse(
        course = course,
        dayOffset = pick.dayOffset,
        startTime = minuteText(startMin),
        endTime = minuteText(endMin),
        startAt = LocalDateTime.of(date, LocalTime.of(startMin / 60, startMin % 60)),
    )
}

/** 某一天的课程（按开始时间排序，已按周次过滤）。 */
fun coursesOnDay(courses: List<Course>, slots: List<TimeSlot>, date: LocalDate, week: Int): List<Course> {
    if (week <= 0) return emptyList()
    return courses
        .filter { it.dayOfWeek == date.dayOfWeek.value && courseOccursInWeek(it, week) }
        .sortedBy { courseStartMinute(it, slots) }
}

/** 本周课程数（用于统计卡）。 */
fun weeklyCourseCount(courses: List<Course>, week: Int, totalWeeks: Int): Int =
    courses.count { courseOccursInWeek(it, week, totalWeeks) }

/**
 * 导出为 .ics（iCalendar）。周末/n 次课都按「每周重复」写 RRULE。
 *
 * 判据：**只导出真正有课的周**，所以 RRULE 的 UNTIL 取该课最后一次出现的日期。
 */
/**
 * 导出为 .ics（iCalendar）。
 *
 * 历史实现用 `RRULE:FREQ=WEEKLY;UNTIL` 一条事件盖住整段，**会把单双周/指定周次里没课的周
 * 也展开出来**（导进日历多出几节不存在的课）。现在「按周展开」委托共享核心
 * `com.pocket.xiaoli.core.course.IcsExpansion`，与 Android 导出同一份逻辑；这里只保留桌面侧的
 * 头部信息与 UID 约定。
 */
fun buildIcs(courses: List<Course>, term: Term, slots: List<TimeSlot>): String {
    val start = runCatching { LocalDate.parse(term.startDate) }.getOrDefault(LocalDate.now())
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
    val slotStart = slots.associate { it.index to it.start }
    val slotEnd = slots.associate { it.index to it.end }

    val inputs = courses.map { course ->
        com.pocket.xiaoli.core.course.IcsCourseInput(
            id = course.id,
            name = course.name,
            teacher = course.teacher,
            room = course.room,
            dayOfWeek = course.dayOfWeek,
            weeks = com.pocket.xiaoli.core.weekSet(course.weekType, course.weeks, term.totalWeeks),
            startSlot = course.startSlot,
            endSlot = course.endSlot,
            startMinute = courseStartMinute(course, slots),
            endMinute = courseEndMinute(course, slots),
        )
    }
    val events = com.pocket.xiaoli.core.course.expandIcsWeekEvents(
        courses = inputs,
        slotStart = slotStart,
        slotEnd = slotEnd,
        semesterStartEpochDay = start.toEpochDay(),
        totalWeeks = term.totalWeeks,
    )

    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\n")
    sb.append("VERSION:2.0\r\n")
    sb.append("PRODID:-//Pocket Xiaoli//Desktop//CN\r\n")
    sb.append("CALSCALE:GREGORIAN\r\n")
    sb.append("X-WR-CALNAME:").append(term.displayName.replace(",", " ")).append("\r\n")
    events.forEach { event ->
        val course = event.course
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:").append(course.id).append('-').append(event.week).append('-').append(stamp).append("\r\n")
        sb.append("DTSTAMP:").append(stamp).append("\r\n")
        sb.append("DTSTART:").append(event.dateCompact).append('T').append(event.startCompact).append("00\r\n")
        sb.append("DTEND:").append(event.dateCompact).append('T').append(event.endCompact).append("00\r\n")
        sb.append("SUMMARY:").append(com.pocket.xiaoli.core.course.icsEscape(course.name)).append("\r\n")
        if (course.room.isNotBlank()) {
            sb.append("LOCATION:").append(com.pocket.xiaoli.core.course.icsEscape(course.room)).append("\r\n")
        }
        val desc = buildString {
            if (course.teacher.isNotBlank()) append("教师：").append(course.teacher)
            if (course.weeks.isNotEmpty()) append("\\n").append("周次：第 ").append(event.week).append(" 周")
        }
        if (desc.isNotBlank()) {
            sb.append("DESCRIPTION:").append(com.pocket.xiaoli.core.course.icsEscape(desc)).append("\r\n")
        }
        sb.append("END:VEVENT\r\n")
    }
    sb.append("END:VCALENDAR\r\n")
    return sb.toString()
}