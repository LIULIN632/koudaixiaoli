package com.pocket.xiaoli.util

import com.pocket.xiaoli.core.course.IcsCourseInput
import com.pocket.xiaoli.core.course.expandIcsWeekEvents
import com.pocket.xiaoli.core.weekSet
import com.pocket.xiaoli.core.course.icsEscape
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.parseHomeworkDue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 课程表 ICS 文件导出。
 *
 * 按周拆分事件（每周一条 `VEVENT`），因此单双周 / 指定周次能被正确表达 ——
 * 用 `RRULE:FREQ=WEEKLY;UNTIL` 会把没课的周也展开出来。
 *
 * **「按周展开」在共享核心 `com.pocket.xiaoli.core.course.IcsExpansion` 里**，与 Windows 桌面端
 * 同一份；本对象只负责 Android 侧的输出格式（PRODID / UID 约定）。不依赖 Android，便于单测。
 */
object CourseIcsExporter {

    private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun export(
        courses: List<CourseEntity>,
        timeSlots: List<TimeSlotEntity>,
        semesterStart: String,
        totalWeeks: Int,
        now: LocalDateTime = LocalDateTime.now(),
        /** 未完成且截止可解析的作业：每条一个带提醒的事件（前 24h / 前 1h 各一次）。 */
        homeworks: List<HomeworkEntity> = emptyList(),
    ): String {
        val startDate = parseSemesterStart(semesterStart) ?: return emptyCalendar()
        val slotMap = timeSlots.associateBy { it.slot }
        val stamp = stampFormatter.format(now)

        val byId = courses.associateBy { it.id.toString() }
        val inputs = courses.map { course ->
            IcsCourseInput(
                id = course.id.toString(),
                name = course.name,
                teacher = course.teacher,
                room = course.room,
                dayOfWeek = course.dayOfWeek,
                weeks = weekSet(course.weekType, course.weeks, totalWeeks),
                startSlot = course.startSlot,
                endSlot = course.endSlot,
                startMinute = course.startMinute,
                endMinute = course.endMinute,
            )
        }
        val events = expandIcsWeekEvents(
            courses = inputs,
            slotStart = slotMap.mapValues { it.value.startTime },
            slotEnd = slotMap.mapValues { it.value.endTime },
            semesterStartEpochDay = startDate.toEpochDay(),
            totalWeeks = totalWeeks,
        )

        return buildString {
            appendHeader()
            events.forEach { event ->
                val course = byId[event.course.id] ?: return@forEach
                append("BEGIN:VEVENT\r\n")
                append("UID:").append(event.course.id).append('-').append(event.week)
                    .append("@pocketxiaoli.local\r\n")
                append("DTSTAMP:").append(stamp).append("\r\n")
                append("DTSTART:").append(event.dateCompact).append('T').append(event.startCompact)
                    .append("00\r\n")
                append("DTEND:").append(event.dateCompact).append('T').append(event.endCompact)
                    .append("00\r\n")
                append("SUMMARY:").append(icsEscape(event.course.name)).append("\r\n")
                if (event.course.room.isNotBlank()) {
                    append("LOCATION:").append(icsEscape(event.course.room)).append("\r\n")
                }
                append("DESCRIPTION:").append(icsEscape(courseDescription(course))).append("\r\n")
                append("END:VEVENT\r\n")
            }
            // 作业截止也进日历：导入系统日历后，哪怕本 App 的闹钟被 ROM 杀掉，
            // 系统日历自己还会再提醒一次 —— 用户要的那道"防忘保险"。
            homeworks.filterNot { it.completed }.forEach { hw ->
                val due = parseHomeworkDue(hw.dueDate, now) ?: return@forEach
                val summary = "作业截止：${hw.courseName}"
                append("BEGIN:VEVENT\r\n")
                append("UID:hw-").append(hw.id).append("@pocketxiaoli.local\r\n")
                append("DTSTAMP:").append(stamp).append("\r\n")
                // 零时长事件：它是个"截止时刻"，不是一段时间
                append("DTSTART:").append(stampFormatter.format(due)).append("\r\n")
                append("DTEND:").append(stampFormatter.format(due)).append("\r\n")
                append("SUMMARY:").append(icsEscape(summary)).append("\r\n")
                if (hw.content.isNotBlank()) {
                    append("DESCRIPTION:").append(icsEscape(hw.content)).append("\r\n")
                }
                repeat(2) { i ->
                    append("BEGIN:VALARM\r\n")
                    append("ACTION:DISPLAY\r\n")
                    append("DESCRIPTION:").append(icsEscape(summary)).append("\r\n")
                    append("TRIGGER:").append(if (i == 0) "-P1D" else "-PT1H").append("\r\n")
                    append("END:VALARM\r\n")
                }
                append("END:VEVENT\r\n")
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
}
