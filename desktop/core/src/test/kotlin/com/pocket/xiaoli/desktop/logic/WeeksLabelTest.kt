package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.desktop.data.Course
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 周次文案。
 *
 * 教务接口给的是「1,2,3,…,16」这种逐周列表，原样显示就是一串数字噪音，
 * 所以这里把连续周次并成区间、覆盖整学期时改说「全学期」。
 */
class WeeksLabelTest {

    private fun course(weeks: String, weekType: String = "all") =
        Course(id = "c1", name = "工程测量", weeks = weeks, weekType = weekType)

    @Test
    fun `逐周列表压成一段区间`() {
        val list = (1..16).joinToString(",")
        assertEquals("1-16 周", weeksLabel(course(list), 20))
    }

    @Test
    fun `覆盖整学期就说全学期`() {
        val list = (1..16).joinToString(",")
        assertEquals("全学期", weeksLabel(course(list), 16))
    }

    @Test
    fun `断开的周次各自成段`() {
        assertEquals("2-4、9-10、15 周", weeksLabel(course("2,3,4,9,10,15"), 20))
    }

    @Test
    fun `单周不写成区间`() {
        assertEquals("3、7 周", weeksLabel(course("3,7"), 20))
    }

    @Test
    fun `单双周后缀保留在压缩之后`() {
        assertEquals("1-16 周（单）", weeksLabel(course("1-16", "odd"), 20))
        assertEquals("全学期（单）", weeksLabel(course("1-16", "odd"), 16))
    }

    @Test
    fun `没填周次按全学期`() {
        assertEquals("全学期", weeksLabel(course(""), 20))
        assertEquals("全学期（双）", weeksLabel(course("", "even"), 20))
    }

    @Test
    fun `乱序与重复的周次也能压对`() {
        assertEquals("1-3、6 周", weeksLabel(course("3,1,2,1,6"), 20))
    }
}
