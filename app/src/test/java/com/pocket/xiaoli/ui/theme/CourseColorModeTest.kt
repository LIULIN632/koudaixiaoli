package com.pocket.xiaoli.ui.theme

import com.pocket.xiaoli.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 高级课表设置：不同着色规则下的调色板下标。 */
class CourseColorModeTest {

    @Test
    fun courseModeUsesPinnedColorWhenUserPickedOne() {
        val course = CourseEntity(name = "高数", colorIndex = 7, colorPinned = true)
        assertEquals(7, course.colorIndexFor(CourseColorMode.COURSE))
    }

    /**
     * 2026-10 的 P0：**同一门课必须同色**，与它在星期几、第几节无关。
     *
     * 改造前 `COURSE` 这档直接返回 `colorIndex`，而那个值在导入时是按
     * `(星期 * 3 + 节次) % 15` 分配的 —— 于是「土木工程施工」周二粉色、周五橙色，
     * 用户以为是两门课。这条用例就是那个 bug 的守门人。
     */
    @Test
    fun courseModeIsStableAcrossDaysAndSlots() {
        val tuesday = CourseEntity(name = "土木工程施工", dayOfWeek = 2, startSlot = 1, colorIndex = (2 * 3 + 1) % 15)
        val friday = CourseEntity(name = "土木工程施工", dayOfWeek = 5, startSlot = 3, colorIndex = (5 * 3 + 3) % 15)
        assertEquals(
            "同一门课在周二与周五必须是同一个颜色",
            tuesday.colorIndexFor(CourseColorMode.COURSE),
            friday.colorIndexFor(CourseColorMode.COURSE),
        )
        assertEquals(tuesday.autoColorIndex, tuesday.colorIndexFor(CourseColorMode.COURSE))
    }

    @Test
    fun courseModeIgnoresTheStaleStoredIndexWhenNotPinned() {
        // 自动分配的位置值（未钉住）不该再影响显示
        val course = CourseEntity(name = "高等数学", dayOfWeek = 3, startSlot = 4, colorIndex = 0)
        assertEquals(course.autoColorIndex, course.colorIndexFor(CourseColorMode.COURSE))
    }

    @Test
    fun teacherModeIsStableForSameTeacher() {
        val a = CourseEntity(name = "高数", teacher = "张老师", colorIndex = 1)
        val b = CourseEntity(name = "英语", teacher = "张老师", colorIndex = 9)
        assertEquals(a.colorIndexFor(CourseColorMode.TEACHER), b.colorIndexFor(CourseColorMode.TEACHER))
        assertTrue(a.colorIndexFor(CourseColorMode.TEACHER) in 0..14)
    }

    @Test
    fun weekTypeModeSeparatesOddEvenAll() {
        val all = CourseEntity(name = "A", weekType = "all")
        val odd = CourseEntity(name = "B", weekType = "odd")
        val even = CourseEntity(name = "C", weekType = "even")
        assertEquals(0, all.colorIndexFor(CourseColorMode.WEEK_TYPE))
        assertEquals(1, odd.colorIndexFor(CourseColorMode.WEEK_TYPE))
        assertEquals(2, even.colorIndexFor(CourseColorMode.WEEK_TYPE))
        assertNotEquals(
            all.colorIndexFor(CourseColorMode.WEEK_TYPE),
            odd.colorIndexFor(CourseColorMode.WEEK_TYPE),
        )
    }
}
