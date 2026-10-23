package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val completed: Boolean = false,
    val sortOrder: Int = 0,
    val schemeId: Long = 0
)
