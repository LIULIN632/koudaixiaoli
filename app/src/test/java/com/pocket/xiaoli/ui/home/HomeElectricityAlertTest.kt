package com.pocket.xiaoli.ui.home

import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.proactive.ProactiveRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 首页电费提醒的判定：只认「已绑定宿舍 + 记住过的余额 < 提醒线」。
 *
 * 阈值来自 [ProactiveRules.LOW_BALANCE_ELECTRICITY]（主动气泡用的同一个数），
 * 所以这里不写死 20 —— 改阈值时这个测试会跟着动，而不是悄悄失配。
 */
class HomeElectricityAlertTest {

    private val dorm = DormInfo(
        building = "10号宿舍楼",
        room = "602",
        serviceCampusId = "ytu_south",
        dormCode = "BS10S0602",
    )

    @Test
    fun `没绑定宿舍不提醒`() {
        assertNull(electricityAlertOf(null, 1.0))
        assertNull(electricityAlertOf(DormInfo(), 1.0))
    }

    @Test
    fun `没记住过余额不提醒`() {
        assertNull(electricityAlertOf(dorm, null))
    }

    @Test
    fun `余额正好等于提醒线不提醒`() {
        assertNull(electricityAlertOf(dorm, ProactiveRules.LOW_BALANCE_ELECTRICITY))
    }

    @Test
    fun `低于提醒线时带上宿舍名与余额`() {
        val alert = electricityAlertOf(dorm, ProactiveRules.LOW_BALANCE_ELECTRICITY - 0.01)

        assertEquals("10号宿舍楼 602", alert?.dormLabel)
        assertEquals(
            ProactiveRules.LOW_BALANCE_ELECTRICITY - 0.01,
            alert?.balance ?: 0.0,
            0.001,
        )
    }

    @Test
    fun `欠费同样提醒`() {
        assertEquals(-3.2, electricityAlertOf(dorm, -3.2)?.balance ?: 0.0, 0.001)
    }

    @Test
    fun `余额正常不提醒`() {
        assertNull(electricityAlertOf(dorm, ProactiveRules.LOW_BALANCE_ELECTRICITY + 100.0))
    }
}
