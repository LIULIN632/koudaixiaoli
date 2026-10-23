package com.pocket.xiaoli.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 安装包的完整性校验 —— 对标岱宗盒子「下载完先验一验再让人装」。
 *
 * 三条判据，任何一条不过都不要交给系统安装器：
 * 1. **大小** ≥ 100 KB（防的是「下载到一半」和「拿到一张 404 页面」）；
 * 2. **头两个字节是 `PK`**（APK 是 zip；HTML 错误页开头是 `<`）；
 * 3. **SHA-256 对得上**（feed 里给了才校验 —— 自建源给不了摘要时不能因此卡住）。
 *
 * 这三条都是**本地**判据，不联网、不依赖签名校验，所以能被单测全覆盖。
 */
object ApkArtifact {

    /** 小于这个字节数的一律当下载失败 —— 我们最小的正常包也有几 MB */
    const val MIN_SIZE_BYTES = 100_000L

    /**
     * @return null 表示通过；否则是给用户看的中文失败原因
     */
    fun verify(file: File, expectedSha256: String? = null): String? {
        if (!file.isFile) return "安装包不存在"
        if (file.length() < MIN_SIZE_BYTES) return "安装包不完整（只有 ${file.length()} 字节）"
        val header = ByteArray(2)
        val read = file.inputStream().use { it.read(header) }
        if (read < 2 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
            return "下载到的不是安装包（可能被网络拦截了）"
        }
        val expected = normalizeSha256(expectedSha256)
        if (expected.isNotEmpty()) {
            val actual = sha256(file)
            if (!actual.equals(expected, ignoreCase = true)) return "安装包校验失败，请重新下载"
        }
        return null
    }

    /** 允许写 `sha256:abc…`、`ABC…` 或空串（空 = 不校验） */
    internal fun normalizeSha256(raw: String?): String =
        raw?.trim()?.removePrefix("sha256:")?.removePrefix("SHA256:")?.trim()?.lowercase().orEmpty()

    /** 流式算摘要，不把 20 MB 读进内存 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * 一次下载回调带回来的进度快照。
 *
 * [total] 可能是 -1（chunked 响应拿不到 Content-Length）—— 这时**百分比与剩余大小都是未知**，
 * [percent] / [remaining] 返回 null，界面只能显示已下载大小，不许编一个 `0%` 出来。
 */
data class DownloadProgress(
    val bytes: Long,
    val total: Long,
    val bytesPerSec: Long,
) {
    val percent: Int? get() = if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else null
    val remaining: Long? get() = if (total > 0) (total - bytes).coerceAtLeast(0L) else null
}

/**
 * 滑动窗口测速（~1 秒窗口）。
 *
 * 不用「总字节 / 已用时间」：起步那一秒里连接建立全算进分母，会给出荒唐的低速。
 * [clock] 可注入，单测里用假时钟拨表。
 */
class DownloadSpeedMeter(
    private val windowMillis: Long = 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val samples = ArrayDeque<Pair<Long, Long>>()

    /** 记一个采样点，返回当前窗口内的速度（B/s）；窗口还没拉开时给 0 */
    fun sample(bytes: Long): Long {
        val now = clock()
        samples.addLast(now to bytes)
        while (samples.size > 2 && now - samples.first().first > windowMillis) {
            samples.removeFirst()
        }
        val (firstTime, firstBytes) = samples.first()
        val elapsed = now - firstTime
        if (elapsed <= 0) return 0
        return ((bytes - firstBytes) * 1000 / elapsed).coerceAtLeast(0)
    }
}

/**
 * 进度文案的唯一真源 —— 通知与 App 内面板都用它，保证两处说法一致。
 * 全部是纯函数：能算的东西不进 Composable（本项目既有惯例）。
 */
object DownloadProgressText {

    /** 完整的一行：`42% · 1.8 MB/s · 还剩 12.4 MB`；总量未知时 `已下载 3.2 MB · 正在下载` */
    fun label(progress: DownloadProgress): String {
        val percent = progress.percent
        if (percent == null) return "已下载 ${size(progress.bytes)} · 正在下载"
        if (progress.bytesPerSec <= 0) {
            // 窗口还没拉开（刚起步）与真的断流分不开，就都别露 `0 B/s`：0% 说「正在准备」，中途说「正在重连…」
            return "$percent% · " + if (percent == 0) "正在准备…" else "正在重连…"
        }
        val remaining = progress.remaining ?: 0L
        val speedPart = speed(progress.bytesPerSec)
        return if (remaining > 0) "$percent% · $speedPart · 还剩 ${size(remaining)}"
        else "$percent% · $speedPart"
    }

    /** `999 B/s` / `512 KB/s` / `1.8 MB/s` / `1.5 GB/s`。0 也照实格式化，显示层自己换成「正在重连…」 */
    fun speed(bytesPerSec: Long): String = when {
        bytesPerSec <= 0 -> "0 B/s"
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
        bytesPerSec < 1024L * 1024 * 1024 -> "${decimalMb(bytesPerSec)} MB/s"
        else -> "${decimalGb(bytesPerSec)} GB/s"
    }

    /** `512 B` / `12.4 MB`。负数按 0 处理 —— 剩余量算到负数只可能是竞态，不该给人看 */
    fun size(bytes: Long): String {
        val value = bytes.coerceAtLeast(0)
        return when {
            value < 1024 -> "$value B"
            value < 1024 * 1024 -> "${value / 1024} KB"
            value < 1024L * 1024 * 1024 -> "${decimalMb(value)} MB"
            else -> "${decimalGb(value)} GB"
        }
    }

    private fun decimalMb(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))
    private fun decimalGb(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/**
 * 把 APK 下到缓存目录（`cacheDir/updates/`，已在 `res/xml/file_paths.xml` 里暴露给 FileProvider）。
 *
 * 先写 `.part` 再改名：**中途被杀掉不会留下一个看起来完整的半截包**。
 *
 * ## 断点续传（2026-10-23）
 *
 * 之前失败就把 `.part` 删掉，于是校园网里断在 90% 只能从 0 重下 8.8 MB。现在：
 *
 * - 发请求时带 `Range: bytes=<已有>-`，服务端回 **206** 就从已有字节往后写（追加）；
 * - 服务端不认 Range、回 **200** 整包，就把 `.part` 截断重写（绝不追加，否则文件会拼成两半）；
 * - **416**（已有字节 ≥ 文件总长）说明这个 `.part` 不是这个文件了 —— 删掉重来一次；
 * - 网络中断时**保留** `.part`，下次点击（或换 Gitee/GitHub 另一条地址）接着下。
 *
 * 两条仓是同一份字节，所以「在 Gitee 下了一半、换 GitHub 续」也是对的；万一拼接结果不对，
 * 安装前的 SHA-256 校验会拦住并删包（见 `ApkArtifact.verify`）。
 */
class ApkDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    /**
     * @return null 表示成功；否则是失败原因（中文，可直接显示给用户）
     */
    fun download(url: String, target: File, onProgress: (DownloadProgress) -> Unit = {}): String? {
        if (url.isBlank()) return "没有下载地址"
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")

        val existing = if (part.exists()) part.length() else 0L
        when (val first = attempt(url, part, existing, onProgress)) {
            is Attempt.Done -> Unit
            is Attempt.Failed -> return first.reason
            is Attempt.Restart -> {
                // 服务端不认 Range / 416：只能从头来。这一步**不**算失败，也不清缓存外的任何文件。
                part.delete()
                when (val second = attempt(url, part, 0L, onProgress)) {
                    is Attempt.Done -> Unit
                    is Attempt.Failed -> return second.reason
                    is Attempt.Restart -> return "下载失败：服务器不支持断点续传"
                }
            }
        }

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) return "保存安装包失败"
        return null
    }

    /** 一次尝试的结果。`Restart` 只在「同一个文件、但服务端不肯按偏移给」时出现。 */
    private sealed interface Attempt {
        object Done : Attempt
        object Restart : Attempt
        data class Failed(val reason: String) : Attempt
    }

    private fun attempt(
        url: String,
        part: File,
        existing: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): Attempt {
        val request = Request.Builder()
            .url(url)
            .apply { if (existing > 0) header("Range", rangeHeader(existing)) }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    416 -> return Attempt.Restart
                    in 200..299 -> Unit
                    else -> return Attempt.Failed("下载失败（HTTP ${response.code}）")
                }
                val append = response.code == 206 && existing > 0
                val body = response.body ?: return Attempt.Failed("下载失败（空响应）")
                // 206 的 Content-Length 只是**这一段**的长度，总长要从 Content-Range 里取
                val total = if (append) {
                    parseContentRangeTotal(response.header("Content-Range"))
                        ?: existing + body.contentLength()
                } else {
                    body.contentLength()
                }
                var downloaded = if (append) existing else 0L
                if (!append && existing > 0) {
                    // 服务端给了整包：先把旧的半截删掉，否则追加会拼成两份文件
                    part.delete()
                }
                body.byteStream().use { input ->
                    FileOutputStream(part, /* append = */ append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        val speedMeter = DownloadSpeedMeter()
                        var lastPercent = -1
                        var lastCallbackAt = 0L
                        onProgress(DownloadProgress(downloaded, total, 0L))
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            // 整数百分比变化必达；停滞时（大缓冲/慢网里 percent 不动）也保证
                            // ~250ms 一次快照，否则速度永远刷不出新值。
                            val percent = if (total > 0) (downloaded * 100 / total).toInt() else -1
                            val now = System.currentTimeMillis()
                            if (percent != lastPercent || now - lastCallbackAt >= PROGRESS_CALLBACK_MS) {
                                lastPercent = percent
                                lastCallbackAt = now
                                onProgress(DownloadProgress(downloaded, total, speedMeter.sample(downloaded)))
                            }
                        }
                    }
                }
                // 连接正常关掉、但字节数没到总长（服务端提前收尾 / 中间代理截断）：
                // 当成失败并**保留** .part，下次接着下，而不是交给安装器一个坏包。
                if (total > 0 && downloaded < total) {
                    return Attempt.Failed(
                        "下载不完整（${DownloadProgressText.size(downloaded)} / ${DownloadProgressText.size(total)}）"
                    )
                }
            }
            Attempt.Done
        } catch (e: Exception) {
            // ⚠️ 故意不删 .part：断点续传的全部价值就在这几个字节上。真正的坏包会在
            // `ApkArtifact.verify` 那一步被判掉并删除（大小 / PK 头 / SHA-256 三重校验）。
            val done = if (part.exists()) part.length() else 0L
            Attempt.Failed("下载中断（已下 ${DownloadProgressText.size(done)}）：${e.message ?: "网络异常"}")
        }
    }

    private companion object {
        /** 下载循环里两次进度快照回调的最小间隔 */
        const val PROGRESS_CALLBACK_MS = 250L
    }
}

/**
 * `Range: bytes=1000-` —— 从已有字节续到文件末尾。
 *
 * 抽成独立函数只为了能单测：下载器的其余部分要真网络，这一行不需要。
 */
internal fun rangeHeader(existing: Long): String = "bytes=$existing-"

/**
 * 从 `Content-Range: bytes 1000-1999/2000` 里取出**文件总长**（2000）。
 *
 * 返回 null 表示格式不认识或总长是 `*`（chunked / 服务端也不知道），调用方退回
 * 「已有 + 这一段长度」的估法。
 */
internal fun parseContentRangeTotal(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val slash = header.lastIndexOf('/')
    if (slash < 0 || slash == header.lastIndex) return null
    val total = header.substring(slash + 1).trim()
    return total.toLongOrNull()?.takeIf { it > 0 }
}

/**
 * 同一个 APK 在**两个仓**的下载地址 —— 下载失败时换一条路再试。
 *
 * 抄岱宗盒子的兜底思路（`AppUpdater.fetchLatest`：Gitee 那条没附件就拿 GitHub 的 url），
 * 只是我们的场景反过来：feed 里只写了一个 `apkUrl`，而 **Gitee 的下载 CDN 会偶发 403 / 超时**。
 * 一失败就让人去浏览器是最后手段，先换仓重试一次更省事。
 *
 * 两边是**同一份字节**（每次发版都按 SHA-256 逐字节校验过），所以 feed 给的摘要
 * 对第二条地址同样成立 —— 换源不需要放宽校验。
 */
object ApkMirrors {

    const val GITEE_BASE = "https://gitee.com/liulin62633/pocket-carp/releases/download"
    const val GITHUB_BASE = "https://github.com/LIULIN632/koudaixiaoli/releases/download"

    /** 附件名两个仓一样（`tools/deploy/publish-gitee.ps1` 与 `gh release` 都用它） */
    fun assetName(version: String): String = "koudaixiaoli_v${version.trim().removePrefix("v")}.apk"

    fun giteeUrl(version: String): String = "$GITEE_BASE/v${version.trim().removePrefix("v")}/${assetName(version)}"

    fun githubUrl(version: String): String = "$GITHUB_BASE/v${version.trim().removePrefix("v")}/${assetName(version)}"

    /**
     * 按顺序要试的地址：**feed 给的那条永远第一**（它才是这一版真正在用的），
     * 之后补上两个仓的直链，去重。
     *
     * 版本号未知时只返回 feed 给的那条 —— 拼不出别的地址，不要瞎猜。
     */
    fun candidates(version: String?, primary: String): List<String> {
        val list = mutableListOf<String>()
        if (primary.isNotBlank()) list += primary
        if (!version.isNullOrBlank()) {
            list += giteeUrl(version)
            list += githubUrl(version)
        }
        return list.distinct()
    }

    /** 给用户看的“换到哪个源”标签 —— 只留 host，别把整条 URL 塞进提示 */
    fun label(url: String): String = when {
        "gitee.com" in url -> "Gitee"
        "github.com" in url -> "GitHub"
        else -> "备用源"
    }
}

/**
 * 交给系统安装器。
 *
 * ⚠️ Android 8+ 要先拿到「安装未知来源应用」的授权，**这个授权不能由 App 自己申请**，
 * 只能把用户送到系统设置页 —— 所以 [install] 失败时调用方必须给「用浏览器下载」的退路，
 * 不能把人卡死在一个装不上的按钮上。
 */
object ApkInstaller {

    fun directory(context: Context): File = File(context.cacheDir, "updates")

    /** 已授权「安装未知来源应用」 */
    fun canInstall(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** 跳系统设置页去授权；返回 false 表示机型不给这个入口（此时只能走浏览器） */
    fun openPermissionSettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)

    /**
     * 交给系统安装器的 Intent。
     *
     * 单独抽出来是为了让**通知**也能用它：通知里的「安装」动作需要
     * `PendingIntent.getActivity(...)`，不能直接 `startActivity`。
     */
    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun install(context: Context, file: File): Boolean = runCatching {
        context.startActivity(installIntent(context, file))
        true
    }.getOrDefault(false)
}
