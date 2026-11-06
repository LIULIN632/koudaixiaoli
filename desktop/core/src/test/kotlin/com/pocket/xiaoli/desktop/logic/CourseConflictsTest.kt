package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 冲突检测与「老师/教室」候选的判据。 */
class CourseConflictsTest {

    private fun course(
        id: String,
        day: Int,
        start: Int,
        end: Int,
        weeks: String = "",
        weekType: String = "all",
        termId: String = "t1",
        teacher: String = "",
        room: String = "",
        kind: String = "course",
        startMinute: Int = -1,
        endMinute: Int = -1,
    ) = Course(
        id = id,
        termId = termId,
        name = id,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = weeks,
        weekType = weekType,
        kind = kind,
        startMinute = startMinute,
        endMinute = endMinute,
    )

    @Test
    fun `同一天同节次且周次重合才算冲突`() {
        val a = course("A", day = 1, start = 1, end = 2, weeks = "1-16")
        val b = course("B", day = 1, start = 2, end = 3, weeks = "1-16")
        val conflicts = findConflicts(a, listOf(b), totalWeeks = 20)
        assertEquals(1, conflicts.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), conflicts.single().sharedWeeks.take(6))
    }

    @Test
    fun `节次相接不算冲突`() {
        val a = course("A", day = 1, start = 1, end = 2)
        val b = course("B", day = 1, start = 3, end = 4)
        assertTrue(findConflicts(a, listOf(b), 20).isEmpty())
    }

    @Test
    fun `单双周错开不算冲突`() {
        val odd = course("A", day = 3, start = 1, end = 2, weeks = "1-15", weekType = "odd")
        val even = course("B", day = 3, start = 1, end = 2, weeks = "2-16", weekType = "even")
        assertTrue(findConflicts(odd, listOf(even), 20).isEmpty(), "单双周错开不该报冲突")
        val same = course("C", day = 3, start = 1, end = 2, weeks = "1-15", weekType = "odd")
        assertEquals(1, findConflicts(odd, listOf(same), 20).size)
    }

    @Test
    fun `周次区间不重合不算冲突`() {
        val early = course("A", day = 5, start = 1, end = 2, weeks = "1-8")
        val late = course("B", day = 5, start = 1, end = 2, weeks = "9-16")
        assertTrue(findConflicts(early, listOf(late), 20).isEmpty())
    }

    @Test
    fun `其他学期与自身不算冲突`() {
        val a = course("A", day = 1, start = 1, end = 2)
        val otherTerm = course("B", day = 1, start = 1, end = 2, termId = "t2")
        assertTrue(findConflicts(a, listOf(otherTerm, a), 20).isEmpty())
    }

    @Test
    fun `日程按分钟区间比，课程不参与`() {
        val agenda = course("接人", day = 2, start = 1, end = 1, kind = "agenda", startMinute = 600, endMinute = 660)
        val clash = course("开会", day = 2, start = 1, end = 1, kind = "agenda", startMinute = 630, endMinute = 700)
        val far = course("晚饭", day = 2, start = 1, end = 1, kind = "agenda", startMinute = 1100, endMinute = 1160)
        assertEquals(1, findAgendaClashes(agenda, listOf(clash, far)).size)
        // 日程与课程节次不可比，findConflicts 应直接跳过
        assertTrue(findConflicts(agenda, listOf(course("课", day = 2, start = 1, end = 2)), 20).isEmpty())
    }

    @Test
    fun `候选值按出现次数排并去空白`() {
        val courses = listOf(
            course("A", 1, 1, 2, teacher = "李慧敏", room = "三教 A-302"),
            course("B", 2, 1, 2, teacher = "李慧敏", room = "三教 A-302"),
            course("C", 3, 1, 2, teacher = "张博文", room = "  "),
        )
        assertEquals(listOf("李慧敏", "张博文"), suggestValues(courses, pick = { it.teacher }))
        assertEquals(listOf("三教 A-302"), suggestValues(courses, pick = { it.room }))
    }

    @Test
    fun `daysUntil 解析不出日期返回 null`() {
        assertEquals(0L, daysUntil("2026-11-02", LocalDate.of(2026, 11, 2)))
        assertEquals(3L, daysUntil("2026-11-05", LocalDate.of(2026, 11, 2)))
        assertNull(daysUntil("待定", LocalDate.of(2026, 11, 2)))
    }
}
