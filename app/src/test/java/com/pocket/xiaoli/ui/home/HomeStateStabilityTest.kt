package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** 探针重跑了几次。它是个计数器，不是测量仪 —— 标注掉了就靠这里抓到。 */
private var probeCalls = 0

/** 只吃首页状态的一小块，用来观察"父级重组时它有没有被跳过"。 */
@Composable
private fun HomeStateProbe(state: HomeUiState) {
    SideEffect { probeCalls++ }
    Text(state.school)
}

/**
 * 守一个像素上看不见的东西：[HomeUiState] 上的 `@Immutable`。
 *
 * Compose 只有**被调方所有参数类型都稳定**才允许跳过重组。`List`、没标注的 data class
 * 一律算不稳定 —— 而且"换了个新实例但内容 equals 相同"救不了，因为 equals 根本不被咨询。
 * 首页还挂着一个每秒走一次的时钟，所以"整页状态不稳定"的代价是**每秒都在付**。
 *
 * ## 为什么只能这么测
 *
 * 加这个标注**改变不了任何一个像素**：基线图永远绿，人眼也永远看不出差别。
 * 唯一能看见它的地方就是重组计数 —— 所以这条测试是这类改动的唯一守门人。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeStateStabilityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `无关状态变化不重画拿首页状态的那一块`() {
        probeCalls = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                val state = remember { HomeUiState(school = "烟台理工学院") }
                var tick by remember { mutableStateOf(0) }
                Box {
                    // 只有这一句跟着变（点它 = 让父级重组一次，而 state 一个字都没动）
                    Text("tick $tick", modifier = Modifier.clickable { tick++ })
                    HomeStateProbe(state)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("首次组合应当恰好跑一次", 1, probeCalls)

        composeRule.onNodeWithText("tick 0").performClick()
        composeRule.waitForIdle()

        // 父级确实重组了（标题换成了 tick 1），而探针被跳过 —— 这就是 @Immutable 换来的东西
        composeRule.onNodeWithText("tick 1").assertExists()
        assertEquals("首页状态是稳定类型，父级重组不该带着它一起重画", 1, probeCalls)
    }
}
