package com.pocket.xiaoli.notification

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ClassReminderTest {

    private val zone = ZoneId.systemDefault()
    private val slots = listOf(TimeSlotEntity(1, "08:00", "08:45"))

    private fun millis(date: String, time: String): Long =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `schedules next monday class with reminder offset`() {
        val course = CourseEntity(name = "高等数学", room = "A101", dayOfWeek = 1, startSlot = 1, schemeId = 0)
        val reminder = ClassReminderScheduler.planNextReminder(
            courses = listOf(course),
            slots = slots,
            semesterStart = "2026-09-07",
            reminderMinutes = 15,
            fromMillis = millis("2026-09-07", "07:00"),
            zone = zone
        )
        assertEquals("高等数学", reminder?.courseName)
        assertEquals("A101", reminder?.room)
        assertEquals(millis("2026-09-07", "07:45"), reminder?.triggerAtMillis)
        assertEquals(millis("2026-09-07", "08:00"), reminder?.startAtMillis)
    }

    @Test
    fun `even week course goes to next active week`() {
        val course = CourseEntity(name = "英语", dayOfWeek = 1, startSlot = 1, weekType = "even", schemeId = 0)
        val reminder = ClassReminderScheduler.planNextReminder(
            courses = listOf(course),
            slots = slots,
            semesterStart = "2026-09-07",
            reminderMinutes = 15,
            fromMillis = millis("2026-09-07", "07:00"),
            zone = zone
        )
        assertEquals(millis("2026-09-14", "07:45"), reminder?.triggerAtMillis)
    }

    @Test
    fun `weeks filter is respected`() {
        val course = CourseEntity(name = "物理", dayOfWeek = 3, startSlot = 1, weeks = "2", schemeId = 0)
        val reminder = ClassReminderScheduler.planNextReminder(
            courses = listOf(course),
            slots = slots,
            semesterStart = "2026-09-07",
            reminderMinutes = 15,
            fromMillis = millis("2026-09-08", "07:00"),
            zone = zone
        )
        assertEquals(millis("2026-09-16", "07:45"), reminder?.triggerAtMillis)
    }

    @Test
    fun `passed reminder rolls to next week`() {
        val course = CourseEntity(name = "数学", dayOfWeek = 1, startSlot = 1, schemeId = 0)
        val reminder = ClassReminderScheduler.planNextReminder(
            courses = listOf(course),
            slots = slots,
            semesterStart = "2026-09-07",
            reminderMinutes = 15,
            fromMillis = millis("2026-09-07", "07:50"),
            zone = zone
        )
        assertEquals(millis("2026-09-14", "07:45"), reminder?.triggerAtMillis)
    }

    @Test
    fun `empty courses returns null`() {
        assertNull(
            ClassReminderScheduler.planNextReminder(
                courses = emptyList(),
                slots = slots,
                semesterStart = "2026-09-07",
                reminderMinutes = 15,
                fromMillis = millis("2026-09-07", "07:00"),
                zone = zone
            )
        )
    }
}