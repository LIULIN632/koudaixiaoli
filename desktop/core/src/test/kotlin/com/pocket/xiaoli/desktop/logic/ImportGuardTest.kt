package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.academic.ParsedCourse
import com.pocket.xiaoli.desktop.data.Course
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 教务导入的「替换计划」测试。
 *
 * 这一层是防呆的最后一道：导入是整学期覆盖，抓回来的课少了、或者其实是占位内容，
 * 都必须停在确认框而不是把用户原本完整的课表换掉。
 */
class ImportGuardTest {

    private val sampleTeachers = setOf("王海涛", "李慧敏", "Sarah")

    private fun parsed(name: String, teacher: String = "真实老师") = ParsedCourse(name = name, teacher = teacher)

    private fun course(id: String, termId: String, kind: String = "course") =
        Course(id = id, termId = termId, name = id, kind = kind)

    private fun many(count: Int) = (1..count).map { parsed("课$it") }

    @Test
    fun `完整导入不需要确认`() {
        val plan = planImport(many(12), emptyList(), "t1", sampleTeachers)
        assertEquals(12, plan.willImport)
        assertFalse(plan.needsConfirm)
        assertEquals("将导入 12 门 · 覆盖 0 门 · 保留 0 条", plan.summary)
    }

    @Test
    fun `门数偏少要确认`() {
        val plan = planImport(many(2), emptyList(), "t1", sampleTeachers)
        assertTrue(plan.needsConfirm)
        assertTrue(plan.warnings.any { "可能不完整" in it })
    }

    @Test
    fun `教务返回空列表要确认`() {
        val plan = planImport(emptyList(), listOf(course("c1", "t1")), "t1", sampleTeachers)
        assertTrue(plan.needsConfirm)
        assertTrue(plan.warnings.any { "没有返回任何课程" in it })
    }

    @Test
    fun `老师名全是内置示例时判为占位内容`() {
        val courses = listOf(parsed("计算机网络", "王海涛"), parsed("数据结构", "李慧敏")) + many(6)
        val plan = planImport(courses, emptyList(), "t1", sampleTeachers)
        assertFalse(plan.warnings.any { "占位" in it }, "掺了真实老师就不该报占位：${plan.warnings}")
        val allSample = listOf(parsed("A", "王海涛"), parsed("B", "Sarah"))
        assertTrue(planImport(allSample, emptyList(), "t1", sampleTeachers).warnings.any { "占位" in it })
    }

    @Test
    fun `替换后数量变少要提醒`() {
        val existing = (1..13).map { course("old$it", "t1") }
        val plan = planImport(many(8), existing, "t1", sampleTeachers)
        assertEquals(13, plan.willReplace)
        assertTrue(plan.warnings.any { "数量变少" in it })
    }

    @Test
    fun `自建日程与其他学期保留`() {
        val existing = listOf(
            course("keep-agenda", "t1", kind = "agenda"),
            course("keep-other-term", "t2"),
            course("replace-me", "t1"),
        )
        val plan = planImport(many(6), existing, "t1", sampleTeachers)
        assertEquals(2, plan.willKeep)
        assertEquals(1, plan.willReplace)
    }

    @Test
    fun `没有当前学期时不会算出覆盖数`() {
        // termId 为空串（activeTerm == null）：现有课都不属于它，因此不会被判成"要覆盖"
        val plan = planImport(many(6), listOf(course("c1", "t1")), "", sampleTeachers)
        assertEquals(0, plan.willReplace)
        assertEquals(1, plan.willKeep)
    }
}
