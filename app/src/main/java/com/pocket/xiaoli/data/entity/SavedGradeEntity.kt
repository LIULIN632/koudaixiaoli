package com.pocket.xiaoli.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 已保存的课程成绩（从教务成绩查询保存，离线可查看）。
 * 唯一索引（课程+学期+总评）保证重复拉取/保存自动去重。
 */
@Entity(
    tableName = "saved_grades",
    indices = [Index(value = ["courseName", "termLabel", "totalScore"], unique = true)]
)
data class SavedGradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseName: String,
    val totalScore: String = "",
    val credits: String = "",
    val gpa: String = "",
    val regular: String = "",
    val final: String = "",
    val midterm: String = "",
    val examType: String = "",
    val termLabel: String = "",
    val schoolName: String = "",
    val savedAt: Long = System.currentTimeMillis(),

    /**
     * 这份数据属于哪所学校（`SchoolScope.current(context)` 的 id）。
     *
     * 空串 = 老数据或插入时没打标，由 `app_state` + 触发器补上（见 `MIGRATION_21_22`）；
     * 读取必须带 `schoolId = (SELECT schoolId FROM app_state WHERE id = 1)`，
     * 否则换学校后会把别校的数据显示出来。
     */
    val schoolId: String = "",
)
