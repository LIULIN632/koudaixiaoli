package com.pocket.xiaoli.telemetry

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 上报器的行为断言。
 *
 * 为什么值得为六行 if 写测试：这里每个返回值都决定了**本地队列丢不丢**。
 * 判错一次的后果不是"少一条统计"，而是要么把该留的事件丢了，
 * 要么让一条服务端永远不认的数据堵在队头，把后面所有事件一起饿死。
 */
class TelemetryUploaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun uploader(baseUrl: String = server.url("/").toString(), key: String = "test-key") =
        TelemetryUploader(baseUrl, key)

    private val events = listOf("""{"n":"app_open","t":1700000000000,"eid":"e-1"}""")

    @Test
    fun `没配地址时一个请求都不发`() {
        val uploader = TelemetryUploader("", "")

        assertFalse("baseUrl 与 key 都空时必须自认未配置", uploader.configured)
        assertEquals("未配置时调用 upload 应当直接返回，不做网络请求", true, uploader.upload("iid", 72, 34, events))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `只配了地址没配 key 也算未配置`() {
        val uploader = TelemetryUploader(server.url("/").toString(), "")

        assertFalse(uploader.configured)
        uploader.upload("iid", 72, 34, events)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `成功时发到 v1-batch 并带上 App-Key`() {
        server.enqueue(MockResponse().setResponseCode(204))

        val accepted = uploader().upload("iid-1", 72, 34, events)

        assertTrue(accepted)
        val request = server.takeRequest()
        assertEquals("/v1/batch", request.path)
        assertEquals("test-key", request.getHeader("X-App-Key"))
        assertEquals("POST", request.method)
    }

    @Test
    fun `请求体里的字段与服务端约定一致`() {
        server.enqueue(MockResponse().setResponseCode(204))

        val uploader = uploader()
        uploader.upload("iid-1", 72, 34, events)
        val body = server.takeRequest().body.readUtf8()

        assertTrue(body.contains("\"v\":1"))
        assertTrue(body.contains("\"iid\":\"iid-1\""))
        assertTrue(body.contains("\"vc\":72"))
        assertTrue(body.contains("\"os\":34"))
        assertTrue(body.contains("\"app_open\""))
    }

    @Test
    fun `限流返回 false 让事件留在队列里`() {
        server.enqueue(MockResponse().setResponseCode(429))

        assertFalse(uploader().upload("iid", 72, 34, events))
    }

    @Test
    fun `服务端说这批发不进去时丢掉而不是重试`() {
        // 400 = 整批校验不过（比如事件名拼错）。无限重试只会永远堵在队头。
        server.enqueue(MockResponse().setResponseCode(400))

        assertTrue(uploader().upload("iid", 72, 34, events))
    }

    @Test
    fun `5xx 保留重试`() {
        server.enqueue(MockResponse().setResponseCode(503))

        assertFalse(uploader().upload("iid", 72, 34, events))
    }

    @Test
    fun `连不上时不抛异常只返回 false`() {
        server.shutdown()

        assertFalse(uploader().upload("iid", 72, 34, events))
    }

    @Test
    fun `空队列不发请求`() {
        assertTrue(uploader().upload("iid", 72, 34, emptyList()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `两个条件缺一个就不记账`() {
        // Telemetry.track 的第一道闸；两个条件在语义上是「用户允许」× 「服务器配置好了」
        assertFalse(Telemetry.shouldRecord(enabled = false, configured = true))
        assertFalse(Telemetry.shouldRecord(enabled = true, configured = false))
        assertFalse(Telemetry.shouldRecord(enabled = false, configured = false))
        assertTrue(Telemetry.shouldRecord(enabled = true, configured = true))
    }

    @Test
    fun `F-Droid 构建里即使开关全开也不记账`() {
        // `-Pfdroid=true` 的包：`Telemetry.init` 直接 return，这里的闸门是第二道保险。
        // 上架 F-Droid 不想要 Tracking 反特性，所以这一条必须永远为 false。
        assertFalse(Telemetry.shouldRecord(enabled = true, configured = true, fdroid = true))
        assertFalse(Telemetry.shouldRecord(enabled = true, configured = false, fdroid = true))
        assertFalse(Telemetry.shouldRecord(enabled = false, configured = true, fdroid = true))
        // 普通构建不受影响
        assertTrue(Telemetry.shouldRecord(enabled = true, configured = true, fdroid = false))
    }
}
