package com.pocket.xiaoli.data.feedback

import android.content.Context

/**
 * 本地反馈账本。
 *
 * ## 为什么本地也要记一条
 *
 * 用户点了「提交」，如果那一刻没有网络、没有邮件 App、或者他选了"复制"
 * 却忘了粘贴，这条反馈就凭空消失了 —— 而他自己也记不清写了什么。
 * 所以三个渠道**都先落本地**，页面下方还能翻回去重新复制。
 *
 * 只存最近 [MAX_ENTRIES] 条，纯本机 SharedPreferences，不上传、不进备份。
 */
class FeedbackStore(context: Context) {

    private val prefs = context.getSharedPreferences("feedback", Context.MODE_PRIVATE)

    data class Entry(
        val ticketId: String,
        val category: FeedbackCategory,
        val preview: String,
        val atMillis: Long,
    )

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

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun readRaw(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    private companion object {
        const val KEY = "tickets"
        const val MAX_ENTRIES = 20
    }
}
