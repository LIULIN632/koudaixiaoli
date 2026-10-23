package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ═══ 海盐蓝主色 (PRD V2: #4D8DFF) ═══
val Accent = Color(0xFF4D8DFF)
val AccentSoft = Color(0xFFEAF4FF)

// ═══ 樱花粉 ═══
val PinkAccent = Color(0xFFF48FB1)

// ═══ 口袋小鲤（马卡龙极简 · Soft UI）═══
//
// ⚠️ 这三个常量**已不是取色真源**（2026-10 Phase C 起）。
// 它们是"收敛前那一套"的字面量，只保留给需要**固定**该色值的场合（对比度探针、导出等）。
// 界面取色请用 `accentColor()` / `accentLightColor()` / `accentSoftColor()` ——
// 它们现在转发到 `LocalPocketXiaoliColors`，因此会跟着**用户选的强调色**走。
val PocketSoftAccent = Color(0xFF7B9FF9)
val PocketSoftAccentLight = Color(0xFFAEC6FF)
val PocketSoftAccentSoft = Color(0xFFDCE7FF)
// ═══ Theme-aware accent providers ═══
//
// ## Phase A → Phase C
//
// Phase A 把 3 × 8 的 `when (LocalThemeType.current)` 塌成单值 —— 但取值**仍来自
// `PocketSoftAccent*` 字面量**，与主题令牌 `accentMain` 并非同一个值
//（`#7B9FF9` vs `#4D6BFE`）。当时的取舍是"只做收敛，不改像素"，两处不一致
// 记为待办（`docs/外观收敛-PhaseA清单.md` §1.5）。
//
// Phase C（本次）把待办结掉：**转发到令牌**。那处不一致有两个可见后果：
//
// 1. **用户选的强调色进不来。** `PocketXiaoliTheme(customAccent = …)` 会把
//    `LocalPocketXiaoliColors.accentMain` 换成用户选的色，而本文件返回常量 ——
//    于是 8 个预设只影响"读令牌的那部分界面"，读本文件的约 120 处一律同一个蓝。
// 2. **对比度。** `#7B9FF9` 压白底只有 **2.57:1**，却被用在 9–13sp 文字上；
//    令牌的 `#4D6BFE` 是 4.33:1。两者都低于正文门槛 4.5:1 ——
//    所以真正的纪律是"**强调色不当小字号文字用**"（见下），令牌只是把最差的一档去掉。
//
// ## 怎么用（Phase C 起）
//
// - 填充 / 边框 / 图标 / 选中态：`accentColor()`
// - **压在强调色填充上的文字或图标**：`onAccentColor()` —— 不要写 `Color.White`，
//   用户可选浅色强调色（`#FFB74D`/`#F48FB1`），白字压上去只有约 1.9:1。
// - 大面积浅色衬底：`accentSoftColor()`

@Composable
fun accentColor(): Color = LocalPocketXiaoliColors.current.accentMain

/**
 * 强调色的**浅色变体**：深色主题下更亮一档，浅色主题下更深一档。
 *
 * 方向跟着背景走（深色底 → 提亮，浅色底 → 压深），而不是固定加白 ——
 * 这样用户换强调色时它跟着换，不会退回一个冷色常量。
 */
@Composable
fun accentLightColor(): Color {
    val accent = LocalPocketXiaoliColors.current.accentMain
    val isDark = LocalDarkMode.current
    return remember(accent, isDark) {
        val target = if (isDark) Color.White else Color.Black
        Color(
            red = accent.red + (target.red - accent.red) * 0.28f,
            green = accent.green + (target.green - accent.green) * 0.28f,
            blue = accent.blue + (target.blue - accent.blue) * 0.28f,
        )
    }
}

/**
 * 强调色的**柔和衬底**：图标衬底、选中行背景这类大面积浅色块。
 *
 * 用低透明度而非固定浅色：旧的固定值 `#DCE7FF` 在用户换成暖色强调色时会变成
 * 刺眼的冷色块；透明度版本永远与强调色同色相。
 */
@Composable
fun accentSoftColor(): Color {
    val accent = LocalPocketXiaoliColors.current.accentMain
    val isDark = LocalDarkMode.current
    return remember(accent, isDark) {
        accent.copy(alpha = if (isDark) 0.22f else 0.14f)
    }
}

/**
 * 压在强调色**填充**上的前景色（文字 / 图标）。
 *
 * ## 为什么必须存在
 *
 * 界面上有多处"强调色填充 + `Color.White`"的写法（首页 hero 卡、课表"今天"按钮、
 * 校准弹窗…）。白色在深色强调色上没问题，但强调色**是用户可选的**：预设里的浅色档
 * （`#FFB74D` / `#F48FB1`）配白字只有约 **1.9:1**，基本读不出。
 *
 * ## ⚠️ 判据是"实测对比度取优"，不是"亮度阈值"
 *
 * 最初这里（以及 `Theme.kt`）用的是 `luminance() > 0.55f` 这种阈值写法。
 * **它在本项目的深色强调色上直接判错**：`PocketSoftDark.accentMain = #8AA4FF`
 * 亮度 0.396（低于阈值 ⇒ 选白字），但白字压它只有 **2.36:1** ——
 * 真正达标的是深色前景 `#1F2937`（8.9:1）。阈值看着合理，却在中间亮度区间系统性判错。
 *
 * 现在改成把两个候选都算一遍、取对比度高的那个。这样**任何**强调色下都恒有
 * ≥4.5:1（由 `AccentTokenTest` 对 9 个色号断言）。`Theme.kt` 用的是同一个函数，
 * 不会再出现"按钮白字、卡片深字"的分裂。
 */
@Composable
fun onAccentColor(): Color {
    val accent = LocalPocketXiaoliColors.current.accentMain
    return remember(accent) { onAccentFor(accent) }
}

/**
 * [onAccentColor] 的**纯函数内核**（无组合依赖，好测也好被 `Theme.kt` 复用）。
 *
 * 返回 [onAccentDark] / [onAccentLight] 中与 [background] 对比度更高的那个。
 *
 * ⚠️ 这是**"取最优"**而不是"达标"：单个色号能取到的最大对比度是它自己的性质。
 * 例如令牌 `#4D6BFE` 的最好情况就是白字 **4.33:1**（深字更差，3.39:1）——
 * 那是这个蓝的物理上限，换前景救不了，只能换填充色或改排版。
 */
fun onAccentFor(background: Color): Color {
    val light = onAccentLight
    val dark = onAccentDark
    return if (contrastRatio(light, background) >= contrastRatio(dark, background)) light else dark
}

/** 压在强调色上的**深色**前景（浅色强调色用）。与 `Theme.kt` 保持一致。 */
val onAccentDark = Color(0xFF1F2937)

/** 压在强调色上的**浅色**前景（深色强调色用）。 */
val onAccentLight = Color.White

/**
 * WCAG 2.1 相对亮度对比度。
 *
 * ⚠️ 与 `androidx.compose.ui.graphics.luminance()` 的关系：后者已是 WCAG 相对亮度，
 * 这里只做 `(亮+0.05)/(暗+0.05)`。**不要在别处再写一份** —— `ThemeContrastTest`
 * 有自己的实现（那是测试的独立性，刻意保留），生产代码只认这一份。
 */
fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

// ═══ 辅助暖色 ═══
val OrangeAccent = Color(0xFFF7B787)

// ═══ Light theme neutral (PRD V2) ═══
val Surface = Color(0xFFF8FAFC)
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceAlt = Color(0xFFF5F5F5)
val CardBorder = Color(0xFFE0E0E0)
val Divider = Color(0xFFEEEEEE)
val TodayBg = Color(0xFFEAF4FF)

val TextPrimary = Color(0xFF1F2937)
val TextSecondary = Color(0xFF8B95A7)
val TextTertiary = Color(0xFF9CA3AF)

val StatusBar = Color(0xFFF8FAFC)

// ═══ Dark theme neutral (PRD V2) ═══
val DarkSurface = Color(0xFF121212)
val DarkSurfaceAlt = Color(0xFF1A1A1A)
val DarkSurfaceCard = Color(0xFF1E1E1E)
val DarkCardBorder = Color(0xFF2B2B2B)
val DarkDivider = Color(0xFF2B2B2B)
val DarkTodayBg = Color(0xFF1A2A4A)
val DarkAccentSoft = Color(0xFF1A2A4A)

val DarkTextPrimary = Color(0xFFE8E8E8)
val DarkTextSecondary = Color(0xFF9E9E9E)
val DarkTextTertiary = Color(0xFF757575)

val DarkStatusBar = Color(0xFF121212)

// ═══ Semantic colors ═══
val SuccessGreen = Color(0xFF4CAF50)

// ═══ Composable theme-aware color providers ═══

@Composable
fun themeSurface(darkMode: String): Color =
    if (darkMode == "dark") DarkSurface else Surface

@Composable
fun themeSurfaceAlt(darkMode: String): Color =
    if (darkMode == "dark") DarkSurfaceAlt else SurfaceAlt

@Composable
fun themeCardBg(darkMode: String): Color =
    if (darkMode == "dark") DarkSurfaceCard else SurfaceCard

@Composable
fun themeCardBorder(darkMode: String): Color =
    if (darkMode == "dark") DarkCardBorder else CardBorder

@Composable
fun themeDivider(darkMode: String): Color =
    if (darkMode == "dark") DarkDivider else Divider

@Composable
fun themeTodayBg(darkMode: String): Color =
    if (darkMode == "dark") DarkTodayBg else TodayBg

@Composable
fun themeAccentSoft(darkMode: String): Color =
    if (darkMode == "dark") DarkAccentSoft else AccentSoft

@Composable
fun themeTextPrimary(darkMode: String): Color =
    if (darkMode == "dark") DarkTextPrimary else TextPrimary

@Composable
fun themeTextSecondary(darkMode: String): Color =
    if (darkMode == "dark") DarkTextSecondary else TextSecondary

@Composable
fun themeTextTertiary(darkMode: String): Color =
    if (darkMode == "dark") DarkTextTertiary else TextTertiary

@Composable
fun themeWhite(darkMode: String): Color =
    if (darkMode == "dark") DarkSurfaceCard else Color.White

@Composable
fun themeStatusBar(darkMode: String): Color =
    if (darkMode == "dark") DarkStatusBar else StatusBar

// ═══ Low-saturation macaron course card colors (light) ═══
val CourseColors = listOf(
    Color(0xFFFFE8E5),  // soft rose
    Color(0xFFDEE8FF),  // soft lavender blue
    Color(0xFFD8F0E2),  // soft sage
    Color(0xFFEDE2F8),  // soft lilac
    Color(0xFFFFEAD6),  // soft peach
    Color(0xFFFFDEE0),  // soft pink
    Color(0xFFD6EDF2),  // soft sky
    Color(0xFFD0EBD8),  // soft mint
    Color(0xFFFFF0D6),  // soft cream
    Color(0xFFE2E2F6),  // soft periwinkle
    Color(0xFFFFD8D6),  // soft coral
    Color(0xFFD2F0D2),  // soft lime
    Color(0xFFFFF8D6),  // soft butter
    Color(0xFFF0DCF6),  // soft orchid
    Color(0xFFD6E6FF)   // soft baby blue
)

// ═══ Low-saturation macaron course card colors (dark) ═══
val CourseColorsDark = listOf(
    Color(0xFF5C3A38), Color(0xFF38405C),
    Color(0xFF385040), Color(0xFF483A50),
    Color(0xFF5C4438), Color(0xFF5C3838),
    Color(0xFF384A5C), Color(0xFF384A40),
    Color(0xFF5C4A38), Color(0xFF42425C),
    Color(0xFF5C383A), Color(0xFF384A38),
    Color(0xFF5C5038), Color(0xFF423A50),
    Color(0xFF384048)
)

// ═══ Course card text colors (readable on light backgrounds) ═══
val CourseTextColors = listOf(
    Color(0xFF8B5A5E), Color(0xFF4A5C82),
    Color(0xFF4A6C56), Color(0xFF584A78),
    Color(0xFF826448), Color(0xFF824E50),
    Color(0xFF486A80), Color(0xFF486A5C),
    Color(0xFF826448), Color(0xFF4E4E7A),
    Color(0xFF824A4C), Color(0xFF3E6A3E),
    Color(0xFF826E40), Color(0xFF6A4680),
    Color(0xFF3E6280)
)

// ═══ Course card text colors (dark mode) ═══
val CourseTextColorsDark = listOf(
    Color(0xFFFFD0CE), Color(0xFFD0DDFF),
    Color(0xFFD0EED8), Color(0xFFDDD0FF),
    Color(0xFFFFE0D0), Color(0xFFFFD0D0),
    Color(0xFFD0ECFF), Color(0xFFD0EED8),
    Color(0xFFFFEED0), Color(0xFFDDDDFF),
    Color(0xFFFFD0D0), Color(0xFFD0FFD0),
    Color(0xFFFFF6D0), Color(0xFFEED0FF),
    Color(0xFFD0E8FF)
)

fun courseBgColor(colorIndex: Int, isDark: Boolean): Color {
    val colors = if (isDark) CourseColorsDark else CourseColors
    return colors[colorIndex % colors.size]
}

fun courseTextColor(colorIndex: Int, isDark: Boolean): Color {
    val colors = if (isDark) CourseTextColorsDark else CourseTextColors
    return colors[colorIndex % colors.size]
}

// ═══ Grid background color palette ═══
// -1 = follow theme (no custom color)
val GridBgPalette = listOf(
    Color(0xFFFAFAFA) to Color(0xFF1A1C1E),  // 极简白
    Color(0xFFFFFDF7) to Color(0xFF1E1C18),  // 奶油白
    Color(0xFFF5F0E8) to Color(0xFF1F1C18),  // 暖米色
    Color(0xFFEEF4FA) to Color(0xFF181C22),  // 淡蓝灰
    Color(0xFFF0F4EE) to Color(0xFF181C1A),  // 淡绿灰
    Color(0xFFFDF2F4) to Color(0xFF1F1A1C),  // 淡粉灰
    Color(0xFFF4F0F8) to Color(0xFF1C1A20),  // 淡紫灰
    Color(0xFFFFF8EB) to Color(0xFF1F1C16),  // 暖蛋黄
    Color(0xFFF0F0F0) to Color(0xFF161616),  // 中性灰
)

fun courseBorderColor(colorIndex: Int, isDark: Boolean): Color {
    val bg = courseBgColor(colorIndex, isDark)
    return bg.copy(
        red = (bg.red * 0.85f).coerceIn(0f, 1f),
        green = (bg.green * 0.85f).coerceIn(0f, 1f),
        blue = (bg.blue * 0.85f).coerceIn(0f, 1f)
    )
}
