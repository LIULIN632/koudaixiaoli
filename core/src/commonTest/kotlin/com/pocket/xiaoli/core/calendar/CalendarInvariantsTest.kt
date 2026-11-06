package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 跨函数**不变量**：周次、教学周结束日、阶段位三处必须永远自洽。
 *
 * 这些函数分开看都有各自单测，但真正会出事的是它们「对不上」——
 * 比如 `teachingEndEpochDay` 少算一天、`phaseOf` 的考试周区间错一周，
 * 单测各自都是绿的，界面上却会出现「最后一周显示未开始」。
 */
class CalendarInvariantsTest {

    private val teachingWeeks = 18
    private val examWeeks = WeekCalendarCore.EXAM_WEEKS
    private val startEpochDay = 20_697L // 任意起点

    private fun lastDayOfWeek(week: Int): Long = startEpochDay + week * 7L - 1

    @Test
    fun teachingEndIsLastDayOfExamWeek() {
        val end = WeekCalendarCore.teachingEndEpochDay(startEpochDay, teachingWeeks)
        assertEquals(teachingWeeks + examWeeks, WeekCalendarCore.weekOf(startEpochDay, end))
        assertEquals(end, lastDayOfWeek(teachingWeeks + examWeeks))
    }

    @Test
    fun dayAfterTeachingEndBelongsToNextWeek() {
        val end = WeekCalendarCore.teachingEndEpochDay(startEpochDay, teachingWeeks)
        assertEquals(teachingWeeks + examWeeks + 1, WeekCalendarCore.weekOf(startEpochDay, end + 1))
    }

    @Test
    fun phaseOfWeekIsConsistentWithTeachingEnd() {
        val endWeek = teachingWeeks + examWeeks
        val end = WeekCalendarCore.teachingEndEpochDay(startEpochDay, teachingWeeks)
        val weekAtEnd = WeekCalendarCore.weekOf(startEpochDay, end)
        assertEquals(TermWeeksCore.Phase.EXAM, TermWeeksCore.phaseOf(weekAtEnd, teachingWeeks))
        assertEquals(TermWeeksCore.Phase.AFTER, TermWeeksCore.phaseOf(endWeek + 1, teachingWeeks))
        assertEquals(TermWeeksCore.Phase.BEFORE, TermWeeksCore.phaseOf(WeekCalendarCore.weekOf(startEpochDay, startEpochDay - 1), teachingWeeks))
    }

    @Test
    fun weekBeforeTermHasNoWeekNumber() {
        assertEquals(null, WeekCalendarCore.weekOf(startEpochDay, startEpochDay - 1))
        assertEquals(1, WeekCalendarCore.weekOf(startEpochDay, startEpochDay))
    }

    @Test
    fun progressAtTeachingEndEqualsTeachingOverTotal() {
        assertEquals(
            teachingWeeks.toFloat() / (teachingWeeks + examWeeks),
            TermWeeksCore.progressFraction(teachingWeeks + 1, teachingWeeks),
        )
        assertTrue(TermWeeksCore.progressFraction(teachingWeeks, teachingWeeks) < 1f)
    }
}
