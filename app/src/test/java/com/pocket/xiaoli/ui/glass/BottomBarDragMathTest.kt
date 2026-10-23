package com.pocket.xiaoli.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 底栏拖拽计算的边界。
 *
 * 这些用例守的是**真机上偶发、肉眼回归不了**的几种表现：
 * 松手落错一格、拖到两端跑出栏外、还没测量出宽度就拖动把胶囊甩飞。
 */
class BottomBarDragMathTest {

    private val tab = 100f          // 一格宽 100px
    private val count = 5
    private val maxIndex = 4f

    // ── 落位 ──

    @Test
    fun `没拖动时落在原地`() {
        (0..4).forEach { i ->
            assertEquals(i, BottomBarDragMath.targetIndex(i.toFloat(), 0f, tab, count))
        }
    }

    @Test
    fun `过半格才换格 不到一半回原位`() {
        // 0.49 格不换、0.51 格换 —— 阈值必须是"最近的一格"，不是"动一点就换"
        assertEquals(0, BottomBarDragMath.targetIndex(0f, 49f, tab, count))
        assertEquals(1, BottomBarDragMath.targetIndex(0f, 51f, tab, count))
        assertEquals(0, BottomBarDragMath.targetIndex(0f, 49.9f, tab, count))
    }

    @Test
    fun `落位被夹在两端`() {
        // 即便位移算大了（浮点累积 / 边界竞争），也绝不能落到不存在的格子上
        assertEquals(0, BottomBarDragMath.targetIndex(0f, -999f, tab, count))
        assertEquals(4, BottomBarDragMath.targetIndex(4f, 999f, tab, count))
    }

    @Test
    fun `只有一格时永远落在第 0 格`() {
        assertEquals(0, BottomBarDragMath.targetIndex(0f, 500f, tab, 1))
    }

    @Test
    fun `宽度未知时不崩且回到当前格`() {
        // tabWidthPx = 0 是"还没测量出来"的真实状态（第一帧）
        assertEquals(2, BottomBarDragMath.targetIndex(2f, 40f, 0f, count))
        assertEquals(0, BottomBarDragMath.targetIndex(0f, 40f, 0f, count))
    }

    @Test
    fun `格子数为 0 不崩`() {
        assertEquals(0, BottomBarDragMath.targetIndex(0f, 0f, tab, 0))
    }

    // ── 跟手位移夹取 ──

    @Test
    fun `第 0 格不能往左拖出栏外`() {
        assertEquals(0f, BottomBarDragMath.clampOffset(-50f, 0f, tab, maxIndex))
    }

    @Test
    fun `最后一格不能往右拖出栏外`() {
        assertEquals(0f, BottomBarDragMath.clampOffset(50f, 4f, tab, maxIndex))
    }

    @Test
    fun `中间格子两端都留出剩余空间`() {
        // 在第 2 格：往左最多退 2 格、往右最多进 2 格
        assertEquals(-200f, BottomBarDragMath.clampOffset(-999f, 2f, tab, maxIndex))
        assertEquals(200f, BottomBarDragMath.clampOffset(999f, 2f, tab, maxIndex))
        // 区间内原样通过
        assertEquals(30f, BottomBarDragMath.clampOffset(30f, 2f, tab, maxIndex))
    }

    @Test
    fun `宽度未知时位移归零 而不是除零算出巨值`() {
        assertEquals(0f, BottomBarDragMath.clampOffset(120f, 2f, 0f, maxIndex))
    }
}
