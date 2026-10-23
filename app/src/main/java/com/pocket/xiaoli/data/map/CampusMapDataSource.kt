package com.pocket.xiaoli.data.map

import android.content.Context
import com.pocket.xiaoli.data.map.model.CampusGeoJson
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusMapIssue
import com.pocket.xiaoli.data.map.model.CampusMapJson
import com.pocket.xiaoli.data.map.model.CampusMapValidator

data class CampusMapLoadResult(
    val data: CampusMapData,
    val issues: List<CampusMapIssue> = emptyList(),
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * 校园地图数据源。
 *
 * 资源约定：
 * ```text
 * campus_map/{campusId}/campus_map.json   楼栋 / 楼层 / 房间 / 节点 / 边
 * campus_map/{campusId}/roads.geojson     道路（可选）
 * campus_map/{campusId}/places.geojson    POI（可选）
 * ```
 *
 * 后续可在此扩展 Room 用户纠错缓存 / 多版本热更新，而 UI 不需要改。
 */
class CampusMapDataSource(
    private val assetReader: (String) -> String?,
) {
    fun load(campusId: String): CampusMapLoadResult {
        val corePath = campusMapPath(campusId)
        val roadsPath = roadsPath(campusId)
        val placesPath = placesPath(campusId)

        val coreJson = assetReader(corePath)
        val roadsJson = assetReader(roadsPath)
        val placesJson = assetReader(placesPath)

        if (coreJson == null && roadsJson == null && placesJson == null) {
            return CampusMapLoadResult(
                data = CampusMapData(campusId = campusId),
                issues = listOf(
                    CampusMapIssue("campus_map_asset_missing", "未找到校园地图资源：$ASSET_ROOT/$campusId/")
                ),
            )
        }

        var data = coreJson?.let { CampusMapJson.parse(it) } ?: CampusMapData(campusId = campusId)
        if (roadsJson != null) {
            data = data.copy(roads = data.roads + CampusGeoJson.toRoads(roadsJson))
        }
        if (placesJson != null) {
            data = data.copy(places = data.places + CampusGeoJson.toPlaces(placesJson))
        }

        return CampusMapLoadResult(
            data = data,
            issues = CampusMapValidator.validate(data),
        )
    }

    fun campusMapPath(campusId: String): String = "$ASSET_ROOT/$campusId/campus_map.json"
    fun roadsPath(campusId: String): String = "$ASSET_ROOT/$campusId/roads.geojson"
    fun placesPath(campusId: String): String = "$ASSET_ROOT/$campusId/places.geojson"

    companion object {
        const val ASSET_ROOT = "campus_map"

        fun fromAssets(context: Context): CampusMapDataSource =
            CampusMapDataSource { path ->
                runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
    }
}