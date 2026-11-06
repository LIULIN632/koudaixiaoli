package com.pocket.xiaoli.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.ui.theme.LocalDarkMode
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

/**
 * 可点元素的统一交互状态。
 *
 * 桌面端没有 Material 涟漪，「这个能不能点」只能靠悬停变色 + 按下微缩来表达；
 * 各页面自己写一遍就会漏写，所以收在这里给按钮 / 芯片 / 卡片共用。
 */
@Immutable
class PressState(
    val source: MutableInteractionSource,
    val hovered: Boolean,
    val pressed: Boolean,
    val scale: Float,
)

@Composable
fun rememberPressState(enabled: Boolean = true): PressState {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = tween(90),
        label = "press-scale",
    )
    return PressState(source, hovered && enabled, pressed && enabled, scale)
}

/** 悬停 / 按下时的底色：浅色主题往黑压，深色主题往白提，避免"按下去没反应"。 */
@Composable
fun pressSurface(base: Color, hovered: Boolean, pressed: Boolean): Color {
    val fraction = when {
        pressed -> 0.15f
        hovered -> 0.07f
        else -> 0f
    }
    if (fraction == 0f) return base
    val toward = if (LocalDarkMode.current) Color.White else Color.Black
    return Color(
        red = base.red + (toward.red - base.red) * fraction,
        green = base.green + (toward.green - base.green) * fraction,
        blue = base.blue + (toward.blue - base.blue) * fraction,
        alpha = base.alpha,
    )
}

/** 悬停时描出强调色边，按下时加深 —— 灰底按钮光靠底色看不出可点。 */
@Composable
fun pressBorder(pressed: PressState, accent: Color): BorderStroke {
    val width = if (pressed.pressed) 1.6.dp else 1.dp
    val color = when {
        pressed.pressed -> accent
        pressed.hovered -> accent.copy(alpha = 0.55f)
        else -> pocketColors().borderDivider
    }
    return BorderStroke(width, color)
}
