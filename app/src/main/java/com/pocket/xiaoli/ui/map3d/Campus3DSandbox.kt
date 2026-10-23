package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/**
 * 可交互的 3D 沙盘画布：单指拖动旋转，双指缩放，点选楼栋/标注。
 */
@Composable
fun Campus3DSandbox(
    layout: Campus3DLayout,
    camera: Campus3DCamera,
    selected: Campus3DHit?,
    onCameraChange: (Campus3DCamera) -> Unit,
    onSelect: (Campus3DHit?) -> Unit,
    modifier: Modifier = Modifier,
    todayBuildingIds: Set<String> = emptySet(),
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val poiRadiusPx = with(density) { 28.dp.toPx() }
    var viewport by remember { mutableStateOf(Size.Zero) }

    val currentCamera by rememberUpdatedState(camera)
    val currentSelectionHandler by rememberUpdatedState(onSelect)
    val currentCameraHandler by rememberUpdatedState(onCameraChange)
    val currentViewport by rememberUpdatedState(viewport)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics {
                contentDescription = "烟台理工学院 3D 校园沙盘，可拖动旋转、双指缩放、点选楼栋"
            }
            .pointerInput(layout) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val current = currentCamera
                    val nextYaw = normalizeYawDegrees(current.yawDegrees - pan.x * 0.18f)
                    val nextPitch = (current.pitchDegrees + pan.y * 0.14f)
                        .coerceIn(Campus3DCamera.MIN_PITCH, Campus3DCamera.MAX_PITCH)
                    val minDistance = layout.radius * 0.45f
                    val maxDistance = layout.radius * 5.6f
                    val nextDistance = (current.distance / zoom.coerceAtLeast(0.2f))
                        .coerceIn(minDistance, maxDistance)
                    currentCameraHandler(
                        current.copy(
                            yawDegrees = nextYaw,
                            pitchDegrees = nextPitch,
                            distance = nextDistance,
                        )
                    )
                }
            }
            .pointerInput(layout) {
                detectTapGestures { offset ->
                    val viewportSize = currentViewport
                    if (viewportSize.width <= 0f || viewportSize.height <= 0f) return@detectTapGestures
                    currentSelectionHandler(
                        hitTestCampus3D(
                            layout = layout,
                            camera = currentCamera,
                            width = viewportSize.width,
                            height = viewportSize.height,
                            x = offset.x,
                            y = offset.y,
                            poiRadiusPx = poiRadiusPx,
                        )
                    )
                }
            }
    ) {
        drawCampus3D(layout, camera, textMeasurer, selected, todayBuildingIds)
    }
}
