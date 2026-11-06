package com.pocket.xiaoli.core.calendar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCoverageCoreTest {

    @Test
    fun `单天事件只覆盖那一天`() {
        assertTrue(eventCoversDay(startEpochDay = 100, endEpochDay = 100, dayEpochDay = 100))
        assertFalse(eventCoversDay(startEpochDay = 100, endEpochDay = 100, dayEpochDay = 99))
        assertFalse(eventCoversDay(startEpochDay = 100, endEpochDay = 100, dayEpochDay = 101))
    }

    @Test
    fun `多天事件首尾都算`() {
        assertTrue(eventCoversDay(10, 20, 10), "首日算")
        assertTrue(eventCoversDay(10, 20, 20), "末日算")
        assertTrue(eventCoversDay(10, 20, 15), "区间内算")
        assertFalse(eventCoversDay(10, 20, 9))
        assertFalse(eventCoversDay(10, 20, 21))
    }

    @Test
    fun `结束日缺省时按单天处理`() {
        assertTrue(eventCoversDay(7, null, 7))
        assertFalse(eventCoversDay(7, null, 8))
    }

    @Test
    fun `开始晚于结束的脏数据不会意外命中`() {
        assertFalse(eventCoversDay(20, 10, 15))
        assertFalse(eventCoversDay(20, 10, 10))
        assertFalse(eventCoversDay(20, 10, 20))
    }
}
