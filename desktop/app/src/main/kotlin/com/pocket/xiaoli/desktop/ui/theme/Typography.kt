package com.pocket.xiaoli.desktop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 字号令牌 —— 7 档，与 Android 端 `PocketTypography` 同值。
 *
 * 24 / 20 / 16 Bold，14 / 12 / 10 Normal，两档字重。层级来自字号差与颜色差。
 */
object PocketTypography {
    val screenTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    val sectionTitle: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val cardTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
    val body: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val caption: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val label: TextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)
    val mono: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        fontFamily = FontFamily.Monospace,
    )
    val numeric: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
}

/** Material3 的 15 个角色全部显式声明（与 Android 端 AppTypography 一致）。 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp, letterSpacing = 0.0.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp, letterSpacing = 0.0.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp, letterSpacing = 0.0.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp, letterSpacing = 0.0.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 28.sp, letterSpacing = 0.0.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = PocketTypography.body,
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

/**
 * 形状令牌 —— 与 Android 端 Vivid 皮肤一致。
 *
 * Android 端由皮肤派生 M3 Shapes：Card 默认读 `medium`，所以卡片圆角统一走 [Shapes]。
 */
val PocketShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** 圆角令牌（写死 Vivid 皮肤的取值）。 */
object Radii {
    val micro = 4.dp
    val chip = 12.dp
    val card = 20.dp
    val large = 28.dp
    val dialog = 32.dp
}

/** 间距令牌（Vivid 皮肤）。 */
object Spacing {
    val micro = 2.dp
    val tiny = 3.dp
    val xs = 4.dp
    val small = 6.dp
    val sm = 8.dp
    val md = 14.dp
    val compact = 16.dp
    val lg = 18.dp
    val wide = 20.dp
    val xl = 22.dp
    val xxl = 26.dp
    val huge = 36.dp
}
