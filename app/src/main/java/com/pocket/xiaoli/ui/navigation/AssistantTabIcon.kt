package com.pocket.xiaoli.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.LocalMascotLook
import com.pocket.xiaoli.ui.theme.MascotAvatar
import com.pocket.xiaoli.ui.theme.reduceMotion
import kotlin.math.absoluteValue
import com.pocket.xiaoli.ui.theme.Motion

/**
 * 底栏上的「鲤鱼」这一格：**满色吉祥物**，不是灰色线性图标。
 *
 * 机制照岱宗盒子（`xjtu-toolbox-android`）的 `PidaiNavButton`：
 * 满色形象 + 比邻居大一圈 + 身上一直有微动 + 按下去会弹，刻意不复用
 * `NavigationBarItem` 的灰度图标，让用户一眼看出"这是另一类东西"。别把这一格改回
 * `Icon(tint = textTertiary)`，那它就又只是一枚 tab 图标了。
 *
 * 三处动效，各自的出处都写在下面代码注释里：
 * 1. **选中放大** 0.94 → 1.0（`Motion.enter`，同岱宗 scale 那一档）；
 * 2. **按下去缩到 0.88 再弹回**（`spring(dampingRatio = 0.32f, StiffnessMediumLow)`，抄
 *    `PidaiNavButton` 的 `snapTo(0.9f)` + 同一根弹簧）；
 * 3. **闲着时轻晃**：9s 一轮、±8°，抄 DS Bot `styles.js` 里 `bt-fidget` 的 sway 分支，
 *    相位按形象名取负偏移（等价它的 `--bt-fidget-delay`）。
 *
 * ⚠️ **底下一律不垫任何实心圆/方底，选中态也不画光圈**（2026-10 用户连着报过两次）。
 * 玻璃底栏的"液态"全靠流体折射，任何填充都会在胶囊里戳出第二块色斑：先是
 * `surfaceHighlight` 的圆底，后来换成径向柔光也还是"一个 disc"。选中态现在只靠
 * 放大 + 眨眼 + 底栏自己的胶囊表示，玻璃原样透过去（`112/113` 两张真底栏图守着这条）。
 *
 * 和岱宗**故意不一样**的两点，都是本项目既有约束：
 * 1. 文字标签去掉（同岱宗）：这一格只有形象，名字在鲤鱼页自己的标题里出现。
 * 2. 形象用用户自己选的（`LocalMascotLook`，AI 设置里那 13 个形象 + 12 色），
 *    而不是写死一只机器人 —— 我们的吉祥物可换装，底栏跟着换才是一套。
 *
 * 性能：全栏只有这一格挂动画。眨眼里那条 `rememberInfiniteTransition` 只在选中时开
 * （`MascotAvatar(blinking = selected && blink)`），切走即停；轻晃是第二条，但只在
 * `blink` 打开且系统没开"减少动态效果"时才挂 —— 两条都读 `reduceMotion()`。
 *
 * @param interactionSource 点击反馈的来源。**必须和真正接管点击的那个 `clickable` 共用**
 *   同一个 source，否则按下去这一下收不到（见 `AppNavigation` / `GlassBottomBar` 的调用点）。
 */
@Composable
internal fun AssistantTabIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    /**
     * 是否允许眨眼。快门（`Roborazzi`）里必须传 `false`：无限循环动画永远不进入 idle，
     * 截到的是随机相位，基线会天天红。默认 `true`，真机上只在选中时眨。
     */
    blink: Boolean = true,
) {
    val look = LocalMascotLook.current

    // 选中态**不画任何额外的圈/底**：玻璃底栏自己会用胶囊高亮选中的那一格，
    // 我们再叠一层就是第二块色斑（用户连着两次点到"背景板糊掉玻璃"）。
    // 这一格只靠三件事表示"开着"：形象放大、眨眼、以及底栏自己的胶囊。
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = tween(durationMillis = Motion.enter),
        label = "assistantTabScale",
    )

    // 按下去缩到 0.88 再弹回来：`spring(0.32)` 会过冲一下，就是"戳了一下"的手感。
    // 参数直接抄 PidaiNavButton：snapTo(0.9f) + spring(dampingRatio = 0.32f, StiffnessMediumLow)，
    // 只是这里再压深一点（0.88），因为我们没有岱宗那层"身体自己会被按扁"。
    val bounce = remember { Animatable(1f) }
    val pressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            bounce.snapTo(PRESSED_SCALE)
        } else {
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.32f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    // 闲着时的微动：DS Bot 那套 `bt-fidget` 的 sway 分支，9s 一轮、只转 ±8°。
    // 它**不是**常驻呼吸，是"隔一阵轻轻晃一下"，所以关键帧里 90% 的时间都是 0°。
    // 相位按形象名取负偏移（同 DS 的 `--bt-fidget-delay`），换形象时不会所有格子同步晃。
    // 快门里（blink = false）和 reduceMotion 时不挂，基线才不会随机。
    val fidget: Float = if (blink && !reduceMotion()) {
        val transition = rememberInfiniteTransition(label = "assistantTabFidget")
        val phase = remember(look.shape.name) {
            -((look.shape.name.hashCode().absoluteValue % FIDGET_PERIOD_MS).toInt())
        }
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = FIDGET_PERIOD_MS.toInt()
                    FIDGET_SWAY_DEG at 225
                    -FIDGET_SWAY_DEG * 0.88f at 450
                    FIDGET_SWAY_DEG * 0.38f at 675
                    0f at 900
                },
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(phase, StartOffsetType.FastForward),
            ),
            label = "assistantTabFidgetDeg",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(TAB_ICON_BOX)
            .graphicsLayer {
                val s = scale * bounce.value
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        MascotAvatar(
            size = MASCOT_SIZE,
            look = look,
            modifier = Modifier.graphicsLayer { rotationZ = fidget },
            blinking = selected && blink,
        )
    }
}

/** 形象本身占 40dp（不再往外留光圈的边）。触摸区靠调用点的 `fillMaxHeight()` 撑满整格。 */
private val TAB_ICON_BOX = 40.dp

/** 形象本体 38dp、占位仍是 40dp —— 椭圆 52dp 高、形象旋转 ±8° 后外接框约 43dp，仍留 4dp 边。 */
private val MASCOT_SIZE = 38.dp

/** 按下去的缩放。 */
private const val PRESSED_SCALE = 0.88f

/** 微动一轮的时长，抄 DS 的 `bt-fidget 9s`。 */
private const val FIDGET_PERIOD_MS = 9_000L

/** 微动的最大角度，抄 DS sway 分支的 8°。 */
private const val FIDGET_SWAY_DEG = 8f
