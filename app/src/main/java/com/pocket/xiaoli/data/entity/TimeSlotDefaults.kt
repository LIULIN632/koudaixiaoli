package com.pocket.xiaoli.data.entity

import com.pocket.xiaoli.core.DefaultTimeSlots
import com.pocket.xiaoli.core.TimeSlotSpec
import com.pocket.xiaoli.core.matchesLegacyTimeSlots as coreMatchesLegacyTimeSlots

/**
 * 默认节次表 —— **数据本体在共享核心** `com.pocket.xiaoli.core.DefaultTimeSlots`。
 *
 * 这里只做「核心纯数据 → Room 实体」的映射，不存任何时间字面量；
 * 改默认时间请改 `core/src/commonMain/kotlin/com/pocket/xiaoli/core/TimeSlotDefaults.kt`，
 * 桌面端会同时生效（这正是把它们抽出去的原因）。
 *
 * 三处消费它：
 * 1. [com.pocket.xiaoli.data.repository.CourseRepository.initTimeSlots]：时间段为空时填充；
 * 2. `PocketXiaoliDatabase` 的 `onCreate` 与 v21 迁移；
 * 3. [com.pocket.xiaoli.util.CalendarExportUtil]：导出系统日历时按节次推算时间。
 *
 * 排布规则（2026-10-25 按用户要求定）：上午第一节 08:00、下午第一节 14:00、
 * 晚上第一节 19:00，每节 45 分钟；段内课间 10 分钟，第 2、3 节之间留 20 分钟大课间。
 * 1-4 上午 / 5-8 下午 / 9-12 晚上。
 */
val defaultTimeSlots: List<TimeSlotEntity> = DefaultTimeSlots.map { it.toEntity() }

/**
 * v20 及之前沿用的默认节次。**只服务 v20→v21 的"用户没动过"判据**，
 * 新代码不要拿它当默认值用。
 */
internal val legacyTimeSlotsBeforeV21: List<TimeSlotEntity> =
    (0 until com.pocket.xiaoli.core.LegacyTimeSlotCount)
        .map { com.pocket.xiaoli.core.legacyTimeSlotAt(it).toEntity() }

/**
 * 表内容是否**仍然完全等于**旧默认值。
 *
 * 迁移只在"是"的时候才改写节次时间：用户在「节次管理」里调过任何一节，
 * 整表就原样保留 —— 覆盖用户数据去追一个新默认值，是比时间不对更糟的结果。
 */
internal fun matchesLegacyTimeSlots(rows: List<TimeSlotEntity>): Boolean =
    coreMatchesLegacyTimeSlots(rows.map { it.toSpec() })

private fun TimeSlotSpec.toEntity(): TimeSlotEntity =
    TimeSlotEntity(slot = slot, startTime = startTime, endTime = endTime)

private fun TimeSlotEntity.toSpec(): TimeSlotSpec =
    TimeSlotSpec(slot = slot, startTime = startTime, endTime = endTime)
