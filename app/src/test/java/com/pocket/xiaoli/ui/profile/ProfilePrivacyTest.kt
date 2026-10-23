package com.pocket.xiaoli.ui.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「我的」页**不显示个人信息**这条产品约定的门禁（2026-10）。
 *
 * ## 为什么要有它（截图看不出来的那一层）
 *
 * 用户的要求是"别显示个人信息，点进去才能显示"。这条要求有两种做法：
 *
 * 1. 把值**传进来、但不渲染** —— 截图看起来是对的，代价是下一个人只要随手
 *    在 `ProfileHomeContent` 里 `Text(studentId)` 一下，隐私就漏了，而**任何一张
 *    基线图都不会红**（那正是新加了一行文字）；
 * 2. 让值**根本进不来** —— 参数表里没有它。
 *
 * 这次选的是第 2 种：`ProfileHomeContent` 只收 `boundCount: Int`。
 * 而"参数表里没有"这件事**只能靠语义树/编译期来守**，截图那一层看不见 ——
 * 所以这个文件与 `ProfileIdentityCardScreenshotTest`（观感）是互补的，不是重复的。
 *
 * ## 断言的是"查不到"，不是"某个控件不存在"
 *
 * 基线图管"长什么样"，这里管"有没有出现过"。所以断言按**文本**做：
 * 昵称、学号（含脱敏尾号）、宿舍门牌、以及"学号"这个词本身，在整页语义树里
 * 一个都不许命中。谁把身份字段加回这一页，第二组断言就会红。
 *
 * ⚠️ 刻意**不**断言"宿舍"这两个字：下面那格服务卡就叫「宿舍电费」，
 * 拿它当禁词会让这条测试在正常改动下误报。禁词只锁**值**与"学号"这个字段名。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ProfilePrivacyTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 渲染整页正文（与真实调用点同形参，只有 `boundCount` 一个身份相关入参）。 */
    private fun render(boundCount: Int, onOpenBinding: () -> Unit = {}) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                ProfileHomeContent(
                    boundCount = boundCount,
                    onOpenBinding = onOpenBinding,
                    electricityBalance = "23.50",
                    electricityLoading = false,
                    onOpenElectricity = {},
                    onOpenFocus = {},
                    showerHint = "趣智校园 · 已登录",
                    onOpenShower = {},
                    onOpenExpress = {},
                    onSchemeManager = {},
                    onImportMenu = {},
                    onShareMenu = {},
                    onSettings = {},
                )
            }
        }
    }

    @Test
    fun `我的页正文里查不到任何身份值`() {
        render(boundCount = 3)

        // 入口卡在：标题 + 提示 + 一个纯计数的胶囊
        composeRule.onNodeWithText("我的信息").assertExists()
        composeRule.onNodeWithText("点开查看账号与绑定信息").assertExists()
        composeRule.onNodeWithText("已绑定 3/3").assertExists()

        // 改造前这一页会直接印出来的东西 —— 现在一个都不许命中
        val forbidden = listOf(
            "同学",          // 默认昵称（「昵称」概念已整体删除）
            "张三",          // 任何用户自定义昵称
            "2023123456789", // 完整学号
            "6789",          // 脱敏后仅剩的尾号
            "3号楼 412",     // 宿舍门牌
            "学号",          // 字段名本身：连"有什么可看"都不在这张卡上说
        )
        forbidden.forEach { word ->
            composeRule.onAllNodesWithText(word, substring = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun `整张卡是一个动作，点它进绑定信息页`() {
        var opened = false
        render(boundCount = 2) { opened = true }

        composeRule.onNodeWithText("我的信息").performClick()

        assertTrue("点身份卡应当打开「绑定信息」页", opened)
    }

    @Test
    fun `一项都没绑时，卡上给的是待绑定而不是空白`() {
        render(boundCount = 0)

        // 计数胶囊是这一页**唯一**允许出现的绑定信息：它是"绑好了没"的一眼提示，
        // 又不泄漏绑的是哪个学号、哪间宿舍。
        composeRule.onNodeWithText("待绑定").assertExists()
    }
}
