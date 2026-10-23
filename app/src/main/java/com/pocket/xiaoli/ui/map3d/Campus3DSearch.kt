package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.RoomCell

internal enum class Campus3DSearchKind(val label: String) {
    BUILDING("楼栋"),
    ROOM("教室"),
    POI("地点"),
    PITCH("场地"),
    WATER("水系"),
}

internal data class Campus3DSearchResult(
    val id: String,
    val kind: Campus3DSearchKind,
    val title: String,
    val subtitle: String,
    val hit: Campus3DHit,
    /** 教室结果带上楼层，选中后可直接打开对应楼层的平面图。 */
    val floor: String? = null,
)

/**
 * 3D 页内搜索：楼栋、已录入教室、OSM 地点/场地/水系共用一套结果。
 *
 * 空查询只列楼栋和地点，不把上百间教室一次性铺开；教室要输房间号或名字才出现。
 */
internal fun searchCampus3D(
    layout: Campus3DLayout,
    roomCells: List<RoomCell>,
    campusId: String,
    query: String,
    limit: Int = 40,
): List<Campus3DSearchResult> {
    val normalized = normalizeSearchQuery(query)
    val scored = ArrayList<ScoredResult>()

    layout.buildings.forEach { building ->
        val rank = if (normalized.isEmpty()) {
            3
        } else {
            minOf(
                matchRank(building.name, normalized),
                matchRank(building.sourceName, normalized),
            )
        }
        if (rank == Int.MAX_VALUE) return@forEach
        scored += ScoredResult(
            rank = rank,
            kindOrder = 0,
            result = Campus3DSearchResult(
                id = "building:${building.id}",
                kind = Campus3DSearchKind.BUILDING,
                title = building.name,
                subtitle = buildList {
                    add("${building.levels} 层")
                    add(categoryLabel(building.category))
                    if (building.sourceName.isNotBlank() && building.sourceName != building.name) {
                        add("又称${building.sourceName}")
                    }
                }.joinToString(" · "),
                hit = Campus3DHit.Building(building),
            ),
        )
    }

    if (normalized.isNotEmpty()) {
        roomCells.asSequence()
            .filter { it.campusId == campusId && (it.room.isNotBlank() || it.name.isNotBlank()) }
            .forEach { cell ->
                val building = layout.buildings.firstOrNull {
                    normalizeSearchQuery(it.name) == normalizeSearchQuery(cell.building)
                } ?: return@forEach
                val rank = listOf(cell.room, cell.name)
                    .filter { it.isNotBlank() }
                    .minOfOrNull { matchRank(it, normalized) }
                    ?: Int.MAX_VALUE
                if (rank == Int.MAX_VALUE) return@forEach
                scored += ScoredResult(
                    rank = rank,
                    kindOrder = 1,
                    result = Campus3DSearchResult(
                        id = "room:${cell.id}",
                        kind = Campus3DSearchKind.ROOM,
                        title = cell.room.ifBlank { cell.name },
                        subtitle = roomSubtitle(cell),
                        hit = Campus3DHit.Building(building),
                        floor = floorLabel(cell.floor),
                    ),
                )
            }
    }

    layout.pois.forEach { poi ->
        val rank = if (normalized.isEmpty()) 3 else matchRank(poi.name, normalized)
        if (rank == Int.MAX_VALUE) return@forEach
        scored += ScoredResult(
            rank = rank,
            kindOrder = 2,
            result = Campus3DSearchResult(
                id = "poi:${poi.id}",
                kind = Campus3DSearchKind.POI,
                title = poi.name,
                subtitle = "位置标注 · ${categoryLabel(poi.category)}",
                hit = Campus3DHit.Poi(poi),
            ),
        )
    }

    layout.pitches.forEach { pitch ->
        val rank = if (normalized.isEmpty()) 3 else matchRank(pitch.name, normalized)
        if (rank == Int.MAX_VALUE) return@forEach
        scored += ScoredResult(
            rank = rank,
            kindOrder = 3,
            result = Campus3DSearchResult(
                id = "pitch:${pitch.id}",
                kind = Campus3DSearchKind.PITCH,
                title = pitch.name,
                subtitle = "${sportLabel(pitch.kind, pitch.sport)} · 运动场地",
                hit = Campus3DHit.Pitch(pitch),
            ),
        )
    }

    layout.water.forEach { water ->
        val rank = if (normalized.isEmpty()) 3 else matchRank(water.name, normalized)
        if (rank == Int.MAX_VALUE) return@forEach
        scored += ScoredResult(
            rank = rank,
            kindOrder = 4,
            result = Campus3DSearchResult(
                id = "water:${water.id}",
                kind = Campus3DSearchKind.WATER,
                title = water.name,
                subtitle = "校园水系",
                hit = Campus3DHit.Water(water),
            ),
        )
    }

    return scored
        .sortedWith(
            compareBy<ScoredResult> { it.rank }
                .thenBy { it.kindOrder }
                .thenBy { it.result.title.length }
                .thenBy { it.result.title }
        )
        .take(limit)
        .map { it.result }
}

private data class ScoredResult(
    val rank: Int,
    val kindOrder: Int,
    val result: Campus3DSearchResult,
)

/** 0 完全相等，1 前缀，2 包含，3 被查询包含；越长越具体的反向包含才参与匹配。 */
private fun matchRank(text: String, query: String): Int {
    val value = normalizeSearchQuery(text)
    if (value.isEmpty()) return Int.MAX_VALUE
    return when {
        value == query -> 0
        value.startsWith(query) -> 1
        value.contains(query) -> 2
        query.contains(value) && value.length >= 2 -> 3
        else -> Int.MAX_VALUE
    }
}

private fun roomSubtitle(cell: RoomCell): String = buildList {
    add(cell.building)
    add(floorLabel(cell.floor))
    if (cell.name.isNotBlank() && cell.name != cell.room) add(cell.name)
    add(cell.roomType.label)
}.filter { it.isNotBlank() }
    .joinToString(" · ")

private fun normalizeSearchQuery(value: String): String =
    value.trim().lowercase().replace(" ", "")
