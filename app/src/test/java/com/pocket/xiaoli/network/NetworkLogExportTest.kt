package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 导出日志的脱敏（2026-10-23 审计修的一处真泄露）。
 *
 * ## 背景
 *
 * 「我的 → 导出日志」会把内存里的网络日志写成 txt 交给用户，用户通常会把它发到群里 / 发给开发者。
 * 旧实现直接拼 `entry.url`，而脱敏函数 `redactUrl` 只用在下方的「复制 cURL」上 ——
 * 教务 CAS 的 `ticket=ST-…`、学习通的 `token=…` 就这样跟着文件离开了设备。
 *
 * 现在统一走 [NetworkLogStore.exportLines]，这个文件钉住三件事：
 * 1. 敏感参数的值变成 `***`；
 * 2. **原始值不出现在导出文本里**（不是"看起来像脱敏"）；
 * 3. 路径与参数名仍保留（不然日志就没法排障了）。
 */
class NetworkLogExportTest {

    @Before
    fun setUp() {
        NetworkLogStore.resetForTest()
    }

    @Test
    fun `导出行里的敏感查询参数被打码`() {
        NetworkLogStore.record(
            method = "GET",
            url = "https://jwxt.yitsd.edu.cn/cas/login?service=https%3A%2F%2Fjwxt&ticket=ST-123456-abcdef",
            status = 302,
            durationMs = 120,
        )
        NetworkLogStore.record(
            method = "POST",
            url = "https://mooc1.chaoxing.com/api?token=deadbeef&courseId=42",
            status = 200,
            durationMs = 80,
        )

        val lines = NetworkLogStore.exportLines()

        assertEquals(2, lines.size)
        val text = lines.joinToString("\n")
        assertFalse("CAS ticket 泄露：$lines", text.contains("ST-123456-abcdef"))
        assertFalse("学习通 token 泄露：$lines", text.contains("deadbeef"))
        assertTrue("参数名要保留，否则没法排障：$lines", text.contains("ticket=***"))
        assertTrue("参数名要保留：$lines", text.contains("token=***"))
        assertTrue("路径要保留：$lines", text.contains("jwxt.yitsd.edu.cn/cas/login"))
        assertTrue("非敏感参数不该被动：$lines", text.contains("courseId=42"))
    }

    @Test
    fun `没有查询串的 URL 原样保留`() {
        NetworkLogStore.record("GET", "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/feed.json", 200, 90)
        val line = NetworkLogStore.exportLines().single()
        assertTrue(line, line.contains("feed.json"))
        assertFalse(line, line.contains("***"))
    }

    @Test
    fun `失败条目也有状态可读`() {
        NetworkLogStore.record("GET", "https://x.yitsd.edu.cn/a?token=zzz", null, 5000, error = "SocketTimeoutException")
        val line = NetworkLogStore.exportLines().single()
        assertTrue(line, line.contains("ERR"))
        assertFalse(line, line.contains("zzz"))
    }

    @Test
    fun `HTTP 成功但内容被拒时原因要出现在导出里`() {
        // 2026-10-26 那份导出的教训：节假日四个镜像全是 `GET 200`，
        // 而真实情况是"200 但解析后 0 条"。屏幕上能看到 `! 原因`，导出看不到 ——
        // 两边必须同一套信息，否则用户导出来的东西不足以定位。
        NetworkLogStore.record(
            "GET",
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/2026.json",
            200,
            164,
            error = "节假日数据不可用：解析到 0 条",
        )
        val line = NetworkLogStore.exportLines().single()
        assertTrue("状态码仍要显示 200：$line", line.contains("200"))
        assertTrue("要带上被拒的原因：$line", line.contains("解析到 0 条"))
    }
}
