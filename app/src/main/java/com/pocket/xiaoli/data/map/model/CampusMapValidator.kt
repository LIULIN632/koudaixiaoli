package com.pocket.xiaoli.data.map.model

/** 一条校园地图数据校验问题。 */
data class CampusMapIssue(
    val code: String,
    val message: String,
)

object CampusMapValidator {

    fun validate(data: CampusMapData): List<CampusMapIssue> {
        val issues = mutableListOf<CampusMapIssue>()

        if (data.campusId.isBlank()) {
            issues += CampusMapIssue("campus_id_blank", "campusId 不能为空")
        }

        checkDuplicateIds(issues, "building", data.buildings.map { it.id })
        checkDuplicateIds(issues, "entrance", data.entrances.map { it.id })
        checkDuplicateIds(issues, "floor", data.floors.map { it.id })
        checkDuplicateIds(issues, "room", data.rooms.map { it.id })
        checkDuplicateIds(issues, "node", data.nodes.map { it.id })

        val buildingIds = data.buildings.mapTo(mutableSetOf()) { it.id }
        val entranceIds = data.entrances.mapTo(mutableSetOf()) { it.id }
        val floorIds = data.floors.mapTo(mutableSetOf()) { it.id }
        val roomIds = data.rooms.mapTo(mutableSetOf()) { it.id }
        val nodeIds = data.nodes.mapTo(mutableSetOf()) { it.id }
        val floorBuilding = data.floors.associate { it.id to it.buildingId }

        data.floors.forEach { floor ->
            if (floor.buildingId !in buildingIds) {
                issues += issue("floor_building_missing", "楼层 ${floor.id} 引用了不存在的楼栋 ${floor.buildingId}")
            }
        }

        data.entrances.forEach { entrance ->
            if (entrance.buildingId !in buildingIds) {
                issues += issue("entrance_building_missing", "入口 ${entrance.id} 引用了不存在的楼栋 ${entrance.buildingId}")
            }
        }

        data.rooms.forEach { room ->
            if (room.buildingId !in buildingIds) {
                issues += issue("room_building_missing", "房间 ${room.id} 引用了不存在的楼栋 ${room.buildingId}")
            }
            if (room.floorId !in floorIds) {
                issues += issue("room_floor_missing", "房间 ${room.id} 引用了不存在的楼层 ${room.floorId}")
            } else if (floorBuilding[room.floorId] != room.buildingId) {
                issues += issue("room_floor_mismatch", "房间 ${room.id} 的楼栋与楼层不一致")
            }
            room.nodeId?.let { nodeId ->
                if (nodeId !in nodeIds) {
                    issues += issue("room_node_missing", "房间 ${room.id} 引用了不存在的节点 $nodeId")
                }
            }
        }

        data.nodes.forEach { node ->
            node.buildingId?.let { buildingId ->
                if (buildingId !in buildingIds) {
                    issues += issue("node_building_missing", "节点 ${node.id} 引用了不存在的楼栋 $buildingId")
                }
            }
            node.floorId?.let { floorId ->
                if (floorId !in floorIds) {
                    issues += issue("node_floor_missing", "节点 ${node.id} 引用了不存在的楼层 $floorId")
                }
            }
        }

        data.edges.forEach { edge ->
            if (edge.from !in nodeIds) {
                issues += issue("edge_from_missing", "边 ${edge.from} -> ${edge.to} 的起点节点不存在")
            }
            if (edge.to !in nodeIds) {
                issues += issue("edge_to_missing", "边 ${edge.from} -> ${edge.to} 的终点节点不存在")
            }
            if (edge.meters < 0) {
                issues += issue("edge_negative_meters", "边 ${edge.from} -> ${edge.to} 的长度不能为负数")
            }
        }

        data.buildings.forEach { building ->
            building.entranceIds.forEach { entranceId ->
                if (entranceId !in entranceIds) {
                    issues += issue("building_entrance_missing", "楼栋 ${building.id} 引用了不存在的入口 $entranceId")
                }
            }
            building.floorIds.forEach { floorId ->
                if (floorId !in floorIds) {
                    issues += issue("building_floor_missing", "楼栋 ${building.id} 引用了不存在的楼层 $floorId")
                }
            }
        }

        data.floors.forEach { floor ->
            floor.roomIds.forEach { roomId ->
                if (roomId !in roomIds) {
                    issues += issue("floor_room_missing", "楼层 ${floor.id} 引用了不存在的房间 $roomId")
                } else if (data.rooms.first { it.id == roomId }.floorId != floor.id) {
                    issues += issue("floor_room_mismatch", "楼层 ${floor.id} 与房间 $roomId 的 floorId 不一致")
                }
            }
        }

        return issues
    }

    private fun checkDuplicateIds(
        issues: MutableList<CampusMapIssue>,
        label: String,
        ids: List<String>,
    ) {
        ids.groupingBy { it }.eachCount()
            .filter { (id, count) -> id.isNotBlank() && count > 1 }
            .keys
            .forEach { id ->
                issues += issue("${label}_id_duplicate", "$label id 重复：$id")
            }
        ids.filter { it.isBlank() }.forEach {
            issues += issue("${label}_id_blank", "$label id 不能为空")
        }
    }

    private fun issue(code: String, message: String) = CampusMapIssue(code, message)
}
