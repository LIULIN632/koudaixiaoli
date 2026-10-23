package com.pocket.xiaoli.ui.map3d

import kotlin.math.sqrt

sealed interface Campus3DHit {
    val id: String
    val displayName: String

    data class Building(val shape: Campus3DBuildingShape) : Campus3DHit {
        override val id: String get() = shape.id
        override val displayName: String get() = shape.name
    }

    data class Poi(val shape: Campus3DPoiShape) : Campus3DHit {
        override val id: String get() = shape.id
        override val displayName: String get() = shape.name
    }

    data class Pitch(val shape: Campus3DPitchShape) : Campus3DHit {
        override val id: String get() = shape.id
        override val displayName: String get() = shape.name
    }

    data class Water(val shape: Campus3DAreaShape) : Campus3DHit {
        override val id: String get() = shape.id
        override val displayName: String get() = shape.name
    }
}

/**
 * 屏幕点命中：POI 优先，其次由近到远测试楼栋屋顶/立面，最后水体和场地。
 */
fun hitTestCampus3D(
    layout: Campus3DLayout,
    camera: Campus3DCamera,
    width: Float,
    height: Float,
    x: Float,
    y: Float,
    poiRadiusPx: Float = 30f,
): Campus3DHit? {
    if (width <= 0f || height <= 0f) return null
    val projection = Campus3DProjection(camera, width, height)

    val poiHit = layout.pois.asSequence()
        .mapNotNull { poi ->
            val screen = projection.projectFlat(poi.position, altitude = 4f) ?: return@mapNotNull null
            val distance = sqrt((screen.x - x) * (screen.x - x) + (screen.y - y) * (screen.y - y))
            if (distance <= poiRadiusPx) poi to distance else null
        }
        .minByOrNull { it.second }
    poiHit?.let { return Campus3DHit.Poi(it.first) }

    val buildingHit = layout.buildings.asSequence()
        .mapNotNull { building ->
            val depth = projection.depthOf(
                Vec3(building.centroid.x, building.height * 0.5f, building.centroid.z)
            )
            building to depth
        }
        .sortedBy { it.second }
        .firstOrNull { (building, _) -> buildingContainsScreenPoint(building, projection, x, y) }
        ?.first
    buildingHit?.let { return Campus3DHit.Building(it) }

    val pitchHit = layout.pitches.asSequence()
        .mapNotNull { pitch ->
            val screen = projection.projectFlat(pitch.centroid) ?: return@mapNotNull null
            if (projectedRingContains(pitch.ring, 0f, projection, x, y)) pitch to screen.depth else null
        }
        .minByOrNull { it.second }
        ?.first
    pitchHit?.let { return Campus3DHit.Pitch(it) }

    val waterHit = layout.water.asSequence()
        .mapNotNull { water ->
            val screen = projection.projectFlat(water.centroid) ?: return@mapNotNull null
            if (projectedRingContains(water.ring, 0f, projection, x, y)) water to screen.depth else null
        }
        .minByOrNull { it.second }
        ?.first
    waterHit?.let { return Campus3DHit.Water(it) }

    return null
}

private fun buildingContainsScreenPoint(
    building: Campus3DBuildingShape,
    projection: Campus3DProjection,
    x: Float,
    y: Float,
): Boolean {
    if (projectedRingContains(building.footprint, building.height, projection, x, y)) return true
    for (index in building.footprint.indices) {
        val a = building.footprint[index]
        val b = building.footprint[(index + 1) % building.footprint.size]
        val bottomA = projection.projectFlat(a) ?: continue
        val bottomB = projection.projectFlat(b) ?: continue
        val topA = projection.projectFlat(a, building.height) ?: continue
        val topB = projection.projectFlat(b, building.height) ?: continue
        if (pointInConvexQuad(listOf(bottomA, bottomB, topB, topA), x, y)) return true
    }
    return false
}

private fun projectedRingContains(
    ring: List<Vec2>,
    altitude: Float,
    projection: Campus3DProjection,
    x: Float,
    y: Float,
): Boolean {
    val screenRing = ring.map { projection.projectFlat(it, altitude) ?: return false }
    return screenRing.size >= 3 && polygonContains(
        screenRing.map { Vec2(it.x, it.y) },
        Vec2(x, y),
    )
}
