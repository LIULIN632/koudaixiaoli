package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomDirectorySearch
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 楼层地图面板（可嵌入任意页面）。
 *
 * 与 `RoomLayoutScreen` 的区别：那个是全屏管理页（增删改查）；本组件默认只读，
 * 传入 [onAddRoom] / [onUpdateRoom] 后即可在课表「地图模式」里原地新建、编辑教室。
 * 两者共用 `RoomDirectorySearch` + [RoomLayoutGrid]，不重复布局逻辑。
 *
 * @param onRoomClick 点某个房间；课表页用它跳到全屏页并高亮该房间
 * @param onOpenFullScreen 右上角「管理」入口
 */
@Composable
fun RoomFloorMapPanel(
    allCells: List<RoomCell>,
    campusId: String,
    modifier: Modifier = Modifier,
    /** 初始列数；用户还可以在面板里快捷修改 */
    columns: Int = 4,
    /** 初始行数；用户还可以在面板里快捷修改 */
    rows: Int = 4,
    /** 是否显示快捷网格大小设置 */
    showGridSizeSettings: Boolean = true,
    /** 课表外观：格子高度，默认与课表设置共用调用方传入值 */
    cellHeight: Dp = 56.dp,
    /** 课表外观：格子间距，默认 1dp，接近课表表格的细线间距 */
    gap: Dp = 1.dp,
    cornerRadius: Int = 4,
    borderStyle: Int = 0,
    textSize: Int = 12,
    opacity: Float = 1f,
    /** 外部状态回调；传 null 时只在面板内部保存 */
    onColumnsChange: ((Int) -> Unit)? = null,
    onRowsChange: ((Int) -> Unit)? = null,
    /** 锁定到某一栋楼（如从「地图导航」点教学楼进来）；为 null 时显示楼栋选择器 */
    fixedBuilding: String? = null,
    onRoomClick: (RoomCell) -> Unit = {},
    onOpenFullScreen: (() -> Unit)? = null,
    /** 新增教室；非 null 时点空格可原地新建（不跳页） */
    onAddRoom: ((RoomCell) -> Unit)? = null,
    /** 编辑已有教室；非 null 时点房间可原地编辑（不跳页） */
    onUpdateRoom: ((RoomCell) -> Unit)? = null,
    /** 长按拖拽落位；null 时拖动不写回 */
    onMoveRoom: ((RoomCell, Int, Int) -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val canAdd = onAddRoom != null
    val canEdit = onUpdateRoom != null
    val editable = canAdd || canEdit
    var editingCell by remember { mutableStateOf<RoomCell?>(null) }
    var building by remember(fixedBuilding) { mutableStateOf(fixedBuilding.orEmpty()) }
    var floor by remember { mutableStateOf("") }
    var gridColumns by rememberSaveable(columns) { mutableIntStateOf(columns) }
    var gridRows by rememberSaveable(rows) { mutableIntStateOf(rows) }

    val buildings = remember(allCells, campusId) {
        RoomDirectorySearch.buildings(allCells, campusId)
    }
    LaunchedEffect(buildings) {
        if (building !in buildings) building = buildings.firstOrNull().orEmpty()
    }
    val floors = remember(allCells, campusId, building) {
        RoomDirectorySearch.floors(allCells, campusId, building)
    }
    LaunchedEffect(floors) {
        if (floor !in floors) floor = floors.firstOrNull().orEmpty()
    }
    val cells = remember(allCells, campusId, building, floor) {
        RoomDirectorySearch.cells(allCells, campusId, building, floor)
    }
    // 房间内容需要的行数；用户设的行数不能小于它，否则房间会被挤出可视网格。
    val contentRows = remember(cells, gridColumns) {
        RoomDirectorySearch.layoutGrid(cells, gridColumns).rows
    }
    val effectiveRows = maxOf(gridRows, contentRows)
    val grid = remember(cells, gridColumns, effectiveRows) {
        RoomDirectorySearch.layoutGrid(cells, gridColumns, minRows = effectiveRows)
    }
    val typesOnFloor = remember(cells) { cells.map { it.roomType }.distinct().sortedBy { it.ordinal } }

    fun roomSeed(row: Int = -1, col: Int = -1): RoomCell = RoomCell(
        id = "",
        campusId = campusId,
        building = building,
        floor = floor,
        room = "",
        row = row,
        col = col,
    )

    if (allCells.isEmpty()) {
        // 一間都没录：给个明确出口，而不是空白
        Column(
            modifier = modifier.padding(skin.spaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("还没有教室数据", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceSmall))
            Text(
                "到「教室分布图管理」录入后，这里会显示楼层平面图",
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
            if (editable || onOpenFullScreen != null) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    if (canAdd) {
                        TextButton(onClick = { editingCell = roomSeed() }) { Text("新建教室") }
                    }
                    if (onOpenFullScreen != null) {
                        TextButton(onClick = onOpenFullScreen) { Text("去录入") }
                    }
                }
            }
        }
        editingCell?.let { initial ->
            RoomEditorHost(
                initial = initial,
                onAddRoom = onAddRoom,
                onUpdateRoom = onUpdateRoom,
                onDismiss = { editingCell = null },
            )
        }
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.spaceMd)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (cells.isNotEmpty()) {
                TextButton(
                    onClick = {
                        shareRoomLayout(
                            context = context,
                            cells = cells,
                            title = listOf(building, floor).filter { it.isNotBlank() }.joinToString(" "),
                        )
                    },
                ) { Text("分享", fontSize = 12.sp) }
            }
            if (canAdd) {
                TextButton(onClick = { editingCell = roomSeed() }) { Text("+ 新建", fontSize = 12.sp) }
            }
            if (onOpenFullScreen != null) {
                TextButton(onClick = onOpenFullScreen) { Text("管理", fontSize = 12.sp) }
            }
        }

        if (showGridSizeSettings) {
            Spacer(Modifier.height(skin.spaceSmall))
            GridSizeQuickSettings(
                columns = gridColumns,
                rows = effectiveRows,
                onColumnsChange = { value ->
                    gridColumns = value
                    onColumnsChange?.invoke(value)
                },
                onRowsChange = { value ->
                    gridRows = value.coerceAtLeast(contentRows)
                    onRowsChange?.invoke(gridRows)
                },
            )
            Spacer(Modifier.height(skin.spaceSmall))
        }

        if (fixedBuilding == null) {
            ChipRow(label = "楼栋", options = buildings, selected = building) { building = it }
        }
        ChipRow(label = "楼层", options = floors, selected = floor) { floor = it }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(skin.radiusCard),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
        ) {
            Column(modifier = Modifier.padding(skin.spaceMd)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    CompassIndicator()
                }
                Spacer(Modifier.height(skin.spaceSm))
                if (cells.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        Text("这一层还没有房间", fontSize = 13.sp, color = colors.textTertiary)
                        if (canAdd) {
                            TextButton(onClick = { editingCell = roomSeed() }) { Text("新建", fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(skin.spaceSm))
                }
                if (cells.isNotEmpty() || canAdd) {
                    RoomLayoutGrid(
                        layout = grid,
                        highlightedRoomId = null,
                        cellHeight = cellHeight,
                        gap = gap,
                        cornerRadius = cornerRadius,
                        borderStyle = borderStyle,
                        textSize = textSize,
                        opacity = opacity,
                        onRoomClick = { cell ->
                            if (canEdit) editingCell = cell else onRoomClick(cell)
                        },
                        onRoomMove = { cell, row, col -> onMoveRoom?.invoke(cell, row, col) },
                        onEmptySlotClick = { row, col ->
                            if (canAdd) editingCell = roomSeed(row, col)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        RoomTypeLegend(types = typesOnFloor)
        Spacer(Modifier.height(skin.spaceSmall))
        Text(
            if (editable) "点房间编辑 / 点 + 新建；共 " + cells.size + " 个房间"
            else "点房间可跳到管理页查看详情；共 " + cells.size + " 个房间",
            fontSize = 11.sp,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(skin.spaceMd))
    }

    editingCell?.let { initial ->
        RoomEditorHost(
            initial = initial,
            onAddRoom = onAddRoom,
            onUpdateRoom = onUpdateRoom,
            onDismiss = { editingCell = null },
        )
    }
}

@Composable
private fun RoomEditorHost(
    initial: RoomCell?,
    onAddRoom: ((RoomCell) -> Unit)?,
    onUpdateRoom: ((RoomCell) -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cell = initial ?: return
    RoomEditDialog(
        initial = cell,
        onConfirm = { edited ->
            if (cell.id.isBlank()) onAddRoom?.invoke(edited) else onUpdateRoom?.invoke(edited)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/** 一行可选中的标签（楼栋 / 楼层） */
@Composable
private fun ChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val skin = LocalSkin.current
    if (options.isEmpty()) return
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = colors.textTertiary)
        Spacer(Modifier.padding(horizontal = skin.spaceXs))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(skin.radiusMicro))
                        .background(
                            if (isSelected) colors.accentMain else colors.surfaceHighlight
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = option.ifBlank { "未命名" },
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else colors.textSecondary,
                    )
                }
            }
        }
    }
}
