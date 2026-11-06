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
    val schemeId: Long = 0,

    /**
     * 这份数据属于哪所学校（`SchoolScope.current(context)` 的 id）。
     *
     * 空串 = 老数据或插入时没打标，由 `app_state` + 触发器补上（见 `MIGRATION_21_22`）；
     * 读取必须带 `schoolId = (SELECT schoolId FROM app_state WHERE id = 1)`，
     * 否则换学校后会把别校的数据显示出来。
     */
    val schoolId: String = "",
)
