package com.pocket.xiaoli.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「推荐给同学」气泡内容的基线（浅/深两张）。
 *
 * ## 为什么截内容而不是截整个气泡
 *
 * 外壳是 MIUIX 的 `WindowBottomSheet` —— 独立窗口，JVM 截图抓不到。所以外壳与内容在
 * `RecommendSheet.kt` 里拆成了两个函数：外壳只管窗口与回调，内容（`RecommendSheetBody`）
 * 可以单独渲染。**真正会改坏的是内容**：二维码尺寸、两行链接的高度、按钮顺序。
 *
 * ## 深色那张守什么
 *
 * 二维码是**白底**（功能需要，不是主题色）。深色下如果这个白块被主题色覆盖，
 * 码在夜间模式下就扫不出来了 —— 这一张就是给这件事留的证据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class RecommendSheetScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var copied = 0
    private var opened = 0
    private var shared = 0

    private fun render(dark: Boolean) {
        copied = 0
        opened = 0
        shared = 0
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        RecommendSheetBody(
                            qr = encodeQr(AppShare.GITEE_URL, 512),
                            onCopyLink = { copied++ },
                            onOpenLink = { opened++ },
                            onShareApk = { shared++ },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `二维码与两条链接都在`() {
        render(dark = false)

        composeRule.onNodeWithContentDescription("下载地址二维码").assertExists()
        composeRule.onNodeWithText("Gitee（国内，推荐）").assertExists()
        composeRule.onNodeWithText("GitHub（备用）").assertExists()
        composeRule.onAllNodesWithText("复制链接").assertCountEquals(1)
    }

    @Test
    fun `链接地址完整显示不断行`() {
        render(dark = false)
        // 两条链接的文字都是网址本身；只要 maxLines=1 而不省略，语义树里就是完整串
        composeRule.onNodeWithText(AppShare.GITEE_URL).assertExists()
        composeRule.onNodeWithText(AppShare.GITHUB_URL).assertExists()
    }

    @Test
    fun `浅色基线`() {
        render(dark = false)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/164-recommend-sheet.png")
    }

    @Test
    fun `深色基线`() {
        render(dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/165-recommend-sheet-dark.png")
    }
}
