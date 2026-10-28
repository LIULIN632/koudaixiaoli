package com.pocket.xiaoli.data.knowledge

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 时效过滤：把「过了期的知识」挡在检索之外，或者标上过期提醒。
 *
 * ## 为什么是纯函数
 *
 * 过滤依赖「今天是哪天」。把它做成可注入的纯函数有两个直接好处：
 * 1. 能单测边界（今天正好等于 `expire_date` 的那一天）；
 * 2. **调用方被迫在每次检索时传入 today** —— 这正是要的行为，见下。
 *
 * ## 为什么必须在「每次检索」时跑，不能在加载时跑一次
 *
 * Android 进程可以存活数天。用户 10 月 2 日打开 App、10 月 3 日还在用同一个进程，
 * 那篇 10 月 2 日到期的通知**仍会被当成现行安排答出来**。
 * 所以 `KnowledgeStore` 只存未过滤的原始文档，本对象在每次 `search_knowledge`
 * 调用时执行。详见设计文档 §6.1。
 */
object ExpiryPolicy {

    /** 无期限的哨兵日期。frontmatter 里 `expire_date: 9999-12-31` 表示永不过期。 */
    val NO_EXPIRY: LocalDate = LocalDate.of(9999, 12, 31)

    sealed interface Decision {
        /** 在有效期内（或本来就没有失效日期）。 */
        data object Include : Decision

        /** 已过期，但内容类型允许保留（[ExpiryKind.SOFT]）。 */
        data class IncludeExpired(val expiredOn: LocalDate, val daysAgo: Long) : Decision

        /** 已过期且类型不允许保留（[ExpiryKind.HARD]）。 */
        data object Exclude : Decision
    }

    /**
     * 单篇判定。
     *
     * 注意 `effectiveFrom` **不参与过滤**：`AssistantContract` §4 的原文是
     * 「**优先** `effectiveFrom <= now <= effectiveTo` 的资料」——是排序偏好，不是硬条件。
     * 而通知类往往提前发布（放假安排提前一周），排除掉反而是坏答案。
     */
    fun decide(document: KnowledgeDocument, today: LocalDate): Decision {
        // 判定规则在共享核心 `com.pocket.xiaoli.core.knowledge.expiryDecision` 里，
        // 与 Windows 桌面端同一份。这边只做「桌面/安卓模型 ↔ 共享层」的映射。
        val decision = com.pocket.xiaoli.core.knowledge.expiryDecision(
            effectiveToIso = document.effectiveTo?.toString(),
            hardExpiry = document.expiryKind == ExpiryKind.HARD,
            todayEpochDay = today.toEpochDay(),
        )
        return when (decision) {
            com.pocket.xiaoli.core.knowledge.ExpiryDecision.Include -> Decision.Include
            com.pocket.xiaoli.core.knowledge.ExpiryDecision.Exclude -> Decision.Exclude
            is com.pocket.xiaoli.core.knowledge.ExpiryDecision.IncludeExpired ->
                Decision.IncludeExpired(
                    expiredOn = LocalDate.parse(decision.expiredOnIso),
                    daysAgo = decision.daysAgo,
                )
        }
    }

    /**
     * 批量过滤，**保持输入顺序**，不修改入参。
     *
     * 软保留的文档会在正文最前面插入一句过期提醒 —— 模型看到它就不会把过期的放假安排
     * 当成今年的安排念出来。
     */
    fun apply(documents: List<KnowledgeDocument>, today: LocalDate): List<KnowledgeDocument> =
        documents.mapNotNull { document ->
            when (val decision = decide(document, today)) {
                is Decision.Exclude -> null
                is Decision.Include -> document
                is Decision.IncludeExpired -> document.copy(
                    body = notice(decision.daysAgo, decision.expiredOn) + document.body,
                )
            }
        }

    /** 过期提醒文案。放在正文最前面，模型与用户都会先看到它。 */
    fun notice(daysAgo: Long, expiredOn: LocalDate): String =
        com.pocket.xiaoli.core.knowledge.expiryNotice(expiredOn.toString(), daysAgo)
}
