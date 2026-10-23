package com.pocket.xiaoli.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.pocket.xiaoli.data.entity.AssistantConversationEntity
import com.pocket.xiaoli.data.entity.AssistantMessageEntity
import kotlinx.coroutines.flow.Flow

/** 会话列表的预览行：会话 id + 该会话第一条用户消息。 */
data class ConversationPreview(
    val conversationId: Long,
    val preview: String,
)

/**
 * 助手会话与消息的读写。
 *
 * 不启用外键级联：项目的 Room 配置里没开 foreignKeys，
 * 删除的级联在 [deleteConversation] 里用事务显式完成。
 */
@Dao
interface AssistantChatDao {

    // ── 会话 ──

    @Query("SELECT * FROM assistant_conversations ORDER BY updated_at DESC")
    fun observeConversations(): Flow<List<AssistantConversationEntity>>

    /** 一次性取全部会话（首帧/非响应式场景用，避免为取一次列表去收集 Flow）。 */
    @Query("SELECT * FROM assistant_conversations ORDER BY updated_at DESC")
    suspend fun allConversations(): List<AssistantConversationEntity>

    @Query("SELECT * FROM assistant_conversations ORDER BY updated_at DESC LIMIT 1")
    suspend fun latestConversation(): AssistantConversationEntity?

    @Query("SELECT * FROM assistant_conversations WHERE id = :id")
    suspend fun conversationById(id: Long): AssistantConversationEntity?

    @Insert
    suspend fun insertConversation(item: AssistantConversationEntity): Long

    @Query("UPDATE assistant_conversations SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateConversation(id: Long, title: String, updatedAt: Long)

    @Query("UPDATE assistant_conversations SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchConversation(id: Long, updatedAt: Long)

    /**
     * 每个会话的**第一条用户消息**摘要（会话列表的预览用）。
     *
     * 为什么用 SQL 而不是"取全部消息再在内存里挑"：会话多了以后，
     * 为了列表上的一行小字把所有会话的全部消息都读进内存是浪费。
     * 这里靠 `role='user'` 过滤 + `MIN(id)` 归组，每个会话只取一行。
     *
     * 为什么取**第一条**而不是最后一条：列表上要回答的是"这段对话是聊什么的"
     * （标题也是从第一句截的），最后一条往往是一句"谢谢"或"再查一下"。
     */
    @Query(
        """
        SELECT m.conversation_id AS conversationId, m.content AS preview
        FROM assistant_messages m
        WHERE m.role = 'user'
          AND m.id = (
              SELECT MIN(m2.id) FROM assistant_messages m2
              WHERE m2.conversation_id = m.conversation_id AND m2.role = 'user'
          )
        """
    )
    suspend fun firstUserPreviews(): List<ConversationPreview>

    /**
     * 与 [firstUserPreviews] 同一份数据，但**响应式**。
     *
     * 为什么要单独有一个 Flow 版本：会话列表要跟着"发了一条新消息"实时更新预览。
     * 若只在进入页面时读一次，用户发完消息回到列表会看到旧预览。
     * 两条查询共用同一段 SQL —— 改一处必须改另一处（Room 在编译期校验列名，
     * 但不会校验两段 SQL 语义一致，所以下面刻意把条件写成完全相同的形状）。
     */
    @Query(
        """
        SELECT m.conversation_id AS conversationId, m.content AS preview
        FROM assistant_messages m
        WHERE m.role = 'user'
          AND m.id = (
              SELECT MIN(m2.id) FROM assistant_messages m2
              WHERE m2.conversation_id = m.conversation_id AND m2.role = 'user'
          )
        """
    )
    fun observeFirstUserPreviews(): Flow<List<ConversationPreview>>

    // ── 消息 ──

    @Query("SELECT * FROM assistant_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    suspend fun messagesOf(conversationId: Long): List<AssistantMessageEntity>

    @Query("SELECT * FROM assistant_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC, id ASC")
    fun observeMessages(conversationId: Long): Flow<List<AssistantMessageEntity>>

    @Insert
    suspend fun insertMessage(item: AssistantMessageEntity): Long

    @Insert
    suspend fun insertMessages(items: List<AssistantMessageEntity>): List<Long>

    // ── 删除 ──

    @Query("DELETE FROM assistant_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesOf(conversationId: Long)

    @Query("DELETE FROM assistant_conversations WHERE id = :conversationId")
    suspend fun deleteConversationRow(conversationId: Long)

    /** 删会话连同其全部消息。分两条语句放在同一事务里，避免中途失败留下孤儿消息。 */
    @Transaction
    suspend fun deleteConversation(conversationId: Long) {
        deleteMessagesOf(conversationId)
        deleteConversationRow(conversationId)
    }

    @Query("DELETE FROM assistant_conversations")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM assistant_messages")
    suspend fun deleteAllMessages()

    @Transaction
    suspend fun clearAll() {
        deleteAllMessages()
        deleteAllConversations()
    }
}
