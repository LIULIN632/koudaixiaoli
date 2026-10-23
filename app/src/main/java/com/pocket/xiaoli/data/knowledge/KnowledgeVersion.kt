package com.pocket.xiaoli.data.knowledge

/**
 * 知识库索引的版本决策 —— 纯函数，便于单测。
 *
 * 抽成顶层函数而不是塞进 `KnowledgeStore`：Store 要读写 `filesDir` 与 assets，
 * 都得有 `Context`，在 `app/src/test` 的单测里跑不动。项目已有先例：
 * `SchoolIndexUpdater` 专门把版本决策抽成顶层 `evaluateIndex(remote, local)`。
 *
 * ⚠️ **刻意不复用 `SchoolIndexUpdater`**：它属于 B 轨道导入引擎，
 * `CLAUDE.md` 明确「要动它之前必须先确认产品意图已变」。这里接受约 40 行结构重复，
 * 换零回归风险（决策记录 D6）。
 *
 * ⚠️ 协议版本常量也是**独立**的，不与学校索引共用 —— 两者升级节奏无关。
 */
internal const val KNOWLEDGE_CLIENT_PROTOCOL_VERSION = 1

internal sealed interface KnowledgeDecision {
    /** 通过校验，可写入缓存。 */
    data object Apply : KnowledgeDecision

    /** 已是最新，无需写入。 */
    data object UpToDate : KnowledgeDecision

    /** 拒绝写入（协议过高或远程更旧），附原因。 */
    data class Rejected(val message: String) : KnowledgeDecision
}

/**
 * 在「本地缓存」与「App 内置 assets」之间择优 —— 取 `version_id` 更高者。
 *
 * 这一步存在的理由是防**降级**：`SchoolIndexUpdater.writeIfNewer` 只看 filesDir 缓存，
 * 若照抄，第一次远程更新时缓存为空就直接写入；万一 App 内置的 assets 已经是 v5
 * 而镜像上还停在 v3（Gitee 没同步），用户一联网就把 v5 **覆盖成 v3**。
 * 所以比较基线必须是两者的较大值。
 */
internal fun chooseKnowledgeIndex(
    cached: KnowledgeIndexJson?,
    asset: KnowledgeIndexJson?,
): KnowledgeIndexJson? = when {
    cached == null -> asset
    asset == null -> cached
    asset.versionId > cached.versionId -> asset
    else -> cached
}

/** 远程索引是否该写入本地（协议版本 / 时间戳版本 / 防回退）。 */
internal fun evaluateKnowledgeIndex(
    remote: KnowledgeIndexJson,
    baseline: KnowledgeIndexJson?,
): KnowledgeDecision {
    if (remote.protocolVersion > KNOWLEDGE_CLIENT_PROTOCOL_VERSION) {
        return KnowledgeDecision.Rejected("知识库协议版本过高，请更新 App 后再试")
    }

    val baseVersion = baseline?.versionId
    if (baseVersion.isNullOrBlank()) return KnowledgeDecision.Apply
    if (remote.versionId == baseVersion) return KnowledgeDecision.UpToDate
    if (remote.versionId < baseVersion) {
        return KnowledgeDecision.Rejected("远程知识库版本过旧，已忽略")
    }
    return KnowledgeDecision.Apply
}
