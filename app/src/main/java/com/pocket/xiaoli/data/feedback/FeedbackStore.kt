package com.pocket.xiaoli.data.feedback

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 本地反馈账本。
 *
 * ## 为什么本地也要记一条
 *
 * 用户点了「提交」，如果那一刻没有网络、没有邮件 App、或者他选了"复制"
 * 却忘了粘贴，这条反馈就凭空消失了 —— 而他自己也记不清写了什么。
 * 所以三个渠道**都先落本地**，页面下方还能翻回去重新复制。
 *
 * 除了账本，这里还管三件「客户端才有资格管」的事：
 *
 * - **提交闸门**：冷却 + 每日上限。没有服务端验证，这里是唯一防线；
 * - **待发队列**：网络/凭据临时故障时把整条草稿留在本机，下次进页面补发；
 * - **回复缓存**：拉取结果落盘并按工单号合并，节流命中时也有内容可渲染。
 *
 * 只存最近 [MAX_ENTRIES] 条账本，纯本机 SharedPreferences，不上传、不进备份。
 * 不放 cacheDir：清缓存不该把用户提过的工单清掉，那样他就再也找不回回复了。
 */
class FeedbackStore(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs = appContext.getSharedPreferences("feedback", Context.MODE_PRIVATE)

    data class Entry(
        val ticketId: String,
        val category: FeedbackCategory,
        val preview: String,
        val atMillis: Long,
    )

    /** 一条还没送出去的反馈。存全文而不是摘要，补发时不能丢字。 */
    data class Pending(
        val ticketId: String,
        val category: FeedbackCategory,
        val content: String,
        val contact: String,
        val includeDiagnostics: Boolean,
        val atMillis: Long,
        /** 提交时拼好的设备信息快照；补发时版本可能已经变了，按当时的来。 */
        val diagnostics: String?,
        /** 表格明确拒收（多半是列名对不上）。自动补发跳过它，等用户自己处理。 */
        val blocked: Boolean = false,
    )

    // ── 账本 ──────────────────────────────────────────────────────────

    fun add(ticketId: String, draft: FeedbackDraft, nowMillis: Long) {
        val preview = draft.content.trim().replace('\n', ' ').replace('\t', ' ').take(60)
        val line = listOf(ticketId, draft.category.name, nowMillis.toString(), preview).joinToString("\t")
        val kept = (listOf(line) + readRaw()).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, kept.joinToString("\n")).apply()
    }

    fun recent(limit: Int = MAX_ENTRIES): List<Entry> = readRaw().take(limit).mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 4) return@mapNotNull null
        val at = parts[2].toLongOrNull() ?: return@mapNotNull null
        Entry(
            ticketId = parts[0],
            category = runCatching { FeedbackCategory.valueOf(parts[1]) }.getOrDefault(FeedbackCategory.OTHER),
            preview = parts[3],
            atMillis = at,
        )
    }

    fun clear() = prefs.edit()
        .remove(KEY)
        .remove(KEY_PENDING)
        .remove(KEY_SENT)
        .remove(KEY_REPLIES)
        .remove(KEY_REPLIES_AT)
        .remove(KEY_LAST_SUBMIT_AT)
        .remove(KEY_SUBMIT_DAY)
        .remove(KEY_SUBMIT_COUNT)
        .remove(KEY_ANON_ID)
        .apply()

    // ── 匿名标识 ──────────────────────────────────────────────────────

    /**
     * 把同一台设备的多条反馈串起来，不含学号，也无法反查设备。
     *
     * 用 [Settings.Secure.ANDROID_ID] 的 SHA-256 前 8 位，不用 IMEI / 序列号。
     * 只有用户按下「直接发送」时才会读这个值（见 [FeedbackRemote.submit] 的调用点），
     * 页面加载、复制、邮件都不会碰它。读不到时退回本机随机 ID。
     */
    fun anonId(): String {
        androidIdHash()?.let { return it }
        prefs.getString(KEY_ANON_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "").take(8)
        prefs.edit().putString(KEY_ANON_ID, id).apply()
        return id
    }

    /** 旧模拟器上 ANDROID_ID 会固定成这个值，当无效。 */
    private fun androidIdHash(): String? {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()
        if (androidId.isEmpty() || androidId.equals(BUG_ANDROID_ID, ignoreCase = true)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("xiaoli|feedback|$androidId".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(8)
    }

    // ── 提交闸门 ──────────────────────────────────────────────────────

    fun submitGate(nowMillis: Long = System.currentTimeMillis()): SubmitGate {
        val last = prefs.getLong(KEY_LAST_SUBMIT_AT, 0L)
        val today = dayStamp(nowMillis)
        val count = if (prefs.getString(KEY_SUBMIT_DAY, null) == today) {
            prefs.getInt(KEY_SUBMIT_COUNT, 0)
        } else {
            0
        }
        return FeedbackLimits.gate(nowMillis, last, count)
    }

    /** 用户每发起一次「直接发送」就记一次，无论成功、排队还是被拒。 */
    fun markSubmitAttempt(nowMillis: Long = System.currentTimeMillis()) {
        val today = dayStamp(nowMillis)
        val count = if (prefs.getString(KEY_SUBMIT_DAY, null) == today) {
            prefs.getInt(KEY_SUBMIT_COUNT, 0)
        } else {
            0
        }
        prefs.edit()
            .putLong(KEY_LAST_SUBMIT_AT, nowMillis)
            .putString(KEY_SUBMIT_DAY, today)
            .putInt(KEY_SUBMIT_COUNT, count + 1)
            .apply()
    }

    // ── 待发队列 ──────────────────────────────────────────────────────

    fun pending(): List<Pending> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ticket = o.optString("ticket")
            if (ticket.isBlank()) return@mapNotNull null
            Pending(
                ticketId = ticket,
                category = runCatching { FeedbackCategory.valueOf(o.optString("category")) }
                    .getOrDefault(FeedbackCategory.OTHER),
                content = o.optString("content"),
                contact = o.optString("contact"),
                includeDiagnostics = o.optBoolean("diagnostics"),
                atMillis = o.optLong("at"),
                diagnostics = o.optString("environment").takeIf { it.isNotBlank() },
                blocked = o.optBoolean("blocked"),
            )
        }
    }

    /** false 表示队列已满，调用方要把内容留在输入框里，别清空。 */
    fun enqueue(item: Pending): Boolean {
        val list = pending()
        if (list.any { it.ticketId == item.ticketId }) return true
        if (list.size >= FeedbackLimits.MAX_PENDING) return false
        writePending(list + item)
        return true
    }

    fun removePending(ticketId: String) {
        writePending(pending().filterNot { it.ticketId == ticketId })
    }

    fun markBlocked(ticketId: String) {
        writePending(pending().map { if (it.ticketId == ticketId) it.copy(blocked = true) else it })
    }

    private fun writePending(items: List<Pending>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("ticket", item.ticketId)
                    .put("category", item.category.name)
                    .put("content", item.content)
                    .put("contact", item.contact)
                    .put("diagnostics", item.includeDiagnostics)
                    .put("at", item.atMillis)
                    .put("environment", item.diagnostics.orEmpty())
                    .put("blocked", item.blocked)
            )
        }
        prefs.edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    // ── 已送达的工单（用来判断「等回复」） ─────────────────────────────

    fun sentIds(): Set<String> = sentList().toSet()

    fun markSent(ticketId: String) {
        val next = (listOf(ticketId) + sentList().filterNot { it == ticketId }).take(MAX_ENTRIES)
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        prefs.edit().putString(KEY_SENT, arr.toString()).apply()
    }

    private fun sentList(): List<String> {
        val raw = prefs.getString(KEY_SENT, null) ?: return emptyList()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
    }

    // ── 回复缓存与节流 ────────────────────────────────────────────────

    fun cachedReplies(): Map<String, FeedbackReply> {
        val raw = prefs.getString(KEY_REPLIES, null) ?: return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return buildMap {
            obj.keys().forEach { ticket ->
                val o = obj.optJSONObject(ticket) ?: return@forEach
                val text = o.optString("text")
                if (text.isNotBlank()) put(ticket, FeedbackReply(text, o.optString("at")))
            }
        }
    }

    /**
     * 按工单号合并，只保留账本里还在的工单。
     *
     * 不合并的话，每次拉到一条新回复都会冲掉之前缓存的；不裁剪的话，
     * 回复缓存会随使用时间无限长下去，而账本本身只留 20 条。
     */
    fun cacheReplies(replies: Map<String, FeedbackReply>) {
        val keep = sentIds()
        val merged = cachedReplies().filterKeys { it in keep } + replies.filterKeys { it in keep }
        val obj = JSONObject()
        merged.forEach { (ticket, reply) ->
            obj.put(ticket, JSONObject().put("text", reply.text).put("at", reply.at))
        }
        prefs.edit().putString(KEY_REPLIES, obj.toString()).apply()
    }

    /**
     * 距上次拉回复是否够久。还在等回复的人 10 分钟一次，全都回复过的人 6 小时一次——
     * 把两者都设成 6 小时，用户提交完当天基本看不到回应；都设成 10 分钟，
     * 反复进页面就是在烧飞书免费版的月配额。
     *
     * @param force 手动「检查回复」时绕过节流。
     */
    fun shouldFetchReplies(
        nowMillis: Long = System.currentTimeMillis(),
        hasPending: Boolean,
        force: Boolean = false,
    ): Boolean {
        if (force) return true
        val last = prefs.getLong(KEY_REPLIES_AT, 0L)
        val gap = if (hasPending) REPLY_GAP_PENDING else REPLY_GAP_IDLE
        return nowMillis - last >= gap
    }

    fun markRepliesFetched(nowMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_REPLIES_AT, nowMillis).apply()
    }

    private fun dayStamp(nowMillis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMillis))

    private fun readRaw(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    private companion object {
        const val KEY = "tickets"
        const val KEY_PENDING = "pending"
        const val KEY_SENT = "sent"
        const val KEY_REPLIES = "replies"
        const val KEY_REPLIES_AT = "replies_at"
        const val KEY_LAST_SUBMIT_AT = "last_submit_at"
        const val KEY_SUBMIT_DAY = "submit_day"
        const val KEY_SUBMIT_COUNT = "submit_count"
        const val KEY_ANON_ID = "anon_id"
        const val MAX_ENTRIES = 20
        const val BUG_ANDROID_ID = "9774d56d682e549c"
        const val REPLY_GAP_PENDING = 10L * 60 * 1000
        const val REPLY_GAP_IDLE = 6L * 60 * 60 * 1000
    }
}
