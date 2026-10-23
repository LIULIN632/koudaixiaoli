package com.pocket.xiaoli.ui.components.campusui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.model.CampusBuilding
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * CampusUI 校园地图基础画布（V1 示意版）。
 *
 * 先不依赖真实瓦片服务：根据 `CampusMapData.buildings` 画建筑块，支持点击回调。
 * 后续可继续扩展为建筑轮廓 / 道路 / POI / 房间。（曾评估过的 MapLibre 方案已于 2026-10-14 移除：
 * 它两个 ABI 的原生库占 APK 的 72% / 18.77 MB，而这层自绘已经够用。）
 */
@Composable
fun CampusMapCanvas(
    data: CampusMapData,
    modifier: Modifier = Modifier,
    showRoadLayer: Boolean = true,
    showPlaceLayer: Boolean = true,
    showRouteLayer: Boolean = true,
    highlightBuildingName: String? = null,
    height: Dp = 260.dp,
    onBuildingClick: ((CampusBuilding) -> Unit)? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val density = LocalDensity.current
    val textPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 13.sp.toPx() }
            color = colors.textPrimary.toArgb()
        }
    }
    val emptyPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = with(density) { 13.sp.toPx() }
            color = colors.textTertiary.toArgb()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(data, onBuildingClick) {
                if (onBuildingClick == null) return@pointerInput
                detectTapGestures { offset ->
                    val rects = layoutBuildingRects(
                        buildings = data.buildings,
                        canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                    )
                    rects.firstOrNull { it.rect.contains(offset) }?.let {
                        onBuildingClick(it.building)
                    }
                }
            }
    ) {
        if (data.buildings.isEmpty() && data.nodes.isEmpty() && data.roads.isEmpty() && data.places.isEmpty()) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    "暂无校园地图数据",
                    size.width / 2f,
                    size.height / 2f,
                    emptyPaint,
                )
            }
            return@Canvas
        }

        val rects = layoutBuildingRects(data.buildings, size)
        rects.forEachIndexed { index, item ->
            val highlighted = highlightBuildingName != null &&
                item.building.name.equals(highlightBuildingName, ignoreCase = true)
            val fill = when {
                highlighted -> colors.accentMain.copy(alpha = 0.18f)
                index % 2 == 0 -> colors.surfaceHighlight
                else -> colors.surfaceAlt
            }
            drawRoundRect(
                color = fill,
                topLeft = item.rect.topLeft,
                size = item.rect.size,
                cornerRadius = CornerRadius(14.dp.toPx()),
            )
            drawRoundRect(
                color = if (highlighted) colors.accentMain else colors.accentMain.copy(alpha = 0.55f),
                topLeft = item.rect.topLeft,
                size = item.rect.size,
                cornerRadius = CornerRadius(14.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (highlighted) 2.dp else 1.dp).toPx()),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    item.building.name,
                    item.rect.center.x,
                    item.rect.center.y - (textPaint.descent() + textPaint.ascent()) / 2f,
                    textPaint,
                )
            }
        }

        if (showRoadLayer || showPlaceLayer) {
            val bounds = coordinateBounds(data)
            if (bounds != null) {
                val (roads, places) = projectGeo(data, bounds, size)
                if (showRoadLayer) {
                    roads.forEach { points ->
                        if (points.size >= 2) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = colors.accentMain.copy(alpha = 0.28f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }
                if (showPlaceLayer) {
                    places.forEach { position ->
                        drawCircle(
                            color = colors.accentMain.copy(alpha = 0.16f),
                            radius = 8.dp.toPx(),
                            center = position,
                        )
                        drawCircle(
                            color = colors.accentMain.copy(alpha = 0.85f),
                            radius = 3.5.dp.toPx(),
                            center = position,
                        )
                    }
                }
            }
        }

        if (showRouteLayer && data.nodes.isNotEmpty()) {
            val positions = layoutRouteNodePositions(data.nodes, size)
            data.edges.forEach { edge ->
                val from = positions[edge.from] ?: return@forEach
                val to = positions[edge.to] ?: return@forEach
                drawLine(
                    color = colors.accentMain.copy(alpha = 0.35f),
                    start = from,
                    end = to,
                    strokeWidth = 2.dp.toPx(),
                )
            }
            positions.forEach { (_, position) ->
                drawCircle(
                    color = colors.surfaceCard,
                    radius = 5.dp.toPx(),
                    center = position,
                )
                drawCircle(
                    color = colors.accentMain.copy(alpha = 0.85f),
                    radius = 5.dp.toPx(),
                    center = position,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

private data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
)

private fun coordinateBounds(data: CampusMapData): GeoBounds? {
    val coordinates = mutableListOf<Pair<Double, Double>>()
    data.nodes.forEach { node ->
        val lat = node.lat
        val lng = node.lng
        if (lat != null && lng != null) coordinates += lat to lng
    }
    data.places.forEach { place ->
        val lat = place.lat
        val lng = place.lng
        if (lat != null && lng != null) coordinates += lat to lng
    }
    data.roads.forEach { road ->
        road.points.forEach { point -> coordinates += point.lat to point.lng }
    }
    if (coordinates.isEmpty()) return null
    return GeoBounds(
        minLat = coordinates.minOf { it.first },
        maxLat = coordinates.maxOf { it.first },
        minLng = coordinates.minOf { it.second },
        maxLng = coordinates.maxOf { it.second },
    )
}

private fun projectGeo(
    data: CampusMapData,
    bounds: GeoBounds,
    canvasSize: Size,
): Pair<List<List<Offset>>, List<Offset>> {
    val latSpan = (bounds.maxLat - bounds.minLat).takeIf { it > 0.0 } ?: 1.0
    val lngSpan = (bounds.maxLng - bounds.minLng).takeIf { it > 0.0 } ?: 1.0
    val padding = minOf(canvasSize.width, canvasSize.height) * 0.12f

    fun project(lat: Double, lng: Double): Offset {
        val x = padding + (((lng - bounds.minLng) / lngSpan).toFloat() * (canvasSize.width - padding * 2))
        val y = canvasSize.height - padding -
            (((lat - bounds.minLat) / latSpan).toFloat() * (canvasSize.height - padding * 2))
        return Offset(x, y)
    }

    val roads = data.roads.map { road -> road.points.map { project(it.lat, it.lng) } }
    val places = data.places.mapNotNull { place ->
        val lat = place.lat
        val lng = place.lng
        if (lat != null && lng != null) project(lat, lng) else null
    }
    return roads to places
}

private fun layoutRouteNodePositions(
    nodes: List<CampusNode>,
    canvasSize: Size,
): Map<String, Offset> {
    if (nodes.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) return emptyMap()

    val withCoordinates = nodes.filter { it.lat != null && it.lng != null }
    if (withCoordinates.size == nodes.size && nodes.size >= 2) {
        val minLat = withCoordinates.minOf { it.lat!! }
        val maxLat = withCoordinates.maxOf { it.lat!! }
        val minLng = withCoordinates.minOf { it.lng!! }
        val maxLng = withCoordinates.maxOf { it.lng!! }
        val latSpan = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        val lngSpan = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        val padding = minOf(canvasSize.width, canvasSize.height) * 0.12f
        return nodes.associate { node ->
            val x = padding + (((node.lng!! - minLng) / lngSpan).toFloat() * (canvasSize.width - padding * 2))
            val y = canvasSize.height - padding -
                (((node.lat!! - minLat) / latSpan).toFloat() * (canvasSize.height - padding * 2))
            node.id to Offset(x, y)
        }
    }

    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    val radiusX = canvasSize.width * 0.38f
    val radiusY = canvasSize.height * 0.36f
    return nodes.mapIndexed { index, node ->
        val angle = 2.0 * PI * index / nodes.size
        node.id to Offset(
            x = center.x + (cos(angle) * radiusX).toFloat(),
            y = center.y + (sin(angle) * radiusY).toFloat(),
        )
    }.toMap()
}

private data class BuildingRect(
    val building: CampusBuilding,
    val rect: Rect,
)

private fun layoutBuildingRects(
    buildings: List<CampusBuilding>,
    canvasSize: Size,
): List<BuildingRect> {
    if (buildings.isEmpty() || canvasSize.width <= 0f || canvasSize.height <= 0f) return emptyList()
    val columns = when {
        buildings.size <= 1 -> 1
        buildings.size <= 4 -> 2
        else -> 3
    }
    val rows = ceil(buildings.size / columns.toFloat()).toInt().coerceAtLeast(1)
    val cellWidth = canvasSize.width / columns
    val cellHeight = (canvasSize.height / rows).coerceAtMost(cellWidth * 0.78f)
    val gap = canvasSize.width * 0.02f

    return buildings.mapIndexed { index, building ->
        val row = index / columns
        val col = index % columns
        val left = col * cellWidth + gap
        val top = row * cellHeight + gap
        BuildingRect(
            building = building,
            rect = Rect(
                left = left,
                top = top,
                right = left + cellWidth - gap * 2,
                bottom = top + cellHeight - gap * 2,
            ),
        )
    }
}