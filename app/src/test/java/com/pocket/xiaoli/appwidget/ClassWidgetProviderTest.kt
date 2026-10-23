package com.pocket.xiaoli.appwidget

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ClassWidgetProviderTest {

    private val slots = listOf(
        TimeSlotEntity(1, "08:00", "08:45"),
        TimeSlotEntity(2, "08:55", "09:40"),
    )
    private val courses = listOf(
        CourseEntity(name = "高数", room = "A101", dayOfWeek = 1, startSlot = 1, schemeId = 0),
        CourseEntity(name = "英语", dayOfWeek = 2, startSlot = 2, schemeId = 0),
    )

    @Test
    fun `returns today courses`() {
        val result = todayCourses(courses, slots, "2026-09-07", LocalDateTime.parse("2026-09-07T07:00"))
        assertEquals(1, result.size)
        assertEquals("高数", result[0].name)
        assertEquals("08:00", result[0].time)
        assertEquals("A101", result[0].room)
    }

    @Test
    fun `filters courses outside active week`() {
        val odd = listOf(CourseEntity(name = "体育", dayOfWeek = 1, startSlot = 1, weekType = "even", schemeId = 0))
        assertTrue(todayCourses(odd, slots, "2026-09-07", LocalDateTime.parse("2026-09-07T07:00")).isEmpty())
    }

    @Test
    fun `empty when no slots`() {
        assertTrue(todayCourses(courses, emptyList(), "2026-09-07", LocalDateTime.parse("2026-09-07T07:00")).isEmpty())
    }
}