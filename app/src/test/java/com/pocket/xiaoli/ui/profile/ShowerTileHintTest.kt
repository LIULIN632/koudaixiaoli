package com.pocket.xiaoli.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 洗澡格子的副标题：把「余额有没有、是哪一种没有」翻译成一句人话。
 *
 * 重点是**不能把不确定写成 0** —— 未登录、未签约、请求失败三种情况
 * 都不能显示 `¥0.00`，否则用户会以为钱没了。
 */
class ShowerTileHintTest {

    @Test
    fun `未登录引导登录`() {
        assertEquals(
            "登录后查看余额",
            showerTileHint(loggedIn = false, loading = false, balance = 16.76, signed = true),
        )
    }

    @Test
    fun `查询中不显示旧数字`() {
        assertEquals(
            "查询中…",
            showerTileHint(loggedIn = true, loading = true, balance = 16.76, signed = true),
        )
    }

    @Test
    fun `拿到余额显示两位小数`() {
        assertEquals(
            "¥16.76",
            showerTileHint(loggedIn = true, loading = false, balance = 16.76, signed = true),
        )
    }

    @Test
    fun `欠费把负号放在货币符号前`() {
        assertEquals(
            "-¥3.20",
            showerTileHint(loggedIn = true, loading = false, balance = -3.2, signed = true),
        )
    }

    @Test
    fun `未签约免密直说缺什么`() {
        assertEquals(
            "未签约免密",
            showerTileHint(loggedIn = true, loading = false, balance = null, signed = false),
        )
    }

    @Test
    fun `请求失败给可点的动作而不是零`() {
        assertEquals(
            "点击查询余额",
            showerTileHint(loggedIn = true, loading = false, balance = null, signed = null),
        )
    }
}
