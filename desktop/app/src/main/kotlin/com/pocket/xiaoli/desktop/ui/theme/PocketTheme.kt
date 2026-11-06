package com.pocket.xiaoli.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val PocketSoftLightScheme = lightColorScheme(
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E8FF),
    onTertiaryContainer = Color(0xFF2E1065),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCFD4DC),
    outlineVariant = Color(0xFFEEF0F3),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val PocketSoftDarkScheme = darkColorScheme(
    primary = Color(0xFF8AA4FF),
    onPrimary = Color(0xFF0B1220),
    primaryContainer = Color(0xFF26324F),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF9AA4B2),
    onSecondary = Color(0xFF0B1220),
    secondaryContainer = Color(0xFF2A2F38),
    onSecondaryContainer = Color(0xFFECEDEF),
    tertiary = Color(0xFFB99AFF),
    onTertiary = Color(0xFF160B2E),
    tertiaryContainer = Color(0xFF3B2A63),
    onTertiaryContainer = Color(0xFFF0E8FF),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFECEDEF),
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF24272E),
    onSurfaceVariant = Color(0xFFB4B8C0),
    outline = Color(0xFF30343B),
    outlineVariant = Color(0xFF24272E),
    error = Color(0xFFF87171),
    onError = Color(0xFF2B0B0B),
    errorContainer = Color(0xFF4A2020),
    onErrorContainer = Color(0xFFFEE2E2),
)

/**
 * Vivid 皮肤对表面色的推移（移植自 Android 端 `SkinTokens.applySurfaces`）。
 *
 * 皮肤只推移"底色与卡色的关系"，不换色相。`surfaceShift` 是混合强度。
 */
private fun applyVividSurfaces(theme: PocketXiaoliColors, isDark: Boolean): PocketXiaoliColors {
    val baseTarget = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val cardTarget = if (isDark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
    val altTarget = lerp(cardTarget, baseTarget, 0.5f)
    val strength = 0.35f
    fun toward(from: Color, to: Color) = lerp(from, to, strength)
    return theme.copy(
        surfaceBase = toward(theme.surfaceBase, baseTarget),
        surfaceCard = toward(theme.surfaceCard, cardTarget),
        surfaceAlt = toward(theme.surfaceAlt, altTarget),
    )
}

/**
 * 主题入口。与 Android 端同名同责：中性底 + 单一强调色 + 少量状态色。
 *
 * @param customAccent 用户选的强调色；为 null 时用配色令牌自带的主色。
 */
@Composable
fun PocketXiaoliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customAccent: Color? = null,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) PocketSoftDark else PocketSoftLight
    val skinned = remember(baseColors, darkTheme) { applyVividSurfaces(baseColors, darkTheme) }
    val finalColors = if (customAccent != null) {
        skinned.copy(accentMain = customAccent, surfaceHighlight = customAccent.copy(alpha = 0.14f))
    } else {
        skinned
    }

    val baseScheme = if (darkTheme) PocketSoftDarkScheme else PocketSoftLightScheme
    val skinnedScheme = baseScheme.copy(
        surface = finalColors.surfaceCard,
        background = finalColors.surfaceBase,
        surfaceVariant = finalColors.surfaceAlt,
        onSurface = finalColors.textPrimary,
        onBackground = finalColors.textPrimary,
        onSurfaceVariant = finalColors.textSecondary,
        outlineVariant = finalColors.borderDivider,
        surfaceContainer = finalColors.surfaceCard,
        surfaceContainerHigh = finalColors.surfaceAlt,
        surfaceContainerLow = finalColors.surfaceBase,
    )
    val accentScheme = if (customAccent != null) {
        val onAccent = onAccentFor(customAccent)
        skinnedScheme.copy(
            primary = customAccent,
            onPrimary = onAccent,
            primaryContainer = customAccent.copy(alpha = 0.18f),
            onPrimaryContainer = onAccent,
            secondary = customAccent,
            onSecondary = onAccent,
        )
    } else {
        skinnedScheme
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalPocketXiaoliColors provides finalColors,
    ) {
        MaterialTheme(
            colorScheme = accentScheme,
            typography = AppTypography,
            shapes = PocketShapes,
            content = content,
        )
    }
}

/** 便捷读取：当前配色令牌。 */
@Composable
fun pocketColors(): PocketXiaoliColors = LocalPocketXiaoliColors.current
