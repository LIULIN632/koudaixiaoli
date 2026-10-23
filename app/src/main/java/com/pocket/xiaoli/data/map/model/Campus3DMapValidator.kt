package com.pocket.xiaoli.data.map.model

/** 3D 沙盘数据校验：保证渲染前关键字段可用，问题沿用 [CampusMapIssue]。 */
object Campus3DMapValidator {

    const val SUPPORTED_SCHEMA_VERSION = 1

    fun validate(scene: Campus3DScene): List<CampusMapIssue> {
        val issues = mutableListOf<CampusMapIssue>()

        if (scene.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            issues += issue(
                "campus_3d_schema_unsupported",
                "不支持的 3D 地图数据版本：${scene.schemaVersion}",
            )
        }
        if (scene.campusId.isBlank()) {
            issues += issue("campus_3d_id_blank", "campusId 不能为空")
        }
        if (scene.boundary.size < 3) {
            issues += issue("campus_3d_boundary_invalid", "校区边界至少需要 3 个点")
        }
        scene.boundary.forEachIndexed { index, point ->
            checkPoint(issues, "校区边界第 ${index + 1} 个点", point)
        }
        scene.bounds?.let { checkBounds(issues, it) }
        scene.center?.let { checkPoint(issues, "场景中心点", it) }

        checkDuplicateIds(issues, "building", scene.buildings.map { it.id })
        checkDuplicateIds(issues, "water", scene.water.map { it.id })
        checkDuplicateIds(issues, "green", scene.green.map { it.id })
        checkDuplicateIds(issues, "pitch", scene.pitches.map { it.id })
        checkDuplicateIds(issues, "path", scene.paths.map { it.id })
        checkDuplicateIds(issues, "poi", scene.pois.map { it.id })

        scene.buildings.forEach { building ->
            if (building.outline.size < 3) {
                issues += issue(
                    "building_outline_invalid",
                    "楼栋 ${building.id} 的轮廓至少需要 3 个点",
                )
            }
            if (building.levels < 1) {
                issues += issue(
                    "building_levels_invalid",
                    "楼栋 ${building.id} 的层数必须大于 0",
                )
            }
            if (!building.heightMeters.isFinite() || building.heightMeters <= 0.0) {
                issues += issue(
                    "building_height_invalid",
                    "楼栋 ${building.id} 的高度必须大于 0",
                )
            }
            building.outline.forEach { point -> checkPoint(issues, "楼栋 ${building.id}", point) }
            building.holes.forEach { hole ->
                if (hole.size < 3) {
                    issues += issue(
                        "building_hole_invalid",
                        "楼栋 ${building.id} 的内院轮廓至少需要 3 个点",
                    )
                }
                hole.forEach { point -> checkPoint(issues, "楼栋 ${building.id} 内院", point) }
            }
        }

        scene.water.forEach { water ->
            if (water.outline.size < 3) {
                issues += issue("water_outline_invalid", "水系 ${water.id} 的轮廓至少需要 3 个点")
            }
            water.outline.forEach { checkPoint(issues, "水系 ${water.id}", it) }
        }

        scene.green.forEach { green ->
            if (green.outline.size < 3) {
                issues += issue("green_outline_invalid", "绿地 ${green.id} 的轮廓至少需要 3 个点")
            }
            green.outline.forEach { checkPoint(issues, "绿地 ${green.id}", it) }
        }

        scene.pitches.forEach { pitch ->
            if (pitch.outline.size < 3) {
                issues += issue("pitch_outline_invalid", "场地 ${pitch.id} 的轮廓至少需要 3 个点")
            }
            pitch.outline.forEach { checkPoint(issues, "场地 ${pitch.id}", it) }
        }

        scene.paths.forEach { path ->
            if (path.points.size < 2) {
                issues += issue("path_points_invalid", "步道 ${path.id} 至少需要 2 个点")
            }
            path.points.forEach { checkPoint(issues, "步道 ${path.id}", it) }
        }

        scene.zones.forEach { checkPoint(issues, "区域标注 ${it.name}", it.point) }
        scene.pois.forEach { poi ->
            if (poi.name.isBlank()) {
                issues += issue("poi_name_blank", "标注点 ${poi.id} 缺少名称")
            }
            checkPoint(issues, "标注点 ${poi.id}", poi.point)
        }

        return issues
    }

    private fun checkBounds(issues: MutableList<CampusMapIssue>, bounds: Campus3DBounds) {
        val values = listOf(bounds.minLat, bounds.maxLat, bounds.minLng, bounds.maxLng)
        if (values.any { !it.isFinite() }) {
            issues += issue("campus_3d_bounds_invalid", "场景 bounds 含非法数值")
        }
        if (bounds.minLat > bounds.maxLat || bounds.minLng > bounds.maxLng) {
            issues += issue("campus_3d_bounds_invalid", "场景 bounds 的最小值不能大于最大值")
        }
    }

    private fun checkPoint(
        issues: MutableList<CampusMapIssue>,
        label: String,
        point: Campus3DPoint,
    ) {
        if (!point.lat.isFinite() || !point.lng.isFinite() ||
            point.lat !in -90.0..90.0 || point.lng !in -180.0..180.0
        ) {
            issues += issue("campus_3d_point_invalid", "$label 坐标非法：${point.lat}, ${point.lng}")
        }
    }

    private fun checkDuplicateIds(
        issues: MutableList<CampusMapIssue>,
        label: String,
        ids: List<String>,
    ) {
        ids.filter { it.isBlank() }.forEach {
            issues += issue("${label}_id_blank", "$label id 不能为空")
        }
        ids.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            .filter { (_, count) -> count > 1 }
            .keys
            .forEach { id ->
                issues += issue("${label}_id_duplicate", "$label id 重复：$id")
            }
    }

    private fun issue(code: String, message: String) = CampusMapIssue(code, message)
}
