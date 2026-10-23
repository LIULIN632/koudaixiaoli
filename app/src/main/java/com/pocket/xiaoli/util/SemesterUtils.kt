package com.pocket.xiaoli.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
 */
fun currentWeekFrom(start: LocalDate, totalWeeks: Int, today: LocalDate = LocalDate.now()): Int {
    val days = ChronoUnit.DAYS.between(start, today)
    return (Math.floorDiv(days, 7).toInt() + 1).coerceIn(1, totalWeeks)
}
