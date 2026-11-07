package com.pocket.xiaoli.core.academic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 列号 → 星期几的换算。
 *
 * 这组用例守的是 2026-11-06 的真机报障：**导入后课程全挤在最右几列**。
 * 根因是两个解析器都用 `col.coerceIn(1, 7)` 兜底 —— 表格前导列（时间、节次）
 * 比 1 多时，第 8、9 列被压成周日，整周课程就堆到周末去了。
 */
class DayResolutionTest {

    @Test
    fun `表头给出绝对列号时直接照用`() {
        val dayMap = (1..7).associateWith { it }

        assertEquals(1, resolveDayOfWeek(dayMap, 1, 8))
        assertEquals(5, resolveDayOfWeek(dayMap, 5, 8))
        assertEquals(7, resolveDayOfWeek(dayMap, 7, 8))
        assertNull("第 8 列不是星期列", resolveDayOfWeek(dayMap, 8, 8))
    }

    @Test
    fun `表头有两列前导时周一在第三列`() {
        // 真实页面：第一列表头是"时间"，第二列是"节次"，星期从第 3 列开始
        val dayMap = (2..8).associateWith { it - 1 }

        assertEquals(1, resolveDayOfWeek(dayMap, 2, 9))
        assertEquals(7, resolveDayOfWeek(dayMap, 8, 9))
        assertNull("周日之后的列不该硬塞成周日", resolveDayOfWeek(dayMap, 9, 9))
    }

    @Test
    fun `表头没认出来时按总列数推前导列数`() {
        // 9 列 = 2 列前导 + 7 天
        assertEquals(1, resolveDayOfWeek(emptyMap(), 2, 9))
        assertEquals(7, resolveDayOfWeek(emptyMap(), 8, 9))
        assertNull(resolveDayOfWeek(emptyMap(), 1, 9))

        // 8 列 = 1 列前导 + 7 天
        assertEquals(1, resolveDayOfWeek(emptyMap(), 1, 8))
        assertEquals(7, resolveDayOfWeek(emptyMap(), 7, 8))
    }

    @Test
    fun `旧的 colOffset 口径仍然兼容`() {
        // dayMap 以"减掉 colOffset 之后"的列号为键（老代码就是这个口径）
        val dayMap = (0..6).associateWith { it + 1 }

        assertEquals(1, resolveDayOfWeek(dayMap, 1, 8, colOffset = 1))
        assertEquals(7, resolveDayOfWeek(dayMap, 7, 8, colOffset = 1))
    }
}
