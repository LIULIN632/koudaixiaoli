package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.knowledge.KeywordRetrieverCore
import com.pocket.xiaoli.core.knowledge.KnowledgeDoc as CoreKnowledgeDoc
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.data.KnowledgeDoc
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 校园知识库检索（简化移植自 Android 端 `KeywordRetriever`）。
 *
 * ## 判据
 *
 * 移动端的打分是「整串命中给大分 + 词项命中给小分 + 字符二元组兜底 + 时效加权」。
 * 桌面端保留同一套骨架，因为它的关键性质是**可解释**：每个分项都能说清为什么命中，
 * 出问题能定位到「是整串没命中还是二元组太多」。
 */
object Knowledge {

    private val docs: List<KnowledgeDoc> = Assets.knowledge

    val all: List<KnowledgeDoc> get() = docs

    val categories: List<String> get() = docs.map { it.category }.filter { it.isNotBlank() }.distinct()

    data class Hit(
        val doc: KnowledgeDoc,
        val score: Double,
        val snippet: String,
        val expired: Boolean,
    )

    private fun snippetOf(body: String, query: String): String {
        val flat = body.replace("\n", " ")
        val index = flat.indexOf(query.take(8), ignoreCase = true)
        if (index < 0) return flat.take(120)
        val from = (index - 40).coerceAtLeast(0)
        val to = (index + 120).coerceAtMost(flat.length)
        val prefix = if (from > 0) "…" else ""
        val suffix = if (to < flat.length) "…" else ""
        return prefix + flat.substring(from, to) + suffix
    }

    /**
     * 检索。**打分规则在共享核心** `com.pocket.xiaoli.core.knowledge.KeywordRetrieverCore`
     * （与 Android 端同一份），本函数只做「桌面模型 ↔ 共享模型」映射。
     *
     * [score] 取共享层的 `rankScore`（已含时效加权），只用于排序与展示相对高低，
     * 不参与任何跨端比较 —— 跨端要比的是"同一份算法给出的顺序"。
     */
    fun search(query: String, topK: Int = 5): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val nowMillis = System.currentTimeMillis()
        val sharedDocs = docs.map {
            CoreKnowledgeDoc(id = it.id, title = it.title, body = it.body, tags = it.tags, updatedAt = it.updatedAt)
        }
        val snippets = KeywordRetrieverCore.search(q, sharedDocs, nowMillis, topK)
        if (snippets.isEmpty()) return emptyList()

        val docsById = docs.associateBy { it.id }
        val today = LocalDate.now()
        return snippets.mapNotNull { snippet ->
            val doc = docsById[snippet.documentId] ?: return@mapNotNull null
            Hit(
                doc = doc,
                score = snippet.rankScore,
                snippet = snippetOf(doc.body, q),
                expired = doc.isExpired(today),
            )
        }
    }
    /** 按 id 取全文（AI 追问「刚才那份」时用）。 */
    fun byId(id: String): KnowledgeDoc? = docs.firstOrNull { it.id == id }

    /**
     * 把命中格式化成给模型的 [K1] 片段。
     *
     * 引用编号用 `id` 的稳定顺序，**不按分数**：分数会随检索词变化，
     * 编号跟着变会让同一段对话里的「[K1]」指到不同文档。
     */
    fun formatForModel(hits: List<Hit>): String {
        if (hits.isEmpty()) return "知识库没有命中。不要凭空编造学校规定；请明确说「知识库里没收录」。"
        return hits.mapIndexed { index, hit ->
            val doc = hit.doc
            val updated = if (doc.updatedAt > 0) {
                Instant.ofEpochMilli(doc.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
            } else {
                doc.effectiveFrom
            }
            buildString {
                append("[K${index + 1}] 《").append(doc.title).append("》")
                append("（").append(doc.category)
                if (doc.owner.isNotBlank()) append("｜").append(doc.owner)
                append("｜更新 ").append(updated)
                if (hit.expired) append("｜⚠️已过有效期")
                append("）\n")
                append("来源：").append(doc.sourceLabel).append("\n")
                append(doc.body.take(1600))
            }
        }.joinToString("\n\n---\n\n")
    }

    /** 一份「知识库有什么」的目录，给 AI 判断该查什么。 */
    fun catalog(): String {
        if (docs.isEmpty()) return "知识库为空。"
        return categories.joinToString("\n") { category ->
            val titles = docs.filter { it.category == category }.joinToString("、") { it.title }
            "[$category] $titles"
        }
    }
}
