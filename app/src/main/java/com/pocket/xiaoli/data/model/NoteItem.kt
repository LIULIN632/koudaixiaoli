package com.pocket.xiaoli.data.model

/**
 * 本地学习笔记 —— **只存在这台手机上**。
 *
 * 2026-10-29 撤掉了 `isPublic` 那个开关：全仓库读它的地方是 0 个（只有模型定义与界面上的
 * 开关本身），没有上传通道、也没有"笔记广场"。留着它等于让界面上有一格永远不生效的开关，
 * 还会让人以为勾了之后笔记就发出去了。真接服务器时再按需求加回来（Gson 存的是 JSON，
 * 老数据里没有这个字段也不会读挂）。
 */
data class NoteItem(
    val id: String,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
