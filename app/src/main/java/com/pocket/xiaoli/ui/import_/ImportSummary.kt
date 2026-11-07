package com.pocket.xiaoli.ui.import_

import com.pocket.xiaoli.data.entity.CourseEntity

/**
 * 导入完成后给用户看的一句话摘要。
 *
 * 为什么不在卡上直接写「已导入 N 门课程」就完了：导入失败的典型后果不是 0 门，而是
 * **看着有课但对不上**（全挤一天、周次缺失、教室全空）。这些都能从解析结果里算出来，
 * 提前告诉用户比让他自己去课表里找快得多。
 *
 * 纯函数，不碰 Android —— 单测里能直接喂各种脏数据（见 `ImportSummaryTest`）。
 */
data class ImportSummary(
    val courseCount: Int,
    val dayCount: Int,
    val firstDay: Int?,
    val lastDay: Int?,
    val weekFrom: Int?,
    val weekTo: Int?,
    val missingTeacher: Int,
    val missingRoom: Int,
    val slotFrom: Int,
    val slotTo: Int,
) {
    /** 一句话：`12 门课 · 周一至周五 · 第 1–16 周` */
    fun headline(): String {
        val parts = mutableListOf("$courseCount 门课")
        if (dayCount > 0 && firstDay != null && lastDay != null) {
            parts += if (firstDay == lastDay) dayName(firstDay) else "${dayName(firstDay)}至${dayName(lastDay)}"
        }
        if (weekFrom != null && weekTo != null) {
            parts += if (weekFrom == weekTo) "第 $weekFrom 周" else "第 $weekFrom–$weekTo 周"
        }
        if (slotFrom > 0 && slotTo > 0) parts += "第 $slotFrom–$slotTo 节"
        return parts.joinToString(" · ")
    }

    /** 需要提醒用户去看一眼的地方；空表示没发现问题。 */
    fun warnings(): List<String> {
        val out = mutableListOf<String>()
        if (courseCount == 0) out += "一门课都没解析出来，先看「解析日志」"
        if (courseCount > 0 && dayCount == 1) out += "全部课程都在${firstDay?.let(::dayName) ?: "同一天"}，星期可能读错了"
        if (courseCount > 0 && weekTo == null) out += "周次没读到，课表会按全周显示"
        if (missingRoom > 0) out += "$missingRoom 门课缺教室"
        if (missingTeacher > 0) out += "$missingTeacher 门课缺教师"
        return out
    }

    companion object {
        fun of(courses: List<CourseEntity>): ImportSummary {
            if (courses.isEmpty()) {
                return ImportSummary(0, 0, null, null, null, null, 0, 0, 0, 0)
            }
            val days = courses.map { it.dayOfWeek }.filter { it in 1..7 }.distinct().sorted()
            val weeks = courses.mapNotNull { firstWeek(it.weeks) }
            val slots = courses.map { it.startSlot } + courses.map { it.endSlot }
            return ImportSummary(
                courseCount = courses.size,
                dayCount = days.size,
                firstDay = days.firstOrNull(),
                lastDay = days.lastOrNull(),
                weekFrom = weeks.minOrNull(),
                weekTo = if (weeks.isEmpty()) null else courses.mapNotNull { lastWeek(it.weeks) }.maxOrNull(),
                missingTeacher = courses.count { it.teacher.isBlank() },
                missingRoom = courses.count { it.room.isBlank() },
                slotFrom = slots.filter { it in 1..20 }.minOrNull() ?: 0,
                slotTo = slots.filter { it in 1..20 }.maxOrNull() ?: 0,
            )
        }

        private fun firstWeek(raw: String): Int? =
            raw.split(',', '，', '-', ' ').firstNotNullOfOrNull { it.trim().toIntOrNull()?.takeIf { w -> w in 1..30 } }

        private fun lastWeek(raw: String): Int? =
            raw.split(',', '，', '-', ' ').lastOrNull { it.trim().toIntOrNull()?.let { w -> w in 1..30 } == true }
                ?.trim()?.toIntOrNull()

        fun dayName(day: Int): String = when (day) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "第${day}天"
        }
    }
}
