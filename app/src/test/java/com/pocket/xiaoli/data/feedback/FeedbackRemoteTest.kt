package com.pocket.xiaoli.data.feedback

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 请求体拼装、结果分级与回复解析 —— 全是纯函数，不碰网络。 */
class FeedbackRemoteTest {

    private val draft = FeedbackDraft(
        category = FeedbackCategory.BUG,
        content = "  课表页白屏  ",
        contact = "",
        includeDiagnostics = false,
    )

    private val diagnostics = "版本 口袋小鲤 0.1.89 (90)\n" +
        "机型 Xiaomi 23127PN0CC\n" +
        "系统 Android 14 (API 34)"

    @Test
    fun `请求体只带服务端认识的字段 没勾设备就不带`() {
        val json = JsonParser.parseString(
            FeedbackRemote.submitJson(draft, "T26102400A1B2", diagnostics, "deadbeef"),
        ).asJsonObject
        assertEquals("T26102400A1B2", json["ticket"].asString)
        assertEquals("BUG", json["category"].asString)
        assertEquals("课表页白屏", json["content"].asString) // 首尾空白被去掉
        assertTrue(json["contact"].isJsonNull)
        assertEquals("deadbeef", json["iid"].asString)
        // 没勾「附带设备信息」：一个字节的机型/系统都不带
        assertTrue(json["device"].isJsonNull)
        assertTrue(json["os"].isJsonNull)
    }

    @Test
    fun `勾选设备信息才带机型与系统`() {
        val json = JsonParser.parseString(
            FeedbackRemote.submitJson(
                draft.copy(includeDiagnostics = true),
                "T26102400A1B2",
                diagnostics,
                "deadbeef",
            ),
        ).asJsonObject
        assertEquals("Xiaomi 23127PN0CC", json["device"].asString)
        assertEquals("Android 14 (API 34)", json["os"].asString)
    }

    @Test
    fun `设备信息从用户看到的那行文本里取`() {
        assertEquals("Xiaomi 23127PN0CC", FeedbackRemote.deviceOf(diagnostics))
        assertEquals("Android 14 (API 34)", FeedbackRemote.osOf(diagnostics))
        assertNull(FeedbackRemote.deviceOf(null))
        assertNull(FeedbackRemote.osOf("只有一行别的东西"))
    }

    @Test
    fun `联系方式留空时写成 null 而不是空串`() {
        val filled = draft.copy(contact = " a@b.c ")
        val json = JsonParser.parseString(
            FeedbackRemote.submitJson(filled, "T26102400A1B2", null, "deadbeef"),
        ).asJsonObject
        assertEquals("a@b.c", json["contact"].asString)
    }

    @Test
    fun `网络与服务端故障可重试 配置错与内容问题直接拒收`() {
        assertEquals(SubmitOutcome.Sent, FeedbackRemote.classify(200))
        assertEquals(SubmitOutcome.Sent, FeedbackRemote.classify(204))
        assertTrue(FeedbackRemote.classify(429) is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(500) is SubmitOutcome.Retryable)
        assertTrue(FeedbackRemote.classify(503) is SubmitOutcome.Retryable)
        // 401/403 是地址或 App-Key 配错了，重试一万次也不会通
        assertTrue(FeedbackRemote.classify(401) is SubmitOutcome.Rejected)
        assertTrue(FeedbackRemote.classify(403) is SubmitOutcome.Rejected)
        assertTrue(FeedbackRemote.classify(400) is SubmitOutcome.Rejected)
        assertTrue(FeedbackRemote.classify(413) is SubmitOutcome.Rejected)
    }

    @Test
    fun `回复列表按工单号索引 空回复被跳过`() {
        val json = """
            {"replies":[
              {"ticket":"T26102400A1B2","reply":"已修好","repliedAt":"2026-10-24T08:00:00Z"},
              {"ticket":"T26102400C3D4","reply":"","repliedAt":"2026-10-24T09:00:00Z"}
            ]}
        """.trimIndent()
        val map = FeedbackRemote.parseReplies(json)
        assertEquals(1, map.size)
        assertEquals("已修好", map["T26102400A1B2"]?.text)
        // 时间转成本地时区显示，不是原样吐 UTC
        assertTrue(map["T26102400A1B2"]!!.at.matches(Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")))
    }

    @Test
    fun `坏 JSON 不抛异常 只返回空表`() {
        assertTrue(FeedbackRemote.parseReplies("not json").isEmpty())
        assertTrue(FeedbackRemote.parseReplies("""{"replies":[]}""").isEmpty())
        assertEquals("", FeedbackRemote.formatTime(null))
        assertEquals("2026-10-24", FeedbackRemote.formatTime("2026-10-24")) // 解析不了就原样
    }

    /**
     * 2026-10-25 的真实事故：feed 里填的是 `https://host/v1/feedback`，
     * 代码又拼了一次 `/v1/feedback/submit`，线上日志是
     * `POST /v1/feedback/v1/feedback/submit → 404`。
     * 所以基址归一化必须把「已经带了接口路径」的写法收掉。
     */
    @Test
    fun `基址归一化 吃掉各种写法`() {
        assertEquals("https://h", FeedbackRemote.baseOf("https://h"))
        assertEquals("https://h", FeedbackRemote.baseOf("https://h/"))
        assertEquals("https://h", FeedbackRemote.baseOf("https://h/v1/feedback"))
        assertEquals("https://h", FeedbackRemote.baseOf("https://h/v1/feedback/"))
        assertEquals("https://h", FeedbackRemote.baseOf("  https://h/v1/feedback/submit  "))
        assertEquals("https://h", FeedbackRemote.baseOf("https://h/v1/feedback/replies"))
        assertEquals("https://h/v1", FeedbackRemote.baseOf("https://h/v1")) // 不是接口路径就不动
        assertEquals("http://10.0.0.2:8080", FeedbackRemote.baseOf("http://10.0.0.2:8080/v1/feedback"))
    }

    @Test
    fun `基址归一化 非法值返回 null`() {
        assertNull(FeedbackRemote.baseOf(null))
        assertNull(FeedbackRemote.baseOf(""))
        assertNull(FeedbackRemote.baseOf("   "))
        assertNull(FeedbackRemote.baseOf("javascript:alert(1)"))
        assertNull(FeedbackRemote.baseOf("/v1/feedback")) // 相对路径没有主机
        assertNull(FeedbackRemote.baseOf("ftp://h/v1/feedback"))
    }

    /**
     * 真正发给服务端的地址：基址 + 一次接口路径。
     * 这条断言就是防回归——上一个 bug 正是这里多了/少了一次路径。
     */
    @Test
    fun `提交地址只拼一次接口路径`() {
        assertEquals(
            "https://h/v1/feedback/submit",
            FeedbackRemote.urlFor(base = FeedbackRemote.baseOf("https://h/v1/feedback")!!, path = "/v1/feedback/submit"),
        )
        assertEquals(
            "https://h",
            FeedbackRemote.baseOf("https://h/v1/feedback")!!,
        )
    }
}
