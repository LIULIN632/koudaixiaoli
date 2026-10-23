package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.ui.map.tintColor
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 建筑档案。
 *
 * 只展示能从轮廓与已录房间算出来的事实：占地/尺寸按轮廓计算，
 * 楼层与房间按「教室分布图」聚合，来源明确标 OSM / 影像描点；
 * 没有数据的部分留空态，不补猜用途、院系或室内布局。
 */
@Composable
internal fun Campus3DBuildingProfileScreen(
    building: Campus3DBuildingShape,
    facts: BuildingProfileFacts,
    todayClassCount: Int,
    showFloorPlans: Boolean,
    onOpenFloor: (String) -> Unit,
    onOpenAllFloors: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyMid))
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(name = facts.name, onClose = onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                ProfileHero(
                    building = building,
                    facts = facts,
                    todayClassCount = todayClassCount,
                )
                Spacer(Modifier.height(12.dp))
                ProfileStats(facts = facts)
                Spacer(Modifier.height(12.dp))
                RoomSection(
                    facts = facts,
                    showFloorPlans = showFloorPlans,
                    onOpenFloor = onOpenFloor,
                )
                Spacer(Modifier.height(12.dp))
                ProvenanceSection(facts = facts)
                Spacer(Modifier.height(20.dp))
            }
            if (showFloorPlans) {
                ProfileBottomBar(
                    levels = facts.levels,
                    onOpenAllFloors = onOpenAllFloors,
                )
            }
        }
    }
}

@Composable
private fun ProfileTopBar(name: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回 3D 沙盘",
                tint = Palette.LabelText,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "建筑档案",
                color = Palette.LabelText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = name,
                color = Palette.LabelTextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfileHero(
    building: Campus3DBuildingShape,
    facts: BuildingProfileFacts,
    todayClassCount: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileChip(
                    text = categoryLabel(facts.category),
                    textColor = Palette.ChipText,
                    background = Palette.ChipBg,
                )
                SourceChip(source = facts.outlineSource)
                if (todayClassCount > 0) {
                    ProfileChip(
                        text = "今日 $todayClassCount 节课",
                        textColor = Palette.TodayClass,
                        background = Palette.TodayClass.copy(alpha = 0.14f),
                        borderColor = Palette.TodayClass.copy(alpha = 0.45f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = facts.name,
                color = Palette.LabelText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts.sourceName.isNotBlank() && facts.sourceName != facts.name) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "又称 · ${facts.sourceName}",
                    color = Palette.LabelTextDim,
                    fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            FootprintOverview(building = building)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "东西 ${facts.widthMeters.roundToInt()} 米 · " +
                        "南北 ${facts.depthMeters.roundToInt()} 米",
                    color = Palette.LabelTextDim,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "占地约 ${formatArea(facts.footprintAreaSqm)}",
                    color = Palette.Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SourceChip(source: BuildingOutlineSource) {
    when (source) {
        BuildingOutlineSource.OpenStreetMap -> ProfileChip(
            text = "OSM 实测",
            textColor = Palette.LabelTextDim,
            background = Palette.SkyTop.copy(alpha = 0.45f),
        )
        BuildingOutlineSource.Traced -> ProfileChip(
            text = "影像描点",
            textColor = Palette.Poi,
            background = Palette.Poi.copy(alpha = 0.12f),
            borderColor = Palette.Poi.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun ProfileChip(
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    borderColor: androidx.compose.ui.graphics.Color? = null,
) {
    Surface(
        shape = CircleShape,
        color = background,
        border = borderColor?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FootprintOverview(building: Campus3DBuildingShape) {
    val bounds = building.bounds
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Palette.SkyTop.copy(alpha = 0.82f),
                        Palette.SkyMid.copy(alpha = 0.5f),
                    )
                )
            )
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            val gridStep = 20.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Palette.LabelBorder.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += gridStep
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Palette.LabelBorder.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += gridStep
            }

            val width = bounds.width.coerceAtLeast(1f)
            val height = bounds.height.coerceAtLeast(1f)
            val scale = min(size.width / width, size.height / height) * 0.82f
            val originX = (size.width - width * scale) * 0.5f - bounds.minX * scale
            val originY = (size.height - height * scale) * 0.5f - bounds.minZ * scale

            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addProfileRing(building.footprint, originX, originY, scale)
                building.holes.forEach { addProfileRing(it, originX, originY, scale) }
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    listOf(
                        Palette.Accent.copy(alpha = 0.34f),
                        Palette.Accent.copy(alpha = 0.1f),
                    )
                ),
            )
            drawPath(
                path = path,
                color = Palette.Accent.copy(alpha = 0.2f),
                style = Stroke(width = 7.dp.toPx()),
            )
            drawPath(
                path = path,
                color = Palette.Accent.copy(alpha = 0.9f),
                style = Stroke(width = 1.8.dp.toPx()),
            )
            drawCircle(
                color = Palette.White,
                radius = 2.5.dp.toPx(),
                center = Offset(
                    originX + building.centroid.x * scale,
                    originY + building.centroid.z * scale,
                ),
            )
        }
        Text(
            text = "轮廓俯视",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            color = Palette.LabelTextDim,
            fontSize = 9.sp,
        )
        Text(
            text = "N ↑",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            color = Palette.LabelText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun Path.addProfileRing(
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
private fun ProfileStats(facts: BuildingProfileFacts) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = if (facts.levelsEstimated) "层数 · 推算" else "层数",
                value = "${facts.levels} 层",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "高度 · 示意",
                value = formatHeight(facts.heightMeters),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "占地 · 按轮廓",
                value = formatArea(facts.footprintAreaSqm),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "已录教室",
                value = if (facts.rooms.isEmpty) "—" else "${facts.rooms.classrooms} 间",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = label,
                color = Palette.LabelTextDim,
                fontSize = 10.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = Palette.Accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoomSection(
    facts: BuildingProfileFacts,
    showFloorPlans: Boolean,
    onOpenFloor: (String) -> Unit,
) {
    val rooms = facts.rooms
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "室内分布",
                    color = Palette.LabelText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (rooms.isEmpty) {
                        "暂无房间数据"
                    } else {
                        "${rooms.coveredFloors} / ${facts.levels} 层有数据"
                    },
                    color = Palette.LabelTextDim,
                    fontSize = 10.sp,
                )
            }
            if (rooms.isEmpty) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "这栋楼还没有录入室内房间",
                    color = Palette.LabelText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (showFloorPlans) {
                        "平面图目前只展示轮廓，不根据轮廓推测内部布局。"
                    } else {
                        "没有房间数据时，建筑档案只展示轮廓与体量信息。"
                    },
                    color = Palette.LabelTextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${rooms.classrooms} 间教室",
                    color = Palette.Accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rooms.types.forEach { item ->
                        RoomTypeChip(type = item.type, count = item.count)
                    }
                }
                Spacer(Modifier.height(12.dp))
                rooms.floors.forEach { floor ->
                    FloorRoomRow(
                        floor = floor,
                        onClick = if (showFloorPlans) {
                            { onOpenFloor(floor.rawFloor) }
                        } else {
                            null
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (showFloorPlans) {
                    Text(
                        text = "点楼层可直接打开对应平面图",
                        color = Palette.LabelTextDim.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTypeChip(type: RoomCellType, count: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Palette.SkyTop.copy(alpha = 0.45f))
            .border(1.dp, Palette.CardBorder, CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(type.tintColor(), CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = type.label,
            color = Palette.LabelTextDim,
            fontSize = 10.sp,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = count.toString(),
            color = Palette.LabelText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FloorRoomRow(floor: BuildingFloorRooms, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = Palette.Accent.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Palette.Accent.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = floor.label,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    color = Palette.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (floor.classrooms > 0) {
                        "教室 ${floor.classrooms}"
                    } else {
                        "无教室"
                    },
                    color = Palette.LabelText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "共 ${floor.totalEntries} 项房间与设施",
                    color = Palette.LabelTextDim,
                    fontSize = 9.sp,
                )
            }
            if (onClick != null) {
                Text("›", color = Palette.LabelTextDim, fontSize = 18.sp)
            }
        }
    }

    if (onClick == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Palette.SkyTop.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, Palette.CardBorder),
        ) {
            content()
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Palette.SkyTop.copy(alpha = 0.4f),
            border = BorderStroke(1.dp, Palette.CardBorder),
        ) {
            content()
        }
    }
}

@Composable
private fun ProvenanceSection(facts: BuildingProfileFacts) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "数据说明",
                color = Palette.LabelText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            val outlineText = when (facts.outlineSource) {
                BuildingOutlineSource.OpenStreetMap -> buildString {
                    append("OpenStreetMap 实测轮廓（ODbL 1.0）。")
                    if (facts.outlineNote.isNotBlank()) append(facts.outlineNote)
                }
                BuildingOutlineSource.Traced -> facts.outlineNote.ifBlank {
                    "影像描点轮廓，非 OSM 数据。"
                }
            }
            ProvenanceLine(title = "轮廓", body = outlineText)
            ProvenanceLine(
                title = "占地与尺寸",
                body = "按楼栋轮廓计算，不是测绘面积。",
            )
            ProvenanceLine(
                title = "楼层与高度",
                body = if (facts.levelsEstimated) {
                    "层数按建筑面积推算；高度仅用于体量示意。"
                } else {
                    "层数来自 OSM；高度仅用于体量示意。"
                },
            )
            ProvenanceLine(
                title = "室内房间",
                body = "来自实拍指引牌与手动录入；未录入的房间不做推测。",
            )
        }
    }
}

@Composable
private fun ProvenanceLine(title: String, body: String) {
    Row(modifier = Modifier.padding(bottom = 9.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .background(Palette.Accent.copy(alpha = 0.8f), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                color = Palette.LabelText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                color = Palette.LabelTextDim,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun ProfileBottomBar(levels: Int, onOpenAllFloors: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Palette.Transparent, Palette.SkyTop)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            onClick = onOpenAllFloors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            color = Palette.Accent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "查看各层平面图",
                    color = Palette.CardSolid,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$levels 层  ›",
                    color = Palette.CardSolid.copy(alpha = 0.72f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatArea(value: Double): String {
    val rounded = max(0, value.roundToInt())
    return if (rounded >= 10_000) {
        val wan = rounded / 10_000.0
        String.format(Locale.CHINA, "%.1f 万㎡", wan)
    } else {
        String.format(Locale.CHINA, "%,d ㎡", rounded)
    }
}

private fun formatHeight(value: Double): String {
    val rounded = (value * 10.0).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) "${rounded.toInt()} 米" else "$rounded 米"
}
