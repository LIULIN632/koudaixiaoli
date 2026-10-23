package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 判断课程是否在第 [week] 周出现。
 *
 * 规则：`weekType`（odd / even / all）与 `weeks`（逗号分隔的周次列表）共同决定；
 * 当 `weeks` 非空时以其为准（比单双周更精确）。
 *
 * 抽成公共函数是因为「首页下一节课」和「教室分布图·今日课程」都要用同一套判断 —
 * 两边各写一份迟早会漂移。
 */
fun isCourseInWeek(course: CourseEntity, week: Int): Boolean {
    val isOdd = week % 2 == 1
    if (course.weekType == "odd" && !isOdd) return false
    if (course.weekType == "even" && isOdd) return false
    if (course.weeks.isNotBlank()) {
        val weeks = course.weeks.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (weeks.isNotEmpty() && week !in weeks) return false
    }
    return true
}