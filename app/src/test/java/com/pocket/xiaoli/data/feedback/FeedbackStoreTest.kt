package com.pocket.xiaoli.data.feedback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 反馈本地账本的增量部分：提交闸门、待发队列、匿名标识、回复缓存。
 *
 * 这一层坏了都不报错：闸门坏了会被刷表，队列坏了会丢用户写的话，
 * 节流坏了是在烧飞书免费版的月配额。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedbackStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)
    private val store get() = FeedbackStore(context)

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `冷却与每日上限落盘`() {
        val now = System.currentTimeMillis()
        assertEquals(SubmitGate.Ok, store.submitGate(now))

        store.markSubmitAttempt(now)
        val gate = store.submitGate(now + 30_000)
        assertEquals(SubmitGate.Cooling(30), gate)

        // 冷却过了，但今天才第 1 条，放行
        assertEquals(SubmitGate.Ok, store.submitGate(now + 61_000))

        repeat(4) { store.markSubmitAttempt(now + 61_000 + it * 1_000) }
        assertEquals(SubmitGate.DailyLimit, store.submitGate(now + 200_000))
    }

    @Test
    fun `待发队列存全文 满五条拒绝`() {
        (1..FeedbackLimits.MAX_PENDING).forEach { assertTrue(store.enqueue(pending("T26052400000$it"))) }
        assertFalse(store.enqueue(pending("T260524000009")))
        assertEquals(FeedbackLimits.MAX_PENDING, store.pending().size)

        // 换行必须活着 —— 补发时丢字就等于用户白写
        assertEquals("第一条\n第二行", store.pending().first().content)

        store.removePending("T260524000001")
        assertEquals(FeedbackLimits.MAX_PENDING - 1, store.pending().size)
    }

    @Test
    fun `被表格拒收的项标记后仍留在队列`() {
        val id = "T26052400ABCD"
        store.enqueue(pending(id))
        store.markBlocked(id)
        val item = store.pending().single()
        assertTrue(item.blocked)
        assertEquals(id, item.ticketId)
    }

    @Test
    fun `匿名标识稳定且八位十六进制`() {
        val first = store.anonId()
        assertEquals(first, store.anonId())
        assertTrue("实际：$first", Regex("^[0-9a-f]{8}$").matches(first))
    }

    @Test
    fun `回复缓存按工单合并且不留下已出账的工单`() {
        store.markSent("T1")
        store.markSent("T2")
        store.cacheReplies(mapOf("T1" to FeedbackReply("收到", "2026-10-24 10:00")))
        store.cacheReplies(mapOf("T2" to FeedbackReply("已修", "")))
        assertEquals(2, store.cachedReplies().size)

        // 不在「已送达」里的工单不缓存：这份缓存是给「我提过的」渲染的
        store.cacheReplies(mapOf("T9" to FeedbackReply("路人", "")))
        assertFalse(store.cachedReplies().containsKey("T9"))
    }

    @Test
    fun `有未回复十分钟一次 全回复六小时一次`() {
        val now = System.currentTimeMillis()
        store.markRepliesFetched(now)

        assertFalse(store.shouldFetchReplies(now + 60_000, hasPending = true))
        assertTrue(store.shouldFetchReplies(now + 11 * 60_000, hasPending = true))

        assertFalse(store.shouldFetchReplies(now + 60 * 60_000, hasPending = false))
        assertTrue(store.shouldFetchReplies(now + 7 * 60 * 60_000, hasPending = false))

        // 手动检查绕过节流
        assertTrue(store.shouldFetchReplies(now, hasPending = false, force = true))
    }

    private fun pending(id: String) = FeedbackStore.Pending(
        ticketId = id,
        category = FeedbackCategory.BUG,
        content = "第一条\n第二行",
        contact = "a@b.com",
        includeDiagnostics = true,
        atMillis = 1L,
        diagnostics = "版本 0.1.85 (86)\n机型 Xiaomi\n系统 Android 14 (API 34)",
    )
}
