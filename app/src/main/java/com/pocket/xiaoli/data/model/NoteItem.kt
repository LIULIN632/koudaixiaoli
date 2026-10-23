package com.pocket.xiaoli.data.model

/**
 * 本地学习笔记。
 *
 * 当前只在本机保存；后续「上传笔记大家都能看」时，
 * 直接把 isPublic 的笔记同步到服务器即可。
 */
data class NoteItem(
    val id: String,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    /** 是否允许后续上传到公开笔记广场 */
    val isPublic: Boolean = false,
)
