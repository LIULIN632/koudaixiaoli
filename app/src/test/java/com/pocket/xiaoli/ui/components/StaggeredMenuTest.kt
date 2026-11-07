package com.pocket.xiaoli.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 菜单逐项出场的曲线（纯函数）。
 *
 * 这段只有"第 i 项在时间轴 p 上出现多少"一个职责，但两个语义写错了**看不出来**：
 * ① 顺序反了 —— 相邻项交换先后，动画照样顺滑，只有盯着看才发现"最后一项先跳出来"；
 * ② 关闭时不倒放 —— 每项各自淡出，等于没有"逆序收回"。
 * 两条都由下面几条断言钉住（倒放是靠同一时间轴反向播放，所以只要"靠前的项在任意时刻 >= 靠后的项"，
 * 倒放时自然就是靠后的先消失）。
 */
class StaggeredMenuTest {

    @Test
    fun `首尾对齐时间轴两端`() {
        val count = 5
        repeat(count) { i ->
            assertEquals("p=0 时全都不可见（第 $i 项）", 0f, menuItemProgress(i, 0f, count), 0.0001f)
            assertEquals("p=1 时全都出现（第 $i 项）", 1f, menuItemProgress(i, 1f, count), 0.0001f)
        }
    }

    @Test
    fun `同一项随时间单调不减`() {
        val count = 4
        for (i in 0 until count) {
            var last = -1f
            var p = 0f
            while (p <= 1f) {
                val v = menuItemProgress(i, p, count)
                assertTrue("第 $i 项在 p=$p 倒退了：$last -> $v", v >= last - 0.0001f)
                last = v
                p += 0.02f
            }
        }
    }

    @Test
    fun `靠前的项永远不落后于靠后的项`() {
        // 这条就是"逆序收回"的判据：倒放时间轴时，index 大的先归零
        val count = 6
        var p = 0f
        while (p <= 1f) {
            val values = (0 until count).map { menuItemProgress(it, p, count) }
            values.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue("p=$p 时第 $i 项($a) 应 >= 第 ${i + 1} 项($b)", a >= b - 0.0001f)
            }
            p += 0.02f
        }
    }

    @Test
    fun `最后一项在时间轴后段才开始出现`() {
        val count = 4
        val start = lastItemStartProgress(count)
        // 40*3 / (160 + 40*3) = 120 / 280
        assertEquals(120f / 280f, start, 0.001f)
        assertEquals(0f, menuItemProgress(count - 1, start, count), 0.0001f)
        assertTrue(menuItemProgress(count - 1, start + 0.01f, count) > 0f)
    }

    @Test
    fun `单项目录直接跟随时间轴`() {
        assertEquals(0.3f, menuItemProgress(0, 0.3f, 1), 0.0001f)
        assertEquals(0f, lastItemStartProgress(1), 0.0001f)
    }

    @Test
    fun `越界的时间轴被夹紧`() {
        assertEquals(0f, menuItemProgress(2, -1f, 3), 0.0001f)
        assertEquals(1f, menuItemProgress(2, 5f, 3), 0.0001f)
    }
}
