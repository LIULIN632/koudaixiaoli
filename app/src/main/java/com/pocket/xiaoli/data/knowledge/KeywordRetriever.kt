package com.pocket.xiaoli.data.knowledge

/**
 * M5 retrieval skeleton: document chunking + keyword scoring.
 * No embedding / vector store yet (see docs/AssistantContract.md).
 */
import kotlin.math.roundToInt
import kotlin.math.sqrt

object KeywordRetriever {

    /**
     * 时间加权系数。`rankScore = score * (1 + weight * recency)`。
     *
     * ⚠️ **这是先验值，没有真实数据支撑**（设计文档 §11 欠账 3）。0.15 的含义是：
     * 一年前更新的文档最多掉 7.5% 分（`0.15 × 0.5`），两年前掉 10%。
     * 足以让「今年通知 vs 去年通知」翻盘，不足以让「弱相关但很新」压过「强相关」。
     * 上线后对着答案质量调；调成 `0.0` 即等价于本次改造前的行为。
     */
    const val DEFAULT_RECENCY_WEIGHT: Double = 0.15

    /** 时间加权的半衰期（天）。见 [DEFAULT_RECENCY_WEIGHT] 的说明，同样是先验值。 */
    const val RECENCY_HALF_LIFE_DAYS: Double = 365.0

    private const val MILLIS_PER_DAY: Double = 86_400_000.0

    private val SPLIT_REGEX = Regex("[\\s,，。.？?！!、;；:：/()（）\\[\\]【】]+")
    private val PARAGRAPH_REGEX = Regex("\\n\\s*\\n")

    /**
     * 时间新鲜度，`0.0..1.0`，越大越新。
     *
     * - **无 `updatedAt` → 返回 0**：这类文档完全不参与加权，于是 `rankScore == score`，
     *   排序与本次改造前逐位相同。内置 FAQ 与既有测试都走这条路。
     * - 时间戳在未来（设备时钟错乱）时年龄被 clamp 到 0，不会产生"暴击"式加权。
     */
    internal fun recencyFactor(updatedAt: Long?, nowMillis: Long): Double {
        if (updatedAt == null || updatedAt <= 0L) return 0.0
        val ageDays = (nowMillis - updatedAt).coerceAtLeast(0L) / MILLIS_PER_DAY
        return 1.0 / (1.0 + ageDays / RECENCY_HALF_LIFE_DAYS)
    }

    fun buildChunks(documents: List<KnowledgeDocument>): List<KnowledgeChunk> =
        documents.flatMap { document ->
            splitParagraphs(document.body).mapIndexed { index, text ->
                KnowledgeChunk(
                    id = document.id + "#" + index,
                    documentId = document.id,
                    order = index,
                    text = text,
                )
            }
        }

    /**
     * 关键词 + 字符 n-gram 相似度检索，带时间加权。
     *
     * [recencyWeight] 与 [nowMillis] 都可注入，便于单测；`nowMillis` 默认取当前时间。
     * 加权为什么必须有（而不是做成"同分才比日期"的 tie-breaker）：
     * 「2025 放假安排」和「2026 放假安排」两篇都命中时，去年那篇关键词往往更长、
     * 原始分**更高** —— 同分比日期根本轮不到它。
     */
    fun search(
        query: String,
        documents: List<KnowledgeDocument>,
        topK: Int = 5,
        recencyWeight: Double = DEFAULT_RECENCY_WEIGHT,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<KnowledgeHit> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val tokens = SPLIT_REGEX.split(normalized).filter { it.isNotBlank() }
        val grams = if (tokens.size <= 1) bigrams(normalized) else emptyList()
        val documentsById = documents.associateBy { it.id }
        val weight = recencyWeight.coerceAtLeast(0.0)

        return buildChunks(documents)
            .mapNotNull { chunk ->
                val document = documentsById[chunk.documentId] ?: return@mapNotNull null
                val haystack = (
                    document.title + " " +
                        document.tags.joinToString(" ") + " " +
                        chunk.text
                    ).lowercase()
                var score = 0
                if (haystack.contains(normalized)) score += 120
                tokens.forEach { token ->
                    if (haystack.contains(token)) score += 10 + token.length
                    if (document.title.lowercase().contains(token)) score += 8
                }
                grams.forEach { gram -> if (haystack.contains(gram)) score += 3 }
                val similarity = cosineSimilarity(normalized, haystack)
                if (similarity > 0.12) score += (similarity * 100).roundToInt()
                if (score > 0) {
                    val recency = recencyFactor(document.updatedAt, nowMillis)
                    KnowledgeHit(
                        chunk = chunk,
                        document = document,
                        score = score,
                        rankScore = score * (1.0 + weight * recency),
                    )
                } else {
                    null
                }
            }
            // 稳定排序：rankScore 相同时保持输入顺序（构建期已按 id 排序 → 结果可复现）
            .sortedByDescending { it.rankScore }
            .take(topK)
    }

    private fun bigrams(text: String): List<String> {
        val cleaned = text.filter { it.isLetterOrDigit() }
        if (cleaned.length < 2) return emptyList()
        return (0 until cleaned.length - 1)
            .map { cleaned.substring(it, it + 2) }
            .distinct()
    }

    /** 字符 1/2-gram TF 向量余弦，作为本地「伪向量」补充关键词检索。 */
    internal fun cosineSimilarity(a: String, b: String): Double {
        val va = ngramVector(a)
        val vb = ngramVector(b)
        if (va.isEmpty() || vb.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        va.forEach { (key, value) ->
            na += value * value
            dot += value * (vb[key] ?: 0.0)
        }
        vb.values.forEach { nb += it * it }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** 仅供检索使用：字符 1-gram + 2-gram 词频向量。 */
    internal fun ngramVector(text: String): Map<String, Double> {
        val clean = text.filter { it.isLetterOrDigit() }
        if (clean.isEmpty()) return emptyMap()
        val grams = mutableMapOf<String, Double>()
        for (n in listOf(1, 2)) {
            if (clean.length < n) continue
            for (i in 0..clean.length - n) {
                val gram = clean.substring(i, i + n)
                grams[gram] = (grams[gram] ?: 0.0) + 1.0
            }
        }
        return grams
    }

    private fun splitParagraphs(body: String): List<String> {
        val parts = PARAGRAPH_REGEX.split(body).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(body.trim()) }
    }
}
