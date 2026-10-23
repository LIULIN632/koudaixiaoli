package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlin.math.roundToInt

private const val MIN_PLAN_SCALE = 1f
private const val MAX_PLAN_SCALE = 3f
private const val PLAN_SCALE_STEP = 0.25f

@Stable
internal class PlanZoomState {
    var scale by mutableFloatStateOf(MIN_PLAN_SCALE)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    private var viewport by mutableStateOf(IntSize.Zero)

    val percent: Int get() = (scale * 100).roundToInt()
    val canZoomIn: Boolean get() = scale < MAX_PLAN_SCALE
    val canZoomOut: Boolean get() = scale > MIN_PLAN_SCALE
    val isZoomed: Boolean get() = scale > MIN_PLAN_SCALE

    internal fun attachViewport(size: IntSize) {
        viewport = size
        offset = clampPlanOffset(offset, scale, size)
    }

    internal fun onGesture(zoomChange: Float, panChange: Offset) {
        val next = (scale * zoomChange).coerceIn(MIN_PLAN_SCALE, MAX_PLAN_SCALE)
        scale = next
        offset = clampPlanOffset(offset + panChange, next, viewport)
    }

    internal fun panBy(delta: Offset) {
        offset = clampPlanOffset(offset + delta, scale, viewport)
    }

    fun zoomIn() = applyScale(scale + PLAN_SCALE_STEP)

    fun zoomOut() = applyScale(scale - PLAN_SCALE_STEP)

    fun reset() = applyScale(MIN_PLAN_SCALE)

    private fun applyScale(target: Float) {
        scale = target.coerceIn(MIN_PLAN_SCALE, MAX_PLAN_SCALE)
        offset = clampPlanOffset(offset, scale, viewport)
    }
}

/** 平移量按「放大后超出视口的一半」夹取，保证平面图不会被拖出可视区。 */
internal fun clampPlanOffset(offset: Offset, scale: Float, viewport: IntSize): Offset {
    if (scale <= MIN_PLAN_SCALE || viewport.width <= 0 || viewport.height <= 0) {
        return Offset.Zero
    }
    val maxX = viewport.width * (scale - 1f) / 2f
    val maxY = viewport.height * (scale - 1f) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY),
    )
}

@Composable
internal fun rememberPlanZoomState(resetKey: Any? = null): PlanZoomState {
    val state = remember { PlanZoomState() }
    LaunchedEffect(resetKey) { state.reset() }
    return state
}

/**
 * 单指在 1x 时不消费事件，交给外层页面滚动；只有双指缩放或放大后的单指拖动才接管手势。
 * 否则把 transformable 直接套在可滚动页面里，会让整页无法上下滑。
 *
 * 放大用真实布局尺寸而不是 graphicsLayer 变换：文字会按比例重新排版，
 * 不会变成放大后的位图，也能避免 Robolectric 快照里缩放层丢字。
 */
@Composable
internal fun ZoomablePlan(
    state: PlanZoomState,
    baseHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (scale: Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(baseHeight)
            .clipToBounds()
            .testTag("floor-plan-viewport")
            .onSizeChanged(state::attachViewport)
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        when {
                            pressed >= 2 -> {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    state.onGesture(zoomChange, panChange)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            state.isZoomed -> {
                                val drag = event.changes.firstOrNull()?.positionChange()
                                if (drag != null && drag != Offset.Zero) {
                                    state.panBy(drag)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val viewportWidth = maxWidth
        val scale = state.scale
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(viewportWidth * scale)
                    .requiredHeight(baseHeight * scale)
                    .offset {
                        IntOffset(state.offset.x.roundToInt(), state.offset.y.roundToInt())
                    },
            ) {
                content(scale)
            }
        }
    }
}

@Composable
internal fun PlanZoomBar(
    state: PlanZoomState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("缩放", color = colors.textTertiary, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        ZoomStepButton(
            label = "−",
            description = "缩小平面图",
            enabled = state.canZoomOut,
            onClick = state::zoomOut,
        )
        Text(
            text = "${state.percent}%",
            modifier = Modifier
                .widthIn(min = 42.dp)
                .semantics { stateDescription = "当前缩放 ${state.percent}%" },
            color = colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        ZoomStepButton(
            label = "＋",
            description = "放大平面图",
            enabled = state.canZoomIn,
            onClick = state::zoomIn,
        )
        Surface(
            onClick = state::reset,
            enabled = state.isZoomed,
            modifier = Modifier.heightIn(min = 44.dp),
            shape = CircleShape,
            color = if (state.isZoomed) {
                colors.accentMain.copy(alpha = 0.16f)
            } else {
                colors.surfaceAlt.copy(alpha = 0.35f)
            },
            border = BorderStroke(1.dp, colors.borderDivider.copy(alpha = 0.6f)),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "复位",
                    color = if (state.isZoomed) colors.accentMain else colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ZoomStepButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = colors.surfaceAlt.copy(alpha = if (enabled) 1f else 0.35f),
        border = BorderStroke(1.dp, colors.borderDivider.copy(alpha = 0.6f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (enabled) colors.textPrimary else colors.textTertiary.copy(alpha = 0.5f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
