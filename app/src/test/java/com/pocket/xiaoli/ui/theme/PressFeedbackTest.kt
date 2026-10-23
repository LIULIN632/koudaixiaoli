package com.pocket.xiaoli.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏按下反馈规则（抄 MIUIX `NavigationBar` 的 isPressed tint）。
 *
 * 为什么值得单测：这条规则被**三处**底栏共用（`SoftBottomBar` / `BottomBarTab` /
 * `GlassBottomTab`），且它只在一瞬间可见 —— 快照测试截不到按下态，改错了没人看得见。
 */
class PressFeedbackTest {

    @Test
    fun `按住普通格会变淡`() {
        assertEquals(PRESSED_CONTENT_ALPHA, tabContentAlpha(pressed = true, isAssistant = false), 0f)
    }

    @Test
    fun `松手后恢复满不透明`() {
        assertEquals(1f, tabContentAlpha(pressed = false, isAssistant = false), 0f)
        assertEquals(1f, tabContentAlpha(pressed = false, isAssistant = true), 0f)
    }

    @Test
    fun `鲤鱼格不跟着变淡`() {
        // 它有形象自己的按压回弹（snapTo(0.88f)），再叠一层变淡会显脏
        assertEquals(1f, tabContentAlpha(pressed = true, isAssistant = true), 0f)
    }

    @Test
    fun `按下透明度落在可见但不隐形的区间`() {
        // 低于 0.4 就看不见图标了；等于 1 就等于没做反馈
        assertTrue(PRESSED_CONTENT_ALPHA in 0.4f..0.9f)
    }
}
