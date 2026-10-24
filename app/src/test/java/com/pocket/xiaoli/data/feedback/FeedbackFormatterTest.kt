package com.pocket.xiaoli.data.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 反馈正文拼装器的单测。
 *
 * 守的是三件事：**工单号能认**（日期 + 尾号）、**正文内容不越权**（没勾设备信息就
 * 一个字都不带）、**三个渠道的链接不炸**（换行与中文都编码干净）。
 */
class FeedbackFormatterTest {

    private val draft = FeedbackDraft(
        category = FeedbackCategory.BUG,
        content = "课表页下拉刷新之后，第二节课的名字变成了上节课的\n重启也还在",
        contact = "xly@example.com",
        includeDiagnostics = false,
    )

    private val diagnostics = "版本 口袋小鲤 0.1.58 (59)\n机型 Xiaomi 23127PN0CC\n系统 Android 14 (API 34)"

    private val baseMillis = 1_792_262_400_000L // 2026-10-18 前后，固定值便于断言

    @Test
    fun `工单号是 T 加日期加六位随机`() {
        val id = FeedbackFormatter.ticketId(baseMillis, random = 0x00A1B2)
        assertTrue("实际：$id", Regex("^T\\d{6}[0-9A-F]{6}$").matches(id))
        assertTrue("实际：$id", id.endsWith("00A1B2"))
    }

    @Test
    fun `同一个输入稳定 同一时间同一随机数给同一个号`() {
        val a = FeedbackFormatter.ticketId(baseMillis, 7)
        val b = FeedbackFormatter.ticketId(baseMillis, 7)
        assertEquals(a, b)
    }

    @Test
    fun `六位随机决定尾号`() {
        val ids = (1..8).map { FeedbackFormatter.ticketId(baseMillis, it) }.toSet()
        assertEquals(8, ids.size)
    }

    @Test
    fun `没勾设备信息时正文里一个字都不带`() {
        val body = FeedbackFormatter.buildBody(draft, "T26101800A1B2", baseMillis, diagnostics)
        assertFalse(body.contains("Xiaomi"))
        assertFalse(body.contains("Android 14"))
        assertTrue(body.contains("【功能坏了】"))
        assertTrue(body.contains("xly@example.com"))
    }

    @Test
    fun `勾了设备信息才带上环境`() {
        val body = FeedbackFormatter.buildBody(
            draft.copy(includeDiagnostics = true),
            "T26101800A1B2",
            baseMillis,
            diagnostics,
        )
        assertTrue(body.contains("Xiaomi 23127PN0CC"))
        assertTrue(body.contains("API 34"))
    }

    @Test
    fun `正文末尾写工单号 方便对照`() {
        val body = FeedbackFormatter.buildBody(draft, "T261018ABCDEF", baseMillis, diagnostics)
        assertTrue(body.contains("工单 T261018ABCDEF"))
    }

    @Test
    fun `五个字才允许发送 空白不算字`() {
        assertFalse(FeedbackDraft(content = "坏了呢").isSendable)
        assertFalse(FeedbackDraft(content = "     ").isSendable)
        assertTrue(FeedbackDraft(content = "首页白屏了").isSendable)
    }

    @Test
    fun `issue 链接把换行与中文都编码掉`() {
        val url = FeedbackFormatter.issueUrl("LIULIN632/koudaixiaoli", draft, "T26101800A1B2", baseMillis, diagnostics)
        assertTrue(url.startsWith("https://github.com/LIULIN632/koudaixiaoli/issues/new?title="))
        assertTrue(url.contains("labels=feedback"))
        assertFalse("裸空格会把链接截断", url.contains(" "))
        assertTrue(url.contains("%0A"))
        assertTrue(url.contains("%E5%8F%8D%E9%A6%88")) // 「反馈」的 UTF-8 编码
    }

    @Test
    fun `邮件链接的主题与正文都编码`() {
        val url = FeedbackFormatter.mailtoUrl("a@b.com", draft, "T26101800A1B2", baseMillis, diagnostics)
        assertTrue(url.startsWith("mailto:a@b.com?subject="))
        assertTrue(url.contains("&body="))
        assertFalse(url.contains("\n"))
    }

    @Test
    fun `issue 标题取正文第一行并截断到 40 字`() {
        val long = draft.copy(content = "一".repeat(80) + "\n第二行")
        val title = FeedbackFormatter.issueTitle(long)
        assertTrue(title.startsWith("[反馈] 功能坏了："))
        assertEquals("[反馈] 功能坏了：".length + 40, title.length)
    }

    @Test
    fun `未知分类文案落到其它`() {
        assertEquals(FeedbackCategory.OTHER, FeedbackCategory.fromLabel("随便"))
        assertEquals(FeedbackCategory.UX, FeedbackCategory.fromLabel("用着别扭"))
    }
}
