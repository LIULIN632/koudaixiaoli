package com.pocket.xiaoli.data.proactive

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 主动提醒的**记账本**：档位、冷却时间戳、闲聊去重、成绩游标、余额快照。
 *
 * ## 为什么单独一个 prefs 文件，而不是塞进 `AppContainer`
 *
 * 这些键只有这一个功能用、而且**都是"可以随时丢掉的记账"**（丢了最多重复提醒一次，
 * 不会丢用户数据）。本项目对这种东西的既有做法就是独立小文件
 *（`ZhengfangImportMemory` 的 `egg_rice_import_memory`、`QzxyValveStore` 的自有文件），
 * 把二十来个键塞进主 prefs 只会让主容器更难读。
 *
 * ## 为什么用 StateFlow 而不是"读属性"
 *
 * 设置页要即时回显档位。prefs 的 getter **不参与 Compose 快照系统**，改了不会触发重组
 *（这个坑在本项目的会话列表与底栏样式上各踩过一次），所以档位必须走一个 Flow。
 */
class ProactiveStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _level = MutableStateFlow(readLevel())
    val level: StateFlow<ProactiveLevel> = _level

    fun setLevel(level: ProactiveLevel) {
        _level.value = level
        prefs.edit().putString(KEY_LEVEL, level.name).apply()
    }

    private fun readLevel(): ProactiveLevel =
        prefs.getString(KEY_LEVEL, null)
            ?.let { raw -> ProactiveLevel.entries.firstOrNull { it.name == raw } }
            ?: ProactiveLevel.STANDARD

    /** 读出规则层要的那份快照（不参与 Compose，评估循环每次现读）。 */
    fun memory(): ProactiveMemory {
        val all = prefs.all
        val shown = all.entries
            .filter { it.key.startsWith(PREFIX_SHOWN) && it.value is Long }
            .associate { it.key.removePrefix(PREFIX_SHOWN) to it.value as Long }
        // ⚠️ `shown_any` 也是 `shown_` 前缀，会被上面收进 map ——
        // 它必须单独读，否则会多出一条叫 "any" 的规则（历史上这类前缀撞车踩过一次）
        val lastAny = shown[KEY_ANY_SUFFIX] ?: 0L
        val dismissed = all.entries
            .filter { it.key.startsWith(PREFIX_DISMISS) && it.value is Int }
            .associate { it.key.removePrefix(PREFIX_DISMISS) to it.value as Int }
        return ProactiveMemory(
            lastAnyAt = lastAny,
            lastShown = shown - KEY_ANY_SUFFIX,
            dismissed = dismissed,
            lastCalendarSeenKey = calendarSeenKey(),
        )
    }

    /**
     * 记下"自动冒过一次"。
     *
     * 闲话也走这里（因此**共用** `shown_any` 这个全局节流）—— 参考实现曾经让闲话
     * 只写自己的时间戳，于是它绕开全局冷却，正事气泡刚收、闲话立刻接上。
     */
    fun markShown(id: String) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(PREFIX_SHOWN + id, now).putLong(PREFIX_SHOWN + KEY_ANY_SUFFIX, now).apply()
    }

    /**
     * 记下"用户戳了一下，它回了一句"。
     *
     * 与 [markShown] 的区别是**不写全局时间戳**：主动逗它不该把正事气泡憋回去 15 分钟。
     */
    fun markTapped(id: String) {
        prefs.edit().putLong(PREFIX_SHOWN + id, System.currentTimeMillis()).apply()
    }

    /** 用户按掉了气泡：这条规则的冷却翻倍（下次更晚再提）。 */
    fun markDismissed(id: String) {
        val n = prefs.getInt(PREFIX_DISMISS + id, 0) + 1
        prefs.edit().putInt(PREFIX_DISMISS + id, n).apply()
    }

    /** 用户**点开了**气泡（说明这条有用）：把它的退避清零。 */
    fun markUseful(id: String) {
        prefs.edit().putInt(PREFIX_DISMISS + id, 0).apply()
    }

    // ── 闲聊去重 ──

    fun recentChatter(): List<String> =
        prefs.getString(KEY_CHATTER_RECENT, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }

    fun rememberChatterLine(lineId: String) {
        val next = (listOf(lineId) + recentChatter()).distinct().take(CHATTER_RECENT_MAX)
        prefs.edit().putString(KEY_CHATTER_RECENT, next.joinToString(",")).apply()
    }

    // ── 成绩游标 ──

    /**
     * "成绩看到哪儿了"。
     *
     * ⚠️ **首次读取会把游标设成此刻**，而不是 0：成绩存档表里可能已经躺着几十门历史成绩，
     * 游标从 0 起算的话，用户装上这个版本的第一眼就是"有 42 门新成绩出了" —— 那是骚扰，
     * 而且不真（那些成绩他早就看过）。从此刻起算，只有**之后**新落库的才算新。
     */
    fun gradeSeenAt(): Long {
        val stored = prefs.getLong(KEY_GRADE_SEEN_AT, 0L)
        if (stored > 0L) return stored
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_GRADE_SEEN_AT, now).apply()
        return now
    }

    /** 冒过气泡就把游标推到现在，避免同几门成绩反复提醒（"冒过即消费信号"）。 */
    fun setGradeSeenAt(value: Long) {
        prefs.edit().putLong(KEY_GRADE_SEEN_AT, value).apply()
    }

    // ── 校历节点游标 ──

    /**
     * "哪个校历节点已经说过了"。
     *
     * 存的是**内容键**（`"${title}@${start}"`），不是时间戳 —— 这点与新成绩游标刻意不同。
     * 成绩是"这一刻之前入库的都不算新"，而校历节点是离散的、一学年就那么几条。
     * 用时间戳的话，说完「寒假还有 3 天」、12 小时冷却一过，离假期只剩 2 天，
     * 它会把**同一件事**再说一遍 —— 正是 `ProactiveRules` 里那条血泪
     *（「冷却只负责多久不再说，不负责这件事已经说过了」）的反面教材。
     * 用内容键，同一个节点这辈子只说一次。
     *
     * 只留**一条**，不存集合：一次只可能宣布"最近的那个即将到来的节点"，
     * 说完就轮到下一个，旧键留着没有意义（也没有"回头再提醒一次说过的事"这种需求）。
     *
     * ⚠️ 键名不许以 `shown_` / `dismiss_` / `balance_` 开头：那三个前缀会被
     * [memory] 整片收走，凭空多出一条假规则（`shown_any` 已经踩过一次）。
     */
    fun calendarSeenKey(): String = prefs.getString(KEY_CALENDAR_SEEN, "").orEmpty()

    fun setCalendarSeenKey(key: String) {
        prefs.edit().putString(KEY_CALENDAR_SEEN, key).apply()
    }

    // ── 天气去重 ──

    /**
     * "哪些天气已经说过了"（`rain@2026-10-22` 这样的内容键）。
     *
     * 与校历游标同一种做法，区别是存一个**小集合**：一天里可能先后有雨、雷、雪，
     * 单条游标会把前一件覆盖掉，于是雨说完、雷说完、下一轮又轮到雨。
     *
     * 这些键都带日期，所以只留最近 [WEATHER_SEEN_MAX] 条即可 —— 跨天的旧键
     * 即使留着也不会再命中（新一天是新键），不需要按日期清理。
     */
    fun weatherSeenKeys(): Set<String> =
        prefs.getString(KEY_WEATHER_SEEN, "").orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()

    fun rememberWeatherSeenKey(key: String) {
        if (key.isBlank()) return
        val next = (weatherSeenKeys() + key).toList().takeLast(WEATHER_SEEN_MAX)
        prefs.edit().putString(KEY_WEATHER_SEEN, next.joinToString(",")).apply()
    }

    // ── 余额快照（跨启动可用）──

    /**
     * 记住一次查到的余额。
     *
     * 为什么需要它：电费与校园卡的仓库缓存**只在内存里活 5 分钟**，
     * 进程一重启就没了。而"余额不足"这种提醒恰恰该在冷启动时说 ——
     * 只读内存缓存的话，用户重启后再也听不到这句话。
     *
     * [identity] 是"这笔余额属于谁"（校园卡手机号 / 宿舍楼栋+房间+编号）。
     * ⚠️ 必须带上它：换了宿舍或换了卡号之后，旧余额对新账户毫无意义
     *（本项目在宿舍编号上吃过一次同类亏："改了编号，旧余额必须作废"）。
     */
    fun rememberBalance(kind: String, identity: String, value: Double) {
        prefs.edit()
            .putString(balanceKey(kind, identity), "$value|${System.currentTimeMillis()}")
            .apply()
    }

    /** 读回上次记住的余额；超过 [maxAgeMs] 视为过期（余额是会变的，念旧数字等于说错话）。 */
    fun balanceSnapshot(kind: String, identity: String, maxAgeMs: Long): Double? {
        val raw = prefs.getString(balanceKey(kind, identity), null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 2) return null
        val value = parts[0].toDoubleOrNull() ?: return null
        val at = parts[1].toLongOrNull() ?: return null
        if (System.currentTimeMillis() - at > maxAgeMs) return null
        return value
    }

    private fun balanceKey(kind: String, identity: String) = "$PREFIX_BALANCE$kind|$identity"

    companion object {
        private const val PREFS = "pocket_proactive"
        private const val KEY_LEVEL = "level"
        private const val PREFIX_SHOWN = "shown_"
        private const val KEY_ANY_SUFFIX = "any"
        private const val PREFIX_DISMISS = "dismiss_"
        private const val KEY_CHATTER_RECENT = "chatter_recent"
        private const val KEY_GRADE_SEEN_AT = "grade_seen_at"

        /**
         * 校历节点游标。刻意**不带** `shown_` / `dismiss_` / `balance_` 前缀
         *（见 [calendarSeenKey] 的注释）。
         */
        private const val KEY_CALENDAR_SEEN = "calendar_seen"

        /**
         * 已经说过的天气事件内容键（`kind@date`）。
         *
         * 与校历游标不同，天气一天里可能有雨、雷、雪几件不同的事，所以存的是一个
         * 小集合而不是单个游标。键名同样刻意避开三个前缀（见 [calendarSeenKey]）。
         */
        private const val KEY_WEATHER_SEEN = "weather_seen"

        /** 天气去重集合最多记这么多条（一天最多几类天气，8 条已远超需要）。 */
        private const val WEATHER_SEEN_MAX = 8

        private const val PREFIX_BALANCE = "balance_"

        /** 最近说过的闲聊句最多记这么多条（参考实现同为 12）。 */
        const val CHATTER_RECENT_MAX = 12

        /**
         * 余额快照的最长有效期。
         *
         * 24 小时：一天之内说"只剩 ¥12"基本还成立；再久就可能是刚充过钱了，
         * 那时候提醒用户"余额不足"比不说更糟。
         */
        const val BALANCE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

        const val KIND_CARD = "card"
        const val KIND_ELECTRICITY = "electricity"
    }
}
