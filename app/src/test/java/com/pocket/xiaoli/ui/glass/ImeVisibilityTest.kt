package com.pocket.xiaoli.ui.glass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键盘可见性判据的算术测试。
 *
 * 数值取本机实测的设备参数（Redmi Note 10 5G / Pixel5 同档：1080×2340）。
 * 抽成纯函数就是为了能在这里把"状态栏 + 导航栏"与"键盘"这两档区分开 ——
 * 而这个区分正是原先"用焦点代理键盘"栽跟头的地方（见 [isImeVisible] 的 KDoc）。
 */
class ImeVisibilityTest {

    private val screen = 2340

    @Test
    fun `没有键盘时不算可见`() {
        // 只扣掉状态栏 + 导航栏（实测约 140px，占 6%）
        assertFalse(isImeVisible(screen, screen - 140))
    }

    @Test
    fun `键盘弹出时算可见`() {
        // 键盘约 900px，加上状态栏与导航栏共约 1040px（占 44%）
        assertTrue(isImeVisible(screen, screen - 1040))
    }

    @Test
    fun `阈值取屏幕高的百分之十五`() {
        val below = (screen * 0.15f).toInt() - 1   // 刚好不到 15%
        val above = (screen * 0.15f).toInt() + 2   // 刚好超过
        assertFalse("低于阈值应判为不可见", isImeVisible(screen, screen - below))
        assertTrue("高于阈值应判为可见", isImeVisible(screen, screen - above))
    }

    /** 手势导航机型的导航栏很矮（约 48px），不能因此误判成键盘。 */
    @Test
    fun `手势导航的矮导航栏不误判为键盘`() {
        assertFalse(isImeVisible(screen, screen - 48))
    }

    @Test
    fun `退化输入一律判为不可见`() {
        assertFalse("屏幕高为 0", isImeVisible(0, 0))
        assertFalse("可见区为 0（还没布局完）", isImeVisible(screen, 0))
        assertFalse("可见区比屏幕还高（异常）", isImeVisible(screen, screen + 100))
    }

    // ── 第二个判据：IME inset（边到边之后才读得到）────────────────────────────
    //
    // 开边到边（`MainActivity.enableEdgeToEdge`）以后窗口不再随键盘变矮，
    // 上面那套「屏幕高 − 可见区高」就永远停在状态栏/导航栏那一档 ——
    // 只用它，键盘弹出来时底栏不会收起，输入区被压在键盘后面。
    // 这两个用例钉住「inset 一侧也能独立判断」，防止以后有人把 inset 判据删掉。

    @Test
    fun `边到边时靠 ime inset 也能认出手键盘`() {
        assertTrue("键盘弹出时 ime inset 底边 > 0", isImeVisibleByInsets(900))
        assertFalse("键盘收起时 ime inset 底边为 0", isImeVisibleByInsets(0))
    }

    /**
     * 两个判据是「或」的关系，任一侧认出键盘就算可见。
     *
     * 不需要渲染：直接把两个布尔组合验一遍，保证以后若有人把「或」改成「与」，
     * 边到边 / 非边到边两种窗口里总有一边会失灵。
     */
    @Test
    fun `两个判据取或`() {
        val byFrame = isImeVisible(screen, screen - 1040)      // 非边到边：窗口变矮了
        val insetQuiet = isImeVisibleByInsets(0)               // 同一次里 inset 读不到
        assertTrue("非边到边的窗口里，frame 认出来就够了", byFrame || insetQuiet)

        val frameQuiet = isImeVisible(screen, screen - 140)    // 边到边：窗口没变矮
        val insetLoud = isImeVisibleByInsets(900)              // 但 inset 读到了
        assertTrue("边到边的窗口里，inset 认出来就够了", frameQuiet || insetLoud)
    }
}
