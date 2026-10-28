package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * ICS 导出的**端到端口径**（Android 侧）。
 *
 * 重点盯住历史回归：单双周/指定周次的课，导出后**不能出现没课的周**。
 * 早期桌面版用 `RRULE:FREQ=WEEKLY;UNTIL` 一条事件盖住整段，正是踩了这个坑；
 * 现在按周展开由共享核心 `expandIcsWeekEvents` 负责，这里验证接线正确（含 `weekSet` 的口径）。
 */
class CourseIcsExporterTest {

    private val slots = (1..12).map { index ->
        val startMin = 8 * 60 + (index - 1) * 55
        TimeSlotEntity(
            slot = index,
            startTime = clock(startMin),
            endTime = clock(startMin + 45),
        )
    }

    private fun clock(minute: Int): String =
        (minute / 60).toString().padStart(2, '0') + ":" + (minute % 60).toString().padStart(2, '0')

    private fun course(
        weekType: String = "all",
        weeks: String = "",
        dayOfWeek: Int = 1,
        startSlot: Int = 1,
        endSlot: Int = 2,
    ) = CourseEntity(
        name = "数据结构",
        teacher = "李慧敏",
        room = "三教 B-201",
        dayOfWeek = dayOfWeek,
        startSlot = startSlot,
        endSlot = endSlot,
        weekType = weekType,
        weeks = weeks,
    )

    private val now = LocalDateTime.of(2026, 10, 28, 12, 0)

    @Test
    fun `单周课只导出单周 不出现 RRULE`() {
        val ics = CourseIcsExporter.export(
            courses = listOf(course(weekType = "odd", weeks = "1-16")),
            timeSlots = slots,
            semesterStart = "2026-08-31",
            totalWeeks = 20,
            now = now,
        )

        assertFalse("不应再用 RRULE 覆盖整段", ics.contains("RRULE"))
        // 1..16 里的奇数周 = 8 条事件
        assertEquals(8, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun `空周次视为全学期`() {
        val ics = CourseIcsExporter.export(
            courses = listOf(course(weeks = "")),
            timeSlots = slots,
            semesterStart = "2026-08-31",
            totalWeeks = 4,
            now = now,
        )
        assertEquals(4, Regex("BEGIN:VEVENT").findAll(ics).count())
    }

    @Test
    fun `日期与时间落在正确的周次和节次`() {
        val ics = CourseIcsExporter.export(
            courses = listOf(course(weeks = "1", dayOfWeek = 3, startSlot = 3, endSlot = 4)),
            timeSlots = slots,
            semesterStart = "2026-08-31",
            totalWeeks = 20,
            now = now,
        )
        // 2026-08-31 是周一 → 第 1 周周三 = 2026-09-02；第 3 节 09:50 开始、第 4 节 11:30 结束
        assertTrue(ics.contains("DTSTART:20260902T095000"))
        assertTrue(ics.contains("DTEND:20260902T113000"))
        assertTrue(ics.contains("LOCATION:三教 B-201"))
    }

    @Test
    fun `学期开始日期非法时导出空日历`() {
        val ics = CourseIcsExporter.export(
            courses = listOf(course()),
            timeSlots = slots,
            semesterStart = "not-a-date",
            totalWeeks = 20,
            now = now,
        )
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertFalse(ics.contains("BEGIN:VEVENT"))
    }
}
