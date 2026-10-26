package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
 * 到梦空间入口页。
 *
 * 这一页的价值全在**说真话**：网页版登录只有扫码，而二维码显示在这台手机上，
 * 自己扫不了自己（取证见 `docs/superpowers/specs/2026-10-05-secondclass-architecture-design.md` §11.6）。
 * 所以断言分两头：该说清的必须说清，不该承诺的一个字都不能出现。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SecondClassEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = SecondClassEntryState(
        webUrl = "https://www.5idream.net/",
        schoolLabel = "烟台理工学院",
    )

    private fun render(dark: Boolean = false, onOpenWeb: (String, String) -> Unit = { _, _ -> }) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    SecondClassEntryContent(
                        state = state,
                        onOpenWeb = onOpenWeb,
                        onCopyUrl = {},
                        onOpenStore = {},
                    )
                }
            }
        }
    }

    @Test
    fun `把唯一走得通的路说清`() {
        render()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("网页版只能在电脑浏览器里登录").assertExists()
        composeRule.onNodeWithText("登录只支持扫码，请在电脑上打开").assertExists()
        composeRule.onNodeWithText("烟台理工学院 · 学分与活动记录都在里面").assertExists()
    }

    @Test
    fun `不承诺做不到的事`() {
        // 这些说法一旦出现就是骗人：我们既没有登录态，也不做写操作。
        render()
        composeRule.waitForIdle()
        listOf("已为你登录", "自动报名成功", "已代签到", "替你登录").forEach { forbidden ->
            composeRule.onNodeWithText(forbidden, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `三个动作各自接线`() {
        var opened: Pair<String, String>? = null
        var copied = 0
        var stored = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                SecondClassEntryContent(
                    state = state,
                    onOpenWeb = { url, title -> opened = url to title },
                    onCopyUrl = { copied++ },
                    onOpenStore = { stored++ },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("复制网页地址").performClick()
        composeRule.onNodeWithText("在浏览器打开（桌面版）").performClick()
        composeRule.onNodeWithText("装官方 App").performClick()

        assertEquals(1, copied)
        assertEquals(1, stored)
        assertEquals("https://www.5idream.net/" to "到梦空间", opened)
    }

    @Test
    fun `入口页快照`() {
        val cases = listOf(false to "350-secondclass-entry", true to "351-secondclass-entry-dark")
        // setContent 每条用例只能调一次：明暗两版共用一个组合，用状态切
        var dark by mutableStateOf(false)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    SecondClassEntryContent(
                        state = state,
                        onOpenWeb = { _, _ -> },
                        onCopyUrl = {},
                        onOpenStore = {},
                    )
                }
            }
        }
        cases.forEach { (wantDark, name) ->
            composeRule.runOnIdle { dark = wantDark }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        }
    }
}
