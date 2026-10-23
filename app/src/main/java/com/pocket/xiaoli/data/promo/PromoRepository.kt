package com.pocket.xiaoli.data.promo

import android.content.Context
import android.content.SharedPreferences
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.network.NetworkLogInterceptor
import com.pocket.xiaoli.data.SelfHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 推广位内容仓库。
 *
 * ## 为什么内容放服务端
 *
 * 原本推广位文案是**硬编码在 `TreasureBoxScreen` 里**的，改一句话要发一次版、
 * 还得等用户升级。合作推广这种"随时上架/下架"的场景，文案与图片必须在服务端。
 *
 * App 侧只负责：拉取 → 校验（[PromoValidator]）→ 缓存原文 → 按当前时间解析 → 兜底。
 *
 * ## 三条不变量
 *
 * 1. **拉不到就用缓存**：离线打开推广页不能变空白，否则看起来像功能坏了
 * 2. **缓存也没有就什么都不显示**：宁可没有推广，也不要显示"预留位"这种占位话术
 *    （那是给开发者看的，用户不该看到）
 * 3. **缓存的是服务端原文，不是解析结果**：解析结果里含"按当时时间过滤后"的结论，
 *    缓存它就等于把一个昨天结束的活动冻到今天。每次读取都重新解析，
 *    时间窗判断就永远只有一个实现（[PromoValidator.parse]）
 *
 * ## 与其它网络模块的关系
 *
 * 和 [com.pocket.xiaoli.network.SchoolIndexUpdater] 同一套路子（远程 JSON + 容错 + 缓存）。
 * 2026-10-23 起配置本身也进了仓库（`announce/promos.json`），与 `feed.json` 一样走
 * Gitee raw + GitHub raw 两级镜像 —— 不再依赖自建服务器（见 [CONFIG_URLS]）。
 */
class PromoRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(NetworkLogInterceptor)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 拉取推广配置。
     *
     * 返回的永远是**已校验、已按当前时间过滤**的结果；任何失败路径都退化为缓存或空配置，
     * 不抛异常 —— 调用方永远拿到一个可展示的列表。
     */
    suspend fun load(): PromoConfig = withContext(Dispatchers.IO) {
        // F-Droid 构建不带推广位（`-Pfdroid=true`）：上架时少一个 Ads 反特性要解释。
        if (BuildConfig.FDROID) return@withContext EMPTY
        // 用户关掉推广 → 连请求都不发。只是"不显示"等于还在偷偷取数据
        if (!enabled) return@withContext EMPTY

        val fresh = runCatching { fetchRaw() }.getOrNull()
        if (fresh != null) {
            prefs.edit().putString(KEY_RAW, fresh).apply()
            return@withContext PromoValidator.parse(fresh, nowSec())
        }

        // 网络失败 → 用缓存原文，按**现在**的时间重新解析
        val cached = prefs.getString(KEY_RAW, null)
        if (cached.isNullOrBlank()) return@withContext EMPTY
        PromoValidator.parse(cached, nowSec())
    }

    /**
     * 按 [CONFIG_URLS] 顺序试，**第一个拿到非空正文的就是结果**；全失败返回 null。
     *
     * 不做并行竞速：这份 JSON 只有几 KB，而它挂在服务页首次渲染的路径上，
     * 多开一个连接并不比「Gitee 正常时 100~400ms」更快；并行反而让失败时的日志更难读。
     */
    private fun fetchRaw(): String? {
        for (url in CONFIG_URLS) {
            val text = try {
                val request = Request.Builder()
                    .url(url)
                    .cacheControl(CacheControl.Builder().noCache().build())
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()?.takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                // 单条源失败就试下一条。日志不用自己写 —— 这个 client 上挂了
                // NetworkLogInterceptor，异常与 4xx/5xx 已经自动记进网络日志。
                null
            }
            if (text != null) return text
        }
        return null
    }

    // ── 本地开关 ──

    /**
     * 是否允许拉取/展示推广内容。
     *
     * ⚠️ **界面上没有这个开关**（2026-10-23 用户决定）：服务页顶部那条是
     * 「介绍学校与周边资源 + 帮合作方推一下」的展示位，属于产品内容，不是需要用户关掉的广告。
     * `AgreementText` 第六条因此也只承诺「仅作展示、不追踪点击」，不再承诺可关闭。
     *
     * 这个字段保留为**内部熔断**：真出了内容事故（配错图、合作方出事），
     * 可以在代码里临时关掉，或者由将来的运营面板控制，不必让用户面对一个"关不关推广"的开关。
     *
     * 默认**开启**。
     */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 清掉本地缓存（用户点「清除缓存」时一并调用）。 */
    fun clearCache() {
        prefs.edit().remove(KEY_RAW).apply()
    }

    private fun nowSec(): Long = System.currentTimeMillis() / 1000

    companion object {
        /**
         * 推广配置的三个取处，按顺序试（第一个拿到内容的就用它）。
         *
         * ## 为什么不再依赖自建服务器
         *
         * 原来只有 `SelfHost.BASE_URL + /index/promos.json` 一条路，而 `BASE_URL` 是空的、
         * 回退的 `cdn.yitsd.edu.cn` 实测 403 —— 于是**轮播从上线起就没显示过**。
         * 2026-10-23 起改成和 `feed.json` 同一套思路：**配置进仓库**，客户端读静态 raw。
         *
         * | 顺序 | 地址 | 说明 |
         * |---|---|---|
         * | 1 | 自建服务器（配了才用） | 有服务器时优先它，改文案不用等 CDN |
         * | 2 | Gitee `raw/main/announce/promos.json` | 主镜像，免备案、国内直连 |
         * | 3 | GitHub `raw/main/announce/promos.json` | 兜底，境内常连不上，只在 Gitee 挂时用 |
         *
         * 配图同理放 `announce/promo/`，域名白名单见 `PromoValidator.IMAGE_HOSTS`。
         * 改推广位 = 改这个 JSON + 传图 + `git push` 两个仓，**不用发版**。
         */
        val CONFIG_URLS: List<String> = buildList {
            SelfHost.promoUrl()?.let { add(it) }
            add("https://gitee.com/liulin62633/pocket-carp/raw/main/announce/promos.json")
            add("https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/announce/promos.json")
        }

        /** 兼容旧调用点：第一个取处。新代码请用 [CONFIG_URLS]。 */
        val CONFIG_URL: String get() = CONFIG_URLS.first()

        private val EMPTY = PromoConfig(version = 0, promos = emptyList())

        private const val KEY_RAW = "promo_config_raw"
        private const val KEY_ENABLED = "promo_enabled"
    }
}
