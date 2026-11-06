package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HolidayCalendarCoreTest {

    @Test
    fun `阳历换算与已知锚点一致`() {
        assertEquals(0L, epochDayOf(1970, 1, 1))
        assertEquals(19_723L, epochDayOf(2024, 1, 1))
        assertEquals(20_089L, epochDayOf(2025, 1, 1), "2024 是闰年，+366")
        assertEquals(20_454L, epochDayOf(2026, 1, 1))
        assertEquals(20_758L, epochDayOf(2026, 11, 1))
        assertEquals(epochDayOf(2026, 3, 1) - 1, epochDayOf(2026, 2, 28), "2026 非闰年")
    }

    @Test
    fun `内置 2026 数据条数与口径正确`() {
        val days = BUILTIN_HOLIDAYS_2026
        assertEquals(39, days.size, "国务院 2026 通知：33 天放假 + 6 天调休上班")
        assertEquals(33, days.count { it.isOff })
        assertEquals(6, days.count { !it.isOff })
        assertTrue(days.all { it.name.isNotBlank() })
        assertEquals(days.sortedBy { it.epochDay }, days, "必须按日期升序，UI 才不用再排")
    }

    @Test
    fun `元旦首尾与春节调休都能命中`() {
        val days = BUILTIN_HOLIDAYS_2026
        val newYear = holidayOn(days, epochDayOf(2026, 1, 1))
        assertEquals("元旦", newYear?.name)
        assertTrue(newYear!!.isOff)
        // 1 月 4 日是调休上班
        val makeup = holidayOn(days, epochDayOf(2026, 1, 4))
        assertEquals(false, makeup?.isOff)
        assertEquals("元旦", makeup?.name)
        // 普通日子不是节假日
        assertNull(holidayOn(days, epochDayOf(2026, 3, 15)))
    }

    @Test
    fun `下一个放假日与倒计时`() {
        val days = BUILTIN_HOLIDAYS_2026
        val march15 = epochDayOf(2026, 3, 15)
        val next = nextOffDay(days, march15)
        assertTrue(next != null && next.epochDay > march15)
        assertEquals((next!!.epochDay - march15).toInt(), daysToNextOffDay(days, march15))
        // 暑假不在数据里（只含法定节假日），所以 8 月之后应该还能找到下一个假
        assertTrue(daysToNextOffDay(days, epochDayOf(2026, 8, 1)) != null)
        // 当天就在假期里 → 0 天
        assertEquals(0, daysToNextOffDay(days, epochDayOf(2026, 1, 2)))
    }

    @Test
    fun `解析 holiday-cn 格式并过滤年份`() {
        val json = """
            {
              "year": 2026,
              "days": [
                {"name":"元旦","date":"2026-01-01","isOffDay":true},
                {"name":"元旦","date":"2026-01-04","isOffDay":false},
                {"name":"跨年噪声","date":"2025-12-31","isOffDay":true}
              ]
            }
        """.trimIndent()

        val all = parseHolidayJson(json)
        assertEquals(3, all.size)

        val only2026 = parseHolidayJson(json, expectYear = 2026)
        assertEquals(2, only2026.size)
        assertEquals(epochDayOf(2026, 1, 1), only2026.first().epochDay)
    }

    @Test
    fun `脏条目被跳过而不是抛异常`() {
        val json = """
            {"days":[
              {"name":"没有日期","isOffDay":true},
              {"name":"缺 else","date":"2026-05-01"},
              {"name":"正常","date":"2026-05-01","isOffDay":true}
            ]}
        """.trimIndent()
        val days = parseHolidayJson(json)
        assertEquals(1, days.size)
        assertEquals("正常", days.first().name)
    }

    @Test
    fun `内置数据与解析结果可以互换`() {
        // 网络更新下来的文件解析后，结构上应能直接替换内置表
        val rebuilt = parseHolidayJson(
            """
            {"days":[
              {"name":"元旦","date":"2026-01-01","isOffDay":true},
              {"name":"元旦","date":"2026-01-04","isOffDay":false}
            ]}
            """.trimIndent(),
            expectYear = 2026,
        )
        assertFalse(rebuilt.isEmpty())
        assertTrue(rebuilt.all { it.epochDay >= epochDayOf(2026, 1, 1) })
    }
}
