package com.pocket.xiaoli.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置行尾「?」的**交互契约**：点问号只开说明，不能顺带把整行点了。
 *
 * 这不是"多测一遍 UI"，而是这条改动的核心风险：`SettingsMenuItem` 的整行是
 * `combinedClickable`，问号是**嵌在行里的另一个 clickable**。Compose 里嵌套点击
 * 默认由最内层消费，但这属于"实现细节"，一旦有人把问号改成 `pointerInput` 手写
 * 手势、或把行换成 `Box` + 外层 `clickable`，就可能两个回调一起触发 ——
 * 表现是"点说明直接跳进了学期设置页"，而快照看不出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SettingsHintRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setRow(onClick: () -> Unit) {
        composeRule.setContent {
            PocketXiaoliTheme {
                SettingsCard {
                    SettingsMenuItem(
                        icon = Icons.Outlined.CalendarMonth,
                        title = "学期设置",
                        onClick = onClick,
                        hint = "学期开始日期决定「当前第几周」怎么算。",
                    )
                }
            }
        }
    }

    @Test
    fun `点问号只开说明不触发行跳转`() {
        var rowClicks = 0
        setRow { rowClicks++ }

        composeRule.onNodeWithContentDescription("说明").performClick()
        composeRule.waitForIdle()

        assertEquals("问号必须自己消费点击，不能冒泡到整行", 0, rowClicks)
    }

    @Test
    fun `点行本身仍然跳转`() {
        var rowClicks = 0
        setRow { rowClicks++ }

        composeRule.onNodeWithText("学期设置").performClick()
        composeRule.waitForIdle()

        assertEquals(1, rowClicks)
    }
}
