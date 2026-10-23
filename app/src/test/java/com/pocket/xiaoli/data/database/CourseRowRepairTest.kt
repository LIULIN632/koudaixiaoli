package com.pocket.xiaoli.data.database

import com.pocket.xiaoli.util.CourseColorIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `MIGRATION_16_17` 的**决策层**：老课表里每一行该改成什么。
 *
 * ## 为什么这个测试比迁移体本身重要
 *
 * 本项目没有引 `room-testing`，迁移体（`SupportSQLiteDatabase`）在单测里跑不起来 ——
 * 能测的只有这段纯判断。所以判断全部搬到这里，迁移体只剩"读行 → 调 plan → 执行 UPDATE"
 * （见 `PocketXiaoliDatabase.repairCourseRows`）。**改迁移前先改这里。**
 */
class CourseRowRepairTest {

    private fun row(
        id: Long,
        name: String,
        dayOfWeek: Int,
        startSlot: Int,
        colorIndex: Int,
        shortName: String = "",
    ) = CourseRowRepair.Row(id, name, dayOfWeek, startSlot, colorIndex, shortName)

    @Test
    fun `位置公式与改造前的实现一致`() {
        // 全仓 9 处解析器当年都是这么写的：(day * 3 + startSlot) % 15
        assertEquals((2 * 3 + 1) % 15, CourseRowRepair.legacyPositionalIndex(2, 1))
        assertEquals((5 * 3 + 3) % 15, CourseRowRepair.legacyPositionalIndex(5, 3))
        assertEquals(12, CourseRowRepair.legacyPositionalIndex(5, 12)) // 27 % 15 = 12
    }

    @Test
    fun `看起来是自动分配的颜色被重算成按课名派生的稳定色`() {
        val fix = CourseRowRepair.plan(
            listOf(row(id = 1, name = "土木工程施工", dayOfWeek = 5, startSlot = 3, colorIndex = (5 * 3 + 3) % 15))
        ).single()

        assertEquals(CourseColorIndex.forCourseName("土木工程施工"), fix.colorIndex)
        assertFalse("自动值不该被当成用户手选", fix.colorPinned)
    }

    @Test
    fun `用户手选的颜色原样保留并标记为钉住`() {
        // 周二第 1 节的自动值是 7，但用户把它改成了 3 —— 迁移必须尊重它
        val fix = CourseRowRepair.plan(
            listOf(row(id = 2, name = "高等数学", dayOfWeek = 2, startSlot = 1, colorIndex = 3))
        ).single()

        assertEquals(3, fix.colorIndex)
        assertTrue("不等于位置值 = 用户选过，必须钉住", fix.colorPinned)
    }

    @Test
    fun `同一门课的两行修完之后颜色一致`() {
        val name = "土木工程施工"
        val fixes = CourseRowRepair.plan(
            listOf(
                row(id = 1, name = name, dayOfWeek = 2, startSlot = 1, colorIndex = (2 * 3 + 1) % 15),
                row(id = 2, name = name, dayOfWeek = 5, startSlot = 3, colorIndex = (5 * 3 + 3) % 15),
            )
        )
        assertEquals("两行必须修成同一个颜色", fixes[0].colorIndex, fixes[1].colorIndex)
        assertEquals(fixes[0].colorIndex, CourseColorIndex.forCourseName(name))
    }

    @Test
    fun `简称：空白的按规则补上，用户填过的不动`() {
        val fixes = CourseRowRepair.plan(
            listOf(
                row(id = 1, name = "工程会计与财务管理", dayOfWeek = 1, startSlot = 1, colorIndex = 0),
                row(id = 2, name = "大学英语（一）", dayOfWeek = 1, startSlot = 1, colorIndex = 0, shortName = "英语一"),
            )
        )
        assertEquals("工程会计", fixes[0].shortName)
        assertEquals("用户自己填的简称是权威，迁移不许覆盖", "英语一", fixes[1].shortName)
    }

    @Test
    fun `推不出简称的行保持空白（交给两行显示，不硬截断）`() {
        val fix = CourseRowRepair.plan(
            listOf(row(id = 1, name = "土木工程施工", dayOfWeek = 1, startSlot = 1, colorIndex = 0))
        ).single()
        assertEquals("", fix.shortName)
    }

    @Test
    fun `空表不炸`() {
        assertTrue(CourseRowRepair.plan(emptyList()).isEmpty())
    }
}
