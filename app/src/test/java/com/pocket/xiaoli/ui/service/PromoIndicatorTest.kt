package com.pocket.xiaoli.ui.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轮播指示条的几何（纯函数）。
 *
 * 为什么单独钉：这一段只有"宽度随拖动连续变化"一个职责，但它有两个容易写错的边界 ——
 * ① 拖动中的 `position` 是小数、还会因回弹短暂越界（负值 / 大于末页），不夹紧就会画出负数宽度；
 * ② 页数变化（服务端改 JSON）时条数必须跟着变，不能残留上一次的宽度表。
 * 单看截图看不出这两种情况（静止时永远是"一条长、其余短"）。
 */
class PromoIndicatorTest {

    private val base = PROMO_INDICATOR_BASE_DP
    private val active = PROMO_INDICATOR_ACTIVE_DP

    @Test
    fun `静止时当前页拉长其余最短`() {
        val widths = promoIndicatorWidths(pageCount = 3, position = 1f)
        assertEquals(listOf(base, active, base), widths)
    }

    @Test
    fun `拖到两页正中间时两条等宽`() {
        val widths = promoIndicatorWidths(pageCount = 3, position = 0.5f)
        assertEquals(base + (active - base) * 0.5f, widths[0], 0.001f)
        assertEquals(widths[0], widths[1], 0.001f)
        assertEquals(base, widths[2], 0.001f)
    }

    @Test
    fun `宽度随拖动连续变化`() {
        // position 0.25：第 0 页距离 0.25（宽 20）、第 1 页距离 0.75（宽 12）
        val widths = promoIndicatorWidths(pageCount = 2, position = 0.25f)
        assertEquals(20f, widths[0], 0.001f)
        assertEquals(12f, widths[1], 0.001f)
    }

    @Test
    fun `位置越界被夹紧而不是画出负宽度`() {
        // 回弹时 pager 的 offsetFraction 可能短暂为负 / 超过末页
        assertEquals(
            promoIndicatorWidths(pageCount = 3, position = 0f),
            promoIndicatorWidths(pageCount = 3, position = -0.8f),
        )
        assertEquals(
            promoIndicatorWidths(pageCount = 3, position = 2f),
            promoIndicatorWidths(pageCount = 3, position = 2.7f),
        )
        promoIndicatorWidths(pageCount = 3, position = -5f).forEach {
            assertTrue("宽度不能小于基础宽度: $it", it >= base - 0.001f)
        }
    }

    @Test
    fun `页数变化时条数跟着变`() {
        assertEquals(1, promoIndicatorWidths(pageCount = 1, position = 0f).size)
        assertEquals(5, promoIndicatorWidths(pageCount = 5, position = 3f).size)
        assertEquals(listOf(active), promoIndicatorWidths(pageCount = 1, position = 0f))
    }

    @Test
    fun `没有页面时返回空表`() {
        assertTrue(promoIndicatorWidths(pageCount = 0, position = 0f).isEmpty())
        assertTrue(promoIndicatorWidths(pageCount = -2, position = 0f).isEmpty())
    }
}
