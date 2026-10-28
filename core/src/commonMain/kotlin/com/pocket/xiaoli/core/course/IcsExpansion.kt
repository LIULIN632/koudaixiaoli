package com.pocket.xiaoli.core.course

/**
 * 课表 ICS 导出里的「按周展开」—— 两端共用同一份。
 *
 * ## 为什么两端必须共享这一层
 *
 * ICS 有两种写法：① 每个课程一条 `RRULE:FREQ=WEEKLY;UNTIL=...`；② 每周一条 `VEVENT`。
 * ① 更短，但**表达不了单双周/指定周次** —— 第 3 周没课也会被展开出来，
 * 导进日历就是「多了一节不存在的课」。所以两端都按周展开（②），并且共用这份展开逻辑，
 * 免得一边改了周次规则、另一边导出还是旧口径。
 *
 * 周次集合由调用方用共享的 `weekSet(weekType, weeks, totalWeeks)` 算好再传进来 ——
 * 「空周次表示全学期」这类语义只在 `CourseWeekRules` 里定义一处。
 *
 * 日期用 **epochDay** 表达，`commonMain` 不依赖 `java.time`。
 */
data class IcsCourseInput(
    val id: String,
    val name: String,
    val teacher: String,
    val room: String,
    /** 1 = 周一 … 7 = 周日；越界会被跳过。 */
    val dayOfWeek: Int,
    /** 生效周次集合（单双周已展开）。 */
    val weeks: Set<Int>,
    val startSlot: Int,
    val endSlot: Int,
    /** 分钟级时间优先；-1 表示未填，回退到节次表。 */
    val startMinute: Int,
    val endMinute: Int,
)

/** 按周展开后的一条日历事件（`dateCompact`/`startCompact` 都是 ICS 用的紧凑格式）。 */
data class IcsWeekEvent(
    val course: IcsCourseInput,
    val week: Int,
    /** `yyyyMMdd`。 */
    val dateCompact: String,
    /** `HHmm`。 */
    val startCompact: String,
    /** `HHmm`。 */
    val endCompact: String,
)

/**
 * 把课程按周展开成事件列表。
 *
 * @param slotStart `节次 → 开始时间 "HH:mm"`（第 1 节 08:00 这种）。
 * @param semesterStartEpochDay 学期第一周周一。
 * @param totalWeeks 总周数；会被夹到 `1..30`（与导出侧的历史口径一致）。
 */
fun expandIcsWeekEvents(
    courses: List<IcsCourseInput>,
    slotStart: Map<Int, String>,
    slotEnd: Map<Int, String>,
    semesterStartEpochDay: Long,
    totalWeeks: Int,
): List<IcsWeekEvent> {
    val safeWeeks = totalWeeks.coerceIn(1, 30)
    val events = ArrayList<IcsWeekEvent>()
    for (course in courses) {
        if (course.dayOfWeek !in 1..7) continue
        for (week in course.weeks.sorted()) {
            if (week !in 1..safeWeeks) continue
            val epochDay = semesterStartEpochDay + (week - 1).toLong() * 7 + (course.dayOfWeek - 1).toLong()
            val start = minuteTime(course.startMinute, slotStart[course.startSlot], FALLBACK_START)
            val end = minuteTime(course.endMinute, slotEnd[course.endSlot], FALLBACK_END)
            events += IcsWeekEvent(
                course = course,
                week = week,
                dateCompact = compactDate(epochDay),
                startCompact = start,
                endCompact = end,
            )
        }
    }
    return events
}

/** ICS 文本转义（`\` `;` `,` 与换行）。 */
fun icsEscape(text: String): String = text
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")

private const val FALLBACK_START = "08:00"
private const val FALLBACK_END = "09:40"

/**
 * 分钟级优先、节次表兜底，输出 `HHmm`。
 *
 * 分钟填了但越界（`MinuteOfDay.isValid` 为假）时同样回退节次表 —— 与两端既有实现一致。
 */
private fun minuteTime(minute: Int, slotTime: String?, fallback: String): String {
    val time = if (minute in 0 until 24 * 60) formatClock(minute) else slotTime
    return compactClock(time ?: fallback) ?: compactClock(fallback) ?: fallback.replace(":", "")
}

/** 分钟 → `"HH:mm"`。 */
fun formatClock(minute: Int): String {
    val hour = minute / 60
    val min = minute % 60
    return hour.toString().padStart(2, '0') + ":" + min.toString().padStart(2, '0')
}

/** `"08:00"` → `"0800"`；解析不了返回 null（由调用方兜底）。 */
fun compactClock(text: String): String? {
    val parts = text.trim().split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return hour.toString().padStart(2, '0') + minute.toString().padStart(2, '0')
}

/** epochDay → `yyyyMMdd`。 */
fun compactDate(epochDay: Long): String =
    com.pocket.xiaoli.core.knowledge.epochDayToIso(epochDay).replace("-", "")
