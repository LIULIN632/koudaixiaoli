package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 学期工具边界用例：开学日期解析 + 周次计算（含边界 clamp）。
 */
class SemesterUtilsTest {

    // ── parseSemesterStart ──

    @Test
    fun parseSemesterStart_validDate() {
        assertEquals(LocalDate.of(2026, 9, 1), parseSemesterStart("2026-09-01"))
        assertEquals(LocalDate.of(2026, 2, 28), parseSemesterStart("2026-2-28"))
    }

    @Test
    fun parseSemesterStart_invalidInputs_returnsNull() {
        assertNull(parseSemesterStart(""))
        assertNull(parseSemesterStart("2026-09"))           // 缺日期
        assertNull(parseSemesterStart("2026-13-01"))        // 非法月份
        assertNull(parseSemesterStart("abc-def-ghi"))
        assertNull(parseSemesterStart("2026/09/01"))        // 分隔符不对
    }

    // ── currentWeekFrom ──

    private val start = LocalDate.of(2026, 9, 1) // 周二

    @Test
    fun currentWeek_firstWeek() {
        assertEquals(1, currentWeekFrom(start, 20, start))
        assertEquals(1, currentWeekFrom(start, 20, start.plusDays(6)))  // 不足 7 天按第 1 周
    }

    @Test
    fun currentWeek_secondWeek() {
        assertEquals(2, currentWeekFrom(start, 20, start.plusDays(7)))
        assertEquals(2, currentWeekFrom(start, 20, start.plusDays(13)))
    }

    @Test
    fun currentWeek_beforeStart_clampedTo1() {
        assertEquals(1, currentWeekFrom(start, 20, start.minusDays(1)))
        assertEquals(1, currentWeekFrom(start, 20, start.minusDays(30)))
    }

    @Test
    fun currentWeek_beyondTotal_clamped() {
        assertEquals(20, currentWeekFrom(start, 20, start.plusDays(7 * 30L))) // 第 31 周 → 上限 20
        assertEquals(1, currentWeekFrom(start, 1, start.plusDays(100)))
    }

    @Test
    fun currentWeek_midSemester() {
        assertEquals(5, currentWeekFrom(start, 20, start.plusDays(28)))
        assertEquals(5, currentWeekFrom(start, 20, start.plusDays(34)))
        assertEquals(6, currentWeekFrom(start, 20, start.plusDays(35)))
    }
}
