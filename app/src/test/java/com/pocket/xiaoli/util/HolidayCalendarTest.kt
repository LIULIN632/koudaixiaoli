package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.holiday.HolidayDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节假日 / 调休换算测试 —— 锁定从「聚在工大」移植过来的语义：
 * 放假（isOffDay=true）与调休上班（isOffDay=false）必须严格区分。
 */
class HolidayCalendarTest {

    private val days = listOf(
        HolidayDay(name = "元旦", date = "2025-01-01", isOffDay = true),
        HolidayDay(name = "春节", date = "2025-01-28", isOffDay = true),
        HolidayDay(name = "春节", date = "2025-01-26", isOffDay = false),
    )

    @Test
    fun offDayDetected() {
        val date = LocalDate.of(2025, 1, 1)
        assertTrue(HolidayCalendar.isOffDay(days, date))
        assertEquals("元旦", HolidayCalendar.offDayOf(days, date)?.name)
    }

    @Test
    fun adjustedWorkdayDetected() {
        val date = LocalDate.of(2025, 1, 26)
        assertTrue(HolidayCalendar.isAdjustedWorkday(days, date))
        assertFalse(HolidayCalendar.isOffDay(days, date))
    }

    @Test
    fun plainDayHasNoMark() {
        val date = LocalDate.of(2025, 1, 2)
        assertNull(HolidayCalendar.offDayOf(days, date))
        assertFalse(HolidayCalendar.isAdjustedWorkday(days, date))
    }

    @Test
    fun weekMarksCoverMondayToSunday() {
        // 2025-01-27 是周一，本周含 2025-01-28 春节
        val marks = HolidayCalendar.weekMarks(days, LocalDate.of(2025, 1, 27))
        assertEquals(1, marks.size)
        assertEquals("春节", marks["2025-01-28"]?.label)
        assertTrue(marks["2025-01-28"]!!.isOffDay)
        assertTrue(marks.keys.all { it.length == 10 })
    }

    @Test
    fun adjustedWorkdayRendersBanBadge() {
        // 2025-01-20 是周一，其周日 2025-01-26 为调休上班
        val marks = HolidayCalendar.weekMarks(days, LocalDate.of(2025, 1, 20))
        assertEquals("班", marks["2025-01-26"]?.label)
        assertFalse(marks["2025-01-26"]!!.isOffDay)
    }

    @Test
    fun emptySourceYieldsEmptyMarks() {
        assertTrue(HolidayCalendar.weekMarks(emptyList(), LocalDate.of(2025, 1, 27)).isEmpty())
    }

    @Test
    fun dateFormatValidation() {
        assertTrue(HolidayCalendar.isValidDate("2025-01-01"))
        assertFalse(HolidayCalendar.isValidDate("2025-1-1"))
        assertFalse(HolidayCalendar.isValidDate(""))
    }
}