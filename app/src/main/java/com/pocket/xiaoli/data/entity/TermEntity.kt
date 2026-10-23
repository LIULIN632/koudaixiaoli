package com.pocket.xiaoli.data.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学期（照岱宗盒子的 `termCode` 设计，但落成 Room 实体而不是缓存 key）。
 *
 * 口袋小鲤原来把学期压成一对全局 SharedPreferences（`semester_start` / `semester_weeks`），
 * 换学期只能覆盖，历史课表回不去。这里把学期升成一级实体，[SchemeEntity.termId] 把课表方案挂到学期下：
 *
 * - 一个学期可以有多个方案（主修 / 辅修 / 自定义）
 * - 切换学期 = 切到该学期下的方案，旧学期原样保留
 *
 * `id = 0` 是迁移时生成的「默认学期」，旧数据全部归入它，见 `MIGRATION_19_20`。
 */
@Immutable
@Entity(tableName = "terms")
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** 学期显示名，如「2025-2026-1」。可空串，界面用 [displayName] 回退。 */
    val name: String = "",

    /** 开学日期，`yyyy-MM-dd`；空串 = 未配置（周次退化为从 1 开始数）。 */
    val startDate: String = "",

    /** 学期总周数，默认 20（与岱宗 `TermWeeks.DEFAULT_TOTAL_WEEKS` 一致）。 */
    val totalWeeks: Int = 20,

    /** 排序：列表按此升序，数值越小越靠前。 */
    val sortOrder: Int = 0,
) {
    /** 列表里显示的学期名：没填就按 id 兜底，不留空行。 */
    val displayName: String get() = name.ifBlank { if (id == 0L) "默认学期" else "学期 ${id + 1}" }
}
