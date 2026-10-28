package com.pocket.xiaoli.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimeSlotDefaultsTest {

    @Test
    fun `默认节次共 12 节且首尾符合口径`() {
        assertEquals(12, DefaultTimeSlots.size)
        assertEquals("08:00", DefaultTimeSlots.first().startTime)
        assertEquals("22:30", DefaultTimeSlots.last().endTime)
        // 下午第一节 14:00、晚上第一节 19:00
        assertEquals("14:00", DefaultTimeSlots[4].startTime)
        assertEquals("19:00", DefaultTimeSlots[8].startTime)
    }

    @Test
    fun `每节 45 分钟`() {
        for (spec in DefaultTimeSlots) {
            val start = toMinutes(spec.startTime)
            val end = toMinutes(spec.endTime)
            assertEquals(45, end - start, "第 ${spec.slot} 节时长异常：${spec.startTime}-${spec.endTime}")
        }
    }

    @Test
    fun `旧默认值能被判定为未改动`() {
        val legacy = (0 until LegacyTimeSlotCount).map { legacyTimeSlotAt(it) }
        assertTrue(matchesLegacyTimeSlots(legacy))
    }

    @Test
    fun `用户改过一节就不再算旧默认值`() {
        val touched = (0 until LegacyTimeSlotCount).map { legacyTimeSlotAt(it) }.toMutableList()
        touched[4] = touched[4].copy(startTime = "13:35")
        assertFalse(matchesLegacyTimeSlots(touched))
    }

    @Test
    fun `新默认值与旧默认值不同`() {
        val legacy = (0 until LegacyTimeSlotCount).map { legacyTimeSlotAt(it) }
        assertFalse(matchesLegacyTimeSlots(DefaultTimeSlots))
        assertEquals(DefaultTimeSlots.size, legacy.size)
    }

    private fun toMinutes(clock: String): Int {
        val parts = clock.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
