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
 * 分值 / 上限在服务端也有一份（那边是权威）。这里钉住的是**历史账本口径与本地拦截**：
 * 2026-10-24 起界面不再展示分值，但 [ContributionAction.points] 仍要与服务端一致，
 * 否则历史事件表两边记的数会漂；[ContributionAction.dailyCap] 则继续决定本地拦不拦。
 */
class ContributionModelsTest {

    @Test
    fun `五个动作的分值与每日上限仍与历史账本一致`() {
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
    fun `分值只留在历史账本口径里`() {
        assertTrue(ContributionAction.entries.all { it.points > 0 })
        assertFalse(ContributionAction.fromWire("points") != null)
    }

    @Test
    fun `榜单按昵称排序 并重编号`() {
        val sorted = ContributionOrder.sortByName(
            listOf(
                ContributionEntry(9, "早八人", mine = true),
                ContributionEntry(0, "313 路"),
                ContributionEntry(3, "笔记搬运工"),
            ),
        )
        // 数字在前、中文按拼音（bei < zao）；rank 与显示顺序一致，mine 跟着人走。
        assertEquals(
            listOf(
                ContributionEntry(1, "313 路"),
                ContributionEntry(2, "笔记搬运工"),
                ContributionEntry(3, "早八人", mine = true),
            ),
            sorted,
        )
    }
}
