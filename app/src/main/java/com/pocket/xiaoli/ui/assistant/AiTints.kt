package com.pocket.xiaoli.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.currentIconPresentation

/**
 * 鲤鱼页彩色卡片的取色真源。
 *
 * ## 口径照岱宗盒子（2026-11-06 用户：「AI 界面色彩可以看看岱宗怎么做的」）
 *
 * 岱宗 `AgentWelcome` 用的是**一组 Tailwind 500 的饱和色**（蓝/青/琥珀/玫红/紫/绿），
 * 统一以**低透明度**上屏：图标块 `alpha 0.10~0.18`、卡片是 `alpha 0.15` 的渐变、
 * 主视觉是 `primary → violet` 的渐变。颜色多，但它们同属一档饱和度与明度，
 * 所以读起来是一族人，不是大杂烩。
 *
 * 这正是前两版各自踩了一头的中间：
 * - 原来借**课程色板**（为课表格子调的低饱和色）→ 八种糖果色，发脏；
 * - 上一版收成**单色四档** → 干净，但整页一片蓝、没有节奏。
 *
 * 现在按岱宗取：固定六色相、同档饱和度，靠**透明度**分浓淡，
 * 文字再各自压深（浅色）/提亮（深色）一档保证对比度。
 */
private val AiTintHues = listOf(
    Color(0xFF3B82F6), // 蓝
    Color(0xFF8B5CF6), // 紫
    Color(0xFF14B8A6), // 青
    Color(0xFFF59E0B), // 琥珀
    Color(0xFFF43F5E), // 玫红
    Color(0xFF22C55E), // 绿
)

/**
 * 主视觉渐变里的第二色（岱宗的 `primary → violet`）。
 *
 * 只给欢迎页那张大卡用：单主色的渐变在浅色下几乎看不出方向，加一档紫才有"色面"。
 */
internal val AiHeroViolet = Color(0xFF8B5CF6)

/** 取色结果。字段名与用途见 [aiTint]。 */
internal data class AiTint(val background: Color, val foreground: Color)

/**
 * 卡片 / 图标块的配色。
 *
 * @param tone 组内序号（随便传，按 [AiTintHues] 轮转）
 * @return [AiTint.background] 淡色底；深色主题下掺得多一些（暗底上 16% 等于没染）。
 *   [AiTint.foreground] 同色相的文字色 —— 它同时是图标色、描边色、水印色与阴影色，
 *   所以每张卡是**一族**颜色，而不是"彩色底 + 中性黑字"。
 *
 * ⚠️ 浅色主题必须压深一档（`lerp(hue, Black, 0.30)`）：琥珀/青这类 Tailwind 500
 * 原样当文字只有 2:1 上下，压在 16% 的同色淡底上根本读不清。
 */
@Composable
internal fun aiTint(tone: Int): AiTint {
    val colors = LocalPocketXiaoliColors.current
    // 纯净 / 极简皮肤不出彩：中性卡 + 主色字，与它们的"灰底白卡"语法一致
    if (!currentIconPresentation().colorful) {
        return AiTint(colors.surfaceCard, colors.accentMain)
    }
    val isDark = LocalDarkMode.current
    val hue = AiTintHues[Math.floorMod(tone, AiTintHues.size)]
    val background = lerp(colors.surfaceCard, hue, if (isDark) 0.30f else 0.16f)
    val foreground = if (isDark) {
        lerp(hue, Color.White, 0.40f)
    } else {
        lerp(hue, Color.Black, 0.30f)
    }
    return AiTint(background = background, foreground = foreground)
}
