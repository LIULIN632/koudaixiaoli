package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.Campus3DTestAssets
import com.pocket.xiaoli.data.map.model.Campus3DPoint
import com.pocket.xiaoli.data.map.model.Campus3DScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

class Campus3DGeometryTest {

    @Test
    fun outerRingNormalizesWinding() {
        val clockwise = listOf(
            Vec2(0f, 0f),
            Vec2(0f, 10f),
            Vec2(10f, 10f),
            Vec2(10f, 0f),
        )

        val ring = outerRing(clockwise)

        assertTrue(signedArea(ring) > 0f)
        assertEquals(4, ring.size)
        assertTrue(polygonContains(ring, Vec2(5f, 5f)))
        assertTrue(!polygonContains(ring, Vec2(15f, 5f)))
    }

    @Test
    fun centroidFallsInsideRegularPolygon() {
        val ring = outerRing(listOf(Vec2(0f, 0f), Vec2(20f, 0f), Vec2(20f, 20f), Vec2(0f, 20f)))

        val centroid = polygonCentroid(ring)

        assertEquals(10f, centroid.x, 0.01f)
        assertEquals(10f, centroid.z, 0.01f)
    }

    @Test
    fun scatterSkipsAvoidedArea() {
        val ring = outerRing(listOf(Vec2(0f, 0f), Vec2(40f, 0f), Vec2(40f, 40f), Vec2(0f, 40f)))

        val points = scatterInside(ring, count = 8, seed = 7, avoid = { it.x < 20f })

        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.x >= 20f && polygonContains(ring, it) })
    }

    @Test
    fun cameraProjectsTargetToViewportCenter() {
        val camera = Campus3DCamera(distance = 1000f, target = Vec3(0f, 10f, 0f))
        val projection = Campus3DProjection(camera, width = 400f, height = 800f)

        val screen = projection.project(camera.target)

        assertNotNull(screen)
        assertEquals(200f, screen!!.x, 0.5f)
        assertEquals(400f, screen.y, 0.5f)
        assertEquals(1000f, screen.depth, 0.5f)
        assertNull(projection.project(projection.eye))
        assertTrue(projection.pixelsPerMeter(500f) > projection.pixelsPerMeter(1000f))
    }

    @Test
    fun fitDistanceGrowsWithRadiusAndPortrait() {
        val portrait = fitCameraDistance(radius = 500f, aspect = 0.46f)
        val landscape = fitCameraDistance(radius = 500f, aspect = 1.8f)
        val larger = fitCameraDistance(radius = 900f, aspect = 0.46f)

        assertTrue(portrait > landscape)
        assertTrue(larger > portrait)
    }

    @Test
    fun preparesRealSceneIntoMeterCoordinates() {
        val layout = Campus3DLayout.prepare(loadAssetScene()) ?: fail("layout is null").let { return }

        assertEquals(19, layout.buildings.size)
        assertEquals(1, layout.water.size)
        assertEquals(5, layout.pitches.size)
        assertEquals(37, layout.paths.size)
        assertTrue(layout.trees.isNotEmpty())
        assertTrue(layout.radius > 450f)
        assertTrue(layout.bounds.width > 500f)
        assertTrue(layout.bounds.height > 500f)

        val teaching = layout.buildings.first { it.name == "1号教学楼" }
        assertTrue(teaching.footprint.size >= 3)
        assertTrue(teaching.height > 10f)
        assertTrue(abs(teaching.centroid.x) < layout.radius)
        assertTrue(abs(teaching.centroid.z) < layout.radius)

        val traced = layout.buildings.first { it.id == "traced/bing/jx3" }
        assertEquals("3号教学楼", traced.name)
        assertEquals("三教", traced.sourceName)
        assertEquals(6, traced.levels)
        assertEquals(19.8f, traced.height, 0.01f)
        assertEquals(33, traced.footprint.size)
        assertTrue(traced.note.isNotBlank())
        assertTrue(abs(traced.centroid.x) < layout.radius)
        assertTrue(abs(traced.centroid.z) < layout.radius)
        assertEquals("三教轮廓面积应约 4916 m²", 4916f, abs(signedArea(traced.footprint)), 100f)

        val complex = layout.buildings.first { it.name == "综合楼" }
        assertTrue("楼名锚点应落在实心屋面上", polygonContains(complex.footprint, complex.centroid))
        assertTrue("楼名锚点不应掉进天井", complex.holes.none { polygonContains(it, complex.centroid) })

        layout.trees.forEach { tree ->
            assertTrue(
                "树不应落在楼里：${tree.position}",
                layout.buildings.none { polygonContains(it.footprint, tree.position) },
            )
        }

        assertTrue("路灯应沿主路生成，实际 ${layout.lamps.size} 盏", layout.lamps.size >= 10)
        layout.lamps.forEach { lamp ->
            assertTrue("路灯应在校区内：$lamp", polygonContains(layout.boundary, lamp))
            assertTrue(
                "路灯不应落在楼里：$lamp",
                layout.buildings.none { polygonContains(it.footprint, lamp) },
            )
            assertTrue(
                "路灯不应落在水里：$lamp",
                layout.water.none { polygonContains(it.ring, lamp) },
            )
        }

        assertTrue(
            "至少一栋楼要有屋顶设备",
            layout.buildings.any { it.roofDetails.isNotEmpty() },
        )
        layout.buildings.forEach { building ->
            building.roofDetails.forEach { detail ->
                assertTrue(
                    "屋顶设备应在实心屋面上：${building.name} ${detail.center}",
                    polygonContains(building.footprint, detail.center) &&
                        building.holes.none { polygonContains(it, detail.center) },
                )
            }
        }
    }

    @Test
    fun focusCameraCentersBuildingAndMovesCloser() {
        val layout = Campus3DLayout.prepare(syntheticScene()) ?: fail("layout is null").let { return }
        val building = layout.buildings.single()

        val overview = layout.initialCamera(aspect = 0.5f)
        val focus = layout.focusCamera(building, aspect = 0.5f)

        assertEquals(building.centroid.x, focus.target.x, 0.01f)
        assertEquals(building.centroid.z, focus.target.z, 0.01f)
        assertTrue(focus.distance < overview.distance)
    }

    @Test
    fun cameraInterpolationTakesShortYawPath() {
        val start = Campus3DCamera(
            yawDegrees = 170f,
            distance = 300f,
            target = Vec3(0f, 10f, 0f),
        )
        val end = Campus3DCamera(
            yawDegrees = -170f,
            distance = 500f,
            target = Vec3(40f, 20f, -40f),
        )

        val mid = interpolateCamera(start, end, 0.5f)

        assertEquals(180f, abs(mid.yawDegrees), 0.01f)
        assertEquals(400f, mid.distance, 0.01f)
        assertEquals(20f, mid.target.x, 0.01f)
        assertEquals(15f, mid.target.y, 0.01f)
        assertEquals(-20f, mid.target.z, 0.01f)
        assertEquals(170f, interpolateCamera(start, end, 0f).yawDegrees, 0.01f)
        assertEquals(-170f, interpolateCamera(start, end, 1f).yawDegrees, 0.01f)
        assertEquals(-170f, interpolateCamera(start, end, 2f).yawDegrees, 0.01f)
    }

    @Test
    fun hitTestFindsBuildingRoofAndEmptySpace() {
        val scene = syntheticScene()
        val layout = Campus3DLayout.prepare(scene) ?: fail("layout is null").let { return }
        val camera = layout.initialCamera(aspect = 0.5f)
        val projection = Campus3DProjection(camera, width = 400f, height = 800f)
        val building = layout.buildings.single()
        val roofCenter = projection.projectFlat(building.centroid, building.height)
            ?: fail("roof center off screen").let { return }

        val hit = hitTestCampus3D(layout, camera, 400f, 800f, roofCenter.x, roofCenter.y)
        assertTrue("应命中楼栋，实际为 $hit", hit is Campus3DHit.Building)
        assertEquals(building.id, hit?.id)

        assertNull(hitTestCampus3D(layout, camera, 400f, 800f, 4f, 4f))
    }

    @Test
    fun poiBeatsBuildingAtSameSpot() {
        val scene = syntheticScene(
            poi = com.pocket.xiaoli.data.map.model.Campus3DPoi(
                id = "amap/test",
                name = "测试标注",
                category = "teaching",
                lat = 37.45,
                lng = 121.47,
                note = "位置来自公开地图标注",
            )
        )
        val layout = Campus3DLayout.prepare(scene) ?: fail("layout is null").let { return }
        val camera = layout.initialCamera(aspect = 0.5f)
        val projection = Campus3DProjection(camera, width = 400f, height = 800f)
        val poiScreen = projection.projectFlat(layout.pois.single().position, altitude = 4f)
            ?: fail("poi off screen").let { return }

        val hit = hitTestCampus3D(layout, camera, 400f, 800f, poiScreen.x, poiScreen.y)

        assertTrue(hit is Campus3DHit.Poi)
        assertEquals("amap/test", hit?.id)
    }

    private fun syntheticScene(
        poi: com.pocket.xiaoli.data.map.model.Campus3DPoi? = null,
    ): Campus3DScene = Campus3DScene(
        campusId = "test",
        center = Campus3DPoint(37.45, 121.47),
        boundary = listOf(
            Campus3DPoint(37.448, 121.468),
            Campus3DPoint(37.452, 121.468),
            Campus3DPoint(37.452, 121.472),
            Campus3DPoint(37.448, 121.472),
        ),
        buildings = listOf(
            com.pocket.xiaoli.data.map.model.Campus3DBuilding(
                id = "way/1",
                name = "测试楼",
                category = "teaching",
                levels = 4,
                heightMeters = 13.0,
                outline = listOf(
                    Campus3DPoint(37.4495, 121.4695),
                    Campus3DPoint(37.4505, 121.4695),
                    Campus3DPoint(37.4505, 121.4705),
                    Campus3DPoint(37.4495, 121.4705),
                ),
            )
        ),
        pois = listOfNotNull(poi),
    )

    private fun loadAssetScene(): Campus3DScene = Campus3DTestAssets.loadScene()
}
