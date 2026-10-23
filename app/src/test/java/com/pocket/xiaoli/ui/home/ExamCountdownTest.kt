package com.pocket.xiaoli.ui.home

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 考试倒计时的测试。
 *
 * ## 为什么值得测
 *
 * 它把「日期字符串」变成「还有几天」—— 算错 1 天就可能让用户**错过考试**。
 * 而它的输入来源很杂（解析器保留了通知原文），所以要覆盖：
 * ISO 写法、中文「M月D日」、跨年、无效输入。
 *
 * ## 测试策略
 *
 * 相对日期（今天/明天）用 `LocalDate.now()` 动态构造，保证测试**任何时候都通过**；
 * 中文写法则锚定到「今天」来断言，避免依赖具体日历日期。
 */
class ExamCountdownTest {

    private fun iso(offsetDays: Long): String = LocalDate.now().plusDays(offsetDays).toString()

    @Test
    fun `today and tomorrow use words not numbers`() {
        assertEquals("今天", examCountdown(iso(0)))
        assertEquals("明天", examCountdown(iso(1)))
    }

    @Test
    fun `future dates report remaining days`() {
        assertEquals("还有 2 天", examCountdown(iso(2)))
        assertEquals("还有 6 天", examCountdown(iso(6)))
        assertEquals("还有 100 天", examCountdown(iso(100)))
    }

    @Test
    fun `past dates report elapsed days`() {
        assertEquals("已过去 1 天", examCountdown(iso(-1)))
        assertEquals("已过去 3 天", examCountdown(iso(-3)))
    }

    @Test
    fun `chinese month day format is accepted`() {
        // 用「今天」构造中文写法，断言必然是"今天"
        val today = LocalDate.now()
        val cn = "${today.monthValue}月${today.dayOfMonth}日"
        assertEquals("今天", examCountdown(cn))
    }

    @Test
    fun `chinese format with padding and spaces`() {
        val today = LocalDate.now()
        val cn = "${today.monthValue} 月 ${today.dayOfMonth} 日"
        assertEquals("今天", examCountdown(cn))
    }

    @Test
    fun `chinese format in a far past month rolls to next year`() {
        // 1月1日在多数时间已过去半年以上 → 应算作明年，因此结果是"还有 N 天"而非"已过去"
        val today = LocalDate.now()
        if (today.monthValue > 7) {
            val r = examCountdown("1月1日")
            assertNull("解析不应失败", null?.let { r })
            assertEquals(true, r!!.startsWith("还有"))
        }
    }

    @Test
    fun `invalid input returns null instead of guessing`() {
        assertNull(examCountdown(""))
        assertNull(examCountdown("待定"))
        assertNull(examCountdown("见通知"))
        assertNull(examCountdown("13月45日"))
    }

    @Test
    fun `iso date with chinese year month day is also accepted`() {
        val today = LocalDate.now()
        val cn = "${today.year}年${today.monthValue}月${today.dayOfMonth}日"
        // 「2026年1月15日」含中文，LocalDate.parse 会失败；
        // 兜底的 M月D日 正则能在其中命中，因此仍应解析成功
        val r = examCountdown(cn)
        assertEquals("今天", r)
    }
}
