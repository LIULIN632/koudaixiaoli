package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate

/**
 * JSON DTO → 领域模型。纯函数，不依赖 `Context`，可直接单测。
 *
 * 容错原则：**能确定是坏数据的就丢掉，不能确定的一律往安全侧倒**。
 * 一份坏的 JSON 不应该让鲤鱼的整个知识库不可用。
 */
object KnowledgeJsonMapper {

    /**
     * 转换整个索引。
     *
     * 丢掉 [KnowledgeDocJson.id]、`title`、`body` 任一为空白的条目 —— 送进检索
     * 只会产生垃圾命中（比如一条只有标签、没有正文的笔记）。
     */
    fun toDocuments(index: KnowledgeIndexJson): List<KnowledgeDocument> =
        index.docs.mapNotNull { doc ->
            if (doc.id.isBlank() || doc.title.isBlank() || doc.body.isBlank()) {
                null
            } else {
                KnowledgeDocument(
                    id = doc.id,
                    title = doc.title,
                    body = doc.body,
                    sourceLabel = doc.sourceLabel,
                    updatedAt = doc.updatedAt.takeIf { it > 0L },
                    effectiveFrom = parseDate(doc.effectiveFrom),
                    effectiveTo = parseDate(doc.effectiveTo),
                    category = doc.category,
                    expiryKind = expiryKindOf(doc.expiryPolicy),
                    owner = doc.owner,
                    tags = doc.tags.filter { it.isNotBlank() },
                )
            }
        }

    /** `YYYY-MM-DD` → [LocalDate]；任何不合法输入返回 `null`（不抛异常）。 */
    fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    /**
     * 未知取值一律当 [ExpiryKind.HARD]。
     *
     * 安全侧：`HARD` 是"宁可不答，也不把过期信息当现行安排"。反过来把未知值当 `SOFT`
     * 会让一份写得有问题的笔记继续参与回答。
     */
    fun expiryKindOf(raw: String): ExpiryKind =
        if (raw.trim().equals("soft", ignoreCase = true)) ExpiryKind.SOFT else ExpiryKind.HARD
}
