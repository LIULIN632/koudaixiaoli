package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "schemes")
data class SchemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,

    /** 所属学期（[TermEntity.id]）；`0` = 迁移前遗留数据，归入默认学期。 */
    val termId: Long = 0,
)
