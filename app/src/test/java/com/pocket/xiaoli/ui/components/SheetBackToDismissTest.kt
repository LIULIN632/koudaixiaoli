package com.pocket.xiaoli.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.test.espresso.Espresso
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 系统返回键必须能关掉 `dismissOnBackPress = false` 的弹窗抽屉。
 *
 * ## 为什么不用真的 `WindowBottomSheet`
 *
 * MIUIX 的抽屉外壳会画 squircle 着色器，Robolectric 的 `RuntimeShader` 解析不了那段 AGSL
 * （`'color' is not a valid layout qualifier`）—— 仓库里所有快照测试也只渲抽屉正文。
 * 这里用一个同样把 `dismissOnBackPress` 关掉的普通 `Dialog` 复现故障条件：
 * Compose 会向**弹窗自己的**分发器注册一个空回调把返回键吞掉，普通 `BackHandler`
 * 注册在 Activity 上永远收不到。只有 [SheetBackToDismiss] 找对分发器才能关掉。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SheetBackToDismissTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = false

    private fun showSheet(backEnabled: Boolean) {
        composeRule.setContent {
            PocketXiaoliTheme {
                Dialog(
                    onDismissRequest = { dismissed = true },
                    properties = DialogProperties(dismissOnBackPress = false),
                ) {
                    SheetBackToDismiss(enabled = backEnabled) { dismissed = true }
                    Text("抽屉正文")
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `返回键可以关闭抽屉`() {
        showSheet(backEnabled = true)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertTrue("返回键应关闭抽屉（否则只有点空白能关）", dismissed)
    }

    @Test
    fun `强更抽屉不响应返回键`() {
        showSheet(backEnabled = false)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertFalse("allowDismiss=false 时返回键不应关闭抽屉", dismissed)
    }
}
