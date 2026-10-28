package com.pocket.xiaoli.core.knowledge

/**
 * 知识库「时效」判定的**平台无关实现**（Android 与 Windows 桌面端共用）。
 *
 * ## 为什么不用 `java.time`
 *
 * 这份代码要放进 KMP 的 `commonMain`，那里不能引用 `java.*`。所以：
 * - 日期用 **epochDay（1970-01-01 起的天数）** 表达，调用方用各平台的日期类型换算；
 * - 需要展示的日期用调用方传进来的 **ISO 字符串**（`9999-12-31` 这类）原样带出，
 *   这样不必在共享层做日期格式化。
 *
 * ## 为什么是纯函数
 *
 * 过滤依赖「今天是哪天」。做成可注入的纯函数才能单测边界（今天正好等于最后有效日）。
 * 而且**必须在每次检索时跑**，不能在加载时跑一次 —— Android 进程可以存活数天，
 * 加载时判定会把昨天到期的通知当成今天的安排。
 */
sealed interface ExpiryDecision {
    /** 在有效期内（或本来就没有失效日期）。 */
    data object Include : ExpiryDecision

    /** 已过期，但内容类型允许保留（软过期）。 */
    data class IncludeExpired(val expiredOnIso: String, val daysAgo: Long) : ExpiryDecision

    /** 已过期且内容类型不允许保留（硬过期）。 */
    data object Exclude : ExpiryDecision
}

/** `9999-12-31` 的 epochDay —— 约定俗成的「永不过期」哨兵。 */
const val NO_EXPIRY_EPOCH_DAY: Long = 2_932_896L

/**
 * 单篇判定。
 *
 * @param effectiveToIso 最后有效日（`yyyy-MM-dd`）；null/空/无法解析 ⇒ 视为无期限。
 * @param hardExpiry 内容类型是否为「硬过期」（过期即排除，而不是标记后保留）。
 * @param todayEpochDay 今天（调用方换算）。
 */
fun expiryDecision(effectiveToIso: String?, hardExpiry: Boolean, todayEpochDay: Long): ExpiryDecision {
    val expireEpochDay = effectiveToIso?.let { parseIsoDateToEpochDay(it) } ?: return ExpiryDecision.Include
    if (expireEpochDay >= NO_EXPIRY_EPOCH_DAY) return ExpiryDecision.Include
    // 最后有效日当天仍然有效，只有「今天 > 最后有效日」才算过期
    if (todayEpochDay <= expireEpochDay) return ExpiryDecision.Include
    if (hardExpiry) return ExpiryDecision.Exclude
    return ExpiryDecision.IncludeExpired(
        expiredOnIso = effectiveToIso,
        daysAgo = todayEpochDay - expireEpochDay,
    )
}

/** 过期提醒文案。放在正文最前面，模型与用户都会先看到它。 */
fun expiryNotice(expiredOnIso: String, daysAgo: Long): String =
    "⚠️ 此安排已于 $expiredOnIso 过期（$daysAgo 天前），最新安排请以官方为准。\n\n"

/**
 * `yyyy-MM-dd` → epochDay（Howard Hinnant 的 `days_from_civil`，纯整数运算）。
 *
 * 解析失败返回 null（调用方按「无期限」处理），绝不抛异常 —— 知识库是数据文件，
 * 一条脏日期不该让整个检索失败。
 */
fun parseIsoDateToEpochDay(text: String): Long? {
    val parts = text.trim().split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return civilToEpochDay(year, month, day)
}

/**
 * 公历日期 → epochDay。算法来自 Howard Hinnant 的 `days_from_civil`（公有领域）。
 *
 * 闰年规则（4 / 100 / 400）已包含在算法里，2 月 30 日这类非法日期会被顺延 ——
 * 我们只在解析数据文件时用它，脏数据顺延比抛异常更安全。
 */
fun civilToEpochDay(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400                                   // [0, 399]
    val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1 // [0, 365]
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy           // [0, 146096]
    return era * 146097L + doe - 719468L
}

/** epochDay → 公历（`civil_from_days`），主要给测试与调用方做回写校验用。 */
fun epochDayToIso(epochDay: Long): String {
    var z = epochDay + 719468L
    val era = (if (z >= 0) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    val year = if (m <= 2) y + 1 else y
    return buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(m.toString().padStart(2, '0'))
        append('-')
        append(d.toString().padStart(2, '0'))
    }
}
