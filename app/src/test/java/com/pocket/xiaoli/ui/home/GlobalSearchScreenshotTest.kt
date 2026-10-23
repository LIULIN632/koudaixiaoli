package com.pocket.xiaoli.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 搜索页**外观**基线（MIUIX 顶栏 + 填充式搜索框 + 圆角结果行）。
 *
 * ## 为什么要有这一组
 *
 * 这一页 2026-10-18 之前是 Material3 顶栏 + 描边 `OutlinedTextField` + `FilterChip`
 * 的拼盘；改完整页只用了 MIUIX 的 `SmallTopAppBar` / `SearchBar` / `InputField`
 * 和本项目配色映射，**编译全绿也可能难看**（占位文案被截断、结果行挤成两层、
 * 分组小标题挨太近……），所以钉两张图：
 *
 * 1. 空闲态：搜索框 + 「常用」胶囊 + 一句"没找到问鲤鱼"；
 * 2. 有结果：分组小标题、结果行标题/说明两行、行尾箭头，且**不被键盘顶掉**。
 *
 * ⚠️ MIUIX 的 `Surface` / `IconButton` 走 squircle `RuntimeShader`，Robolectric
 * 渲不出来（截图会是纯色块），所以这里只用 `SearchBar` + `InputField` + Material3
 * `Surface`（详见 `memory/03-pitfalls.md` §8）。若这页以后加了 MIUIX `Surface`，
 * 这条基线要跟着看是不是糊了。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class GlobalSearchScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val campus = CampusConfig.byId("ytlg_main")

    private var dark by mutableStateOf(false)

    @Test
    fun `搜索页外观截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                // MIUIX SearchBar 内部挂了 NavigationBackHandler（SearchBar.kt:121），
                // 它要 LocalNavigationEventDispatcherOwner；真机上由 MainActivity 根部提供
                // （见 memory/03-pitfalls.md §8.1），单测里得自己补一个根 owner。
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides
                        rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    GlobalSearchScreen(
                        campus = campus,
                        onBack = {},
                        onOpenRoute = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/160-search-miuix-light.png")

        // 打字 → 有结果那一屏。MIUIX InputField 底层仍是 BasicTextField，
        // 在语义树上带 SetText 动作，所以能直接输入。
        composeRule.onNode(hasSetTextAction()).performTextInput("成绩")
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/161-search-results-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/162-search-results-dark.png")
    }
}
