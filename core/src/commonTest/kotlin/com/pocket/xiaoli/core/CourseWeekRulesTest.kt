package com.pocket.xiaoli.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseWeekRulesTest {

    @Test
    fun `all 周任何周都出现`() {
        assertTrue(isCourseInWeek("all", "", 1))
        assertTrue(isCourseInWeek("all", "", 17))
    }

    @Test
    fun `单双周过滤`() {
        assertTrue(isCourseInWeek("odd", "", 3))
        assertFalse(isCourseInWeek("odd", "", 4))
        assertTrue(isCourseInWeek("even", "", 4))
        assertFalse(isCourseInWeek("even", "", 5))
    }

    @Test
    fun `周次列表在单双周之内再收窄`() {
        // 单双周先判：第 4 周是偶数周，"odd" 课不出现（列表里写了也没用 —— 与 Android 端历史行为一致）
        assertFalse(isCourseInWeek("odd", "2,4,6", 4))
        // 第 5 周是奇数周，但列表里没有 → 不出现
        assertFalse(isCourseInWeek("odd", "3,5,7", 4))
        // 列表内外都满足 → 出现
        assertTrue(isCourseInWeek("odd", "3,5,7", 5))
        // 没说单双周时，列表就是唯一依据
        assertTrue(isCourseInWeek("all", "2,4,6", 4))
        assertFalse(isCourseInWeek("all", "2,4,6", 5))
    }

    @Test
    fun `列表里的空格与非法项被忽略`() {
        assertTrue(isCourseInWeek("all", " 3 , ,7 ", 7))
        assertFalse(isCourseInWeek("all", " 3 , ,7 ", 5))
    }

    @Test
    fun `列表解析不出数字时退回单双周判断`() {
        assertTrue(isCourseInWeek("odd", "abc,,", 1))
        assertFalse(isCourseInWeek("odd", "abc,,", 2))
    }

    @Test
    fun `parseWeeks 丢掉 0 与负数`() {
        assertEquals(listOf(1, 2), parseWeeks("1,2,0,-3,,"))
        assertEquals(emptyList(), parseWeeks(""))
    }
}
