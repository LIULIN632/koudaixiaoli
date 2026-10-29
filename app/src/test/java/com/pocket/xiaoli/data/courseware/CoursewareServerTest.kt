package com.pocket.xiaoli.data.courseware

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.Socket

/**
 * 课件本地服务的测试 —— 重点是**真的连一次**，不是只测纯函数。
 *
 * ## 为什么要真连
 *
 * 这一层最容易出的错都不是"路径判断写反"，而是**页面拿不到模块**：
 * importmap 指错、MIME 不对（Chromium 会拒绝以 `application/octet-stream` 加载 ESM）、
 * 服务在页面还在请求时就关了。这三种都只有走一遍真实的 HTTP 往返才暴露出来，
 * 而 Robolectric 能读 `src/main/assets`，所以这一跑是免费的。
 */
@RunWith(RobolectricTestRunner::class)
class CoursewareServerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * 发一个 GET，拿回"状态行 / 响应头 / 正文"。
     *
     * ⚠️ 头按 ISO-8859-1、正文按 UTF-8 分别解：HTTP 头本身是 latin-1，
     * 而课件里有中文 —— 整段按 latin-1 解会让"手机上的手势说明"这类断言永远不成立
     *（第一版就是这么红的）。
     */
    private class Response(val head: String, val body: String) {
        val status: String get() = head.lineSequence().first()
    }

    private fun fetch(port: Int, path: String): Response =
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
            val bytes = socket.getInputStream().readBytes()
            val asLatin = bytes.toString(Charsets.ISO_8859_1)
            val headEnd = asLatin.indexOf("\r\n\r\n")
            if (headEnd < 0) return Response(asLatin, "")
            Response(
                head = asLatin.substring(0, headEnd),
                body = bytes.copyOfRange(headEnd + 4, bytes.size).toString(Charsets.UTF_8),
            )
        }

    @Test
    fun `入口页真能取到，且 importmap 指向共享 vendor`() {
        CoursewareServer(context.assets).use { server ->
            val response = fetch(server.port, "/courseware/${Courseware.BeamDeflection.dir}/index.html")

            assertEquals("HTTP/1.1 200 OK", response.status)
            assertTrue("入口页要是 html", response.head.contains("Content-Type: text/html"))
            assertTrue("模块映射不能被改回 node_modules", response.body.contains("../vendor/three.module.min.js"))
            assertTrue("手机上的手势说明要替换掉鼠标说明", response.body.contains("单指拖拽旋转"))
        }
    }

    @Test
    fun `three 与 OrbitControls 都在，且以 javascript 送出`() {
        CoursewareServer(context.assets).use { server ->
            val three = fetch(server.port, "/courseware/vendor/three.module.min.js")
            val controls = fetch(server.port, "/courseware/vendor/jsm/controls/OrbitControls.js")

            assertEquals("HTTP/1.1 200 OK", three.status)
            assertTrue("ESM 必须以 javascript MIME 送出", three.head.contains("text/javascript"))
            assertTrue(controls.body.contains("from 'three'"))
        }
    }

    @Test
    fun `跳出课件子树与未知后缀都是 404`() {
        CoursewareServer(context.assets).use { server ->
            for (path in listOf(
                "/AndroidManifest.xml",
                "/courseware/../adapters/zhengfang_01.js",
                "/courseware/beam-deflection/index.html%00.png",
                "/courseware/beam-deflection/../../build.gradle.kts",
            )) {
                assertEquals("$path 不该被服务", "HTTP/1.1 404 Not Found", fetch(server.port, path).status)
            }
        }
    }

    @Test
    fun `路径守卫：先解码再判穿越`() {
        // 正常路径
        assertEquals(
            "courseware/soil-three-phase/src/soil.js",
            assetPathFor("/courseware/soil-three-phase/src/soil.js"),
        )
        // 百分号编码的 ".."：只看字面量会放过，解码后才拦得住
        assertNull(assetPathFor("/courseware/%2e%2e%2fAndroidManifest.xml"))
        assertNull(assetPathFor("/courseware/../../assets/secret"))
        // 反斜杠在 Windows 上是分隔符，asset 名字里不该有
        assertNull(assetPathFor("/courseware\\beam-deflection\\index.html"))
        // 查询串与 fragment 要剥掉再判后缀
        assertEquals(
            "courseware/beam-deflection/styles.css",
            assetPathFor("/courseware/beam-deflection/styles.css?v=3#top"),
        )
        // 白名单之外的后缀
        assertNull(assetPathFor("/courseware/beam-deflection/secret.log"))
        // 不是课件子树
        assertNull(assetPathFor("/adapters/zhengfang_01.js"))
        assertNull(assetPathFor("courseware/beam-deflection/index.html"))
    }

    @Test
    fun `关服务之后端口不再接受连接`() {
        val server = CoursewareServer(context.assets)
        val port = server.port
        assertEquals("HTTP/1.1 200 OK", fetch(port, "/courseware/beam-deflection/index.html").status)

        server.close()

        val refused = runCatching { Socket("127.0.0.1", port).close() }.isFailure
        assertTrue("close() 之后必须真的不再监听", refused)
    }

    @Test
    fun `清单里的每个课件都在 assets 里`() {
        // 加了课件却忘了跑 tools/sync_courseware.py 是最容易犯的一种漏：
        // 格子会出现在服务页上，点进去永远白屏。这条把它变成测试失败。
        // ⚠️ 逐个文件 open，不用 assets.list() 判目录 —— 子目录在 Robolectric 下
        // 不一定列得出来（第一版就是被"没有 src 目录"误报的）。
        for (item in Courseware.entries) {
            for (name in listOf("index.html", "styles.css", "app-mobile.css", "src/main.js")) {
                val path = "courseware/${item.dir}/$name"
                val bytes = runCatching { context.assets.open(path).use { it.readBytes() } }
                    .getOrElse { error("$path 读不到：$it") }
                assertTrue("$path 是空的", bytes.isNotEmpty())
            }
        }
        for (name in listOf("three.module.min.js", "three.core.min.js", "jsm/controls/OrbitControls.js")) {
            val path = "courseware/vendor/$name"
            assertTrue("$path 读不到", runCatching { context.assets.open(path).close() }.isSuccess)
        }
    }
}
