package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import kotlin.math.abs

/** 楼栋轮廓的来源。描点轮廓会在档案里明确标注「非 OSM」。 */
internal enum class BuildingOutlineSource {
    OpenStreetMap,
    Traced,
}

internal data class BuildingRoomTypeCount(
    val type: RoomCellType,
    val count: Int,
)

internal data class BuildingFloorRooms(
    /** 统一后的楼层标签，如 "4F" */
    val label: String,
    /** 房间数据里的原始楼层写法，用于平面图定位 */
    val rawFloor: String,
    val classrooms: Int,
    val totalEntries: Int,
)

internal data class BuildingRoomFacts(
    val classrooms: Int,
    val totalEntries: Int,
    val floors: List<BuildingFloorRooms>,
    val types: List<BuildingRoomTypeCount>,
) {
    val isEmpty: Boolean get() = totalEntries == 0
    val coveredFloors: Int get() = floors.size
}

/** 建筑档案里可如实展示的楼栋事实；不做用途、院系之类的猜测。 */
internal data class BuildingProfileFacts(
    val name: String,
    val sourceName: String,
    val category: String,
    val levels: Int,
    val levelsEstimated: Boolean,
    val heightMeters: Double,
    val footprintAreaSqm: Double,
    val widthMeters: Double,
    val depthMeters: Double,
    val outlineSource: BuildingOutlineSource,
    val outlineNote: String,
    val rooms: BuildingRoomFacts,
)

internal fun buildingProfileFacts(
    building: Campus3DBuildingShape,
    campusId: String,
    roomCells: List<RoomCell>,
): BuildingProfileFacts {
    val area = (ringArea(building.footprint) - building.holes.sumOf(::ringArea))
        .coerceAtLeast(0.0)
    return BuildingProfileFacts(
        name = building.name.ifBlank { "未命名楼栋" },
        sourceName = building.sourceName.trim(),
        category = building.category,
        levels = building.levels,
        levelsEstimated = building.levelsEstimated,
        heightMeters = building.height.toDouble(),
        footprintAreaSqm = area,
        widthMeters = building.bounds.width.toDouble(),
        depthMeters = building.bounds.height.toDouble(),
        outlineSource = outlineSourceOf(building.note),
        outlineNote = building.note.trim(),
        rooms = buildingRoomFacts(building, campusId, roomCells),
    )
}

private fun buildingRoomFacts(
    building: Campus3DBuildingShape,
    campusId: String,
    roomCells: List<RoomCell>,
): BuildingRoomFacts {
    // 与 Campus3DFloorPlanScreen 使用同一条匹配规则，避免档案里有的楼层点进去却是空的。
    val buildingName = building.name.trim()
    val matched = roomCells.filter { cell ->
        cell.campusId.equals(campusId, ignoreCase = true) && cell.building.trim() == buildingName
    }

    val types = matched
        .groupingBy { it.roomType }
        .eachCount()
        .map { (type, count) -> BuildingRoomTypeCount(type, count) }
        .sortedWith(compareByDescending<BuildingRoomTypeCount> { it.count }.thenBy { it.type.ordinal })

    val floors = matched
        .groupBy { floorLabel(it.floor) }
        .map { (label, cells) ->
            BuildingFloorRooms(
                label = label,
                rawFloor = cells.first().floor.trim().ifEmpty { label },
                classrooms = cells.count { it.roomType == RoomCellType.CLASSROOM },
                totalEntries = cells.size,
            )
        }
        .sortedWith(
            compareBy(
                { it.label.dropLast(1).toIntOrNull() ?: Int.MAX_VALUE },
                { it.label },
            )
        )

    return BuildingRoomFacts(
        classrooms = matched.count { it.roomType == RoomCellType.CLASSROOM },
        totalEntries = matched.size,
        floors = floors,
        types = types,
    )
}

private fun outlineSourceOf(note: String): BuildingOutlineSource =
    if (note.contains("描点") || note.contains("非 OSM", ignoreCase = true)) {
        BuildingOutlineSource.Traced
    } else {
        BuildingOutlineSource.OpenStreetMap
    }

private fun ringArea(points: List<Vec2>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (index in points.indices) {
        val current = points[index]
        val next = points[(index + 1) % points.size]
        sum += current.x.toDouble() * next.z - next.x.toDouble() * current.z
    }
    return abs(sum) / 2.0
}
