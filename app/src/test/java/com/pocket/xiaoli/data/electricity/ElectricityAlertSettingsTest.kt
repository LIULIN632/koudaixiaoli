package com.pocket.xiaoli.data.electricity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 电费提醒口径：提醒线（用户可调）与「知道了」（当天静默，第二天还低会再说）。
 *
 * 这两件事都不是技术常量：提醒线是宿管口径，知道了是不让提醒变成骚扰。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ElectricityAlertSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().putString("academic_school_id", "YIT").apply()
        context.getSharedPreferences("dorm_electricity_alert@YIT", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `没设过时用默认提醒线`() {
        // 2026-11-05 用户口径：19 元不该提醒，默认线从 20 降到 10
        assertEquals(10.0, ElectricityAlertSettings.threshold(context), 0.001)
        assertEquals(10.0, ElectricityAlertSettings.DEFAULT_THRESHOLD, 0.001)
    }

    @Test
    fun `设过的提醒线会被读回来`() {
        ElectricityAlertSettings.setThreshold(context, 50.0)
        assertEquals(50.0, ElectricityAlertSettings.threshold(context), 0.001)
        assertFalse(ElectricityAlertSettings.isDefaultThreshold(context))

        ElectricityAlertSettings.setThreshold(context, 10.0)
        assertEquals(10.0, ElectricityAlertSettings.threshold(context), 0.001)
        assertTrue(ElectricityAlertSettings.isDefaultThreshold(context))
    }

    @Test
    fun `离谱的值退回默认提醒线`() {
        context.getSharedPreferences("dorm_electricity_alert@YIT", Context.MODE_PRIVATE)
            .edit().putFloat("threshold_yuan", 0f).apply()
        assertEquals(ElectricityAlertSettings.DEFAULT_THRESHOLD, ElectricityAlertSettings.threshold(context), 0.001)
    }

    @Test
    fun `阈值上下与欠费分别怎么判`() {
        // 默认 10 元
        assertFalse("19 元不该提醒（这就是用户反馈的那一条）", ElectricityAlertSettings.shouldAlert(context, 19.0))
        assertFalse("刚好等于提醒线不提醒", ElectricityAlertSettings.shouldAlert(context, 10.0))
        assertTrue("9.4 元要提醒", ElectricityAlertSettings.shouldAlert(context, 9.4))
        assertTrue("欠费永远提醒", ElectricityAlertSettings.shouldAlert(context, -3.0))
        assertFalse("没查到余额不提醒", ElectricityAlertSettings.shouldAlert(context, null))
    }

    @Test
    fun `点了知道了当天不再提醒`() {
        val now = 1_700_000_000_000L
        ElectricityAlertSettings.dismissAt(context, 9.0, now)

        assertFalse("还在 9 元，已被告知", ElectricityAlertSettings.shouldAlertAt(context, 9.0, now + 60_000))
        assertFalse("掉到 4 元也不当天再响（旧规则会响，用户嫌烦）", ElectricityAlertSettings.shouldAlertAt(context, 4.0, now + 3_600_000))
        assertFalse("充上来当然不提醒", ElectricityAlertSettings.shouldAlertAt(context, 12.0, now + 60_000))
        assertTrue("欠费不受知道了影响", ElectricityAlertSettings.shouldAlertAt(context, -1.0, now + 60_000))
    }

    @Test
    fun `过了静默期还低就再说一次`() {
        val now = 1_700_000_000_000L
        ElectricityAlertSettings.dismissAt(context, 9.0, now)

        val justBefore = now + ElectricityAlertSettings.DISMISS_QUIET_MS - 1
        assertFalse(ElectricityAlertSettings.shouldAlertAt(context, 8.0, justBefore))

        val after = now + ElectricityAlertSettings.DISMISS_QUIET_MS
        assertTrue("满 24 小时再说", ElectricityAlertSettings.shouldAlertAt(context, 8.0, after))
    }

    @Test
    fun `时钟被往回拨也不会永久静音`() {
        val now = 1_700_000_000_000L
        ElectricityAlertSettings.dismissAt(context, 9.0, now + 10L * 60 * 60 * 1000)
        // 用户把系统时间改早一天 → now 比 dismissedAt 还小
        assertTrue(ElectricityAlertSettings.shouldAlertAt(context, 9.0, now))
    }

    @Test
    fun `充值后复位`() {
        ElectricityAlertSettings.dismiss(context, 8.0)
        assertEquals(8.0, ElectricityAlertSettings.dismissedBalance(context)!!, 0.001)
        assertFalse(ElectricityAlertSettings.shouldAlert(context, 8.0))

        ElectricityAlertSettings.clearDismiss(context)
        assertNull(ElectricityAlertSettings.dismissedBalance(context))
        assertTrue(ElectricityAlertSettings.shouldAlert(context, 8.0))
    }

    @Test
    fun `提醒线按学校隔离`() {
        ElectricityAlertSettings.setThreshold(context, 50.0)

        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().putString("academic_school_id", "YTU").apply()
        // B 校没设过，应该是自己的默认值
        assertEquals(ElectricityAlertSettings.DEFAULT_THRESHOLD, ElectricityAlertSettings.threshold(context), 0.001)
    }
}
