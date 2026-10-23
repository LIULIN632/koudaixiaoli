package com.pocket.xiaoli.data.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.data.dao.AssistantChatDao
import com.pocket.xiaoli.data.entity.AssistantConversationEntity
import com.pocket.xiaoli.data.entity.AssistantMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 会话列表项（界面上只需要标题、时间与一句预览）。 */
data class AssistantConversation(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    /**
     * 该会话第一条用户消息（截断后），列表上做预览用。
     *
     * 空串表示"还没有用户消息"（理论上不会出现：会话是在第一句话发出去时才创建的，
     * 但脏数据不该让一行列表变成空白）。
     */
    val preview: String = "",
)

/**
 * 助手会话持久化。
 *
 * 职责边界：本类只管「通信协议消息 ⇄ 数据库行」的转换，
 * 不含任何 Agent 逻辑（循环、工具执行在 [AssistantAgentLoop]）。
 *
 * tool_calls 的编解码走 [JsonParser] 手工处理，不用 Gson 反射反序列化 ——
 * 与 [AssistantLlmClient] 同样的理由：R8 改字段名不会影响手动取值。
 */
class AssistantChatRepository(
    private val dao: AssistantChatDao,
) {

    // ── 会话 ──

    /** 新建会话，返回 id。 */
    suspend fun createConversation(): Long =
        dao.insertConversation(AssistantConversationEntity())

    /** 取最近一次会话（打开助手页时优先续接），没有则返回 null。 */
    suspend fun latestConversationId(): Long? = dao.latestConversation()?.id

    suspend fun conversations(): List<AssistantConversation> {
        val previews = dao.firstUserPreviews().associate { it.conversationId to it.preview }
        return dao.allConversations().map { it.toConversation(previews[it.id]) }
    }

    /**
     * 全部会话（新的在前），响应式。
     *
     * 返回映射后的 [AssistantConversation] 而不是实体：会话列表页只需要标题、时间与预览，
     * 让它拿到实体等于把数据库列名泄漏到界面层（将来加字段/改列名会连带改 UI）。
     * 与 [conversations] 用同一个映射函数，两条路径的标题兜底规则不会漂移。
     *
     * 用 `combine` 把两份 Flow 合起来：预览来自另一张表（第一条用户消息），
     * 会话行本身不带它。两份都变了才重算 —— Room 会只在对应表变化时各推一次，
     * 所以一次"发消息"（写 messages + touch conversations）最多触发两次重算，可接受。
     */
    fun observeConversations(): Flow<List<AssistantConversation>> =
        combine(
            dao.observeConversations(),
            dao.observeFirstUserPreviews(),
        ) { rows, previewRows ->
            val previews = previewRows.associate { it.conversationId to it.preview }
            rows.map { it.toConversation(previews[it.id]) }
        }

    suspend fun deleteConversation(id: Long) = dao.deleteConversation(id)

    suspend fun clearAll() = dao.clearAll()

    /**
     * 首轮之后用**本地截取**生成标题，不额外花一次模型调用。
     *
     * 为什么不用模型生成标题（岱宗盒子的 `AgentTitleGen` 那套）：
     * 那要多一次请求、多一份延迟与费用，而会话标题的信息量极低。
     * 截首句 14 字对用户已经够用。这个取舍是刻意做的。
     */
    suspend fun ensureTitle(conversationId: Long, firstUserText: String) {
        val existing = dao.conversationById(conversationId) ?: return
        if (existing.title.isNotBlank()) return
        val title = firstUserText.trim()
            .replace(Regex("\\s+"), " ")
            .take(14)
            .ifBlank { "新对话" }
        dao.updateConversation(conversationId, title, System.currentTimeMillis())
    }

    suspend fun touch(conversationId: Long) =
        dao.touchConversation(conversationId, System.currentTimeMillis())

    // ── 消息 ──

    /** 读取会话的完整上下文，用于恢复多轮工具调用（含 tool 消息）。 */
    suspend fun loadMessages(conversationId: Long): List<ChatMessage> =
        dao.messagesOf(conversationId).map { it.toChatMessage() }

    fun observeMessages(conversationId: Long) = dao.observeMessages(conversationId)

    /** 批量追加本轮消息。 */
    suspend fun appendMessages(conversationId: Long, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        dao.insertMessages(messages.map { it.toEntity(conversationId) })
        dao.touchConversation(conversationId, System.currentTimeMillis())
    }

    // ── 映射 ──

    /**
     * 会话实体 → 界面用的轻量模型。
     *
     * [preview] 由调用方从消息表带进来（会话行本身不存预览）；
     * 单行截断 + 压掉换行：列表里一行放不下多行文本，换行会让行高跳动。
     */
    private fun AssistantConversationEntity.toConversation(preview: String? = null) = AssistantConversation(
        id = id,
        title = title.ifBlank { "新对话" },
        updatedAt = updatedAt,
        preview = preview.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(48),
    )

    private fun AssistantMessageEntity.toChatMessage(): ChatMessage = ChatMessage(
        // 未知角色降级为 ASSISTANT：宁可显示异常也不要让整段历史因一行脏数据读不出来
        role = when (role.lowercase()) {
            "user" -> ChatRole.USER
            "assistant" -> ChatRole.ASSISTANT
            "tool" -> ChatRole.TOOL
            else -> ChatRole.ASSISTANT
        },
        content = content,
        toolCalls = decodeToolCalls(toolCallsJson),
        toolCallId = toolCallId,
        toolName = toolName,
    )

    private fun ChatMessage.toEntity(conversationId: Long) = AssistantMessageEntity(
        conversationId = conversationId,
        role = role.name.lowercase(),
        content = content,
        toolCallsJson = encodeToolCalls(toolCalls),
        toolCallId = toolCallId,
        // tool 行存工具名；assistant 行被界面借去存「失败轮」标记（见 AssistantChatViewModel.isErrorText），
        // 所以这里必须原样落盘，不能只在 role == TOOL 时才写
        toolName = toolName,
    )

    private fun encodeToolCalls(calls: List<ToolCall>): String? {
        if (calls.isEmpty()) return null
        val arr = JsonArray()
        calls.forEach { call ->
            arr.add(JsonObject().apply {
                addProperty("id", call.id)
                addProperty("name", call.name)
                addProperty("arguments", call.argumentsJson)
            })
        }
        return arr.toString()
    }

    private fun decodeToolCalls(json: String?): List<ToolCall> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: return@mapNotNull null
                ToolCall(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    name = name,
                    argumentsJson = obj.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString ?: "{}",
                )
            }
        }.getOrDefault(emptyList()) // 脏数据不阻塞历史恢复
    }
}
