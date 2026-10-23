package com.pocket.xiaoli.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `ApkDownloader` 的断点续传（2026-10-23）。
 *
 * ## 为什么要用 MockWebServer 而不是只测两个纯函数
 *
 * 续传的价值全在「服务端怎么回」的分支上：**206 追加 / 200 重写 / 416 重来 / 断线留半截**。
 * 这四条只要有一条写反，用户拿到的就是**拼错位的包**（能装，但 SHA 不对），而纯函数测试
 * 一条都测不出来。这里用真 HTTP 服务器把四种响应都演一遍。
 */
class ApkDownloaderResumeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun target(): File = File(tmp.root, "updates/pocket-xiaoli-v1.apk")
    private fun partOf(t: File): File = File(t.path + ".part")

    // ── 纯函数：一条 header、一个总长 ──────────────────────────────

    @Test
    fun `Range 头从已有字节续到末尾`() {
        assertEquals("bytes=0-", rangeHeader(0))
        assertEquals("bytes=1234-", rangeHeader(1234))
    }

    @Test
    fun `Content-Range 能取出文件总长`() {
        assertEquals(150L, parseContentRangeTotal("bytes 100-149/150"))
        assertEquals(2_000L, parseContentRangeTotal("bytes 1000-1999/2000"))
    }

    @Test
    fun `Content-Range 不认识或总长为星号时返回 null`() {
        assertNull(parseContentRangeTotal(null))
        assertNull(parseContentRangeTotal(""))
        assertNull(parseContentRangeTotal("bytes 0-99/*"))
        assertNull(parseContentRangeTotal("bytes 0-99"))
        assertNull(parseContentRangeTotal("garbage"))
    }

    // ── 四种服务端行为 ────────────────────────────────────────────

    @Test
    fun `206 时从已有字节往后写，进度也从已有字节起算`() {
        val target = target()
        target.parentFile!!.mkdirs()
        val first = ByteArray(100) { it.toByte() }
        partOf(target).writeBytes(first)

        val rest = ByteArray(50) { (100 + it).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 100-149/150")
                .setBody(Buffer().write(rest))
        )

        val progress = mutableListOf<DownloadProgress>()
        val error = ApkDownloader().download(server.url("/a.apk").toString(), target) { progress += it }

        assertNull("续传应当成功：$error", error)
        assertEquals(150L, target.length())
        val bytes = target.readBytes()
        assertEquals(100, bytes.size - 50) // 前 100 字节是原来的
        for (i in 0 until 100) assertEquals("已有字节被改写 @$i", first[i], bytes[i])
        for (i in 0 until 50) assertEquals("续传字节不对 @$i", rest[i], bytes[100 + i])

        val request = server.takeRequest()
        assertEquals("bytes=100-", request.getHeader("Range"))
        assertEquals("第一条进度必须从已有字节起算", 100L, progress.first().bytes)
        assertEquals(150L, progress.first().total)
        assertTrue("进度里应出现过 150/150，实际=${progress.map { it.bytes }}", progress.any { it.bytes == 150L })
    }

    @Test
    fun `服务端回 200 整包时必须重写，不能追加`() {
        val target = target()
        target.parentFile!!.mkdirs()
        partOf(target).writeBytes(ByteArray(100) { 7 }) // 半截脏数据

        val full = ByteArray(150) { (it * 3).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(full)))

        val error = ApkDownloader().download(server.url("/a.apk").toString(), target)

        assertNull("整包重下应当成功：$error", error)
        assertEquals("文件必须是整包长度，不能被追加成 250", 150L, target.length())
        assertTrue("内容必须整包一致", full.contentEquals(target.readBytes()))
        // 有半截时**会**带 Range（那不是 bug）；这里钉的是「服务端不认 Range、回 200 时
        // 客户端要能识别出来并重写」—— 上一条 assert 已经证明了没有拼成 250 字节。
        assertEquals("bytes=100-", server.takeRequest().getHeader("Range"))
    }

    @Test
    fun `416 说明半截不是这个文件，删掉重来一次`() {
        val target = target()
        target.parentFile!!.mkdirs()
        partOf(target).writeBytes(ByteArray(500) { 1 })

        server.enqueue(MockResponse().setResponseCode(416))
        val full = ByteArray(120) { (255 - it).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(full)))

        val error = ApkDownloader().download(server.url("/a.apk").toString(), target)

        assertNull("416 之后应当自动重下一次：$error", error)
        assertEquals(120L, target.length())
        assertTrue(full.contentEquals(target.readBytes()))
        assertEquals("应当发两次请求（416 + 整包）", 2, server.requestCount)
    }

    @Test
    fun `网络断在半路时保留 part，下次能接着下`() {
        val target = target()
        val payload = ByteArray(512 * 1024) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val error = ApkDownloader().download(server.url("/a.apk").toString(), target)

        assertNotNull("断线必须报失败", error)
        assertTrue("文案要说明已下多少：$error", error!!.contains("下载中断"))
        assertTrue("目标文件不该出现（半截不能当成品）", !target.exists())
        assertTrue("必须保留 .part 供续传", partOf(target).exists())
        assertTrue("保留的半截应当有内容", partOf(target).length() > 0)
    }
}
