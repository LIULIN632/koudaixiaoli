package com.pocket.xiaoli.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** 自定义首页：排序归一化与移动。首页只保留「课程卡片 + 待办」两块。 */
class HomeLayoutTest {

    @Test
    fun normalizeFillsMissingModulesInDefaultOrder() {
        val order = HomeLayout.normalize(listOf("todo"))
        assertEquals(2, order.size)
        assertEquals(HomeModule.TODO, order[0])
        assertEquals(HomeModule.COURSE_CARD, order[1])
    }

    @Test
    fun normalizeDropsUnknownDuplicatesAndRemovedModules() {
        // header / quick_actions / learning_sync 是已删除的旧模块，必须被丢弃
        val order = HomeLayout.normalize(
            listOf("todo", "unknown", "todo", "header", "quick_actions", "learning_sync")
        )
        assertEquals(2, order.size)
        assertEquals(HomeModule.TODO, order[0])
        assertEquals(HomeModule.COURSE_CARD, order[1])
    }

    @Test
    fun normalizeEmptyFallsBackToDefaultOrder() {
        assertEquals(HomeLayout.defaultOrder, HomeLayout.normalize(emptyList()))
    }

    @Test
    fun moveChangesOrder() {
        val moved = HomeLayout.move(listOf("a", "b", "c"), 0, 2)
        assertEquals(listOf("b", "c", "a"), moved)
    }

    @Test
    fun moveOutOfRangeKeepsOriginal() {
        val original = listOf("a", "b", "c")
        assertEquals(original, HomeLayout.move(original, -1, 1))
        assertEquals(original, HomeLayout.move(original, 1, 3))
    }
}
