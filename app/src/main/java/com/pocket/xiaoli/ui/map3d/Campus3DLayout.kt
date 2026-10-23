package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.model.Campus3DPoint
import com.pocket.xiaoli.data.map.model.Campus3DScene
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

data class Campus3DRoofDetail(
    val center: Vec2,
    val halfX: Float,
    val halfZ: Float,
    val height: Float,
    val kind: Int,
)

data class Campus3DBuildingShape(
    val id: String,
    val name: String,
    val category: String,
    val levels: Int,
    val levelsEstimated: Boolean,
    val height: Float,
    val footprint: List<Vec2>,
    val holes: List<List<Vec2>>,
    val centroid: Vec2,
    val bounds: Rect2D,
    val roofDetails: List<Campus3DRoofDetail>,
    val sourceName: String = "",
    val note: String = "",
)

data class Campus3DAreaShape(
    val id: String,
    val name: String,
    val kind: String,
    val ring: List<Vec2>,
    val centroid: Vec2,
)

data class Campus3DPitchShape(
    val id: String,
    val name: String,
    val kind: String,
    val sport: String,
    val ring: List<Vec2>,
    val centroid: Vec2,
)

data class Campus3DZoneShape(
    val name: String,
    val kind: String,
    val position: Vec2,
)

data class Campus3DPoiShape(
    val id: String,
    val name: String,
    val category: String,
    val note: String,
    val position: Vec2,
)

data class Campus3DTree(
    val position: Vec2,
    val height: Float,
    val canopy: Float,
    val tint: Float,
)

/**
 * 把经纬度场景转换为局部米制世界坐标的预处理结果；渲染与命中测试都复用它。
 */
class Campus3DLayout private constructor(
    val scene: Campus3DScene,
    val boundary: List<Vec2>,
    val buildings: List<Campus3DBuildingShape>,
    val water: List<Campus3DAreaShape>,
    val green: List<Campus3DAreaShape>,
    val pitches: List<Campus3DPitchShape>,
    val paths: List<List<Vec2>>,
    val zones: List<Campus3DZoneShape>,
    val pois: List<Campus3DPoiShape>,
    val lamps: List<Vec2>,
    val trees: List<Campus3DTree>,
    val radius: Float,
    val bounds: Rect2D,
) {
    val hasContent: Boolean get() = boundary.size >= 3 && buildings.isNotEmpty()

    /**
     * 竖屏无法同时装下「约 800 米见方」的校区与 1:2 的画面比例 —— 全塞进去楼会小到看不清。
     * 因此竖屏只框校园核心区：把注视点往北移，让近侧边界落到画面外，
     * 避免屏幕下方露出一大片空天空；横屏/平板则保持居中全景。
     */
    fun initialCamera(aspect: Float): Campus3DCamera {
        val portrait = aspect < 0.85f
        return Campus3DCamera(
            distance = fitCameraDistance(radius, aspect),
            target = if (portrait) {
                Vec3(x = -0.14f * radius, y = 10f, z = -0.27f * radius)
            } else {
                Vec3(0f, 10f, 0f)
            },
        )
    }

    /** 从课表带房间号进来时，把对应楼栋放到画面中心，而不是停在它看不到的全景。 */
    fun focusCamera(building: Campus3DBuildingShape, aspect: Float): Campus3DCamera =
        focusCamera(
            center = building.centroid,
            span = max(building.bounds.width, building.bounds.height).coerceAtLeast(40f) * 1.8f,
            aspect = aspect,
            targetHeight = building.height * 0.4f,
        )

    /** 搜索选中任意对象后的取景：楼栋按轮廓，地点/场地/水系按锚点与跨度。 */
    fun focusCamera(hit: Campus3DHit, aspect: Float): Campus3DCamera = when (hit) {
        is Campus3DHit.Building -> focusCamera(hit.shape, aspect)
        is Campus3DHit.Poi -> focusCamera(
            center = hit.shape.position,
            span = 70f,
            aspect = aspect,
            targetHeight = 4f,
        )
        is Campus3DHit.Pitch -> {
            val bounds = Rect2D.of(hit.shape.ring)
            focusCamera(
                center = hit.shape.centroid,
                span = max(bounds.width, bounds.height),
                aspect = aspect,
            )
        }
        is Campus3DHit.Water -> {
            val bounds = Rect2D.of(hit.shape.ring)
            focusCamera(
                center = hit.shape.centroid,
                span = max(bounds.width, bounds.height),
                aspect = aspect,
            )
        }
    }

    fun focusCamera(
        center: Vec2,
        span: Float,
        aspect: Float,
        targetHeight: Float = 0f,
    ): Campus3DCamera {
        val extent = span.coerceAtLeast(40f)
        return Campus3DCamera(
            distance = fitCameraDistance(extent, aspect)
                .coerceIn(260f, fitCameraDistance(radius, aspect)),
            target = Vec3(center.x, targetHeight, center.z),
        )
    }

    companion object {
        private const val METERS_PER_DEGREE_LAT = 110_540.0
        private const val METERS_PER_DEGREE_LNG = 111_320.0

        fun prepare(scene: Campus3DScene): Campus3DLayout? {
            val center = resolveCenter(scene) ?: return null
            val boundary = outerRing(scene.boundary.map { it.toLocal(center) })
            if (boundary.size < 3) return null

            val buildings = scene.buildings.mapNotNull { building ->
                val footprint = outerRing(building.outline.map { it.toLocal(center) })
                if (footprint.size < 3) return@mapNotNull null
                val holes = building.holes.mapNotNull { hole ->
                    holeRing(hole.map { it.toLocal(center) }).takeIf { it.size >= 3 }
                }
                val height = when {
                    building.heightMeters > 0.0 -> building.heightMeters.toFloat()
                    else -> building.levels * 3.2f
                }.coerceIn(3.5f, 120f)
                val bounds = Rect2D.of(footprint)
                val anchor = solidAnchor(footprint, holes)
                Campus3DBuildingShape(
                    id = building.id,
                    name = building.name.ifBlank { building.sourceName },
                    category = building.category,
                    levels = building.levels.coerceAtLeast(1),
                    levelsEstimated = building.levelsEstimated,
                    height = height,
                    footprint = footprint,
                    holes = holes,
                    centroid = anchor,
                    bounds = bounds,
                    roofDetails = buildRoofDetails(footprint, holes, anchor, bounds),
                    sourceName = building.sourceName,
                    note = building.note,
                )
            }

            val water = scene.water.mapNotNull { item ->
                val ring = outerRing(item.outline.map { it.toLocal(center) })
                if (ring.size < 3) null else Campus3DAreaShape(
                    id = item.id,
                    name = item.name,
                    kind = "water",
                    ring = ring,
                    centroid = polygonCentroid(ring),
                )
            }

            val green = scene.green.mapNotNull { item ->
                val ring = outerRing(item.outline.map { it.toLocal(center) })
                if (ring.size < 3) null else Campus3DAreaShape(
                    id = item.id,
                    name = "",
                    kind = item.kind,
                    ring = ring,
                    centroid = polygonCentroid(ring),
                )
            }

            val pitches = scene.pitches.mapNotNull { item ->
                val ring = outerRing(item.outline.map { it.toLocal(center) })
                if (ring.size < 3) null else Campus3DPitchShape(
                    id = item.id,
                    name = item.name,
                    kind = item.kind,
                    sport = item.sport,
                    ring = ring,
                    centroid = polygonCentroid(ring),
                )
            }

            val paths = scene.paths.mapNotNull { item ->
                item.points.map { it.toLocal(center) }
                    .fold(mutableListOf<Vec2>()) { acc, point ->
                        if (acc.isEmpty() || acc.last().distanceTo(point) > 0.2f) acc += point
                        acc
                    }
                    .takeIf { it.size >= 2 }
            }

            val zones = scene.zones.map {
                Campus3DZoneShape(
                    name = it.name,
                    kind = it.kind,
                    position = it.point.toLocal(center),
                )
            }

            val pois = scene.pois.map {
                Campus3DPoiShape(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    note = it.note,
                    position = it.point.toLocal(center),
                )
            }

            val lamps = buildLamps(paths, boundary, buildings, water)

            val allPoints = buildList {
                addAll(boundary)
                buildings.forEach { addAll(it.footprint); it.holes.forEach(::addAll) }
                water.forEach { addAll(it.ring) }
                green.forEach { addAll(it.ring) }
                pitches.forEach { addAll(it.ring) }
                paths.forEach { addAll(it) }
                zones.forEach { add(it.position) }
                pois.forEach { add(it.position) }
                lamps.forEach { add(it) }
            }
            val radius = allPoints.maxOfOrNull { it.length() }?.coerceAtLeast(80f) ?: 80f

            val buildingRings = buildings.map { it.footprint }
            val trees = green.flatMap { area ->
                val areaSize = abs(signedArea(area.ring))
                val count = (areaSize / 950f).toInt().coerceIn(1, 6)
                val seed = area.id.hashCode()
                scatterInside(
                    ring = area.ring,
                    count = count,
                    seed = seed,
                    minSpacing = 11f,
                    avoid = { point ->
                        buildingRings.any { polygonContains(it, point) }
                    },
                ).mapIndexed { index, point ->
                    val noise = ((seed + index * 37) % 100) / 100f
                    Campus3DTree(
                        position = point,
                        height = 5.6f + noise * 3.4f,
                        canopy = 2.7f + noise * 1.4f,
                        tint = noise,
                    )
                }
            }

            return Campus3DLayout(
                scene = scene,
                boundary = boundary,
                buildings = buildings,
                water = water,
                green = green,
                pitches = pitches,
                paths = paths,
                zones = zones,
                pois = pois,
                lamps = lamps,
                trees = trees,
                radius = radius,
                bounds = Rect2D.of(allPoints),
            )
        }

        private fun buildRoofDetails(
            footprint: List<Vec2>,
            holes: List<List<Vec2>>,
            anchor: Vec2,
            bounds: Rect2D,
        ): List<Campus3DRoofDetail> {
            val minDim = min(bounds.width, bounds.height)
            if (minDim < 9f) return emptyList()

            fun solid(point: Vec2): Boolean =
                polygonContains(footprint, point) && holes.none { polygonContains(it, point) }

            fun fits(center: Vec2, halfX: Float, halfZ: Float): Boolean =
                corners(center, halfX, halfZ).all { solid(it) }

            val result = ArrayList<Campus3DRoofDetail>(3)
            val bulkheadHalfX = (minDim * 0.075f).coerceIn(0.9f, 3.4f)
            val bulkheadHalfZ = (minDim * 0.05f).coerceIn(0.8f, 2.6f)
            val bulkhead = anchor + Vec2(bounds.width * 0.09f, -bounds.height * 0.07f)
            if (fits(bulkhead, bulkheadHalfX, bulkheadHalfZ)) {
                result += Campus3DRoofDetail(bulkhead, bulkheadHalfX, bulkheadHalfZ, 2.4f, 0)
            }

            val clearance = (minDim * 0.24f).coerceAtLeast(4.5f)
            val vents = scatterInside(
                ring = footprint,
                count = 2,
                seed = footprint.hashCode() * 31 + 17,
                minSpacing = clearance,
                avoid = { point ->
                    holes.any { polygonContains(it, point) } ||
                        result.any { it.center.distanceTo(point) < clearance }
                },
            )
            vents.forEachIndexed { index, point ->
                val halfX = (minDim * 0.028f).coerceIn(0.45f, 1.1f)
                val halfZ = (minDim * 0.022f).coerceIn(0.4f, 0.95f)
                if (fits(point, halfX, halfZ)) {
                    result += Campus3DRoofDetail(point, halfX, halfZ, 0.9f + index * 0.15f, 1)
                }
            }
            return result
        }

        private fun corners(center: Vec2, halfX: Float, halfZ: Float): List<Vec2> = listOf(
            Vec2(center.x - halfX, center.z - halfZ),
            Vec2(center.x + halfX, center.z - halfZ),
            Vec2(center.x + halfX, center.z + halfZ),
            Vec2(center.x - halfX, center.z + halfZ),
        )

        private fun buildLamps(
            paths: List<List<Vec2>>,
            boundary: List<Vec2>,
            buildings: List<Campus3DBuildingShape>,
            water: List<Campus3DAreaShape>,
        ): List<Vec2> {
            val result = ArrayList<Vec2>(26)
            val candidates = paths
                .map { path -> path to path.first().distanceTo(path.last()) }
                .sortedByDescending { it.second }
                .take(8)
            for ((pathIndex, candidate) in candidates.withIndex()) {
                val path = candidate.first
                var travelled = 0f
                var nextAt = 30f + pathIndex * 8f
                for (pointIndex in 1 until path.size) {
                    val a = path[pointIndex - 1]
                    val b = path[pointIndex]
                    val segment = b - a
                    val length = segment.length()
                    if (length < 0.5f) {
                        travelled += length
                        continue
                    }
                    val direction = segment * (1f / length)
                    val normal = Vec2(direction.z, -direction.x)
                    while (nextAt <= travelled + length) {
                        val fraction = (nextAt - travelled) / length
                        val at = a + segment * fraction + normal * 2.6f
                        if (result.size >= 26) return result
                        val clear = polygonContains(boundary, at) &&
                            buildings.none { polygonContains(it.footprint, at) } &&
                            water.none { polygonContains(it.ring, at) } &&
                            result.none { it.distanceTo(at) < 32f }
                        if (clear) result += at
                        nextAt += 64f
                    }
                    travelled += length
                }
            }
            return result
        }

        private fun resolveCenter(scene: Campus3DScene): Campus3DPoint? {
            scene.center?.let { if (it.lat != 0.0 || it.lng != 0.0) return it }
            scene.bounds?.let {
                return Campus3DPoint(
                    lat = (it.minLat + it.maxLat) * 0.5,
                    lng = (it.minLng + it.maxLng) * 0.5,
                )
            }
            if (scene.boundary.isEmpty()) return null
            return Campus3DPoint(
                lat = scene.boundary.sumOf { it.lat } / scene.boundary.size,
                lng = scene.boundary.sumOf { it.lng } / scene.boundary.size,
            )
        }

        /**
         * 楼名/阴影的锚点必须是**实心屋面**上的点。有内院的大楼（如综合楼）
         * 其几何重心会落进天井，标签杆看起来像扎在洞里。这里在包围盒内找
         * 离重心最近的实心点兜底，网格精度 12×12 对厘米级标注足够。
         */
        private fun solidAnchor(ring: List<Vec2>, holes: List<List<Vec2>>): Vec2 {
            val centroid = polygonCentroid(ring)
            if (holes.none { polygonContains(it, centroid) }) return centroid
            val bounds = Rect2D.of(ring)
            var best = centroid
            var bestDistance = Float.MAX_VALUE
            val steps = 12
            for (row in 0..steps) {
                for (column in 0..steps) {
                    val point = Vec2(
                        x = bounds.minX + bounds.width * column / steps,
                        z = bounds.minZ + bounds.height * row / steps,
                    )
                    if (!polygonContains(ring, point)) continue
                    if (holes.any { polygonContains(it, point) }) continue
                    val distance = point.distanceTo(centroid)
                    if (distance < bestDistance) {
                        best = point
                        bestDistance = distance
                    }
                }
            }
            return best
        }

        private fun Campus3DPoint.toLocal(center: Campus3DPoint): Vec2 {
            val metersPerLng = METERS_PER_DEGREE_LNG *
                cos(Math.toRadians(center.lat)).toFloat()
            return Vec2(
                x = ((lng - center.lng) * metersPerLng).toFloat(),
                z = (-(lat - center.lat) * METERS_PER_DEGREE_LAT).toFloat(),
            )
        }
    }
}
