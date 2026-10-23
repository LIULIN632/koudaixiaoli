package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.holiday.HolidayDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 调休排课换算测试。
 * 场景取自 2026 国庆：10-01 ~ 10-07 放假，10-11（周日）补 10-07（周三）的课。
 */
class HolidayScheduleTest {

    private val days = listOf(
        HolidayDay(name = "国庆节", date = "2026-10-01", isOffDay = true),
        HolidayDay(name = "国庆节", date = "2026-10-10", isOffDay = true),
        HolidayDay(name = "国庆节", date = "2026-10-07", isOffDay = true),
        HolidayDay(name = "国庆节后补班", date = "2026-10-11", isOffDay = false),
    )
    private val mappings = mapOf("2026-10-11" to "2026-10-07")

    @Test
    fun offDayHasNoSchedule() {
        val week = HolidayCalendar.weekMarks(days, LocalDate.of(2026, 10, 5))
        assertTrue(week.containsKey("2026-10-07"))
        val schedule = HolidaySchedule.weekSchedule(days, mappings, LocalDate.of(2026, 10, 5))
        assertEquals(7, schedule.size)
        assertEquals(0, schedule.first { it.date == "2026-10-07" }.effectiveDayOfWeek)
        assertFalse(schedule.first { it.date == "2026-10-07" }.isAdjusted)
    }

    @Test
    fun adjustedWorkdayUsesTargetWeekday() {
        val schedule = HolidaySchedule.weekSchedule(days, mappings, LocalDate.of(2026, 10, 5))
        val sunday = schedule.first { it.date == "2026-10-11" }
        assertEquals(3, sunday.effectiveDayOfWeek) // 补周三的课
        assertTrue(sunday.isAdjusted)
        val monday = schedule.first { it.date == "2026-10-05" }
        assertEquals(1, monday.effectiveDayOfWeek) // 10-05 不在本次测试数据的放假列表中，按自身星期一排课
        assertFalse(monday.isAdjusted)
    }

    @Test
    fun adjustedWorkdayWithoutMappingKeepsOwnWeekday() {
        val schedule = HolidaySchedule.weekSchedule(days, emptyMap(), LocalDate.of(2026, 10, 5))
        val sunday = schedule.first { it.date == "2026-10-11" }
        assertEquals(7, sunday.effectiveDayOfWeek)
        assertFalse(sunday.isAdjusted)
    }

    @Test
    fun columnMappingMovesTargetCoursesToAdjustedColumn() {
        val schedule = HolidaySchedule.weekSchedule(days, mappings, LocalDate.of(2026, 10, 5))
        val mapping = HolidaySchedule.columnMapping(schedule)
        // 周三(3)的课画到周日列(7)
        assertEquals(7, mapping[3])
        // 周日(7)自己的课让位，不显示
        assertNull(mapping[7])
        // 未被调休影响的星期保持原样
        assertEquals(1, mapping[1])
        assertEquals(2, mapping[2])
    }

    @Test
    fun columnMappingSuppressesCoursesOnOffDay() {
        val schedule = HolidaySchedule.weekSchedule(days, emptyMap(), LocalDate.of(2026, 10, 5))
        val mapping = HolidaySchedule.columnMapping(schedule)
        assertNull(mapping[3]) // 周三放假，不显示
        assertEquals(4, mapping[4]) // 假周（10-01 假期）内其他工作日不放假时保持原样
    }

    @Test
    fun normalWeekIsIdentity() {
        val schedule = HolidaySchedule.weekSchedule(emptyList(), emptyMap(), LocalDate.of(2026, 10, 5))
        val mapping = HolidaySchedule.columnMapping(schedule)
        (1..7).forEach { assertEquals(it, mapping[it]) }
    }

    @Test
    fun invalidMappingIsIgnored() {
        val schedule = HolidaySchedule.weekSchedule(days, mapOf("2026-10-11" to "not-a-date"), LocalDate.of(2026, 10, 5))
        val sunday = schedule.first { it.date == "2026-10-11" }
        assertEquals(7, sunday.effectiveDayOfWeek)
        assertFalse(sunday.isAdjusted)
    }

    @Test
    fun weekdayLabels() {
        assertEquals("周三", HolidaySchedule.weekdayLabel(3))
        assertEquals("三", HolidaySchedule.weekdayShortLabel(3))
        assertEquals("", HolidaySchedule.weekdayLabel(0))
    }

    @Test
    fun remapCoursesMovesAndSuppresses() {
        val courses = listOf(
            CourseEntity(id = 1, name = "高数", dayOfWeek = 3),
            CourseEntity(id = 2, name = "英语", dayOfWeek = 7),
            CourseEntity(id = 3, name = "体育", dayOfWeek = 1),
        )
        val schedule = HolidaySchedule.weekSchedule(days, mappings, LocalDate.of(2026, 10, 5))
        val result = HolidaySchedule.remapCoursesForWeek(courses, HolidaySchedule.columnMapping(schedule))
        assertEquals(2, result.size)
        assertEquals(7, result.first { it.id == 1L }.dayOfWeek) // 周三的课画到周日列
        assertEquals(1, result.first { it.id == 3L }.dayOfWeek) // 不受影响的课保持原样
        assertTrue(result.none { it.id == 2L })                 // 周日自己的课让位
    }

    @Test
    fun translateDayDeltaInAdjustedWeek() {
        val schedule = HolidaySchedule.weekSchedule(days, mappings, LocalDate.of(2026, 10, 5))
        // 周三的课显示在周日列(7)，向左拖 2 列到周五(5) → 原始星期 +2
        assertEquals(2, HolidaySchedule.translateDayDelta(3, -2, schedule))
        // 向左拖 1 列到周六(6)：当天放假，拒绝
        assertNull(HolidaySchedule.translateDayDelta(3, -1, schedule))
        // 未受调休影响的星期，位移原样保留
        assertEquals(1, HolidaySchedule.translateDayDelta(1, 1, schedule))
    }

    @Test
    fun translateDayDeltaIdentityWeek() {
        val schedule = HolidaySchedule.weekSchedule(emptyList(), emptyMap(), LocalDate.of(2026, 10, 5))
        assertEquals(-1, HolidaySchedule.translateDayDelta(4, -1, schedule))
        assertNull(HolidaySchedule.translateDayDelta(1, -1, schedule)) // 越界
    }
}
