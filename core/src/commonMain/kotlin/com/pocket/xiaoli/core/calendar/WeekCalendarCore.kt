package com.pocket.xiaoli.core.calendar

/**
 * 学期周次与教学周边界的**平台无关实现**（Android 与 Windows 桌面端共用）。
 *
 * ## 口径
 *
 * - 「第 N 周」= 从开学第一周的**第一天**起每 7 天一周，**不足 7 天算第 1 周**；
 * - 开学前的日期**没有周次**（返回 null）——不存在「第 0 周」；
 * - 课表口径的学期结束日 = 第 `teachingWeeks + examWeeks` 周的**周日**
 *   （校历原文「第 19–20 周为期末考试时间」，所以 teachingWeeks 要加 2）。
 *
 * 日期用 **epochDay**（1970-01-01 起的天数）表达，`commonMain` 不依赖 `java.time`。
 */
object WeekCalendarCore {

    /** 期末考试占用的周数（校历统一 2 周）。 */
    const val EXAM_WEEKS: Int = 2

    /**
     * 日期落在第几周（**不夹取上限**）。
     *
     * @return 开学前返回 null；开学当天返回 1。
     */
    fun weekOf(startEpochDay: Long, todayEpochDay: Long): Int? {
        val days = todayEpochDay - startEpochDay
        if (days < 0) return null
        return (days / 7).toInt() + 1
    }

    /**
     * 课表口径的周次：在 [weekOf] 基础上 `coerceIn(1, totalWeeks)`。
     *
     * 开学前会被夹到第 1 周 —— 课表页要画格子，没有一个可展示的周次不行；
     * 而校历页要判断「这天跳不跳得过去」，那边请直接用 [weekOf]。
     */
    fun weekOfClamped(startEpochDay: Long, todayEpochDay: Long, totalWeeks: Int): Int =
        (weekOf(startEpochDay, todayEpochDay) ?: 1).coerceIn(1, maxOf(1, totalWeeks))

    /**
     * 教学 + 考试区间的最后一天（含当天）。
     *
     * @param teachingWeeks 教学周数（校历里的 `teaching_weeks`，通常 18）。
     */
    fun teachingEndEpochDay(startEpochDay: Long, teachingWeeks: Int): Long =
        startEpochDay + (maxOf(0, teachingWeeks) + EXAM_WEEKS).toLong() * 7 - 1
}
