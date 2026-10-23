package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate

/**
 * M5 knowledge model: documents are chunked, retrieval is keyword-based in this version.
 *
 * ## 时效字段的来历
 *
 * `updatedAt` 与位次里的 `effectiveFrom` 原本是 M5 骨架留下的占位字段（声明了但全项目无人读），
 * 本次接入校园知识库时启用。`effectiveAt: Long?` 改名为 [KnowledgeDocument.effectiveFrom]
 * 并把类型换成 [LocalDate]：它此前**没有任何读方**（全项目搜过），改名零回归；
 * 而日期语义统一用 `LocalDate` 比 epoch 毫秒少了"哪个时区"这个隐含假设。
 *
 * 字段改动依据：`docs/superpowers/specs/2026-10-03-obsidian-campus-knowledge-base-design.md` §4.3。
 */
data class KnowledgeDocument(
    val id: String,
    val title: String,
    val body: String,
    val sourceLabel: String,
    val sourceUrl: String? = null,
    val updatedAt: Long? = null,
    val effectiveFrom: LocalDate? = null,
    val effectiveTo: LocalDate? = null,
    val category: String = "",
    val expiryKind: ExpiryKind = ExpiryKind.HARD,
    val owner: String = "",
    val version: String? = null,
    val tags: List<String> = emptyList(),
)

/** 内容过了 `effectiveTo` 之后该怎么处理。 */
enum class ExpiryKind {
    /** 过期即失效，不再参与检索（制度、场馆时间、办事流程）。 */
    HARD,

    /** 过期仍可检索，但正文会被标上「已于 X 过期」（放假安排、临时闭馆这类事件型通知）。 */
    SOFT,
}

data class KnowledgeChunk(
    val id: String,
    val documentId: String,
    val order: Int,
    val text: String,
)

data class KnowledgeHit(
    val chunk: KnowledgeChunk,
    val document: KnowledgeDocument,
    /** 原始相关度分数。语义不变，供将来展示「匹配度」用。 */
    val score: Int,
    /**
     * 时间加权后的排序口径，**唯一**决定顺序。
     *
     * 与 [score] 分开的理由：文档没有 `updatedAt` 时 `rankScore == score`，
     * 于是本次改造对既有数据完全无感；而将来若要展示原始匹配度，读 [score] 也不会被污染。
     */
    val rankScore: Double,
)

/** A provider of knowledge documents. Local FAQ is the first implementation. */
interface KnowledgeSource {
    val id: String
    suspend fun listDocuments(): List<KnowledgeDocument>
}
