package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 课程表 ICS 文件导出。
 *
 * 按周拆分事件，沿用 [isCourseInWeek] 处理单双周 / 指定周次。
 * 不依赖 Android，便于单测和复用。
 */
object CourseIcsExporter {

    private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(
        courses: List<CourseEntity>,
        timeSlots: List<TimeSlotEntity>,
        semesterStart: String,
        totalWeeks: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        val startDate = parseSemesterStart(semesterStart) ?: return emptyCalendar()
        val slotMap = timeSlots.associateBy { it.slot }
        val stamp = stampFormatter.format(now)
        val safeWeeks = totalWeeks.coerceIn(1, 30)

        return buildString {
            appendHeader()
            courses.forEach { course ->
                if (course.dayOfWeek !in 1..7) return@forEach
                for (week in 1..safeWeeks) {
                    if (!isCourseInWeek(course, week)) continue
                    val date = startDate
                        .plusWeeks((week - 1).toLong())
                        .plusDays((course.dayOfWeek - 1).toLong())
                    val dateText = date.year.toString().padStart(4, '0') +
                        date.monthValue.toString().padStart(2, '0') +
                        date.dayOfMonth.toString().padStart(2, '0')
                    // 分钟级时间优先（照岱宗 minuteOfDay）：填了时间就不再回退节次表
                    val startTimeText = if (MinuteOfDay.isValid(course.startMinute)) {
                        MinuteOfDay.format(course.startMinute)
                    } else slotMap[course.startSlot]?.startTime
                    val endTimeText = if (MinuteOfDay.isValid(course.endMinute)) {
                        MinuteOfDay.format(course.endMinute)
                    } else slotMap[course.endSlot]?.endTime
                    val start = normalizeTime(startTimeText, "08:00")
                    val end = normalizeTime(endTimeText, "09:40")

                    append("BEGIN:VEVENT\r\n")
                    append("UID:${course.id}-$week@pocketxiaoli.local\r\n")
                    append("DTSTAMP:$stamp\r\n")
                    append("DTSTART:${dateText}T${start}00\r\n")
                    append("DTEND:${dateText}T${end}00\r\n")
                    append("SUMMARY:${icsEscape(course.name)}\r\n")
                    if (course.room.isNotBlank()) {
                        append("LOCATION:${icsEscape(course.room)}\r\n")
                    }
                    append("DESCRIPTION:${icsEscape(courseDescription(course))}\r\n")
                    append("END:VEVENT\r\n")
                }
            }
            append("END:VCALENDAR\r\n")
        }
    }

    private fun StringBuilder.appendHeader() {
        append("BEGIN:VCALENDAR\r\n")
        append("VERSION:2.0\r\n")
        append("PRODID:-//PocketXiaoli//Course ICS//CN\r\n")
        append("CALSCALE:GREGORIAN\r\n")
    }

    private fun emptyCalendar(): String = buildString { appendHeader(); append("END:VCALENDAR\r\n") }

    private fun courseDescription(course: CourseEntity): String {
        return buildString {
            if (course.teacher.isNotBlank()) append("教师: ${course.teacher}")
            if (course.teacher.isNotBlank() && course.room.isNotBlank()) append('\n')
            if (course.room.isNotBlank()) append("教室: ${course.room}")
        }
    }

    private fun normalizeTime(value: String?, fallback: String): String {
        val time = value?.trim().orEmpty().ifBlank { fallback }
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return fallback.replace(":", "")
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour.toString().padStart(2, '0') + minute.toString().padStart(2, '0')
    }

    private fun icsEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
    }
}
