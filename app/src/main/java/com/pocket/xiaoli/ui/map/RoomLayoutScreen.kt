package com.pocket.xiaoli.ui.map

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomDirectorySearch
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.util.isCourseInWeek
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import java.time.LocalDate
import java.util.UUID
import com.pocket.xiaoli.ui.theme.LocalSkin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 楼层分布图：一层楼一张格子图，一个格子一间教室/办公室。
 *
 * 只做「看分布」——不含走廊、楼梯、路径规划。
 * 格子位置默认按录入顺序排布；数据里给了 row/col 才按真实方位落位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomLayoutScreen(
    container: AppContainer,
    initialRoomQuery: String = "",
    initialBuilding: String = "",
    initialFloor: String = "",
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val campus by container.selectedCampus.collectAsStateWithLifecycle()
    val allCells by container.roomCells.collectAsStateWithLifecycle()
    val schemeId by container.activeSchemeId.collectAsStateWithLifecycle()
    // ⚠️ 这几个外观设置必须**收集**，不能在 composition 里读 `container.xxx.value`。
    // `StateFlow.value` 是非 Compose 可观察的读取方式 —— 读它不建立订阅，
    // 用户在设置里改了网格高度/圆角/字号，这一页不会重组，看起来"改了没生效"。
    // Compose 的 lint 规则 StateFlowValueCalledInComposition 抓的就是这个（本次恢复 lint 后命中）。
    val gridHeightDp by container.gridHeight.collectAsStateWithLifecycle()
    val cornerRadius by container.cornerRadius.collectAsStateWithLifecycle()
    val borderStyle by container.borderStyle.collectAsStateWithLifecycle()
    val gridTextSize by container.gridTextSize.collectAsStateWithLifecycle()
    val gridOpacity by container.gridOpacity.collectAsStateWithLifecycle()
    // WithLifecycle 版本：`getCoursesByScheme` 是 Room 冷 Flow，写在参数里会每次重组
    // 新建实例并重新注册 InvalidationTracker 观察者。`allTimeSlots` 是 StateFlow，代价小，
    // 但两处保持同一种写法更不容易再退化。
    val courses by container.repository.getCoursesByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val timeSlots by container.repository.allTimeSlots
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 「今日课程」联动所需的上下文
    val week = container.autoCurrentWeek()
    val todayDay = remember { LocalDate.now().dayOfWeek.value }

    var search by remember { mutableStateOf(initialRoomQuery) }
    var building by remember { mutableStateOf(initialBuilding.trim().ifBlank { DEFAULT_BUILDING }) }
    var floor by remember { mutableStateOf(initialFloor.trim().ifBlank { DEFAULT_FLOOR }) }
    var addSeed by remember { mutableStateOf<RoomCell?>(null) }
    var showPaste by remember { mutableStateOf(false) }
    var gridColumns by rememberSaveable { mutableIntStateOf(GRID_COLUMNS) }
    var gridRows by rememberSaveable { mutableIntStateOf(EMPTY_GRID_ROWS) }
    var detail by remember { mutableStateOf<RoomCell?>(null) }
    var editing by remember { mutableStateOf<RoomCell?>(null) }
    // 没有任何教室数据时也能开工：允许先自定义楼栋 / 楼层。
    // 从 3D 楼层图跳进来时把目标楼栋 / 楼层带进来，免得用户在一个空列表里再找一遍。
    val customBuildings = remember {
        mutableStateListOf<String>().apply {
            initialBuilding.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
    val customFloors = remember {
        mutableStateListOf<String>().apply {
            initialFloor.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }
    var newBuildingDialog by remember { mutableStateOf(false) }
    var newFloorDialog by remember { mutableStateOf(false) }

    val buildings = remember(allCells, campus.id) {
        RoomDirectorySearch.buildings(allCells, campus.id)
    }
    val buildingOptions = remember(buildings, customBuildings.size) {
        (customBuildings.toList() + buildings).distinct().ifEmpty { listOf(DEFAULT_BUILDING) }
    }
    LaunchedEffect(buildingOptions) {
        if (building !in buildingOptions) building = buildingOptions.first()
    }
    val floors = remember(allCells, campus.id, building) {
        RoomDirectorySearch.floors(allCells, campus.id, building)
    }
    val floorOptions = remember(floors, customFloors.size, building) {
        (customFloors.toList() + floors).distinct().ifEmpty { listOf(DEFAULT_FLOOR) }
    }
    LaunchedEffect(floorOptions) {
        if (floor !in floorOptions) floor = floorOptions.first()
    }

    val cells = remember(allCells, campus.id, building, floor) {
        RoomDirectorySearch.cells(allCells, campus.id, building, floor)
    }
    val hits = remember(allCells, campus.id, search) {
        RoomDirectorySearch.find(allCells, campus.id, search)
    }
    // 空楼层也给一张可点网格，用户才能点空格子直接添加教室。
    // 用户可以把网格设大；房间内容超过时自动扩展行数。
    val contentRows = remember(cells, gridColumns) {
        RoomDirectorySearch.layoutGrid(cells, gridColumns).rows
    }
    val effectiveRows = maxOf(gridRows, contentRows)
    val grid = remember(cells, gridColumns, effectiveRows) {
        RoomDirectorySearch.layoutGrid(cells, gridColumns, minRows = effectiveRows)
    }
    val typesOnFloor = remember(cells) { cells.map { it.roomType }.distinct().sortedBy { it.ordinal } }

    // 从课表跳进来时：解析教室文本，直接切到对应楼层并高亮
    val target = remember(initialRoomQuery, allCells, campus.id) {
        if (initialRoomQuery.isBlank()) null
        else RoomDirectorySearch.resolve(allCells, campus.id, initialRoomQuery)
    }
    LaunchedEffect(target?.id) {
        target?.let { hit ->
            building = hit.building
            floor = hit.floor
            search = ""
        }
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("教室分布", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            addSeed = RoomCell(
                                id = "",
                                campusId = campus.id,
                                building = building,
                                floor = floor,
                                room = ""
                            )
                        },
                        enabled = building.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Add, "添加教室", tint = colors.accentMain)
                    }
                    IconButton(onClick = {
                        val campusRooms = allCells.filter { it.campusId == campus.id }
                        // 直接导出内置投稿格式（见 docs/BuildingDataFormat.md）
                        val text = buildString {
                            appendLine("# 楼栋|楼层|房间号|用途|类型|容量|行|列|跨行|跨列")
                            campusRooms.forEach { c ->
                                appendLine(
                                    listOf(
                                        c.building,
                                        c.floor,
                                        c.room,
                                        c.name,
                                        c.roomType.key,
                                        if (c.capacity > 0) c.capacity.toString() else "",
                                        if (c.row >= 0) c.row.toString() else "",
                                        if (c.col >= 0) c.col.toString() else "",
                                        c.rowSpan.toString(),
                                        c.colSpan.toString()
                                    ).joinToString("|")
                                )
                            }
                        }
                        val copied = runCatching {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("room_cells", text))
                            true
                        }.getOrDefault(false)
                        Toast.makeText(
                            context,
                            if (copied) "已复制 ${campusRooms.size} 间教室（内置格式），粘贴发给开发者即可内置"
                            else "复制失败，请重试",
                            Toast.LENGTH_LONG
                        ).show()
                    }) {
                        Icon(Icons.Filled.FileDownload, "导出教室数据", tint = colors.accentMain)
                    }
                    TextButton(onClick = { showPaste = true }, enabled = building.isNotBlank()) {
                        Text("批量录入", fontSize = 13.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜房间号，如 S552") },
                singleLine = true,
                shape = RoundedCornerShape(skin.radiusChip)
            )

            if (allCells.isEmpty()) {
                EmptyRoomsCard()
            }

            if (search.isNotBlank()) {
                Text("搜索结果 ${hits.size} 条", fontSize = 13.sp, color = colors.textSecondary)
                if (hits.isEmpty()) {
                    Text("没找到，换个房间号试试", fontSize = 13.sp, color = colors.textTertiary)
                }
                hits.forEach { cell ->
                    RoomHitRow(cell) {
                        building = cell.building
                        floor = cell.floor
                        search = ""
                    }
                }
                return@Column
            }

            SectionLabel("楼栋")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                buildingOptions.forEach { name ->
                    FilterChip(
                        selected = name == building,
                        onClick = { building = name },
                        label = { Text(name, fontSize = 13.sp) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { newBuildingDialog = true },
                    label = { Text("+ 新建", fontSize = 13.sp) }
                )
            }

            SectionLabel("楼层")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                floorOptions.forEach { name ->
                    FilterChip(
                        selected = name == floor,
                        onClick = { floor = name },
                        label = { Text(name + " 层", fontSize = 13.sp) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { newFloorDialog = true },
                    label = { Text("+ 新建", fontSize = 13.sp) }
                )
            }

            SectionLabel("网格大小")
            GridSizeQuickSettings(
                columns = gridColumns,
                rows = effectiveRows,
                onColumnsChange = { gridColumns = it },
                onRowsChange = { gridRows = it.coerceAtLeast(contentRows) },
            )

            if (target != null) {
                Text(
                    "已定位：" + target.building + " " + target.floor + " 层 " + target.room,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentMain
                )
            }

            if (cells.isEmpty()) {
                Text(
                    "这一层还没有房间：点下方空格子可直接添加教室",
                    fontSize = 13.sp,
                    color = colors.textTertiary
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusCard),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
            ) {
                Column(modifier = Modifier.padding(skin.spaceMd)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        CompassIndicator()
                    }
                    Spacer(Modifier.height(skin.spaceSm))
                    RoomLayoutGrid(
                        layout = grid,
                        highlightedRoomId = target?.id,
                        cellHeight = gridHeightDp.dp,
                        gap = 1.dp,
                        cornerRadius = cornerRadius,
                        borderStyle = borderStyle,
                        textSize = gridTextSize,
                        opacity = gridOpacity,
                        onRoomClick = { detail = it },
                        onRoomMove = { cell, row, col -> container.moveRoomCell(cell.id, row, col) },
                        onEmptySlotClick = { row, col ->
                            addSeed = RoomCell(
                                id = "",
                                campusId = campus.id,
                                building = building,
                                floor = floor,
                                room = "",
                                row = row,
                                col = col
                            )
                        }
                    )
                }
            }
            if (cells.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                RoomTypeLegend(types = typesOnFloor)
                Spacer(Modifier.height(skin.spaceSmall))
                Text(
                    (if (cells.any { it.row >= 0 }) {
                        "已按坐标摆放；未设坐标的房间自动补位（共 " + cells.size + " 个房间）"
                    } else {
                        "尚未设置坐标，按录入顺序自动排布（共 " + cells.size + " 个房间）"
                    }) + " · 长按房间可拖动，点空格子可添加",
                    fontSize = 11.sp,
                    color = colors.textTertiary
                )
            }
        }
    }

    detail?.let { cell ->
        // 这间教室今天、本周的课（用与首页同一套周次规则，避免两边漂移）
        val todayCourses = remember(courses, timeSlots, cell, week) {
            val slotMap = timeSlots.associateBy { it.slot }
            courses.asSequence()
                .filter { it.dayOfWeek == todayDay && isCourseInWeek(it, week) }
                .filter {
                    it.room.isNotBlank() &&
                        RoomDirectorySearch.resolve(listOf(cell), campus.id, it.room) != null
                }
                .sortedBy { it.startSlot }
                .map { c ->
                    RoomCourseRow(
                        name = c.name,
                        teacher = c.teacher,
                        time = slotMap[c.startSlot]?.startTime.orEmpty() +
                            "-" + slotMap[c.endSlot]?.endTime.orEmpty()
                    )
                }
                .toList()
        }
        RoomDetailDialog(
            cell = cell,
            todayCourses = todayCourses,
            canSplit = cell.rowSpan > 1 || cell.colSpan > 1,
            canMergeRight = RoomDirectorySearch.mergeNeighbor(
                cells = allCells, id = cell.id, direction = RoomDirectorySearch.RoomMergeDirection.RIGHT,
            ) != null,
            canMergeDown = RoomDirectorySearch.mergeNeighbor(
                cells = allCells, id = cell.id, direction = RoomDirectorySearch.RoomMergeDirection.DOWN,
            ) != null,
            onSplit = { container.splitRoomCell(cell.id); detail = null },
            onMergeRight = { container.mergeRoomCell(cell.id, RoomDirectorySearch.RoomMergeDirection.RIGHT); detail = null },
            onMergeDown = { container.mergeRoomCell(cell.id, RoomDirectorySearch.RoomMergeDirection.DOWN); detail = null },
            onEdit = { editing = cell; detail = null },
            onDelete = { container.removeRoomCell(cell.id); detail = null },
            onDismiss = { detail = null }
        )
    }

    editing?.let { cell ->
        RoomEditDialog(
            initial = cell,
            onConfirm = { container.updateRoomCell(it); editing = null },
            onDismiss = { editing = null }
        )
    }

    addSeed?.let { seed ->
        RoomEditDialog(
            initial = seed,
            onConfirm = {
                container.addRoomCells(listOf(it))
                addSeed = null
            },
            onDismiss = { addSeed = null }
        )
    }

    if (newBuildingDialog) {
        TextInputDialog(
            title = "新建楼栋",
            label = "楼栋名称",
            placeholder = "如 3 号楼",
            onConfirm = { name ->
                if (name !in customBuildings) customBuildings += name
                building = name
                newBuildingDialog = false
            },
            onDismiss = { newBuildingDialog = false }
        )
    }

    if (newFloorDialog) {
        TextInputDialog(
            title = "新建楼层",
            label = "楼层",
            placeholder = "如 2 层",
            onConfirm = { name ->
                if (name !in customFloors) customFloors += name
                floor = name
                newFloorDialog = false
            },
            onDismiss = { newFloorDialog = false }
        )
    }

    if (showPaste) {
        RoomPasteDialog(
            building = building,
            floor = floor,
            onConfirm = { names ->
                val added = container.addRoomCells(
                    names.map { part ->
                        RoomCell(
                            id = UUID.randomUUID().toString(),
                            campusId = campus.id,
                            building = building,
                            floor = floor,
                            room = part.first,
                            name = part.second
                        )
                    }
                )
                showPaste = false
                android.widget.Toast.makeText(
                    context,
                    "本层新增 " + added + " 间教室",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            },
            onClearFloor = {
                container.clearRoomFloor(campus.id, building, floor)
                showPaste = false
                android.widget.Toast.makeText(context, "已清空本层", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPaste = false }
        )
    }
}

private const val GRID_COLUMNS = 4
private const val EMPTY_GRID_ROWS = 4
private const val DEFAULT_BUILDING = "1 号楼"
private const val DEFAULT_FLOOR = "1 层"

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalPocketXiaoliColors.current
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
}

@Composable
private fun EmptyRoomsCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
    ) {
        Column(modifier = Modifier.padding(skin.spaceWide)) {
            Text("还没有教室数据", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceSmall))
            Text(
                "已经可以直接开工了：点网格里的「+」逐间创建，或先「+ 新建」楼栋 / 楼层再用「批量录入」贴房间号。\n"
                + "建好后点右上角「导出」，把复制的数据发给开发者即可内置进下一个版本。",
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}


@Composable
private fun RoomHitRow(cell: RoomCell, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.surfaceCard)
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceCompact, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(cell.room, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.width(10.dp))
        Text(
            cell.building + " " + cell.floor + " 层" + if (cell.name.isBlank()) "" else " · " + cell.name,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RoomDetailDialog(
    cell: RoomCell,
    todayCourses: List<RoomCourseRow> = emptyList(),
    canSplit: Boolean = false,
    canMergeRight: Boolean = false,
    canMergeDown: Boolean = false,
    onSplit: () -> Unit = {},
    onMergeRight: () -> Unit = {},
    onMergeDown: () -> Unit = {},
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cell.room, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(skin.spaceXs)
            ) {
                Text(cell.building + " · " + cell.floor + " 层", fontSize = 13.sp)
                Text("类型：" + cell.roomType.label, fontSize = 13.sp)
                if (cell.capacity > 0) Text("容量：" + cell.capacity + " 人", fontSize = 13.sp)
                if (cell.rowSpan > 1 || cell.colSpan > 1) {
                    Text("占用：" + cell.rowSpan + " × " + cell.colSpan + " 格", fontSize = 13.sp)
                }
                if (cell.name.isNotBlank()) Text("用途：" + cell.name, fontSize = 13.sp)
                if (cell.note.isNotBlank()) Text("备注：" + cell.note, fontSize = 13.sp)

                Spacer(Modifier.height(skin.spaceSmall))
                Text("今日课程", fontSize = 12.sp, color = colors.textTertiary)
                if (todayCourses.isEmpty()) {
                    Text("今天这间教室没有课", fontSize = 12.sp, color = colors.textTertiary)
                } else {
                    todayCourses.forEach { course ->
                        Text(
                            course.time + "  " + course.name +
                                (if (course.teacher.isNotBlank()) " · " + course.teacher else ""),
                            fontSize = 12.sp
                        )
                    }
                }
                if (canSplit || canMergeRight || canMergeDown) {
                    Spacer(Modifier.height(skin.spaceSm))
                    Text("布局操作", fontSize = 12.sp, color = colors.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        if (canSplit) TextButton(onClick = onSplit) { Text("拆分为单格") }
                        if (canMergeRight) TextButton(onClick = onMergeRight) { Text("向右合并") }
                        if (canMergeDown) TextButton(onClick = onMergeDown) { Text("向下合并") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("编辑") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun RoomPasteDialog(
    building: String,
    floor: String,
    onConfirm: (List<Pair<String, String>>) -> Unit,
    onClearFloor: () -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scrollState = rememberScrollState()
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量录入 · " + building + " " + floor + " 层", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                Text(
                    "一行一个房间号，也可用逗号 / 顿号分隔；\n房间号后面接用途（可选），例如：\nS552 多媒体教室\nS553,S554,教师办公室",
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    label = { Text("房间号列表") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(parseRoomLines(text)) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(50)
            ) { Text("录入") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClearFloor) { Text("清空本层", fontSize = 12.sp) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 解析批量录入文本 -> (房间号, 用途) */
internal fun parseRoomLines(text: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    val seen = mutableSetOf<String>()
    text.lines().forEach { rawLine ->
        rawLine.split(',', '，', '、', '\t', ';', '；').forEach { piece ->
            val line = piece.trim()
            if (line.isEmpty()) return@forEach
            val parts = line.split(' ', '　').map { it.trim() }.filter { it.isNotEmpty() }
            val room = parts.firstOrNull().orEmpty()
            if (room.isEmpty()) return@forEach
            if (seen.add(room)) {
                out += room to parts.drop(1).joinToString(" ")
            }
        }
    }
    return out
}
/** 房间卡上的一行「今日课程」 */
private data class RoomCourseRow(
    val name: String,
    val teacher: String,
    val time: String
)