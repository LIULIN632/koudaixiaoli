package com.pocket.xiaoli.data.map.model

import com.pocket.xiaoli.data.map.CampusPoi

/** POI 与校园地图对象的绑定结果。 */
data class CampusPoiMapLink(
    val buildingId: String? = null,
    val floorId: String? = null,
    val roomId: String? = null,
) {
    val isLinked: Boolean get() = buildingId != null || roomId != null
}

/**
 * 把已有 CampusPoi 绑定到校园地图数据。
 *
 * 不修改 CampusPoi 的持久化结构，只在需要时做匹配；以后校园地图数据完善后，
 * 可以让自定义 POI 自动带上 buildingId / floorId / roomId。
 */
object CampusMapPoiLinker {

    fun link(poi: CampusPoi, data: CampusMapData): CampusPoiMapLink? {
        val building = findBuilding(poi, data)
        val room = building?.let { findRoom(poi, data, it.id) }

        if (building == null && room == null) return null

        val floorId = room?.floorId ?: building?.let { findFloor(poi, data, it.id)?.id }
        return CampusPoiMapLink(
            buildingId = building?.id,
            floorId = floorId,
            roomId = room?.id,
        )
    }

    private fun findBuilding(poi: CampusPoi, data: CampusMapData): CampusBuilding? {
        val queries = listOf(poi.building, poi.name)
            .map { normalize(it) }
            .filter { it.isNotBlank() }
        if (queries.isEmpty()) return null

        return data.buildings.firstOrNull { building ->
            val names = (listOf(building.name) + building.aliases).map { normalize(it) }
            queries.any { query ->
                names.any { name -> name.isNotBlank() && (query == name || query.contains(name)) }
            }
        }
    }

    private fun findFloor(
        poi: CampusPoi,
        data: CampusMapData,
        buildingId: String,
    ): CampusFloor? {
        val query = normalize(poi.floor)
        if (query.isBlank()) return null
        return data.floors.firstOrNull { floor ->
            floor.buildingId == buildingId && (
                normalize(floor.name) == query ||
                    floor.level.toString() == query ||
                    "${floor.level}层" == query
                )
        }
    }

    private fun findRoom(
        poi: CampusPoi,
        data: CampusMapData,
        buildingId: String,
    ): CampusRoom? {
        val query = normalize(poi.name)
        if (query.isBlank()) return null
        return data.rooms
            .filter { it.buildingId == buildingId }
            .sortedByDescending { it.name.length }
            .firstOrNull { room ->
                val roomName = normalize(room.name)
                roomName.isNotBlank() && query.contains(roomName)
            }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(" ", "")
}