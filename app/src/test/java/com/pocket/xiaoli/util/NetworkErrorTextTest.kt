package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 断网 / 超时 / 握手失败时**用户看到的那句话**。
 *
 * 对应发布门禁清单「验证断网使用与异常恢复」的验收项之一：
 * **错误提示可理解**。飞行模式下最常见的三种异常必须各自给出可执行的下一步，
 * 而不是把 `java.net.UnknownHostException` 原样丢给用户。
 */
class NetworkErrorTextTest {

    @Test
    fun `域名解析失败提示检查网络`() {
        assertEquals(
            "连不上教务服务，检查网络或接口地址是否正确。",
            NetworkErrorText.networkFailure("教务", UnknownHostException("jwxt.yitsd.edu.cn"))
        )
    }

    @Test
    fun `超时提示稍后再试`() {
        assertEquals(
            "教务响应超时，稍后再试。",
            NetworkErrorText.networkFailure("教务", SocketTimeoutException("timeout"))
        )
    }

    @Test
    fun `SSL 握手失败提示检查协议`() {
        assertEquals(
            "HTTPS 握手失败，检查接口地址是否为 https。",
            NetworkErrorText.networkFailure("教务", SSLException("handshake failed"))
        )
    }

    @Test
    fun `其它 IO 异常带上原始信息`() {
        assertEquals(
            "网络异常：connection reset",
            NetworkErrorText.networkFailure("教务", IOException("connection reset"))
        )
    }

    @Test
    fun `没有 message 的异常退回类名，不能是空白提示`() {
        val text = NetworkErrorText.networkFailure("教务", IOException())
        assertTrue("提示不能是空串：$text", text.isNotBlank())
        assertTrue("没有 message 时应带上类名：$text", text.contains("IOException"))
    }

    @Test
    fun `换一个业务线，文案跟着换`() {
        val text = NetworkErrorText.networkFailure("模型", UnknownHostException("api.deepseek.com"))
        assertEquals("连不上模型服务，检查网络或接口地址是否正确。", text)
    }

    // ── 回归保护：抽出公共实现前，鲤鱼这条线的三条文案是逐字如此的 ──

    @Test
    fun `模型这条线的原有文案逐字未变`() {
        assertEquals(
            "连不上模型服务，检查网络或接口地址是否正确。",
            NetworkErrorText.networkFailure("模型", UnknownHostException("x"))
        )
        assertEquals(
            "模型响应超时，稍后再试。",
            NetworkErrorText.networkFailure("模型", SocketTimeoutException("x"))
        )
        assertEquals(
            "HTTPS 握手失败，检查接口地址是否为 https。",
            NetworkErrorText.networkFailure("模型", SSLException("x"))
        )
    }

    @Test
    fun `三条主要异常互不串台`() {
        val label = "教务"
        val three = listOf(
            NetworkErrorText.networkFailure(label, UnknownHostException("a")),
            NetworkErrorText.networkFailure(label, SocketTimeoutException("b")),
            NetworkErrorText.networkFailure(label, SSLException("c"))
        )
        assertEquals("三种情形的提示必须各不相同", 3, three.toSet().size)
    }
}
