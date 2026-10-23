package com.pocket.xiaoli.ui.map3d

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DScene
import com.pocket.xiaoli.ui.share.AppShare
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette

/**
 * 全屏 3D 校园沙盘。
 *
 * [initialBuildingName] 用于从课表「找教室」带房间号进入：相机聚焦该楼并直接选中，
 * 对应不上时保持校区全景。
 *
 * [todayCourseCounts] 是「今日有课」图层：楼栋 id → 今天的课节数。
 * 只在能确定楼栋时传入，空 Map 不改变沙盘外观。
 */
@Composable
fun Campus3DScreen(
    scene: Campus3DScene,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    campusId: String = scene.campusId,
    roomCells: List<RoomCell> = emptyList(),
    showFloorPlans: Boolean = true,
    initialBuildingName: String? = null,
    onManageRooms: ((String, String) -> Unit)? = null,
    todayCourseCounts: Map<String, Int> = emptyMap(),
) {
    val layout = remember(scene) { Campus3DLayout.prepare(scene) }
    if (layout == null || !layout.hasContent) {
        Campus3DUnavailable(onClose = onClose, modifier = modifier)
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Palette.SkyTop),
    ) {
        val aspect = if (maxWidth.value > 0f && maxHeight.value > 0f) {
            maxWidth.value / maxHeight.value
        } else {
            0.46f
        }
        val focusBuilding = remember(layout, initialBuildingName) {
            initialBuildingName?.let { name -> layout.buildings.firstOrNull { it.name == name } }
        }
        var camera by remember(layout, focusBuilding) {
            mutableStateOf(
                focusBuilding?.let { layout.focusCamera(it, aspect) }
                    ?: layout.initialCamera(aspect)
            )
        }
        var selected by remember(layout, focusBuilding) {
            mutableStateOf<Campus3DHit?>(
                focusBuilding?.let { Campus3DHit.Building(it) }
            )
        }
        var floorPlanBuilding by remember { mutableStateOf<Campus3DBuildingShape?>(null) }
        var floorPlanFloor by remember { mutableStateOf<String?>(null) }
        var profileBuilding by remember { mutableStateOf<Campus3DBuildingShape?>(null) }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var cameraJob by remember { mutableStateOf<Job?>(null) }
        val tourStops = remember(layout) { buildCampus3DTour(layout) }
        val todayBuildingIds = remember(todayCourseCounts) { todayCourseCounts.keys.toSet() }
        var tourActive by rememberSaveable { mutableStateOf(false) }
        var tourIndex by rememberSaveable { mutableIntStateOf(0) }
        var tourPlaying by rememberSaveable { mutableStateOf(false) }

        fun animateCameraTo(target: Campus3DCamera) {
            cameraJob?.cancel()
            val start = camera
            if (start == target) return
            cameraJob = scope.launch {
                Animatable(0f).animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 460,
                        easing = FastOutSlowInEasing,
                    ),
                ) {
                    camera = interpolateCamera(start, target, value)
                }
            }
        }

        fun stopTour() {
            tourActive = false
            tourPlaying = false
        }

        fun goToTourStop(index: Int) {
            val stop = tourStops.getOrNull(index) ?: return
            tourIndex = index
            selected = stop.hit
            animateCameraTo(layout.focusCamera(stop.hit, aspect))
        }

        fun startTour() {
            if (tourStops.size < 2) return
            showSearch = false
            floorPlanBuilding = null
            tourActive = true
            tourPlaying = false
            goToTourStop(0)
        }

        BackHandler {
            when {
                showSearch -> showSearch = false
                floorPlanBuilding != null -> floorPlanBuilding = null
                profileBuilding != null -> profileBuilding = null
                tourActive -> stopTour()
                selected != null -> selected = null
                else -> onClose()
            }
        }

        floorPlanBuilding?.let { building ->
            Campus3DFloorPlanScreen(
                building = building,
                campusId = campusId,
                roomCells = roomCells,
                onClose = { floorPlanBuilding = null },
                initialFloor = floorPlanFloor,
                onManageRooms = onManageRooms,
            )
            return@BoxWithConstraints
        }

        profileBuilding?.let { building ->
            val facts = remember(building, campusId, roomCells) {
                buildingProfileFacts(
                    building = building,
                    campusId = campusId,
                    roomCells = roomCells,
                )
            }
            Campus3DBuildingProfileScreen(
                building = building,
                facts = facts,
                todayClassCount = todayCourseCounts[building.id] ?: 0,
                showFloorPlans = showFloorPlans,
                onOpenFloor = { floor ->
                    profileBuilding = null
                    selected = Campus3DHit.Building(building)
                    floorPlanFloor = floor
                    floorPlanBuilding = building
                },
                onOpenAllFloors = {
                    profileBuilding = null
                    selected = Campus3DHit.Building(building)
                    floorPlanFloor = null
                    floorPlanBuilding = building
                },
                onClose = { profileBuilding = null },
            )
            return@BoxWithConstraints
        }

        LaunchedEffect(tourActive, tourPlaying, tourIndex) {
            if (!tourActive || !tourPlaying) return@LaunchedEffect
            delay(TOUR_AUTOPLAY_INTERVAL_MS)
            if (tourIndex >= tourStops.lastIndex) {
                tourPlaying = false
            } else {
                goToTourStop(tourIndex + 1)
            }
        }

        Campus3DSandbox(
            layout = layout,
            camera = camera,
            selected = selected,
            onCameraChange = {
                cameraJob?.cancel()
                if (tourActive) tourPlaying = false
                camera = it
            },
            onSelect = { hit ->
                stopTour()
                selected = hit
                // 点楼即把镜头推到楼前，详情卡负责文字与楼层入口，沙盘负责立体建筑本身。
                if (hit is Campus3DHit.Building) {
                    animateCameraTo(layout.focusCamera(hit, aspect))
                }
            },
            modifier = Modifier.fillMaxSize(),
            todayBuildingIds = todayBuildingIds,
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(148.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.Scrim, Palette.Transparent),
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconSurface(
                onClick = onClose,
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Palette.LabelText) },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "3D 校园",
                    color = Palette.LabelText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${scene.name.ifBlank { "烟台理工学院" }} · 拖动旋转 / 双指缩放",
                    color = Palette.LabelTextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RoundIconSurface(
                onClick = {
                    stopTour()
                    showSearch = true
                },
                label = "搜索楼栋、教室和地点",
            ) {
                Icon(Icons.Filled.Search, null, tint = Palette.LabelText)
            }
            Spacer(Modifier.width(8.dp))
            RoundIconSurface(
                onClick = {
                    stopTour()
                    animateCameraTo(layout.initialCamera(aspect))
                    selected = null
                },
                icon = { Icon(Icons.Filled.Refresh, "重置视角", tint = Palette.LabelText) },
            )
        }

        if (!tourActive && tourStops.size >= 2) {
            Surface(
                onClick = { startTour() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 68.dp)
                    .invisibleTouchTarget(),
                shape = CircleShape,
                color = Palette.CardBg,
                border = BorderStroke(1.dp, Palette.CardBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = null,
                        tint = Palette.Accent,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "校园导览",
                        color = Palette.LabelText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (!tourActive && todayCourseCounts.isNotEmpty()) {
            Campus3DTodayLegend(
                buildingCount = todayCourseCounts.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        start = 16.dp,
                        top = if (tourStops.size >= 2) 108.dp else 68.dp,
                    ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tourActive) {
                tourStops.getOrNull(tourIndex)?.let { stop ->
                    Campus3DTourCard(
                        stop = stop,
                        index = tourIndex,
                        total = tourStops.size,
                        playing = tourPlaying,
                        onPrevious = { goToTourStop(tourIndex - 1) },
                        onNext = {
                            goToTourStop(
                                if (tourIndex >= tourStops.lastIndex) 0 else tourIndex + 1
                            )
                        },
                        onTogglePlay = { tourPlaying = !tourPlaying },
                        onExit = { stopTour() },
                    )
                }
            } else {
                selected?.let { hit ->
                    val openFloors: (() -> Unit)? = (hit as? Campus3DHit.Building)
                        ?.takeIf { showFloorPlans }
                        ?.let { building ->
                            {
                                floorPlanFloor = null
                                floorPlanBuilding = building.shape
                            }
                        }
                    SelectionCard(
                        hit = hit,
                        onDismiss = { selected = null },
                        onOpenFloors = openFloors,
                        onOpenProfile = (hit as? Campus3DHit.Building)?.let { building ->
                            { profileBuilding = building.shape }
                        },
                        todayClassCount = (hit as? Campus3DHit.Building)
                            ?.let { todayCourseCounts[it.shape.id] }
                            ?: 0,
                    )
                }
                Surface(
                    color = Palette.CardBg.copy(alpha = 0.78f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Palette.CardBorder),
                ) {
                    Text(
                        text = "点楼聚焦立体建筑 · 双指缩小可看全校区",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Palette.LabelTextDim,
                        fontSize = 10.sp,
                    )
                }
            }
            AttributionText(scene = scene)
        }

        if (showSearch) {
            Campus3DSearchOverlay(
                layout = layout,
                roomCells = roomCells,
                campusId = campusId,
                onDismiss = { showSearch = false },
                onSelect = { result ->
                    showSearch = false
                    selected = result.hit
                    val building = (result.hit as? Campus3DHit.Building)?.shape
                    if (result.floor != null && building != null) {
                        // 教室直接落到对应楼层，关掉平面图时镜头已经对准这栋楼。
                        cameraJob?.cancel()
                        camera = layout.focusCamera(result.hit, aspect)
                        floorPlanFloor = result.floor
                        floorPlanBuilding = building
                    } else {
                        animateCameraTo(layout.focusCamera(result.hit, aspect))
                    }
                },
            )
        }
    }
}

@Composable
private fun RoundIconSurface(
    onClick: () -> Unit,
    label: String? = null,
    icon: @Composable () -> Unit,
) {
    val semantics = label?.let { value ->
        Modifier.semantics { contentDescription = value }
    } ?: Modifier
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp).then(semantics),
        shape = CircleShape,
        color = Palette.CardBg,
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun AttributionText(scene: Campus3DScene) {
    val context = LocalContext.current
    val text = scene.attribution.ifBlank { "地图数据 © OpenStreetMap 贡献者，ODbL 1.0" }
    val url = scene.sourceUrl.ifBlank { "https://www.openstreetmap.org/copyright" }
    Surface(
        onClick = { AppShare.openUrl(context, url) },
        color = Palette.Transparent,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            color = Palette.LabelTextDim.copy(alpha = 0.8f),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun Campus3DSearchOverlay(
    layout: Campus3DLayout,
    roomCells: List<RoomCell>,
    campusId: String,
    onSelect: (Campus3DSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(layout, roomCells, campusId, query) {
        searchCampus3D(
            layout = layout,
            roomCells = roomCells,
            campusId = campusId,
            query = query,
        )
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize(), color = Palette.SkyTop) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("campus3d-search-input")
                        .semantics { contentDescription = "搜索输入框" },
                    placeholder = { Text("搜索楼栋 / 教室 / 地点") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Palette.Accent,
                        unfocusedBorderColor = Palette.CardBorder,
                        focusedTextColor = Palette.LabelText,
                        unfocusedTextColor = Palette.LabelText,
                        focusedContainerColor = Palette.CardBg,
                        unfocusedContainerColor = Palette.CardBg,
                        focusedPlaceholderColor = Palette.LabelTextDim,
                        unfocusedPlaceholderColor = Palette.LabelTextDim,
                        focusedLeadingIconColor = Palette.LabelTextDim,
                        unfocusedLeadingIconColor = Palette.LabelTextDim,
                        cursorColor = Palette.Accent,
                    ),
                )
                TextButton(onClick = onDismiss) {
                    Text("取消", color = Palette.Accent, fontSize = 14.sp)
                }
            }
            Text(
                text = if (query.isBlank()) "全部楼栋与地点" else "找到 ${results.size} 个结果",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = Palette.LabelTextDim,
                fontSize = 11.sp,
            )
            if (results.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "没有找到「${query.trim()}」",
                        color = Palette.LabelTextDim,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { result ->
                        SearchResultRow(
                            result = result,
                            onClick = { onSelect(result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: Campus3DSearchResult,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Palette.CardBg,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = Palette.ChipBg, shape = CircleShape) {
                Text(
                    text = result.kind.label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Palette.ChipText,
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    color = Palette.LabelText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.subtitle,
                    color = Palette.LabelTextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = Palette.LabelTextDim, fontSize = 18.sp)
        }
    }
}

@Composable
private fun Campus3DUnavailable(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.SkyTop, Palette.SkyMid))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("3D 校园暂不可用", color = Palette.LabelText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("缺少 3D 场景数据", color = Palette.LabelTextDim, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onClose) { Text("返回") }
        }
    }
}

private const val TOUR_AUTOPLAY_INTERVAL_MS = 6_000L
