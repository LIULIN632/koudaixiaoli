package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawGreenAreas(layout: Campus3DLayout, projection: Campus3DProjection) {
    layout.green.forEachIndexed { index, area ->
        val path = ringToPath(projection, area.ring, 0.3f) ?: return@forEachIndexed
        val base = when {
            area.kind == "forest" -> Palette.LawnDark
            index % 3 == 0 -> Palette.LawnAlt
            else -> Palette.Lawn
        }
        drawPath(path, base)
        if (abs(signedArea(area.ring)) > 3200f) {
            val inner = scaleTowards(area.ring, area.centroid, 0.52f)
            ringToPath(projection, inner, 0.35f)?.let {
                drawPath(it, Palette.LawnDark.copy(alpha = 0.28f))
            }
        }
        drawPath(path, Palette.LawnEdge.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
    }
}

internal fun DrawScope.drawPitches(
    layout: Campus3DLayout,
    projection: Campus3DProjection,
    selected: Campus3DHit?,
) {
    layout.pitches.forEach { pitch ->
        val path = ringToPath(projection, pitch.ring, 0.45f) ?: return@forEach
        val fill = when {
            pitch.kind == "track" || pitch.sport == "running" -> Palette.Track
            pitch.sport == "soccer" -> Palette.Soccer
            pitch.sport == "basketball" -> Palette.Basketball
            pitch.sport == "tennis" -> Palette.Tennis
            else -> Palette.LawnAlt
        }
        drawPath(path, fill)

        if (pitch.kind == "track" || pitch.sport == "running") {
            val infield = scaleTowards(pitch.ring, pitch.centroid, 0.68f)
            ringToPath(projection, infield, 0.5f)?.let { innerPath ->
                drawPath(innerPath, Palette.TrackInner)
                drawPath(innerPath, Palette.CourtLine.copy(alpha = 0.35f), style = strokeStyle(1.1f))
            }
            val lane = scaleTowards(pitch.ring, pitch.centroid, 0.84f)
            ringToPath(projection, lane, 0.5f)?.let {
                drawPath(it, Palette.CourtLine.copy(alpha = 0.18f), style = strokeStyle(1f))
            }
        }

        drawPitchMarkings(path, pitch)

        drawPath(path, Palette.CourtLine.copy(alpha = 0.32f), style = strokeStyle(1.2f))
        if (selected is Campus3DHit.Pitch && selected.shape.id == pitch.id) {
            drawPath(path, Palette.Selection.copy(alpha = 0.22f), style = strokeStyle(9f))
            drawPath(path, Palette.Selection, style = strokeStyle(2.2f))
        }
    }
}

private fun DrawScope.drawPitchMarkings(path: androidx.compose.ui.graphics.Path, pitch: Campus3DPitchShape) {
    val rect = path.getBounds()
    if (rect.width < 10f || rect.height < 10f) return
    val lineWidth = max(1f, min(rect.width, rect.height) * 0.008f).coerceAtMost(2.2f)
    clipPath(path) {
        val horizontalLongAxis = rect.width >= rect.height
        if (horizontalLongAxis) {
            drawLine(
                color = Palette.CourtLine.copy(alpha = 0.55f),
                start = Offset(rect.center.x, rect.top),
                end = Offset(rect.center.x, rect.bottom),
                strokeWidth = lineWidth,
            )
        } else {
            drawLine(
                color = Palette.CourtLine.copy(alpha = 0.55f),
                start = Offset(rect.left, rect.center.y),
                end = Offset(rect.right, rect.center.y),
                strokeWidth = lineWidth,
            )
        }
        drawCircle(
            color = Palette.CourtLine.copy(alpha = 0.5f),
            radius = min(rect.width, rect.height) * 0.16f,
            center = rect.center,
            style = Stroke(width = lineWidth),
        )
        if (pitch.sport == "basketball") {
            val keyWidth = rect.width * 0.18f
            drawRect(
                color = Palette.CourtLine.copy(alpha = 0.38f),
                topLeft = Offset(rect.left, rect.center.y - rect.height * 0.16f),
                size = androidx.compose.ui.geometry.Size(keyWidth, rect.height * 0.32f),
                style = Stroke(width = lineWidth),
            )
            drawRect(
                color = Palette.CourtLine.copy(alpha = 0.38f),
                topLeft = Offset(rect.right - keyWidth, rect.center.y - rect.height * 0.16f),
                size = androidx.compose.ui.geometry.Size(keyWidth, rect.height * 0.32f),
                style = Stroke(width = lineWidth),
            )
        }
        if (pitch.sport == "tennis") {
            val inset = rect.height * 0.22f
            listOf(0.28f, 0.72f).forEach { fraction ->
                if (horizontalLongAxis) {
                    val x = rect.left + rect.width * fraction
                    drawLine(
                        Palette.CourtLine.copy(alpha = 0.35f),
                        Offset(x, rect.top + inset),
                        Offset(x, rect.bottom - inset),
                        strokeWidth = lineWidth,
                    )
                } else {
                    val y = rect.top + rect.height * fraction
                    drawLine(
                        Palette.CourtLine.copy(alpha = 0.35f),
                        Offset(rect.left + inset, y),
                        Offset(rect.right - inset, y),
                        strokeWidth = lineWidth,
                    )
                }
            }
        }
    }
}

internal fun DrawScope.drawPaths(layout: Campus3DLayout, projection: Campus3DProjection) {
    layout.paths.forEach { polyline ->
        val screen = polyline.mapNotNull { projection.projectFlat(it, 0.5f) }
        if (screen.size != polyline.size || screen.size < 2) return@forEach
        val averageDepth = screen.sumOf { it.depth.toDouble() }.toFloat() / screen.size
        val pxPerMeter = projection.pixelsPerMeter(averageDepth)
        val width = (3.2f * pxPerMeter).coerceIn(1.1f, 7f)
        val path = polylinePath(screen)
        drawPath(path, Palette.PathEdge.copy(alpha = 0.85f), style = strokeStyle(width + 1.8f))
        drawPath(path, Palette.Path.copy(alpha = 0.96f), style = strokeStyle(width))
    }
}

internal fun DrawScope.drawWater(
    layout: Campus3DLayout,
    projection: Campus3DProjection,
    selected: Campus3DHit?,
) {
    layout.water.forEach { water ->
        val path = ringToPath(projection, water.ring, 0.4f) ?: return@forEach
        val bounds = path.getBounds()
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Palette.Water, Palette.WaterDeep),
                start = Offset(bounds.left, bounds.top),
                end = Offset(bounds.right, bounds.bottom),
            ),
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(Palette.WaterMoon.copy(alpha = 0.2f), Palette.Transparent),
                start = Offset(bounds.right, bounds.top),
                end = Offset(bounds.left + bounds.width * 0.52f, bounds.bottom),
            ),
        )
        clipPath(path) {
            // 月亮在湖的右前方，倒影不按月亮屏幕横坐标硬对齐（会落到岸外被裁掉），
            // 而是锚在湖内右岸一侧，朝视点方向（屏幕左下）逐渐变宽、断开。
            val lakeWidth = bounds.width
            val anchorX = (size.width * CELESTIAL_X_FRACTION).coerceIn(
                bounds.left + lakeWidth * 0.62f,
                bounds.right - lakeWidth * 0.12f,
            )
            val columnTop = bounds.top + bounds.height * 0.24f
            val columnHeight = bounds.height * 0.56f
            val columnCenter = Offset(
                anchorX - lakeWidth * 0.13f,
                columnTop + columnHeight * 0.5f,
            )
            rotate(degrees = -24.5f, pivot = columnCenter) {
                scale(scaleX = 0.35f, scaleY = 1f, pivot = columnCenter) {
                    val glowRadius = columnHeight * 0.5f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Palette.WaterMoon.copy(alpha = 0.24f),
                                Palette.Transparent,
                            ),
                            center = columnCenter,
                            radius = glowRadius,
                        ),
                        radius = glowRadius,
                        center = columnCenter,
                    )
                }
            }
            for (index in 0 until 12) {
                val fraction = index / 11f
                val centerX = anchorX - lakeWidth * 0.26f * fraction +
                    lakeWidth * 0.014f * sin(index * 1.9f)
                val halfWidth = lakeWidth * (0.012f + 0.052f * fraction) *
                    (0.85f + 0.15f * ((index * 7) % 3))
                val height = 2f + 3.2f * fraction
                val centerY = columnTop + columnHeight * fraction
                drawOval(
                    color = Palette.WaterMoon.copy(alpha = 0.58f - 0.26f * fraction),
                    topLeft = Offset(centerX - halfWidth, centerY - height / 2f),
                    size = Size(halfWidth * 2f, height),
                )
                if (fraction < 0.6f) {
                    val coreWidth = halfWidth * 0.42f
                    drawOval(
                        color = Palette.SunCore.copy(alpha = 0.4f),
                        topLeft = Offset(centerX - coreWidth, centerY - 0.7f),
                        size = Size(coreWidth * 2f, 1.4f),
                    )
                }
            }
            for (index in 0 until 8) {
                val fraction = 0.3f + 0.08f * index
                val halfWidth = lakeWidth * (0.005f + 0.005f * (index % 3))
                val centerX = anchorX - lakeWidth * 0.26f * fraction +
                    lakeWidth * 0.045f * sin(index * 2.3f)
                drawOval(
                    color = Palette.WaterMoon.copy(alpha = 0.34f),
                    topLeft = Offset(centerX - halfWidth, columnTop + columnHeight * fraction - 0.9f),
                    size = Size(halfWidth * 2f, 1.8f),
                )
            }
            listOf(0.3f, 0.46f, 0.62f, 0.76f).forEachIndexed { index, fraction ->
                val y = bounds.top + bounds.height * fraction
                drawLine(
                    color = Palette.WaterShine.copy(alpha = 0.3f - index * 0.05f),
                    start = Offset(bounds.left + bounds.width * (0.1f + index * 0.04f), y),
                    end = Offset(bounds.right - bounds.width * (0.14f + index * 0.05f), y),
                    strokeWidth = max(1f, bounds.width * 0.006f).coerceAtMost(2.4f),
                )
            }
        }
        drawPath(path, Palette.Ink.copy(alpha = 0.16f), style = strokeStyle(3f))
        drawPath(path, Palette.Shore.copy(alpha = 0.9f), style = strokeStyle(1.4f))
        if (selected is Campus3DHit.Water && selected.shape.id == water.id) {
            drawPath(path, Palette.Selection.copy(alpha = 0.2f), style = strokeStyle(9f))
            drawPath(path, Palette.Selection, style = strokeStyle(2.2f))
        }
    }
}

internal fun DrawScope.drawBuildingShadows(layout: Campus3DLayout, projection: Campus3DProjection) {
    val ordered = layout.buildings.sortedByDescending { building ->
        projection.depthOf(Vec3(building.centroid.x, 0f, building.centroid.z))
    }
    ordered.forEach { building ->
        val depth = projection.depthOf(Vec3(building.centroid.x, 0f, building.centroid.z))
        val softWidth = (projection.pixelsPerMeter(depth) * 3.5f).coerceIn(4f, 26f)
        ringToPath(projection, building.footprint, 0.18f)?.let { path ->
            drawPath(path, Palette.Shadow.copy(alpha = 0.1f), style = Stroke(width = softWidth))
            drawPath(
                path,
                Palette.Shadow.copy(alpha = 0.14f),
                style = Stroke(width = softWidth * 0.45f),
            )
        }

        val offset = Vec2(building.height * 0.34f, -building.height * 0.2f)
        val offsetRing = building.footprint.map { Vec2(it.x + offset.x, it.z + offset.z) }
        ringToPath(projection, offsetRing, 0.18f)?.let { drawPath(it, Palette.Shadow) }
        val farOffset = Vec2(building.height * 0.52f, -building.height * 0.3f)
        val farRing = building.footprint.map { Vec2(it.x + farOffset.x, it.z + farOffset.z) }
        ringToPath(projection, farRing, 0.18f)?.let {
            drawPath(it, Palette.Shadow.copy(alpha = 0.1f))
        }
        ringToPath(projection, building.footprint, 0.22f)?.let {
            drawPath(it, Palette.Ink.copy(alpha = 0.17f))
        }
    }
}
