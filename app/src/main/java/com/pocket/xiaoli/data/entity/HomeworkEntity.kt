package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    val content: String = "",
    val dueDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val schemeId: Long = 0
)
