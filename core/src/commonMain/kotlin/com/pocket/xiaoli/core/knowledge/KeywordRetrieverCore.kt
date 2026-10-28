package com.pocket.xiaoli.core.knowledge

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 知识库检索的**唯一实现**：分块 + 关键词/字符 n-gram 打分 + 时间加权。
 *
 * ## 为什么模型在这里另起一套，而不直接用 Android 的 `KnowledgeDocument`
 *
 * Android 的模型带 `java.time.LocalDate`（`effectiveFrom/effectiveTo`），桌面端用的是自己的类；
 * 把 `LocalDate` 搬进 `commonMain` 会逼着桌面端也依赖 java.time（Android 上还要 API 26+ 脱糖），
 * 或者引入 kotlinx-datetime —— 那是新依赖，阶段 2 不允许。
 *
 * 所以边界这样切：**算法共享，模型各自持有**。两端的适配层把本端模型映射成 [KnowledgeDoc]
 * （日期压成 `yyyy-MM-dd` 字符串，只在展示层还原），检索结果再按 `chunkId` 映射回本端对象。
 *
 * ## 时间加权
 *
 * `rankScore = score * (1 + weight * recency)`，`recency` 是 0..1 的新鲜度。
 * 没有 `updatedAt` 的文档 `recency == 0`，于是 `rankScore == score` —— 对既有数据完全无感。
 */
object KeywordRetrieverCore {

    /** 时间加权系数。先验值，无真实数据支撑：一年前更新的文档最多掉 7.5% 分。 */
    const val DEFAULT_RECENCY_WEIGHT: Double = 0.15

    /** 时间加权的半衰期（天）。同样是先验值。 */
    const val RECENCY_HALF_LIFE_DAYS: Double = 365.0

    private const val MILLIS_PER_DAY: Double = 86_400_000.0

    private val SPLIT_REGEX = Regex("[\\s,，。.？?！!、;；:：/()（）\\[\\]【】]+")
    private val PARAGRAPH_REGEX = Regex("\\n\\s*\\n")

    /** 把一个文档正文切成块（按空行分段）。两端共用同一切法，避免分块边界不一致。 */
    fun chunkTexts(body: String): List<String> {
        val parts = PARAGRAPH_REGEX.split(body).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(body.trim()) }
    }

    /**
     * 时间新鲜度，`0.0..1.0`，越大越新。
     *
     * 无 `updatedAt`（或 ≤0）返回 0；时间戳在未来时年龄 clamp 到 0。
     */
    fun recencyFactor(updatedAt: Long?, nowMillis: Long): Double {
        if (updatedAt == null || updatedAt <= 0L) return 0.0
        val ageDays = (nowMillis - updatedAt).coerceAtLeast(0L) / MILLIS_PER_DAY
        return 1.0 / (1.0 + ageDays / RECENCY_HALF_LIFE_DAYS)
    }

    /**
     * 关键词 + 字符 n-gram 相似度检索，带时间加权。
     *
     * [nowMillis] 由调用方注入（共享层不读系统时钟，便于单测与跨端复现）。
     */
    fun search(
        query: String,
        documents: List<KnowledgeDoc>,
        nowMillis: Long,
        topK: Int = 5,
        recencyWeight: Double = DEFAULT_RECENCY_WEIGHT,
    ): List<KnowledgeSnippet> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val tokens = SPLIT_REGEX.split(normalized).filter { it.isNotBlank() }
        val grams = if (tokens.size <= 1) bigrams(normalized) else emptyList()
        val documentsById = documents.associateBy { it.id }
        val weight = recencyWeight.coerceAtLeast(0.0)

        return documents.flatMap { document ->
            chunkTexts(document.body).mapIndexed { index, text ->
                document to KnowledgeChunkView(
                    chunkId = document.id + "#" + index,
                    order = index,
                    text = text,
                )
            }
        }.mapNotNull { (document, chunk) ->
            val haystack = (document.title + " " + document.tags.joinToString(" ") + " " + chunk.text).lowercase()
            var score = 0
            if (haystack.contains(normalized)) score += 120
            tokens.forEach { token ->
                if (haystack.contains(token)) score += 10 + token.length
                if (document.title.lowercase().contains(token)) score += 8
            }
            grams.forEach { gram -> if (haystack.contains(gram)) score += 3 }
            val similarity = cosineSimilarity(normalized, haystack)
            if (similarity > 0.12) score += (similarity * 100).roundToInt()
            if (score <= 0) return@mapNotNull null

            // 文档必须存在（防止调用方传了重复 id 造成假命中）
            if (documentsById[document.id] == null) return@mapNotNull null

            val recency = recencyFactor(document.updatedAt, nowMillis)
            KnowledgeSnippet(
                documentId = document.id,
                chunkId = chunk.chunkId,
                order = chunk.order,
                text = chunk.text,
                score = score,
                rankScore = score * (1.0 + weight * recency),
            )
        }
            // 稳定排序：同分时保持输入顺序，结果可复现
            .sortedByDescending { it.rankScore }
            .take(topK)
    }

    private fun bigrams(text: String): List<String> {
        val cleaned = text.filter { it.isLetterOrDigit() }
        if (cleaned.length < 2) return emptyList()
        return (0 until cleaned.length - 1).map { cleaned.substring(it, it + 2) }.distinct()
    }

    /** 字符 1/2-gram TF 向量余弦，作为本地「伪向量」补充关键词检索。 */
    fun cosineSimilarity(a: String, b: String): Double {
        val va = ngramVector(a)
        val vb = ngramVector(b)
        if (va.isEmpty() || vb.isEmpty()) return 0.0
        var dot = 0.0
        var na = 0.0
        va.forEach { (key, value) ->
            na += value * value
            dot += value * (vb[key] ?: 0.0)
        }
        var nb = 0.0
        vb.values.forEach { nb += it * it }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    /** 字符 1-gram + 2-gram 词频向量。 */
    fun ngramVector(text: String): Map<String, Double> {
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
}

/** 检索输入：两端把本端模型压成这个形状（日期已序列化，tags 已摊平）。 */
data class KnowledgeDoc(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val updatedAt: Long? = null,
)

/** 一块命中的最小信息；调用方用 [chunkId] 映射回本端 chunk 对象。 */
data class KnowledgeSnippet(
    val documentId: String,
    val chunkId: String,
    val order: Int,
    val text: String,
    val score: Int,
    val rankScore: Double,
)

/** 内部用的分块视图（不对外暴露，避免两端各自维护一份）。 */
data class KnowledgeChunkView(
    val chunkId: String,
    val order: Int,
    val text: String,
)
