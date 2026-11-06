package com.pocket.xiaoli.core.calendar

/**
 * 校历事件是否覆盖某天（闭区间，首尾都算）。
 *
 * 两端原来各写一遍 `!date.isBefore(start) && !date.isAfter(end)`；
 * 现在统一在这里，用 epoch day 表达，共享层不依赖 `java.time`。
 */
fun eventCoversDay(startEpochDay: Long, endEpochDay: Long, dayEpochDay: Long): Boolean =
    dayEpochDay >= startEpochDay && dayEpochDay <= endEpochDay

/** 同上，但事件只有一天时不必构造结束日。 */
fun eventCoversDay(startEpochDay: Long, endEpochDay: Long?, dayEpochDay: Long): Boolean =
    eventCoversDay(startEpochDay, endEpochDay ?: startEpochDay, dayEpochDay)
