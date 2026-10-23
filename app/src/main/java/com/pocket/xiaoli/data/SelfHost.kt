package com.pocket.xiaoli.data

/**
 * 自建分发地址 —— **全项目唯一一处**填自己域名的地方。
 *
 * ## 为什么要有它
 *
 * 这个 App 有几样东西必须从网上拿：学校索引、节假日、更新信息、推广位。
 * 在此之前它们全都托管在**别人的免费 CDN 上**（Gitee / raw.githubusercontent / jsDelivr / GitHub Releases），
 * 国内可达性时好时坏，`SchoolIndexUpdater` 甚至为此写了五级回退链。
 *
 * 现在有了自己的服务器，就把自家地址**排在回退链最前面**：
 * 自建可达时走自建（快、稳、自己说了算），自建挂了仍旧回落到原来那些镜像 ——
 * **不是替换，是加一层**。这也意味着填这个常量永远不会把 App 弄瞎。
 *
 * ## 怎么用
 *
 * 部署完服务器后，把下面 [BASE_URL] 改成自己的域名（例：`https://xy.example.com`），
 * 重新打一次包即可。**留空 = 完全不启用**，四条链路与今天完全一致。
 *
 * 目录约定（与 `tools/deploy/` 的脚本一致）：
 * ```
 * /index/school_index.json     学校索引
 * /index/holiday/<年份>.json   节假日（每年一个文件）
 * /index/update/version.json   更新信息（tag_name / html_url 两个字段）
 * /index/promos.json           推广位配置
 * /index/knowledge.json        校园知识库（由 tools/kb/build_knowledge.py 生成）
 * /apk/口袋小鲤_vX.Y.Z.apk     安装包（更新信息里的 html_url 指过来）
 * ```
 *
 * ⚠️ 这里只放**公开静态数据**。用户的学号、密码、课表、笔记一律不上服务器
 * （见 `CLAUDE.md` 的约束与 `docs/优化清单.md` 的服务端那一节）。
 */
object SelfHost {

    /** 自建地址；留空即不启用（回退链保持原样）。改完需要重新打包。 */
    const val BASE_URL: String = ""

    /**
     * 匿名运行统计的基址。
     *
     * 和 [BASE_URL] 同一个域名（运营中心后端就挂在同一台机器上），但**故意分成两个常量**：
     * 统计可以先不开，静态数据分发照旧。为空时 [com.pocket.xiaoli.telemetry.Telemetry]
     * 连队列都不写 —— 不是"发了没人收"，是根本不产生数据。
     */
    const val TELEMETRY_URL: String = "https://www.linyang62633.vip"

    /**
     * 上报用的 `X-App-Key`。
     *
     * ⚠️ 它在 APK 里必然能被提取出来，**不是防破解的凭据**。真正挡住刷量的是
     * 服务端的日配额（单 iid 每天 2000 条）与 Nginx 的速率限制。
     */
    const val TELEMETRY_KEY: String = "3fd734693bb2d38ae550415245c572f41a3cbae284a04bd8"

    /** 是否已配置自建地址 */
    val enabled: Boolean get() = BASE_URL.isNotBlank()

    /**
     * 把自建地址插到回退链最前面。
     *
     * 纯函数，便于单测：`baseUrl` 为空（没配置）时**原样返回**原来的链，
     * 所以"没配置"和"配置了"两条路都不会少一个兜底。
     *
     * @param path 自建服务器上的路径，如 `/index/school_index.json`
     */
    internal fun prepend(baseUrl: String, path: String, fallback: List<String>): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank() || path.isBlank()) return fallback
        return listOf(base + path) + fallback
    }

    /** 学校索引的完整回退链（自建 → 原有镜像） */
    fun indexUrls(fallback: List<String>): List<String> =
        prepend(BASE_URL, "/index/school_index.json", fallback)

    /** 节假日（带年份占位符，与原实现一样用 `%d` 填年份） */
    fun holidayMirrors(fallback: List<String>): List<String> =
        prepend(BASE_URL, "/index/holiday/%d.json", fallback)

    /** 更新信息（自建 version.json 排在 GitHub Releases 前面） */
    fun updateUrls(fallback: List<String>): List<String> =
        prepend(BASE_URL, "/index/update/version.json", fallback)

    /**
     * 更新 + 公告的统一数据源 `feed.json`（自建排两个仓库镜像前面）。
     *
     * 与 [updateUrls] 的区别：那个是老的「只比 tag」的接口，这个是新版带
     * `versionCode` / `minSupported` / `sha256` / 公告的接口。
     */
    fun feedUrls(fallback: List<String>): List<String> =
        withCacheBuster(prepend(BASE_URL, "/index/feed.json", fallback), bustCache = false)

    /**
     * 同 [feedUrls]，但可以给 URL 挂一个时间戳来**绕开 CDN / 边缘缓存**。
     *
     * 为什么要这个：实测 Gitee raw 的响应头是 `Cache-Control: public, max-age=60`、
     * GitHub raw 是 `max-age=300`。发版后 1~5 分钟内点「检查更新」，边缘节点可能还
     * 在吐旧 feed，用户就看到「已是最新版本」—— 新版明明已经发了。
     * 手动检查必须带 `?t=now` 打穿缓存；自动轮询不带（省流量，且不催那么紧）。
     */
    fun feedUrls(
        fallback: List<String>,
        bustCache: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<String> = withCacheBuster(prepend(BASE_URL, "/index/feed.json", fallback), bustCache, nowMillis)

    /**
     * 纯函数：给每个 URL 追加 `?t=<毫秒>`（已有 `?` 就用 `&`）。单测见 `SelfHostTest`。
     */
    internal fun withCacheBuster(urls: List<String>, bustCache: Boolean, nowMillis: Long = System.currentTimeMillis()): List<String> {
        if (!bustCache) return urls
        return urls.map { u -> u + (if ("?" in u) "&" else "?") + "t=" + nowMillis }
    }

    /** 校园知识库的完整回退链（自建 → 原有镜像） */
    fun knowledgeUrls(fallback: List<String>): List<String> =
        prepend(BASE_URL, "/index/knowledge.json", fallback)

    /**
     * 资料站索引 `library/index.json` 的完整回退链（自建 → 两个 Gitee 分支 → GitHub）。
     *
     * 与 [feedUrls] 的区别：**故意不挂破缓存参数** —— 索引不是发版数据，晚一分钟没人在意。
     */
    fun libraryIndexUrls(fallback: List<String>): List<String> =
        prepend(BASE_URL, "/index/library/index.json", fallback)

    /** 推广位配置；没配置自建时返回 null，由调用方用自己的兜底值 */
    fun promoUrl(): String? =
        BASE_URL.trim().trimEnd('/')
            .takeIf { it.isNotBlank() }
            ?.plus("/index/promos.json")
}
