package com.pocket.xiaoli.core.course

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 课程配色的**主键**规则。
 *
 * 这个文件守的是 2026-10 用户报的 P0 之一：
 * "「土木工程施工」在周二出现粉色、在周五又出现橙色，我会以为是两门课。"
 */
class CourseColorIndexTest {

    @Test
    fun `同一门课在任何一天都是同一个颜色`() {
        val name = "土木工程施工"
        assertEquals(CourseColorIndex.forCourseName(name), CourseColorIndex.forCourseName(name))
    }

    @Test
    fun `首尾空白与中间空格不影响颜色`() {
        val base = CourseColorIndex.forCourseName("高等数学")
        assertEquals(base, CourseColorIndex.forCourseName(" 高等数学 "))
        assertEquals(base, CourseColorIndex.forCourseName("高等 数学"))
        assertEquals(base, CourseColorIndex.forCourseName("高等　数学")) // 全角空格：教务导出里真的出现过
        // 大小写不敏感（英文课名）
        assertEquals(CourseColorIndex.forCourseName("c++"), CourseColorIndex.forCourseName("C++"))
    }

    @Test
    fun `下标一定落在调色板范围内`() {
        val samples = listOf("", " ", "高等数学", "毛概", "体育", "C++程序设计", "习近平新时代中国特色社会主义思想概论")
        samples.forEach { name ->
            val index = CourseColorIndex.forCourseName(name)
            assertTrue(index in 0 until CourseColorIndex.PALETTE_SIZE, "$name -> $index 越界")
        }
    }

    @Test
    fun `不同课名大体落在不同颜色上`() {
        // 不追求零碰撞（15 个槽位塞不下几十门课），但最常见的几门课不该全挤成一色 ——
        // 那等于"颜色"这个信号完全失效。
        val names = listOf(
            "高等数学", "大学英语", "土木工程施工", "工程会计与财务管理",
            "建筑设备与智能建造", "形势与政策", "大学物理", "线性代数",
        )
        val distinct = names.map { CourseColorIndex.forCourseName(it) }.toSet()
        assertTrue(distinct.size >= 5, "8 门常见课只落在 ${distinct.size} 种颜色上，区分度太低")
    }

    @Test
    fun `相邻字符的课名不会挤到同一个颜色`() {
        // 这正是弃用 String.hashCode 的理由：它在 15 色小调色板上对"A/B"这类
        // 只差一个字符的课名雪崩很差。这里只要求**这两个具体样本**不同色。
        assertNotEquals(
            CourseColorIndex.forCourseName("高等数学A"),
            CourseColorIndex.forCourseName("高等数学B"),
        )
    }

    @Test
    fun `教师主键与课程主键走同一套散列`() {
        // 同名教师同色（CourseColorMode.TEACHER 的前提）
        assertEquals(CourseColorIndex.forTeacherName("张伟"), CourseColorIndex.forTeacherName("张伟"))
        assertEquals(CourseColorIndex.forCourseName("张伟"), CourseColorIndex.forTeacherName("张伟"))
    }
}
