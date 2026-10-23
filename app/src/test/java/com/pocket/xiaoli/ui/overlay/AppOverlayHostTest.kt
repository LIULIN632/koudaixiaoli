package com.pocket.xiaoli.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 浮层宿主的三条不变量。
 *
 * 为什么值得单独测：宿主是"弹窗不用系统窗口"的唯一支点。
 * 它一旦坏掉（比如 `hide()` 之后内容还留在树上），表现是
 * "弹窗看不见了但点哪都没反应" —— 这类问题在截图里看不出来，只能靠断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppOverlayHostTest {

    @get:Rule
    val compose = createComposeRule()

    /** 搭一个「页面 + 浮层」的最小组合：页面永远在，浮层由宿主决定画不画。 */
    private fun setContent(host: AppOverlayHostState): AppOverlayHostState {
        compose.setContent {
            Box {
                Text("页面正文")
                CompositionLocalProvider(LocalAppOverlayHost provides host) {
                    AppOverlayLayer(host)
                }
            }
        }
        return host
    }

    @Test
    fun show_renders_content_and_hide_removes_it() {
        val host = setContent(AppOverlayHostState())

        compose.onNodeWithText("浮层内容").assertDoesNotExist()

        compose.runOnIdle { host.show(onDismiss = {}) { Text("浮层内容") } }
        compose.onNodeWithText("浮层内容").assertIsDisplayed()
        // 页面本身没有被浮层顶掉：这是"背景保持原样"的最小证据。
        compose.onNodeWithText("页面正文").assertIsDisplayed()

        compose.runOnIdle { host.hide() }
        compose.onNodeWithText("浮层内容").assertDoesNotExist()
    }

    @Test
    fun only_latest_overlay_survives() {
        val host = setContent(AppOverlayHostState())

        compose.runOnIdle { host.show(onDismiss = {}) { Text("第一层") } }
        compose.onNodeWithText("第一层").assertIsDisplayed()

        compose.runOnIdle { host.show(onDismiss = {}) { Text("第二层") } }
        compose.onNodeWithText("第二层").assertIsDisplayed()
        compose.onNodeWithText("第一层").assertDoesNotExist()
    }

    @Test
    fun dismiss_callback_is_the_single_exit() {
        val host = setContent(AppOverlayHostState())
        var dismissed = 0

        compose.runOnIdle { host.show(onDismiss = { dismissed++ }) { Text("浮层内容") } }
        // 返回键由 `AppOverlayLayer` 接管，走的就是 show 传进来的那个 onDismiss。
        compose.runOnIdle { host.current?.onDismiss?.invoke() }

        assertEquals(1, dismissed)
    }
}
