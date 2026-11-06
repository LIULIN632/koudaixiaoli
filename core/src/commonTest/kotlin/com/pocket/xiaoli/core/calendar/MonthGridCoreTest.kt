package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthGridCoreTest {

    // 锚点：1970-01-01 是周四；2026-01-01 是周四（20454），故 2026-11-01 是周日（20758）。
    // 这两个常量现在由 `epochDayOf` 直接算出来，不再手写魔数（之前写错过一天，见 HolidayCalendarCoreTest 的绝对锚点断言）。
    private val nov1 = epochDayOf(2026, 11, 1)
    private val feb1 = epochDayOf(2027, 2, 1)

    @Test
    fun `周一开头的一周里, 11月1日是周日, 前面补 6 个灰格`() {
        val cells = monthGrid(firstEpochDayOfMonth = nov1, daysInMonth = 30, weekdayOfFirst = 6)
        assertEquals(42, cells.size, "6 + 30 = 36 格，向上取整到 42")
        assertEquals(6, cells.count { !it.inMonth && it.epochDay < nov1 }, "11 月前面补 6 天（10/26 起）")
        assertEquals(30, cells.count { it.inMonth })
        assertEquals(6, cells.count { !it.inMonth && it.epochDay >= nov1 + 30 })
        assertEquals(cells.map { it.epochDay }, (cells.first().epochDay..cells.last().epochDay).toList(), "格子必须连续")
    }

    @Test
    fun `一周从周日起算时, 11月1日正好落在第一格`() {
        val cells = monthGrid(firstEpochDayOfMonth = nov1, daysInMonth = 30, weekdayOfFirst = 6, firstDayOfWeek = 6)
        assertEquals(0, cells.count { !it.inMonth && it.epochDay < nov1 })
        assertTrue(cells.first().inMonth)
        assertEquals(35, cells.size, "30 天从周日起 = 5 行, 不留第 6 行空白")
    }

    @Test
    fun `28 天且从周一开始的月只有 4 行`() {
        // 2027-02-01 是周一（2026-11-02 周一 + 91 天）
        val cells = monthGrid(firstEpochDayOfMonth = feb1, daysInMonth = 28, weekdayOfFirst = 0)
        assertEquals(28, cells.size)
        assertTrue(cells.all { it.inMonth })
    }

    @Test
    fun `月内天数与补位数量自洽`() {
        for (days in 28..31) {
            for (weekday in 0..6) {
                val cells = monthGrid(firstEpochDayOfMonth = 0L, daysInMonth = days, weekdayOfFirst = weekday)
                assertEquals(0, cells.size % 7, "格数必须是 7 的倍数（days=$days weekday=$weekday）")
                assertEquals(days, cells.count { it.inMonth })
                assertFalse(cells.any { it.inMonth && (it.epochDay < 0 || it.epochDay >= days) })
            }
        }
    }

    @Test
    fun `参数越界直接失败而不是画错格子`() {
        assertFailsWith<IllegalArgumentException> { monthGrid(0L, 27, 0) }
        assertFailsWith<IllegalArgumentException> { monthGrid(0L, 31, 7) }
        assertFailsWith<IllegalArgumentException> { monthGrid(0L, 31, 0, firstDayOfWeek = -1) }
    }

    @Test
    fun `锚点日期与节假日模块的换算一致`() {
        assertEquals(20_758L, nov1, "2026-11-01")
        assertEquals(20_850L, feb1, "2027-02-01")
        assertEquals(6, monthGrid(nov1, 30, weekdayOfFirst = 6).count { !it.inMonth && it.epochDay < nov1 })
    }
}
