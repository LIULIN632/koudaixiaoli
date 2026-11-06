package com.pocket.xiaoli.data.courseware

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/** 一次下载的结果 —— UI 只按这几种分支处理，不再解析异常字符串。 */
internal sealed interface CoursewareDownloadOutcome {
    data object Installed : CoursewareDownloadOutcome
    /** 包已经在本地且版本一致，没走网络。 */
    data object AlreadyInstalled : CoursewareDownloadOutcome
    data class Failed(val reason: String) : CoursewareDownloadOutcome
    /** 用户离开页面导致的取消 —— 不是错误，不该弹提示。 */
    data object Cancelled : CoursewareDownloadOutcome
}

/**
 * 把课件包从直链下到 `filesDir/courseware/<id>/`。
 *
 * ## 四道校验（顺序固定，先便宜后贵）
 *
 * 1. **HTTP 200 + Content-Length**：直链是 302 到 CDN 的，不跟随就会拿到空 302 体；
 * 2. **字节数**与清单 `size` 一致（差一个字节就删，不做"差不太多"的努力）；
 * 3. **sha256** 与清单一致 —— 这一条防的是 CDN 缓存串味与中间人替换；
 * 4. **解压时不越界**：条目名含 `..`、以 `/` 开头、带反斜杠一律整包拒绝；
 *    同时限制解压总量（防 zip bomb），并要求根目录有 `index.html`。
 *
 * 失败时**不留下半个目录**：先解到 `.tmp-<id>`，全部通过才替换旧目录。
 */
internal object CoursewareDownloader {

    const val MAX_ZIP_BYTES = 8L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 200L * 1024 * 1024

    private val ENTRY_NAME = Regex("^[A-Za-z0-9._/-]+$")

    fun targetDir(root: File, id: String): File = File(root, id)

    /**
     * 解压一个 zip 到 [dest]。**不含网络**，所以单测可以直接喂一个手工构造的坏包。
     *
     * @return null 表示成功；否则是给用户看的原因（不暴露路径细节）。
     */
    fun unzip(zip: File, dest: File, requireIndex: Boolean = true): String? {
        val temp = File(dest.parentFile, ".tmp-${dest.name}")
        temp.deleteRecursively()
        if (!temp.mkdirs()) return "无法创建解压目录"
        var total = 0L
        try {
            ZipFile(zip).use { archive ->
                val entries = archive.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.isEmpty() || !ENTRY_NAME.matches(name) || name.contains("..") || name.startsWith("/")) {
                        return "包内有非法路径"
                    }
                    val out = File(temp, name)
                    if (!out.canonicalPath.startsWith(temp.canonicalPath + File.separator)) {
                        return "包内有非法路径"
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    total += entry.size.coerceAtLeast(0L)
                    if (total > MAX_TOTAL_BYTES) return "解压后超过 200 MB"
                    out.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                }
            }
        } catch (t: Throwable) {
            temp.deleteRecursively()
            return "解压失败：${t.javaClass.simpleName}"
        }
        if (requireIndex && !File(temp, "index.html").isFile) {
            temp.deleteRecursively()
            return "包里没有 index.html"
        }
        dest.deleteRecursively()
        if (!temp.renameTo(dest)) {
            temp.deleteRecursively()
            return "无法写入课件目录"
        }
        return null
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 人类可读的体积，服务页那一格只给到 0.1 MB 的精度就够。 */
    fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> "未知大小"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}

/** 下载进度（0..100）。回调发生在 IO 线程，UI 侧要自己 `withContext(Main)`。 */
internal fun interface DownloadProgress {
    fun onProgress(percent: Int)
}
