package com.pocket.xiaoli.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏显隐判定的契约测试。
 *
 * 其中 `收起键盘后底栏必须回来` 一条，直接来自 2026-10 的用户反馈
 *（"点开输入框再推出，底部 tab 不见了"）—— **把那次事故变成永久测试**，
 * 而不是只写一段注释说明"别忘了键盘不等于焦点"。
 */
class BottomBarVisibilityTest {

    private fun visible(
        hasOpenSubPage: Boolean = false,
        isTabRoute: Boolean = true,
        imeVisible: Boolean = false,
        textInputFocused: Boolean = false,
    ) = shouldShowBottomBar(hasOpenSubPage, isTabRoute, imeVisible, textInputFocused)

    @Test
    fun `平时显示底栏`() {
        assertTrue(visible())
    }

    @Test
    fun `打开子页时收起底栏`() {
        assertFalse(visible(hasOpenSubPage = true))
    }

    @Test
    fun `非 tab 路由没有底栏`() {
        assertFalse(visible(isTabRoute = false))
    }

    @Test
    fun `键盘弹出且正在编辑时收起底栏`() {
        assertFalse(visible(imeVisible = true, textInputFocused = true))
    }

    /**
     * ⚠️ **本次事故的回归测试**。
     *
     * 用户收起键盘（按返回键 / 下拉箭头）后，**输入框仍然保持焦点** ——
     * 这是 Android 的标准行为。此时底栏必须回来。
     *
     * 曾经的实现只看焦点，于是这里会返回 `false`，底栏永久消失。
     */
    @Test
    fun `收起键盘后底栏必须回来（焦点可能还留着）`() {
        assertTrue(
            "键盘已收起（imeVisible=false）时，即使输入框仍有焦点，底栏也必须显示 —— " +
                "否则用户会看到「推出键盘后底部 tab 不见了」",
            visible(imeVisible = false, textInputFocused = true),
        )
    }

    /** 反过来：键盘可能在**别的窗口**（弹窗里的输入框），那时不该收本窗口的底栏。 */
    @Test
    fun `键盘在但没有输入框在编辑时不收底栏`() {
        assertTrue(
            "没有输入框获得焦点时不收底栏：键盘可能来自弹窗（adjustResize 缩的是那个窗口）",
            visible(imeVisible = true, textInputFocused = false),
        )
    }

    /**
     * 四条件的效果逐条验证：前两个各自单独生效；后两个**必须同时**才生效。
     */
    @Test
    fun `前两个条件各自单独生效`() {
        assertFalse("有子页就该收", visible(hasOpenSubPage = true))
        assertFalse("非 tab 路由就没有底栏", visible(isTabRoute = false))
    }

    @Test
    fun `后两个条件必须同时满足才收起`() {
        assertFalse("键盘 + 正在编辑 → 收起", visible(imeVisible = true, textInputFocused = true))
        assertTrue("只有键盘、没在编辑 → 不收", visible(imeVisible = true, textInputFocused = false))
        assertTrue("只在编辑、键盘已收 → 不收（本次事故的场景）", visible(imeVisible = false, textInputFocused = true))
    }
}
