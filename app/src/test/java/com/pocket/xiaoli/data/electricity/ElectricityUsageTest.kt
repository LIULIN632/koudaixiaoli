package com.pocket.xiaoli.data.electricity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电费用量统计的纯逻辑测试。
 *
 * ## 为什么这块必须被逐条钉住
 *
 * 上游只给「实时余额」一个数，所以图上的**日均、还能用几天、距提醒线几天**全是我自己推算的。
 * 推算错不会崩、不会报错，只会**显示一个看起来很可信的错数字**，
 * 而用户是照这个数字决定"要不要现在去充值"的 —— 错方向的代价是宿舍断电。
 *
 * 于是每条规则都单独有一条测试，尤其是这几条容易想当然的：
 * 充值不能当成用电、余额整月不动不能算出"还能用无限天"、
 * 只有一天记录时不许画出线、预测要**向下取整**。
 */
class ElectricityUsageTest {

    private val day0 = 20_000L

    private fun samples(vararg pairs: Pair<Long, Double>) = pairs.map { BalanceSample(day0 + it.first, it.second) }

    // ── 采样 → 统计 ──────────────────────────────────────────────

    @Test
    fun `没有采样时什么都不成立`() {
        val stats = usageStatsOf(emptyList(), todayDay = day0)
        assertTrue(stats.points.isEmpty())
        assertTrue(!stats.plottable)
        assertNull(stats.perDayYuan)
        assertNotNull(usageNotice(stats))
        assertTrue(usageNotice(stats)!!.contains("本机还没有"))
    }

    @Test
    fun `同一天多条只留最后一条`() {
        val stats = usageStatsOf(
            samples(0L to 30.0, 0L to 28.0, 0L to 26.5),
            todayDay = day0,
        )
        assertEquals(1, stats.points.size)
        assertEquals(26.5, stats.points.single().balance, 0.0001)
        assertTrue(!stats.plottable)
        assertTrue(usageNotice(stats)!!.contains("再查一天"))
    }

    @Test
    fun `每天掉十块则日均十块还能用八天`() {
        val stats = usageStatsOf(
            samples(0L to 100.0, 1L to 90.0, 2L to 80.0),
            todayDay = day0 + 2,
        )
        assertTrue(stats.plottable)
        assertNull(usageNotice(stats))
        assertEquals(3, stats.observedDays)
        assertEquals(3, stats.spanDays)
        assertEquals(10.0, stats.perDayYuan!!, 0.0001)
        // 80 / 10 = 8 天
        assertEquals(8, stats.daysUntilEmpty)
        assertTrue(stats.rechargeDays.isEmpty())
    }

    /**
     * 充值不是用电。
     *
     * 12 号那天余额从 90 涨回 120（充了 30），这一段既不能算成"用了 -30 元"，
     * 也不能把它摊进日均把整体压低。掉钱的那一段（120 → 110）才作数。
     */
    @Test
    fun `充值那一段不进日均且被标出来`() {
        val stats = usageStatsOf(
            samples(0L to 100.0, 1L to 90.0, 2L to 120.0, 3L to 110.0),
            todayDay = day0 + 3,
        )
        assertEquals(listOf(day0 + 2), stats.rechargeDays)
        // 只有 2→3 这一段是真实消耗：10 元 / 1 天
        assertEquals(10.0, stats.perDayYuan!!, 0.0001)
        assertEquals(11, stats.daysUntilEmpty!!)   // 110 / 10
    }

    @Test
    fun `小数抖动不算充值`() {
        val stats = usageStatsOf(
            samples(0L to 50.0, 1L to 50.004),
            todayDay = day0 + 1,
        )
        assertTrue("0.4 分钱的差值不该被当成充了钱", stats.rechargeDays.isEmpty())
    }

    /** 余额纹丝不动（放假、没人住）：日均是 0，而不是"算不出"，也**绝不许**是无限天。 */
    @Test
    fun `用量为零时报零而不是无限`() {
        val stats = usageStatsOf(
            samples(0L to 50.0, 1L to 50.0, 2L to 50.0),
            todayDay = day0 + 2,
        )
        assertEquals(0.0, stats.perDayYuan!!, 0.0001)
        assertNull("日均为 0 时不能给'还能用几天'，那是无限的另一种说法", stats.daysUntilEmpty)
        assertNull(usageNotice(stats))   // 平线是真实数据，图照画，只是不预测
    }

    /**
     * 跨天空档必须进分母。
     *
     * 放假两周没查、返校后查了两次掉 10 元 —— 如果按"有记录的天数"摊，
     * 日均会被虚报成 5 元/天，然后告诉你"还能用 2 天"，把你吓去充值。
     * 按真实间隔摊才是 0.7 元/天。
     */
    @Test
    fun `采样之间的空档按真实天数摊`() {
        val stats = usageStatsOf(
            samples(0L to 20.0, 14L to 10.0),
            todayDay = day0 + 14,
        )
        assertEquals(10.0 / 14.0, stats.perDayYuan!!, 0.0001)
        // 跨度是"首末相隔 14 天 = 占 15 个日历日"
        assertEquals(15, stats.spanDays)
    }

    @Test
    fun `预测向下取整不说满话`() {
        // 25 元、日均 7 元 → 3.57 天。说"约 4 天"会让第 4 天中途断电。
        val stats = usageStatsOf(
            samples(0L to 32.0, 1L to 25.0),
            todayDay = day0 + 1,
        )
        assertEquals(7.0, stats.perDayYuan!!, 0.0001)
        assertEquals(3, stats.daysUntilEmpty)
    }

    @Test
    fun `距提醒线已越界时给零天`() {
        val stats = usageStatsOf(
            samples(0L to 12.0, 1L to 8.0),
            todayDay = day0 + 1,
            lowBalanceThreshold = 10.0,
        )
        assertEquals(0, stats.daysUntilThreshold)
        assertTrue("余额 8 元、线在 10 元 → 确实已经越线", stats.belowLowBalance)
    }

    /**
     * **截图验收时抓到的那个自相矛盾**。
     *
     * 余额 14 元、提醒线 10 元、日均 6.57 元 —— 距越线只剩 0.6 天，向下取整是 0。
     * 第一版把"0 天"直接说成「已经低于提醒线」，于是同一屏上大字写着 14.00 元、
     * 下面一行说已经低于 10 元。两种 0 必须分开：越线是一种，不到一天是另一种。
     */
    @Test
    fun `还没越线但不到一天时不能说已经低于`() {
        val stats = usageStatsOf(
            samples(0L to 60.0, 1L to 52.0, 2L to 45.0, 3L to 40.0, 4L to 33.0, 5L to 27.0, 6L to 20.0, 7L to 14.0),
            todayDay = day0 + 7,
            lowBalanceThreshold = 10.0,
        )
        assertTrue(!stats.belowLowBalance)
        assertEquals(0, stats.daysUntilThreshold)
        val lines = usageStatLines(stats, threshold = 10.0).toMap()
        val distance = lines.getValue("距提醒线 10 元")
        assertEquals("不到 1 天", distance)
        assertTrue(!distance.contains("已经低于"))
    }

    @Test
    fun `天数说法的四种情况`() {
        assertEquals("约 5 天", daysPhrase(5))
        assertEquals("不到 1 天", daysPhrase(0))
        assertEquals("算不出（没有稳定用量）", daysPhrase(null))
        // 已经越线时优先说越线 —— 这时"不到 1 天"是在绕圈子
        assertEquals("已经低于提醒线", daysPhrase(0, alreadyBelow = true))
        assertEquals("已经低于提醒线", daysPhrase(null, alreadyBelow = true))
    }

    @Test
    fun `距提醒线还有几天`() {
        val stats = usageStatsOf(
            samples(0L to 50.0, 1L to 40.0),
            todayDay = day0 + 1,
            lowBalanceThreshold = 10.0,
        )
        // 40 元、日均 10、线在 10 → 还能掉 30 元 = 3 天
        assertEquals(3, stats.daysUntilThreshold)
    }

    @Test
    fun `超出窗口的旧记录不参与`() {
        val stats = usageStatsOf(
            samples(0L to 100.0, 60L to 20.0, 61L to 10.0),
            todayDay = day0 + 61,
            thresholdDays = USAGE_WINDOW_DAYS,
        )
        assertEquals("60 天前那条不该被拉进来算日均", listOf(20.0, 10.0), stats.points.map { it.balance })
        assertEquals(10.0, stats.perDayYuan!!, 0.0001)
    }

    @Test
    fun `窗口内一条都不剩时等于没有记录`() {
        val stats = usageStatsOf(samples(0L to 100.0, 1L to 90.0), todayDay = day0 + 100)
        assertTrue(stats.points.isEmpty())
        assertTrue(usageNotice(stats)!!.contains("本机还没有"))
    }

    // ── 画图坐标：三种"不该画"的情况 ────────────────────────────────

    @Test
    fun `画不出图时坐标是空而不是假线`() {
        assertNull(trendCoordinates(UsageStats()))
        assertNull(trendCoordinates(UsageStats(points = samples(0L to 10.0))))
        // 同一天两点：spanDays = 0 ⇒ 不画
        assertNull(
            trendCoordinates(
                UsageStats(points = listOf(BalanceSample(day0, 10.0), BalanceSample(day0, 5.0)), spanDays = 0),
            ),
        )
    }

    @Test
    fun `坐标铺满归一化区间且没有NaN`() {
        val stats = usageStatsOf(samples(0L to 100.0, 1L to 60.0, 2L to 20.0), todayDay = day0 + 2)
        val coords = trendCoordinates(stats, extraLine = 10.0)!!
        assertEquals(3, coords.size)
        assertEquals(0f, coords.first().first, 0.0001f)
        assertEquals(1f, coords.last().first, 0.0001f)
        coords.forEach { (x, y) ->
            assertTrue("x 必须落在 0..1", x in 0f..1f)
            assertTrue("y 必须落在 0..1（NaN 会在这里挂掉）", y.isFinite() && y in 0f..1f)
        }
        // 余额越高越靠上（y 越小）
        assertTrue(coords[0].second < coords[2].second)
    }

    /** 余额整月不动时，纵轴跨度为 0 —— 直接除会得到 Infinity，图会画到屏幕外。 */
    @Test
    fun `水平线也能画且提醒线仍看得见`() {
        val stats = usageStatsOf(samples(0L to 50.0, 1L to 50.0), todayDay = day0 + 1)
        val coords = trendCoordinates(stats, extraLine = 10.0)!!
        coords.forEach { (_, y) -> assertTrue(y.isFinite() && y in 0f..1f) }
        val lineY = trendLineY(10.0, stats)
        assertNotNull(lineY)
        assertTrue("提醒线在线 below 余额时应该更靠下", lineY!! > coords.first().second)
    }

    @Test
    fun `提醒线在数值上方时仍然落在图内`() {
        val stats = usageStatsOf(samples(0L to 5.0, 1L to 3.0), todayDay = day0 + 1)
        // 余额都低于 10 元线：范围必须把线撑进来，否则用户看不到"越线了"
        val lineY = trendLineY(10.0, stats)!!
        val topY = trendCoordinates(stats, extraLine = 10.0)!!.first().second
        assertTrue(lineY < topY)
        assertTrue(lineY in 0f..1f)
    }

    // ── 界面上的那几句话 ──────────────────────────────────────────

    @Test
    fun `算不出来的那两行不许写成零`() {
        val stats = usageStatsOf(samples(0L to 50.0, 1L to 50.0), todayDay = day0 + 1)
        val lines = usageStatLines(stats, threshold = 10.0).toMap()
        assertEquals("约 0.00 元 / 天", lines["日均花费"])
        // 日均 0 时"还能用几天"必须留白，写 0 天或无限天都是假话
        assertEquals("算不出（没有稳定用量）", lines["还能用"])
    }

    @Test
    fun `有充值时脚注要说清排除规则`() {
        val withRecharge = usageStatsOf(samples(0L to 10.0, 1L to 30.0), todayDay = day0 + 1)
        assertTrue(usageFootnote(withRecharge).contains("有 1 天余额上涨"))
        val clean = usageStatsOf(samples(0L to 30.0, 1L to 20.0), todayDay = day0 + 1)
        assertTrue(usageFootnote(clean).contains("不是后勤系统给的数据"))
        assertTrue(!usageFootnote(clean).contains("余额上涨"))
    }

    // ── 余额原文解析 ─────────────────────────────────────────────

    @Test
    fun `余额解析宁可不记也不猜`() {
        assertEquals(21.86, ElectricityUsageStore.parseBalance("21.86")!!, 0.0001)
        assertEquals(21.86, ElectricityUsageStore.parseBalance(" 21.86 元 ")!!, 0.0001)
        assertEquals(1234.5, ElectricityUsageStore.parseBalance("1,234.50 元")!!, 0.0001)
        assertEquals(-3.2, ElectricityUsageStore.parseBalance("-3.20")!!, 0.0001)
        assertNull(ElectricityUsageStore.parseBalance(null))
        assertNull(ElectricityUsageStore.parseBalance(""))
        assertNull(ElectricityUsageStore.parseBalance("暂无"))
        assertNull(ElectricityUsageStore.parseBalance("NaN"))
        assertNull(ElectricityUsageStore.parseBalance("Infinity"))
    }
}
