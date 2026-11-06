package com.pocket.xiaoli.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 换学校时课表方案判据的测试。
 *
 * ## 这几条钉的是什么事故
 *
 * 导入按 `课程名 + 星期 + 节次` 合并进**当前方案**，而方案表里没有学校字段。
 * 判据写错的表现不是报错，而是**一张课表里混进两所学校的课**：
 * 周三上午同时出现两门不同的课，用户只会觉得 App 乱了，不会怀疑是方案判据。
 * 所以这里把"什么时候改名、什么时候新建、什么时候复用"钉死，
 * 并且钉住那条最容易被"顺手优化"掉的不变量：**有课表的方案永远不许改名成别的学校**。
 */
class SchemePlanTest {

    private val sdut = "山东理工大学"
    private val yit = "烟台理工学院"

    @Test
    fun `已经在这所学校的方案里就什么都不做`() {
        assertEquals(
            SchemePlan.Keep,
            schemePlanFor(
                activeSchemeId = 7L, activeSchemeName = sdut, activeHasCourses = true,
                existingByName = mapOf(sdut to 7L), targetSchoolName = sdut,
            ),
        )
    }

    @Test
    fun `装机后第一次选学校是改名沿用而不是新建`() {
        // 默认方案 id=0 还是空的：新建会留下一个空的「默认课表」让用户去清理
        assertEquals(
            SchemePlan.Rename(0L),
            schemePlanFor(
                activeSchemeId = 0L, activeSchemeName = "默认课表", activeHasCourses = false,
                existingByName = mapOf("默认课表" to 0L), targetSchoolName = yit,
            ),
        )
    }

    @Test
    fun `以前建过这所学校的方案就切回去不重复建`() {
        assertEquals(
            SchemePlan.Reuse(3L),
            schemePlanFor(
                activeSchemeId = 5L, activeSchemeName = yit, activeHasCourses = true,
                existingByName = mapOf(yit to 5L, sdut to 3L), targetSchoolName = sdut,
            ),
        )
    }

    @Test
    fun `当前方案有别的学校的课表时新建一个`() {
        assertEquals(
            SchemePlan.Create,
            schemePlanFor(
                activeSchemeId = 5L, activeSchemeName = yit, activeHasCourses = true,
                existingByName = mapOf(yit to 5L), targetSchoolName = sdut,
            ),
        )
    }

    @Test
    fun `有课表的方案永远不会被改名成别的学校`() {
        // 这是最要紧的一条：把 A 校方案改名成 B 校，A 的课表就挂在 B 名下，
        // 用户看到的是"别的学校的课混进我的课表"，而没有任何一处会报错
        val plans = listOf(
            schemePlanFor(5L, yit, true, mapOf(yit to 5L), sdut),
            schemePlanFor(5L, yit, true, emptyMap(), sdut),
            schemePlanFor(5L, yit, true, mapOf(sdut to 9L), sdut),
        )
        plans.forEach { plan ->
            assertFalse("有课表时不允许改名：$plan", plan is SchemePlan.Rename)
            assertTrue(plan == SchemePlan.Create || plan is SchemePlan.Reuse)
        }
    }

    @Test
    fun `空方案改名用的是当前方案 id 而不是别的`() {
        val plan = schemePlanFor(
            activeSchemeId = 12L, activeSchemeName = "试导方案", activeHasCourses = false,
            existingByName = mapOf("试导方案" to 12L, sdut to 4L), targetSchoolName = sdut,
        )
        assertEquals(SchemePlan.Rename(12L), plan)
    }
}
