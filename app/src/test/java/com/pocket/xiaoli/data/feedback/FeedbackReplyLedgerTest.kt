package com.pocket.xiaoli.data.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 回复通知的去重账：同一句话只响一次，正文变了再响。
 *
 * 这本账存在 prefs 里（`notified_replies`），App 重启不清空 —— 否则每次冷启动
 * 都会把上周那条回复再提醒一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackReplyLedgerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)
    private val store get() = FeedbackStore(context)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `记过就不算新回复`() {
        store.markSent("T1")
        assertTrue(store.notifiedReplies().isEmpty())

        store.markReplyNotified("T1", "已经修了")
        assertEquals(mapOf("T1" to "已经修了"), store.notifiedReplies())

        val replies = mapOf("T1" to FeedbackReply("已经修了", "10-30 10:00"))
        assertEquals(null, FeedbackReplyNotice.newest(replies, store.notifiedReplies()))
    }

    @Test
    fun `正文改了会重新弹出`() {
        store.markSent("T1")
        store.markReplyNotified("T1", "已经修了")
        val replies = mapOf("T1" to FeedbackReply("已经修了，今晚发版", "10-31 09:00"))
        assertEquals("T1", FeedbackReplyNotice.newest(replies, store.notifiedReplies())?.first)
    }

    @Test
    fun `账本只留还在的工单`() {
        // 账本里留一条已经不存在的工单（模拟「清过数据/换过手机」留下的孤儿）
        prefs.edit()
            .putString("sent", "[]")
            .putString("notified_replies", "{\"T9\":\"旧答复\"}")
            .commit()
        store.markSent("T1")
        store.markReplyNotified("T1", "新答复")

        // 新记录写进去，孤儿 T9 被裁掉
        assertEquals(setOf("T1"), store.notifiedReplies().keys)
    }
}
