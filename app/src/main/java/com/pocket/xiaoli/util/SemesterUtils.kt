package com.pocket.xiaoli.util

import java.time.LocalDate

/**
 * 解析 "yyyy-MM-dd" 格式的学期开始日期，解析失败返回 null。
 */
fun parseSemesterStart(startStr: String): LocalDate? = try {
    val parts = startStr.split("-")
    if (parts.size >= 3) {
        LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } else {
        null
    }
} catch (_: Exception) {
    null
}

/**
 * 由学期开始日期计算给定日期处于第几周（不足 7 天按第 1 周计），并限制在 [1, totalWeeks]。
 *
 * 口径在共享核心 `com.pocket.xiaoli.core.calendar.WeekCalendarCore` 里，与 Windows 桌面端同一份：
 * 开学前夹到第 1 周、超界夹到总周数、总周数非法按 1 处理。
 */
fun currentWeekFrom(start: LocalDate, totalWeeks: Int, today: LocalDate = LocalDate.now()): Int =
    com.pocket.xiaoli.core.calendar.WeekCalendarCore.weekOfClamped(
        startEpochDay = start.toEpochDay(),
        todayEpochDay = today.toEpochDay(),
        totalWeeks = totalWeeks,
    )
