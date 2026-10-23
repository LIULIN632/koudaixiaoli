package com.pocket.xiaoli.ui.map

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DBuilding
import com.pocket.xiaoli.data.map.model.Campus3DScene
import com.pocket.xiaoli.util.HolidaySchedule
import com.pocket.xiaoli.util.isCourseInWeek
import java.time.LocalDate

/**
 * 今日有课的楼栋：`楼栋 id -> 今天的课节数`。
 *
 * 课程地点先按「已录入房间 / 场景楼名」解析成楼栋；解析不出的地点直接跳过，
 * 不按序号、拼音或近似名字猜楼栋。法定放假日返回空，调休补课日按补的那一天取课。
 */
internal fun resolveTodayCourseBuildings(
    courses: List<CourseEntity>,
    week: Int,
    today: LocalDate,
    holidays: List<HolidayDay>,
    specialWorkDayMap: Map<String, String>,
    campusId: String,
    roomCells: List<RoomCell>,
    scene: Campus3DScene,
): Map<String, Int> {
    if (courses.isEmpty() || scene.buildings.isEmpty()) return emptyMap()

    val weekMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val effectiveDay = HolidaySchedule
        .weekSchedule(holidays, specialWorkDayMap, weekMonday)
        .firstOrNull { it.date == today.toString() }
        ?.effectiveDayOfWeek
        ?: return emptyMap()
    if (effectiveDay !in 1..7) return emptyMap()

    val counts = HashMap<String, Int>()
    courses.forEach { course ->
        if (course.isAgenda) return@forEach
        if (!isCourseInWeek(course, week) || course.dayOfWeek != effectiveDay) return@forEach
        val building = resolveCourseBuilding(course.room, campusId, roomCells, scene)
            ?: return@forEach
        counts[building.id] = (counts[building.id] ?: 0) + 1
    }
    return counts
}

/**
 * 课程地点 → 3D 楼栋。
 *
 * 1. 地点文本自带楼名（「综合楼 B201」「1号教学楼306」）时以楼名为准；
 * 2. 否则按已录入房间匹配，只有命中结果**全部指向同一栋楼**才返回；
 * 3. 房间号同时出现在多栋楼时返回 null —— 宁可少标一栋，也不蒙一个。
 */
private fun resolveCourseBuilding(
    roomText: String,
    campusId: String,
    roomCells: List<RoomCell>,
    scene: Campus3DScene,
): Campus3DBuilding? {
    val query = normalizeRoomText(roomText)
    if (query.isEmpty()) return null

    scene.buildings
        .mapNotNull { building ->
            val matchedLength = listOf(building.name, building.sourceName)
                .map(::normalizeRoomText)
                .filter { it.length >= 2 && query.contains(it) }
                .maxOfOrNull { it.length }
            matchedLength?.let { building to it }
        }
        .maxByOrNull { it.second }
        ?.let { return it.first }

    val matchedBuildings = roomCells.asSequence()
        .filter { cell ->
            cell.campusId == campusId &&
                listOf(cell.room, cell.name)
                    .map(::normalizeRoomText)
                    .filter { it.isNotEmpty() }
                    .any { label -> label == query || (label.length >= 2 && query.contains(label)) }
        }
        .map { normalizeRoomText(it.building) }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
    if (matchedBuildings.size != 1) return null

    val buildingName = matchedBuildings.single()
    return scene.buildings.firstOrNull { normalizeRoomText(it.name) == buildingName }
}

private fun normalizeRoomText(value: String): String =
    value.trim().lowercase().replace(" ", "")
