package com.pocket.xiaoli.data.mcd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 麦当劳榜单的数据口径与凑蛋白算法。
 *
 * 数据来自 `hework1110/mcd-protein-index` 的快照（MIT），这里钉三件事：
 * 1. 两套菜单都解析出来、按元/克蛋白升序；
 * 2. 榜首与项目 README 的结论一致（早餐 0.67、正餐 0.71）；
 * 3. 「预算 40 元吃够 40 克蛋白」给出 2 份脆汁鸡（¥30 / 42 g），与官方算价示例一致。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McProteinModelsTest {

    private lateinit var catalog: McProteinCatalog

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.assets.open("mcd_protein.json").bufferedReader().use { it.readText() }
        catalog = requireNotNull(McProteinCatalogParser.parse(raw))
    }

    @Test
    fun `两套菜单数量与榜首`() {
        val breakfast = catalog.ranking(McProteinSlot.BREAKFAST)
        val dinner = catalog.ranking(McProteinSlot.DINNER)
        assertEquals(25, breakfast.size)
        assertEquals(28, dinner.size)

        assertEquals("原味板烧鸡腿炒双蛋堡", breakfast.first().name)
        assertEquals(18.0, breakfast.first().priceYuan, 1e-9)
        assertEquals(27.0, breakfast.first().proteinGram, 1e-9)
        assertEquals("麦麦脆汁鸡1块(鸡腿或带骨里脊)", dinner.first().name)
        assertEquals(15.0, dinner.first().priceYuan, 1e-9)
        assertEquals(21.0, dinner.first().proteinGram, 1e-9)

        // 升序：后一项的元/克蛋白不可能比前一项更小
        dinner.zipWithNext().forEach { (a, b) ->
            assertTrue("${b.name} 应排在 ${a.name} 之后", b.yuanPerGram >= a.yuanPerGram - 1e-9)
        }
    }

    @Test
    fun `预算四十吃够四十克蛋白`() {
        val plan = McProteinPlanner.cheapest(catalog.ranking(McProteinSlot.DINNER), 40.0, 40.0)
        assertNotNull(plan)
        plan!!
        assertEquals(30.0, plan.totalPriceYuan, 1e-6)
        assertEquals(42.0, plan.totalProteinGram, 1e-6)
        assertEquals(1, plan.parts.size)
        assertEquals(2, plan.parts.first().count)
        assertTrue(plan.withinBudget)
        assertEquals(
            "2 × 麦麦脆汁鸡1块(鸡腿或带骨里脊)\n¥30.00 · 42 g 蛋白",
            McProteinPlanner.describe(plan),
        )
    }

    @Test
    fun `早餐凑四十克更便宜`() {
        val plan = McProteinPlanner.cheapest(catalog.ranking(McProteinSlot.BREAKFAST), 40.0, 40.0)!!
        assertEquals(28.0, plan.totalPriceYuan, 1e-6)
        assertEquals(41.0, plan.totalProteinGram, 1e-6)
        assertTrue(plan.withinBudget)
    }

    @Test
    fun `预算不够时如实标出超出多少`() {
        val plan = McProteinPlanner.cheapest(catalog.ranking(McProteinSlot.DINNER), 40.0, 20.0)!!
        assertTrue(!plan.withinBudget)
        assertEquals(plan.totalPriceYuan - 20.0, plan.overBudgetYuan, 1e-6)
    }

    @Test
    fun `十点半是早餐正餐的分界`() {
        assertEquals(McProteinSlot.BREAKFAST, McProteinSlot.forClock(10, 29))
        assertEquals(McProteinSlot.DINNER, McProteinSlot.forClock(10, 30))
        assertEquals(McProteinSlot.BREAKFAST, McProteinSlot.forClock(0, 0))
    }

    @Test
    fun `海报那句对比由快照现算`() {
        // 这两组数字原先是**手写进海报文案**的（"早餐 ¥18，正餐 ¥23"）。
        // 现在海报调 cheapestPrice，快照一换文案自己跟着变；这条用例把当前快照的结论钉住，
        // 下次更新数据时如果价格变了，这里会红 —— 那正是提醒你去核对，而不是让文案变假话。
        assertEquals(
            18.0,
            McProteinPlanner.cheapestPrice(catalog.ranking(McProteinSlot.BREAKFAST), 27.0)!!,
            1e-6,
        )
        assertEquals(
            23.0,
            McProteinPlanner.cheapestPrice(catalog.ranking(McProteinSlot.DINNER), 27.0)!!,
            1e-6,
        )
    }

    @Test
    fun `目标为零或没有单品时不给价格`() {
        assertEquals(null, McProteinPlanner.cheapestPrice(catalog.ranking(McProteinSlot.DINNER), 0.0))
        assertEquals(null, McProteinPlanner.cheapestPrice(emptyList(), 27.0))
    }
}
