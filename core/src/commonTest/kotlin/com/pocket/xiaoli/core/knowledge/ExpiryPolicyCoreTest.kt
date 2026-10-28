package com.pocket.xiaoli.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 时效判定的边界规则。
 *
 * 守的是「最后有效日当天仍然有效」这一条 —— 写错一天，用户就会在假期当天被告知
 * 「此安排已过期」。
 */
class ExpiryPolicyCoreTest {

    private fun epoch(iso: String): Long = parseIsoDateToEpochDay(iso) ?: error("bad date $iso")

    @Test
    fun `日期换算与 java-time 口径一致`() {
        assertEquals(0L, epoch("1970-01-01"))
        assertEquals(19_000L, epoch("2022-01-08"))
        assertEquals(2_932_896L, epoch("9999-12-31"))
        assertEquals("1970-01-01", epochDayToIso(0L))
        assertEquals("2026-10-28", epochDayToIso(epoch("2026-10-28")))
        // 闰年 2 月 29 日
        assertEquals("2024-02-29", epochDayToIso(epoch("2024-02-29")))
    }

    @Test
    fun `脏日期不抛异常而是按无期限处理`() {
        assertNull(parseIsoDateToEpochDay("2026-13-01"))
        assertNull(parseIsoDateToEpochDay("not-a-date"))
        assertNull(parseIsoDateToEpochDay("2026/10/01"))
        assertEquals(ExpiryDecision.Include, expiryDecision("2026-13-01", hardExpiry = true, todayEpochDay = 20_000L))
    }

    @Test
    fun `无失效日期时一律放行`() {
        assertEquals(ExpiryDecision.Include, expiryDecision(null, hardExpiry = true, todayEpochDay = 20_000L))
        assertEquals(ExpiryDecision.Include, expiryDecision("", hardExpiry = true, todayEpochDay = 20_000L))
    }

    @Test
    fun `最后有效日当天仍然有效`() {
        val lastDay = epoch("2026-10-02")
        assertEquals(ExpiryDecision.Include, expiryDecision("2026-10-02", hardExpiry = false, todayEpochDay = lastDay))
        assertEquals(ExpiryDecision.Include, expiryDecision("2026-10-02", hardExpiry = false, todayEpochDay = lastDay - 3))
    }

    @Test
    fun `超过最后有效日一天即视为过期`() {
        val lastDay = epoch("2026-10-02")
        val decision = expiryDecision("2026-10-02", hardExpiry = false, todayEpochDay = lastDay + 1)
        assertTrue(decision is ExpiryDecision.IncludeExpired)
        decision as ExpiryDecision.IncludeExpired
        assertEquals(1L, decision.daysAgo)
        assertEquals("2026-10-02", decision.expiredOnIso)
    }

    @Test
    fun `硬过期排除 软过期标记保留`() {
        val today = epoch("2026-11-01")
        assertEquals(ExpiryDecision.Exclude, expiryDecision("2026-10-02", hardExpiry = true, todayEpochDay = today))
        val soft = expiryDecision("2026-10-02", hardExpiry = false, todayEpochDay = today)
        assertTrue(soft is ExpiryDecision.IncludeExpired)
        assertEquals(30L, (soft as ExpiryDecision.IncludeExpired).daysAgo)
    }

    @Test
    fun `9999 哨兵视为永不过期`() {
        assertEquals(ExpiryDecision.Include, expiryDecision("9999-12-31", hardExpiry = true, todayEpochDay = 20_000L))
    }

    @Test
    fun `过期提醒文案包含日期与天数`() {
        val text = expiryNotice("2026-10-02", 30)
        assertTrue(text.contains("2026-10-02"))
        assertTrue(text.contains("30 天前"))
    }
}
