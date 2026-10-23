package com.pocket.xiaoli.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.FeatureFlag
import com.pocket.xiaoli.data.map.Campus3DMapDataSource
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DScene
import com.pocket.xiaoli.ui.map3d.Campus3DScreen
import com.pocket.xiaoli.util.currentWeekFrom
import com.pocket.xiaoli.util.parseSemesterStart
import java.time.LocalDate

/**
 * 「校园地图」入口：直接进入全屏 3D 沙盘。
 *
 * [initialQuery] 来自课表「找教室」或深链，可能是房间号、房间名或楼名；
 * 能对上就聚焦对应楼栋，对不上则仍打开校区全景。不再停留在旧的 2D 列表页。
 */
@Composable
fun CampusMapScreen(
    initialQuery: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PocketXiaoliApplication).appContainer
    val campus by container.selectedCampus.collectAsStateWithLifecycle()
    val roomCells by container.roomCells.collectAsStateWithLifecycle()
    val featureFlags by container.featureFlags.collectAsStateWithLifecycle()
    val scene = remember(campus.id, context) {
        Campus3DMapDataSource.fromAssets(context).load(campus.id).scene
    }
    val focusBuilding = remember(scene, roomCells, initialQuery, campus.id) {
        resolveFocusBuilding(initialQuery, campus.id, roomCells, scene)
    }

    val activeSchemeId by container.activeSchemeId.collectAsStateWithLifecycle()
    val coursesFlow = remember(activeSchemeId) {
        container.repository.getCoursesByScheme(activeSchemeId)
    }
    val courses by coursesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val holidays by container.holidays.collectAsStateWithLifecycle()
    val specialWorkDays by container.specialWorkDays.collectAsStateWithLifecycle()
    val semesterStart by container.semesterStart.collectAsStateWithLifecycle()
    val semesterWeeks by container.semesterWeeks.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val week = remember(semesterStart, semesterWeeks, today) {
        parseSemesterStart(semesterStart)
            ?.let { currentWeekFrom(it, semesterWeeks.coerceAtLeast(1), today) }
            ?: 1
    }
    val todayCourseCounts = remember(
        courses, week, today, holidays, specialWorkDays, campus.id, roomCells, scene,
    ) {
        resolveTodayCourseBuildings(
            courses = courses,
            week = week,
            today = today,
            holidays = holidays,
            specialWorkDayMap = specialWorkDays.associate { it.originDate to it.targetDate },
            campusId = campus.id,
            roomCells = roomCells,
            scene = scene,
        )
    }

    var showRoomLayout by rememberSaveable { mutableStateOf(false) }
    var managingBuilding by rememberSaveable { mutableStateOf("") }
    var managingFloor by rememberSaveable { mutableStateOf("") }

    if (showRoomLayout) {
        RoomLayoutScreen(
            container = container,
            initialBuilding = managingBuilding,
            initialFloor = managingFloor,
            onBack = {
                showRoomLayout = false
                managingBuilding = ""
                managingFloor = ""
            },
        )
        return
    }

    Campus3DScreen(
        scene = scene,
        onClose = onBack,
        campusId = campus.id,
        roomCells = roomCells,
        showFloorPlans = FeatureFlag.BUILDING_FLOOR_MAP.key in featureFlags,
        initialBuildingName = focusBuilding,
        onManageRooms = { building, floor ->
            managingBuilding = building
            managingFloor = floor
            showRoomLayout = true
        },
        todayCourseCounts = todayCourseCounts,
    )
}

/** 把「A406」「1号教学楼 406」「综合楼」这类入口文本解析成 3D 场景里的楼名。 */
internal fun resolveFocusBuilding(
    query: String,
    campusId: String,
    roomCells: List<RoomCell>,
    scene: Campus3DScene,
): String? {
    val normalized = normalizeFocusQuery(query)
    if (normalized.isEmpty()) return null

    val cell = roomCells.firstOrNull { cell ->
        if (cell.campusId != campusId) return@firstOrNull false
        val labels = listOf(cell.room, cell.name)
            .map(::normalizeFocusQuery)
            .filter { it.isNotEmpty() }
        labels.any { it == normalized } ||
            labels.any { it.length >= 2 && normalized.contains(it) }
    }
    if (cell != null) {
        val buildingName = scene.buildings
            .firstOrNull { normalizeFocusQuery(it.name) == normalizeFocusQuery(cell.building) }
            ?.name
        return buildingName ?: cell.building
    }

    return scene.buildings.firstOrNull { building ->
        listOf(building.name, building.sourceName)
            .map(::normalizeFocusQuery)
            .filter { it.isNotEmpty() }
            .any { normalized.contains(it) }
    }?.name
}

private fun normalizeFocusQuery(value: String): String =
    value.trim().lowercase().replace(" ", "")
