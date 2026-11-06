package com.pocket.xiaoli.core.bus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 公交到站判据的测试。
 *
 * 文案样本全部来自 2026-11-04 本校站（烟台理工学院）CMD=105 的**真实响应**，
 * 不是照着接口文档想象的：上游给的是一行中文文案 + 一个站数 + 两个标志位，
 * 判据错了用户就会按错的时间出门，所以每条形态都要钉住。
 */
class BusArrivalRulesCoreTest {

    private fun arrivalOf(text: String, num: Int, hasReal: Boolean = true, runState: Int = 0) =
        BusArrivalRules.parse(text, num, hasReal, runState)

    // ── 解析：上游实测到的四种形态 ──

    @Test
    fun `已到站 归一成 Arrived`() {
        val a = arrivalOf("已到站 [到达 烟台理工学院]", 0)
        assertEquals(BusArrival.Arrived, a)
        assertEquals("已到站", BusArrivalRules.chipText(a))
        assertEquals(BusArrivalTone.IMMEDIATE, BusArrivalRules.toneOf(a))
    }

    @Test
    fun `N站 归一成 StopsAway 并丢掉解释不了的括号文案`() {
        val a = arrivalOf("2站 [开往 蓝色智谷]", 2)
        // 括号里那句与卡片标题的「开往 X」不是一回事，含义没核实就不显示（见 StopsAway 注释）
        assertEquals(BusArrival.StopsAway(2), a)
        assertEquals("2 站", BusArrivalRules.chipText(a))
    }

    @Test
    fun `等待发车 的 nearnum 100 是哨兵不是还有100站`() {
        val a = arrivalOf("等待发车", 100)
        assertEquals(BusArrival.WaitingDeparture, a)
        assertEquals("等待发车", BusArrivalRules.chipText(a))
        assertEquals(BusArrivalTone.MUTED, BusArrivalRules.toneOf(a))
        // 排在能等的班次后面，但仍在「没实时」前面
        assertEquals(BusArrivalRules.STOP_COUNT_SENTINEL, BusArrivalRules.sortRank(a))
    }

    @Test
    fun `没开实时时哪怕上游带了站数也不当实时报`() {
        // 这是诚实性主闸：hasReal=0 时 neartext 可能是上一班留下的陈旧文案
        val a = arrivalOf("3站 [开往 X]", 3, hasReal = false)
        assertEquals(BusArrival.NoRealtime, a)
        assertEquals("未开通实时", BusArrivalRules.chipText(a))
        assertFalse(BusArrivalRules.isImminent(a))
    }

    @Test
    fun `停运优先于文案`() {
        assertEquals(BusArrival.Suspended, arrivalOf("2站 [开往 X]", 2, runState = 1))
        assertEquals("停运", BusArrivalRules.chipText(BusArrival.Suspended))
    }

    @Test
    fun `有实时但没文案时给 Unknown 而不是零站`() {
        val a = arrivalOf("", 0)
        assertEquals(BusArrival.Unknown, a)
        assertEquals("暂无实时", BusArrivalRules.chipText(a))
    }

    @Test
    fun `读不懂的写法原样透传不猜意思`() {
        val a = arrivalOf("未到站", 0)
        assertEquals(BusArrival.Other("未到站"), a)
        assertEquals("未到站", BusArrivalRules.chipText(a))
        assertEquals(BusArrivalTone.MUTED, BusArrivalRules.toneOf(a))
    }

    @Test
    fun `文案读不出站数时才兜底采信 nearnum`() {
        val a = arrivalOf("预计 5 分钟", 5)
        assertEquals(BusArrival.StopsAway(5), a)
        // 哨兵值不许被兜底路径当成「还有 100 站」，读不懂就原样显示
        assertEquals(BusArrival.Other("预计"), arrivalOf("预计", 100))
    }

    // ── 档位与阈值 ──

    @Test
    fun `两站以内算临近`() {
        assertTrue(BusArrivalRules.isImminent(arrivalOf("2站", 2)))
        assertTrue(BusArrivalRules.isImminent(BusArrival.Arrived))
        assertFalse(BusArrivalRules.isImminent(arrivalOf("3站", 3)))
        assertEquals(2, BusArrivalRules.IMMINENT_STOPS)
    }

    @Test
    fun `排序权重从能等到不能等`() {
        val ranks = listOf(
            BusArrival.Arrived,
            BusArrival.StopsAway(2),
            BusArrival.StopsAway(8),
            BusArrival.WaitingDeparture,
            BusArrival.Unknown,
            BusArrival.NoRealtime,
            BusArrival.Suspended,
        ).map(BusArrivalRules::sortRank)
        assertEquals(ranks.sorted(), ranks, "到站档位必须严格由近及远")
        assertTrue(BusArrivalRules.sortRank(BusArrival.Arrived) == 0)
    }

    // ── 文案 ──

    @Test
    fun `方向文案优先给终点`() {
        assertEquals("开往 火车站", BusArrivalRules.directionText("山东商务职业学院南门", "火车站"))
        assertEquals("始发 火车站", BusArrivalRules.directionText("火车站", ""))
        assertEquals("", BusArrivalRules.directionText("", ""))
    }

    @Test
    fun `距离文案把直线距离说清楚`() {
        assertEquals("205 米", BusArrivalRules.distanceText(205))
        assertEquals("999 米", BusArrivalRules.distanceText(999))
        assertEquals("1.0 公里", BusArrivalRules.distanceText(1000))
        assertEquals("1.1 公里", BusArrivalRules.distanceText(1055))
        // 上游没给距离（-1）时返回空串，界面那一行就不画，而不是写个 0 米
        assertEquals("", BusArrivalRules.distanceText(-1))
    }

    @Test
    fun `线路号按数值比而不是按字典序`() {
        assertTrue(BusArrivalRules.compareLineNames("2路", "17路") < 0)
        assertTrue(BusArrivalRules.compareLineNames("17路", "105路") < 0)
        // 站牌上「快51路」是独立系列，没有数字前缀就按字符串排在有编号的后面
        assertTrue(BusArrivalRules.compareLineNames("51路", "快51路") < 0)
        assertEquals(0, BusArrivalRules.compareLineNames("17路", "17路"))
    }
}
