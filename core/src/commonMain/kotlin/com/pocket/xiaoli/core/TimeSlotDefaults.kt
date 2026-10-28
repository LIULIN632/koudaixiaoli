package com.pocket.xiaoli.core

/**
 * 一个节次的纯数据形态（无 ORM 注解、无 Android 类型）。
 *
 * Android 的 `TimeSlotEntity` 与桌面的 `TimeSlot` 都从这里取值，
 * 避免「默认节次表」出现三份字面量。
 */
data class TimeSlotSpec(
    val slot: Int,
    val startTime: String,
    val endTime: String,
)

/**
 * 默认节次表 —— **唯一真源**。
 *
 * 排布规则（2026-10-25 按用户要求定）：上午第一节 08:00、下午第一节 14:00、
 * 晚上第一节 19:00，每节 45 分钟；段内课间 10 分钟，第 2、3 节之间留 20 分钟大课间。
 * 1-4 上午 / 5-8 下午 / 9-12 晚上。
 */
val DefaultTimeSlots: List<TimeSlotSpec> = listOf(
    TimeSlotSpec(1, "08:00", "08:45"),
    TimeSlotSpec(2, "08:55", "09:40"),
    TimeSlotSpec(3, "10:00", "10:45"),
    TimeSlotSpec(4, "10:55", "11:40"),
    TimeSlotSpec(5, "14:00", "14:45"),
    TimeSlotSpec(6, "14:55", "15:40"),
    TimeSlotSpec(7, "16:00", "16:45"),
    TimeSlotSpec(8, "16:55", "17:40"),
    TimeSlotSpec(9, "19:00", "19:45"),
    TimeSlotSpec(10, "19:55", "20:40"),
    TimeSlotSpec(11, "20:50", "21:35"),
    TimeSlotSpec(12, "21:45", "22:30"),
)

/**
 * v20 及之前沿用的默认节次。**只服务 v20→v21 的「用户没动过」判据**，
 * 新代码不要拿它当默认值用。
 */
internal val LegacyTimeSlotsBeforeV21: List<TimeSlotSpec> = listOf(
    TimeSlotSpec(1, "08:00", "08:45"),
    TimeSlotSpec(2, "08:55", "09:40"),
    TimeSlotSpec(3, "10:00", "10:45"),
    TimeSlotSpec(4, "10:55", "11:40"),
    TimeSlotSpec(5, "13:30", "14:15"),
    TimeSlotSpec(6, "14:25", "15:10"),
    TimeSlotSpec(7, "15:30", "16:15"),
    TimeSlotSpec(8, "16:25", "17:10"),
    TimeSlotSpec(9, "18:30", "19:15"),
    TimeSlotSpec(10, "19:25", "20:10"),
    TimeSlotSpec(11, "20:20", "21:05"),
    TimeSlotSpec(12, "21:15", "22:00"),
)

/**
 * 表内容是否**仍然完全等于**旧默认值。
 *
 * 迁移只在「是」的时候才改写节次时间：用户在「节次管理」里调过任何一节，
 * 整表就原样保留 —— 覆盖用户数据去追一个新默认值，是比时间不对更糟的结果。
 */
fun matchesLegacyTimeSlots(rows: List<TimeSlotSpec>): Boolean =
    rows.size == LegacyTimeSlotsBeforeV21.size &&
        rows.zip(LegacyTimeSlotsBeforeV21).all { (actual, expected) ->
            actual.slot == expected.slot &&
                actual.startTime == expected.startTime &&
                actual.endTime == expected.endTime
        }

/** 旧默认节次的数量（迁移判据里用得到）。 */
val LegacyTimeSlotCount: Int get() = LegacyTimeSlotsBeforeV21.size

/** 按序号取旧默认值（迁移时逐行对照）。 */
fun legacyTimeSlotAt(index: Int): TimeSlotSpec = LegacyTimeSlotsBeforeV21[index]
