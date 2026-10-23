package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "good_items")
data class GoodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",    // 数码, 生活, 学习, 自定义
    val reason: String = "",       // 推荐理由
    val description: String = "",  // 为什么早买早享受
    val referencePrice: String = "", // 参考价格
    val purchased: Boolean = false,
    val sortOrder: Int = 0,
    val schemeId: Long = 0
)
