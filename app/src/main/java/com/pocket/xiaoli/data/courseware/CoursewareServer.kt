package com.pocket.xiaoli.data.courseware

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

private const val LOOPBACK = "127.0.0.1"
private const val ASSET_ROOT = "courseware"

/** 只认这几种后缀：不在表里的（哪怕路径合法）一律 404，避免变成"任意文件读取口"。 */
private val SERVED_EXTENSIONS = setOf("html", "css", "js", "mjs", "json", "svg", "png")

/**
 * 课件的**本机回环 HTTP 服务**：把 `assets/courseware/` 那一棵子树用 `http://127.0.0.1:<随机端口>`
 * 暴露给 WebView，页面关掉就停。
 *
 * ## 为什么非要有这个服务（不是洁癖，是硬约束）
 *
 * 课件用的是 ESM + `<script type="importmap">`。Chromium 对 `file://` 下的模块脚本按
 * **跨源**处理并直接拦掉（`Cross origin requests are only supported for protocol schemes:
 * http, https, …`），所以 `file:///android_asset/.../index.html` 只会得到一张白屏 ——
 * 源项目自己的 README 也写着"必须通过 http 打开，双击 index.html 会被 CORS 拦"。
 *
 * 三条出路里只有这条合规：
 *
 * | 方案 | 为什么不选 |
 * |---|---|
 * | `androidx.webkit` 的 `WebViewAssetLoader` | 要加依赖，本项目的硬约束是不新增 |
 * | 打包成单个 classic script（`file://` 就能跑） | 得引入 esbuild 之类的打包器，且产物是"生成后再没人看得懂"的大文件 |
 * | **本机回环服务**（本类） | 只用平台 API（`ServerSocket`），课件文件**原样**跑，与开发者本机 `npm start` 看到的是同一套东西 |
 *
 * ## 安全边界（四条，都写在被测函数里）
 *
 * 1. **只绑 `127.0.0.1`**：不监听任何对外地址，局域网里别的设备碰不到；
 * 2. **只读 `assets/courseware/` 一棵子树**：路径先 percent-decode 再判 `..`，
 *    跳出子树、绝对路径、反斜杠全部 404（见 [assetPathFor]）；
 * 3. **只回白名单后缀**：`html/css/js/mjs/json/svg/png` 之外一律 404；
 * 4. **随页面创建、离开即 `close()`**：不是常驻服务，端口也不固定，用完就没有。
 *
 * ⚠️ 诚实的残余风险：本机**另一个应用**理论上能连上这个端口。它能拿到的只有
 * 这份本来就公开的课件（`assets` 里没有任何用户数据、Cookie、凭据），
 * 所以这里没有再加一层 token 校验 —— 加了也只是防一个"本来就不是秘密"的东西，
 * 而子资源请求不带 query，token 实际上罩不住整棵目录树。
 */
internal class CoursewareServer(private val assets: AssetManager) : AutoCloseable {

    private val server = ServerSocket(0, 8, InetAddress.getByName(LOOPBACK))

    private val worker = thread(name = "CoursewareServer", isDaemon = true) { acceptLoop() }

    val port: Int get() = server.localPort

    /** 课件入口地址。子资源（css / js / vendor）都是相对路径，由同一个服务给出。 */
    fun urlFor(courseware: Courseware): String =
        "http://$LOOPBACK:$port/$ASSET_ROOT/${courseware.dir}/index.html"

    private fun acceptLoop() {
        while (!server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                // close() 会让 accept 抛异常，这就是正常的退出路径
                return
            }
            // 一次只服务一个请求就断开（响应里写 Connection: close）：
            // 浏览器会为每个子资源重新连，串行处理因此不会互相等死。
            runCatching { socket.use { serveOne(it) } }
        }
    }

    private fun serveOne(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.ISO_8859_1))
        val requestLine = reader.readLine() ?: return
        // 必须把请求头读干净：只读一行的话，剩下的字节会留在缓冲区里影响关闭时机
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val parts = requestLine.split(' ')
        if (parts.size < 2 || parts[0] != "GET") {
            write(socket, status(405, "Method Not Allowed"))
            return
        }
        val assetPath = assetPathFor(parts[1])
        val bytes = assetPath?.let { path ->
            runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
        }
        if (assetPath == null || bytes == null) {
            write(socket, status(404, "Not Found"))
            return
        }
        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ").append(contentTypeFor(assetPath)).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            // 课件是 APK 里的静态资源，改一次要发版 —— 缓存只会让"改了看不到"变成常态
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        socket.getOutputStream().use { out ->
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            out.write(bytes)
            out.flush()
        }
    }

    private fun status(code: Int, reason: String): ByteArray {
        val body = "$code $reason".toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        return header.toByteArray(Charsets.ISO_8859_1) + body
    }

    private fun write(socket: Socket, payload: ByteArray) {
        socket.getOutputStream().use { it.write(payload); it.flush() }
    }

    override fun close() {
        runCatching { server.close() }
        runCatching { worker.join(500) }
    }
}

/**
 * 请求目标 → 要读的 asset 路径；**不该服务的一律返回 null**。
 *
 * 纯函数，单测直接打边界（`CoursewareServerTest`）。顺序不能换：
 * **先 decode 再判 `..`** —— 反过来 `%2e%2e/` 会绕过字符串检查，
 * 而 `AssetManager.open` 是拿解码后的路径去查的。
 */
internal fun assetPathFor(requestTarget: String): String? {
    val raw = requestTarget.substringBefore('?').substringBefore('#')
    val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
    if (!decoded.startsWith("/")) return null
    val relative = decoded.removePrefix("/")
    if (relative.isEmpty()) return null
    if (relative.contains("..") || relative.contains('\\')) return null
    if (!relative.startsWith("$ASSET_ROOT/")) return null
    val extension = relative.substringAfterLast('/').substringAfterLast('.', "")
    if (extension.lowercase() !in SERVED_EXTENSIONS) return null
    return relative
}

internal fun contentTypeFor(path: String): String =
    when (path.substringAfterLast('.', "").lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js", "mjs" -> "text/javascript; charset=utf-8"
        "json" -> "application/json; charset=utf-8"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }
