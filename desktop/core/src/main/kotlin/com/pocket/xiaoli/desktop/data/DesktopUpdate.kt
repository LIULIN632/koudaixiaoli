package com.pocket.xiaoli.desktop.data

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 桌面端更新检查。
 *
 * ## 为什么不是「自动更新」
 *
 * 自动装更新要动正在运行的程序目录（Windows 上必须外挂进程替换文件），踩坑面很大；
 * 这一版只做**检查 + 告知**：发现新版本就在左侧栏显示一条「新版本 x.y.z · 去下载」，
 * 点一下用系统浏览器打开下载页。真正的增量/静默更新等有人用再说。
 *
 * ## 数据源
 *
 * 与 Android 同一条链：仓库里的 `announce/feed.json` 的 **`desktop` 块**。
 * 镜像顺序也一样（Gitee raw 实测比 GitHub 快一个数量级，jsDelivr 兜最后）。
 * `desktop` 块缺失或字段不全时**一律当作「没有更新」**，绝不误报。
 *
 * ## 边界
 *
 * 只发 GET、只看版本号；不上报任何东西，也没有设备标识 —— 与 Android 的匿名统计无关。
 */
object DesktopUpdate {

    /** 一份可用的远程版本信息。 */
    data class Release(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String? = null,
        val notes: List<String> = emptyList(),
    )

    /**
     * 镜像顺序与 Android 的 `AppUpdateChecker.feedMirrors` 对齐。
     *
     * `feed.json` 在仓库里是 `announce/feed.json`，所以 raw 路径带这一层。
     */
    val MIRRORS: List<String> = listOf(
        "https://gitee.com/liulin62633/pocket-carp/raw/main/announce/feed.json",
        "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/announce/feed.json",
        "https://cdn.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/announce/feed.json",
    )

    /**
     * 解析 `feed.json` 里的 `desktop` 块。
     *
     * 容忍：缺 `desktop` 块、`desktop` 是空对象、`versionCode` 不是数字、`url` 为空。
     * 这几种情况都返回 null（= 没有可用更新），而不是抛异常 —— 检查更新失败不该打扰任何人。
     */
    fun parse(text: String): Release? = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        val block = root.getAsJsonObject("desktop") ?: return null
        val code = block.get("versionCode")?.takeIf { it.isJsonPrimitive }?.asInt ?: return null
        val name = block.get("versionName")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val url = block.get("url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (code <= 0 || url.isBlank()) return null
        val sha = block.get("sha256")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        // 只要真正的 JSON 字符串：Gson 的 `asString` 会把数字 1 也变成 "1"，
        // 那样一条脏数据就会变成一条假说明。
        val notes = block.getAsJsonArray("notes")
            ?.mapNotNull { element ->
                val primitive = element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return@mapNotNull null
                primitive.asString.takeIf { primitive.isString }
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        Release(versionCode = code, versionName = name, url = url, sha256 = sha, notes = notes)
    }.getOrNull()

    /** 纯比较：远程版本比本地新才提示。同号/更低都不提示。 */
    fun isNewer(remote: Int, local: Int): Boolean = remote > local

    /**
     * 真的去拉一次。按 [MIRRORS] 顺序试，任意一路解析成功就返回；
     * 全部失败返回 null（调用方据此当作「暂时查不到」）。
     */
    fun check(
        timeoutMs: Long = 6_000,
        client: OkHttpClient = defaultClient(timeoutMs),
    ): Release? {
        for (url in candidates()) {
            val body = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PocketXiaoli-Desktop")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.string()
                }
            }.getOrNull() ?: continue
            parse(body)?.let { return it }
        }
        return null
    }

    /**
     * 实际要试的地址：**测试/CI 可以用 `-Dxiaoli.update.feed=<url>` 打头**。
     *
     * 加这个开关不是为了用户，而是为了让「检查更新」这条链能离线验证——
     * 否则本机连不上 Gitee/GitHub 时，这条逻辑永远只能靠读代码相信。
     */
    internal fun candidates(localOverride: String? = System.getProperty("xiaoli.update.feed")): List<String> =
        if (localOverride.isNullOrBlank()) MIRRORS else listOf(localOverride) + MIRRORS

    private fun defaultClient(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs * 2, TimeUnit.MILLISECONDS)
        .build()
}
