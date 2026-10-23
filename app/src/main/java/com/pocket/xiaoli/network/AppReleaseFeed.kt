package com.pocket.xiaoli.network

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Instant

/**
 * 更新与公告的**唯一数据源**：一个 `feed.json`，同时承载「有没有新版」和「要说什么话」。
 *
 * ## 为什么要独立成文件（而不是读 Release 正文）
 *
 * 1. **不发版也能推公告** —— 改一次 json 提交即可，不打包、不跑 CI；
 * 2. **schema 可以演进** —— 正文里塞 HTML 注释终究是 hack，注释里再套 JSON 就没法维护；
 * 3. **两个镜像内容完全一致** —— Gitee 的 Release body 与 GitHub 的 body 格式并不一样
 *    （实测：Gitee 只有「下载见下方附件」，没有 versionCode），读文件就没有这个差异。
 *
 * Release 于是退回它的本职：**存 APK + 给人看更新说明**。
 *
 * ## 判定为什么用 versionCode 而不是版本字符串
 *
 * `gradle.properties` 的 `versionCode` 是**单调整数**，比版本字符串干净一万倍。
 * 岱宗盒子被迫写了一段 `compareVersions` 去兼容 `4.72 == 4.7.2` 这种坑，
 * 我们用 `Int` 比较就绕过了整个问题。
 *
 * ## 兼容
 *
 * 老 Release 正文里没有 feed 块、feed.json 也还没部署时，[AppUpdateChecker] 会退回今天
 * 「读 releases/latest 比 tag」的老路 —— 所以这个文件上线**不会让 App 变瞎**。
 */
data class AppReleaseFeed(
    /** 最新版的 versionCode；0 表示这份 feed 不带版本信息（只推公告） */
    val versionCode: Int = 0,
    val versionName: String = "",
    /** 低于这个 versionCode 的客户端**必须**升级（不可取消）；0/null 表示不强制 */
    val minSupported: Int = 0,
    val apkUrl: String = "",
    val sha256: String = "",
    /** 灰度百分比 0..100：0 = 全面暂停，100 = 全量（缺省） */
    val rollout: Int = 100,
    val notes: List<String> = emptyList(),
    val announcements: List<Announcement> = emptyList(),
    /**
     * 内测通道（`feed.json` 的顶层 `beta` 块），结构与 [app] 完全一样。
     *
     * null = 这份 feed 没带内测包。它**只可能出现在根对象上**（beta 块里再套 beta 会被忽略），
     * 所以判定"这个包能不能给用户装"的逻辑两个通道共用一份，不用写两遍。
     */
    val beta: AppReleaseFeed? = null,
) {
    /** 这份 feed 是否带「有版本可以装」的信息 */
    val hasApp: Boolean get() = versionCode > 0 && versionName.isNotBlank()

    /** 有没有内测包可发（块在、但 versionCode 没填的半截 beta 不算） */
    val hasBeta: Boolean get() = beta?.hasApp == true

    /** 是否有任何值得让用户看见的东西 */
    val isEmpty: Boolean get() = !hasApp && !hasBeta && announcements.isEmpty()
}

/**
 * 拉下来的 feed 原文 + 解析结果。
 *
 * 原文要留着做缓存（下次网络挂了还能渲染上一份公告），
 * 所以网络层不能只回 [AppReleaseFeed]。
 */
data class FetchedFeed(
    val feed: AppReleaseFeed,
    /** 镜像返回的**原文**（已拆掉 Gitee 的 base64 信封） */
    val rawJson: String,
)

/**
 * 一条公告。等级对标岱宗盒子的五级，但字段只保留用得上的。
 */
data class Announcement(
    /** 稳定 id，用于「已读」去重 —— 同一条公告改文案不应重新弹 */
    val id: String,
    val level: AnnouncementLevel = AnnouncementLevel.INFO,
    val title: String,
    val body: String = "",
    /** 有链接时抽屉里可点开 */
    val url: String? = null,
    /** 生效/失效时间（epoch 毫秒）；null = 不限 */
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    /** 可见性版本窗：低于/高于这些版本的客户端看不到这条 */
    val minVersionCode: Int? = null,
    val maxVersionCode: Int? = null,
    /**
     * 低于这个 versionCode 的客户端要把这条**提升为强更**并必须确认。
     * 注意与 [minVersionCode] 的区别：那个是「窗口外就别显示」，这个是「窗口内但太老就强制」。
     */
    val forceBelow: Int? = null,
    /** 必须点「知道了」才能消掉（缺省：CRITICAL 及以上自动为 true） */
    val mustAck: Boolean = false,
)

/**
 * 必须点掉才能继续：CRITICAL 及以上，或显式 `mustAck`。
 *
 * 这条规则只写一次（不靠 data class 的默认值）—— 否则「代码里 new 出来的 CRITICAL 公告」
 * 和「从 json 解析出来的」行为会不一致。
 */
internal val Announcement.needsAck: Boolean
    get() = mustAck || level.rank >= AnnouncementLevel.CRITICAL.rank

/** 公告等级；[rank] 越大越优先 */
enum class AnnouncementLevel(val rank: Int) {    INFO(1),
    WARN(2),
    UPDATE(3),
    CRITICAL(4),
    FORCE_UPDATE(5),
    ;

    companion object {
        /** 容错解析：认不出来的一律当 INFO，绝不因为写错等级就整份 feed 作废 */
        fun from(raw: String?): AnnouncementLevel = when (raw?.trim()?.uppercase()) {
            "WARN", "WARNING", "NOTICE" -> WARN
            "UPDATE", "NEW_VERSION" -> UPDATE
            "CRITICAL", "IMPORTANT" -> CRITICAL
            "FORCE_UPDATE", "FORCE" -> FORCE_UPDATE
            else -> INFO
        }
    }
}

/**
 * feed.json 的解析器。**纯函数、不联网**，所有容错都在这里，
 * 单测见 `AppReleaseFeedTest`。
 *
 * 解析策略：**能读多少读多少**。缺字段用默认值，类型不对的那一个字段当没填，
 * 只有当「整份不是 JSON 对象」或「读完什么也没有」时才返回 null。
 * 理由是这份文件是**手写运维**的 —— 一个拼错的字段不该让全量用户失去更新提示。
 */
object AppReleaseFeedParser {

    private const val COMMENT_OPEN = "<!--"
    private const val COMMENT_CLOSE = "-->"
    private const val FEED_KEYWORD = "feed"

    /**
     * 解析 feed.json 本体。
     *
     * 两种形状都认：
     * ```json
     * { "app": { "versionCode": 41, ... }, "announcements": [ ... ] }
     * { "announcements": [ ... ] }                     // 只推公告（不发版）
     * ```
     */
    fun parse(json: String?): AppReleaseFeed? {
        // ⚠️ 顺序有讲究：U+FEFF 不算空白字符，`trim()` 去不掉它。
        // 先用 trim 去掉空白、再摘 BOM、再 trim 一次 —— 否则带 BOM 的文件会被误判成非 JSON。
        val text = json?.trim()?.removePrefix("\uFEFF")?.trim() ?: return null
        if (!text.startsWith("{")) return null
        val root = runCatching { JsonParser.parseString(text) }.getOrNull() as? JsonObject ?: return null

        val app = runCatching { root.getAsJsonObject("app") }.getOrNull()
        val stable = releaseBlock(app, root).copy(
            announcements = parseAnnouncements(root.get("announcements")),
            // 内测块只认顶层 `beta` 里的字段（不继承根上的老式平铺值），否则两个通道谁覆盖谁会说不清
            beta = releaseBlockOrNull(runCatching { root.getAsJsonObject("beta") }.getOrNull()),
        )
        return stable.takeIf { !it.isEmpty }
    }

    /**
     * 一个通道的版本信息。`app` 与 `beta` 结构完全相同，所以这段只写一份 ——
     * 两个通道走的判定（[ReleaseGate]）也因此天然一致。
     *
     * @param fallback 老式平铺写法（字段直接写在根上）继续认，已经部署出去的旧 feed 才不会读空
     */
    private fun releaseBlock(obj: JsonObject?, fallback: JsonObject?): AppReleaseFeed = AppReleaseFeed(
        versionCode = obj.int("versionCode") ?: fallback.int("versionCode") ?: 0,
        versionName = obj.string("versionName") ?: fallback.string("versionName") ?: "",
        minSupported = obj.int("minSupported") ?: fallback.int("minSupported") ?: 0,
        apkUrl = obj.string("apkUrl") ?: obj.string("apk") ?: fallback.string("apkUrl") ?: "",
        sha256 = obj.string("sha256")?.lowercase() ?: "",
        rollout = (obj.int("rollout") ?: fallback.int("rollout") ?: 100).coerceIn(0, 100),
        notes = obj.stringList("notes"),
    )

    /** 块不存在就是不存在（区别于 [releaseBlock] 的"块在但字段缺"），别把 null 塞成空对象 */
    private fun releaseBlockOrNull(obj: JsonObject?): AppReleaseFeed? =
        obj?.let { releaseBlock(it, null) }

    /**
     * 从 Release 正文里抽 `<!-- feed {...} -->` 块。
     * 只作为第三兜底（feed.json 两个镜像都拿不到时），也让老 Release 有机会被复用。
     *
     * ⚠️ **这里刻意不用正则**（2026-10-17 线上崩溃）：原来的写法是
     * `Regex("(?s)<!--\s*feed\s*(\{.*?})\s*-->")`，`{` 转义了、`}` 忘了转义。
     * 桌面 JVM 的正则把裸 `}` 当作普通字符，单测因此全绿；但 **Android 用的是 ICU 正则**，
     * 裸 `}` 直接抛 `PatternSyntaxException`。而这个 `Regex` 是 `object` 的 `val`，
     * 于是 `AppReleaseFeedParser.<clinit>` 抛 `ExceptionInInitializerError`，
     * 之后**每一次**引用这个类都变成 `NoClassDefFoundError` —— 这就是
     * 「检查失败：NoClassDefFoundError（com.pocket.xiaoli.network.AppReleaseFeedParser）」
     * 的真实原因（崩溃记录 2026-10-17 16:36:59，APP 0.1.43）。
     *
     * 顺带把另一个隐患一起修掉了：原来的 `(\{.*?})` 是**非贪婪**的，遇到 `{"app":{...}}`
     * 这种嵌套对象会在**第一个** `}` 截断，剩下的 JSON 根本解析不了。改用
     * 「最后一个 `}`」才能吃掉整个对象。
     */
    fun parseFeedComment(body: String?): AppReleaseFeed? {
        val raw = body ?: return null
        var cursor = 0
        while (true) {
            val open = raw.indexOf(COMMENT_OPEN, cursor)
            if (open < 0) return null
            val close = raw.indexOf(COMMENT_CLOSE, open + COMMENT_OPEN.length)
            if (close < 0) return null
            val inner = raw.substring(open + COMMENT_OPEN.length, close)
            val keywordAt = inner.indexOf(FEED_KEYWORD)
            // 注释里 `feed` 之前只允许空白，避免把 `<!-- not a feed {} -->` 认成 feed 块
            if (keywordAt >= 0 && inner.substring(0, keywordAt).isBlank()) {
                val braceAt = inner.indexOf('{', keywordAt)
                val lastBrace = inner.lastIndexOf('}')
                if (braceAt >= 0 && lastBrace > braceAt) {
                    parse(inner.substring(braceAt, lastBrace + 1))?.let { return it }
                }
            }
            cursor = close + COMMENT_CLOSE.length
        }
    }

    private fun parseAnnouncements(element: JsonElement?): List<Announcement> {
        val array = runCatching { element?.asJsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = runCatching { item.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val id = obj.string("id")?.trim().orEmpty()
            val title = obj.string("title")?.trim().orEmpty()
            // id 和标题都没有 = 这条没法显示，也没法去重，直接丢
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val level = AnnouncementLevel.from(obj.string("level"))
            Announcement(
                id = id,
                level = level,
                title = title,
                body = obj.string("body").orEmpty(),
                url = obj.string("url")?.takeIf { it.isNotBlank() },
                startsAt = obj.time("startsAt"),
                endsAt = obj.time("endsAt"),
                minVersionCode = obj.int("minVersionCode"),
                maxVersionCode = obj.int("maxVersionCode"),
                forceBelow = obj.int("forceBelow"),
                mustAck = obj.bool("mustAck") ?: (level.rank >= AnnouncementLevel.CRITICAL.rank),
            )
        }
    }

    // ── JsonObject 取值小工具（全部容错） ──────────────────────────────────

    private fun JsonObject?.primitive(key: String): JsonElement? =
        this?.get(key)?.takeIf { it.isJsonPrimitive }

    private fun JsonObject?.int(key: String): Int? =
        primitive(key)?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonObject?.bool(key: String): Boolean? =
        primitive(key)?.let { runCatching { it.asBoolean }.getOrNull() }

    private fun JsonObject?.string(key: String): String? =
        primitive(key)?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject?.stringList(key: String): List<String> {
        val element = this?.get(key) ?: return emptyList()
        runCatching { element.asJsonArray }.getOrNull()?.let { array ->
            return array.mapNotNull { item ->
                runCatching { item.asString }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
        val single = runCatching { element.asString }.getOrNull()
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }

    /** 时间：认数字（epoch 毫秒）也认 ISO-8601（`2026-10-20T00:00:00Z`），都认不出就是 null（= 不限） */
    private fun JsonObject?.time(key: String): Long? {
        val element = primitive(key) ?: return null
        return runCatching {
            if (element.asJsonPrimitive.isNumber) element.asLong
            else Instant.parse(element.asString).toEpochMilli()
        }.getOrNull()
    }
}

/**
 * 判定逻辑，全部是纯函数 —— 便于单测，也便于解释「为什么这台机器没收到更新」。
 */
object ReleaseGate {

    /** 有新版可装 */
    fun isNewer(feed: AppReleaseFeed, currentVersionCode: Int): Boolean =
        feed.hasApp && feed.versionCode > currentVersionCode

    /**
     * 必须升级（不可取消）。
     *
     * 严格小于：`minSupported == 当前版本` 表示这个版本仍然被支持，不该被拦。
     */
    fun mustUpdate(feed: AppReleaseFeed, currentVersionCode: Int): Boolean =
        feed.hasApp && feed.minSupported > 0 && currentVersionCode < feed.minSupported

    /**
     * 灰度：同一个 (rolloutId, versionCode) 永远落在同一个桶里，所以
     * 「重试不会翻脸、升级版本会重新分桶」。
     *
     * `rolloutId` 必须是**设备本地随机生成的 UUID、永不上传** —— 否则就成了用户标识。
     */
    fun isRolloutHit(rolloutId: String, versionCode: Int, percent: Int): Boolean {
        if (percent <= 0) return false
        if (percent >= 100) return true
        return bucket("$rolloutId:$versionCode") < percent
    }

    /** SHA-256 前 4 字节当无符号整数，取模 100 —— 与岱宗盒子同一套分桶口径 */
    internal fun bucket(seed: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val value = ((digest[0].toInt() and 0xFF) shl 24) or
            ((digest[1].toInt() and 0xFF) shl 16) or
            ((digest[2].toInt() and 0xFF) shl 8) or
            (digest[3].toInt() and 0xFF)
        return Math.floorMod(value, 100)
    }

    /**
     * 此刻该显示哪些公告：先按时间窗/版本窗过滤，再把「太老」的提级为强更，
     * 最后按等级降序、id 降序（新公告 id 通常更大 → 更靠前）。
     *
     * @param seenIds 已经点掉过的公告 id（不参与过滤，由调用方决定怎么展示「历史」）
     */
    fun visible(
        feed: AppReleaseFeed,
        nowMillis: Long,
        currentVersionCode: Int,
    ): List<Announcement> = feed.announcements
        .filter { it.inWindow(nowMillis) && it.inVersionRange(currentVersionCode) }
        .map { it.escalated(currentVersionCode) }
        .sortedWith(compareByDescending<Announcement> { it.level.rank }.thenByDescending { it.id })

    /** 必须确认才能继续用的那一条（有的话） */
    fun blocking(feed: AppReleaseFeed, nowMillis: Long, currentVersionCode: Int): Announcement? =
        visible(feed, nowMillis, currentVersionCode).firstOrNull { it.needsAck }

    private fun Announcement.inWindow(nowMillis: Long): Boolean {
        val start = startsAt
        val end = endsAt
        return (start == null || nowMillis >= start) && (end == null || nowMillis < end)
    }

    private fun Announcement.inVersionRange(currentVersionCode: Int): Boolean {
        val min = minVersionCode
        val max = maxVersionCode
        return (min == null || currentVersionCode >= min) && (max == null || currentVersionCode <= max)
    }

    /** 低于 forceBelow 的客户端：把这条提成强更，并要求确认 */
    private fun Announcement.escalated(currentVersionCode: Int): Announcement {
        val floor = forceBelow ?: return this
        if (currentVersionCode >= floor) return this
        return copy(level = AnnouncementLevel.FORCE_UPDATE, mustAck = true)
    }
}
