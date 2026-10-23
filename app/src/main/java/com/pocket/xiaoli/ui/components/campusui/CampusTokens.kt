package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.ui.unit.dp

/**
 * CampusUI 设计令牌。
 *
 * 页面不要散落硬编码圆角 / 间距；统一从这里取，后续换视觉只改 tokens。
 */
object CampusTokens {
    val ScreenHorizontal = 16.dp
    val CardRadius = 20.dp
    val CardRadiusSmall = 14.dp
    val CardPadding = 16.dp
    val SectionGap = 14.dp
    val ItemGap = 10.dp

    val IconBoxSmall = 34.dp
    val IconBoxMedium = 42.dp
    val IconBoxLarge = 52.dp

    /** 玻璃卡片基础透明度；低版本设备/低性能模式可统一降级。 */
    const val GlassCardAlpha = 0.86f
    const val GlassHighlightAlpha = 0.72f

    /** API 31+ 才建议开实时模糊；低版本走这个半透明降级。 */
    const val GlassBorderAlpha = 0.55f
}