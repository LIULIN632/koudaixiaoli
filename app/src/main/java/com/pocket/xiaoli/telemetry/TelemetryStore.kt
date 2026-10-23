package com.pocket.xiaoli.telemetry

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 匿名运行统计的**本地账本**（一个独立的小 prefs 文件）。
 *
 * ## 三条不许破的约定
 *
 * 1. **没有设备标识**。这里的 [installId] 是个随机 UUID，每 30 天整桶换一个新的；
 *    库里不存任何硬件号、Android ID、账号。换桶时连队列一起清 —— 旧队列里的
 *    事件属于旧号，留着就会被算到新号头上。
 * 2. **关掉就什么都不记**。[enabled] 为 false 时上层（[Telemetry]）连队列都不写，
 *    不是"记下来不发"。所以关掉之后再打开，中间那段是真的不存在过。
 * 3. **丢了不心疼**。队列上限 200 条，满了丢最旧的；宁可少一条 `app_open`，
 *    也不能让一个小功能把用户的存储占满。
 */
class TelemetryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── 开关 ────────────────────────────────────────────────────────────

    /** 默认开：首启会告知一次，用户可以在设置里关掉。 */
    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** 首启那条说明有没有给他看过 */
    fun noticeSeen(): Boolean = prefs.getBoolean(KEY_NOTICE_SEEN, false)

    fun markNoticeSeen() {
        prefs.edit().putBoolean(KEY_NOTICE_SEEN, true).apply()
    }

    // ── 匿名安装标识 ────────────────────────────────────────────────────

    /**
     * 当期安装标识。**同一桶内稳定，跨桶变化。**
     *
     * 桶号 = `epochDay(Asia/Shanghai) / 30`，全设备同一时刻换桶 —— 这样 MAU 才有
     * 一个说得清的口径（见 `docs/adr/0001-匿名运行统计.md`）。
     */
    fun installId(nowMs: Long = System.currentTimeMillis()): String {
        val bucket = bucketOf(nowMs)
        if (prefs.getLong(KEY_BUCKET, -1L) != bucket) {
            val fresh = UUID.randomUUID().toString()
            prefs.edit()
                .putLong(KEY_BUCKET, bucket)
                .putString(KEY_IID, fresh)
                .putString(KEY_QUEUE, "")
                .commit() // 立刻落盘：这一次启动就要用它上报
            return fresh
        }
        prefs.getString(KEY_IID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_IID, fresh).commit()
        return fresh
    }

    // ── 队列 ────────────────────────────────────────────────────────────

    fun enqueue(json: String) {
        val items = queued().toMutableList()
        items.add(json)
        while (items.size > MAX_QUEUE) items.removeAt(0)
        prefs.edit().putString(KEY_QUEUE, items.joinToString("\n")).apply()
    }

    fun queued(): List<String> =
        prefs.getString(KEY_QUEUE, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }

    fun replaceQueue(items: List<String>) {
        prefs.edit().putString(KEY_QUEUE, items.joinToString("\n")).apply()
    }

    fun clearQueue() {
        prefs.edit().putString(KEY_QUEUE, "").apply()
    }

    // ── 每日去重 ────────────────────────────────────────────────────────

    /** 同一天同一个 key 只返回一次 true（`update_shown` 这类一天记一次的事件用）。 */
    fun firstTimeToday(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val tag = "$key@${today(nowMs)}"
        val seen = prefs.getString(KEY_SEEN, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
            .toMutableList()
        if (tag in seen) return false
        seen.add(tag)
        prefs.edit().putString(KEY_SEEN, seen.takeLast(MAX_SEEN).joinToString("\n")).apply()
        return true
    }

    // ── 版本变化（用于 update_install 的 from_vc）─────────────────────────

    fun lastVersionCode(): Int = prefs.getInt(KEY_LAST_VC, 0)

    fun setLastVersionCode(value: Int) {
        prefs.edit().putInt(KEY_LAST_VC, value).apply()
    }

    companion object {
        private const val PREFS = "pocket_telemetry"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NOTICE_SEEN = "notice_seen"
        private const val KEY_BUCKET = "bucket"
        private const val KEY_IID = "iid"
        private const val KEY_QUEUE = "queue"
        private const val KEY_SEEN = "seen"
        private const val KEY_LAST_VC = "last_vc"

        /** 队列上限。200 条 × 约 80 字节 ≈ 16 KB，离 SharedPreferences 的舒适区很远。 */
        const val MAX_QUEUE = 200

        private const val MAX_SEEN = 40

        /** `epochDay = (now + 8h) / 1 天`，避免为一个除法引入 java.time */
        private const val TZ_OFFSET_MS = 8L * 3600 * 1000
        private const val DAY_MS = 24L * 3600 * 1000

        /** 与 `epochDay(Asia/Shanghai) / 30` 等价 */
        fun bucketOf(nowMs: Long): Long = ((nowMs + TZ_OFFSET_MS) / DAY_MS) / 30

        fun today(nowMs: Long): Long = (nowMs + TZ_OFFSET_MS) / DAY_MS
    }
}
