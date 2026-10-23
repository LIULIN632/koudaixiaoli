package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusMapPlace

data class CampusMapCorrection(
    val id: String,
    val campusId: String,
    /** place / room / node */
    val kind: String,
    val targetId: String,
    val name: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val buildingId: String? = null,
    val floorId: String? = null,
    val payload: String = "",
)

object CampusMapCorrectionApplier {

    fun apply(
        data: CampusMapData,
        corrections: List<CampusMapCorrection>,
    ): CampusMapData {
        var result = data
        corrections.forEach { correction -> result = applyOne(result, correction) }
        return result
    }

    private fun applyOne(data: CampusMapData, correction: CampusMapCorrection): CampusMapData =
        when (correction.kind) {
            "place" -> {
                val exists = data.places.any { it.id == correction.targetId }
                val updated = if (exists) {
                    data.places.map { place ->
                        if (place.id == correction.targetId) {
                            place.copy(
                                name = correction.name.ifBlank { place.name },
                                kind = correction.payload.ifBlank { place.kind },
                                lat = correction.lat ?: place.lat,
                                lng = correction.lng ?: place.lng,
                                buildingId = correction.buildingId ?: place.buildingId,
                            )
                        } else {
                            place
                        }
                    }
                } else {
                    data.places + CampusMapPlace(
                        id = correction.targetId,
                        name = correction.name,
                        kind = correction.payload,
                        lat = correction.lat,
                        lng = correction.lng,
                        buildingId = correction.buildingId,
                    )
                }
                data.copy(places = updated)
            }

            "room" -> data.copy(
                rooms = data.rooms.map { room ->
                    if (room.id == correction.targetId) {
                        room.copy(
                            name = correction.name.ifBlank { room.name },
                            buildingId = correction.buildingId ?: room.buildingId,
                            floorId = correction.floorId ?: room.floorId,
                        )
                    } else {
                        room
                    }
                }
            )

            // 课表里写「三教S552」，但地图规范教室名是「S552」时，用户可提交一条别名纠错。
            // 纠错缓存在本地，CourseRoomLocator 会优先读规范名，再读 aliases。
            "room_alias" -> {
                val alias = correction.name.trim().ifBlank { correction.payload.trim() }
                if (alias.isBlank()) data
                else data.copy(
                    rooms = data.rooms.map { room ->
                        if (room.id == correction.targetId) {
                            room.copy(aliases = (room.aliases + alias).distinct())
                        } else {
                            room
                        }
                    }
                )
            }

            "node" -> data.copy(
                nodes = data.nodes.map { node ->
                    if (node.id == correction.targetId) {
                        node.copy(
                            lat = correction.lat ?: node.lat,
                            lng = correction.lng ?: node.lng,
                            kind = correction.payload.ifBlank { node.kind },
                            buildingId = correction.buildingId ?: node.buildingId,
                            floorId = correction.floorId ?: node.floorId,
                        )
                    } else {
                        node
                    }
                }
            )

            else -> data
        }
}