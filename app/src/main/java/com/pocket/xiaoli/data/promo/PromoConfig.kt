package com.pocket.xiaoli.data.promo

import com.google.gson.JsonParser
import java.net.URI

/**
 * 推广位的动作：点一下发生什么。
 *
 * 刻意做成**封闭集合**而不是直接存一个 URL 字符串：
 * 推广内容来自服务端，是一个**不受本 App 控制的下游数据源**。
 * 如果只存 URL，那么服务端一旦被改（或配置写错），App 就会变成
 * "任人指定跳转目标"的跳板 —— 那是下游数据信任问题里最典型的一种。
 * 封闭集合 + 域名白名单把这件事关在笼子里。
 */
sealed interface PromoAction {
    /** 外部链接。**必须**通过 [PromoValidator] 的白名单校验才会被接受。 */
    data class External(val url: String) : PromoAction

    /** App 内跳转。同样走白名单（见 [PromoValidator.ALLOWED_ROUTES]）。 */
    data class Route(val route: String) : PromoAction
}

/**
 * 一条推广位。
 *
 * 字段全部来自服务端 JSON，**任何一条校验不过就单独丢弃**，不影响其余条目 ——
 * 一条写错的配置不能让整个推广区变空白。
 */
data class Promo(
    /** 稳定标识，用于去重与埋点前定位；重复 id 只保留第一条。 */
    val id: String,
    /** 挂在哪个服务介绍页上（对应 [PromoSlot]）。 */
    val slot: PromoSlot,
    val title: String,
    val body: String,
    /** 配图 URL（可选）。放 OSS+CDN，不进 APK。 */
    val imageUrl: String?,
    /** null 表示纯文字推广、不可点。 */
    val action: PromoAction?,
    /** 生效时间窗（Unix 秒）。null 表示不限制。 */
    val startAt: Long?,
    val endAt: Long?,
    val priority: Int,
)

/**
 * 推广位可以挂在哪些页面上。
 *
 * ⚠️ [key] 必须与 `CampusServiceKeys` / `SERVICE_TITLES` 里的服务名**完全一致**：
 * 它是服务端 JSON 里 `"slot"` 的取值。写错就等于该页面永远不显示推广，
 * 而且**不会有任何报错**（校验器只会静默丢弃不认识的 slot）——
 * 这是最难排查的一类配置问题，所以改这里要同步改服务端。
 */
enum class PromoSlot(val key: String) {
    /**
     * 服务页**顶部海报横幅**。
     *
     * ⚠️ 与上面几个不同：那些是"某个服务介绍页内部的推广位"（key 就是服务名），
     * 这个是"服务页首屏的横幅"，key 取页面名。服务端 JSON 里的 `"slot"` 写 `"服务首页"`。
     */
    SERVICE_HOME("服务首页"),
    ZHUANSANGBEN("专升本"),
    FANXIANG_DABA("返乡大巴"),
    KAOYAN("考研"),
    JIANSHENFANG("健身房"),
    JIAXIAO("驾校"),
    ;

    companion object {
        fun fromKey(key: String): PromoSlot? = entries.firstOrNull { it.key == key }
    }
}

/** 服务端下发的整份配置。 */
data class PromoConfig(
    /** 配置版本号，仅用于日志与缓存比对（不参与"新旧"判断，避免又一套版本语义）。 */
    val version: Int,
    val promos: List<Promo>,
)

/**
 * 推广配置的解析与校验。**纯函数、无 Android 依赖、无 IO**，所以能单测。
 *
 * ## 为什么校验必须严格
 *
 * 这是本 App 唯一一处"**内容与跳转目标都由服务端决定**"的地方。风险有三层：
 *
 * 1. **跳转被劫持**：配置里塞一个钓鱼站，用户从"专升本"页点出去就被骗。
 *    对策：只接受 `https://`，且 host 必须在白名单里（见 [ALLOWED_HOSTS]）。
 * 2. **一条坏数据毁整页**：某条缺 `title`、或 `endAt` 写成字符串，解析抛异常的话
 *    整个推广区就空了。对策：**逐条校验、逐条丢弃**，并在结尾返回成功解析的部分。
 * 3. **越权展示**：给不存在的 slot 配内容、或把过期活动继续展示。
 *    对策：slot 必须在枚举内；时间窗越界的直接过滤掉。
 *
 * ⚠️ [ALLOWED_HOSTS] 是**硬编码**的：新增合作方要发版加域名。
 * 这是刻意的取舍 —— "改个配置就能让 App 跳任意站点"的便利不值得换。
 * 如果将来合作方多到需要动态域名，应该改成**服务端签名 + 客户端验签**，
 * 而不是放开白名单。
 */
object PromoValidator {

    /**
     * 允许跳转的域名白名单。
     *
     * 只放**你自己控制的域名**。合作方的链接建议先落到你的跳转页（或直接用你域名的
     * 短链），这样换合作方不用发版。
     */
    val ALLOWED_HOSTS: Set<String> = setOf(
        // 校园服务常用域名（与 CampusServiceCatalog 里已实测可达的域名保持一致）
        "ehall.yitsd.edu.cn",
        "lib.yitsd.edu.cn",
        "jwxt.yitsd.edu.cn",
        "yitsd.edu.cn",
        "ytu.edu.cn",
        "ydny.ytu.edu.cn",
    )

    /** 允许 App 内跳转的路由。同样封闭 —— 不能让服务端指定任意内部路由。 */
    val ALLOWED_ROUTES: Set<String> = setOf(
        "services",
        "timetable",
        "home",
    )

    /**
     * 允许加载**配图**的域名。
     *
     * 与 [ALLOWED_HOSTS] 分开，理由很简单：图是从哪里取的，和点一下会跳到哪里，是两件事。
     * 2026-10-23 起推广配置本身放在 Gitee（`announce/promos.json`，走 raw），配图同样放
     * 仓库里的 `announce/promo/`，所以这里要放行 Gitee / GitHub 的静态域；
     * 但**跳转目标仍然只有学校域名** —— 不能因为「图在 Gitee」就允许把人往 gitee.com 里带。
     *
     * 顺带说明：图片加载失败只会让那张卡变成纯文字（`PromoBanner` 的兜底），
     * 不存在"图挂了整页空白"。
     */
    val IMAGE_HOSTS: Set<String> = ALLOWED_HOSTS + setOf(
        "gitee.com",
        "giteeusercontent.com",
        "github.com",
        "githubusercontent.com",
    )

    /** 单条推广正文长度上限：防止服务端塞一屏长文把页面撑爆。 */
    private const val MAX_BODY = 600
    private const val MAX_TITLE = 60

    /**
     * 解析整份配置。
     *
     * @param nowSec 当前时间（Unix 秒），用于过滤过期/未生效的条目。注入以便单测。
     * @return 成功解析出的推广位；JSON 完全不可解析时返回**空配置**而不是抛异常。
     */
    fun parse(rawJson: String, nowSec: Long): PromoConfig {
        val root = runCatching { JsonParser.parseString(rawJson).asJsonObject }.getOrNull()
            ?: return PromoConfig(version = 0, promos = emptyList())

        val version = root.intOrNull("version") ?: 0

        // ⚠️ 这里**不能**写 `root.getAsJsonArray("slots")`：`slots` 若存在但不是数组
        // （配置写成 `"slots": "oops"`），getAsJsonArray 会抛 ClassCastException。
        // 这是真实踩过的：单测「完全无法解析的 JSON」就是这么挂的。
        // 一律走 `get()` + 类型判定，拿不到就当没有。
        val arr = root.get("slots")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return PromoConfig(version, emptyList())

        val seenIds = HashSet<String>()
        val out = mutableListOf<Promo>()

        arr.forEach { el ->
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val promo = parseOne(obj, nowSec) ?: return@forEach
            // 同 id 只保留第一条：服务端配重了不该重复展示两条一样的卡片
            if (seenIds.add(promo.id)) out += promo
        }

        // 高优先级在前；同优先级按 id 排序保证顺序稳定（否则每次拉取顺序可能变，看起来像在跳）
        return PromoConfig(version, out.sortedWith(compareByDescending<Promo> { it.priority }.thenBy { it.id }))
    }

    /** 解析并校验单条。任何一项不过就返回 null（调用方丢弃它，而不是整份失败）。 */
    private fun parseOne(obj: com.google.gson.JsonObject, nowSec: Long): Promo? {
        val id = obj.stringOrNull("id")?.trim().orEmpty()
        val slotKey = obj.stringOrNull("slot")?.trim().orEmpty()
        val title = obj.stringOrNull("title")?.trim().orEmpty()

        if (id.isEmpty() || id.length > 64) return null
        val slot = PromoSlot.fromKey(slotKey) ?: return null
        if (title.isEmpty() || title.length > MAX_TITLE) return null

        // enabled 缺省视为**启用**：漏写一个字段就让整份配置静默失效，是很难排查的坑。
        // 下架必须显式写 `"enabled": false` —— 这也让"为什么下架了"在配置里一目了然。
        if (!obj.booleanOrDefault("enabled", default = true)) return null

        val body = obj.stringOrNull("body")?.trim().orEmpty().take(MAX_BODY)

        // 配图与跳转用**两套**白名单：图上哪里取不影响「点一下跳哪里」
        val imageUrl = obj.stringOrNull("image")?.trim()?.takeIf { isAllowedImage(it) }

        val startAt = obj.longOrNull("startAt")
        val endAt = obj.longOrNull("endAt")
        // 时间窗过滤：没到开始时间、或已经结束的，直接不展示
        if (startAt != null && nowSec < startAt) return null
        if (endAt != null && nowSec > endAt) return null
        // 起止写反了属于配置错误：展示出来会永远不生效，不如当作无效丢弃
        if (startAt != null && endAt != null && startAt > endAt) return null

        val action = parseAction(obj)

        return Promo(
            id = id,
            slot = slot,
            title = title,
            body = body,
            imageUrl = imageUrl,
            action = action,
            startAt = startAt,
            endAt = endAt,
            priority = obj.intOrNull("priority") ?: 0,
        )
    }

    /**
     * 解析跳转动作。**不合法就返回 null（降级为不可点的纯文字推广），而不是丢弃整条** ——
     * 文案本身仍然有价值，只是不给它一个危险的跳转目标。
     */
    private fun parseAction(obj: com.google.gson.JsonObject): PromoAction? {
        val act = obj.getAsJsonObject("action") ?: return null
        val kind = act.stringOrNull("kind")?.trim().orEmpty()
        return when (kind) {
            "external" -> act.stringOrNull("url")?.trim()
                ?.takeIf { isAllowedHttps(it) }
                ?.let { PromoAction.External(it) }

            "route" -> act.stringOrNull("route")?.trim()
                ?.takeIf { it in ALLOWED_ROUTES }
                ?.let { PromoAction.Route(it) }

            else -> null
        }
    }

    /**
     * 只放行 `https://` 且 host 在白名单内的地址。
     *
     * 为什么强制 https：明文 http 的跳转在校园网/公共 Wi-Fi 下可被篡改，
     * 而推广位是"点出去就要填报名信息"的场景，被改成钓鱼页后果很实际。
     */
    private fun isAllowedHttps(url: String): Boolean = isHttpsOnHost(url, ALLOWED_HOSTS)

    /** 配图走 [IMAGE_HOSTS]（比跳转白名单多几个静态站），规则同样是 https + 域名后缀匹配。 */
    internal fun isAllowedImage(url: String): Boolean = isHttpsOnHost(url, IMAGE_HOSTS)

    private fun isHttpsOnHost(url: String, hosts: Set<String>): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        // 允许白名单域名本身及其子域名（如 lib.yitsd.edu.cn 命中 yitsd.edu.cn）
        return hosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }

    // ── Gson 容错取值：字段类型不符一律当没有，绝不抛 ──
    //
    // ⚠️ 这三个函数是**必需**的，不能直接用 gson 的 asXxx：
    // `asInt` 遇到字符串 "high" 会抛 NumberFormatException，`getAsJsonArray` 遇到
    // 非数组会抛 ClassCastException。推广配置是外部输入，任何一处抛穿都会让
    // 整个推广区崩掉 —— 单测「字段类型不符」与「完全无法解析」正是钉住这一点。

    private fun com.google.gson.JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    /** 只有真正的数字才取值；字符串数字也当缺失（配置写错就该暴露成缺失，而不是猜） */
    private fun com.google.gson.JsonObject.longOrNull(key: String): Long? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

    private fun com.google.gson.JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt

    private fun com.google.gson.JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default
}
