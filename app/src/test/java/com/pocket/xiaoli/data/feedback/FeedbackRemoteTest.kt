package com.pocket.xiaoli.data.feedback

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 提交字段拼装、结果分级与飞书返回值解析 —— 全是纯函数，不碰网络。 */
class FeedbackRemoteTest {

    private val draft = FeedbackDraft(
        category = FeedbackCategory.BUG,
        content = "课表页白屏",
        contact = "",
        includeDiagnostics = false,
    )

    private val diagnostics = "版本 口袋小鲤 0.1.85 (86)\n机型 Xiaomi 23127PN0CC\n系统 Android 14 (API 34)"

    @Test
    fun `提交字段带匿名ID 默认不带环境`() {
        val json = FeedbackRemote.recordJson(draft, "T26052400A1B2", diagnostics, "deadbeef")
        assertTrue(json.contains("\"匿名ID\""))
        assertTrue(json.contains("deadbeef"))
        assertTrue(json.contains("T26052400A1B2"))
        assertFalse(json.contains("环境"))
        assertFalse(json.contains("Xiaomi"))
    }

    @Test
    fun `勾选设备信息才带环境`() {
        val json = FeedbackRemote.recordJson(
            draft.copy(includeDiagnostics = true),
            "T26052400A1B2",
            diagnostics,
            "deadbeef",
        )
        assertTrue(json.contains("环境"))
        assertTrue(json.contains("Xiaomi"))
    }

    @Test
    fun `网络与凭据问题可重试 表格字段问题直接拒收`() {
        assertTrue(FeedbackRemote.classify(401, -1, "") is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(403, -1, "") is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(503, -1, "") is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(200, 99991663, "") is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(200, 1254045, "FieldNameNotFound") is SubmitOutcome.Rejected)
        assertTrue(FeedbackRemote.classify(400, -1, "") is SubmitOutcome.Rejected)
        assertEquals(SubmitOutcome.Sent, FeedbackRemote.classify(200, 0, ""))
    }

    @Test
    fun `富文本数组与毫秒时间戳都能读`() {
        val rich = JsonParser.parseString("""[{"type":"text","text":"已修好"}]""")
        assertEquals("已修好", FeedbackRemote.plainText(rich))

        val date = FeedbackRemote.plainText(JsonParser.parseString("1761271200000"))
        assertTrue("实际：$date", Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$").matches(date))
    }
}
