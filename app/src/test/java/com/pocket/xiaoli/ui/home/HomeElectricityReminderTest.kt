package com.pocket.xiaoli.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页电费提醒的接线：显示条件与"点开电费页"这一下。
 *
 * ⚠️ 不要 `import assertDoesNotExist` —— 它是 `SemanticsNodeInteraction` 的成员函数，
 * 额外 import 反而会编译失败（仓库里踩过）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeElectricityReminderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `低余额提醒显示并可点开电费页`() {
        var opened = 0
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                HomeContent(
                    state = HomeUiState(
                        electricityAlert = HomeElectricityAlert(
                            dormLabel = "北校10楼南602",
                            balance = 12.4,
                        ),
                    ),
                    hasUnread = false,
                    modules = listOf(HomeModule.TODO),
                    hiddenModules = emptySet(),
                    onOpenElectricity = { opened++ },
                )
            }
        }

        composeRule.onNodeWithText("宿舍电费只剩 ¥12.40").assertExists()
        composeRule.onNodeWithText("上次查到的余额，点开可刷新").assertExists()
        composeRule.onNodeWithText("宿舍电费只剩 ¥12.40").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `没有提醒时不渲染这一行`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                HomeContent(
                    state = HomeUiState(),
                    hasUnread = false,
                    modules = listOf(HomeModule.TODO),
                    hiddenModules = emptySet(),
                )
            }
        }

        composeRule.onNodeWithText("上次查到的余额，点开可刷新").assertDoesNotExist()
    }
}
