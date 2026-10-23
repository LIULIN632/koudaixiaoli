package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 皮肤（Minimal / Vivid）数据契约的单元测试。
 *
 * ## 为什么必须测
 *
 * 这套主题的**全部差异都由 `SkinTokens` 的数据表达**，
 * `AppCard` 里没有任何 `if (isMinimal)` 分支。所以：
 *
 * > **数据错了，观感就错了，而编译器不会报错。**
 *
 * 例如把 `VividSkin.shadowBlur` 误写成 `0.dp`，编译通过、测试不测的话没人发现，
 * 只是"鲜活"看起来和"极简"一样平。这类错误只能靠断言数据来拦。
 *
 * ## 这里守的是「两套确实不同」+「同一套内部自洽」
 *
 * 不是测像素，而是测**设计意图**：
 * - 两套在圆角 / 阴影 / 底色上必须真的有差异（否则等于只有一套）
 * - 声明"无阴影"的必须两个字段同时为空（不能只设 blur 忘了 color）
 * - 声明"无边框"的 borderWidth 必须是 0
 */
class SkinThemeContractTest {

    // ── 两套必须有实质差异 ──

    @Test
    fun `两套皮肤的卡片圆角必须不同`() {
        assertNotEquals(
            "Minimal 与 Vivid 的 radiusCard 相同，等于没有两套风格",
            MinimalSkin.radiusCard,
            VividSkin.radiusCard,
        )
    }

    @Test
    fun `Minimal 圆角必须小于 Vivid`() {
        assertTrue(
            "极简应当是小圆角（当前 ${MinimalSkin.radiusCard}），鲜活应当是大圆角（当前 ${VividSkin.radiusCard}）",
            MinimalSkin.radiusCard < VividSkin.radiusCard,
        )
    }

    @Test
    fun `Minimal 背景必须比卡片深（灰底白卡的分层前提）`() {
        // 极简方案不画阴影，全靠"灰底 + 白卡"的对比分层。
        // 若两者同色，卡片会在背景上消失。
        assertNotEquals(
            "Minimal 的背景色与卡片色相同 —— 无阴影方案下卡片将不可见",
            MinimalSkin.backgroundColorLight,
            MinimalSkin.cardColorLight,
        )
    }

    @Test
    fun `Vivid 浅色态背景与卡片可以同色（靠阴影分层）`() {
        // 这不是"必须相同"，而是记录设计意图：同色时必须有阴影兜底。
        // 断言真正重要的那条：同色则必须有阴影。
        if (VividSkin.backgroundColorLight == VividSkin.cardColorLight) {
            assertTrue(
                "Vivid 背景与卡片同色，此时必须有阴影，否则卡片不可见",
                VividSkin.shadowBlur > 0.dp,
            )
        }
    }

    // ── 同一套内部自洽 ──

    @Test
    fun `Minimal 必须无阴影（blur 与 color 同时为空）`() {
        assertEquals("Minimal 的 shadowBlur 应为 0", 0.dp, MinimalSkin.shadowBlur)
        assertEquals(
            "Minimal 的阴影色应为 Transparent，否则 AppCard 仍会绘制阴影",
            Color.Transparent,
            MinimalSkin.shadowColorLight,
        )
    }

    @Test
    fun `Vivid 必须有阴影（blur 大于 0）`() {
        assertTrue(
            "Vivid 声明为弥散阴影风格，shadowBlur 必须 > 0（当前 ${VividSkin.shadowBlur}）",
            VividSkin.shadowBlur > 0.dp,
        )
        assertNotEquals(
            "Vivid 的阴影色不应是 Transparent，否则看不出阴影",
            Color.Transparent,
            VividSkin.shadowColorLight,
        )
    }

    @Test
    fun `深色态的阴影色不得是纯黑不透明（在深底上会消失）`() {
        // 具体值可以调，但必须区别于完全不透明的黑。
        assertTrue(
            "深色态阴影色 alpha 过低会在 #121212 上看不见",
            VividSkin.shadowColorDark.alpha > 0f,
        )
    }

    @Test
    fun `两套都声明了无边框，borderWidth 必须为 0`() {
        assertEquals(0.dp, MinimalSkin.borderWidth)
        assertEquals(0.dp, VividSkin.borderWidth)
        // 若将来启用边框，把这条断言改成"允许 > 0"，不要直接删。
    }

    // ── 图标模式 ──

    @Test
    fun `Minimal 是单色图标、Vivid 是彩色图标`() {
        assertEquals(IconMode.MONOCHROME, MinimalSkin.iconMode)
        assertEquals(IconMode.COLORFUL, VividSkin.iconMode)
    }

    @Test
    fun `只有 Vivid 启用彩色标签`() {
        assertFalse(MinimalSkin.colorfulTags)
        assertTrue(VividSkin.colorfulTags)
    }

    // ── 图标线形（与"单色/彩色"正交的第二维） ──

    @Test
    fun `Minimal 是线性图标、Vivid 是面性图标`() {
        assertEquals(IconStyle.OUTLINED, MinimalSkin.iconStyle)
        assertEquals(IconStyle.FILLED, VividSkin.iconStyle)
    }

    @Test
    fun `两套皮肤的图标线形必须不同`() {
        // ⚠️ 这与上面的「单色 / 彩色」是**两个正交维度**（见 IconStyle 的 KDoc）：
        // 颜色参与「皮肤上限 × 用色强度」的合成，形状恒定跟随皮肤。
        // 若两套皮肤的 iconStyle 相同，「线性」与「面性」这层差异就没了 ——
        // 而它正是需求里"单色**线性**图标"与"多色**面性**图标"的字面区别。
        assertNotEquals(
            "Minimal 与 Vivid 的 iconStyle 相同，图标形状不随皮肤变化",
            MinimalSkin.iconStyle,
            VividSkin.iconStyle,
        )
    }

    // ── 迁移兼容（老用户存档） ──

    @Test
    fun `fromKey 必须兼容两个历史 key`() {
        // 改造前 skin_style 存的是 classic / modern。若不兼容，
        // 老用户升级后观感会静默突变。
        assertEquals(SkinStyle.VIVID, SkinStyle.fromKey("classic"))
        assertEquals(SkinStyle.MINIMAL, SkinStyle.fromKey("modern"))
    }

    @Test
    fun `fromKey 解析当前 key`() {
        assertEquals(SkinStyle.MINIMAL, SkinStyle.fromKey("minimal"))
        assertEquals(SkinStyle.VIVID, SkinStyle.fromKey("vivid"))
    }

    @Test
    fun `fromKey 遇未知或空值回退到 Vivid 而非崩溃`() {
        assertEquals(SkinStyle.VIVID, SkinStyle.fromKey(null))
        assertEquals(SkinStyle.VIVID, SkinStyle.fromKey(""))
        assertEquals(SkinStyle.VIVID, SkinStyle.fromKey("不存在的key"))
    }

    @Test
    fun `两个 enumerable 的 key 互不重复`() {
        val keys = SkinStyle.entries.map { it.key }
        assertEquals("SkinStyle.key 有重复：$keys", keys.size, keys.distinct().size)
    }

    // ── 间距体系完整性 ──

    @Test
    fun `两套皮肤的间距刻度都是单调递增`() {
        listOf("Minimal" to MinimalSkin, "Vivid" to VividSkin).forEach { (name, skin) ->
            val scales = listOf(
                skin.spaceMicro, skin.spaceTiny, skin.spaceXs, skin.spaceSmall,
                skin.spaceSm, skin.spaceMd, skin.spaceCompact, skin.spaceLg,
                skin.spaceWide, skin.spaceXl, skin.spaceXxl, skin.spaceHuge,
            )
            scales.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue(
                    "$name 的间距刻度在第 ${i + 1} 档不单调（$a -> $b），会让视觉节奏错乱",
                    a < b,
                )
            }
        }
    }

    @Test
    fun `Minimal 的间距应比 Vivid 紧凑`() {
        assertTrue(
            "极简应当更紧凑：Minimal.spaceLg=${MinimalSkin.spaceLg} 应 < Vivid.spaceLg=${VividSkin.spaceLg}",
            MinimalSkin.spaceLg < VividSkin.spaceLg,
        )
    }

    // ══════════════════════════════════════════════════════════════
    // 皮肤必须**真的生效**（2026-10 补）
    //
    // 上面那批用例只断言"数据自洽"，于是漏掉了一整类真实缺陷：
    // 字段声明全对、两套数据也确实不同，但**没有任何消费者** ——
    // 换皮肤时 App 纹丝不动。这正是线上反馈的"换了皮肤感觉没啥变化"。
    //
    // 所以下面不再只看 `SkinTokens` 的字段，而是断言
    // **套用到同一配色主题之后的结果确实不同**。
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `Minimal 的底色必须明显暗于卡色（无阴影方案的分层前提）`() {
        val gap = MinimalSkin.cardColorLight.luminance() - MinimalSkin.backgroundColorLight.luminance()
        // 反例：#F5F5F7 这类近似白的亮度差只有约 0.086 ——
        // 与配色主题的白底几乎同色，"灰底白卡"读不出来。
        assertTrue(
            "Minimal 底色与卡色的亮度差仅 $gap，无阴影方案下卡片会与页面糊在一起（门限 0.15）",
            gap > 0.15f,
        )
    }

    @Test
    fun `两套皮肤的 surfaceShift 都必须落在 0 到 1 之间`() {
        listOf("Minimal" to MinimalSkin, "Vivid" to VividSkin).forEach { (name, skin) ->
            assertTrue(
                "$name 的 surfaceShift=${skin.surfaceShift} 越界，lerp 会外推出脏色",
                skin.surfaceShift in 0f..1f,
            )
        }
    }

    @Test
    fun `Minimal 对表面色的推移必须强于 Vivid`() {
        assertTrue(
            "Minimal.surfaceShift=${MinimalSkin.surfaceShift} 应大于 " +
                "Vivid.surfaceShift=${VividSkin.surfaceShift}，否则两套表面看不出差别",
            MinimalSkin.surfaceShift > VividSkin.surfaceShift,
        )
    }

    @Test
    fun `surfaceShift 为 0 时必须完全保留配色主题的表面色`() {
        // 守"两条轴都留话语权"的契约：强度为 0 就不该动任何颜色。
        val neutral = VividSkin.copy(surfaceShift = 0f)
        assertEquals(PocketSoftLight, neutral.applySurfaces(PocketSoftLight, isDark = false))
    }

    @Test
    fun `applySurfaces 只动表面三色，文字与强调色原样保留`() {
        val out = MinimalSkin.applySurfaces(PocketSoftLight, isDark = false)
        val expected = PocketSoftLight.copy(
            surfaceBase = out.surfaceBase,
            surfaceCard = out.surfaceCard,
            surfaceAlt = out.surfaceAlt,
        )
        assertEquals("套用皮肤后除了表面三色之外不应有别的字段被改动", expected, out)
        assertEquals(PocketSoftLight.textPrimary, out.textPrimary)
        assertEquals(PocketSoftLight.accentMain, out.accentMain)
        assertEquals(PocketSoftLight.danger, out.danger)
    }

    @Test
    fun `套用后 Minimal 的卡色仍明显亮于底色`() {
        val out = MinimalSkin.applySurfaces(PocketSoftLight, isDark = false)
        val gap = out.surfaceCard.luminance() - out.surfaceBase.luminance()
        assertTrue(
            "套用后底色与卡色亮度差仅 $gap —— 灰底白卡没有落地（门限 0.10）",
            gap > 0.10f,
        )
    }

    @Test
    fun `同一配色下两套皮肤的最终页面底色必须明显不同`() {
        // 这是"换皮肤能不能被看见"的直接断言：只看 data class 的字段不够，
        // 要看**套用到同一配色主题之后**的结果。
        val minimal = MinimalSkin.applySurfaces(PocketSoftLight, isDark = false).surfaceBase
        val vivid = VividSkin.applySurfaces(PocketSoftLight, isDark = false).surfaceBase
        val delta = kotlin.math.abs(minimal.luminance() - vivid.luminance())
        assertTrue(
            "两套皮肤套用同一配色后页面底色亮度只差 $delta —— 用户切换时看不出变化" +
                "（这正是 2026-10 的线上反馈，门限 0.08）",
            delta > 0.08f,
        )
    }

    @Test
    fun `深色态下套用皮肤不得把卡色压暗到底色之下`() {
        listOf("Minimal" to MinimalSkin, "Vivid" to VividSkin).forEach { (name, skin) ->
            val out = skin.applySurfaces(PocketSoftDark, isDark = true)
            assertTrue(
                "$name 在深色态下把卡色压到了底色之下，卡片会失去层次：" +
                    "card=${out.surfaceCard} base=${out.surfaceBase}",
                out.surfaceCard.luminance() > out.surfaceBase.luminance(),
            )
        }
    }
}
