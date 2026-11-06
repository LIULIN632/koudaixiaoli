package com.pocket.xiaoli.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// ═══ 品牌主色（海盐蓝 PRD V2）═══
val Accent = Color(0xFF4D8DFF)
val AccentSoft = Color(0xFFEAF4FF)

// ═══ 口袋小鲤（马卡龙极简 · Soft UI）—— 与 Android 端 DesignTokens.kt 同值 ═══
val PocketSoftAccent = Color(0xFF7B9FF9)
val PocketSoftAccentLight = Color(0xFFAEC6FF)
val PocketSoftAccentSoft = Color(0xFFDCE7FF)

@Composable
fun accentColor(): Color = LocalPocketXiaoliColors.current.accentMain

@Composable
fun accentSoftColor(): Color {
    val accent = LocalPocketXiaoliColors.current.accentMain
    val isDark = LocalDarkMode.current
    return accent.copy(alpha = if (isDark) 0.22f else 0.14f)
}

@Composable
fun onAccentColor(): Color {
    val accent = LocalPocketXiaoliColors.current.accentMain
    return onAccentFor(accent)
}

val onAccentDark = Color(0xFF1F2937)
val onAccentLight = Color.White

fun onAccentFor(background: Color): Color {
    return if (contrastRatio(onAccentLight, background) >= contrastRatio(onAccentDark, background)) {
        onAccentLight
    } else {
        onAccentDark
    }
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

// ═══ 语义状态色（按明暗分档，取值与 Android 端一致）═══
private val SuccessOnLight = Color(0xFF2E7D32)
private val WarningOnLight = Color(0xFFB45309)
private val InfoOnLight = Color(0xFF1565C0)
private val SuccessOnDark = Color(0xFF4CAF50)
private val WarningOnDark = Color(0xFFFF9800)
private val InfoOnDark = Color(0xFF2196F3)

// 金属金（总进度专用）
val MetallicGoldStart = Color(0xFFFFD700)
val MetallicGoldEnd = Color(0xFFF9A825)
val MetallicGoldHighlight = Color(0xFFFFFDF0)

val LocalDarkMode = staticCompositionLocalOf { false }

@Immutable
data class PocketXiaoliColors(
    val surfaceBase: Color,
    val surfaceCard: Color,
    val surfaceAlt: Color,
    val surfaceHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val borderDivider: Color,
    val danger: Color,
    val accentMain: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val progressMaster: Color = MetallicGoldEnd,
)

val PocketSoftLight = PocketXiaoliColors(
    surfaceBase = Color(0xFFF7F8FA),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F3F5),
    surfaceHighlight = Color(0xFFEEF2FF),
    textPrimary = Color(0xFF1F2937),
    textSecondary = Color(0xFF4B5563),
    textTertiary = Color(0xFF636B7A),
    borderDivider = Color(0xFFE5E7EB),
    danger = Color(0xFFB91C1C),
    accentMain = Color(0xFF4D6BFE),
    success = SuccessOnLight,
    warning = WarningOnLight,
    info = InfoOnLight,
)

val PocketSoftDark = PocketXiaoliColors(
    surfaceBase = Color(0xFF0F1115),
    surfaceCard = Color(0xFF171A20),
    surfaceAlt = Color(0xFF24272E),
    surfaceHighlight = Color(0xFF26324F),
    textPrimary = Color(0xFFECEDEF),
    textSecondary = Color(0xFFB4B8C0),
    textTertiary = Color(0xFF8A9099),
    borderDivider = Color(0xFF30343B),
    danger = Color(0xFFF87171),
    accentMain = Color(0xFF8AA4FF),
    success = SuccessOnDark,
    warning = WarningOnDark,
    info = InfoOnDark,
)

val LocalPocketXiaoliColors = staticCompositionLocalOf { PocketSoftLight }

// ═══ 低饱和马卡龙课程卡片配色（与 Android 端 Color.kt 完全一致）═══
val CourseColors = listOf(
    Color(0xFFFFE8E5), // soft rose
    Color(0xFFDEE8FF), // soft lavender blue
    Color(0xFFD8F0E2), // soft sage
    Color(0xFFEDE2F8), // soft lilac
    Color(0xFFFFEAD6), // soft peach
    Color(0xFFFFDEE0), // soft pink
    Color(0xFFD6EDF2), // soft sky
    Color(0xFFD0EBD8), // soft mint
    Color(0xFFFFF0D6), // soft cream
    Color(0xFFE2E2F6), // soft periwinkle
    Color(0xFFFFD8D6), // soft coral
    Color(0xFFD2F0D2), // soft lime
    Color(0xFFFFF8D6), // soft butter
    Color(0xFFF0DCF6), // soft orchid
    Color(0xFFD6E6FF), // soft baby blue
)

val CourseColorsDark = listOf(
    Color(0xFF5C3A38), Color(0xFF38405C),
    Color(0xFF385040), Color(0xFF483A50),
    Color(0xFF5C4438), Color(0xFF5C3838),
    Color(0xFF384A5C), Color(0xFF384A40),
    Color(0xFF5C4A38), Color(0xFF42425C),
    Color(0xFF5C383A), Color(0xFF384A38),
    Color(0xFF5C5038), Color(0xFF423A50),
    Color(0xFF384048),
)

val CourseTextColors = listOf(
    Color(0xFF8B5A5E), Color(0xFF4A5C82),
    Color(0xFF4A6C56), Color(0xFF584A78),
    Color(0xFF826448), Color(0xFF824E50),
    Color(0xFF486A80), Color(0xFF486A5C),
    Color(0xFF826448), Color(0xFF4E4E7A),
    Color(0xFF824A4C), Color(0xFF3E6A3E),
    Color(0xFF826E40), Color(0xFF6A4680),
    Color(0xFF3E6280),
)

val CourseTextColorsDark = listOf(
    Color(0xFFFFD0CE), Color(0xFFD0DDFF),
    Color(0xFFD0EED8), Color(0xFFDDD0FF),
    Color(0xFFFFE0D0), Color(0xFFFFD0D0),
    Color(0xFFD0ECFF), Color(0xFFD0EED8),
    Color(0xFFFFEED0), Color(0xFFDDDDFF),
    Color(0xFFFFD0D0), Color(0xFFD0FFD0),
    Color(0xFFFFF6D0), Color(0xFFEED0FF),
    Color(0xFFD0E8FF),
)

/** 课程块底色；[colorIndex] 会被取模，越界不会崩。 */
fun courseBgColor(colorIndex: Int, isDark: Boolean): Color {
    val colors = if (isDark) CourseColorsDark else CourseColors
    return colors[Math.floorMod(colorIndex, colors.size)]
}

fun courseTextColor(colorIndex: Int, isDark: Boolean): Color {
    val colors = if (isDark) CourseTextColorsDark else CourseTextColors
    return colors[Math.floorMod(colorIndex, colors.size)]
}

fun courseBorderColor(colorIndex: Int, isDark: Boolean): Color {
    val bg = courseBgColor(colorIndex, isDark)
    return bg.copy(
        red = (bg.red * 0.85f).coerceIn(0f, 1f),
        green = (bg.green * 0.85f).coerceIn(0f, 1f),
        blue = (bg.blue * 0.85f).coerceIn(0f, 1f),
    )
}

/** 课表网格底色预设（-1 = 跟随主题）。 */
val GridBgPalette = listOf(
    Color(0xFFFAFAFA) to Color(0xFF1A1C1E),
    Color(0xFFFFFDF7) to Color(0xFF1E1C18),
    Color(0xFFF5F0E8) to Color(0xFF1F1C18),
    Color(0xFFEEF4FA) to Color(0xFF181C22),
    Color(0xFFF0F4EE) to Color(0xFF181C1A),
    Color(0xFFFDF2F4) to Color(0xFF1F1A1C),
    Color(0xFFF4F0F8) to Color(0xFF1C1A20),
    Color(0xFFFFF8EB) to Color(0xFF1F1C16),
    Color(0xFFF0F0F0) to Color(0xFF161616),
)

// ═══ 第三方品牌事实色 ═══
object BrandColors {
    val Taobao = Color(0xFFFF6A00)
    val Pinduoduo = Color(0xFFE02E24)
    val Slate = Color(0xFF8B95A8)
}
