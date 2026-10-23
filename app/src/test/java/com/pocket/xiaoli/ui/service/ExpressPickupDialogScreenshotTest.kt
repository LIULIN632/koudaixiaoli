package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
 * 快递取件码**弹窗**的基线。
 *
 * ## 为什么截的是卡片而不是 `Dialog`
 *
 * `Dialog` 是独立窗口，JVM 上没有窗口合成器，`captureRoboImage` 截不到它
 * （`RecommendSheet` 踩过同一个坑，那边是把内容拆成 `RecommendSheetBody`）。
 * 所以这里直接渲染 `ExpressPickupCard` —— 它就是弹窗里那张卡的全部内容，
 * 连遮罩一起画，截出来与真机同一观感。
 *
 * ## 为什么还是四张
 *
 * 这张卡的观感由**皮肤**与**明暗**两个轴决定，两者都会动到同一批东西：
 * - 皮肤：`radiusLarge`（28 vs 16）与 `radiusCard`（20 vs 12）—— 外卡与内行的圆角同时变；
 * - 明暗：遮罩透明度、卡面、文字三级色、卡外柔光的透明度减半。
 *
 * 只拍浅色鲜活一张的话，"深色下遮罩太黑压死卡面"和"极简下内行圆角像没切"都拍不到。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ExpressPickupDialogScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(skin: SkinStyle, dark: Boolean) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = skin,
                darkTheme = dark,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                ) {
                    ExpressPickupCard(onDismiss = {})
                }
            }
        }
    }

    @Test
    fun `标题只出现一次且两个平台都在`() {
        render(SkinStyle.VIVID, dark = false)

        // 弹窗名出现一次即可；卡里再来一遍就是当年 ServiceInfoScreen 的老毛病
        composeRule.onAllNodesWithText("快递取件码").assertCountEquals(1)
        composeRule.onNodeWithText("淘宝 / 菜鸟").assertExists()
        composeRule.onNodeWithText("拼多多").assertExists()
    }

    @Test
    fun `关闭键有可读的无障碍描述`() {
        render(SkinStyle.VIVID, dark = false)
        // 右上角那个圆钮只有图标，没有 contentDescription 就是读屏黑洞
        composeRule.onNodeWithContentDescription("关闭").assertExists()
    }

    @Test
    fun `深色下两张平台卡都在`() {
        render(SkinStyle.VIVID, dark = true)
        composeRule.onNodeWithText("淘宝 / 菜鸟").assertExists()
        composeRule.onNodeWithText("拼多多").assertExists()
    }

    @Test
    fun `鲜活浅色基线`() {
        render(SkinStyle.VIVID, dark = false)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/160-express-dialog-vivid.png")
    }

    @Test
    fun `鲜活深色基线`() {
        render(SkinStyle.VIVID, dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/161-express-dialog-vivid-dark.png")
    }

    @Test
    fun `极简浅色基线`() {
        render(SkinStyle.MINIMAL, dark = false)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/162-express-dialog-minimal.png")
    }

    @Test
    fun `极简深色基线`() {
        render(SkinStyle.MINIMAL, dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/163-express-dialog-minimal-dark.png")
    }
}
