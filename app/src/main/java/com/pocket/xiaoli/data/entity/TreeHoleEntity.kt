package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "tree_holes")
data class TreeHoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val author: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val schemeId: Long = 0
)
