package com.pocket.xiaoli.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 调休上班日映射 —— 移植自「聚在工大」(com.pocket.xiaoli) 的 SpecialWorkDayEntity。
 *
 * 语义：[originDate] 是「需要上班/上课」的调休日（通常是周末），
 * [targetDate] 指明这一天要按哪一天的课表上课（用户手动指定，因为
 * 国务院放假通知只公布「哪天上调休」，不公布「补哪天的课」）。
 */
@Entity(tableName = "special_work_day")
data class SpecialWorkDayEntity(
    @PrimaryKey
    @ColumnInfo(name = "origin_date") val originDate: String,
    @ColumnInfo(name = "target_date") val targetDate: String,
)