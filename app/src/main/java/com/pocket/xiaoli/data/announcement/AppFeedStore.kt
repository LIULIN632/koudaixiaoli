package com.pocket.xiaoli.data.announcement

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 更新与公告的**记账本**：上次检查时刻、上次拉到的 feed 原文、已读公告 id、
 * 稍后提醒的版本号、灰度用的设备 UUID。
 *
 * ## 为什么独立一个 prefs 文件
 *
 * 这些键只有这个功能用，而且**丢了也不心疼**（最多重复提醒一次）。
 * 本项目对这种东西的既有做法就是独立小文件（见 `ProactiveStore` 的注释），
 * 塞进 `pocket_xiaoli_prefs` 只会让主容器更难读。
 *
 * ⚠️ 它会被 `AppContainer` 的「清空全部数据」扫到（那里按 `shared_prefs/` 目录枚举），
 * 所以不需要额外登记 —— 但**也别把这份文件想成需要备份的数据**。
 */
class AppFeedStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 检查节流 ────────────────────────────────────────────────────────

    fun lastCheckAt(): Long = prefs.getLong(KEY_LAST_CHECK_AT, 0L)

    fun setLastCheckAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()
    }

    /** 上次拉到的 feed 原文：网络挂了也要能显示上一次的公告（而不是白屏） */
    fun cachedFeedJson(): String? = prefs.getString(KEY_CACHED_FEED, null)

    fun cacheFeed(json: String) {
        prefs.edit().putString(KEY_CACHED_FEED, json).apply()
    }

    // ── 灰度用的设备标识 ────────────────────────────────────────────────

    /**
     * 灰度分桶用的随机 UUID。
     *
     * ⚠️ **只在本机生成、只在本机使用、永不上传** —— 它一旦上传就成了设备标识，
     * 撞「禁止上传用户数据」那条红线。它的全部用途是让同一个设备对同一个版本
     * 永远落在同一个灰度桶里（见 `ReleaseGate.isRolloutHit`）。
     */
    fun rolloutId(): String {
        val existing = prefs.getString(KEY_ROLLOUT_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ROLLOUT_ID, fresh).commit() // 立刻落盘：分桶必须稳定
        return fresh
    }

    // ── 已读去重 ────────────────────────────────────────────────────────

    fun seenIds(): Set<String> =
        prefs.getString(KEY_SEEN_IDS, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toSet()

    /** 记一条已读；只留最近 [MAX_SEEN] 条，防止 prefs 无限长 */
    fun markSeen(id: String) {
        if (id.isBlank()) return
        val next = (seenIds() + id).toList().takeLast(MAX_SEEN)
        prefs.edit().putString(KEY_SEEN_IDS, next.joinToString("\n")).apply()
    }

    fun markSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = (seenIds() + ids).toList().takeLast(MAX_SEEN)
        prefs.edit().putString(KEY_SEEN_IDS, next.joinToString("\n")).apply()
    }

    // ── 「稍后提醒」────────────────────────────────────────────────────

    /** 用户对这個 versionCode 点了「稍后提醒」：它不再进红点，但仍在页面里可见 */
    fun snoozedVersionCode(): Int = prefs.getInt(KEY_SNOOZED_VERSION, 0)

    fun snoozeVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_SNOOZED_VERSION, versionCode).apply()
    }

    /** 上次发过本地通知的版本号，避免同一个版本反复打扰 */
    fun notifiedVersionCode(): Int = prefs.getInt(KEY_NOTIFIED_VERSION, 0)

    fun setNotifiedVersionCode(versionCode: Int) {
        prefs.edit().putInt(KEY_NOTIFIED_VERSION, versionCode).apply()
    }

    /** 上次发过通知的公告 id（新公告才提醒） */
    fun notifiedAnnouncementIds(): Set<String> =
        prefs.getString(KEY_NOTIFIED_ANNOUNCEMENTS, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toSet()

    fun setNotifiedAnnouncementIds(ids: Collection<String>) {
        prefs.edit()
            .putString(KEY_NOTIFIED_ANNOUNCEMENTS, ids.toList().takeLast(MAX_SEEN).joinToString("\n"))
            .apply()
    }

    // ── 检查与提醒的本地偏好 ────────────────────────────────────────────

    /**
     * 更新渠道：`auto` / `gitee` / `github`（见 [com.pocket.xiaoli.network.AppUpdateChecker.CHANNEL_AUTO]）。
     *
     * 不参与备份：它是「这台设备在国内哪条路快」的本地偏好，
     * 换机之后按自动试更合理。
     */
    fun preferredChannel(): String = prefs.getString(KEY_CHANNEL, "auto").orEmpty().ifBlank { "auto" }

    fun setPreferredChannel(channel: String) {
        prefs.edit().putString(KEY_CHANNEL, channel).apply()
    }

    /**
     * 要不要在后台定期检查（默认开）。
     *
     * 关掉只影响 L2（WorkManager 轮询）；**打开 App 时的 L1 检查不受影响** ——
     * 否则用户关了个开关就再也收不到更新提示，那不是他想要的。
     */
    fun backgroundEnabled(): Boolean = prefs.getBoolean(KEY_BACKGROUND_ENABLED, true)

    fun setBackgroundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_ENABLED, enabled).apply()
    }

    /**
     * 要不要**主动**提醒更新（默认开）。2026-10-29 加：不想更新的人可以关掉打扰。
     *
     * 它挡的是"App 自己凑上来"的三处 —— 自动弹的更新抽屉、通知栏的更新推送、
     * 消息入口那颗更新红点。**这三处之外一律不动**：
     *
     * - 检查照旧（feed 里还带着公告，停了检查公告也一起瞎了）；
     * - 「设置 → 更新 → 检查更新」这个手动入口照旧，想更新随时能更新；
     * - **强制更新照旧弹**：那条是"你这个版本已不受支持"的安全底线，
     *   能被一个开关关掉就不叫底线了（同 [updatesEnabled] 在 F-Droid 构建上的处理思路）。
     *
     * 与 [backgroundEnabled] 是两回事：那个管"要不要去问"，这个管"问到了要不要说"。
     */
    fun pushEnabled(): Boolean = prefs.getBoolean(KEY_PUSH_ENABLED, true)

    fun setPushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH_ENABLED, enabled).apply()
    }

    /**
     * 要不要走内测通道（默认**关**）。
     *
     * 它只决定本机读 feed 的 `beta` 块还是 `app` 块，**不上传任何东西** ——
     * 服务端并没有"这个用户在内测"的名单，所以也谈不上隐私：改了开关，别人看不见。
     *
     * 与「更新渠道」（`auto`/`gitee`/`github`）是两回事：那个选**先问哪个仓**，这个选**要哪条流水线的包**。
     */
    fun betaEnabled(): Boolean = prefs.getBoolean(KEY_BETA_ENABLED, false)

    fun setBetaEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BETA_ENABLED, enabled).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /**
     * 反馈接口地址（`feed.json` 顶层 `endpoints.feedback`）。
     *
     * 为什么要跟着 feed 走、而不是编在 APK 里：换服务器时**不用发版**。
     * 老客户端读不到这个键就回落到编译期默认值（见 `FeedbackRemote.endpoint`），
     * 所以下线这个键不会让反馈功能消失。
     *
     * 不吃备份：它是「当前该往哪台服务器发」的运行时配置，换机由 feed 重新给。
     */
    fun feedbackEndpoint(): String? = prefs.getString(KEY_FEEDBACK_ENDPOINT, null)

    fun setFeedbackEndpoint(url: String?) {
        prefs.edit().apply {
            if (url.isNullOrBlank()) remove(KEY_FEEDBACK_ENDPOINT) else putString(KEY_FEEDBACK_ENDPOINT, url)
        }.apply()
    }

    companion object {
        private const val PREFS = "pocket_app_feed"
        private const val KEY_LAST_CHECK_AT = "last_check_at"
        private const val KEY_CACHED_FEED = "cached_feed"
        private const val KEY_ROLLOUT_ID = "rollout_id"
        private const val KEY_SEEN_IDS = "seen_ids"
        private const val KEY_SNOOZED_VERSION = "snoozed_version"
        private const val KEY_NOTIFIED_VERSION = "notified_version"
        private const val KEY_NOTIFIED_ANNOUNCEMENTS = "notified_announcements"
        private const val KEY_BACKGROUND_ENABLED = "background_enabled"
        private const val KEY_PUSH_ENABLED = "push_enabled"
        private const val KEY_BETA_ENABLED = "beta_enabled"
    private const val KEY_CHANNEL = "update_channel"
        private const val KEY_FEEDBACK_ENDPOINT = "feedback_endpoint"

        /** 已读 id 最多记这么多条（一条公告几十字节，200 条也就几 KB） */
        const val MAX_SEEN = 200
    }
}
