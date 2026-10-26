package com.pocket.xiaoli.data.entity

/**
 * 默认节次表 —— **唯一真源**。
 *
 * 三处消费它，别再各自抄一份字面量：
 * 1. [com.pocket.xiaoli.data.repository.CourseRepository.initTimeSlots]：时间段为空时填充；
 * 2. `PocketXiaoliDatabase` 的 `onCreate` 与 v21 迁移；
 * 3. [com.pocket.xiaoli.util.CalendarExportUtil]：导出系统日历时按节次推算时间。
 *
 * 排布规则（2026-10-25 按用户要求定）：**上午第一节 08:00、下午第一节 14:00、
 * 晚上第一节 19:00，每节 45 分钟**；段内课间 10 分钟，第 2、3 节之间留 20 分钟大课间。
 * 1-4 上午 / 5-8 下午 / 9-12 晚上。
 */
val defaultTimeSlots: List<TimeSlotEntity> = listOf(
    TimeSlotEntity(1, "08:00", "08:45"),
    TimeSlotEntity(2, "08:55", "09:40"),
    TimeSlotEntity(3, "10:00", "10:45"),
    TimeSlotEntity(4, "10:55", "11:40"),
    TimeSlotEntity(5, "14:00", "14:45"),
    TimeSlotEntity(6, "14:55", "15:40"),
    TimeSlotEntity(7, "16:00", "16:45"),
    TimeSlotEntity(8, "16:55", "17:40"),
    TimeSlotEntity(9, "19:00", "19:45"),
    TimeSlotEntity(10, "19:55", "20:40"),
    TimeSlotEntity(11, "20:50", "21:35"),
    TimeSlotEntity(12, "21:45", "22:30"),
)

/**
 * v20 及之前沿用的默认节次。**只服务 v20→v21 的"用户没动过"判据**，
 * 新代码不要拿它当默认值用。
 */
internal val legacyTimeSlotsBeforeV21: List<TimeSlotEntity> = listOf(
    TimeSlotEntity(1, "08:00", "08:45"),
    TimeSlotEntity(2, "08:55", "09:40"),
    TimeSlotEntity(3, "10:00", "10:45"),
    TimeSlotEntity(4, "10:55", "11:40"),
    TimeSlotEntity(5, "13:30", "14:15"),
    TimeSlotEntity(6, "14:25", "15:10"),
    TimeSlotEntity(7, "15:30", "16:15"),
    TimeSlotEntity(8, "16:25", "17:10"),
    TimeSlotEntity(9, "18:30", "19:15"),
    TimeSlotEntity(10, "19:25", "20:10"),
    TimeSlotEntity(11, "20:20", "21:05"),
    TimeSlotEntity(12, "21:15", "22:00"),
)

/**
 * 表内容是否**仍然完全等于**旧默认值。
 *
 * 迁移只在"是"的时候才改写节次时间：用户在「节次管理」里调过任何一节，
 * 整表就原样保留 —— 覆盖用户数据去追一个新默认值，是比时间不对更糟的结果。
 */
internal fun matchesLegacyTimeSlots(rows: List<TimeSlotEntity>): Boolean =
    rows.size == legacyTimeSlotsBeforeV21.size &&
        rows.zip(legacyTimeSlotsBeforeV21).all { (actual, expected) ->
            actual.slot == expected.slot &&
                actual.startTime == expected.startTime &&
                actual.endTime == expected.endTime
        }
