package com.pocket.xiaoli.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 30 天轮换的时间窗。
 *
 * 这两条断言守的是**口径**：`bucketOf` 一旦和文档里写的
 * `epochDay(Asia/Shanghai) / 30` 不一致，面板上的 MAU 就悄悄变成了另一个指标，
 * 而这种错在界面上完全看不出来。所以它是纯函数、单独测。
 *
 * ⚠️ 别用"随手挑一个时间戳再加 29 天"来写这类断言：桶边界是全局对齐的
 * （边界落在 `k * 30 天 - 8 小时`，不是整天），随手挑的时间大多不落在桶头，
 * 加 29 天就跨到下一桶去了。
 * ⚠️ 也别用"一天天往回挪，直到桶号变了"来找桶头：边界带 8 小时偏移，
 * 从整天的时间戳出发永远挪不到边界上，会死循环（第一版就这么挂的，
 * 表现是测试 JVM 打满 CPU 二十分钟不出结果）。按公式直接算。
 */
class TelemetryWindowTest {

    private val day = 24L * 3600 * 1000

    /** 东八区偏移，和 `TelemetryStore` 里的常量是同一个意思 */
    private val tz = 8L * 3600 * 1000

    /** 第 `TelemetryStore.bucketOf(ms)` 个桶的第一毫秒 */
    private fun bucketStart(ms: Long): Long =
        TelemetryStore.bucketOf(ms) * 30 * day - tz

    @Test
    fun `桶头正好落在桶的起点`() {
        val start = bucketStart(1_782_000_000_000L)

        assertEquals(start, TelemetryStore.bucketOf(start) * 30 * day - tz)
        // 前一毫秒已经在上一桶
        assertEquals(TelemetryStore.bucketOf(start) - 1, TelemetryStore.bucketOf(start - 1))
    }

    @Test
    fun `同一桶内不换号`() {
        val start = bucketStart(1_782_000_000_000L)

        assertEquals(TelemetryStore.bucketOf(start), TelemetryStore.bucketOf(start + 29 * day))
    }

    @Test
    fun `跨 30 天换桶`() {
        val start = bucketStart(1_782_000_000_000L)

        assertNotEquals(TelemetryStore.bucketOf(start), TelemetryStore.bucketOf(start + 30 * day))
        // 换桶是整段平移，不是每 30 天漂一格
        assertEquals(TelemetryStore.bucketOf(start) + 1, TelemetryStore.bucketOf(start + 30 * day))
    }

    @Test
    fun `按东八区切日，不按 UTC`() {
        // 东八区零点 = 某个 UTC 日界 - 8 小时。
        // 若按 UTC 算，跨零点前后两小时会被算到前一天，日活会集体偏一天。
        val beijingMidnight = 20_625L * day - tz

        assertEquals(
            TelemetryStore.today(beijingMidnight),
            TelemetryStore.today(beijingMidnight - 1) + 1,
        )
        assertEquals(beijingMidnight, TelemetryStore.today(beijingMidnight) * day - tz)
    }
}
