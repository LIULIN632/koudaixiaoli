package com.pocket.xiaoli.data.contribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 客户端口径与昵称规则。
 *
 * 分值 / 上限在服务端也有一份（那边是权威）。这里钉住的是**界面文案与本地拦截**用的那份：
 * 两边改得不一样时，用户会看到「+10」却只加 5 分，或者本地以为还能加、服务端回 429。
 */
class ContributionModelsTest {

    @Test
    fun `五个动作的分值与每日上限`() {
        val expected = mapOf(
            ContributionAction.COURSE_IMPORT to (ContributionCategory.TIMETABLE to (10 to 1)),
            ContributionAction.ROOM_CELL_ADD to (ContributionCategory.CAMPUS to (5 to 5)),
            ContributionAction.ROOM_CELL_EDIT to (ContributionCategory.CAMPUS to (2 to 10)),
            ContributionAction.NOTE_SAVE to (ContributionCategory.NOTES to (5 to 3)),
            ContributionAction.FEEDBACK_SEND to (ContributionCategory.FEEDBACK to (5 to 1)),
        )
        ContributionAction.entries.forEach { action ->
            val (category, pointsAndCap) = expected.getValue(action)
            val (points, cap) = pointsAndCap
            assertEquals(action.wire, category, action.category)
            assertEquals(action.wire, points, action.points)
            assertEquals(action.wire, cap, action.dailyCap)
        }
    }

    @Test
    fun `昵称清洗与服务端同口径`() {
        assertEquals("鲤鱼王", ContributionNickname.normalize("  鲤鱼王  "))
        assertNotNull(ContributionNickname.normalize("🐟".repeat(12)))
        assertNull(ContributionNickname.normalize("🐟".repeat(13)))
        assertNull(ContributionNickname.normalize(""))
        assertNull(ContributionNickname.normalize("小\n鲤"))
        assertNull(ContributionNickname.normalize(" Admin "))
        assertNull(ContributionNickname.normalize("管理员"))
    }

    @Test
    fun `昵称错误提示分空与非法两种`() {
        assertNull(ContributionNickname.errorText("鲤鱼王"))
        assertEquals("先起一个昵称", ContributionNickname.errorText("  "))
        assertNotNull(ContributionNickname.errorText("这个名字实在是太长了根本放不下"))
    }

    @Test
    fun `分类与周期能从线上值反解`() {
        ContributionCategory.entries.forEach {
            assertEquals(it, ContributionCategory.fromWire(it.wire))
        }
        ContributionPeriod.entries.forEach {
            assertEquals(it, ContributionPeriod.fromWire(it.wire))
        }
        assertEquals(ContributionCategory.ALL, ContributionCategory.fromWire("bogus"))
        assertEquals(ContributionPeriod.WEEK, ContributionPeriod.fromWire(null))
    }

    @Test
    fun `只有进度类动作有得分 其它字段不参与计算`() {
        assertTrue(ContributionAction.entries.all { it.points > 0 })
        assertFalse(ContributionAction.fromWire("points") != null)
    }
}
