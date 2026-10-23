package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
 * 鲤鱼输入框（`InputRow`）的**外观快照**。
 *
 * ## 为什么单独给它一组快照
 *
 * 这是全 App 用户**停留最久、看得最多**的一个控件（聊天输入框），
 * 而"好不好看"是主观的 —— 争论没有意义，**渲出来看**才有意义。
 * 用户反馈过"太难看"，本测试就是那次的取证与验收工具：
 * 改之前渲一遍、改之后渲一遍，把两张图并排放在一起看差在哪。
 *
 * ## 覆盖的状态
 *
 * 空输入（禁用发送）/ 正在输入（发送可用）/ 正在回答（停止按钮）/ 多行长文本。
 * 这四个状态各自有不同的配色与高度，只看其中一个会漏掉问题。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AssistantComposerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 一个渲染用例：文件后缀 + 查询文本 + 是否流式回答中。 */
    private data class Case(val name: String, val query: String, val streaming: Boolean)

    private val cases = listOf(
        Case("30-composer-empty", "", false),
        Case("31-composer-typing", "帮我看看明天的课表", false),
        Case("32-composer-streaming", "帮我看看明天的课表", true),
        Case(
            "33-composer-multiline",
            "我想问一下，下周三上午的高数课在哪个教室？如果换教室了麻烦也说一下，谢谢",
            false,
        ),
    )

    @Test
    fun `鲤鱼输入框各状态截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            Lounge(
                query = current.value.query,
                streaming = current.value.streaming,
                onQueryChange = { current.value = current.value.copy(query = it) },
            )
        }
        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}

/** 把输入框放在**真实的页底色**上渲染 —— 只看控件本身会误判边界与对比度。 */
@Composable
private fun Lounge(query: String, streaming: Boolean, onQueryChange: (String) -> Unit) {
    PocketXiaoliTheme(
        appearanceMode = AppearanceMode.RAINBOW,
        skinStyle = SkinStyle.VIVID,
        darkTheme = false,
    ) {
        val colors = LocalPocketXiaoliColors.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                InputRow(
                    query = query,
                    onQueryChange = onQueryChange,
                    isStreaming = streaming,
                    onSend = {},
                    onStop = {},
                )
            }
        }
    }
}
