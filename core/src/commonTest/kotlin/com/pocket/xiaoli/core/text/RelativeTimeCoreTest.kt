package com.pocket.xiaoli.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeCoreTest {

    private val now = 1_800_000_000_000L          // 任意固定时刻
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour
    private val dateFallback: (Long) -> String = { "2026-09-12" }

    @Test
    fun `没有时间戳就不说话`() {
        assertEquals("", relativeSinceLabel(0L, now, "更新", dateFallback))
        assertEquals("", relativeSinceLabel(-5L, now, "更新", dateFallback))
    }

    @Test
    fun `一分钟以内是刚`() {
        assertEquals("刚更新", relativeSinceLabel(now - 30_000L, now, "更新", dateFallback))
    }

    @Test
    fun `分钟与小时分档`() {
        assertEquals("1 分钟前更新", relativeSinceLabel(now - minute, now, "更新", dateFallback))
        assertEquals("59 分钟前更新", relativeSinceLabel(now - 59 * minute, now, "更新", dateFallback))
        assertEquals("1 小时前更新", relativeSinceLabel(now - hour, now, "更新", dateFallback))
        assertEquals("23 小时前更新", relativeSinceLabel(now - 23 * hour, now, "更新", dateFallback))
    }

    @Test
    fun `天数分档最多到 cutoff`() {
        assertEquals("1 天前更新", relativeSinceLabel(now - day, now, "更新", dateFallback))
        assertEquals("29 天前更新", relativeSinceLabel(now - 29 * day, now, "更新", dateFallback))
        // 边界：正好 30 天整已经落到「说日期」那一档（与原 Android 口径一致）`r`n        assertEquals("2026-09-12 更新", relativeSinceLabel(now - 30 * day, now, "更新", dateFallback))
    }

    @Test
    fun `超过 cutoff 改说日期`() {
        assertEquals("2026-09-12 更新", relativeSinceLabel(now - 31 * day, now, "更新", dateFallback))
    }

    @Test
    fun `动词为空时不留尾空格`() {
        assertEquals("2026-09-12", relativeSinceLabel(now - 40 * day, now, "", dateFallback))
        assertEquals("刚", relativeSinceLabel(now - 1_000L, now, "", dateFallback))
    }

    @Test
    fun `cutoff 可覆盖，桌面端可以按自己的口径传`() {
        assertEquals(
            "2026-09-12 上次同步",
            relativeSinceLabel(now - 8 * day, now, "上次同步", dateFallback, cutoffDays = 7),
        )
    }
}
