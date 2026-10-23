package com.pocket.xiaoli.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 「推荐给同学」的全部落地逻辑：链接、文案、把**已装 APK 递出去**。
 *
 * ## 为什么分享的是 APK 本体，而不是链接
 *
 * 同学群里发一条链接，对方要点开浏览器、找到 release、挑包、下载 —— 四步；
 * 发安装包是"点一下保存、点一下安装"。对一个只在烟台理工用的校内工具，
 * 最可能的传播场景是**面对面 / 同班群**，本机就装着最新版，直接递过去最快。
 * 链接仍保留（气泡里可复制），给不能收文件的人兜底。
 *
 * ## 三条纪律
 *
 * 1. **拷贝在 IO 线程**：24MB 的 `copyTo` 放主线程会卡住动画（`shareInstalledApk` 是 `suspend`）。
 * 2. **每次清空旧件**：缓存目录只留本次要发的那一个，换版本不会堆一摞 APK。
 * 3. **不可用时返回 false，不抛**：调用方只负责提示，不负责兜底逻辑。
 */
object AppShare {

    /** 主链接：Gitee（国内直连，同学点开就能下） */
    const val GITEE_URL = "https://gitee.com/liulin62633/pocket-carp/releases"

    /** 备用链接：GitHub（Gitee 打不开时用） */
    const val GITHUB_URL = "https://github.com/LIULIN632/koudaixiaoli/releases"

    const val APK_MIME = "application/vnd.android.package-archive"

    /** 分享时随包带的一句话。二维码里**只放 URL** —— 文案会让二维码更密、更难扫。 */
    fun recommendText(versionName: String): String =
        "口袋小鲤 · 烟台理工学院校园助手（v$versionName）\n下载地址：$GITEE_URL"

    /**
     * 版本号里可能出现任何字符（`0.1.67-beta/2`），而它要拼进**文件名**。
     * 只留安全字符，其余替成下划线；全非法时给 `unknown`，绝不返回空串。
     */
    fun safeVersion(versionName: String): String {
        val cleaned = versionName.trim()
            .replace(Regex("[^0-9A-Za-z._-]"), "_")
            .trim('.')
        return cleaned.ifBlank { "unknown" }
    }

    /** 与 `build.ps1` 的 renameApk 同形：`口袋小鲤_v0.1.67.apk` */
    fun apkFileName(versionName: String): String = "口袋小鲤_v${safeVersion(versionName)}.apk"

    /**
     * 分享安装包的 Intent（抽出来是为了能单独测：type / EXTRA_STREAM / 读权限 flag 三件事
     * 任何一件漏了，接收方都会拿到一个"打不开的文件"）。
     */
    fun apkShareIntent(uri: Uri, versionName: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "口袋小鲤 v${safeVersion(versionName)}")
            putExtra(Intent.EXTRA_TEXT, recommendText(versionName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /**
     * 把已装的 APK 拷到缓存并挂上 FileProvider。
     *
     * 返回 null 的两种情形：拿不到 `sourceDir`（理论不该发生）、拷贝失败（空间不足）。
     * 调用方据此提示，不要自己再读一次 `sourceDir`。
     */
    fun stageApkFile(context: Context, versionName: String): File? {
        val source = runCatching { File(context.applicationInfo.sourceDir) }.getOrNull()
        if (source == null || !source.isFile || source.length() <= 0L) return null

        val dir = File(context.cacheDir, "share")
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = File(dir, apkFileName(versionName))
        // 清掉上次留下的（可能同版本重装、也可能换了版本号）
        dir.listFiles()?.forEach { old -> if (old.absolutePath != target.absolutePath) old.deleteRecursively() }

        return runCatching {
            source.copyTo(target, overwrite = true)
            target.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    /**
     * 走系统分享面板把安装包递出去。
     *
     * @return true = 面板已拉起；false = 没找到包或拷贝失败（调用方 Toast 提示）
     */
    suspend fun shareInstalledApk(context: Context, versionName: String): Boolean {
        val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            stageApkFile(context, versionName)
        } ?: return false

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return false

        val chooser = Intent.createChooser(apkShareIntent(uri, versionName), "分享安装包")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /** 复制一段文本（剪贴板内容在 Android 13+ 会由系统提示，所以这里不再弹自己的 Toast 也行） */
    fun copy(context: Context, label: String, text: String) {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** 用系统浏览器打开（不内置 WebView：这里只有两个固定的发行页） */
    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
