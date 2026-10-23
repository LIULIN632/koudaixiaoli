package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.holiday.HolidayDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 课表周头上的节假日标记：休 = 放假，班 = 调休上班。 */
data class HolidayMark(val label: String, val isOffDay: Boolean)

/**
 * 节假日换算工具 —— 语义对齐「聚在工大」的 Day.kt + SpecialWorkDayEntity。
 * 全部纯函数，不依赖 Android，便于单元测试。
 */
object HolidayCalendar {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")

    /** 该日期的放假信息，非放假返回 null。 */
    fun offDayOf(days: List<HolidayDay>, date: LocalDate): HolidayDay? =
        days.firstOrNull { it.isOffDay && it.date == date.format(ISO) }

    /** 该日期的调休上班信息，非调休日返回 null。 */
    fun adjustedWorkdayOf(days: List<HolidayDay>, date: LocalDate): HolidayDay? =
        days.firstOrNull { !it.isOffDay && it.date == date.format(ISO) }

    /** 是否法定休息日（不排课）。 */
    fun isOffDay(days: List<HolidayDay>, date: LocalDate): Boolean = offDayOf(days, date) != null

    /** 是否调休上班日（周末补课）。 */
    fun isAdjustedWorkday(days: List<HolidayDay>, date: LocalDate): Boolean =
        adjustedWorkdayOf(days, date) != null

    /**
     * 生成某周（自 [weekMonday] 起 7 天）的周头标记，key 为 yyyy-MM-dd。
     * 放假 → 节日名（空白兜底「休」）；调休上班 → 「班」。
     */
    fun weekMarks(days: List<HolidayDay>, weekMonday: LocalDate): Map<String, HolidayMark> {
        if (days.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, HolidayMark>()
        for (offset in 0..6) {
            val date = weekMonday.plusDays(offset.toLong())
            val key = date.format(ISO)
            val off = offDayOf(days, date)
            if (off != null) {
                result[key] = HolidayMark(off.name.ifBlank { "休" }, true)
                continue
            }
            if (adjustedWorkdayOf(days, date) != null) {
                result[key] = HolidayMark("班", false)
            }
        }
        return result
    }

    /** 校验调休映射的日期格式（对齐聚在工大 insertSafely 的入参校验）。 */
    fun isValidDate(value: String): Boolean = DATE_REGEX.matches(value)
}