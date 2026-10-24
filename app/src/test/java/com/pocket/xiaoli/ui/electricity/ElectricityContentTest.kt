package com.pocket.xiaoli.ui.electricity

import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.data.proactive.ProactiveRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 电费页的纯判定：余额档位、金额展示、认人方式文案。
 *
 * 阈值不在界面里另写一份 —— 低余额与主动提醒共用
 * [ProactiveRules.LOW_BALANCE_ELECTRICITY]，这里用常量本身做边界，
 * 以后调阈值不会出现两处说法不一致。
 */
class ElectricityContentTest {

    private fun detail(matchedByCode: Boolean) = DormElectricityBalance(
        balance = "36.80",
        accountNo = "BS10S0602",
        address = "北校10楼南602",
        matchedByCode = matchedByCode,
    )

    @Test
    fun `没查到余额时不是零余额`() {
        assertEquals(BalanceLevel.NONE, balanceLevel(null))
        assertEquals(BalanceLevel.NONE, balanceLevel(""))
        assertEquals(BalanceLevel.NONE, balanceLevel("  "))
        assertEquals(BalanceLevel.NONE, balanceLevel("--"))
    }

    @Test
    fun `余额档位边界`() {
        val threshold = ProactiveRules.LOW_BALANCE_ELECTRICITY
        assertEquals(BalanceLevel.NORMAL, balanceLevel(threshold.toString()))
        assertEquals(BalanceLevel.LOW, balanceLevel((threshold - 0.01).toString()))
        assertEquals(BalanceLevel.LOW, balanceLevel("0.00"))
        assertEquals(BalanceLevel.NORMAL, balanceLevel("128.5"))
    }

    @Test
    fun `负数就是欠费 关掉提醒线也不改判`() {
        assertEquals(BalanceLevel.OVERDRAFT, balanceLevel("-0.01"))
        assertEquals(BalanceLevel.OVERDRAFT, balanceLevel("-3.20"))
    }

    @Test
    fun `金额原样展示 只去空白`() {
        assertEquals("--", balanceDigits(null))
        assertEquals("--", balanceDigits("   "))
        assertEquals("36.80", balanceDigits(" 36.80 "))
        assertEquals("-3.20", balanceDigits("-3.20"))
    }

    @Test
    fun `认人方式说清楚`() {
        assertNull(matchSourceLabel(null))
        assertEquals("按宿舍编号", matchSourceLabel(detail(matchedByCode = true)))
        assertEquals("按楼栋 + 房间号", matchSourceLabel(detail(matchedByCode = false)))
    }
}
