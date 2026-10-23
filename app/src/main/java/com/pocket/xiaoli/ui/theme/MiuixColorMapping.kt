package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 把口袋小鲤现有的 9 个颜色令牌映射成 MIUIX 的配色方案。
 *
 * ## 为什么是"映射"而不是"换成 MIUIX 默认色"
 *
 * MIUIX 的 [Colors] 有 52 个参数，其中大部分是派生色（容器色 / 禁用态 / 前景色）。
 * 全手写不现实，而直接采用 MIUIX 默认配色又会**冲掉本 App 自己的配色**
 *（2026-10 起唯一配色真源是 `PocketSoft*`；此前那 8 套色板已按外观收敛决策删掉）——
 * 那是产品的视觉身份。
 *
 * 折中方案，也是本文件的核心思路：
 * 1. 用 [lightColorScheme] / [darkColorScheme] 取 MIUIX 的**全套 52 色骨架**
 * 2. 只用本 App 的令牌去覆盖**语义上真正对应的那几个**（主色、背景、卡片、文字、分割线、危险色）
 *
 * 这样两边都不牺牲：MIUIX 组件的派生色（禁用态、容器色）全部合理，
 * 而主色调仍是用户选的那一套。
 *
 * ## 映射依据
 *
 * | MIUIX | 本 App 令牌 | 理由 |
 * |---|---|---|
 * | `primary` / `onPrimary` | `accentMain` / 白或黑 | 主色就是强调色 |
 * | `background` / `onBackground` | `surfaceBase` / `textPrimary` | 页面大背景与主文字 |
 * | `surface` / `onSurface` | `surfaceCard` / `textPrimary` | 卡片与卡片上的文字 |
 * | `surfaceContainer` | `surfaceCard` | MIUIX 里卡片容器常与 surface 同色 |
 * | `surfaceVariant` | `surfaceAlt` | 次级背景 |
 * | `onSurfaceSecondary` | `textSecondary` | 次级文字 |
 * | `onSurfaceVariantSummary` | `textTertiary` | 弱化说明文字 |
 * | `outline` / `dividerLine` | `borderDivider` | 描边与分割线 |
 * | `error` | `danger` | 危险/错误色 |
 * | `primaryContainer` | `surfaceHighlight` | 主色柔和背景（今日高亮等） |
 *
 * 未映射的参数（`tertiaryContainer`、`windowDimming`、`slider*` 等）沿用 MIUIX 默认值 ——
 * 它们要么没有对应令牌，要么默认值本身已与本 App 深浅底适配。
 */
fun PocketXiaoliColors.toMiuixColors(isDark: Boolean): Colors {
    val base = if (isDark) darkColorScheme() else lightColorScheme()

    // 前景色判定：调用方已按 isDark 选好对应变体，所以浅色主题一定是亮底
    // （配白字），深色主题是暗底（配深字或白字，按主色亮度二选一）。
    val onAccent = accentForeground(accentMain, isDark)

    return base.copy(
        primary = accentMain,
        onPrimary = onAccent,
        primaryVariant = accentMain,
        onPrimaryVariant = onAccent,
        primaryContainer = surfaceHighlight,
        onPrimaryContainer = textPrimary,

        error = danger,
        onError = Color.White,
        errorContainer = danger.copy(alpha = 0.16f),
        onErrorContainer = danger,

        background = surfaceBase,
        onBackground = textPrimary,

        surface = surfaceCard,
        onSurface = textPrimary,
        surfaceVariant = surfaceAlt,
        onSurfaceSecondary = textSecondary,
        onSurfaceVariantSummary = textTertiary,
        onSurfaceVariantActions = accentMain,
        disabledOnSurface = textTertiary.copy(alpha = 0.38f),

        surfaceContainer = surfaceCard,
        onSurfaceContainer = textPrimary,
        onSurfaceContainerVariant = textSecondary,
        surfaceContainerHigh = surfaceAlt,
        onSurfaceContainerHigh = textPrimary,
        surfaceContainerHighest = surfaceHighlight,
        onSurfaceContainerHighest = textPrimary,

        outline = borderDivider,
        dividerLine = borderDivider,
    )
}

/**
 * 主色之上的前景色（按钮文字/图标）。
 *
 * 浅色主题的底一定是亮的，直接用白字。深色主题里马卡龙色本身偏亮
 * （如 `#FFB3BA`、`#AED581`），配白字对比度不足，按亮度阈值切深色字。
 * 阈值 0.6 是经验值：高于它的主色用深字更清楚。
 *
 * ⚠️ 标 `internal` 而不是 `private`：**主色实底上的前景色只有这一个真源**。
 * 主动气泡（`ui/proactive/ProactiveBubble.kt`）整块都是主色底，它必须用同一个判定，
 * 否则会出现"设置页的按钮用深字、气泡上的字用白字"这种同一主题两种读法。
 */
internal fun accentForeground(accent: Color, isDark: Boolean): Color = when {
    !isDark -> Color.White
    accent.luminance() > 0.6f -> Color(0xFF1A1A1A)
    else -> Color.White
}
