package com.pocket.xiaoli.ui.proactive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketTypography
import kotlinx.coroutines.delay

/** 「?」按钮的触摸区（本项目 44dp 底线，视觉上只是一枚 16dp 的小图标）。 */
private val TipTriggerSize = 44.dp

/** 气泡与锚点之间的空隙。 */
private val TipGap = 6.dp

/** 说明气泡自动收起的时间：**不**让它一直挂着 —— 它是解释，不是对话框。 */
private const val TIP_AUTO_DISMISS_MS = 8_000L

/**
 * 气泡式说明：一枚「?」，点开在它下方弹出一个带尖角的小气泡。
 *
 * ## 为什么自己写一个（本项目此前**一个** Tooltip/Popup 都没有）
 *
 * 需要解释的东西都是**一句话**（"这一档到底差在哪"），而 `AlertDialog` 会打断整页、
 * 还得点一个"知道了"；Toast 又不能挂在具体某个控件上。带尖角的气泡是唯一
 * 既贴着来源、又不打断的形态。
 *
 * ## 三处刻意的取舍
 *
 * 1. **尖角从右边量**（[BubbleShape] 的 `arrowFromEnd`）：它是挂在行尾的「?」上的，
 *    从起始边量的话文案一长尖角就偏了。
 * 2. **点气泡自己也会收起**：不然用户看完会到处找关闭按钮（这里刻意不放 ✕，
 *    那是主动气泡的语义 —— "这件事需要你处理"）。
 * 3. **8 秒自动收起**：说明气泡挂在窗口层（`Popup`），用户接着滚动页面时它会停在原地，
 *    留着不走就是"浮着一块来历不明的卡片"。
 *
 * @param text 说明正文。**可以长**（与主动气泡的 24 字上限不同）：它能换行、跟着锚点走。
 */
@Composable
fun BubbleTip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.AutoMirrored.Outlined.HelpOutline,
    contentDescription: String = "说明",
    maxWidth: Dp = 240.dp,
    /** 「?」的颜色；null = 用弱化文字色。压在深色卡上时必须传（否则那枚图标看不见）。 */
    tint: Color? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(TIP_AUTO_DISMISS_MS)
            expanded = false
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(TipTriggerSize)
                .clip(CircleShape)
                .clickable { expanded = !expanded }
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        if (expanded) {
            Popup(
                // 顶部对齐 + 内部让出一段空隙：气泡就落在「?」的正下方，
                // 右边缘与锚点对齐（尖角再从右边量固定距离 = 正对图标中心）
                alignment = Alignment.TopEnd,
                onDismissRequest = { expanded = false },
                // focusable：点空白处与返回键都能收起（读屏用户也拿得到焦点）
                properties = PopupProperties(focusable = true),
            ) {
                Column {
                    Spacer(Modifier.height(TipTriggerSize + TipGap))
                    TipBubble(text = text, maxWidth = maxWidth, onDismiss = { expanded = false })
                }
            }
        }
    }
}

/** 说明气泡的**样子**。标 `internal` 是为了能在快照测试里单独渲它（Popup 是独立窗口，拍不到）。 */
@Composable
internal fun TipBubble(text: String, maxWidth: Dp, onDismiss: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val shape = remember { BubbleShape(BubbleArrow.Top, arrowOffset = 16.dp, arrowFromEnd = true) }
    Box(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 12.dp,
                    spread = 0.dp,
                    color = Color.Black.copy(alpha = 0.18f),
                    offset = DpOffset(0.dp, 3.dp),
                ),
            )
            .clip(shape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDivider, shape)
            .clickable(onClick = onDismiss)
            // 尖角那一侧留出它的深度，否则正文会压到三角上
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp + BubbleArrowDepth,
                bottom = 10.dp,
            ),
    ) {
        Text(
            text = text,
            style = PocketTypography.caption,
            color = colors.textSecondary,
            lineHeight = 17.sp,
        )
    }
}
