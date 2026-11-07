package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
 * 鲤鱼页「还没配置模型」的那张卡片。
 *
 * 为什么只渲这张卡片：整页要 `AppContainer`（真数据库 + 真 prefs + 真 ViewModel），
 * 而这次的改动只在**卡片本身**——它原来钉在页面顶端（读起来像悬浮），现在作为列表项
 * 跟着内容滚。会出问题的地方是「一行放不放得下标题 + 两行说明 + 箭头」，
 * 360dp 是页面的真实可用宽度（屏宽减去左右 padding）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantNotConfiguredCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `未配置卡片 亮色`() {
        render(dark = false, path = "src/test/screenshots/350-assistant-not-configured-card.png")
    }

    @Test
    fun `未配置卡片 深色`() {
        render(dark = true, path = "src/test/screenshots/351-assistant-not-configured-card-dark.png")
    }

    private fun render(dark: Boolean, path: String) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    NotConfiguredCard(onOpenSettings = {})
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(path)
    }
}
