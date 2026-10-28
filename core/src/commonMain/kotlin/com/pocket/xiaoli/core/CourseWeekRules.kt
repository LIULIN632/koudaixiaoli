package com.pocket.xiaoli.core

/**
 * 课表「这一周有没有这门课」的**唯一实现**（两端共用）。
 *
 * 规则（与 Android 端 2026-10 之前的历史行为逐条对齐，不要再改顺序）：
 * 1. `weekType` = `odd` / `even` / 其它（视同 `all`）：先按单双周过滤，不通过直接 false；
 * 2. 再按 `weeks` 收窄：解析出的周次集合里没有 `week` 就不出现；
 *    `weeks` 解析不出任何数字（空串、`"  "`、`"a,b"`）则视为「没有额外限制」。
 *
 * 两端共用：Android 的 `CourseEntity` 与桌面的 `Course` 都只是把字段喂进来。
 */
fun isCourseInWeek(weekType: String, weeks: String, week: Int): Boolean {
    if (!matchesWeekType(weekType, week)) return false
    val parsed = parseWeekSpec(weeks)
    if (parsed.isNotEmpty() && week !in parsed) return false
    return true
}

/**
 * 把课程展开成「整学期里出现的周次集合」。
 *
 * 与 [isCourseInWeek] 同一套规则，只是能同时拿到「学期总周数」：
 * `weeks` 为空时展开成 `1..totalWeeks`（课表要画格子，需要知道具体哪几周）。
 */
fun weekSet(weekType: String, weeks: String, totalWeeks: Int): Set<Int> {
    val total = maxOf(totalWeeks, 1)
    val parsed = parseWeekSpec(weeks)
    val base = if (parsed.isEmpty()) (1..total).toSet() else parsed.filter { it in 1..60 }.toSet()
    return base.filter { matchesWeekType(weekType, it) }.toSet()
}

/**
 * 解析周次描述：`"1,3,5"`、`"1-16"`、`"1~8,10"` 都认。
 *
 * 兼容中文逗号与破折号；非法片段直接丢弃（解析不出任何数字时返回空集合）。
 */
fun parseWeekSpec(spec: String): Set<Int> {
    val result = linkedSetOf<Int>()
    spec.split(',', '，').forEach { raw ->
        val part = raw.trim()
        if (part.isEmpty()) return@forEach
        val dash = part.indexOfFirst { it == '-' || it == '~' || it == '—' }
        if (dash > 0) {
            val from = part.substring(0, dash).trim().toIntOrNull()
            val to = part.substring(dash + 1).trim().toIntOrNull()
            if (from != null && to != null) {
                for (w in minOf(from, to)..maxOf(from, to)) if (w in 1..60) result.add(w)
            }
        } else {
            part.toIntOrNull()?.let { if (it in 1..60) result.add(it) }
        }
    }
    return result
}

/** 只解析逗号分隔的整数周次（老调用点用；范围请用 [parseWeekSpec]）。 */
fun parseWeeks(weeks: String): List<Int> = parseWeekSpec(weeks).toList()

/** 单双周过滤：`odd` 只认奇数周、`even` 只认偶数周、其它一律通过。 */
fun matchesWeekType(weekType: String, week: Int): Boolean {
    val isOdd = week % 2 == 1
    return when (weekType) {
        "odd" -> isOdd
        "even" -> !isOdd
        else -> true
    }
}
