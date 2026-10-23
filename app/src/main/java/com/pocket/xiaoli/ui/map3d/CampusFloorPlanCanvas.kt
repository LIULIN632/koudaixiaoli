package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.indoor.IndoorCoordinateMapper
import com.pocket.xiaoli.data.map.indoor.IndoorFloorPlan
import com.pocket.xiaoli.data.map.indoor.IndoorRoute
import com.pocket.xiaoli.data.map.indoor.IndoorSide
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors

/**
 * 室内底图：走廊带、房间块、楼梯、房间号、路线与水印。
 *
 * 这是**拓扑示意**，不是按比例的建筑平面图。房间与走廊的纵向比例被刻意拉伸，
 * 否则 13 个开间挤在一条窄带上，房间号根本读不出来。
 */
@Composable
internal fun CampusFloorPlanCanvas(
    plan: IndoorFloorPlan,
    selectedRoomId: String?,
    route: IndoorRoute?,
    scale: Float,
    onRoomSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val textMeasurer = rememberTextMeasurer()
    val selectedRoom = plan.room(selectedRoomId.orEmpty())

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("indoor-plan-canvas")
            .semantics {
                contentDescription = "${plan.buildingName} ${plan.floorLabel} 室内拓扑示意图，" +
                    "${plan.rooms.size} 间房、${plan.facilities.size} 个楼梯口。" +
                    "点按房间可查看示意路线；数据未实测、未按实际比例。"
                stateDescription = selectedRoom
                    ?.let { "已选${it.side.label} ${it.name}" }
                    ?: "未选择房间"
            }
            .pointerInput(plan) {
                detectTapGestures { tap ->
                    plan.roomAt(tap, size.width.toFloat(), size.height.toFloat())
                        ?.let { room -> onRoomSelected(room.id) }
                }
            },
    ) {
        drawFloorPlan(
            plan = plan,
            selectedRoomId = selectedRoomId,
            route = route,
            scale = scale,
            colors = colors,
            textMeasurer = textMeasurer,
        )
    }
}

private fun DrawScope.drawFloorPlan(
    plan: IndoorFloorPlan,
    selectedRoomId: String?,
    route: IndoorRoute?,
    scale: Float,
    colors: PocketXiaoliColors,
    textMeasurer: TextMeasurer,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val mapper = plan.mapper
    val width = size.width
    val height = size.height
    val s = scale.coerceIn(1f, 3f)
    val unitX = mapper.unitX(width)
    val unitY = mapper.unitY(height)
    val corridorWidth = (plan.corridors.firstOrNull()?.widthUnits ?: 0.6f) * unitY
    val corridorFill = colors.surfaceCard
    val guideDash = PathEffect.dashPathEffect(
        floatArrayOf(4.dp.toPx() * s, 7.dp.toPx() * s),
    )
    val roomDash = PathEffect.dashPathEffect(
        floatArrayOf(5.dp.toPx() * s, 4.dp.toPx() * s),
    )

    drawRect(colors.surfaceAlt.copy(alpha = 0.42f))

    val guideTop = mapper.toScreen(Offset(0f, -1.5f), width, height).y
    val guideBottom = mapper.toScreen(Offset(0f, 1.5f), width, height).y
    for (bay in -1..12) {
        val x = mapper.toScreen(Offset(bay + 0.5f, 0f), width, height).x
        drawLine(
            color = colors.borderDivider.copy(alpha = 0.32f),
            start = Offset(x, guideTop),
            end = Offset(x, guideBottom),
            strokeWidth = 0.8.dp.toPx() * s,
            pathEffect = guideDash,
        )
    }

    plan.corridors.forEach { corridor ->
        val screenPath = corridor.points.toScreenPath(mapper, width, height)
        drawPath(
            path = screenPath,
            color = colors.borderDivider.copy(alpha = 0.55f),
            style = Stroke(
                width = corridorWidth + 2.dp.toPx() * s,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawPath(
            path = screenPath,
            color = corridorFill,
            style = Stroke(
                width = corridorWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        drawPath(
            path = screenPath,
            color = colors.borderDivider.copy(alpha = 0.35f),
            style = Stroke(width = 0.8.dp.toPx() * s, pathEffect = guideDash),
        )
    }

    plan.rooms.forEach { room ->
        val path = room.polygon.toScreenPath(mapper, width, height)
        val selected = room.id == selectedRoomId
        val sideTint = room.side.tint(colors)
        drawPath(
            path = path,
            color = if (selected) {
                colors.accentMain.copy(alpha = 0.26f)
            } else {
                sideTint.copy(alpha = 0.14f)
            },
        )
        if (selected) {
            drawPath(
                path = path,
                color = colors.accentMain.copy(alpha = 0.18f),
                style = Stroke(width = 6.dp.toPx() * s, join = StrokeJoin.Round),
            )
        }
        drawPath(
            path = path,
            color = when {
                selected -> colors.accentMain
                room.provisional -> colors.warning
                else -> sideTint.copy(alpha = 0.62f)
            },
            style = Stroke(
                width = if (selected) 2.dp.toPx() * s else 1.1.dp.toPx() * s,
                join = StrokeJoin.Round,
                pathEffect = if (room.provisional && !selected) roomDash else null,
            ),
        )
        val door = mapper.toScreen(room.doorPoint, width, height)
        val gap = 4.dp.toPx() * s
        drawLine(
            color = corridorFill,
            start = Offset(door.x - gap, door.y),
            end = Offset(door.x + gap, door.y),
            strokeWidth = 2.2.dp.toPx() * s,
        )
    }

    plan.facilities.forEach { facility ->
        val center = mapper.toScreen(facility.point, width, height)
        val halfWidth = 0.34f * unitX
        val halfHeight = 0.13f * unitY
        val badgeTopLeft = Offset(center.x - halfWidth, center.y - halfHeight)
        val badgeSize = Size(halfWidth * 2f, halfHeight * 2f)
        val corner = CornerRadius(6.dp.toPx() * s)
        drawRoundRect(
            color = colors.surfaceAlt.copy(alpha = 0.96f),
            topLeft = badgeTopLeft,
            size = badgeSize,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = colors.borderDivider.copy(alpha = 0.85f),
            topLeft = badgeTopLeft,
            size = badgeSize,
            cornerRadius = corner,
            style = Stroke(width = 0.9.dp.toPx() * s),
        )
        val label = measure("梯", 8f * s, colors.textSecondary, FontWeight.Medium, textMeasurer)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                center.x - label.size.width / 2f,
                center.y - label.size.height / 2f,
            ),
        )
    }

    route?.let {
        drawRoute(
            plan = plan,
            route = it,
            mapper = mapper,
            width = width,
            height = height,
            scale = s,
            colors = colors,
            textMeasurer = textMeasurer,
        )
    }

    plan.rooms.forEach { room ->
        val center = mapper.toScreen(room.labelPoint, width, height)
        val selected = room.id == selectedRoomId
        val fontSize = (if (room.shortLabel.length <= 2) 10f else 8.5f) * s
        val layout = textMeasurer.measure(
            text = AnnotatedString(room.shortLabel),
            style = TextStyle(
                color = if (selected) colors.accentMain else colors.textPrimary,
                fontSize = fontSize.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            maxLines = 1,
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                center.x - layout.size.width / 2f,
                center.y - layout.size.height / 2f,
            ),
        )
    }

    val corridorLabel = measure("主走廊（示意）", 8f * s, colors.textTertiary.copy(alpha = 0.75f), FontWeight.Medium, textMeasurer)
    val corridorLabelCenter = mapper.toScreen(Offset(6.4f, 0.16f), width, height)
    drawText(
        textLayoutResult = corridorLabel,
        topLeft = Offset(
            corridorLabelCenter.x - corridorLabel.size.width / 2f,
            corridorLabelCenter.y - corridorLabel.size.height / 2f,
        ),
    )

    val rowLabelStyle = TextStyle(
        color = colors.textTertiary.copy(alpha = 0.9f),
        fontSize = (9f * s).sp,
        fontWeight = FontWeight.Medium,
    )
    drawCenteredText("北区", mapper.toScreen(Offset(0.5f, -1.52f), width, height), textMeasurer, rowLabelStyle)
    drawCenteredText("南区", mapper.toScreen(Offset(0.5f, 1.52f), width, height), textMeasurer, rowLabelStyle)
    drawCenteredText("东", mapper.toScreen(Offset(-0.78f, 0f), width, height), textMeasurer, rowLabelStyle)
    drawCenteredText("西", mapper.toScreen(Offset(12.78f, 0f), width, height), textMeasurer, rowLabelStyle)

    val watermark = textMeasurer.measure(
        text = AnnotatedString("拓扑示意 · 未按实际比例"),
        style = TextStyle(
            color = colors.textTertiary.copy(alpha = 0.24f),
            fontSize = (12f * s).sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    rotate(degrees = -14f, pivot = Offset(width / 2f, height / 2f)) {
        drawText(
            textLayoutResult = watermark,
            topLeft = Offset(
                width / 2f - watermark.size.width / 2f,
                height / 2f - watermark.size.height - 6.dp.toPx() * s,
            ),
        )
    }

    route?.let { drawRouteMarkers(plan, it, mapper, width, height, s, colors) }
}

private fun DrawScope.drawRoute(
    plan: IndoorFloorPlan,
    route: IndoorRoute,
    mapper: IndoorCoordinateMapper,
    width: Float,
    height: Float,
    scale: Float,
    colors: PocketXiaoliColors,
    textMeasurer: TextMeasurer,
) {
    val room = plan.room(route.roomId)
    val points = route.nodeIds.mapNotNull { nodeId ->
        if (room != null && nodeId == room.doorNodeId) {
            mapper.toScreen(room.doorPoint, width, height)
        } else {
            plan.node(nodeId)?.let { mapper.toScreen(it.point, width, height) }
        }
    }.distinct()
    if (points.size < 2) return

    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    drawPath(
        path = path,
        color = colors.surfaceCard.copy(alpha = 0.94f),
        style = Stroke(
            width = 8.dp.toPx() * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
    drawPath(
        path = path,
        color = colors.accentMain,
        style = Stroke(
            width = 3.2.dp.toPx() * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    val longest = points.zipWithNext().maxByOrNull { (from, to) -> (to - from).getDistance() }
    longest?.let { (from, to) ->
        drawArrowHead(from, to, colors.surfaceCard, 11.dp.toPx() * scale)
        drawArrowHead(from, to, colors.accentMain, 8.dp.toPx() * scale)
    }

    val origin = points.first()
    val startLabel = textMeasurer.measure(
        text = AnnotatedString("起点"),
        style = TextStyle(
            color = colors.success,
            fontSize = (8f * scale).sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
    )
    drawText(
        textLayoutResult = startLabel,
        topLeft = Offset(
            origin.x + 9.dp.toPx() * scale,
            origin.y - startLabel.size.height / 2f,
        ),
    )
}

private fun DrawScope.drawRouteMarkers(
    plan: IndoorFloorPlan,
    route: IndoorRoute,
    mapper: IndoorCoordinateMapper,
    width: Float,
    height: Float,
    scale: Float,
    colors: PocketXiaoliColors,
) {
    val room = plan.room(route.roomId) ?: return
    val start = plan.node(plan.startNodeId)?.let { mapper.toScreen(it.point, width, height) } ?: return
    val end = mapper.toScreen(room.doorPoint, width, height)
    drawMarker(start, colors.success, scale, colors)
    drawMarker(end, colors.accentMain, scale, colors)
}

private fun DrawScope.drawMarker(
    center: Offset,
    color: Color,
    scale: Float,
    colors: PocketXiaoliColors,
) {
    val radius = 7.dp.toPx() * scale
    drawCircle(color.copy(alpha = 0.22f), radius + 5.dp.toPx() * scale, center)
    drawCircle(colors.surfaceCard, radius + 2.dp.toPx() * scale, center)
    drawCircle(color, radius, center)
    drawCircle(colors.surfaceCard, radius * 0.32f, center)
}

private fun DrawScope.drawArrowHead(
    from: Offset,
    to: Offset,
    color: Color,
    headSize: Float,
) {
    val delta = to - from
    val length = delta.getDistance()
    if (length < 1f) return
    val unit = delta / length
    val anchor = from + delta * 0.6f
    val back = anchor - unit * headSize
    val side = Offset(-unit.y, unit.x) * (headSize * 0.55f)
    val arrow = Path().apply {
        moveTo(anchor.x, anchor.y)
        lineTo(back.x + side.x, back.y + side.y)
        lineTo(back.x - side.x, back.y - side.y)
        close()
    }
    drawPath(arrow, color)
}

private fun DrawScope.drawCenteredText(
    text: String,
    center: Offset,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val layout = textMeasurer.measure(AnnotatedString(text), style = style, maxLines = 1)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}

private fun List<Offset>.toScreenPath(
    mapper: IndoorCoordinateMapper,
    width: Float,
    height: Float,
): Path {
    val path = Path()
    forEachIndexed { index, point ->
        val screen = mapper.toScreen(point, width, height)
        if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
    }
    if (isNotEmpty()) path.close()
    return path
}

private fun measure(
    text: String,
    sizeSp: Float,
    color: Color,
    weight: FontWeight,
    textMeasurer: TextMeasurer,
) = textMeasurer.measure(
    text = AnnotatedString(text),
    style = TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = weight),
    maxLines = 1,
)

private fun IndoorSide.tint(colors: PocketXiaoliColors): Color =
    when (this) {
        IndoorSide.North -> colors.info
        IndoorSide.South -> colors.warning
    }
