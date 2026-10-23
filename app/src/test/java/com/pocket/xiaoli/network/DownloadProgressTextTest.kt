package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DownloadProgress] / [DownloadProgressText] / [DownloadSpeedMeter] —— 下载速度与剩余大小的纯函数层。
 *
 * 守三条红线：
 * 1. **total 未知不许编**：percent 为 null，文案里不许出现 `0%` / `null` / `NaN`；
 * 2. **停滞不许显示 `0 B/s`**：速度算不出来时退化为「正在重连…」；
 * 3. **测速用滑动窗口**：不用「总字节 / 已用时间」，起步那一秒不给荒唐低速。
 */
class DownloadProgressTextTest {

    private val mb = 1024L * 1024L

    // --- DownloadProgress 派生 ------------------------------------------------

    @Test
    fun `total 大于 0 时 percent 与 remaining 都算得出`() {
        val p = DownloadProgress(bytes = 13 * mb, total = 26 * mb, bytesPerSec = 2 * mb)
        assertEquals(50, p.percent)
        assertEquals(13 * mb, p.remaining)
    }

    @Test
    fun `total 为 0 或负数时 percent 与 remaining 都是 null`() {
        for (total in longArrayOf(0L, -1L)) {
            val p = DownloadProgress(bytes = 5 * mb, total = total, bytesPerSec = mb)
            assertNull("total=$total 不该给出百分比", p.percent)
            assertNull("total=$total 不该给出剩余量", p.remaining)
        }
    }

    @Test
    fun `bytes 等于 total 时百分百计满且剩余归零`() {
        val p = DownloadProgress(bytes = 26 * mb, total = 26 * mb, bytesPerSec = 4 * mb)
        assertEquals(100, p.percent)
        assertEquals(0L, p.remaining)
    }

    @Test
    fun `竞态下 bytes 超总长时 percent 封顶 100 且剩余不为负`() {
        val p = DownloadProgress(bytes = 30 * mb, total = 26 * mb, bytesPerSec = 4 * mb)
        assertEquals(100, p.percent)
        assertEquals(0L, p.remaining)
    }

    // --- 文案格式化 -----------------------------------------------------------

    @Test
    fun `一行文案是 百分比 速度 还剩 三段`() {
        val p = DownloadProgress(bytes = 11 * mb, total = 26 * mb, bytesPerSec = 1_887_437L)
        assertEquals("42% · 1.8 MB/s · 还剩 15.0 MB", DownloadProgressText.label(p))
    }

    @Test
    fun `速度为零时不显示 0 字节每秒 而是正在重连`() {
        val label = DownloadProgressText.label(DownloadProgress(8 * mb, 26 * mb, 0L))
        assertTrue(label, label.contains("正在重连…"))
        assertFalse(label, label.contains("0 B/s"))
    }

    @Test
    fun `起步一秒速度还没算出来时说的是正在准备`() {
        val label = DownloadProgressText.label(DownloadProgress(64 * 1024, 26 * mb, 0L))
        assertTrue(label, label.contains("正在准备…"))
        assertFalse(label, label.contains("0 B/s"))
    }

    @Test
    fun `总量未知只显示已下载大小与正在下载`() {
        val label = DownloadProgressText.label(DownloadProgress(3 * mb + 2048, -1L, 2 * mb))
        assertTrue(label, label.contains("已下载"))
        assertTrue(label, label.contains("正在下载"))
        assertFalse(label, label.contains("%"))
        assertFalse(label, label.contains("还剩"))
        assertFalse(label, label.contains("null"))
        assertFalse(label, label.contains("NaN"))
    }

    @Test
    fun `下完时省掉还剩半句`() {
        val label = DownloadProgressText.label(DownloadProgress(26 * mb, 26 * mb, 3 * mb))
        assertEquals("100% · 3.0 MB/s", label)
        assertFalse(label, label.contains("还剩"))
    }

    @Test
    fun `速度格式化覆盖 B KB MB GB 四档`() {
        assertEquals("0 B/s", DownloadProgressText.speed(0L))
        assertEquals("999 B/s", DownloadProgressText.speed(999L))
        assertEquals("500 KB/s", DownloadProgressText.speed(512_000L))
        assertEquals("1.8 MB/s", DownloadProgressText.speed(1_887_437L))
        assertEquals("1.5 GB/s", DownloadProgressText.speed(3L * 1024 * 1024 * 1024 / 2))
    }

    @Test
    fun `大小格式化：剩余不足 1 MB 走 KB 档`() {
        assertEquals("500 KB", DownloadProgressText.size(512_000L))
        assertEquals("9 B", DownloadProgressText.size(9L))
        assertEquals("12.4 MB", DownloadProgressText.size(13_002_342L))
        assertEquals("2.5 GB", DownloadProgressText.size(2_684_354_560L))
    }

    @Test
    fun `剩余小于 1 MB 的完整一行文案`() {
        val p = DownloadProgress(bytes = 26 * mb - 700 * 1024, total = 26 * mb, bytesPerSec = 2 * mb)
        val label = DownloadProgressText.label(p)
        assertTrue(label, label.endsWith("· 还剩 700 KB"))
    }

    @Test
    fun `负数字节按 0 处理不显示负号`() {
        assertEquals("0 B", DownloadProgressText.size(-123L))
    }

    // --- 滑动窗口测速 ---------------------------------------------------------

    @Test
    fun `首个采样点窗口没拉开时给 0`() {
        var now = 10_000L
        val meter = DownloadSpeedMeter(windowMillis = 1_000L, clock = { now })
        assertEquals(0L, meter.sample(0L))
    }

    @Test
    fun `一满秒给的是这一秒内的字节差`() {
        var now = 10_000L
        val meter = DownloadSpeedMeter(windowMillis = 1_000L, clock = { now })
        meter.sample(0L)
        now = 11_000L
        assertEquals(2_000_000L, meter.sample(2_000_000L))
    }

    @Test
    fun `超过窗口的老采样点会被丢掉`() {
        var now = 10_000L
        val meter = DownloadSpeedMeter(windowMillis = 1_000L, clock = { now })
        meter.sample(0L)                 // t=10.0s
        now = 10_500L; meter.sample(500_000L)
        now = 11_500L                    // 10.0s 那个点已经出窗，速度按 10.5s 起算
        assertEquals(1_000_000L, meter.sample(1_500_000L))
    }

    @Test
    fun `停滞不动时速度归零`() {
        var now = 10_000L
        val meter = DownloadSpeedMeter(windowMillis = 1_000L, clock = { now })
        meter.sample(500L)
        now = 11_000L
        assertEquals(0L, meter.sample(500L))
    }
}
