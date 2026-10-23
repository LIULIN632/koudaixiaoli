package com.pocket.xiaoli.ui.proactive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.proactive.ProactiveMessage
import com.pocket.xiaoli.data.proactive.ProactiveRules
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 主动气泡与说明气泡的基线。
 *
 * ## 为什么渲的是 `*Content` 而不是完整的 View
 *
 * 完整的气泡带出现/消失动画与 8 秒计时，直接拍会拍到"某一帧"，**基准图会自己变红**
 *（本项目把"无限动画 / 相对时间"列为三类让基准图自己变红的不确定性）。
 * 所以这里渲纯状态驱动的 [ProactiveBubbleContent] 与 [TipBubble]：
 * 它们决定"长什么样"，而"什么时候出现"由 `ProactiveRulesTest` 钉常量、由人工验收看观感。
 *
 * ## 三个状态各守一件事
 *
 * | 状态 | 守什么 |
 * |---|---|
 * | 普通一行 | 主色底 + 尖角 + 关闭叉，常规形态 |
 * | 闲聊 | `fullReveal`：**不省略号**（说半句话比不显示更糟）。池子里最长的句子 17 字，
 *   在普通手机上是**一行**；两行只会在窄屏 / 大字号下出现，所以这张图守的是"没被裁掉" |
 * | 长文案 | 一行 + 省略号：纸面不能撑破（文案本身已在规则层封顶到 24 字） |
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ProactiveBubbleScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /** 说明气泡的正文（截图与断言共用一份，避免"图里是 A、断言查的是 B"）。 */
        const val TIP_TEXT = "两条正事提醒之间至少隔 15 分钟；没有要紧事时偶尔搭句话，" +
            "闲聊之间至少隔 25 分钟。"
    }

    private fun content(message: ProactiveMessage) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                // 与真实挂载点一致：`Box(fillMaxWidth, BottomCenter)` 里放一个**自适应宽度**的气泡。
                // ⚠️ 不要给气泡本体传 fillMaxWidth —— 那会拍出一张"被拉满整屏"的假图，
                // 而线上气泡永远是按文案宽度收缩的。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.spaceLg, vertical = skin.spaceXl),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ProactiveBubbleContent(
                        message = message,
                        onOpen = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun `气泡三个状态截图`() {
        val cases = listOf(
            "95-bubble-class" to ProactiveMessage(
                id = ProactiveRules.ID_CLASS,
                text = "30分钟后上高等数学",
                openRoute = "timetable",
            ),
            "96-bubble-chatter" to ProactiveMessage(
                id = ProactiveRules.CHATTER_ID,
                // 用池子里**最长**的一句：这样"没被裁"这件事才真的被这张图守住
                text = "我是鲤鱼，不是锦鲤，许愿得自带实力",
                fullReveal = true,
            ),
            "97-bubble-long" to ProactiveMessage(
                id = ProactiveRules.ID_EXAM,
                text = "毛泽东思想和中国特色社会主义…",
            ),
        )
        val current = mutableStateOf(cases.first().second)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = skin.spaceLg, vertical = skin.spaceXl),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ProactiveBubbleContent(
                        message = current.value,
                        onOpen = {},
                        onDismiss = {},
                    )
                }
            }
        }
        cases.forEach { (name, message) ->
            composeRule.runOnIdle { current.value = message }
            composeRule.waitForIdle()
            // ⚠️ 拍**气泡节点本身**，不拍 `onRoot()`：整屏截图里会留下一层
            // "首次布局帧"的残影（Robolectric 录制会用同一块画布叠画），
            // 而节点截图同时还把基准图缩到真正要看的那个矩形上。
            composeRule.onNodeWithContentDescription("鲤鱼提醒：${message.text}")
                .captureRoboImage("src/test/screenshots/$name.png")
        }
    }

    /** 说明气泡：贴着「?」展开的那一块（内含长文案，能换行）。 */
    @Test
    fun `说明气泡截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(skin.spaceLg),
                ) {
                    TipBubble(
                        text = TIP_TEXT,
                        maxWidth = 240.dp,
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(TIP_TEXT).captureRoboImage("src/test/screenshots/98-bubble-tip.png")
    }

    /**
     * 关闭键的**触摸区**必须够大。
     *
     * 参考实现这里是 20dp —— 照着搬会做出一个"看得见、点不准"的叉，而**截图完全看不出来**
     *（20dp 与 44dp 在缩略图上几乎一样）。所以这条断言只能落在尺寸上，不能靠眼睛。
     */
    @Test
    fun `关闭键的触摸高度达标`() {
        content(ProactiveMessage(id = ProactiveRules.ID_CLASS, text = "30分钟后上高等数学"))
        composeRule.onNodeWithContentDescription("关闭提醒").assertHeightIsAtLeast(44.dp)
    }

    /** 气泡本体对读屏也要说得清"这是谁在提醒什么"。 */
    @Test
    fun `气泡带得出语义描述`() {
        content(ProactiveMessage(id = ProactiveRules.ID_CLASS, text = "30分钟后上高等数学"))
        composeRule.onNodeWithContentDescription("鲤鱼提醒：30分钟后上高等数学").assertIsDisplayed()
    }

    /**
     * 说明气泡：点「?」展开、点气泡收起。
     *
     * ⚠️ 断言用 `onNodeWithText` 而不是截图：气泡渲染在 `Popup`（独立窗口）里，
     * `captureRoboImage(onRoot())` 拍不到它 —— 它的样子由上面那张 `TipBubble` 基线负责。
     */
    @Test
    fun `点问号能展开说明再点气泡收起`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Column(Modifier.background(colors.surfaceBase)) {
                    Text("主动提醒")
                    BubbleTip(text = "两条正事提醒之间至少隔 1 小时。", contentDescription = "「少」这一档的说明")
                }
            }
        }
        val tipText = "两条正事提醒之间至少隔 1 小时。"
        val trigger = composeRule.onNodeWithContentDescription("「少」这一档的说明")
        // 「?」是 44dp 触摸区：低于本项目无障碍底线会被 AssistantAccessibilityTest 判死
        trigger.assertHeightIsAtLeast(44.dp)
        trigger.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tipText).assertIsDisplayed()
        composeRule.onNodeWithText(tipText).performClick()
        composeRule.waitForIdle()
        assertTrue(
            "点气泡自己就该收起（这里刻意不放关闭叉：那是主动气泡的语义）",
            composeRule.onAllNodesWithTextCount(tipText) == 0,
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
