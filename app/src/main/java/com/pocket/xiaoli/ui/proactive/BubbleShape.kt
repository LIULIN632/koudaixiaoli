package com.pocket.xiaoli.ui.proactive

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** 尖角长在哪条边上：气泡在锚点**上方**时尖角朝下（[Bottom]），在锚点下方时朝上（[Top]）。 */
enum class BubbleArrow { Bottom, Top }

/** 尖角伸出的深度。外形与布局内边距都按它算，**两处必须一致**（少了会压到字，多了会露缝）。 */
internal val BubbleArrowDepth = 7.dp

/** 尖角底边宽度。 */
internal val BubbleArrowBase = 12.dp

/** 气泡圆角。 */
private val BubbleCorner = 14.dp

/**
 * 带尖角的气泡外形。
 *
 * [arrowOffset] 为空时尖角取气泡**自身**在该轴上的中心 —— 挂在底栏正中的气泡必须走这一支：
 * 锚点固定在屏幕中线，而尖角要随气泡宽度走（文案一长一短，用固定 dp 尖角就偏出锚点了）。
 * 传了值（说明气泡贴在某个控件旁边）时按该值放，并夹在两端留出 [BubbleArrowBase] 的余量，
 * 免得尖角跑到圆角外面去。
 *
 * ⚠️ 这是自定义 [Shape]（[Outline.Generic]）——**玻璃不能用它**：
 * backdrop 库的 `lens()` 只认圆角类形状，遇到它直接 `throwUnsupportedSDFException()` 闪退。
 * 参考实现也因此在气泡上关掉了折射。所以气泡走**实底 + 主色柔光**，不做玻璃。
 */
internal class BubbleShape(
    private val arrow: BubbleArrow,
    private val arrowOffset: Dp?,
    /**
     * [arrowOffset] 是从**右边**量的。
     *
     * 说明气泡挂在某一行的右端（"?"图标），尖角要跟着它走；
     * 而从起始边量的话，气泡宽度一变（文案长短）尖角就偏了 —— 从右边量才是稳的。
     */
    private val arrowFromEnd: Boolean = false,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val arrowH = with(density) { BubbleArrowDepth.toPx() }
        val arrowW = with(density) { BubbleArrowBase.toPx() }
        val radius = with(density) { BubbleCorner.toPx() }
        val offsetPx = arrowOffset?.let { with(density) { it.toPx() } }

        // 尖角中心：null = 气泡该轴的中点；`arrowFromEnd` 时从右边量
        val center = (if (arrowFromEnd) size.width - (offsetPx ?: (size.width / 2f)) else offsetPx)
            ?: (size.width / 2f)
        val cx = center.coerceIn(arrowW, (size.width - arrowW).coerceAtLeast(arrowW))

        val path = Path().apply {
            when (arrow) {
                BubbleArrow.Bottom -> {
                    val bodyBottom = size.height - arrowH
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = size.width,
                            bottom = bodyBottom,
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    )
                    moveTo(cx - arrowW / 2, bodyBottom)
                    lineTo(cx, size.height)
                    lineTo(cx + arrowW / 2, bodyBottom)
                    close()
                }

                BubbleArrow.Top -> {
                    val bodyTop = arrowH
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = bodyTop,
                            right = size.width,
                            bottom = size.height,
                            cornerRadius = CornerRadius(radius, radius),
                        )
                    )
                    moveTo(cx - arrowW / 2, bodyTop)
                    lineTo(cx, 0f)
                    lineTo(cx + arrowW / 2, bodyTop)
                    close()
                }
            }
        }
        return Outline.Generic(path)
    }
}
