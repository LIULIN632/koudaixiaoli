package com.pocket.xiaoli.data.knowledge

import com.pocket.xiaoli.core.knowledge.KeywordRetrieverCore
import com.pocket.xiaoli.core.knowledge.KnowledgeDoc

/**
 * 知识库检索 —— **算法本体在共享核心** `com.pocket.xiaoli.core.knowledge.KeywordRetrieverCore`
 * （Android 与 Windows 桌面端同一份打分规则）。
 *
 * 本文件只做三件事：
 * 1. 把本端的 [KnowledgeDocument] 压成共享层的 `KnowledgeDoc`（平台模型各自持有，见共享层注释）；
 * 2. 调共享层检索；
 * 3. 用 `chunkId` 把结果映射回本端对象，保持既有调用方 API 完全不变。
 */
object KeywordRetriever {

    /** 时间加权系数，委托共享层常量（改一处两端一致）。 */
    const val DEFAULT_RECENCY_WEIGHT: Double = KeywordRetrieverCore.DEFAULT_RECENCY_WEIGHT

    /** 时间加权半衰期（天），委托共享层常量。 */
    const val RECENCY_HALF_LIFE_DAYS: Double = KeywordRetrieverCore.RECENCY_HALF_LIFE_DAYS

    internal fun recencyFactor(updatedAt: Long?, nowMillis: Long): Double =
        KeywordRetrieverCore.recencyFactor(updatedAt, nowMillis)

    fun buildChunks(documents: List<KnowledgeDocument>): List<KnowledgeChunk> =
        documents.flatMap { document ->
            KeywordRetrieverCore.chunkTexts(document.body).mapIndexed { index, text ->
                KnowledgeChunk(
                    id = document.id + "#" + index,
                    documentId = document.id,
                    order = index,
                    text = text,
                )
            }
        }

    fun search(
        query: String,
        documents: List<KnowledgeDocument>,
        topK: Int = 5,
        recencyWeight: Double = DEFAULT_RECENCY_WEIGHT,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<KnowledgeHit> {
        if (documents.isEmpty()) return emptyList()
        val sharedDocs = documents.map { KnowledgeDoc(it.id, it.title, it.body, it.tags, it.updatedAt) }
        val snippets = KeywordRetrieverCore.search(query, sharedDocs, nowMillis, topK, recencyWeight)
        if (snippets.isEmpty()) return emptyList()

        val documentsById = documents.associateBy { it.id }
        val chunksById = buildChunks(documents).associateBy { it.id }
        return snippets.mapNotNull { snippet ->
            val document = documentsById[snippet.documentId] ?: return@mapNotNull null
            val chunk = chunksById[snippet.chunkId] ?: return@mapNotNull null
            KnowledgeHit(
                chunk = chunk,
                document = document,
                score = snippet.score,
                rankScore = snippet.rankScore,
            )
        }
    }

    internal fun cosineSimilarity(a: String, b: String): Double =
        KeywordRetrieverCore.cosineSimilarity(a, b)

    internal fun ngramVector(text: String): Map<String, Double> =
        KeywordRetrieverCore.ngramVector(text)
}
