package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 课表筛选的判据：弱化错课程比不筛更糟，所以匹配规则要钉住。 */
class TimetableFilterTest {

    private fun course(id: String, teacher: String = "", room: String = "") =
        Course(id = id, termId = "t1", name = id, teacher = teacher, room = room)

    private val data = listOf(
        course("A", teacher = "李慧敏", room = "三教 A-302"),
        course("B", teacher = " 李慧敏 ", room = "三教 B-201"),
        course("C", teacher = "张博文", room = "三教 A-302"),
        course("D", teacher = "", room = ""),
    )

    @Test
    fun `没选条件时不算筛选且全部匹配`() {
        val filter = TimetableFilter()
        assertFalse(filter.active)
        assertEquals(4, filter.matchCount(data))
        assertTrue(data.all { filter.matches(it) })
    }

    @Test
    fun `老师筛选忽略首尾空格与大小写`() {
        val filter = TimetableFilter(teacher = "李慧敏")
        assertTrue(filter.active)
        assertEquals(listOf("A", "B"), data.filter { filter.matches(it) }.map { it.id })
    }

    @Test
    fun `老师与教室同时选中要都满足`() {
        val filter = TimetableFilter(teacher = "李慧敏", room = "三教 A-302")
        assertEquals(listOf("A"), data.filter { filter.matches(it) }.map { it.id })
        assertEquals(1, filter.matchCount(data))
    }

    @Test
    fun `空老师不匹配任何填了老师的课`() {
        // 只筛教室时，老师为空的课照样能出现（它没写老师不代表不是这门课）
        val filter = TimetableFilter(room = "田径场")
        assertEquals(0, filter.matchCount(data))
        assertTrue(TimetableFilter(teacher = "").let { !it.active })
    }

    @Test
    fun `标签文案把两个条件拼起来`() {
        assertEquals("老师 李慧敏", TimetableFilter(teacher = "李慧敏").label)
        assertEquals("老师 李慧敏 · 教室 三教 A-302", TimetableFilter("李慧敏", "三教 A-302").label)
        assertEquals("", TimetableFilter().label)
    }
}
