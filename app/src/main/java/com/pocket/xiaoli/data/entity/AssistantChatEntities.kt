package com.pocket.xiaoli.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一个助手会话（一次连续的对话）。
 *
 * 多会话隔离：每条消息与工具调用都挂 [conversationId]，
 * 删会话连带删消息（在 DAO 里用事务显式做，不用外键级联 —— 项目未启用外键）。
 */
@Entity(
    tableName = "assistant_conversations",
    indices = [Index(value = ["updated_at"])],
)
data class AssistantConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    /** 会话标题。首轮回答后由本地截取生成，不额外花一次模型调用。 */
    val title: String = "",

    /** 创建时间（毫秒），沿用项目惯例 [System.currentTimeMillis]。 */
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),

    /** 最后一条消息时间，用于会话列表倒序。 */
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 一条消息。[role] 取值与协议一致：`user` / `assistant` / `tool`。
 *
 * 为什么要存 `tool` 消息：多轮对话恢复时必须能还原「模型请求调用工具 → 工具回了什么」，
 * 否则模型看不到自己上一轮查到了什么，会重复调用工具甚至答非所问。
 */
@Entity(
    tableName = "assistant_messages",
    indices = [Index(value = ["conversation_id", "created_at"])],
)
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    @ColumnInfo(name = "conversation_id") val conversationId: Long,

    /** `user` / `assistant` / `tool`，与 [com.pocket.xiaoli.data.agent.ChatRole] 小写名一致。 */
    val role: String,

    val content: String = "",

    /**
     * assistant 消息携带的工具调用，存 JSON 数组字符串。
     *
     * 项目无 TypeConverter 且既有实体一律用字符串存结构化数据（如 GoodItemEntity 的多字段），
     * 这里沿用同一惯例，序列化/反序列化在 DAO 与仓储层做。
     */
    @ColumnInfo(name = "tool_calls") val toolCallsJson: String? = null,

    /** tool 消息专用：回应的哪个调用，对应 [com.pocket.xiaoli.data.agent.ToolCall.id]。 */
    @ColumnInfo(name = "tool_call_id") val toolCallId: String? = null,

    /** tool 消息专用：工具名，便于排错与界面展示。 */
    @ColumnInfo(name = "tool_name") val toolName: String? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
