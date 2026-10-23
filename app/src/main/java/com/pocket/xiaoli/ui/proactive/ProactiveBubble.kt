package com.pocket.xiaoli.ui.proactive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.proactive.ProactiveMessage
import com.pocket.xiaoli.data.proactive.ProactiveRules
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.accentForeground
import kotlinx.coroutines.delay

/**
 * 右上角关闭键的**触摸区**边长。
 *
 * ⚠️ 参考实现这里是 20dp —— 而本项目 44dp 是无障碍底线
 *（`AssistantAccessibilityTest` 明确按这条断言）。照着搬会做出一个"看得见、点不准"的叉，
 * 而且**截图完全看不出来**。所以视觉上仍是一枚小叉，触摸区撑到 44dp。
 */
private val CloseTouchSize = 44.dp

/**
 * 主动气泡：从底栏正中的鲤鱼头顶冒出来的一句话。
 *
 * ## 外形与动效（照搬参考实现，两处偏差都是有意的）
 *
 * - 从**尖角那一点弹出来**：初始缩放 0.35 再用欠阻尼 spring 回弹，观感是"从鲤鱼头顶长出来"；
 *   tween 做不出这个过冲，只会像一张图渐显。淡入比缩放收尾快得多，否则半透明的放大过程显得糊。
 * - 收回去也回到同一点，不原地淡出。
 * - **不做玻璃**：尖角是自定义 [androidx.compose.ui.graphics.Shape]，
 *   backdrop 的 `lens()` 遇到它会 `throwUnsupportedSDFException()` 闪退
 *   （参考实现同样因为这条把气泡的折射关掉了）。这里用主色实底 + 同色柔光投影。
 *
 * ## 为什么 visible 不跟着 message 走
 *
 * 它一度 `remember(message.id, message.text)`：换一条文案时先被重置成 false 再被下面的
 * effect 置回 true，而这两次赋值发生在**同一帧**内 —— `AnimatedVisibility` 根本觉察不到变化，
 * 于是连点两下时气泡只是原地换字，一点动静都没有。现在拆成两层：
 * 外层 [AnimatedVisibility] 管"气泡在不在"，内层 [AnimatedContent] 管"一条换一条"。
 */
@Composable
fun ProactiveBubbleView(
    message: ProactiveMessage,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    /** 尖角距起始边的距离；null = 气泡自身水平中心（挂在底栏正中时走这一支）。 */
    arrowFromStart: Dp? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message.id, message.text) {
        visible = true
        delay(ProactiveRules.AUTO_DISMISS_MS)
        visible = false
        // 等退场动画走完再通知宿主清空，否则气泡会"啪"地消失
        delay(200)
        onTimeout()
    }

    // 缩放锚点跟着尖角走：尖角在哪，气泡就从哪长出来
    val pivot = if (arrowFromStart == null) TransformOrigin(0.5f, 1f) else TransformOrigin(0.12f, 1f)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(90)) +
            scaleIn(
                initialScale = 0.35f,
                transformOrigin = pivot,
                animationSpec = spring(
                    dampingRatio = 0.52f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        exit = fadeOut(animationSpec = tween(140)) +
            scaleOut(
                targetScale = 0.6f,
                transformOrigin = pivot,
                animationSpec = tween(160),
            ),
        modifier = modifier.wrapContentWidth(),
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                // 新的一条从尖角弹出来，旧的一条同时缩回尖角：看着就是"它又说了一句"，
                // 而不是"文字被替换了"
                (
                    fadeIn(animationSpec = tween(90)) +
                        scaleIn(
                            initialScale = 0.5f,
                            transformOrigin = pivot,
                            animationSpec = spring(
                                dampingRatio = 0.5f,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                    ).togetherWith(
                    fadeOut(animationSpec = tween(90)) +
                        scaleOut(
                            targetScale = 0.7f,
                            transformOrigin = pivot,
                            animationSpec = tween(110),
                        )
                    // 尺寸变化不裁剪：长短不一的两条交叉淡入淡出时不该被对方的框切掉
                ) using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.BottomCenter,
            label = "proactiveBubbleSwap",
        ) { shown ->
            ProactiveBubbleContent(
                message = shown,
                onOpen = onOpen,
                onDismiss = onDismiss,
                maxWidth = maxWidth,
                arrowFromStart = arrowFromStart,
            )
        }
    }
}

/**
 * 气泡的**样子**（不含出现/消失动画与 8 秒计时）。
 *
 * 拆出来的唯一理由是**可测**：动画与计时都是时间相关的，直接对 [ProactiveBubbleView]
 * 拍基准图会拍到"某一帧"，基准图会自己变红（本项目把"无限动画"列为三类不确定性之一）。
 * 有了这个纯状态驱动的入口，快照测试渲它就够了；动画与计时那部分由
 * `ProactiveRulesTest` 钉住常量、由人工验收看观感。
 */
@Composable
internal fun ProactiveBubbleContent(
    message: ProactiveMessage,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    arrowFromStart: Dp? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val accent = colors.accentMain
    val onAccent = accentForeground(accent, isDark)
    val shape = remember(arrowFromStart) { BubbleShape(BubbleArrow.Bottom, arrowFromStart) }

    Row(
        modifier = modifier
            .wrapContentWidth()
            .widthIn(max = maxWidth)
            .semantics { contentDescription = "鲤鱼提醒：${message.text}" }
            // 气泡是浮在页面上的，给它一圈和自己同色的柔影：像主色的光晕，
            // 不是灰黑的投影。写在 clip 前面，不然影子会被裁掉。
            .dropShadow(
                shape = shape,
                shadow = Shadow(
                    radius = 14.dp,
                    spread = 0.dp,
                    color = accent.copy(alpha = if (isDark) 0.45f else 0.30f),
                    offset = DpOffset(0.dp, 4.dp),
                ),
            )
            .clip(shape)
            .background(accent)
            .clickable(onClick = onOpen)
            // 尖角那一侧要多留出它的伸出深度，否则文字会压到三角上
            .padding(
                start = 14.dp,
                end = 4.dp,
                top = 0.dp,
                bottom = BubbleArrowDepth,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message.text,
            style = PocketTypography.body,
            fontWeight = FontWeight.Medium,
            color = onAccent,
            maxLines = if (message.fullReveal) 2 else 1,
            overflow = if (message.fullReveal) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(CloseTouchSize)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭提醒" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = onAccent.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 气泡的**产生位**与**展示位**不在同一棵子树：文案要靠课表、考试、成绩、余额算出来，
 * 而展示位挂在底栏上方（导航层）。两边之间只差一个 message，为它把首页那一坨状态
 * 提升到导航层不划算 —— 用进程内单例中转（本项目已有 `NetworkLogStore` 这类先例）。
 */
object ProactiveBubbleHost {

    var message by mutableStateOf<ProactiveMessage?>(null)

    /**
     * 自动冒泡的抑制开关：用户正待在鲤鱼页时置 true。
     *
     * 人都已经在跟它聊天了，再让它从底栏探出头说句不相干的话，既遮挡输入框也很怪。
     * **只挡自动那一路** —— 用户主动戳它照常回应。
     */
    var autoSuppressed by mutableStateOf(false)

    /**
     * 待在鲤鱼页时戳它，话不从底栏冒泡（会挡住输入框），而是交给**空态欢迎页**
     * 的那句问候：首屏读这一句替换它的副标题。
     *
     * 这样首屏问候与底栏气泡不会同时各说一句。
     */
    var heroLine by mutableStateOf<String?>(null)

    fun clear() {
        message = null
    }
}
