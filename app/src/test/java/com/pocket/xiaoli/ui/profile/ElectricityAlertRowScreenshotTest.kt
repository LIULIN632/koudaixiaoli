package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.electricity.ElectricityAlertSettings
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「服务功能」里「电费提醒线」这一行的观感。
 *
 * 为什么单独渲一行而不是整页：那一页要 `AppContainer`（真数据库 + 真 prefs），
 * 而这一行的排版风险只在**行本身**——右侧当前值 + 箭头会不会被两行小字挤掉。
 * 360dp 是页面的真实可用宽度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ElectricityAlertRowScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `电费提醒线这一行 360dp`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                ) {
                    SettingsMenuChoiceItem(
                        icon = Icons.Outlined.Notifications,
                        title = "电费提醒线",
                        subtitle = "低于这条线提醒，当天只说一次",
                        options = ElectricityAlertSettings.THRESHOLD_CHOICES.map { "¥${it.toInt()}" to it },
                        selected = 10.0,
                        onSelect = {},
                        accent = ServiceAccent.LIFE,
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/330-settings-electricity-alert-row.png")
    }
}
