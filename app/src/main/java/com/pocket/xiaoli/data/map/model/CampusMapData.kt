package com.pocket.xiaoli.data.map.model

/**
 * 校园地图基础数据。
 *
 * 坐标系与业务约定：
 * - lat/lng 可选：没有真实坐标的示意地图可以先留空；
 * - 楼栋 / 楼层 / 房间 / 节点 / 边 使用稳定字符串 id；
 * - 室内节点通过 floorId 表示楼层，跨楼层由 edges 里的楼梯 / 电梯连接。
 */
data class CampusMapData(
    val campusId: String = "",
    val buildings: List<CampusBuilding> = emptyList(),
    val entrances: List<CampusEntrance> = emptyList(),
    val floors: List<CampusFloor> = emptyList(),
    val rooms: List<CampusRoom> = emptyList(),
    val nodes: List<CampusNode> = emptyList(),
    val edges: List<CampusEdge> = emptyList(),
    val roads: List<CampusRoad> = emptyList(),
    val places: List<CampusMapPlace> = emptyList(),
)

data class CampusLatLng(
    val lat: Double,
    val lng: Double,
)

data class CampusRoad(
    val id: String = "",
    val name: String = "",
    /** footway / primary / stairs / elevator_hall 等 */
    val kind: String = "",
    val points: List<CampusLatLng> = emptyList(),
)

data class CampusMapPlace(
    val id: String = "",
    val name: String = "",
    /** library / dining / dorm / gate / atelier 等 */
    val kind: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val buildingId: String? = null,
)

data class CampusBuilding(
    val id: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val entranceIds: List<String> = emptyList(),
    val floorIds: List<String> = emptyList(),
    val lat: Double? = null,
    val lng: Double? = null,
)

data class CampusEntrance(
    val id: String = "",
    val buildingId: String = "",
    val name: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

data class CampusFloor(
    val id: String = "",
    val buildingId: String = "",
    val level: Int = 0,
    val name: String = "",
    val planAsset: String? = null,
    val roomIds: List<String> = emptyList(),
)

data class CampusRoom(
    val id: String = "",
    val buildingId: String = "",
    val floorId: String = "",
    val name: String = "",
    val type: String = "",
    val nodeId: String? = null,
    /** 课表 / 投稿里的别名；用于把「三教S552」这类文本映射到规范教室名 */
    val aliases: List<String> = emptyList(),
)

data class CampusNode(
    val id: String = "",
    val buildingId: String? = null,
    val floorId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    /** corridor / room / stairs / elevator / entrance / outdoor */
    val kind: String = "",
)

data class CampusEdge(
    val from: String = "",
    val to: String = "",
    val meters: Double = 0.0,
    /** corridor / stairs / elevator / outdoor */
    val kind: String = "",
)