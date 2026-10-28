package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 周次口径的边界。
 *
 * 守两条：**开学前没有周次**（不是第 1 周）、**不足 7 天算第 1 周**。
 * 这两条写错，课表会把寒假画成第 1 周、把第 1 周的周画成第 2 周。
 */
class WeekCalendarCoreTest {

    private val start = 20_000L // 任意基准日，测的是相对天数

    @Test
    fun `开学前没有周次`() {
        assertNull(WeekCalendarCore.weekOf(start, start - 1))
        assertNull(WeekCalendarCore.weekOf(start, start - 400))
    }

    @Test
    fun `开学当天与第 6 天都算第 1 周`() {
        assertEquals(1, WeekCalendarCore.weekOf(start, start))
        assertEquals(1, WeekCalendarCore.weekOf(start, start + 6))
    }

    @Test
    fun `满 7 天进入第 2 周`() {
        assertEquals(2, WeekCalendarCore.weekOf(start, start + 7))
        assertEquals(2, WeekCalendarCore.weekOf(start, start + 13))
        assertEquals(3, WeekCalendarCore.weekOf(start, start + 14))
    }

    @Test
    fun `课表口径把开学前夹到第 1 周 把超界夹到总周数`() {
        assertEquals(1, WeekCalendarCore.weekOfClamped(start, start - 30, totalWeeks = 20))
        assertEquals(1, WeekCalendarCore.weekOfClamped(start, start + 3, totalWeeks = 20))
        assertEquals(20, WeekCalendarCore.weekOfClamped(start, start + 7 * 40, totalWeeks = 20))
        // 总周数非法时不崩，按 1 处理
        assertEquals(1, WeekCalendarCore.weekOfClamped(start, start + 7 * 40, totalWeeks = 0))
    }

    @Test
    fun `教学周结束日等于 教学周数加考试周 的周日`() {
        // 18 周教学 + 2 周考试 = 20 周；最后一天 = 开学日 + 140 - 1
        assertEquals(start + 140 - 1, WeekCalendarCore.teachingEndEpochDay(start, teachingWeeks = 18))
        // 最后一天确实落在第 20 周内，而次日已不属于任何教学周
        val end = WeekCalendarCore.teachingEndEpochDay(start, teachingWeeks = 18)
        assertEquals(20, WeekCalendarCore.weekOf(start, end))
        assertEquals(21, WeekCalendarCore.weekOf(start, end + 1))
    }

    @Test
    fun `教学周数为负时至少覆盖考试周`() {
        assertEquals(start + 14 - 1, WeekCalendarCore.teachingEndEpochDay(start, teachingWeeks = -3))
    }
}
