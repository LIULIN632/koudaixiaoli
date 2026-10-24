package com.pocket.xiaoli.data.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提交闸门的纯函数部分：冷却、每日上限、正文长度。 */
class FeedbackLimitsTest {

    @Test
    fun `刚提交过的六十秒内冷却`() {
        val now = 1_000_000L
        val gate = FeedbackLimits.gate(now, lastSubmitAt = now - 30_000, todayCount = 0)
        assertEquals(SubmitGate.Cooling(30), gate)
    }

    @Test
    fun `满六十秒放行`() {
        assertEquals(
            SubmitGate.Ok,
            FeedbackLimits.gate(2_000_000L, lastSubmitAt = 2_000_000L - 60_000L, todayCount = 0),
        )
    }

    @Test
    fun `当天第五条之后拦住`() {
        assertEquals(SubmitGate.Ok, FeedbackLimits.gate(2_000_000L, 0L, todayCount = 4))
        assertEquals(SubmitGate.DailyLimit, FeedbackLimits.gate(2_000_000L, 0L, todayCount = 5))
    }

    @Test
    fun `正文上限两千字`() {
        assertTrue(FeedbackDraft(content = "字".repeat(2000)).isSendable)
        assertFalse(FeedbackDraft(content = "字".repeat(2001)).isSendable)
        assertFalse(FeedbackDraft(content = "坏了呢").isSendable)
    }
}
