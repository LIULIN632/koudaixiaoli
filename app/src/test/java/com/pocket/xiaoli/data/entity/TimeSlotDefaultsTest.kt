package com.pocket.xiaoli.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 默认节次表与 v20→v21 迁移判据。
 *
 * ## 为什么钉这几条
 *
 * 节次时间是课表网格的尺子，改一次要同时动三处（填充、建库、日历导出兜底），
 * 而本项目没引 room-testing，迁移体在 JVM 里跑不起来 —— 所以按既有规矩
 * 只测抽出来的纯函数 [matchesLegacyTimeSlots]，SQL 那半留在真机上验。
 *
 * 迁移判据最重要的不是"旧默认能命中"，而是**用户动过就不命中**：
 * 覆盖用户数据去追一个新默认值，比时间不对更糟。
 */
class TimeSlotDefaultsTest {

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    private fun TimeSlotEntity.minutes(): Long = ChronoUnit.MINUTES.between(
        LocalTime.parse(startTime, hhmm),
        LocalTime.parse(endTime, hhmm),
    )

    @Test
    fun `十二节课每节四十五分钟`() {
        assertEquals(12, defaultTimeSlots.size)
        assertEquals((1..12).toList(), defaultTimeSlots.map { it.slot })
        defaultTimeSlots.forEach { slot ->
            assertEquals("${slot.slot} 节时长", 45L, slot.minutes())
        }
    }

    @Test
    fun `三段起点是上午八点下午两点晚上七点`() {
        assertEquals("08:00", defaultTimeSlots.first { it.slot == 1 }.startTime)
        assertEquals("14:00", defaultTimeSlots.first { it.slot == 5 }.startTime)
        assertEquals("19:00", defaultTimeSlots.first { it.slot == 9 }.startTime)
    }

    @Test
    fun `节次按时间递增且不留负课间`() {
        defaultTimeSlots.zip(defaultTimeSlots.drop(1)).forEach { (previous, next) ->
            assertTrue(
                "第 ${next.slot} 节开始早于上一节结束",
                LocalTime.parse(next.startTime, hhmm) >= LocalTime.parse(previous.endTime, hhmm),
            )
        }
    }

    @Test
    fun `旧默认表命中迁移而新默认不命中`() {
        assertTrue(matchesLegacyTimeSlots(legacyTimeSlotsBeforeV21))
        // 不命中才会在升级时改写成新表；否则迁移等于没跑
        assertFalse(matchesLegacyTimeSlots(defaultTimeSlots))
    }

    @Test
    fun `用户动过任何一节就整表不动`() {
        val touched = legacyTimeSlotsBeforeV21.toMutableList().also {
            it[4] = it[4].copy(startTime = "13:00")
        }
        assertFalse(matchesLegacyTimeSlots(touched))
        assertFalse(matchesLegacyTimeSlots(legacyTimeSlotsBeforeV21.dropLast(1)))
        assertFalse(
            matchesLegacyTimeSlots(
                legacyTimeSlotsBeforeV21 + TimeSlotEntity(13, "22:40", "23:25")
            )
        )
    }
}
