package com.pocket.xiaoli.data.map.model

/**
 * 3D 校园沙盘数据。
 *
 * 数据来自 OpenStreetMap 实测轮廓，由 `tools/campus-map-data/build_campus_3d.py`
 * 生成到 `assets/campus_map/{campusId}/campus_3d.json`；坐标统一为 WGS-84。
 * 高度字段仅用于示意，`levelsEstimated` 标记是否由面积推算。
 */
data class Campus3DScene(
    val schemaVersion: Int = 1,
    val campusId: String = "",
    val name: String = "",
    val generatedAt: String = "",
    val attribution: String = "",
    val sourceUrl: String = "",
    val center: Campus3DPoint? = null,
    val bounds: Campus3DBounds? = null,
    val boundary: List<Campus3DPoint> = emptyList(),
    val buildings: List<Campus3DBuilding> = emptyList(),
    val water: List<Campus3DWater> = emptyList(),
    val green: List<Campus3DGreen> = emptyList(),
    val pitches: List<Campus3DPitch> = emptyList(),
    val paths: List<Campus3DPath> = emptyList(),
    val zones: List<Campus3DZone> = emptyList(),
    val pois: List<Campus3DPoi> = emptyList(),
) {
    val hasGeometry: Boolean get() = boundary.size >= 3 && buildings.isNotEmpty()
}

data class Campus3DPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

data class Campus3DBounds(
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLng: Double = 0.0,
    val maxLng: Double = 0.0,
)

data class Campus3DBuilding(
    val id: String = "",
    val name: String = "",
    val sourceName: String = "",
    val category: String = "",
    val levels: Int = 1,
    val levelsEstimated: Boolean = false,
    val heightMeters: Double = 0.0,
    val outline: List<Campus3DPoint> = emptyList(),
    val holes: List<List<Campus3DPoint>> = emptyList(),
    val note: String = "",
)

data class Campus3DWater(
    val id: String = "",
    val name: String = "",
    val outline: List<Campus3DPoint> = emptyList(),
    val label: Campus3DPoint? = null,
)

data class Campus3DGreen(
    val id: String = "",
    val kind: String = "",
    val outline: List<Campus3DPoint> = emptyList(),
)

data class Campus3DPitch(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val sport: String = "",
    val outline: List<Campus3DPoint> = emptyList(),
    val label: Campus3DPoint? = null,
)

data class Campus3DPath(
    val id: String = "",
    val points: List<Campus3DPoint> = emptyList(),
)

data class Campus3DZone(
    val name: String = "",
    val kind: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
) {
    val point: Campus3DPoint get() = Campus3DPoint(lat, lng)
}

data class Campus3DPoi(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val note: String = "",
) {
    val point: Campus3DPoint get() = Campus3DPoint(lat, lng)
}
