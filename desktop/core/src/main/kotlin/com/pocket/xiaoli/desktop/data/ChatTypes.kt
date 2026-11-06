package com.pocket.xiaoli.desktop.data

/**
 * 会话相关的领域类型。
 *
 * 放在 :core 而不是 UI 层，是因为它们会被**序列化进文件**（`sessions_v1.json`）——
 * 落盘格式属于数据层契约，UI 只负责渲染。改动字段时要考虑旧文件的兼容
 * （见 memory/03-pitfalls.md「给 SharedPreferences 里的 JSON 类加字段」）。
 */

/** 对话里的一条工具调用记录（界面上显示为小胶囊）。 */
data class ToolTrace(
    val name: String = "",
    val label: String = "",
    val summary: String = "",
    val done: Boolean = false,
)

/**
 * 一条消息。`role` 取 `user` / `assistant`。
 *
 * ⚠️ 名字不要和 `Models.kt` 的领域模型混用：领域模型进备份，会话记录单独一份文件，
 * 命名上必须能一眼分开。
 */
data class ChatMessage(
    val id: String = "",
    val role: String = "user",
    val text: String = "",
    val reasoning: String = "",
    val tools: List<ToolTrace> = emptyList(),
    val at: Long = 0L,
    val failed: Boolean = false,
)

data class ChatSession(
    val id: String = "",
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = 0L,
)
