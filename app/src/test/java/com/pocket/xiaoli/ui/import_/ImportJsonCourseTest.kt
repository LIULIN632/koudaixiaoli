package com.pocket.xiaoli.ui.import_

import com.pocket.xiaoli.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JS 桥（拾光适配器）课程 JSON → 落库实体的纯逻辑测试。
 *
 * ## 为什么单独钉这一小块
 *
 * 网页导入的最终坐标只由这个函数决定，而它以前是这么写的：
 * `startSection ?: 1`。适配器没给出节次时**不报错、不跳过**，而是把课写进第 1 节 ——
 * 于是周一上午十几门课全叠在同一格里（`mergeCourses` 还会把它们合成一块），
 * 用户看到的就是"导入课表，课程都挤在一起"，而导入条数、日志、Toast 全都看着正常。
 *
 * 这门课在第 1 节是事实；几十门课都在第 1 节是编数据。所以这里的要求很硬：
 * **读不到星期或节次就返回 null**，让调用方数出"跳过了几门"并说出来。
 */
class ImportJsonCourseTest {

    private fun json(
        name: String? = "高等数学",
        day: Int? = 1,
        startSection: Int? = 1,
        endSection: Int? = 2,
        weeks: List<Int>? = listOf(1, 2, 3),
        teacher: String? = "张三",
        position: String? = "一教101",
    ) = ImportCourseJson(
        name = name,
        day = day,
        startSection = startSection,
        endSection = endSection,
        weeks = weeks,
        teacher = teacher,
        position = position,
    )

    @Test
    fun `完整的一门课按 JSON 落库`() {
        val course = importJsonToCourse(json(day = 3, startSection = 5, endSection = 6), schemeId = 7L)
            as CourseEntity
        assertEquals("高等数学", course.name)
        assertEquals("张三", course.teacher)
        assertEquals("一教101", course.room)
        assertEquals(3, course.dayOfWeek)
        assertEquals(5, course.startSlot)
        assertEquals(6, course.endSlot)
        assertEquals(7L, course.schemeId)
        assertEquals("1,2,3", course.weeks)
    }

    @Test
    fun `周次乱序也排序后写入`() {
        val course = importJsonToCourse(json(weeks = listOf(16, 1, 8)), schemeId = 0L) as CourseEntity
        assertEquals("1,8,16", course.weeks)
    }

    @Test
    fun `没有节次的课程被跳过而不是写进第 1 节`() {
        // 这两条一起构成"挤在一起"的回归门禁：缺节次 ≠ 缺信息时默认第 1 节。
        assertNull(importJsonToCourse(json(startSection = null), schemeId = 0L))
        assertNull(importJsonToCourse(json(startSection = 0), schemeId = 0L))
        assertNull(importJsonToCourse(json(startSection = -3), schemeId = 0L))
    }

    @Test
    fun `只有开始节次时结束等于开始`() {
        val course = importJsonToCourse(json(startSection = 4, endSection = null), schemeId = 0L)
            as CourseEntity
        assertEquals(4, course.startSlot)
        assertEquals(4, course.endSlot)
    }

    @Test
    fun `结束早于开始时抬到开始节次`() {
        // endSlot < startSlot 会让格子高度算成 0.3 节，课程被压成一条缝。
        val course = importJsonToCourse(json(startSection = 6, endSection = 2), schemeId = 0L)
            as CourseEntity
        assertEquals(6, course.startSlot)
        assertEquals(6, course.endSlot)
    }

    @Test
    fun `星期缺失或越界时跳过`() {
        // Gson 会绕过 Kotlin 的默认值：JSON 里没写 day 时它是 null，不是 1。
        assertNull(importJsonToCourse(json(day = null), schemeId = 0L))
        assertNull(importJsonToCourse(json(day = 0), schemeId = 0L))
        assertNull(importJsonToCourse(json(day = 8), schemeId = 0L))
    }

    @Test
    fun `课名太短或为空时跳过`() {
        assertNull(importJsonToCourse(json(name = null), schemeId = 0L))
        assertNull(importJsonToCourse(json(name = "   "), schemeId = 0L))
        assertNull(importJsonToCourse(json(name = "数"), schemeId = 0L))
    }
}
