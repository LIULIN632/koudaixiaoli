package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette

private data class LabelCandidate(
    val text: String,
    val anchor: Offset,
    val priority: Int,
    val style: TextStyle,
    val selected: Boolean = false,
    val today: Boolean = false,
    val stem: Boolean = false,
    val background: Color = Palette.LabelBg,
    /** POI 图钉向上长，名字放到锚点下方，避免与图钉或楼名互相挤掉。 */
    val below: Boolean = false,
)

internal fun DrawScope.drawCampus3DLabels(
    layout: Campus3DLayout,
    projection: Campus3DProjection,
    textMeasurer: TextMeasurer,
    selected: Campus3DHit?,
    todayBuildingIds: Set<String> = emptySet(),
) {
    if (size.width <= 0f || size.height <= 0f) return
    val occupied = ArrayList<Rect>()
    val selectedPoiId = (selected as? Campus3DHit.Poi)?.shape?.id
    val selectedBuildingId = (selected as? Campus3DHit.Building)?.shape?.id
    val selectedPitchId = (selected as? Campus3DHit.Pitch)?.shape?.id
    val selectedWaterId = (selected as? Campus3DHit.Water)?.shape?.id

    val poiAnchors = HashMap<String, Offset>()
    layout.pois
        .mapNotNull { poi ->
            val screen = projection.projectFlat(poi.position, 4f) ?: return@mapNotNull null
            poi to screen
        }
        .sortedByDescending { it.second.depth }
        .forEach { (poi, screen) ->
            if (!onScreen(screen.x, screen.y, margin = 24f)) return@forEach
            val anchor = Offset(screen.x, screen.y)
            val pinRect = drawPoiPin(anchor, poi.id == selectedPoiId)
            occupied += pinRect
            poiAnchors[poi.id] = anchor
        }

    val candidates = ArrayList<LabelCandidate>()

    layout.pois.forEach { poi ->
        val anchor = poiAnchors[poi.id] ?: return@forEach
        candidates += LabelCandidate(
            text = poi.name,
            anchor = anchor,
            priority = 900,
            style = TextStyle(
                color = if (poi.id == selectedPoiId) Palette.Selection else Palette.LabelText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            selected = poi.id == selectedPoiId,
            background = Palette.LabelBgStrong,
            below = true,
        )
    }

    layout.buildings.forEach { building ->
        if (building.name.isBlank()) return@forEach
        val roof = projection.projectFlat(building.centroid, building.height) ?: return@forEach
        val base = projection.projectFlat(building.centroid, 0f) ?: return@forEach
        if (!onScreen(roof.x, roof.y, margin = 8f)) return@forEach
        val isSelected = building.id == selectedBuildingId
        val hasClassToday = building.id in todayBuildingIds
        if (base.y - roof.y < 4f && !isSelected && !hasClassToday) return@forEach
        candidates += LabelCandidate(
            text = building.name,
            anchor = Offset(roof.x, roof.y),
            priority = when {
                isSelected -> 1000
                hasClassToday -> 760
                else -> 700
            },
            style = TextStyle(
                color = when {
                    isSelected -> Palette.Selection
                    hasClassToday -> Palette.TodayClass
                    else -> Palette.LabelText
                },
                fontSize = if (isSelected) 12.sp else 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            ),
            selected = isSelected,
            today = hasClassToday,
            stem = true,
            background = if (isSelected || hasClassToday) Palette.LabelBgStrong else Palette.LabelBg,
        )
    }

    layout.zones.forEach { zone ->
        val screen = projection.projectFlat(zone.position, 14f) ?: return@forEach
        if (!onScreen(screen.x, screen.y, margin = 8f)) return@forEach
        candidates += LabelCandidate(
            text = zone.name,
            anchor = Offset(screen.x, screen.y),
            priority = 430,
            style = TextStyle(
                color = Palette.LabelTextDim,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp,
            ),
            background = Palette.LabelBg.copy(alpha = 0.72f),
        )
    }

    layout.pitches.forEach { pitch ->
        if (pitch.name.isBlank()) return@forEach
        val screen = projection.projectFlat(pitch.centroid, 1.2f) ?: return@forEach
        if (!onScreen(screen.x, screen.y, margin = 8f)) return@forEach
        val isSelected = pitch.id == selectedPitchId
        candidates += LabelCandidate(
            text = pitch.name,
            anchor = Offset(screen.x, screen.y),
            priority = if (isSelected) 980 else 400,
            style = TextStyle(
                color = if (isSelected) Palette.Selection else Palette.LabelText.copy(alpha = 0.92f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            selected = isSelected,
            background = Palette.LabelBg.copy(alpha = 0.76f),
        )
    }

    layout.water.forEach { water ->
        if (water.name.isBlank()) return@forEach
        val screen = projection.projectFlat(water.centroid, 1.2f) ?: return@forEach
        if (!onScreen(screen.x, screen.y, margin = 8f)) return@forEach
        val isSelected = water.id == selectedWaterId
        candidates += LabelCandidate(
            text = water.name,
            anchor = Offset(screen.x, screen.y),
            priority = if (isSelected) 970 else 380,
            style = TextStyle(
                color = if (isSelected) Palette.Selection else Palette.LabelText.copy(alpha = 0.92f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            selected = isSelected,
            background = Palette.LabelBg.copy(alpha = 0.74f),
        )
    }

    candidates.sortWith(compareByDescending<LabelCandidate> { it.priority }.thenBy { it.anchor.y })
    candidates.take(26).forEach { candidate ->
        drawLabel(candidate, textMeasurer, occupied)
    }
}

private fun DrawScope.drawLabel(
    candidate: LabelCandidate,
    textMeasurer: TextMeasurer,
    occupied: MutableList<Rect>,
) {
    val textLayout = textMeasurer.measure(
        text = AnnotatedString(candidate.text),
        style = candidate.style,
        maxLines = 1,
    )
    val horizontalPadding = 7.dp.toPx()
    val verticalPadding = 3.dp.toPx()
    val dotRadius = 2.6.dp.toPx()
    val dotWidth = if (candidate.today) dotRadius * 2 + 4.dp.toPx() else 0f
    val pillWidth = textLayout.size.width + horizontalPadding * 2 + dotWidth
    val pillHeight = textLayout.size.height + verticalPadding * 2
    val gap = 7.dp.toPx()
    // 顶部让开返回键/标题，底部让开操作提示与 OSM 署名；否则标签会被 UI 盖住一半。
    val topSafe = 84.dp.toPx()
    val bottomSafe = size.height - 112.dp.toPx()
    val desiredX = candidate.anchor.x - pillWidth * 0.5f
    val desiredY = if (candidate.below) {
        candidate.anchor.y + gap
    } else {
        candidate.anchor.y - pillHeight - gap
    }
    if (desiredY < topSafe && !candidate.selected) return
    val x = desiredX.coerceIn(6.dp.toPx(), (size.width - pillWidth - 6.dp.toPx()).coerceAtLeast(6.dp.toPx()))
    val y = desiredY.coerceAtLeast(6.dp.toPx())
    val rect = Rect(x, y, x + pillWidth, y + pillHeight)
    val padded = Rect(rect.left - 2f, rect.top - 2f, rect.right + 2f, rect.bottom + 2f)
    val collides = occupied.any { it.overlaps(padded) }
    if (collides && !candidate.selected) return
    if (rect.bottom > bottomSafe || rect.right < 0f) return

    if (candidate.stem) {
        val bottomCenter = Offset(rect.center.x, rect.bottom)
        drawLine(
            color = Palette.LabelText.copy(alpha = 0.32f),
            start = bottomCenter,
            end = candidate.anchor,
            strokeWidth = 1f,
        )
        drawCircle(
            color = Palette.LabelText.copy(alpha = 0.5f),
            radius = 1.8f,
            center = candidate.anchor,
        )
    }

    drawRoundRect(
        color = candidate.background,
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(rect.height * 0.5f),
    )
    drawRoundRect(
        color = when {
            candidate.selected -> Palette.Selection.copy(alpha = 0.85f)
            candidate.today -> Palette.TodayClass.copy(alpha = 0.72f)
            else -> Palette.LabelBorder
        },
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(rect.height * 0.5f),
        style = Stroke(width = if (candidate.selected) 1.4f else 1f),
    )
    if (candidate.today) {
        drawCircle(
            color = Palette.TodayClass,
            radius = dotRadius,
            center = Offset(rect.left + horizontalPadding + dotRadius, rect.center.y),
        )
    }
    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(
            rect.left + horizontalPadding + dotWidth,
            rect.top + verticalPadding,
        ),
    )
    occupied += rect
}

private fun DrawScope.drawPoiPin(anchor: Offset, selected: Boolean): Rect {
    val radius = if (selected) 10.dp.toPx() else 8.dp.toPx()
    val tail = 7.dp.toPx()
    val center = Offset(anchor.x, anchor.y - tail - radius)
    val tailPath = Path().apply {
        moveTo(center.x - radius * 0.5f, center.y + radius * 0.55f)
        lineTo(center.x + radius * 0.5f, center.y + radius * 0.55f)
        lineTo(anchor.x, anchor.y)
        close()
    }
    if (selected) {
        drawCircle(
            color = Palette.Selection.copy(alpha = 0.32f),
            radius = radius + 5.dp.toPx(),
            center = center,
        )
    }
    drawPath(tailPath, Palette.Poi)
    drawCircle(Palette.Poi, radius = radius, center = center)
    drawCircle(
        color = Palette.PoiStroke,
        radius = radius,
        center = center,
        style = Stroke(width = 1.3.dp.toPx()),
    )
    drawCircle(Palette.White, radius = radius * 0.32f, center = center)
    return Rect(
        left = center.x - radius - 4.dp.toPx(),
        top = center.y - radius - 4.dp.toPx(),
        right = center.x + radius + 4.dp.toPx(),
        bottom = anchor.y + 2.dp.toPx(),
    )
}

private fun DrawScope.onScreen(x: Float, y: Float, margin: Float): Boolean =
    x >= -margin && x <= size.width + margin && y >= -margin && y <= size.height + margin
