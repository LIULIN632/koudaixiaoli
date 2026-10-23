package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private sealed interface Campus3DFace {
    val depth: Float

    class Wall(
        val building: Campus3DBuildingShape,
        val quad: List<ScreenPoint>,
        val brightness: Float,
        override val depth: Float,
    ) : Campus3DFace

    class Roof(
        val building: Campus3DBuildingShape,
        val path: Path,
        override val depth: Float,
    ) : Campus3DFace

    class TreeFace(
        val tree: Campus3DTree,
        val base: ScreenPoint,
        val top: ScreenPoint,
        override val depth: Float,
    ) : Campus3DFace

    class LampFace(
        val base: ScreenPoint,
        val top: ScreenPoint,
        override val depth: Float,
    ) : Campus3DFace
}

private class Campus3DBoxFace(
    val quad: List<ScreenPoint>,
    val color: Color,
    val depth: Float,
)

internal fun DrawScope.drawBuildingsAndTrees(
    layout: Campus3DLayout,
    projection: Campus3DProjection,
    selected: Campus3DHit?,
    todayBuildingIds: Set<String> = emptySet(),
) {
    val faces = ArrayList<Campus3DFace>(
        layout.buildings.size * 6 + layout.trees.size + layout.lamps.size
    )
    val eyeXZ = Vec2(projection.eye.x, projection.eye.z)
    val selectedBuildingId = (selected as? Campus3DHit.Building)?.shape?.id

    layout.buildings.forEach { building ->
        val footprint = building.footprint
        for (index in footprint.indices) {
            val a = footprint[index]
            val b = footprint[(index + 1) % footprint.size]
            val edge = b - a
            if (edge.length() < 0.4f) continue
            val normal = Vec2(edge.z, -edge.x).normalized()
            val midpoint = (a + b) * 0.5f
            if (normal.dot(eyeXZ - midpoint) <= 0f) continue

            val bottomA = projection.projectFlat(a) ?: continue
            val bottomB = projection.projectFlat(b) ?: continue
            val topA = projection.projectFlat(a, building.height) ?: continue
            val topB = projection.projectFlat(b, building.height) ?: continue
            val brightness = (0.74f + 0.32f * max(0f, normal.dot(LIGHT_XZ))).coerceIn(0.64f, 1.08f)
            faces += Campus3DFace.Wall(
                building = building,
                quad = listOf(bottomA, bottomB, topB, topA),
                brightness = brightness,
                depth = (bottomA.depth + bottomB.depth + topA.depth + topB.depth) * 0.25f,
            )
        }

        val roofPath = areaToPath(projection, footprint, building.holes, building.height) ?: return@forEach
        faces += Campus3DFace.Roof(
            building = building,
            path = roofPath,
            depth = projection.depthOf(Vec3(building.centroid.x, building.height, building.centroid.z)),
        )
    }

    layout.trees.forEach { tree ->
        val base = projection.projectFlat(tree.position) ?: return@forEach
        val top = projection.projectFlat(tree.position, tree.height) ?: return@forEach
        faces += Campus3DFace.TreeFace(tree, base, top, base.depth)
    }

    layout.lamps.forEach { lamp ->
        val base = projection.projectFlat(lamp) ?: return@forEach
        val top = projection.projectFlat(lamp, 4.8f) ?: return@forEach
        faces += Campus3DFace.LampFace(base, top, base.depth)
    }

    faces.sortByDescending { it.depth }
    faces.forEach { face ->
        when (face) {
            is Campus3DFace.Wall -> drawWall(
                face,
                isSelected = face.building.id == selectedBuildingId,
                hasClassToday = face.building.id in todayBuildingIds,
            )
            is Campus3DFace.Roof -> drawRoof(
                face,
                projection,
                isSelected = face.building.id == selectedBuildingId,
                hasClassToday = face.building.id in todayBuildingIds,
            )
            is Campus3DFace.TreeFace -> drawTree(face)
            is Campus3DFace.LampFace -> drawLamp(face)
        }
    }
}

private fun DrawScope.drawWall(
    face: Campus3DFace.Wall,
    isSelected: Boolean,
    hasClassToday: Boolean,
) {
    val quad = face.quad
    val path = quadPath(quad)
    if (hasClassToday) {
        drawPath(path, Palette.TodayClassGlow, style = strokeStyle(7f))
    }
    val baseColor = wallColor(face.building.category)
    val topCenter = Offset((quad[3].x + quad[2].x) * 0.5f, (quad[3].y + quad[2].y) * 0.5f)
    val bottomCenter = Offset((quad[0].x + quad[1].x) * 0.5f, (quad[0].y + quad[1].y) * 0.5f)
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                shade(baseColor, face.brightness * 1.06f),
                shade(baseColor, face.brightness * 0.8f),
            ),
            start = topCenter,
            end = bottomCenter,
        ),
    )
    drawWallPlinth(face)
    drawWallFloorLines(face)
    drawWallWindows(face)

    drawLine(
        color = Palette.RoofHighlight.copy(alpha = 0.42f),
        start = quad[3].offset(),
        end = quad[2].offset(),
        strokeWidth = 1.1f,
    )
    drawPath(path, Palette.Ink.copy(alpha = 0.18f), style = Stroke(width = 0.9f))
    if (hasClassToday) {
        drawPath(path, Palette.TodayClass.copy(alpha = 0.9f), style = strokeStyle(1.5f))
    }
    if (isSelected) {
        drawPath(path, Palette.Selection.copy(alpha = 0.28f), style = strokeStyle(6f))
        drawPath(path, Palette.Selection.copy(alpha = 0.85f), style = strokeStyle(1.6f))
    }
}

private fun DrawScope.drawWallPlinth(face: Campus3DFace.Wall) {
    val quad = face.quad
    val faceHeight = hypot(
        (quad[3].x + quad[2].x) * 0.5f - (quad[0].x + quad[1].x) * 0.5f,
        (quad[3].y + quad[2].y) * 0.5f - (quad[0].y + quad[1].y) * 0.5f,
    )
    if (faceHeight < 24f) return
    val bottomLeft = bilinear(quad, 0f, 0f)
    val bottomRight = bilinear(quad, 1f, 0f)
    val topLeft = bilinear(quad, 0f, 0.065f)
    val topRight = bilinear(quad, 1f, 0.065f)
    drawPath(
        quadPath(listOf(bottomLeft, bottomRight, topRight, topLeft)),
        shade(wallColor(face.building.category), face.brightness * 0.66f),
    )
    drawLine(
        color = Palette.Ink.copy(alpha = 0.2f),
        start = topLeft.offset(),
        end = topRight.offset(),
        strokeWidth = 0.8f,
    )
}

private fun DrawScope.drawWallWindows(face: Campus3DFace.Wall) {
    val quad = face.quad
    val bottomWidth = hypot(quad[1].x - quad[0].x, quad[1].y - quad[0].y)
    val midBottomX = (quad[0].x + quad[1].x) * 0.5f
    val midBottomY = (quad[0].y + quad[1].y) * 0.5f
    val midTopX = (quad[3].x + quad[2].x) * 0.5f
    val midTopY = (quad[3].y + quad[2].y) * 0.5f
    val faceHeight = hypot(midTopX - midBottomX, midTopY - midBottomY)
    if (faceHeight < 20f || bottomWidth < 18f) return

    val rows = min(face.building.levels, max(1, (faceHeight / 14f).toInt())).coerceAtMost(8)
    val columns = max(1, (bottomWidth / 28f).toInt()).coerceAtMost(5)
    val cellWidth = 1f / columns
    val cellHeight = 1f / rows
    val wallSeed = face.building.id.hashCode() +
        face.quad[0].x.toInt() * 31 +
        face.quad[0].y.toInt() * 17

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val u0 = column * cellWidth + cellWidth * 0.24f
            val u1 = column * cellWidth + cellWidth * 0.76f
            val v0 = row * cellHeight + cellHeight * 0.28f
            val v1 = row * cellHeight + cellHeight * 0.74f
            val window = quadPath(
                listOf(
                    bilinear(quad, u0, v0),
                    bilinear(quad, u1, v0),
                    bilinear(quad, u1, v1),
                    bilinear(quad, u0, v1),
                )
            )
            val hash = wallSeed + row * 17 + column * 31
            val lit = (hash and 0x7FFFFFFF) % 100 < 46
            if (lit) {
                val strong = (hash ushr 7) % 100 < 38
                if (strong) {
                    drawPath(
                        window,
                        Palette.WindowGlow.copy(alpha = 0.55f),
                        style = Stroke(width = (faceHeight * 0.012f).coerceIn(1.2f, 2.6f)),
                    )
                }
                drawPath(window, Palette.WindowLitCore.copy(alpha = 0.96f))
                val sillLeft = bilinear(quad, u0, v0)
                val sillRight = bilinear(quad, u1, v0)
                drawLine(
                    color = Palette.WindowLitWarm.copy(alpha = 0.55f),
                    start = sillLeft.offset(),
                    end = sillRight.offset(),
                    strokeWidth = 0.7f,
                )
            } else {
                drawPath(window, Palette.WindowDark.copy(alpha = 0.72f))
            }
            if ((v1 - v0) * faceHeight > 5f) {
                drawPath(window, Palette.Ink.copy(alpha = 0.18f), style = Stroke(width = 0.55f))
            }
        }
    }
}

private fun DrawScope.drawWallFloorLines(face: Campus3DFace.Wall) {
    val quad = face.quad
    val faceHeight = hypot(
        (quad[3].x + quad[2].x) * 0.5f - (quad[0].x + quad[1].x) * 0.5f,
        (quad[3].y + quad[2].y) * 0.5f - (quad[0].y + quad[1].y) * 0.5f,
    )
    if (faceHeight < 64f || face.building.levels < 2) return
    for (level in 1 until face.building.levels) {
        val fraction = level.toFloat() / face.building.levels
        val left = lerpScreen(quad[0], quad[3], fraction)
        val right = lerpScreen(quad[1], quad[2], fraction)
        drawLine(
            color = Palette.FloorLine,
            start = left,
            end = right,
            strokeWidth = 0.8f,
        )
    }
}

private fun bilinear(quad: List<ScreenPoint>, u: Float, v: Float): ScreenPoint {
    val bottomX = quad[0].x + (quad[1].x - quad[0].x) * u
    val bottomY = quad[0].y + (quad[1].y - quad[0].y) * u
    val topX = quad[3].x + (quad[2].x - quad[3].x) * u
    val topY = quad[3].y + (quad[2].y - quad[3].y) * u
    return ScreenPoint(
        x = bottomX + (topX - bottomX) * v,
        y = bottomY + (topY - bottomY) * v,
        depth = quad[0].depth,
    )
}

private fun DrawScope.drawRoof(
    face: Campus3DFace.Roof,
    projection: Campus3DProjection,
    isSelected: Boolean,
    hasClassToday: Boolean,
) {
    val color = roofColor(face.building.category, face.building.id)
    val bounds = face.path.getBounds()
    if (hasClassToday) {
        drawPath(face.path, Palette.TodayClassGlow, style = strokeStyle(14f))
    }
    if (isSelected) {
        drawPath(face.path, Palette.Selection.copy(alpha = 0.18f), style = strokeStyle(12f))
    }
    drawPath(
        path = face.path,
        brush = Brush.linearGradient(
            colors = listOf(color, shade(color, 0.82f)),
            start = Offset(bounds.left, bounds.top),
            end = Offset(bounds.right, bounds.bottom),
        ),
    )
    drawPath(face.path, Palette.RoofEdge.copy(alpha = 0.28f), style = Stroke(width = 2.8f))
    drawPath(face.path, Palette.RoofHighlight.copy(alpha = 0.3f), style = Stroke(width = 1.1f))
    drawPath(face.path, Palette.RoofEdge, style = Stroke(width = 1.3f))
    if (hasClassToday) {
        drawPath(face.path, Palette.TodayClass.copy(alpha = 0.12f))
        drawPath(face.path, Palette.TodayClass.copy(alpha = 0.9f), style = strokeStyle(2f))
    }
    drawRoofDetails(face.building, projection)
    if (isSelected) {
        drawPath(face.path, Palette.Selection, style = strokeStyle(2.4f))
    }
}

private fun DrawScope.drawRoofDetails(
    building: Campus3DBuildingShape,
    projection: Campus3DProjection,
) {
    if (building.roofDetails.isEmpty()) return
    val anchor = projection.projectFlat(building.centroid, building.height) ?: return
    val pxPerMeter = projection.pixelsPerMeter(anchor.depth)
    if (pxPerMeter <= 0f) return
    val eyeXZ = Vec2(projection.eye.x, projection.eye.z)
    building.roofDetails
        .sortedByDescending { detail ->
            projection.depthOf(Vec3(detail.center.x, building.height, detail.center.z))
        }
        .forEach { detail ->
            val widthPx = pxPerMeter * detail.halfX * 2f
            val heightPx = pxPerMeter * detail.height
            if (widthPx < 2.5f || heightPx < 2f) return@forEach
            drawRoofBox(building, detail, projection, eyeXZ)
        }
}

private fun DrawScope.drawRoofBox(
    building: Campus3DBuildingShape,
    detail: Campus3DRoofDetail,
    projection: Campus3DProjection,
    eyeXZ: Vec2,
) {
    val baseAltitude = building.height
    val topAltitude = baseAltitude + detail.height
    val corners = listOf(
        Vec2(detail.center.x - detail.halfX, detail.center.z - detail.halfZ),
        Vec2(detail.center.x + detail.halfX, detail.center.z - detail.halfZ),
        Vec2(detail.center.x + detail.halfX, detail.center.z + detail.halfZ),
        Vec2(detail.center.x - detail.halfX, detail.center.z + detail.halfZ),
    )
    val bottom = corners.map { projection.projectFlat(it, baseAltitude) ?: return }
    val top = corners.map { projection.projectFlat(it, topAltitude) ?: return }

    val faces = ArrayList<Campus3DBoxFace>(5)
    for (index in corners.indices) {
        val a = corners[index]
        val b = corners[(index + 1) % corners.size]
        val edge = b - a
        if (edge.length() < 0.05f) continue
        val normal = Vec2(edge.z, -edge.x).normalized()
        val midpoint = (a + b) * 0.5f
        if (normal.dot(eyeXZ - midpoint) <= 0f) continue
        val bottomA = bottom[index]
        val bottomB = bottom[(index + 1) % bottom.size]
        val topA = top[index]
        val topB = top[(index + 1) % top.size]
        val light = 0.7f + 0.3f * max(0f, normal.dot(LIGHT_XZ))
        faces += Campus3DBoxFace(
            quad = listOf(bottomA, bottomB, topB, topA),
            color = shade(Palette.RoofDetailSide, light),
            depth = (bottomA.depth + bottomB.depth + topA.depth + topB.depth) * 0.25f,
        )
    }
    faces.sortByDescending { it.depth }
    faces.forEach { face ->
        val path = quadPath(face.quad)
        drawPath(path, face.color)
        drawPath(path, Palette.Ink.copy(alpha = 0.18f), style = Stroke(width = 0.6f))
    }

    val topPath = quadPath(top)
    drawPath(
        topPath,
        if (detail.kind == 0) Palette.RoofDetailTop else shade(Palette.RoofDetailTop, 0.92f),
    )
    drawPath(topPath, Palette.RoofHighlight.copy(alpha = 0.4f), style = Stroke(width = 0.8f))
    drawPath(topPath, Palette.Ink.copy(alpha = 0.2f), style = Stroke(width = 0.6f))
}

private fun DrawScope.drawTree(face: Campus3DFace.TreeFace) {
    val heightPixels = face.base.y - face.top.y
    if (heightPixels < 3f) return
    val trunkTop = Offset(face.base.x, face.base.y - heightPixels * 0.42f)
    drawLine(
        color = Palette.TreeTrunk,
        start = face.base.offset(),
        end = trunkTop,
        strokeWidth = max(1f, heightPixels * 0.055f).coerceAtMost(3f),
    )
    val radius = (heightPixels * 0.4f).coerceIn(2.5f, 34f)
    drawOval(
        color = Palette.TreeShadow,
        topLeft = Offset(face.base.x - radius * 0.95f, face.base.y - radius * 0.2f),
        size = Size(radius * 1.9f, radius * 0.4f),
    )

    val conifer = ((face.tree.position.x * 3.7f + face.tree.position.z * 5.3f).toInt() and 3) == 0
    if (conifer) {
        val topY = face.top.y + heightPixels * 0.08f
        val bottomY = face.base.y - heightPixels * 0.18f
        val halfWidth = radius * 0.9f
        val body = Path().apply {
            moveTo(face.base.x, topY)
            lineTo(face.base.x + halfWidth, bottomY)
            lineTo(face.base.x - halfWidth, bottomY)
            close()
        }
        drawPath(body, shade(Palette.TreeCanopyDeep, 0.9f + face.tree.tint * 0.2f))
        val litTier = Path().apply {
            moveTo(face.base.x, topY)
            lineTo(face.base.x + halfWidth * 0.58f, bottomY - heightPixels * 0.2f)
            lineTo(face.base.x - halfWidth * 0.28f, bottomY - heightPixels * 0.2f)
            close()
        }
        drawPath(litTier, Palette.TreeCanopyLight.copy(alpha = 0.5f))
        return
    }

    val canopyCenter = Offset(face.base.x, face.base.y - heightPixels * 0.64f)
    val warm = face.tree.tint > 0.55f
    drawCircle(
        color = shade(Palette.TreeCanopyDeep, 0.92f + face.tree.tint * 0.16f),
        radius = radius,
        center = canopyCenter,
    )
    drawCircle(
        color = if (warm) Palette.TreeCanopyWarm else Palette.TreeCanopy,
        radius = radius * 0.86f,
        center = Offset(canopyCenter.x - radius * 0.16f, canopyCenter.y - radius * 0.16f),
    )
    if (face.tree.tint > 0.45f) {
        drawCircle(
            color = Palette.TreeCanopy.copy(alpha = 0.9f),
            radius = radius * 0.55f,
            center = Offset(canopyCenter.x + radius * 0.5f, canopyCenter.y + radius * 0.1f),
        )
    }
    drawCircle(
        color = Palette.TreeCanopyLight.copy(alpha = 0.82f),
        radius = radius * 0.46f,
        center = Offset(canopyCenter.x - radius * 0.24f, canopyCenter.y - radius * 0.3f),
    )
    if (face.tree.tint > 0.6f) {
        drawCircle(
            color = Palette.TreeCanopyLight.copy(alpha = 0.55f),
            radius = radius * 0.2f,
            center = Offset(canopyCenter.x + radius * 0.22f, canopyCenter.y - radius * 0.16f),
        )
    }
}

private fun DrawScope.drawLamp(face: Campus3DFace.LampFace) {
    val heightPixels = face.base.y - face.top.y
    if (heightPixels < 2f) return
    val headRadius = (heightPixels * 0.055f).coerceIn(1.6f, 3.2f)
    drawOval(
        color = Palette.LampGlow.copy(alpha = 0.3f),
        topLeft = Offset(face.base.x - headRadius * 6f, face.base.y - headRadius * 1.5f),
        size = Size(headRadius * 12f, headRadius * 3f),
    )
    drawLine(
        color = Palette.LampGlow.copy(alpha = 0.18f),
        start = face.base.offset(),
        end = face.top.offset(),
        strokeWidth = headRadius * 2.2f,
    )
    drawLine(
        color = Palette.LampPole,
        start = face.base.offset(),
        end = face.top.offset(),
        strokeWidth = max(0.9f, headRadius * 0.6f),
    )
    drawCircle(Palette.LampGlow, headRadius * 3.6f, face.top.offset())
    drawCircle(Palette.LampGlow.copy(alpha = 0.9f), headRadius * 1.9f, face.top.offset())
    drawCircle(Palette.LampCore, headRadius, face.top.offset())
}
