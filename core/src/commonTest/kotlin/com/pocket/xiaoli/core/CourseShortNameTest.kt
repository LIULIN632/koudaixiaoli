package com.pocket.xiaoli.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseShortNameTest {

    @Test
    fun `内置表命中直接给简称`() {
        assertEquals("工程会计", CourseShortName.suggest("工程会计与财务管理"))
        assertEquals("毛概", CourseShortName.suggest("毛泽东思想和中国特色社会主义理论体系概论"))
        assertEquals("思政实践", CourseShortName.suggest("思想政治理论课综合实践"))
    }

    @Test
    fun `结尾括号被去掉`() {
        assertEquals("大学英语", CourseShortName.suggest("大学英语（一）"))
        assertEquals("高等数学", CourseShortName.suggest("高等数学(上)"))
    }

    @Test
    fun `与字断开只在两边都够长时发生`() {
        assertEquals("工程会计", CourseShortName.suggest("工程会计与财务管理"))
        assertEquals("建筑设备", CourseShortName.suggest("建筑设备与智能建造"))
        // 5 字的「形势与政策」是一门课，不能被砍成「形势」
        assertNull(CourseShortName.suggest("形势与政策"))
    }

    @Test
    fun `通用尾巴被去掉`() {
        assertEquals("数据库原理", CourseShortName.suggest("数据库原理及应用"))
        assertEquals("计算机网络", CourseShortName.suggest("计算机网络与技术"))
    }

    @Test
    fun `去掉尾巴会短于 3 字时不动`() {
        // 「C语言与应用」去掉「与应用」只剩「C语言」3 字，允许；但「网络与应用」只剩 2 字 → 不动
        assertEquals("网络与应用", CourseShortName.suggest("网络与应用") ?: "网络与应用")
    }

    @Test
    fun `不值得给简称时返回 null`() {
        assertNull(CourseShortName.suggest("数据结构"))
        assertNull(CourseShortName.suggest(""))
        assertNull(CourseShortName.suggest("   "))
    }

    @Test
    fun `简称必须真的比原名短`() {
        val samples = listOf(
            "大学英语（一）",
            "工程会计与财务管理",
            "数据库原理及应用",
            "毛泽东思想和中国特色社会主义理论体系概论",
        )
        for (name in samples) {
            val short = CourseShortName.suggest(name)
            assertTrue(short == null || short.length < name.length, "$name → $short")
        }
    }

    @Test
    fun `内置表不许出现跟原名一样的值`() {
        for ((full, short) in CourseShortName.BUILTIN) {
            assertTrue(short.isNotBlank(), "$full 的简称是空白")
            assertTrue(short != full, "$full 的简称与原名相同")
        }
    }
}
