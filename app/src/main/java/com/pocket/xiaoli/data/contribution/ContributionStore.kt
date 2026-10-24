package com.pocket.xiaoli.data.contribution

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** 一条还没送出去的上报。昵称在发送时现取，改名后旧队列跟着新昵称走。 */
data class PendingReport(val action: ContributionAction, val eid: String)

/**
 * 共建榜的**本地账本**（独立 prefs 文件，与匿名统计互不相干）。
 *
 * ## 三条约定
 *
 * 1. **默认关**。没点「加入」之前，这里没有 cid、没有队列、没有任何网络请求。
 * 2. **关掉就清干净**。退出榜单会删掉 cid / 队列 / 每日计数 / 缓存；
 *    服务器侧由 `POST /v1/contribution/leave` 级联删除。
 * 3. **上限在本地也挡一层**。服务端才是权威，但本地先挡掉重复动作，
 *    既省流量，也避免断网时把 50 条同样的「保存笔记」塞进队列。
 *
 * ## cid 与 iid 的区别
 *
 * 匿名统计的 iid 每 30 天轮换（算 MAU 用），拿它上榜会让积分月月清零。
 * cid 是加入榜单时单独生成的 UUID，只在本机保存，与 iid 没有映射关系。
 */
class ContributionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val gson = Gson()

    // ── 开关与身份 ──────────────────────────────────────────────────────

    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun nickname(): String = prefs.getString(KEY_NICKNAME, "").orEmpty()

    fun setNickname(value: String) {
        prefs.edit().putString(KEY_NICKNAME, value).apply()
    }

    /** 取当前 cid；没有就生成一个。**只在加入榜单后调用。** */
    fun contributorId(): String {
        contributorIdOrNull()?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CID, fresh).apply()
        return fresh
    }

    fun contributorIdOrNull(): String? =
        prefs.getString(KEY_CID, null)?.takeIf { it.isNotBlank() }

    // ── 退出榜单（断网也要能记住）───────────────────────────────────────

    /**
     * 退出时先把 cid 记在这里，等联网后由仓库补发删除。
     * 这样「点退出」不需要网络也能立刻停止一切上报。
     */
    fun pendingLeave(): String? =
        prefs.getString(KEY_PENDING_LEAVE, null)?.takeIf { it.isNotBlank() }

    fun setPendingLeave(cid: String) {
        prefs.edit().putString(KEY_PENDING_LEAVE, cid).apply()
    }

    fun clearPendingLeave() {
        prefs.edit().remove(KEY_PENDING_LEAVE).apply()
    }

    /** 退出时清个人痕迹，但保留 [pendingLeave] 这把「删除钥匙」。 */
    fun clearPersonal() {
        val edit = prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_NICKNAME)
            .remove(KEY_CID)
            .remove(KEY_QUEUE)
            .remove(KEY_CACHE)
            .remove(KEY_COUNTS)
        prefs.all.keys
            .filter { it.startsWith(COUNT_PREFIX) }
            .forEach { edit.remove(it) }
        edit.apply()
    }

    // ── 队列 ────────────────────────────────────────────────────────────

    fun pending(): List<PendingReport> =
        prefs.getString(KEY_QUEUE, "").orEmpty()
            .split('\n')
            .mapNotNull { line ->
                val action = ContributionAction.fromWire(line.substringBefore('|'))
                val eid = line.substringAfter('|', "")
                if (action != null && eid.isNotBlank()) PendingReport(action, eid) else null
            }

    fun enqueue(report: PendingReport) {
        val items = pending().toMutableList()
        items.add(report)
        while (items.size > MAX_QUEUE) items.removeAt(0)
        writeQueue(items)
    }

    fun writeQueue(items: List<PendingReport>) {
        prefs.edit()
            .putString(KEY_QUEUE, items.joinToString("\n") { "${it.action.wire}|${it.eid}" })
            .apply()
    }

    fun clearQueue() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    // ── 每日次数 ────────────────────────────────────────────────────────

    /**
     * 占今天的第 N 次。返回 false 表示这个动作今天已经到上限。
     *
     * 日期按 UTC+8 切：与匿名统计的日切一致（`TelemetryStore.today`），
     * 中国没有夏令时，固定偏移不会错一天。
     */
    fun takeDailySlot(action: ContributionAction, nowMs: Long = System.currentTimeMillis()): Boolean {
        val today = dayOf(nowMs).toString()
        val raw = prefs.getString(COUNT_PREFIX + action.wire, null)
        val day = raw?.substringBefore('|')
        val count = raw?.substringAfter('|', "")?.toIntOrNull() ?: 0
        if (day == today && count >= action.dailyCap) return false
        val next = if (day == today) count + 1 else 1
        prefs.edit().putString(COUNT_PREFIX + action.wire, "$today|$next").apply()
        return true
    }

    // ── 榜单缓存 ────────────────────────────────────────────────────────

    fun cachedBoard(): ContributionBoard? {
        val raw = prefs.getString(KEY_CACHE, null) ?: return null
        return runCatching {
            val cached = gson.fromJson(raw, CachedBoard::class.java) ?: return@runCatching null
            cached.toBoard()
        }.getOrNull()
    }

    fun cacheBoard(board: ContributionBoard) {
        val cached = CachedBoard(
            category = board.category.wire,
            period = board.period.wire,
            entries = board.entries.map {
                CachedEntry(it.rank, it.nickname, it.points, it.mine)
            },
            me = board.me?.let { CachedMe(it.rank, it.nickname, it.points) },
            fetchedAt = board.fetchedAt,
        )
        prefs.edit().putString(KEY_CACHE, gson.toJson(cached)).apply()
    }

    companion object {
        private const val PREFS = "pocket_contribution"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_CID = "cid"
        private const val KEY_PENDING_LEAVE = "pending_leave"
        private const val KEY_QUEUE = "queue"
        private const val KEY_CACHE = "board_cache"
        private const val KEY_COUNTS = "counts" // 仅用于 clearPersonal 的旧键占位
        private const val COUNT_PREFIX = "count_"

        const val MAX_QUEUE = 40

        private val DAY_ZONE = ZoneOffset.ofHours(8)

        fun dayOf(nowMs: Long): LocalDate =
            LocalDate.ofEpochDay(Math.floorDiv(nowMs + DAY_ZONE.totalSeconds * 1000L, 86_400_000L))
    }

    /** 缓存用**可空**字段：Gson 绕过构造器，坏数据不能把整页带崩。 */
    private data class CachedBoard(
        val category: String? = null,
        val period: String? = null,
        val entries: List<CachedEntry>? = null,
        val me: CachedMe? = null,
        val fetchedAt: Long = 0,
    ) {
        fun toBoard(): ContributionBoard = ContributionBoard(
            category = ContributionCategory.fromWire(category),
            period = ContributionPeriod.fromWire(period),
            entries = entries.orEmpty().mapNotNull { it.toEntry() },
            me = me?.toMe(),
            fetchedAt = fetchedAt,
            fromCache = true,
        )
    }

    private data class CachedEntry(
        val rank: Int = 0,
        val nickname: String? = null,
        val points: Int = 0,
        val mine: Boolean = false,
    ) {
        fun toEntry(): ContributionEntry? {
            val name = nickname?.trim().orEmpty()
            if (rank <= 0 || name.isEmpty()) return null
            return ContributionEntry(rank, name, points.coerceAtLeast(0), mine)
        }
    }

    private data class CachedMe(
        val rank: Int = 0,
        val nickname: String? = null,
        val points: Int = 0,
    ) {
        fun toMe(): ContributionMe? {
            val name = nickname?.trim().orEmpty()
            if (rank <= 0 || name.isEmpty()) return null
            return ContributionMe(rank, points.coerceAtLeast(0), name)
        }
    }
}
