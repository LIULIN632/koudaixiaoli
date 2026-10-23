package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.data.map.RoomDirectorySearch
import com.pocket.xiaoli.ui.map.RoomLayoutGrid
import com.pocket.xiaoli.ui.map.tintColor
import com.pocket.xiaoli.ui.share.AppShare
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 楼栋各层平面图。
 *
 * 楼层数来自 OSM 的 `building:levels`，室内房间来自「教室分布图」；
 * 没有房间数据的楼层只画 OSM 实测外轮廓 —— 不根据轮廓猜内部布局。
 */
@Composable
fun Campus3DFloorPlanScreen(
    building: Campus3DBuildingShape,
    campusId: String,
    roomCells: List<RoomCell>,
    onClose: () -> Unit,
    /** 从搜索直达时指定楼层（"4F" 或原始 "4"），无效则回落到有房间的首层。 */
    initialFloor: String? = null,
    /** 参数是楼栋名与当前楼层（优先用已录入楼层的原始写法，便于管理页对上）。 */
    onManageRooms: ((String, String) -> Unit)? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val levels = building.levels.coerceAtLeast(1)
    val buildingName = building.name.trim()

    val buildingCells = remember(roomCells, campusId, buildingName) {
        if (buildingName.isEmpty()) {
            emptyList()
        } else {
            roomCells.filter {
                it.campusId.equals(campusId, ignoreCase = true) &&
                    it.building.trim() == buildingName
            }
        }
    }
    val floorOptions = remember(building.id, levels, buildingCells) {
        floorOptionsFor(levels, buildingCells)
    }
    val defaultFloor = remember(building.id, floorOptions, buildingCells, initialFloor) {
        val requested = initialFloor
            ?.takeIf { it.isNotBlank() }
            ?.let(::floorLabel)
            ?.takeIf { it in floorOptions }
        requested ?: run {
            val floorsWithRooms = buildingCells.mapTo(mutableSetOf()) { floorLabel(it.floor) }
            floorOptions.firstOrNull { it in floorsWithRooms } ?: floorOptions.first()
        }
    }
    var selectedFloor by remember(building.id) { mutableStateOf(defaultFloor) }
    LaunchedEffect(floorOptions) {
        if (selectedFloor !in floorOptions) selectedFloor = defaultFloor
    }

    val floorCells = remember(buildingCells, selectedFloor) {
        buildingCells
            .filter { floorLabel(it.floor) == selectedFloor }
            .sortedWith(compareBy({ it.row.coerceAtLeast(0) }, { it.col.coerceAtLeast(0) }, { it.room }))
    }
    val grid = remember(floorCells) {
        RoomDirectorySearch.layoutGrid(floorCells, columns = 4, minRows = 3)
    }
    val planRows = grid.rows.coerceAtLeast(1)
    val planBaseHeight = 52.dp * planRows + 1.dp * (planRows - 1)
    val roomTypes = remember(floorCells) {
        floorCells.map { it.roomType }.distinct().sortedBy { it.ordinal }
    }
    val selectedFloorRaw = floorCells.firstOrNull()?.floor?.takeIf { it.isNotBlank() } ?: selectedFloor
    val zoom = rememberPlanZoomState(resetKey = selectedFloor)

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surfaceBase) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回 3D 沙盘", tint = colors.textPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildingName.ifBlank { "未命名楼栋" },
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("各层平面图 · $levels 层")
                            if (building.levelsEstimated) append("（按面积推算）")
                        },
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = skin.spaceWide),
            ) {
                Spacer(Modifier.height(skin.spaceSmall))
                Text("选择楼层", fontSize = 11.sp, color = colors.textTertiary)
                Spacer(Modifier.height(skin.spaceSm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                ) {
                    floorOptions.forEach { floor ->
                        val isSelected = floor == selectedFloor
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Tab,
                                    onClick = { selectedFloor = floor },
                                ),
                            shape = CircleShape,
                            color = if (isSelected) colors.accentMain else colors.surfaceAlt,
                            border = if (isSelected) null else BorderStroke(1.dp, colors.borderDivider),
                        ) {
                            Text(
                                text = floor,
                                modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(skin.spaceMd))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                ) {
                    Column(modifier = Modifier.padding(skin.spaceLg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$selectedFloor 平面图",
                                modifier = Modifier.semantics { heading() },
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (floorCells.isEmpty()) "暂无房间数据" else "${floorCells.size} 个房间",
                                color = colors.textTertiary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.height(skin.spaceMd))
                        if (floorCells.isEmpty()) {
                            BuildingOutlinePlan(
                                building = building,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(skin.spaceMd))
                            Text(
                                text = "楼栋外轮廓来自 OSM；室内房间尚未录入，这里不做推测。",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                            )
                        } else {
                            PlanZoomBar(state = zoom)
                            Spacer(Modifier.height(skin.spaceSm))
                            ZoomablePlan(state = zoom, baseHeight = planBaseHeight) { scale ->
                                RoomLayoutGrid(
                                    layout = grid,
                                    cellHeight = 52.dp * scale,
                                    gap = 1.dp * scale,
                                    cornerRadius = (10 * scale).roundToInt(),
                                    borderStyle = 1,
                                    textSize = (12 * scale).roundToInt(),
                                    showEmptySlots = false,
                                    roomsClickable = false,
                                )
                            }
                            if (roomTypes.isNotEmpty()) {
                                Spacer(Modifier.height(skin.spaceMd))
                                RoomTypeSummary(types = roomTypes)
                            }
                        }
                    }
                }

                if (buildingName.isNotEmpty() && onManageRooms != null) {
                    Spacer(Modifier.height(skin.spaceMd))
                    Surface(
                        onClick = { onManageRooms(buildingName, selectedFloorRaw) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(skin.radiusCard),
                        color = colors.accentMain,
                    ) {
                        Text(
                            text = if (floorCells.isEmpty()) "去「教室分布图」录入房间" else "管理教室分布",
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (buildingName.isEmpty()) {
                    Spacer(Modifier.height(skin.spaceMd))
                    Text(
                        text = "该楼栋在 OSM 中暂无名称，无法与「教室分布图」里的房间关联。",
                        color = colors.textTertiary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }

                Spacer(Modifier.height(skin.spaceSm))
                FloorPlanAttribution()
                Spacer(Modifier.height(skin.spaceLg))
            }
        }
    }
}

@Composable
private fun BuildingOutlinePlan(
    building: Campus3DBuildingShape,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val bounds = building.bounds
    val accent = colors.accentMain

    Box(
        modifier = modifier
            .height(224.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceAlt.copy(alpha = 0.45f))
            .clearAndSetSemantics {
                contentDescription = "OSM 实测外轮廓，东西约 ${bounds.width.roundToInt()} 米，" +
                    "南北约 ${bounds.height.roundToInt()} 米，指北针向上；室内房间尚未录入。"
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
        ) {
            val width = bounds.width.coerceAtLeast(1f)
            val height = bounds.height.coerceAtLeast(1f)
            val scale = min(size.width / width, size.height / height) * 0.84f
            val originX = (size.width - width * scale) * 0.5f - bounds.minX * scale
            val originY = (size.height - height * scale) * 0.5f - bounds.minZ * scale

            val gridStep = 22.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = colors.borderDivider.copy(alpha = 0.22f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = colors.borderDivider.copy(alpha = 0.22f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += gridStep
            }

            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRing(building.footprint, originX, originY, scale)
                building.holes.forEach { addRing(it, originX, originY, scale) }
            }
            drawPath(path, accent.copy(alpha = 0.14f))
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.78f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Text(
            text = "OSM 轮廓",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = colors.textTertiary,
            fontSize = 10.sp,
        )
        Text(
            text = "N ↑",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            color = colors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "约 ${bounds.width.roundToInt()} × ${bounds.height.roundToInt()} 米",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            color = colors.textTertiary,
            fontSize = 10.sp,
        )
    }
}

private fun Path.addRing(
    points: List<Vec2>,
    originX: Float,
    originY: Float,
    scale: Float,
) {
    if (points.size < 3) return
    moveTo(originX + points[0].x * scale, originY + points[0].z * scale)
    for (index in 1 until points.size) {
        lineTo(originX + points[index].x * scale, originY + points[index].z * scale)
    }
    close()
}

@Composable
private fun RoomTypeSummary(types: List<RoomCellType>) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        types.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(type.tintColor()),
                )
                Spacer(Modifier.width(5.dp))
                Text(type.label, color = colors.textTertiary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FloorPlanAttribution() {
    val context = LocalContext.current
    Surface(
        onClick = { AppShare.openUrl(context, "https://www.openstreetmap.org/copyright") },
        color = Color.Transparent,
        shape = CircleShape,
    ) {
        Text(
            text = "楼栋轮廓 © OpenStreetMap 贡献者 · ODbL 1.0",
            modifier = Modifier.padding(vertical = 6.dp),
            color = LocalPocketXiaoliColors.current.textTertiary.copy(alpha = 0.8f),
            fontSize = 9.sp,
        )
    }
}

private val FLOOR_PATTERN = Regex("""^第?\s*(\d+)\s*(层|F|f)?$""")

/** "1" / "1F" / "1 层" / "第1层" 统一成 "1F"；非数字楼层保留原文。 */
internal fun floorLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "1F"
    val match = FLOOR_PATTERN.matchEntire(trimmed)
    if (match != null) {
        val parsed = match.groupValues[1].toIntOrNull() ?: 0
        return "${parsed}F"
    }
    return trimmed.uppercase()
}

/** 楼层选项 = OSM 层数 ∪ 已有房间数据的楼层，数字楼层升序、异形楼层排最后。 */
internal fun floorOptionsFor(levels: Int, cells: List<RoomCell>): List<String> {
    val known = (1..levels.coerceAtLeast(1)).map { "${it}F" }
    val fromCells = cells.map { floorLabel(it.floor) }
    return (known + fromCells)
        .distinct()
        .sortedWith(compareBy({ it.dropLast(1).toIntOrNull() ?: Int.MAX_VALUE }, { it }))
}
