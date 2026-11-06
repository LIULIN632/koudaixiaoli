package com.pocket.xiaoli.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 单行表：当前「数据归属哪所学校」。
 *
 * ## 为什么它必须是 Room 实体，而不是裸 SQL 表
 *
 * 四张按校隔离的表，其查询条件都是 `schoolId = (SELECT schoolId FROM app_state WHERE id = 1)`。
 * Room 会把这些查询的**依赖表**记进 `InvalidationTracker`：只要 `app_state` 被 Room 写入，
 * 所有带这个子查询的 Flow 就会重新发射。切成裸 SQL 表（没有实体）时 Room 不建跟踪触发器，
 * 换校后课表 Flow 不会刷新 —— 用户会看到「换校了但课表还是旧的」，那是最难查的一类 bug。
 */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = 1,
    val schoolId: String = "",
)
