package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.entity.CourseEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 周次过滤测试：首页「下一节课」与教室分布图「今日课程」共用这套规则。 */
class CourseWeekTest {

    private fun course(
        weekType: String = "all",
        weeks: String = ""
    ) = CourseEntity(
        name = "测试课",
        dayOfWeek = 1,
        startSlot = 1,
        endSlot = 2,
        weekType = weekType,
        weeks = weeks,
    )

    @Test
    fun allWeeksAlwaysMatch() {
        assertTrue(isCourseInWeek(course(), 1))
        assertTrue(isCourseInWeek(course(), 16))
    }

    @Test
    fun oddWeekType() {
        assertTrue(isCourseInWeek(course(weekType = "odd"), 3))
        assertFalse(isCourseInWeek(course(weekType = "odd"), 4))
    }

    @Test
    fun evenWeekType() {
        assertTrue(isCourseInWeek(course(weekType = "even"), 4))
        assertFalse(isCourseInWeek(course(weekType = "even"), 3))
    }

    @Test
    fun explicitWeeksListOverridesWeekType() {
        val c = course(weekType = "all", weeks = "1,3,5")
        assertTrue(isCourseInWeek(c, 3))
        assertFalse(isCourseInWeek(c, 4))
    }

    @Test
    fun blankWeeksFallsBackToWeekType() {
        assertTrue(isCourseInWeek(course(weekType = "odd", weeks = "  "), 5))
        assertFalse(isCourseInWeek(course(weekType = "odd", weeks = "  "), 6))
    }

    @Test
    fun malformedWeeksAreIgnored() {
        // 全是非法值时视为"没有有效周次"，退回 weekType 判断
        assertTrue(isCourseInWeek(course(weekType = "all", weeks = "a,b"), 7))
    }
}