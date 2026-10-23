package com.pocket.xiaoli.ui.profile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 「关于 → 用户协议」必须**整页**打开，不能塞在设置二级页的滚动容器里。
 *
 * ## 这条测试是给一个真机 bug 立的碑（2026-10-23）
 *
 * 第一版把 `UserAgreementScreen` 直接写在 `AboutContent` 里（`if (open) { …; return }`）。
 * 编译过、单测全绿，但用户点进去**一片空白** —— 因为 `AboutContent` 是
 * `SettingsSubPageScaffold` 内容区（`verticalScroll` 的 Column）的子项，
 * 子项拿到的高度约束是**无限**；协议页里的 `fillMaxSize()` 与 `weight(1f)`
 * 在无限高度下量出来是 0。
 *
 * 正确做法：在 `SettingsSubPageHost` 的**最外层**分流（打开协议时整页替换设置页）。
 * 这个文件用源码扫描把位置钉住 —— 排版类问题截图测试抓不到（渲染树里根本没有那一页），
 * 只有"它被放在哪一层"这件事能测。
 */
class AgreementPagePlacementTest {

    private fun source(): String {
        val candidates = listOf(
            File("../app/src/main/java/com/pocket/xiaoli/ui/profile/SettingsSubPages.kt"),
            File("app/src/main/java/com/pocket/xiaoli/ui/profile/SettingsSubPages.kt"),
            File("src/main/java/com/pocket/xiaoli/ui/profile/SettingsSubPages.kt"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("找不到 SettingsSubPages.kt，候选：${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    @Test
    fun `协议页在二级页顶栏之前分流，而不是渲染在关于页里`() {
        val src = source()

        val hostStart = src.indexOf("internal fun SettingsSubPageHost(")
        assertTrue("找不到 SettingsSubPageHost", hostStart >= 0)
        val scaffold = src.indexOf("SettingsSubPageScaffold(", hostStart)
        val agreement = src.indexOf("UserAgreementScreen(", hostStart)
        assertTrue("找不到 UserAgreementScreen 的调用", agreement >= 0)
        assertTrue(
            "UserAgreementScreen 必须在 SettingsSubPageScaffold **之前**调用 —— " +
                "放进滚动容器里会因为高度无约束而渲染成空白（真机 bug，2026-10-23）",
            agreement < scaffold,
        )
    }

    @Test
    fun `关于页自己不渲染协议页`() {
        val src = source()
        val aboutStart = src.indexOf("private fun AboutContent(")
        assertTrue("找不到 AboutContent", aboutStart >= 0)
        // 切到下一个顶层声明为止（AboutContent 是文件里最后一个 private 页时也能截到末尾）
        val nextTopLevel = src.indexOf("\n@Composable", aboutStart)
        val aboutBody = if (nextTopLevel > aboutStart) src.substring(aboutStart, nextTopLevel) else src.substring(aboutStart)

        assertTrue(
            "AboutContent 里不该再出现 UserAgreementScreen —— 它会被放进 verticalScroll，" +
                "量出 0 高度，用户看到的是一片空白",
            !aboutBody.contains("UserAgreementScreen("),
        )
        assertTrue(
            "AboutContent 应该通过回调把「打开协议」这件事交上去",
            aboutBody.contains("onOpenAgreement"),
        )
    }
}
