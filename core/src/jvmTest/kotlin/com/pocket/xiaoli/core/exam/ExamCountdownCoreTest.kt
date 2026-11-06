package com.pocket.xiaoli.core.exam

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 共享倒计时的测试：手机端首页/气泡与桌面端考试页/托盘提醒都读这一份，
 * 所以算错 1 天会同时错两处。覆盖 ISO、中文「M月D日」、跨年与无效输入。
 */
class ExamCountdownCoreTest {

    private val today = LocalDate.of(2026, 6, 10)

    @Test
    fun `iso dates count days between`() {
        assertEquals(0L, ExamCountdown.daysLeft("2026-06-10", today))
        assertEquals(3L, ExamCountdown.daysLeft("2026-06-13", today))
        assertEquals(-2L, ExamCountdown.daysLeft("2026-06-08", today))
    }

    @Test
    fun `month day without year uses this year`() {
        assertEquals(LocalDate.of(2026, 1, 15), ExamCountdown.parseDate("1月15日", today))
    }

    @Test
    fun `a month day more than half a year back rolls to next year`() {
        // 8 月底看到「1月15日」：早已过去半年以上，那是 12 月发的通知考的明年 1 月
        assertEquals(LocalDate.of(2027, 1, 15), ExamCountdown.parseDate("1月15日", LocalDate.of(2026, 8, 30)))
        // 半年以内仍按今年算（"已过去的考试"该显示"已过去 N 天"，不该跳到明年）
        assertEquals(LocalDate.of(2026, 1, 15), ExamCountdown.parseDate("1月15日", LocalDate.of(2026, 6, 30)))
        // 而 1 月看到「1月15日」当然是今年
        assertEquals(LocalDate.of(2026, 1, 15), ExamCountdown.parseDate("1月15日", LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `invalid or missing dates return null instead of guessing`() {
        assertNull(ExamCountdown.parseDate("", today))
        assertNull(ExamCountdown.parseDate("待定", today))
        assertNull(ExamCountdown.parseDate("13月45日", today))
        assertNull(ExamCountdown.daysLeft("见通知", today))
    }

    @Test
    fun `countdown wording is fixed for the ui and the tray bubble`() {
        assertEquals("今天", ExamCountdown.countdownText(0))
        assertEquals("明天", ExamCountdown.countdownText(1))
        assertEquals("还有 6 天", ExamCountdown.countdownText(6))
        assertEquals("已过去 1 天", ExamCountdown.countdownText(-1))
        assertEquals("已过去 4 天", ExamCountdown.countdownText(-4))
    }

    @Test
    fun `soon window is the single source both ends read`() {
        assertTrue(ExamCountdown.SOON_DAYS > 0)
        assertEquals(3, ExamCountdown.SOON_DAYS)
    }
}
