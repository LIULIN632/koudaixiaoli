package com.pocket.xiaoli.core.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 「下一节课」的规则表。
 *
 * 这些用例原本是两端各写一份的（Android `UpcomingCourseTest`、桌面手工点验），
 * 现在钉在共享层：任何一端改了排序/口径，这里先红。
 */
class UpcomingCourseCoreTest {

    private fun candidate(
        dayOfWeek: Int,
        startSlot: Int,
        startMinute: Int,
        endMinute: Int,
        occurs: Boolean = true,
    ) = UpcomingCandidate(dayOfWeek, startSlot, startMinute, endMinute, occurs)

    // 2026-10-26 是周一
    private val monday = 1

    @Test
    fun `今天还有没下课的课，取节次最早那条`() {
        val courses = listOf(
            candidate(monday, startSlot = 5, startMinute = 14 * 60, endMinute = 15 * 60 + 40),
            candidate(monday, startSlot = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40),
        )
        // 08:30：第一节还没下课 → 命中 1（不是 5）
        val pick = pickUpcomingCourse(courses, todayDayOfWeek = monday, nowMinute = 8 * 60 + 30)
        assertEquals(UpcomingPick(index = 1, dayOffset = 0), pick)
    }

    @Test
    fun `今天的课都下课了，顺延到明天`() {
        val courses = listOf(
            candidate(monday, startSlot = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40),
            candidate(dayOfWeek = 2, startSlot = 3, startMinute = 10 * 60, endMinute = 11 * 60 + 40),
        )
        val pick = pickUpcomingCourse(courses, todayDayOfWeek = monday, nowMinute = 20 * 60)
        assertEquals(UpcomingPick(index = 1, dayOffset = 1), pick)
    }

    @Test
    fun `下课时间未知的课算还没下课`() {
        val courses = listOf(candidate(monday, startSlot = 2, startMinute = 10 * 60, endMinute = -1))
        val pick = pickUpcomingCourse(courses, todayDayOfWeek = monday, nowMinute = 22 * 60)
        assertEquals(UpcomingPick(index = 0, dayOffset = 0), pick)
    }

    @Test
    fun `放假日跳过且不回退到今天`() {
        val courses = listOf(
            candidate(monday, startSlot = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40),
            candidate(dayOfWeek = 3, startSlot = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40),
        )
        // 周二（offset=1）放假 → 应落到周三（offset=2）
        val pick = pickUpcomingCourse(
            courses,
            todayDayOfWeek = monday,
            nowMinute = 20 * 60,
            isOffDay = { it == 1 },
        )
        assertEquals(UpcomingPick(index = 1, dayOffset = 2), pick)
    }

    @Test
    fun `周次不命中的课不参与挑选`() {
        val courses = listOf(candidate(monday, startSlot = 1, startMinute = 8 * 60, endMinute = 9 * 60 + 40, occurs = false))
        assertNull(pickUpcomingCourse(courses, todayDayOfWeek = monday, nowMinute = 8 * 60))
    }

    @Test
    fun `一周内都没课返回 null`() {
        assertNull(pickUpcomingCourse(emptyList(), todayDayOfWeek = monday, nowMinute = 8 * 60))
    }

    @Test
    fun `周日往后扫会绕回周一`() {
        val courses = listOf(candidate(dayOfWeek = 1, startSlot = 2, startMinute = 10 * 60, endMinute = 11 * 60 + 40))
        // 今天是周日(7)、当前 20:00；offset=1 → 周一(1)
        val pick = pickUpcomingCourse(courses, todayDayOfWeek = 7, nowMinute = 20 * 60)
        assertEquals(UpcomingPick(index = 0, dayOffset = 1), pick)
    }
}
