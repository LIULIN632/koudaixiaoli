package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程简称：内置表 → 规则兜底 → （推不出就 null，交给两行显示）。
 *
 * 守的是 2026-10 用户报的另一个 P0：
 * "「工程会计与财」「建筑设备与智」「形势与政」这种截断非常影响识别。"
 */
class CourseShortNameTest {

    @Test
    fun `内置表里的长课名给出人手定好的简称`() {
        assertEquals("工程会计", CourseShortName.suggest("工程会计与财务管理"))
        assertEquals("建筑设备", CourseShortName.suggest("建筑设备与智能化"))
        assertEquals("毛概", CourseShortName.suggest("毛泽东思想和中国特色社会主义理论体系概论"))
    }

    @Test
    fun `规则一：去掉结尾的括号说明`() {
        assertEquals("大学英语", CourseShortName.suggest("大学英语（一）"))
        assertEquals("体育", CourseShortName.suggest("体育(三)"))
    }

    @Test
    fun `规则二：在「与」处断开长课名`() {
        // 内置表没收录、但断得放心的长课名
        assertEquals("数据库原理", CourseShortName.suggest("数据库原理与SQL实现"))
    }

    @Test
    fun `⚠️「形势与政策」不许被断成「形势」`() {
        // 5 个字，本来就没超长；而且它是一门课，不是两门并列 ——
        // 这条是"宁可返回 null 也不硬断"的守门人（用户正是拿它举例的）
        assertNull(CourseShortName.suggest("形势与政策"))
    }

    @Test
    fun `规则三：去掉结尾的通用词`() {
        assertEquals("单片机原理", CourseShortName.suggest("单片机原理及应用"))
    }

    @Test
    fun `不硬截断：推不出好简称时返回 null`() {
        // 「土木工程施工」7 个字 —— 取前 6 会得到「土木工程施」这种既不是原名也不像简称的东西。
        // 宁可不给简称，让它两行显示。
        assertNull(CourseShortName.suggest("土木工程施工"))
        assertNull(CourseShortName.suggest("高等数学"))
        assertNull(CourseShortName.suggest(""))
        assertNull(CourseShortName.suggest("   "))
    }

    @Test
    fun `简称一定比原名短，且不超长`() {
        val names = CourseShortName.BUILTIN.keys + listOf(
            "大学英语（一）", "数据库原理与SQL实现", "单片机原理及应用", "土木工程施工", "形势与政策",
        )
        names.forEach { name ->
            val short = CourseShortName.suggest(name)
            if (short != null) {
                assertTrue("$name 的简称「$short」没比原名短", short.length < name.length)
                assertTrue("$name 的简称「$short」超过 ${CourseShortName.MAX_CHARS} 字", short.length <= CourseShortName.MAX_CHARS)
                assertTrue("简称不能是空白", short.isNotBlank())
            }
        }
    }

    @Test
    fun `内置表的键与值都不该有空白烂数据`() {
        CourseShortName.BUILTIN.forEach { (full, short) ->
            assertEquals("内置表的键不该带首尾空白：$full", full.trim(), full)
            assertTrue("$full 的简称是空的", short.isNotBlank())
            assertTrue("$full 的简称没比原名短", short.length < full.length)
            assertTrue("$full 的简称超过 ${CourseShortName.MAX_CHARS} 字", short.length <= CourseShortName.MAX_CHARS)
        }
    }
}
