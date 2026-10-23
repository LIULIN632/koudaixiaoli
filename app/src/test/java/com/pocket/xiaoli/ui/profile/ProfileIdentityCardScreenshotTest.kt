package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
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
 * 「我的」页身份卡（`ProfileIdentityCard`）的**外观快照**。
 *
 * ## 为什么补这组基线
 *
 * 这张卡是「我的」tab **第一眼看到的东西**（该页没有顶栏，正文第一条就是它），
 * 却一直没有任何视觉基线 —— 皮肤 / 配色 / 圆角改动的门禁里，它是一个洞。
 *
 * 用户反馈"信息界面不好看"时，按 `docs/UI验收手册.md` 的处方：**先把图渲出来**，
 * 再指出具体问题（上一轮「鲤鱼输入框太难看」就是这么定位到"56dp 最小高度撑到 72dp"
 * 与"禁用态灰疙瘩"这两个具体缺陷的，而不是凭感觉调参数）。
 *
 * ## 覆盖的四个状态（2026-10 改版后）
 *
 * 卡片现在**不含任何身份值**，所以这组基线测的是"两行 + 一枚计数胶囊"的排布，
 * 而不是各种数据长度下的截断：
 *
 * | 文件 | 想验证的 |
 * |---|---|
 * | `60-identity-bound` | 鲜活皮肤 · 三项全绑（`已绑定 3/3`）—— 基准排布 |
 * | `61-identity-partial` | 极简皮肤 · 只绑了一项（`已绑定 1/3`）—— 该皮肤把页底推暗一档，层次会变 |
 * | `62-identity-unbound` | 全新用户（0 项）—— 琥珀「待绑定」的**描边**够不够显眼、又不至于像报错 |
 * | `63-identity-dark` | 深色态 —— 渐变头像、胶囊底与描边在这里最容易"糊成一片" |
 *
 * ⚠️ 这组只渲**单张卡**，看不到"和下面的四个服务格叠在一起好不好看" ——
 * 那件事由 `ProfileHomeScreenshotTest`（`70/71` 整页）负责。**两张一起看**：
 * 上一轮正是整页基线暴露出"两块 2×2 叠着"，才把卡里那块事实格删掉的。
 *
 * ## 为什么是确定性的
 *
 * 2026-10 起卡上的鲤鱼徽标已删除（用户要求"去掉 AI 形象"），整张卡是**纯静态**的：
 * 只有文字、渐变头像圆与胶囊，没有任何动画或随时间 / 状态变化的元素。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ProfileIdentityCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 一个渲染用例：文件名 + 皮肤 + 明暗 + 已绑项数（0..3）。 */
    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val dark: Boolean,
        val boundCount: Int,
    )

    private val cases = listOf(
        Case(name = "60-identity-bound", skin = SkinStyle.VIVID, dark = false, boundCount = 3),
        Case(name = "61-identity-partial", skin = SkinStyle.MINIMAL, dark = false, boundCount = 1),
        Case(name = "62-identity-unbound", skin = SkinStyle.VIVID, dark = false, boundCount = 0),
        Case(name = "63-identity-dark", skin = SkinStyle.VIVID, dark = true, boundCount = 3),
    )

    @Test
    fun `我的页身份卡各状态截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = case.skin,
                darkTheme = case.dark,
            ) {
                val colors = LocalPocketXiaoliColors.current
                // 复现真实约束：页底色 + 「我的」页正文的 16dp 横向内边距。
                // 给卡片 fillMaxWidth 会掩盖真实宽度下的截断（见 UI验收手册的 20/21 那节）。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    ProfileIdentityCard(
                        boundCount = case.boundCount,
                        onOpenBinding = {},
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
