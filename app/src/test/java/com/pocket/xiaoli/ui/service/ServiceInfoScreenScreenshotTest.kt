package com.pocket.xiaoli.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
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
 * 「服务信息页」的基线（专升本 / 考研 / 返乡大巴 / 健身房 / 驾校 五个入口共用同一个组件）。
 *
 * ## 为什么补这一张
 *
 * 2026-10 用户报「点进去成绩查询，下面还是有一行字写的成绩查询，其他的服务也是这样的」——
 * `ServiceInfoScreen` 当时把**页面名写了两遍**：顶栏一遍、正文那个彩色说明块里又一遍。
 * 而它此前**一张基线图都没有**，所以这种"同一句话出现两次"的重复在测试里完全看不见。
 *
 * 两条判据各守一半：
 * - **语义**（`页面名只出现一次`）：重复文字这件事，位置变了图也会跟着变，但"到底出现了几次"
 *   用语义树断言最直接，也不怕配色/间距微调；
 * - **像素**（基线图）：说明块的留白、与下面各段卡片的层次，只有看图才知道。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ServiceInfoScreenScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sections = listOf(
        ServiceInfoSection("基本流程", "报名（身份证 + 体检）→ 科目一理论 → 科目二场地 → 科目三道路 → 科目四安全文明 → 领证。"),
        ServiceInfoSection("怎么选驾校", "重点看三点：① 训练场离学校的距离 ② 是否一人一车 ③ 补考与练车是否额外收费。"),
    )

    private fun render() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                ) {
                    ServiceInfoScreen(
                        title = "驾校",
                        subtitle = "学车流程与选校要点，价格与班型以驾校公布为准。",
                        accent = Color(0xFF6E8BA8),
                        onBack = {},
                        onOpenLink = { _, _ -> },
                        sections = sections,
                    )
                }
            }
        }
    }

    @Test
    fun `页面名只出现一次`() {
        render()

        // 顶栏那一处是**必须**的（返回箭头与滚动吸附挂在它上面）；
        // 正文里再来一遍就是用户报的"重复信息"
        composeRule.onAllNodesWithText("驾校").assertCountEquals(1)
        // 说明文字本身要留着（它是有用的信息，不是重复的名字）
        composeRule.onNodeWithText("学车流程与选校要点，价格与班型以驾校公布为准。").assertExists()
    }

    @Test
    fun `服务信息页基线`() {
        render()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/140-service-info-page.png")
    }
}
