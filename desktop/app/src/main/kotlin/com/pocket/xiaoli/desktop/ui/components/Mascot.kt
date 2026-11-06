package com.pocket.xiaoli.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 鲤鱼吉祥物（Canvas 手绘）。
 *
 * ## 为什么不用素材图
 *
 * 移动端的鲤鱼是一组 PNG（Lottie 太重）。桌面端要"一眼认得出是鲤鱼"同时保持零素材
 * 依赖 —— 用矢量 Canvas 画：鱼身椭圆 + 尾巴 + 鱼鳍 + 眼睛 + 气泡。`animate=true` 时
 * 尾巴轻微摆动、气泡上浮，用无限动画的相位驱动。
 */
@Composable
fun PocketMascot(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    bodyColor: Color = Color(0xFF4D8DFF),
    bellyColor: Color = Color(0xFFEAF1FF),
) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val phase by if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
            label = "phase",
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0.5f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.52f
        val cy = h * 0.56f
        val bodyW = w * 0.58f
        val bodyH = h * 0.42f

        // 气泡（上浮，循环）
        repeat(3) { index ->
            val p = (phase + index * 0.33f) % 1f
            val r = w * (0.035f + 0.015f * index)
            val bubbleX = w * (0.74f + 0.06f * index)
            val bubbleY = h * (0.72f - p * 0.5f)
            drawCircle(
                color = bodyColor.copy(alpha = 0.35f * (1f - p)),
                radius = r,
                center = Offset(bubbleX, bubbleY),
                style = Stroke(width = w * 0.008f),
            )
        }

        // 尾巴：绕根部旋转 ±8°
        rotate(degrees = (phase - 0.5f) * 16f, pivot = Offset(cx - bodyW * 0.48f, cy)) {
            val tail = Path().apply {
                moveTo(cx - bodyW * 0.46f, cy)
                lineTo(cx - bodyW * 0.82f, cy - bodyH * 0.5f)
                quadraticBezierTo(cx - bodyW * 0.68f, cy, cx - bodyW * 0.82f, cy + bodyH * 0.5f)
                close()
            }
            drawPath(tail, brush = Brush.linearGradient(listOf(bodyColor, bodyColor.copy(alpha = 0.75f))))
        }

        // 鱼身
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(bodyColor, bodyColor.copy(alpha = 0.85f)),
                startY = cy - bodyH * 0.6f,
                endY = cy + bodyH * 0.6f,
            ),
            topLeft = Offset(cx - bodyW * 0.5f, cy - bodyH * 0.5f),
            size = Size(bodyW, bodyH),
        )
        // 肚子
        drawOval(
            color = bellyColor,
            topLeft = Offset(cx - bodyW * 0.34f, cy + bodyH * 0.02f),
            size = Size(bodyW * 0.62f, bodyH * 0.4f),
        )
        // 背鳍
        val fin = Path().apply {
            moveTo(cx - bodyW * 0.18f, cy - bodyH * 0.44f)
            quadraticBezierTo(cx, cy - bodyH * 0.78f, cx + bodyW * 0.16f, cy - bodyH * 0.42f)
            close()
        }
        drawPath(fin, color = bodyColor.copy(alpha = 0.85f))
        // 侧鳍
        val sideFin = Path().apply {
            moveTo(cx + bodyW * 0.02f, cy + bodyH * 0.1f)
            quadraticBezierTo(cx + bodyW * 0.18f, cy + bodyH * 0.34f, cx + bodyW * 0.24f, cy + bodyH * 0.12f)
            close()
        }
        drawPath(sideFin, color = bodyColor.copy(alpha = 0.7f))

        // 眼睛
        val eyeX = cx + bodyW * 0.24f
        val eyeY = cy - bodyH * 0.12f
        drawCircle(Color.White, radius = bodyH * 0.17f, center = Offset(eyeX, eyeY))
        drawCircle(Color(0xFF1E2430), radius = bodyH * 0.10f, center = Offset(eyeX + bodyH * 0.03f, eyeY))
        drawCircle(
            Color.White.copy(alpha = 0.9f),
            radius = bodyH * 0.035f,
            center = Offset(eyeX + bodyH * 0.06f, eyeY - bodyH * 0.04f),
        )
        // 腮红
        drawCircle(
            color = Color(0xFFFF8FA3).copy(alpha = 0.35f),
            radius = bodyH * 0.10f,
            center = Offset(cx + bodyW * 0.40f, cy + bodyH * 0.12f),
        )
    }
}

/** 圆底卡片上放一个吉祥物（首页 hero 卡、鲤鱼页头用）。 */
@Composable
fun MascotBadge(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFDCE8FF), Color(0xFFEFF5FF)))),
        contentAlignment = Alignment.Center,
    ) {
        PocketMascot(Modifier.size(size * 0.86f))
    }
}
