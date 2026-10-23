package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.holiday.HolidayDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 某一天的实际排课归属。
 * [effectiveDayOfWeek] = 0 表示当天放假、不上课；1..7 表示按周几的课表上课。
 */
data class DaySchedule(
    val date: String,
    val effectiveDayOfWeek: Int,
    /** 调休上班且用户配置了「补哪天的课」时为 true。 */
    val isAdjusted: Boolean = false,
)

/**
 * 调休排课换算 —— 把 holiday-cn 的放假/补班数据与用户手动配置的
 * [com.pocket.xiaoli.data.entity.SpecialWorkDayEntity] 映射，换算成一周 7 天的实际排课归属。
 *
 * 规则优先级：放假（不排课） > 调休映射（按 target 的星期） > 当天自身的星期。
 * 全部纯函数，不依赖 Android，便于单元测试。
 */
object HolidaySchedule {

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * 计算某周（自 [weekMonday] 起 7 天）每天实际按周几的课表上课。
     *
     * @param days holiday-cn 全量数据
     * @param specialWorkDays originDate(yyyy-MM-dd) -> targetDate(yyyy-MM-dd)
     */
    fun weekSchedule(
        days: List<HolidayDay>,
        specialWorkDays: Map<String, String>,
        weekMonday: LocalDate,
    ): List<DaySchedule> = (0..6).map { offset ->
        val date = weekMonday.plusDays(offset.toLong())
        val key = date.format(ISO)
        if (HolidayCalendar.offDayOf(days, date) != null) {
            DaySchedule(date = key, effectiveDayOfWeek = 0)
        } else {
            val targetDow = specialWorkDays[key]?.let { target ->
                runCatching { LocalDate.parse(target, ISO).dayOfWeek.value }.getOrNull()
            }
            if (targetDow != null && targetDow in 1..7) {
                DaySchedule(date = key, effectiveDayOfWeek = targetDow, isAdjusted = true)
            } else {
                DaySchedule(date = key, effectiveDayOfWeek = date.dayOfWeek.value)
            }
        }
    }

    /**
     * 把一周的排课归属反推成「课程原始星期 -> 应显示在哪一列（周几）」。
     * value = null 表示该原始星期的课在本周不显示（对应那天放假）。
     *
     * 典型场景：10-07（周三）放假、10-11（周日）调休补周三的课 →
     * 返回 `3 -> 7`（周三的课画到周日列）、`7 -> null`（周日自己的课不显示）。
     */
    fun columnMapping(schedule: List<DaySchedule>): Map<Int, Int?> {
        val result = (1..7).associateWith { it as Int? }.toMutableMap()
        // 1) 放假：当天原本的课不显示
        schedule.forEach { day ->
            if (day.effectiveDayOfWeek == 0) {
                dayOfWeekOf(day.date)?.let { result[it] = null }
            }
        }
        // 2) 调休补课：目标星期的课改画到补课日列；补课日自己的课让位（不显示）
        schedule.forEach { day ->
            val effective = day.effectiveDayOfWeek
            if (day.isAdjusted && effective in 1..7) {
                dayOfWeekOf(day.date)?.let { column ->
                    result[column] = null
                    result[effective] = column
                }
            }
        }
        return result
    }

    /** 周一..周日的中文短标签，如 3 -> 「周三」。 */
    fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> ""
    }

    /** 周次列表中的中文标签（日历顺序），如 1 -> 「一」。 */
    fun weekdayShortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"
        5 -> "五"; 6 -> "六"; 7 -> "日"; else -> ""
    }

    /**
     * 按本周列映射重排课程：[columnMapping] 中 value 为 null 的星期整周不显示；
     * 需要换列的课程返回 `dayOfWeek` 改写后的副本（不修改原对象）。
     */
    fun remapCoursesForWeek(
        courses: List<CourseEntity>,
        columnMapping: Map<Int, Int?>,
    ): List<CourseEntity> = courses.mapNotNull { course ->
        val column = columnMapping[course.dayOfWeek] ?: return@mapNotNull null
        if (column == course.dayOfWeek) course else course.copy(dayOfWeek = column)
    }

    /**
     * 把「显示列上的位移」翻译回「课程原始星期的位移」，供调休周拖拽换课使用。
     * 目标列越界、或目标列当天放假时返回 null，调用方应忽略本次拖动。
     */
    fun translateDayDelta(
        originalDay: Int,
        displayDayDelta: Int,
        schedule: List<DaySchedule>,
    ): Int? {
        val displayColumn = columnMapping(schedule)[originalDay] ?: return null
        val newColumn = displayColumn + displayDayDelta
        if (newColumn !in 1..7) return null
        val newOriginalDay = schedule
            .firstOrNull { it.effectiveDayOfWeek in 1..7 && dayOfWeekOf(it.date) == newColumn }
            ?.effectiveDayOfWeek
            ?: return null
        return newOriginalDay - originalDay
    }

    /** 校验并返回日期对应的周几；格式非法返回 null。 */
    private fun dayOfWeekOf(date: String): Int? =
        runCatching { LocalDate.parse(date, ISO).dayOfWeek.value }.getOrNull()
}
