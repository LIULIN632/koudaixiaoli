package com.pocket.xiaoli.core.calendar

/**
 * 一个「法定节假日 / 调休上班」的日子。
 *
 * [isOff] = true 表示放假；false 表示**调休上班**（本应休息的周末被调走）。
 * 只描述法定口径，不含「与周末连休」的普通周末。
 */
data class HolidayDay(
    val epochDay: Long,
    val name: String,
    val isOff: Boolean,
)

/**
 * 阳历 → Unix epoch day（1970-01-01 = 0）。
 *
 * 共享层没有 `java.time`，这里用 Howard Hinnant 的 civil-days 算法，
 * 对 1970 前后、闰年、世纪年都成立（单测覆盖）。
 */
fun epochDayOf(year: Int, month: Int, day: Int): Long {
    require(month in 1..12) { "month 越界: $month" }
    require(day in 1..31) { "day 越界: $day" }
    val yy = if (month <= 2) year - 1 else year
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

/**
 * 解析 holiday-cn 同格式的 JSON（`{"year":2026,"days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}, …]}`）。
 *
 * 故意不引 JSON 库：这里只需要三个字段，正则够用且能给出「跳过几条」的诊断。
 * 返回按 epochDay 升序；[expectYear] 不为 null 时会丢掉其它年份的条目。
 */
fun parseHolidayJson(text: String, expectYear: Int? = null): List<HolidayDay> {
    val dayBlock = Regex("\\{[^{}]*}")
    val dateRe = Regex("\"date\"\\s*:\\s*\"(\\d{4})-(\\d{2})-(\\d{2})\"")
    val nameRe = Regex("\"name\"\\s*:\\s*\"([^\"]*)\"")
    val offRe = Regex("\"isOffDay\"\\s*:\\s*(true|false)")

    return dayBlock.findAll(text)
        .mapNotNull { match ->
            val block = match.value
            val date = dateRe.find(block) ?: return@mapNotNull null
            val (y, m, d) = date.destructured
            val year = y.toIntOrNull() ?: return@mapNotNull null
            val month = m.toIntOrNull() ?: return@mapNotNull null
            val day = d.toIntOrNull() ?: return@mapNotNull null
            if (expectYear != null && year != expectYear) return@mapNotNull null
            val name = nameRe.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val isOff = offRe.find(block)?.groupValues?.get(1)?.toBoolean() ?: return@mapNotNull null
            HolidayDay(epochDay = epochDayOf(year, month, day), name = name, isOff = isOff)
        }
        .toList()
        .sortedBy { it.epochDay }
}

/** 这一天是不是法定节假日 / 调休上班日；不是就返回 null。 */
fun holidayOn(days: List<HolidayDay>, epochDay: Long): HolidayDay? =
    days.firstOrNull { it.epochDay == epochDay }

/** 从 [fromEpochDay] 当天开始（含当天）找下一个放假的日子。 */
fun nextOffDay(days: List<HolidayDay>, fromEpochDay: Long): HolidayDay? =
    days.asSequence().filter { it.isOff && it.epochDay >= fromEpochDay }.minByOrNull { it.epochDay }

/** 距下一个放假还有几天（当天放假 = 0）；没有数据返回 null。 */
fun daysToNextOffDay(days: List<HolidayDay>, fromEpochDay: Long): Int? =
    nextOffDay(days, fromEpochDay)?.let { (it.epochDay - fromEpochDay).toInt() }
