package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette
import kotlin.math.max

internal val LIGHT_XZ: Vec2 = Vec2(0.52f, 0.85f).normalized()

internal const val CELESTIAL_X_FRACTION = 0.74f
internal const val CELESTIAL_Y_FRACTION = 0.265f

/** 3D 沙盘总入口。顺序即遮挡关系：天空 → 地块 → 地面细节 → 楼体/树 → 暗角 → 标签。 */
fun DrawScope.drawCampus3D(
    layout: Campus3DLayout,
    camera: Campus3DCamera,
    textMeasurer: TextMeasurer,
    selected: Campus3DHit?,
    todayBuildingIds: Set<String> = emptySet(),
) {
    val projection = Campus3DProjection(camera, size.width, size.height)
    drawSky()
    drawSlab(layout, projection)
    drawGroundBase(layout, projection)
    drawGreenAreas(layout, projection)
    drawPitches(layout, projection, selected)
    drawPaths(layout, projection)
    drawWater(layout, projection, selected)
    drawBuildingShadows(layout, projection)
    drawBuildingsAndTrees(layout, projection, selected, todayBuildingIds)
    drawVignette()
    drawCampus3DLabels(layout, projection, textMeasurer, selected, todayBuildingIds)
}

private fun DrawScope.drawSky() {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Palette.SkyTop,
            0.42f to Palette.SkyMid,
            0.76f to Palette.SkyHorizon,
            1f to Palette.SkyHaze,
        )
    )
    for (index in 0 until 28) {
        val nx = ((index * 73 + 19) % 100) / 100f
        val ny = ((index * 37 + 7) % 38) / 100f
        drawCircle(
            color = Palette.Star.copy(alpha = 0.18f + (index % 3) * 0.08f),
            radius = if (index % 5 == 0) 1.4f else 0.9f,
            center = Offset(nx * size.width, ny * size.height),
        )
    }

    val sunCenter = Offset(
        size.width * CELESTIAL_X_FRACTION,
        size.height * CELESTIAL_Y_FRACTION,
    )
    val glowRadius = size.width * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Palette.SunGlow, Palette.Transparent),
            center = sunCenter,
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = sunCenter,
    )
    val moonBloomRadius = size.width * 0.16f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Palette.SunGlow.copy(alpha = 0.5f),
                Palette.SunGlow.copy(alpha = 0.12f),
                Palette.Transparent,
            ),
            center = sunCenter,
            radius = moonBloomRadius,
        ),
        radius = moonBloomRadius,
        center = sunCenter,
    )
    val moonRadius = size.width * 0.045f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Palette.White.copy(alpha = 0.95f),
                Palette.SunCore,
                Palette.SunGlow.copy(alpha = 0.92f),
            ),
            center = Offset(sunCenter.x - moonRadius * 0.3f, sunCenter.y - moonRadius * 0.3f),
            radius = moonRadius * 1.5f,
        ),
        radius = moonRadius,
        center = sunCenter,
    )
}

private fun DrawScope.drawSlab(layout: Campus3DLayout, projection: Campus3DProjection) {
    data class Side(
        val rock: Path,
        val lip: Path,
        val rockStart: Offset,
        val rockEnd: Offset,
        val depth: Float,
        val brightness: Float,
    )

    val ring = layout.boundary
    val eyeXZ = Vec2(projection.eye.x, projection.eye.z)
    val sides = mutableListOf<Side>()
    for (index in ring.indices) {
        val a = ring[index]
        val b = ring[(index + 1) % ring.size]
        val edge = b - a
        if (edge.length() < 0.5f) continue
        val normal = Vec2(edge.z, -edge.x).normalized()
        val midpoint = (a + b) * 0.5f
        if (normal.dot(eyeXZ - midpoint) <= 0f) continue

        val topA = projection.project(Vec3(a.x, 0f, a.z)) ?: continue
        val topB = projection.project(Vec3(b.x, 0f, b.z)) ?: continue
        val lipA = projection.project(Vec3(a.x, -3f, a.z)) ?: continue
        val lipB = projection.project(Vec3(b.x, -3f, b.z)) ?: continue
        val bottomA = projection.project(Vec3(a.x, -20f, a.z)) ?: continue
        val bottomB = projection.project(Vec3(b.x, -20f, b.z)) ?: continue

        val brightness = (0.72f + 0.3f * max(0f, normal.dot(LIGHT_XZ))).coerceIn(0.62f, 1.06f)
        val rockStart = Offset((lipA.x + lipB.x) * 0.5f, (lipA.y + lipB.y) * 0.5f)
        val rockEnd = Offset((bottomA.x + bottomB.x) * 0.5f, (bottomA.y + bottomB.y) * 0.5f)
        sides += Side(
            rock = quadPath(listOf(lipA, lipB, bottomB, bottomA)),
            lip = quadPath(listOf(topA, topB, lipB, lipA)),
            rockStart = rockStart,
            rockEnd = rockEnd,
            depth = (topA.depth + topB.depth) * 0.5f,
            brightness = brightness,
        )
    }

    sides.sortedByDescending { it.depth }.forEach { side ->
        drawPath(
            path = side.rock,
            brush = Brush.linearGradient(
                colors = listOf(
                    shade(Palette.SlabRockTop, side.brightness),
                    shade(Palette.SlabRockDeep, side.brightness),
                ),
                start = side.rockStart,
                end = side.rockEnd,
            ),
        )
        drawPath(side.lip, shade(Palette.SlabLip, side.brightness))
        drawPath(side.lip, Palette.Ink.copy(alpha = 0.12f), style = Stroke(width = 1f))
    }

    val bottomRing = ring.mapNotNull { projection.project(Vec3(it.x, -20f, it.z)) }
    if (bottomRing.size >= 2) {
        drawPath(
            polylinePath(bottomRing),
            Palette.SlabEdge.copy(alpha = 0.28f),
            style = Stroke(width = 1.4f),
        )
    }
}

private fun DrawScope.drawGroundBase(layout: Campus3DLayout, projection: Campus3DProjection) {
    val path = ringToPath(projection, layout.boundary, 0.1f) ?: return
    val bounds = path.getBounds()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Palette.GroundLight, Palette.Ground),
            start = Offset(bounds.left, bounds.top),
            end = Offset(bounds.right, bounds.bottom),
        ),
    )
    drawPath(path, Palette.GroundEdge.copy(alpha = 0.16f), style = Stroke(width = 9f))
    drawPath(path, Palette.GroundEdge.copy(alpha = 0.72f), style = Stroke(width = 1.6f))
}

private fun DrawScope.drawVignette() {
    val radius = max(size.width, size.height) * 0.78f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Palette.Transparent, Palette.Vignette),
            center = Offset(size.width * 0.5f, size.height * 0.44f),
            radius = radius,
        )
    )
}

internal fun ringToPath(
    projection: Campus3DProjection,
    ring: List<Vec2>,
    altitude: Float,
): Path? {
    if (ring.size < 3) return null
    val screen = ArrayList<ScreenPoint>(ring.size)
    ring.forEach { point ->
        screen += projection.projectFlat(point, altitude) ?: return null
    }
    return polylinePath(screen).apply { close() }
}

internal fun areaToPath(
    projection: Campus3DProjection,
    outer: List<Vec2>,
    holes: List<List<Vec2>>,
    altitude: Float,
): Path? {
    val path = ringToPath(projection, outer, altitude) ?: return null
    path.fillType = PathFillType.EvenOdd
    holes.forEach { hole ->
        val holePath = ringToPath(projection, hole, altitude) ?: return@forEach
        path.addPath(holePath)
    }
    return path
}

internal fun quadPath(points: List<ScreenPoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (index in 1 until points.size) path.lineTo(points[index].x, points[index].y)
    path.close()
    return path
}

internal fun polylinePath(points: List<ScreenPoint>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (index in 1 until points.size) path.lineTo(points[index].x, points[index].y)
    return path
}

internal fun ScreenPoint.offset(): Offset = Offset(x, y)

internal fun lerpScreen(a: ScreenPoint, b: ScreenPoint, fraction: Float): Offset = Offset(
    x = a.x + (b.x - a.x) * fraction,
    y = a.y + (b.y - a.y) * fraction,
)

internal fun shade(color: Color, factor: Float, alpha: Float = color.alpha): Color = Color(
    red = (color.red * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue = (color.blue * factor).coerceIn(0f, 1f),
    alpha = alpha,
)

internal fun wallColor(category: String): Color = when (category) {
    "teaching" -> Palette.WallTeaching
    "dorm" -> Palette.WallDorm
    "dining" -> Palette.WallDining
    "office" -> Palette.WallOffice
    "commercial" -> Palette.WallCommercial
    else -> Palette.WallFacility
}

internal fun roofColor(category: String, id: String): Color = when (category) {
    "teaching" -> if (id.hashCode() % 3 == 0) Palette.RoofSlate else Palette.RoofTerracotta
    "dorm" -> Palette.RoofWarm
    "dining" -> Palette.RoofDining
    "office" -> Palette.RoofSlate
    "commercial" -> Palette.RoofTerracotta
    else -> if (id.hashCode() % 2 == 0) Palette.RoofSlate else Palette.RoofWarm
}

internal fun categoryLabel(category: String): String = when (category) {
    "teaching" -> "教学楼"
    "dorm" -> "学生公寓"
    // 楼名本身常就是「餐厅」「综合楼」，类型标签用近义词，避免卡片里上下重复。
    "dining" -> "餐饮"
    "office" -> "办公楼"
    "commercial" -> "商业配套"
    else -> "校园建筑"
}

internal fun sportLabel(kind: String, sport: String): String = when {
    sport == "running" || kind == "track" -> "田径场"
    sport == "soccer" -> "足球场"
    sport == "basketball" -> "篮球场"
    sport == "tennis" -> "网球场"
    else -> "运动场地"
}

internal fun strokeStyle(width: Float): Stroke = Stroke(
    width = width,
    cap = StrokeCap.Round,
    join = StrokeJoin.Round,
)
