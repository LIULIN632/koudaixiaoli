package com.pocket.xiaoli.data.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 闲聊池：只测"什么时候这句能出现"，不测它好不好笑。
 *
 * 池子内容是文案，改文案不该让测试红（那会让人不敢改文案）；但**时段/月份/星期/事实
 * 这几条闸门**必须被钉住 —— 它们少了，凌晨三点会冒出一句"早八人起床啦"。
 */
class ChatterPoolTest {

    /** 造一个固定时刻，并把日期平移到目标星期（不依赖真实日历落在周几）。 */
    private fun at(hour: Int, day: DayOfWeek = DayOfWeek.WEDNESDAY, month: Int = 10) =
        LocalDateTime.of(2026, month, 7, hour, 0)
            .plusDays((day.value - LocalDateTime.of(2026, month, 7, hour, 0).dayOfWeek.value).toLong())

    @Test
    fun `早八句只在早上出现`() {
        val morning = ChatterPool.eligible(at(7), ChatterFacts()).map { it.id }
        val evening = ChatterPool.eligible(at(20), ChatterFacts()).map { it.id }
        assertTrue("early_rise" in morning)
        assertTrue("晚上不该劝人起床", "early_rise" !in evening)
    }

    @Test
    fun `考试月句只在考试月出现`() {
        val december = ChatterPool.eligible(at(15, month = 12), ChatterFacts()).map { it.id }
        val october = ChatterPool.eligible(at(15, month = 10), ChatterFacts()).map { it.id }
        assertTrue("exam_month" in december)
        assertTrue("exam_month" !in october)
    }

    @Test
    fun `周末句只在周末出现`() {
        val saturday = ChatterPool.eligible(at(15, day = DayOfWeek.SATURDAY), ChatterFacts()).map { it.id }
        val monday = ChatterPool.eligible(at(15, day = DayOfWeek.MONDAY), ChatterFacts()).map { it.id }
        assertTrue("weekend_sat" in saturday)
        assertTrue("weekend_sat" !in monday)
    }

    @Test
    fun `依赖课表事实的句子在不知道事实时不出现`() {
        val without = ChatterPool.eligible(at(19), ChatterFacts()).map { it.id }
        assertTrue("eve_done" !in without)

        val with = ChatterPool.eligible(at(19), ChatterFacts(todayCourseCount = 3)).map { it.id }
        assertTrue("知道今天有几节课时才该说那句话", "eve_done" in with)
    }

    @Test
    fun `放假句只在真的放假时出现`() {
        val normal = ChatterPool.eligible(at(15), ChatterFacts()).map { it.id }
        assertTrue(normal.none { it.startsWith("holiday_") })

        val holiday = ChatterPool.eligible(at(15), ChatterFacts(todayHoliday = "中秋")).map { it.id }
        assertTrue("holiday_中秋" in holiday)
    }

    @Test
    fun `情景句用的是真实数字`() {
        val heavy = ChatterPool.eligible(at(15), ChatterFacts(todayCourseCount = 6))
            .first { it.id == "heavy_day" }
        assertTrue(heavy.text.contains("6"))

        val none = ChatterPool.eligible(at(15), ChatterFacts(todayCourseCount = 0)).map { it.id }
        assertTrue("no_class_today" in none)
    }

    @Test
    fun `抽句子会避开最近说过的`() {
        val now = at(15)
        val all = ChatterPool.eligible(now, ChatterFacts()).map { it.id }
        val recent = all.dropLast(1)
        repeat(20) { seed ->
            val pick = ChatterPool.pick(now, recent = recent, facts = ChatterFacts(), random = Random(seed))
            assertEquals("只剩一条没说过，就该抽到它", all.last(), pick?.id)
        }
    }

    @Test
    fun `候选被最近记录清空时仍然出句`() {
        // 池子小的时候这是真会发生的：宁可说一句刚说过的，也不要因为它哑掉
        val now = at(15)
        val all = ChatterPool.eligible(now, ChatterFacts()).map { it.id }
        val pick = ChatterPool.pick(now, recent = all, facts = ChatterFacts(), random = Random(1))
        assertTrue(pick != null && pick.id in all)
    }

    @Test
    fun `池子里没有重复 id`() {
        // id 重复会让"最近说过"的去重失效，而且 `distinct` 会静默吃掉一条
        val ids = ChatterPool.lines.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `每句话都不超过两行能放下的长度`() {
        // 气泡最多两行：句子太长会被裁，等于说了半句
        ChatterPool.lines.forEach { line ->
            assertTrue("「${line.text}」太长", line.text.length <= 20)
        }
    }
}
