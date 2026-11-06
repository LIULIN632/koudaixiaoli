package com.pocket.xiaoli.core.text

import kotlin.test.Test
import kotlin.test.assertEquals


class ConversationDayLabelCoreTest {

    /** 2026-11-01 12:00 UTC+8（东八区正午，离当天边界足够远）。 */
    private val now = 1_793_505_600_000L

    private fun label(millis: Long, nowMillis: Long = now) =
        conversationDayLabel(millis = millis, nowMillis = nowMillis) { "M月d日" }

    @Test
    fun noTimestampFallsBackToJustNow() {
        assertEquals("刚刚", label(0L))
    }

    @Test
    fun sameDaySaysToday() {
        assertEquals("今天", label(now - 60 * 60 * 1000))
    }

    @Test
    fun yesterdayAndTheDayBefore() {
        val day = 24L * 60 * 60 * 1000
        assertEquals("昨天", label(now - day))
        assertEquals("前天", label(now - 2 * day))
    }

    @Test
    fun olderFallsBackToMonthDay() {
        val day = 24L * 60 * 60 * 1000
        assertEquals("M月d日", label(now - 3 * day))
    }

    @Test
    fun dayBoundaryFollowsOffsetNotUtc() {
        // 2026-11-01 00:30 UTC+8 = 2026-10-31 16:30 UTC。按 UTC 划天会判成"昨天"，
        // 按东八区划天必须是"今天"，这条锁住的就是那个差异。
        val nowAt0030 = 1_793_464_200_000L // 2026-11-01T00:30+08:00
        val todayMorning = 1_793_464_000_000L // 2026-11-01T00:26:40+08:00
        assertEquals("今天", label(todayMorning, nowAt0030))
    }
}
