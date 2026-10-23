package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusBuilding
import com.pocket.xiaoli.data.map.model.CampusFloor
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.data.map.model.CampusRoom

/**
 * 把「教室分布图」里用户维护的 [RoomCell] 叠加到校园地图数据上。
 *
 * 当前 assets 地图数据可能还没投稿，而用户已经在教室分布图里录了楼栋 / 楼层 / 教室。
 * 这个桥接器让这些数据也能：
 * - 出现在地图画布上（楼栋块）
 * - 被 [CourseRoomLocator] 识别（课程教室 -> 楼栋 / 楼层）
 * - 出现在当前位置选择器里（每个房间生成 room 节点）
 *
 * 仅生成示意图需要的最小实体，不伪造真实经纬度；已存在同名楼栋 / 楼层 / 教室时不会重复添加。
 */
object CampusMapRoomBridge {

    private const val PREFIX = "roomcell:"

    fun overlay(
        base: CampusMapData,
        campusId: String,
        cells: List<RoomCell>,
    ): CampusMapData {
        val scoped = cells.filter {
            it.campusId.equals(campusId, ignoreCase = true) &&
                it.building.isNotBlank() &&
                it.room.isNotBlank()
        }
        if (scoped.isEmpty()) return base

        val buildings = base.buildings.toMutableList()
        val floors = base.floors.toMutableList()
        val rooms = base.rooms.toMutableList()
        val nodes = base.nodes.toMutableList()

        val buildingByKey = buildings
            .mapNotNull { building -> normalize(building.name)?.let { it to building } }
            .toMap()
            .toMutableMap()

        val existingRoomKeys = rooms.mapNotNull { room ->
            val building = buildings.firstOrNull { it.id == room.buildingId } ?: return@mapNotNull null
            val floor = floors.firstOrNull { it.id == room.floorId }
            roomKey(building.name, floorMatchKey(floor), room.name) to room.id
        }.toMap().toMutableMap()

        scoped.forEach { cell ->
            val buildingName = cell.building.trim()
            val building = buildingByKey[normalize(buildingName)] ?: CampusBuilding(
                id = stableId("b", buildingName),
                name = buildingName,
            ).also {
                buildings += it
                buildingByKey[normalize(buildingName)!!] = it
            }

            val rawFloor = cell.floor.trim().ifBlank { "1" }
            val floor = floors.firstOrNull {
                it.buildingId == building.id && floorMatches(it, rawFloor)
            } ?: CampusFloor(
                id = stableId("f", buildingName, rawFloor),
                buildingId = building.id,
                level = rawFloor.toIntOrNull() ?: 0,
                name = floorDisplay(rawFloor),
            ).also { floors += it }

            val roomName = cell.room.trim()
            val key = roomKey(buildingName, floorMatchKey(floor), roomName)
            if (existingRoomKeys.containsKey(key)) return@forEach

            val roomId = stableId("r", buildingName, rawFloor, roomName)
            val nodeId = stableId("n", buildingName, rawFloor, roomName)
            rooms += CampusRoom(
                id = roomId,
                buildingId = building.id,
                floorId = floor.id,
                name = roomName,
                type = cell.roomType.key,
                nodeId = nodeId,
                aliases = buildList {
                    if (cell.name.isNotBlank()) add(cell.name.trim())
                },
            )
            nodes += CampusNode(
                id = nodeId,
                buildingId = building.id,
                floorId = floor.id,
                kind = nodeKind(cell.roomType),
            )
            existingRoomKeys[key] = roomId
        }

        val floorRoomIds = rooms.groupBy { it.floorId }.mapValues { (_, grouped) -> grouped.map { it.id } }
        val rebuiltFloors = floors.map { floor ->
            floor.copy(
                roomIds = (
                    floor.roomIds.filter { id -> rooms.any { it.id == id } } +
                        floorRoomIds[floor.id].orEmpty()
                    ).distinct()
            )
        }
        val buildingFloorIds = rebuiltFloors.groupBy { it.buildingId }
            .mapValues { (_, grouped) -> grouped.map { it.id } }
        val rebuiltBuildings = buildings.map { building ->
            building.copy(
                floorIds = (
                    building.floorIds.filter { id -> rebuiltFloors.any { it.id == id } } +
                        buildingFloorIds[building.id].orEmpty()
                    ).distinct()
            )
        }

        return base.copy(
            buildings = rebuiltBuildings,
            floors = rebuiltFloors,
            rooms = rooms,
            nodes = nodes,
        )
    }

    /**
     * 把地图数据里的房间转成只读 [RoomCell]，供已有的 `RoomFloorMapPanel` /
     * `RoomLayoutGrid` 渲染，不再为「地图数据有房间、教室分布图没录」单独写一套 UI。
     */
    fun toRoomCells(data: CampusMapData): List<RoomCell> {
        val buildingsById = data.buildings.associateBy { it.id }
        val floorsById = data.floors.associateBy { it.id }
        return data.rooms.mapNotNull { room ->
            val building = buildingsById[room.buildingId] ?: return@mapNotNull null
            val floor = floorsById[room.floorId]
            RoomCell(
                id = stableId("panel", room.id),
                campusId = data.campusId,
                building = building.name,
                floor = floor?.name?.takeIf { it.isNotBlank() }
                    ?: floor?.level?.toString().orEmpty(),
                room = room.name,
                name = room.name,
                type = roomTypeKey(room.type),
            )
        }
    }

    private fun roomTypeKey(type: String): String = when (type.lowercase()) {
        "office" -> RoomCellType.OFFICE.key
        "lab", "laboratory" -> RoomCellType.LAB.key
        "computer" -> RoomCellType.COMPUTER.key
        "restroom", "toilet", "wc" -> RoomCellType.RESTROOM.key
        "stairs", "stair" -> RoomCellType.STAIR.key
        "elevator", "lift" -> RoomCellType.ELEVATOR.key
        "entrance" -> RoomCellType.ENTRANCE.key
        "corridor", "hall" -> RoomCellType.CORRIDOR.key
        else -> RoomCellType.CLASSROOM.key
    }

    private fun roomKey(building: String, floor: String, room: String): String =
        listOf(building, floor, room).joinToString("\u0000") {
            it.trim().lowercase()
        }

    private fun floorMatchKey(floor: CampusFloor?): String =
        floor?.level?.takeIf { it != 0 }?.toString()
            ?: floor?.name.orEmpty()

    private fun floorMatches(floor: CampusFloor, rawFloor: String): Boolean {
        if (floor.name.equals(rawFloor, ignoreCase = true)) return true
        val level = rawFloor.toIntOrNull() ?: return false
        return floor.level == level
    }

    private fun floorDisplay(rawFloor: String): String {
        val number = rawFloor.toIntOrNull()
        return if (number != null && rawFloor == number.toString()) "${number}层" else rawFloor
    }

    private fun nodeKind(type: RoomCellType): String = when (type) {
        RoomCellType.ENTRANCE -> "entrance"
        RoomCellType.STAIR -> "stairs"
        RoomCellType.ELEVATOR -> "elevator"
        RoomCellType.CORRIDOR -> "corridor"
        else -> "room"
    }

    private fun normalize(value: String): String? =
        value.trim().lowercase().takeIf { it.isNotBlank() }

    private fun stableId(prefix: String, vararg parts: String): String =
        PREFIX + prefix + ":" + parts.joinToString("|") {
            it.trim().lowercase().replace("|", "/")
        }
}