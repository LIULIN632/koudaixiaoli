package com.pocket.xiaoli.util

/**
 * 分钟级时间（照岱宗盒子 `startMinuteOfDay` / `endMinuteOfDay` 的表示：距 00:00 的分钟）。
 *
 * 课表原来只有节次（1-12），实验课 / 临时调课这种「时间落在两节之间」的课表达不了。
 * `CourseEntity.startMinute/endMinute = -1` 表示回退到节次推导；有值则以分钟为准。
 */
object MinuteOfDay {
    const val NONE = -1

    /** "08:30" / "8:5" → 510 / 485；无法解析或越界返回 null。 */
    fun parse(text: String): Int? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 510 → "08:30"；[NONE] 返回空串（调用方据此显示占位）。 */
    fun format(minute: Int): String {
        if (minute < 0) return ""
        val h = minute / 60
        val m = minute % 60
        return String.format("%02d:%02d", h, m)
    }

    /** 是否是有效时间（不是 [NONE]，也在一天之内）。 */
    fun isValid(minute: Int): Boolean = minute in 0..(24 * 60 - 1)
}
