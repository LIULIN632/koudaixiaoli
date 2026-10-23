package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 强调色取色与"压在强调色上的前景"的契约（2026-10 Phase C）。
 *
 * ## 为什么要有这份测试
 *
 * 本次改动把 `accentColor()` 从**硬编码常量**改成**读主题令牌**，动因是两个可见后果：
 *
 * 1. 用户选的强调色（`PocketXiaoliTheme(customAccent = …)`，8 个预设）此前**进不来** ——
 *    约 120 处读的是常量，只有读令牌的那部分界面会变；
 * 2. 填充色上写死 `Color.White` 的地方，遇到浅色强调色（`#FFB74D` / `#F48FB1`）
 *    只有约 **1.9:1**，基本读不出。
 *
 * 这两件事都**不会编译报错、也不会让任何别的单测变红**，只能靠断言守。
 *
 * ## 判据为什么用对比度而不是"等于某个色号"
 *
 * 断言 `onAccentColor(白) == 0xFF1F2937` 只是把实现抄了一遍；真正该守的是**规则的目的** ——
 * 前景与填充的对比度不低于 4.5:1。所以下面自己算 WCAG 相对亮度，
 * 实现怎么取值都行，达不到就红。
 *
 * ## ⚠️ 一条 Robolectric 约束
 *
 * `composeRule.setContent` **每个测试只能调一次**（第二次会抛
 * "Cannot call setContent twice per test"）。所以下面统一用一次组合把
 * 所有待测强调色一起取出来，而不是"每个色号调一次 setContent"。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// ⚠️ 必须用 `RobolectricDeviceQualifiers.Pixel5` 常量而不是裸字符串 "Pixel5" ——
// 后者会被限定符解析器拒掉（unrecognized trailing qualifier）。
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AccentTokenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 覆盖"最亮到最暗"的强调色候选：默认令牌、8 个预设的极端档、两个出过事的色号。 */
    private val accents: List<Color> = listOf(
        PocketSoftLight.accentMain,   // 0xFF4D6BFE
        PocketSoftDark.accentMain,    // 0xFF8AA4FF
        Color(0xFFFFB74D),            // 预设里最亮的一档
        Color(0xFFF48FB1),            // 预设里的浅粉
        Color(0xFF2F6B5A),            // 首页非 PURE 态的深绿
        Color(0xFF7B9FF9),            // Phase A 的旧常量（白字压它只有 2.57:1）
        Color(0xFF1F2937),            // 接近最暗
        Color.White,
        Color.Black,
    )

    /**
     * 每个强调色**理论上限**（在白/深两个候选前景里取更优）。
     *
     * 这些数字是算出来的、不是抄实现的，写在断言里当"这条色号的物理上限"文档。
     * 实测（WCAG 2.1）：
     *
     * | 强调色 | 最优前景 | 对比度 |
     * |---|---|---|
     * | `#4D6BFE` 浅色令牌 | 白 | **4.33:1** ← 达不到 4.5，见下 |
     * | `#8AA4FF` 深色令牌 | `#1F2937` | 6.18:1 |
     * | `#FFB74D` 最亮预设 | `#1F2937` | 8.48:1 |
     * | `#F48FB1` 浅粉预设 | `#1F2937` | 6.58:1 |
     * | `#7B9FF9` 旧常量 | `#1F2937` | 5.71:1 |
     */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * 一次组合取齐：**每个强调色**对应的前景、`accentColor()`、`accentSoftColor()`。
     *
     * 用 `LocalPocketXiaoliColors` 覆盖强调色 —— 这正是"用户选了某个强调色"在
     * 组合树里的等价形式，也是 `PocketXiaoliTheme(customAccent = …)` 做的事。
     */
    private fun probe(): Triple<Map<Color, Color>, Map<Color, Color>, Map<Color, Color>> {
        val foregrounds = mutableMapOf<Color, Color>()
        val mains = mutableMapOf<Color, Color>()
        val softs = mutableMapOf<Color, Color>()
        composeRule.setContent {
            for (accent in accents) {
                CompositionLocalProvider(
                    LocalPocketXiaoliColors provides PocketSoftLight.copy(accentMain = accent),
                    LocalDarkMode provides false,
                ) {
                    foregrounds[accent] = onAccentColor()
                    mains[accent] = accentColor()
                    softs[accent] = accentSoftColor()
                }
            }
        }
        composeRule.waitForIdle()
        return Triple(foregrounds, mains, softs)
    }

    /**
     * **核心不变量**：`onAccentColor()` 必须取到该强调色下的**最优**前景。
     *
     * 旧写法用 `luminance() > 0.55f` 的阈值，在中间亮度区间会**系统性判错**：
     * 深色令牌 `#8AA4FF`（亮度 0.396 < 0.55 ⇒ 选白字）白字只有 **2.36:1**，
     * 而深色前景是 6.18:1。阈值看着合理，错得很安静 —— 所以这里断言"取最优"。
     */
    @Test
    fun foregroundIsTheBestAvailableChoiceForEveryAccent() {
        val (foregrounds, _, _) = probe()
        for (accent in accents) {
            val fg = requireNotNull(foregrounds[accent]) { "没有取到 $accent 的前景色" }
            val chosen = contrast(fg, accent)
            val best = maxOf(contrast(Color.White, accent), contrast(Color(0xFF1F2937), accent))
            assertEquals(
                "强调色 #${accent.value.toString(16)} 没有取到最优前景：选了 ${"%.2f".format(chosen)}:1，最优是 ${"%.2f".format(best)}:1",
                best,
                chosen,
                0.01,
            )
        }
    }

    /**
     * 前景对比度**不得低于 3:1**（WCAG 1.4.11 对非文字 UI 组件与图形对象的下限）。
     *
     * 3:1 是这里能承诺的底线，不是偷懒：单个色号能取到的最大对比度是它自己的性质。
     * 令牌 `#4D6BFE` 的最优解就是 4.33:1（白字），深色令牌是 6.18:1 —— 都过 3:1。
     */
    @Test
    fun foregroundNeverDropsBelowTheNonTextMinimum() {
        val (foregrounds, _, _) = probe()
        for (accent in accents) {
            val ratio = contrast(requireNotNull(foregrounds[accent]), accent)
            assertTrue(
                "强调色 #${accent.value.toString(16)} 上的前景只有 ${"%.2f".format(ratio)}:1（需 ≥3:1）",
                ratio >= 3.0,
            )
        }
    }

    /**
     * ⚠️ **已知不足（记录在案，不是通过项）**：浅色令牌 `#4D6BFE` 的填充上，
     * 即使取最优前景也只有 **4.33:1 < 4.5:1**。
     *
     * 这条**不是**本次能靠换前景解决的事 —— 它是那个蓝色的物理上限，
     * 两条出路都属于产品决定，不该由重构顺手改掉：
     *
     * - 把 `accentMain` 压深一档（约需 `#3D5BEE` 级别），代价是浅色主题整体变深；
     * - 或改排版：强调色**填充**上不放 <14sp 的文字（当前首页 hero 的胶囊是 12sp）。
     *
     * 本测试把这个数字**钉住**：一旦有人把令牌改浅导致它跌破 4.33，这条会红；
     * 一旦有人压深到过 4.5，这条会因 `assertEquals` 失败而提醒"该更新记录了"。
     */
    @Test
    fun lightTokenReachesOnlyFourPointThreeThreeOnItsFill() {
        val ratio = contrast(onAccentFor(PocketSoftLight.accentMain), PocketSoftLight.accentMain)
        assertEquals(
            "浅色令牌的前景上限变了（原记录 4.33:1）。若已提到 ≥4.5，请把这个测试改成达标断言",
            4.33,
            ratio,
            0.02,
        )
    }

    @Test
    fun lightAccentGetsDarkForegroundAndDarkAccentGetsLightForeground() {
        val (foregrounds, _, _) = probe()
        assertTrue(
            "白色填充上必须用深色前景",
            requireNotNull(foregrounds[Color.White]).luminance() < 0.5f,
        )
        assertTrue(
            "近黑填充上必须用浅色前景",
            requireNotNull(foregrounds[Color(0xFF1F2937)]).luminance() > 0.5f,
        )
    }

    /**
     * 三个 accent 取色入口必须**跟着令牌走** —— 这是"用户选的强调色进得来"的唯一判据。
     *
     * 若有人把它们改回读 `PocketSoftAccent*` 常量，这条会立刻红。
     */
    @Test
    fun accentProvidersFollowTheTokenNotTheRetiredConstants() {
        val (_, mains, softs) = probe()
        for (accent in accents) {
            assertEquals(
                "accentColor() 必须等于当前令牌强调色",
                accent,
                requireNotNull(mains[accent]),
            )
            val soft = requireNotNull(softs[accent])
            assertTrue(
                "accentSoftColor() 应是强调色的低透明度衬底，实际 alpha=${soft.alpha}",
                soft.alpha in 0.05f..0.35f,
            )
            assertEquals("衬底色相必须与强调色一致", accent.red, soft.red, 0.001f)
        }
        assertTrue(
            "accentColor() 又变回作废常量 PocketSoftAccent 了",
            mains[Color(0xFF2F6B5A)] != PocketSoftAccent,
        )
    }

    /**
     * Phase C 的主要收益：**新令牌不比作废常量更差**。
     *
     * 旧常量 `#7B9FF9` 压白底 **2.57:1**（不达标，也是本次改动的起因）；
     * 令牌 `#4D6BFE` 是 **4.33:1** —— 仍差一点点，所以真正的纪律是
     * "**强调色不当小字号文字用**"。这条断言把"至少不能更差"钉住，
     * 免得以后有人把令牌调回浅色。
     */
    @Test
    fun accentMainIsNotWorseThanTheRetiredConstantOnWhite() {
        val retired = contrast(Color(0xFF7B9FF9), Color.White)
        val current = contrast(PocketSoftLight.accentMain, Color.White)
        assertTrue(
            "新令牌 ${"%.2f".format(current)}:1 不应比作废常量 ${"%.2f".format(retired)}:1 更差",
            current >= retired,
        )
        assertTrue(
            "令牌至少要过 3:1（大字/图标门槛），实际 ${"%.2f".format(current)}:1",
            current >= 3.0,
        )
    }
}
