package com.pocket.xiaoli.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
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
 * 页内底部抽屉 [AppOverlaySheet] 的三条契约。
 *
 * ## 为什么这四张抽屉此前一张基线都没有
 *
 * 它们的外壳原来是 `top.yukonga.miuix.kmp.window.WindowBottomSheet` —— **独立窗口**，
 * JVM 快照抓不到。于是"抽屉背后变成一块空白"这种事没有任何东西会变红，
 * 最后是用户 2026-10-30 报上来的（见 `memory/03` §九：同一批 ROM 会把弹窗窗口后面糊掉）。
 * 换成页内浮层以后终于截得到、也点得到，这几条就是补上的守门人。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AppOverlaySheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 抽屉 + 它背后那张"页面"。页面留一句可断言的文字，用来证明背景没被换掉。 */
    private fun page(dismissible: Boolean, onDismiss: () -> Unit) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val host = remember { AppOverlayHostState() }
                CompositionLocalProvider(LocalAppOverlayHost provides host) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalPocketXiaoliColors.current.surfaceBase)
                    ) {
                        Text("课表页原文", modifier = Modifier.padding(24.dp))
                        AppOverlaySheet(
                            title = "课表管理",
                            onDismiss = onDismiss,
                            dismissible = dismissible,
                        ) {
                            Text("新建方案")
                        }
                    }
                    AppOverlayLayer(host)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `抽屉盖在页面上而不是换掉背景`() {
        page(dismissible = true, onDismiss = {})

        // 抽屉在
        composeRule.onNodeWithText("课表管理").assertIsDisplayed()
        composeRule.onNodeWithText("新建方案").assertIsDisplayed()
        // ⚠️ 这一条才是这次改动的本体：背后的页面**还在组合里、还看得见**。
        // 窗口式抽屉的表现正是它反过来 —— 页面被 ROM 糊成一块空白
        composeRule.onNodeWithText("课表页原文").assertIsDisplayed()

        composeRule.onRoot().captureRoboImage("src/test/screenshots/384-overlay-sheet.png")
    }

    @Test
    fun `点遮罩关闭`() {
        var dismissed = false
        page(dismissible = true, onDismiss = { dismissed = true })

        composeRule.onNodeWithTag("app-overlay-scrim").performClick()
        composeRule.waitForIdle()

        assertEquals("点遮罩必须回调 onDismiss（返回键走同一个闸门，见 AppOverlayLayer）", true, dismissed)
    }

    @Test
    fun `关不掉的那种点遮罩也不关`() {
        var dismissed = false
        // 强制更新用的就是这一档：能被单点关掉就不叫闸门
        page(dismissible = false, onDismiss = { dismissed = true })

        composeRule.onNodeWithTag("app-overlay-scrim").performClick()
        composeRule.waitForIdle()

        assertEquals(false, dismissed)
        composeRule.onNodeWithText("课表管理").assertIsDisplayed()
    }
}
