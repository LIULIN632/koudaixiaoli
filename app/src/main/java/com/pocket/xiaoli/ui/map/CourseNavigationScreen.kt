package com.pocket.xiaoli.ui.map

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.CampusMapCorrection
import com.pocket.xiaoli.data.map.CampusUserLocation
import com.pocket.xiaoli.data.map.CourseNavigationPlanner
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusRoom
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.campusui.CampusCard
import com.pocket.xiaoli.ui.components.campusui.CampusMapCanvas
import com.pocket.xiaoli.ui.components.campusui.CampusSectionHeader
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlinx.coroutines.launch
import java.util.UUID
import com.pocket.xiaoli.ui.theme.LocalSkin
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 课程导航页：课程教室 -> 楼栋 / 楼层 / 房间 -> 步行步骤。
 *
 * 没有地图数据时给出明确出口，不显示伪路线；找不到教室时可以直接提交
 * 「课程文本 -> 规范教室」的别名纠错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseNavigationScreen(
    container: AppContainer,
    initialRoomQuery: String,
    onBack: () -> Unit,
    onOpenRoomLayout: ((String) -> Unit)? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val campus by container.selectedCampus.collectAsStateWithLifecycle()
    val roomCells by container.roomCells.collectAsStateWithLifecycle()
    var correctionVersion by remember { mutableStateOf(0) }

    val loadResult by produceState(
        initialValue = container.campusMapRepository.load(campus.id),
        campus.id,
        roomCells,
        correctionVersion,
    ) {
        value = container.campusMapRepository.loadWithCorrections(campus.id, roomCells)
    }
    val mapData = loadResult.data


    var location by remember(campus.id) { mutableStateOf(container.campusLocationStore.load(campus.id)) }
    var showLocationPicker by remember { mutableStateOf(false) }
    var showCorrectionDialog by remember { mutableStateOf(false) }
    val plan = remember(mapData, initialRoomQuery, campus.id, location?.nodeId) {
        CourseNavigationPlanner.plan(
            courseRoom = initialRoomQuery,
            campusId = campus.id,
            data = mapData,
            startNodeId = location?.nodeId,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("去上课", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceCompact),
        ) {
            item {
                CampusSectionHeader(
                    title = initialRoomQuery.ifBlank { "教室导航" },
                    subtitle = "当前校区：${campus.name}",
                )
            }

            item {
                CampusCard {
                    Text(
                        text = "当前位置",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = locationLabel(mapData, location) ?: "未设置（默认从建筑入口出发）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    TextButton(onClick = { showLocationPicker = true }) {
                        Text("设置当前位置", color = colors.accentMain, fontSize = 13.sp)
                    }
                }
            }

            if (plan == null) {
                item {
                    CampusCard {
                        Text(
                            text = "未找到这个教室",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "地图或教室分布图里还没有「${initialRoomQuery.ifBlank { "这间教室" }}」。可以关联已有教室，或先去补录房间。",
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(skin.spaceSmall))
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                            if (mapData.rooms.isNotEmpty()) {
                                TextButton(onClick = { showCorrectionDialog = true }) {
                                    Text("关联已有教室", color = colors.accentMain)
                                }
                            }
                            if (onOpenRoomLayout != null) {
                                TextButton(onClick = { onOpenRoomLayout(initialRoomQuery) }) {
                                    Text("去教室分布图", color = colors.accentMain)
                                }
                            }
                        }
                        Text(
                            text = "纠错只保存在本机，用于下次导航识别，不会上传位置。",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                }
            } else {
                item {
                    CampusCard {
                        Text(
                            text = plan.target.roomName ?: initialRoomQuery,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        val buildingName = plan.target.buildingId
                            ?.let { id -> mapData.buildings.firstOrNull { it.id == id }?.name }
                        Text(
                            text = listOfNotNull(
                                buildingName,
                                plan.target.floorId
                                    ?.let { id -> mapData.floors.firstOrNull { it.id == id }?.name }
                            ).joinToString(" · ").ifBlank { "位置已登记" },
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }
                }

                item {
                    CampusCard(contentPadding = PaddingValues(8.dp)) {
                        CampusMapCanvas(
                            data = mapData,
                            showRoadLayer = true,
                            showPlaceLayer = true,
                            showRouteLayer = true,
                        )
                    }
                }

                if (plan.steps.isNotEmpty()) {
                    item {
                        CampusSectionHeader(
                            title = "步行路线",
                            subtitle = plan.path?.let { "${it.totalMeters.toInt()} 米" } ?: "",
                        )
                    }
                    // key = 步骤序号：路线重算后是"整段替换"，有序号才能让 Compose 复用而未变的步骤
                    items(plan.steps.size, key = { index -> index }) { index ->
                        val step = plan.steps[index]
                        CampusCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = (index + 1).toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentMain,
                                )
                                Spacer(Modifier.padding(horizontal = skin.spaceSmall))
                                Text(
                                    text = step.instruction,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                } else {
                    item {
                        CampusCard {
                            Text(
                                text = "暂无可用步行路线",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "地图数据里还没有从入口到教室的连通路网；可先查看教室分布图，或提交教室纠错。",
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                                if (mapData.rooms.isNotEmpty()) {
                                    TextButton(onClick = { showCorrectionDialog = true }) {
                                        Text("提交纠错", color = colors.accentMain)
                                    }
                                }
                                if (onOpenRoomLayout != null) {
                                    TextButton(onClick = { onOpenRoomLayout(initialRoomQuery) }) {
                                        Text("查看教室分布图", color = colors.accentMain)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLocationPicker) {
        CampusLocationPickerDialog(
            campusId = campus.id,
            data = mapData,
            initial = location,
            onConfirm = { selected ->
                location = selected
                container.campusLocationStore.save(selected)
                showLocationPicker = false
            },
            onDismiss = { showLocationPicker = false },
        )
    }

    if (showCorrectionDialog) {
        CourseRoomCorrectionDialog(
            courseRoom = initialRoomQuery,
            data = mapData,
            onDismiss = { showCorrectionDialog = false },
            onSave = { room ->
                scope.launch {
                    container.campusMapRepository.saveCorrection(
                        CampusMapCorrection(
                            id = "room_alias_" + UUID.randomUUID().toString(),
                            campusId = campus.id,
                            kind = "room_alias",
                            targetId = room.id,
                            name = initialRoomQuery.trim(),
                        )
                    )
                    correctionVersion++
                    showCorrectionDialog = false
                    Toast.makeText(
                        context,
                        "已关联：${initialRoomQuery.trim()} → ${room.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }
}

@Composable
private fun CourseRoomCorrectionDialog(
    courseRoom: String,
    data: CampusMapData,
    onDismiss: () -> Unit,
    onSave: (CampusRoom) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val rooms = remember(data) {
        data.rooms.sortedWith(compareBy({ it.buildingId }, { it.floorId }, { it.name }))
    }
    var selectedId by remember(rooms) { mutableStateOf(rooms.firstOrNull()?.id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联已有教室", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                Text(
                    text = "课程文本：${courseRoom.ifBlank { "（空）" }}",
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "选择地图 / 教室分布图里的规范教室；提交后下次导航会按别名识别。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
                rooms.forEach { room ->
                    val building = data.buildings.firstOrNull { it.id == room.buildingId }?.name
                    val floor = data.floors.firstOrNull { it.id == room.floorId }?.name
                    val label = listOfNotNull(building, floor, room.name)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusChip))
                            .background(
                                if (room.id == selectedId) colors.surfaceHighlight else colors.surfaceCard
                            )
                            .clickable { selectedId = room.id }
                            .padding(horizontal = skin.spaceMd, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (room.id == selectedId) FontWeight.Bold else FontWeight.Normal,
                            color = if (room.id == selectedId) colors.accentMain else colors.textPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedId != null,
                onClick = {
                    rooms.firstOrNull { it.id == selectedId }?.let(onSave)
                },
            ) { Text("提交纠错", color = colors.accentMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun locationLabel(data: CampusMapData, location: CampusUserLocation?): String? {
    if (location == null) return null
    val building = location.buildingId?.let { id -> data.buildings.firstOrNull { it.id == id }?.name }
    val floor = location.floorId?.let { id -> data.floors.firstOrNull { it.id == id }?.name }
    val room = location.nodeId?.let { nodeId -> data.rooms.firstOrNull { it.nodeId == nodeId }?.name }
    return listOfNotNull(building, floor, room).joinToString(" · ").ifBlank { null }
}
