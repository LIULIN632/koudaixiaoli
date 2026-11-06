package com.pocket.xiaoli.core.calendar

/**
 * 月历格子里的一个格子。
 *
 * [epochDay] 用 Unix epoch day 表达，避免共享层依赖 `java.time`；
 * 两端各自转成自己的日期类型（Android `LocalDate.ofEpochDay`、桌面 `LocalDate.ofEpochDay`）。
 */
data class MonthCellSpec(
    val epochDay: Long,
    val inMonth: Boolean,
)

/**
 * 月历几何：算这个月要画几行、每格是哪天、哪几格是补位的灰字。
 *
 * **不写死 6 行**：正好从周一开始的 28 天月只占 4 行，写死 6 行会在下面留一大片空白。
 *
 * 参数都是「已算好的事实」，共享层不做日历运算：
 * - [firstEpochDayOfMonth]：本月 1 号的 epoch day；
 * - [daysInMonth]：本月天数；
 * - [weekdayOfFirst]：本月 1 号是周几，`0=周一 … 6=周日`；
 * - [firstDayOfWeek]：一周从周几开始，默认周一（`0=周一 … 6=周日`）。
 */
fun monthGrid(
    firstEpochDayOfMonth: Long,
    daysInMonth: Int,
    weekdayOfFirst: Int,
    firstDayOfWeek: Int = 0,
): List<MonthCellSpec> {
    require(daysInMonth in 28..31) { "daysInMonth 应在 28..31，实际 $daysInMonth" }
    require(weekdayOfFirst in 0..6) { "weekdayOfFirst 应在 0..6，实际 $weekdayOfFirst" }
    require(firstDayOfWeek in 0..6) { "firstDayOfWeek 应在 0..6，实际 $firstDayOfWeek" }

    val leading = Math.floorMod(weekdayOfFirst - firstDayOfWeek, 7)
    val rows = (leading + daysInMonth + 6) / 7
    val startEpochDay = firstEpochDayOfMonth - leading

    return (0 until rows * 7).map { offset ->
        MonthCellSpec(
            epochDay = startEpochDay + offset,
            inMonth = offset in leading until (leading + daysInMonth),
        )
    }
}
