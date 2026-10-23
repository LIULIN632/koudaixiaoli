package com.pocket.xiaoli.data.map

import android.content.Context
import com.pocket.xiaoli.data.map.model.Campus3DMapJson
import com.pocket.xiaoli.data.map.model.Campus3DMapValidator
import com.pocket.xiaoli.data.map.model.Campus3DScene
import com.pocket.xiaoli.data.map.model.CampusMapIssue

data class Campus3DMapLoadResult(
    val scene: Campus3DScene,
    val issues: List<CampusMapIssue> = emptyList(),
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * 3D 沙盘数据源。
 *
 * 主资产 `campus_map/{campusId}/campus_3d.json` 只放 OpenStreetMap 数据；
 * 人工描点的补充楼栋放同目录 `campus_3d_traced.json`，加载时并入，
 * 避免改写 OSM 主数据。补充资产缺失时按原样加载主资产。
 */
class Campus3DMapDataSource(
    private val assetReader: (String) -> String?,
) {
    fun load(campusId: String): Campus3DMapLoadResult {
        val path = scenePath(campusId)
        val json = assetReader(path)
            ?: return Campus3DMapLoadResult(
                scene = Campus3DScene(campusId = campusId),
                issues = listOf(
                    CampusMapIssue(
                        "campus_3d_asset_missing",
                        "未找到 3D 校园地图资源：$ASSET_ROOT/$campusId/campus_3d.json",
                    )
                ),
            )
        val parsed = Campus3DMapJson.parse(json)
        val base = if (parsed.campusId.isBlank()) parsed.copy(campusId = campusId) else parsed

        val tracedJson = assetReader(tracedPath(campusId))
        val supplement = tracedJson?.let(Campus3DMapJson::parse)?.takeIf {
            (it.campusId.isBlank() || it.campusId == campusId) &&
                (it.buildings.isNotEmpty() || it.pois.isNotEmpty())
        }
        val scene = supplement?.let { merge(base, it) } ?: base
        val issues = buildList {
            if (tracedJson != null && supplement == null) {
                add(
                    CampusMapIssue(
                        "campus_3d_traced_invalid",
                        "3D 补充轮廓为空、解析失败或 campusId 不匹配",
                    )
                )
            }
            addAll(Campus3DMapValidator.validate(scene))
        }
        return Campus3DMapLoadResult(scene = scene, issues = issues)
    }

    /** 补充楼栋按 id 去重，楼栋同名的 POI 标记让位给真实楼体。 */
    private fun merge(base: Campus3DScene, supplement: Campus3DScene): Campus3DScene {
        val buildingIds = base.buildings.mapTo(mutableSetOf()) { it.id }
        val extraBuildings = supplement.buildings.filter { it.id.isNotBlank() && buildingIds.add(it.id) }
        val buildings = base.buildings + extraBuildings
        val buildingNames = buildings
            .flatMap { listOf(it.name.trim(), it.sourceName.trim()) }
            .filter { it.isNotBlank() }
            .toSet()

        val poiIds = base.pois.mapTo(mutableSetOf()) { it.id }
        val extraPois = supplement.pois.filter {
            it.id.isNotBlank() && poiIds.add(it.id) && it.name.trim() !in buildingNames
        }
        val pois = (base.pois + extraPois).filter { it.name.trim() !in buildingNames }
        if (extraBuildings.isEmpty() && extraPois.isEmpty() && pois.size == base.pois.size) return base

        return base.copy(
            buildings = buildings,
            pois = pois,
            attribution = mergeAttribution(base.attribution, supplement.attribution),
        )
    }

    private fun mergeAttribution(base: String, extra: String): String = when {
        extra.isBlank() || base.contains(extra) -> base
        base.isBlank() -> extra
        else -> "$base；$extra"
    }

    fun scenePath(campusId: String): String = "$ASSET_ROOT/$campusId/campus_3d.json"

    fun tracedPath(campusId: String): String = "$ASSET_ROOT/$campusId/campus_3d_traced.json"

    companion object {
        const val ASSET_ROOT = "campus_map"

        fun fromAssets(context: Context): Campus3DMapDataSource =
            Campus3DMapDataSource { path ->
                runCatching {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
    }
}
