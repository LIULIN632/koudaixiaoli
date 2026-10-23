package com.pocket.xiaoli.data.knowledge

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * `knowledge.json` 的 DTO —— **只用于 Gson 反序列化**，不参与业务。
 *
 * ## 每个字段都必须有默认值（这不是风格问题）
 *
 * 本项目 `DormInfo` 崩过一次（v1.0.93）：它的声明里有一个「非空且无默认值」的字段，
 * Kotlin 因此**不生成无参构造**，Gson 于是退回 `UnsafeAllocator` **反射直接赋值** ——
 * 那条路径上**所有**字段的默认值一起失效，JSON 里缺的字段就是 `null`，
 * `dormCode.isNotBlank()` 直接抛 NPE。
 *
 * 反过来推：**全部字段都有默认值 ⟺ 存在无参构造 ⟺ Gson 走默认值路径**。
 * 而「从网络/文件读 JSON」正是这个坑的高危区（`SchoolIndexUpdater.loadLocalIndex`
 * 里那段"Gson 绕过 Kotlin 空安全"的防御就是被它咬出来的）。
 *
 * 所以：新增字段时**先给默认值**，`KnowledgeDocJsonTest` 里那条
 * 「存在无参构造」的断言就是这道防线的守门人。
 */
data class KnowledgeIndexJson(
    @SerializedName("protocol_version") val protocolVersion: Int = 0,
    @SerializedName("version_id") val versionId: String = "",
    @SerializedName("content_hash") val contentHash: String = "",
    @SerializedName("generated_at") val generatedAt: String = "",
    val docs: List<KnowledgeDocJson> = emptyList(),
)

/** 单篇笔记（与 `tools/kb/build_knowledge.py` 的输出逐字段对应）。 */
data class KnowledgeDocJson(
    val id: String = "",
    val title: String = "",
    val category: String = "",
    val campus: String = "",
    @SerializedName("expiry_policy") val expiryPolicy: String = "hard",
    val owner: String = "",
    @SerializedName("source_label") val sourceLabel: String = "",
    @SerializedName("effective_from") val effectiveFrom: String = "",
    @SerializedName("effective_to") val effectiveTo: String = "",
    @SerializedName("updated_at") val updatedAt: Long = 0L,
    val tags: List<String> = emptyList(),
    val body: String = "",
)

/**
 * 解析一份 `knowledge.json`，坏数据返回 `null`（不抛异常）。
 *
 * 顶层函数而非私有方法，是为了能在 `app/src/test` 里直接测（不需要 `Context`）。
 *
 * ⚠️ `docs` 那一行不是多余的：**Gson 绕过 Kotlin 空安全**，JSON 里
 * `"docs": null` 会真的塞进去一个 null，而声明是非空的 `List`。
 * 用可空中转接一下再判断，抄的是 `SchoolIndexUpdater.loadLocalIndex` 的做法。
 */
internal fun parseKnowledgeIndex(raw: String?): KnowledgeIndexJson? {
    if (raw.isNullOrBlank()) return null

    val index = runCatching { Gson().fromJson(raw, KnowledgeIndexJson::class.java) }.getOrNull()
        ?: return null
    val docs: List<KnowledgeDocJson>? = index.docs
    return if (docs == null) null else index
}
