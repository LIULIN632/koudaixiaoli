package com.pocket.xiaoli.ui.legal

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
 * 用户协议页的两张基线。
 *
 * ## 为什么要图
 *
 * 这是**每个用户第一次打开 App 看到的唯一一屏**，也是唯一一屏"不能跳过、必须读完才能用"
 * 的内容。它的风险不在逻辑（`AgreementStoreTest` 管），而在排版：
 *
 * - 正文十条、滚三屏 —— 字号 13sp + 行高 21sp 挤一档就会变成一堵墙；
 * - `**强调**` 是手写的分段渲染（`EmphasizedText`），切错一段就会把星号原样漏出来；
 * - 底部两个按钮 + 一行小字在 `navigationBarsPadding()` 之下，窄屏上不能挤成两行。
 *
 * 所以要两张：**同意态**（首启，两个按钮）与**阅读态**（设置里进来，只有返回）。
 * 深浅只录同意态的浅色 —— 文本色全部来自令牌，深色由 `LocalPocketXiaoliColors` 保证，
 * 这里不再重复一遍同样版式。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AgreementScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun Screen(dark: Boolean = false, content: @Composable () -> Unit) {
        PocketXiaoliTheme(
            appearanceMode = AppearanceMode.RAINBOW,
            skinStyle = SkinStyle.VIVID,
            darkTheme = dark,
        ) { content() }
    }

    @Test
    fun `首启同意页`() {
        composeRule.setContent {
            Screen { UserAgreementScreen(mode = AgreementMode.CONSENT) }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/270-agreement-consent.png")
    }

    @Test
    fun `设置里的阅读页`() {
        composeRule.setContent {
            Screen { UserAgreementScreen(mode = AgreementMode.READ) }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/271-agreement-read.png")
    }
}
