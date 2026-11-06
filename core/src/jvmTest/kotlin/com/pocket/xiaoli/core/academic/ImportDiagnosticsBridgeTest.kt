package com.pocket.xiaoli.core.academic

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * 导入诊断出口的测试。
 *
 * 三件必须钉住的事：
 * 1. 密码这类凭据**不能**落盘（表单与 URL 查询串都要打码）；
 * 2. 拦截器不能把响应正文吃掉 —— 调用方还要再读一次来解析课表；
 * 3. 开关没打开时不产生任何回调（手机端与桌面默认状态都是零开销）。
 */
class ImportDiagnosticsBridgeTest {

    private val server = MockWebServer()

    @AfterTest
    fun tearDown() {
        ImportDiagnosticsBridge.onExchange = null
        runCatching { server.shutdown() }
    }

    @Test
    fun `secret form fields are masked and other keys untouched`() {
        val redacted = ImportDiagnosticsBridge.redact(
            "yhm=202408501222&mm=SuperSecret123&password=&xnm=2026&xqm=3&pwd2=keepme"
        )
        assertTrue("mm=SuperSecret123" !in redacted, "密码必须打码：$redacted")
        assertTrue("password=SuperSecret" !in redacted)
        assertContains(redacted, "yhm=202408501222")
        assertContains(redacted, "xnm=2026&xqm=3")
        // pwd2 不是密码字段（按整键名匹配，不做前缀匹配）
        assertContains(redacted, "pwd2=keepme")
    }

    @Test
    fun `masking keeps value length and pairs without equals`() {
        val redacted = ImportDiagnosticsBridge.redact("mm=123456&flag")
        assertContains(redacted, "mm=«已打码(6 字符)»")
        assertContains(redacted, "&flag")
    }

    @Test
    fun `body is clamped instead of dumped whole`() {
        val huge = "x".repeat(ImportDiagnosticsBridge.MAX_BODY_CHARS + 1000)
        val clamped = ImportDiagnosticsBridge.clampBody(huge)
        assertTrue(clamped.length < huge.length)
        assertContains(clamped, "截断，共 ${huge.length} 字符")
    }

    @Test
    fun `exchange captured with redirect chain and caller can still read body`() = runBlocking {
        val captured = mutableListOf<AcademicExchange>()
        ImportDiagnosticsBridge.onExchange = { captured += it }
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/jwglxt/xtgl/login_slogin.html"))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<html><title>学生个人课表</title><div class=\"timetable_con\">…</div></html>")
        )
        server.start()
        val base = server.url("/jwglxt").toString().trimEnd('/')
        val client = ZhengfangClient()
        val body = client.post("$base/xtgl/login_slogin.html", "yhm=202408501222&mm=SuperSecret123").use {
            it.body?.string().orEmpty()
        }

        // 2) 正文没被诊断拦截器消耗
        assertContains(body, "学生个人课表")
        // 3) 只记最后一段响应，但重定向链保留
        assertEquals(1, captured.size)
        val exchange = captured.single()
        assertEquals("POST", exchange.method)
        assertEquals(200, exchange.status)
        assertEquals(1, exchange.redirectChain.size, "应记录 302 那一跳")
        assertContains(exchange.requestForm, "mm=«已打码")
        assertTrue("SuperSecret123" !in exchange.requestForm)
        assertContains(exchange.body, "timetable_con")
        // URL 里也不留凭据
        assertTrue("SuperSecret123" !in exchange.url)
    }

    @Test
    fun `no capture when the switch is off`() = runBlocking {
        ImportDiagnosticsBridge.onExchange = null
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()
        val base = server.url("/jwglxt").toString().trimEnd('/')
        val client = ZhengfangClient()
        val text = client.get("$base/kbcx/xskbcx_cxXsgrkb.html").use { it.body?.string().orEmpty() }
        assertEquals("ok", text)
        assertNull(ImportDiagnosticsBridge.onExchange)
        assertTrue(!ImportDiagnosticsBridge.active)
    }
}
