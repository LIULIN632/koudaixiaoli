package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分钟级时间（照岱宗 `minuteOfDay`）：解析 / 格式化 / 有效性。 */
class MinuteOfDayTest {

    @Test
    fun parseNormalizesHourAndMinute() {
        assertEquals(8 * 60 + 30, MinuteOfDay.parse("08:30"))
        assertEquals(8 * 60 + 5, MinuteOfDay.parse("8:5"))
        assertEquals(10 * 60, MinuteOfDay.parse(" 10:00 "))
        assertEquals(23 * 60 + 59, MinuteOfDay.parse("23:59"))
        assertEquals(0, MinuteOfDay.parse("00:00"))
    }

    @Test
    fun parseRejectsMalformedOrOutOfRange() {
        assertNull(MinuteOfDay.parse(""))
        assertNull(MinuteOfDay.parse("   "))
        assertNull(MinuteOfDay.parse("abc"))
        assertNull(MinuteOfDay.parse("8"))
        assertNull(MinuteOfDay.parse("24:00"))
        assertNull(MinuteOfDay.parse("10:60"))
        assertNull(MinuteOfDay.parse("10-30"))
        assertNull(MinuteOfDay.parse("8:5:3"))
    }

    @Test
    fun formatPadsToHhMm() {
        assertEquals("08:30", MinuteOfDay.format(8 * 60 + 30))
        assertEquals("00:00", MinuteOfDay.format(0))
        assertEquals("23:59", MinuteOfDay.format(23 * 60 + 59))
    }

    @Test
    fun formatOfNoneIsEmpty() {
        assertEquals("", MinuteOfDay.format(MinuteOfDay.NONE))
        assertEquals("", MinuteOfDay.format(-2))
    }

    @Test
    fun isValidExcludesSentinelAndOverflow() {
        assertFalse(MinuteOfDay.isValid(MinuteOfDay.NONE))
        assertTrue(MinuteOfDay.isValid(0))
        assertTrue(MinuteOfDay.isValid(23 * 60 + 59))
        assertFalse(MinuteOfDay.isValid(24 * 60))
    }

    @Test
    fun parseAndFormatRoundTrip() {
        listOf("00:00", "07:45", "12:00", "18:30", "23:59").forEach { text ->
            assertEquals(text, MinuteOfDay.format(MinuteOfDay.parse(text)!!))
        }
    }
}
