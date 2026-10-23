package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapData

/** 课表里的教室文本，定位到校园地图后的目标。 */
data class CourseRoomTarget(
    val raw: String,
    val buildingId: String? = null,
    val floorId: String? = null,
    val roomId: String? = null,
    val roomName: String? = null,
) {
    val isLinked: Boolean get() = roomId != null
    val canOpenBuilding: Boolean get() = buildingId != null
}

/**
 * 课程教室 -> 校园地图房间。
 *
 * 支持常见写法：
 * - `教学楼A 101`
 * - `教学楼A-406`
 * - `A406`
 * - `406`
 *
 * 没匹配到房间时，仍可能返回 buildingId，供 UI 打开楼栋。
 */
object CourseRoomLocator {

    fun locate(
        courseRoom: String,
        campusId: String,
        data: CampusMapData,
    ): CourseRoomTarget? {
        val raw = courseRoom.trim()
        if (raw.isBlank()) return null
        if (data.campusId.isNotBlank() && campusId.isNotBlank() && data.campusId != campusId) return null

        val normalizedRaw = normalize(raw)
        val building = data.buildings.firstOrNull { item ->
            val names = listOf(item.name) + item.aliases
            names.any { name ->
                val normalizedName = normalize(name)
                normalizedName.isNotBlank() && normalizedRaw.contains(normalizedName)
            }
        }

        if (building != null) {
            val room = findRoom(raw, data.rooms.filter { it.buildingId == building.id })
            return CourseRoomTarget(
                raw = raw,
                buildingId = building.id,
                floorId = room?.floorId,
                roomId = room?.id,
                roomName = room?.name,
            )
        }

        val exactMatches = data.rooms.filter { roomMatchesName(it, normalizedRaw) }
        if (exactMatches.size == 1) return exactMatches.first().toTarget(raw)

        val roomNumber = Regex("""[A-Za-z]?\d{2,5}""").find(raw)?.value
        if (roomNumber != null) {
            val numberMatches = data.rooms.filter { normalize(it.name) == normalize(roomNumber) }
            if (numberMatches.size == 1) return numberMatches.first().toTarget(raw)
        }

        return null
    }

    private fun findRoom(raw: String, rooms: List<com.pocket.xiaoli.data.map.model.CampusRoom>): com.pocket.xiaoli.data.map.model.CampusRoom? {
        val normalizedRaw = normalize(raw)
        rooms.firstOrNull { roomMatchesName(it, normalizedRaw) }?.let { return it }
        val roomNumber = Regex("""[A-Za-z]?\d{2,5}""").find(raw)?.value
        if (roomNumber != null) {
            val normalizedNumber = normalize(roomNumber)
            rooms.firstOrNull { roomMatchesName(it, normalizedNumber) }?.let { return it }
        }
        return rooms
            .sortedByDescending { room -> namesOf(room).maxOfOrNull { it.length } ?: 0 }
            .firstOrNull { room ->
                namesOf(room).any { name ->
                    val normalizedName = normalize(name)
                    normalizedName.isNotBlank() && normalizedRaw.contains(normalizedName)
                }
            }
    }

    private fun roomMatchesName(
        room: com.pocket.xiaoli.data.map.model.CampusRoom,
        normalizedQuery: String,
    ): Boolean = namesOf(room).any { normalize(it) == normalizedQuery }

    private fun namesOf(
        room: com.pocket.xiaoli.data.map.model.CampusRoom,
    ): List<String> = buildList {
        if (room.name.isNotBlank()) add(room.name)
        addAll(room.aliases)
    }

    private fun com.pocket.xiaoli.data.map.model.CampusRoom.toTarget(raw: String) =
        CourseRoomTarget(
            raw = raw,
            buildingId = buildingId,
            floorId = floorId,
            roomId = id,
            roomName = name,
        )

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(" ", "")
}