package com.pocket.xiaoli.ui.calendar

import com.pocket.xiaoli.data.calendar.CalendarEvent
import java.time.LocalDate

/**
 * 校历页面上的中文日期写法 —— **单一真源**。
 *
 * 抽出来是因为同一个日期要在三个地方出现（顶部主标题、事件列表的日期块、选中日卡片），
 * 三处各写一遍 `"${date.monthValue}月..."` 迟早会有一处忘了带「日」或忘了星期。
 *
 * 全部是纯函数：不依赖 Android，也不读 `LocalDate.now()`（现在由调用方注入）。
 */
internal object CalendarFormat {

    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 「10月16日」 */
    fun monthDay(date: LocalDate): String = "${date.monthValue}月${date.dayOfMonth}日"

    /** 「周五」 */
    fun weekday(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.value - 1]

    /** 「10月16日 周五」—— 事件列表左侧日期块下面那行。 */
    fun monthDayWeekday(date: LocalDate): String = "${monthDay(date)} ${weekday(date)}"

    /**
     * 事件的日期区间。
     *
     * 单日事件只写一天（不写成「10月1日 – 10月1日」）；
     * 跨月的事件写成「1月18日 – 2月28日」，两边都带月份 —— 只写「1月18日 – 28日」
     * 在跨月时会读成 1 月 28 日。
     */
    fun range(event: CalendarEvent): String = when {
        event.start == event.end -> monthDay(event.start)
        event.start.monthValue == event.end.monthValue ->
            "${monthDay(event.start)} – ${event.end.dayOfMonth}日"

        else -> "${monthDay(event.start)} – ${monthDay(event.end)}"
    }
}
