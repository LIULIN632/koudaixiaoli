package com.pocket.xiaoli.data.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「哪条回复该弹通知」的判定。
 *
 * 这条判据坏了有两种坏法，都很难被用户发现：
 * - 判宽了：每次轮询都响一遍，用户会去系统里关掉通知；
 * - 判窄了：回复永远不响，等于这功能没做。
 */
class FeedbackReplyNoticeTest {

    private fun reply(text: String, at: String) = FeedbackReply(text, at)

    @Test
    fun `没通知过的回复会被挑出来`() {
        val replies = mapOf("T1" to reply("已经修了，下版生效", "10-30 10:00"))
        val picked = FeedbackReplyNotice.newest(replies, emptyMap())
        assertEquals("T1", picked?.first)
        assertEquals("已经修了，下版生效", picked?.second?.text)
    }

    @Test
    fun `同一句话不重复通知`() {
        val replies = mapOf("T1" to reply("已经修了", "10-30 10:00"))
        assertNull(FeedbackReplyNotice.newest(replies, mapOf("T1" to "已经修了")))
    }

    @Test
    fun `回复被改写过要再通知一次`() {
        val replies = mapOf("T1" to reply("已经修了，下版生效", "10-31 09:00"))
        val picked = FeedbackReplyNotice.newest(replies, mapOf("T1" to "已经修了"))
        assertEquals("已经修了，下版生效", picked?.second?.text)
    }

    @Test
    fun `多条时挑最新的那条`() {
        val replies = mapOf(
            "T1" to reply("旧答复", "10-28 09:00"),
            "T2" to reply("新答复", "10-31 20:00"),
        )
        assertEquals("T2", FeedbackReplyNotice.newest(replies, emptyMap())?.first)
    }

    @Test
    fun `正文压成一行并截断`() {
        assertEquals("第一行 第二行", FeedbackReplyNotice.preview("第一行\n第二行"))
        val long = "字".repeat(FeedbackReplyNotice.MAX_PREVIEW + 20)
        val preview = FeedbackReplyNotice.preview(long)
        assertEquals(FeedbackReplyNotice.MAX_PREVIEW + 1, preview.length)
        assertEquals("…", preview.takeLast(1))
    }
}
