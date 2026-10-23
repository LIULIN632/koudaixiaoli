package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.indoor.IndoorFloorPlan
import com.pocket.xiaoli.data.map.indoor.IndoorPlanDataSource
import com.pocket.xiaoli.data.map.indoor.IndoorRoom
import com.pocket.xiaoli.data.map.indoor.IndoorRoute
import com.pocket.xiaoli.data.map.indoor.IndoorSide
import com.pocket.xiaoli.data.map.indoor.IndoorStartOption
import com.pocket.xiaoli.data.map.indoor.routeTo
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors

/**
 * 室内平面图的内部预览页。
 *
 * 它只回答一个问题：**「点房间 → 高亮 → 出路线」这条交互链在 Compose 里跑不跑得通**。
 * 底图从 `assets/indoor/` 的拓扑草案 JSON 读取，所以页面处处标明未实测、未按比例，
 * 路线不报米数、不报用时，起点只能是 4F 的四个楼梯口。
 *
 * 入口只放在开发者实验室；普通用户的楼层页仍只显示内置教室分布图。
 */
@Composable
fun IndoorFloorPlanPreviewScreen(
    onBack: () -> Unit,
    plan: IndoorFloorPlan? = null,
) {
    if (plan != null) {
        IndoorFloorPlanPreviewContent(onBack = onBack, plan = plan)
        return
    }
    val context = LocalContext.current
    val result = remember(context) { IndoorPlanDataSource.fromAssets(context).load() }
    val loaded = result.plan
    if (loaded == null) {
        IndoorPlanLoadError(onBack = onBack, issues = result.issues)
        return
    }
    IndoorFloorPlanPreviewContent(onBack = onBack, plan = loaded)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndoorFloorPlanPreviewContent(
    onBack: () -> Unit,
    plan: IndoorFloorPlan,
) {
    val skin = LocalSkin.current
    var listMode by rememberSaveable { mutableStateOf(false) }
    var selectedRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedStartNodeId by rememberSaveable(plan.id) { mutableStateOf(plan.startNodeId) }
    val selectedRoom = plan.room(selectedRoomId.orEmpty())
    val route = remember(plan, selectedRoomId, selectedStartNodeId) {
        selectedRoomId?.let { plan.routeTo(it, selectedStartNodeId) }
    }
    val zoom = rememberPlanZoomState()

    Scaffold(
        topBar = { PreviewTopBar(onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            PreviewHeader(plan)
            ModeSwitch(
                listMode = listMode,
                onModeChange = { listMode = it },
                modifier = Modifier.padding(horizontal = skin.spaceWide, vertical = skin.spaceSm),
            )
            StartPicker(
                plan = plan,
                selectedStartNodeId = selectedStartNodeId,
                onStartChange = { selectedStartNodeId = it },
                modifier = Modifier.padding(horizontal = skin.spaceWide),
            )
            Spacer(Modifier.height(4.dp))
            if (listMode) {
                RoomListPane(
                    plan = plan,
                    selectedRoomId = selectedRoomId,
                    route = route,
                    onRoomClick = { selectedRoomId = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                MapPane(
                    plan = plan,
                    selectedRoomId = selectedRoomId,
                    selectedRoom = selectedRoom,
                    route = route,
                    zoom = zoom,
                    onRoomClick = { selectedRoomId = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewTopBar(onBack: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    TopAppBar(
        title = {
            Column {
                Text("室内平面图预览", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "内部验证 · 仅开发者可见",
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回开发者实验室",
                    tint = colors.textPrimary,
                )
            }
        },
    )
}

@Composable
private fun IndoorPlanLoadError(
    onBack: () -> Unit,
    issues: List<String>,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Scaffold(topBar = { PreviewTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(skin.spaceWide),
        ) {
            Text(
                text = "拓扑草案加载失败",
                color = colors.warning,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "资产校验没有通过，预览页不会画半张图。",
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            issues.distinct().forEach { issue ->
                Text(
                    text = "· $issue",
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun PreviewHeader(plan: IndoorFloorPlan) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(
        modifier = Modifier.padding(horizontal = skin.spaceWide),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniChip("内部预览", colors.accentMain)
            Spacer(Modifier.width(6.dp))
            MiniChip(plan.status.label, colors.warning)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${plan.nodes.size} 节点 · ${plan.edges.size} 边",
                color = colors.textTertiary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${plan.buildingName} · ${plan.floorLabel}",
            modifier = Modifier.semantics { heading() },
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "按 ${plan.floorLabel} 指引牌整理的连接关系，未实测、未按比例；只用于验证空间导览交互。",
            color = colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ModeSwitch(
    listMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModeTab(
            text = "平面图",
            selected = !listMode,
            testTag = "indoor-mode-map",
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f),
        )
        ModeTab(
            text = "列表视图",
            selected = listMode,
            testTag = "indoor-mode-list",
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeTab(
    text: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { stateDescription = if (selected) "已选中" else "未选中" },
        shape = CircleShape,
        color = if (selected) colors.accentMain else colors.surfaceAlt,
        border = if (selected) null else BorderStroke(1.dp, colors.borderDivider),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 9.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun StartPicker(
    plan: IndoorFloorPlan,
    selectedStartNodeId: String,
    onStartChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "起点",
            color = colors.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        plan.startOptions.forEachIndexed { index, option ->
            if (index > 0) Spacer(Modifier.width(6.dp))
            StartChip(
                option = option,
                selected = option.nodeId == selectedStartNodeId,
                onClick = { onStartChange(option.nodeId) },
            )
        }
    }
}

@Composable
private fun StartChip(
    option: IndoorStartOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("indoor-start-${option.nodeId}")
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { stateDescription = if (selected) "已选起点" else "未选起点" },
        shape = CircleShape,
        color = if (selected) colors.accentMain.copy(alpha = 0.12f) else colors.surfaceAlt,
        border = BorderStroke(1.dp, if (selected) colors.accentMain else colors.borderDivider),
    ) {
        Text(
            text = option.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            color = if (selected) colors.accentMain else colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MapPane(
    plan: IndoorFloorPlan,
    selectedRoomId: String?,
    selectedRoom: IndoorRoom?,
    route: IndoorRoute?,
    zoom: PlanZoomState,
    onRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.spaceWide),
    ) {
        Spacer(Modifier.height(skin.spaceXs))
        MapCard(
            plan = plan,
            selectedRoomId = selectedRoomId,
            route = route,
            zoom = zoom,
            onRoomClick = onRoomClick,
        )
        Spacer(Modifier.height(skin.spaceMd))
        if (selectedRoom == null) {
            EmptySelectionCard()
        } else {
            SelectedRoomCard(room = selectedRoom, route = route)
        }
        Spacer(Modifier.height(skin.spaceMd))
        MapLegend(plan)
        Spacer(Modifier.height(skin.spaceMd))
        ProvenanceCard(plan)
        Spacer(Modifier.height(skin.spaceLg))
    }
}

@Composable
private fun MapCard(
    plan: IndoorFloorPlan,
    selectedRoomId: String?,
    route: IndoorRoute?,
    zoom: PlanZoomState,
    onRoomClick: (String) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderDivider),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${plan.floorLabel} 平面（拓扑示意）",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "选楼梯口 → 点房间 → 出示意路线",
                        color = colors.textTertiary,
                        fontSize = 10.sp,
                    )
                }
                MiniChip("未按比例", colors.warning)
            }
            Spacer(Modifier.height(6.dp))
            PlanZoomBar(state = zoom)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ZoomablePlan(state = zoom, baseHeight = 252.dp) { scale ->
                    CampusFloorPlanCanvas(
                        plan = plan,
                        selectedRoomId = selectedRoomId,
                        route = route,
                        scale = scale,
                        onRoomSelected = onRoomClick,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "双指缩放，放大后可单指拖动；读屏用户可切「列表视图」。",
                color = colors.textTertiary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun RoomListPane(
    plan: IndoorFloorPlan,
    selectedRoomId: String?,
    route: IndoorRoute?,
    onRoomClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val northRooms = plan.rooms.filter { it.side == IndoorSide.North }
    val southRooms = plan.rooms.filter { it.side == IndoorSide.South }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = skin.spaceWide,
            end = skin.spaceWide,
            top = skin.spaceXs,
            bottom = skin.spaceLg,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "列表视图 · 读屏可用",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "点按一项展开示意路线；路线不含距离与用时。",
                color = colors.textTertiary,
                fontSize = 10.sp,
            )
        }
        roomSection(
            title = "北区 · ${northRooms.size} 间",
            rooms = northRooms,
            selectedRoomId = selectedRoomId,
            route = route,
            onRoomClick = onRoomClick,
        )
        val provisionalCount = southRooms.count { it.provisional }
        roomSection(
            title = buildString {
                append("南区 · ${southRooms.size} 间")
                if (provisionalCount > 0) append("（含 $provisionalCount 间待核）")
            },
            rooms = southRooms,
            selectedRoomId = selectedRoomId,
            route = route,
            onRoomClick = onRoomClick,
        )
        item {
            Text(
                text = "起点可切换为四个 4F 楼梯口；1F 大门与跨层路网未实测，" +
                    "因此没有「东大门 → 教室」的完整路线。",
                color = colors.textTertiary,
                fontSize = 9.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun MiniChip(text: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

internal fun roomSideColor(side: IndoorSide, colors: PocketXiaoliColors): Color =
    when (side) {
        IndoorSide.North -> colors.info
        IndoorSide.South -> colors.warning
    }
