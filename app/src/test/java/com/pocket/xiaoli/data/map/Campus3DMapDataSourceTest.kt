package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.Campus3DMapJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Campus3DMapDataSourceTest {

    @Test
    fun parsesFullScene() {
        val scene = Campus3DMapJson.parse(SCENE_JSON)

        assertEquals(1, scene.schemaVersion)
        assertEquals("ytlg_main", scene.campusId)
        assertEquals("烟台理工学院", scene.name)
        assertEquals("地图数据 © OpenStreetMap 贡献者，ODbL 1.0", scene.attribution)
        assertEquals(37.45, scene.center?.lat ?: 0.0, 1e-9)
        assertEquals(121.48, scene.bounds?.maxLng ?: 0.0, 1e-9)
        assertEquals(3, scene.boundary.size)

        val building = scene.buildings.single()
        assertEquals("way/1", building.id)
        assertEquals("第一教学楼", building.sourceName)
        assertEquals(6, building.levels)
        assertFalse(building.levelsEstimated)
        assertEquals(19.8, building.heightMeters, 1e-9)
        assertEquals(4, building.outline.size)
        assertEquals(1, building.holes.size)
        assertEquals(4, building.holes.first().size)

        assertEquals("琴心湖", scene.water.single().name)
        assertEquals(37.4538, scene.water.single().label?.lat ?: 0.0, 1e-9)
        assertEquals("grass", scene.green.single().kind)
        assertEquals("田径场", scene.pitches.single().name)
        assertEquals(2, scene.paths.single().points.size)
        assertEquals("学生公寓", scene.zones.single().name)
        assertEquals("2号教学楼", scene.pois.single().name)
        assertTrue(scene.hasGeometry)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val scene = Campus3DMapJson.parse(
            """{"campusId":"ytlg_main","buildings":[{"id":"way/9"}]}"""
        )

        assertEquals(1, scene.schemaVersion)
        assertEquals("ytlg_main", scene.campusId)
        assertNull(scene.center)
        assertNull(scene.bounds)
        assertTrue(scene.boundary.isEmpty())
        assertEquals(1, scene.buildings.single().levels)
        assertEquals("", scene.buildings.single().name)
        assertTrue(scene.buildings.single().holes.isEmpty())
        assertFalse(scene.hasGeometry)
    }

    @Test
    fun malformedJsonFallsBackToEmptyScene() {
        val scene = Campus3DMapJson.parse("""{"campusId": "ytlg_main", "buildings": [""")

        assertEquals("", scene.campusId)
        assertTrue(scene.buildings.isEmpty())
        assertFalse(scene.hasGeometry)
    }

    @Test
    fun loadsJsonFromExpectedAssetPath() {
        val requested = mutableListOf<String>()
        val source = Campus3DMapDataSource { path ->
            requested += path
            SCENE_JSON
        }

        val result = source.load("ytlg_main")

        assertEquals(
            listOf(
                "campus_map/ytlg_main/campus_3d.json",
                "campus_map/ytlg_main/campus_3d_traced.json",
            ),
            requested,
        )
        assertTrue(result.isValid)
        assertEquals("1号教学楼", result.scene.buildings.single().name)
    }

    @Test
    fun mergesTracedBuildingsAndDropsMatchingPoi() {
        val source = Campus3DMapDataSource { path ->
            if (path.endsWith("campus_3d_traced.json")) TRACED_FOR_MERGE else MAIN_FOR_MERGE
        }

        val result = source.load("ytlg_main")

        assertTrue(result.isValid)
        assertEquals(2, result.scene.buildings.size)
        assertTrue(result.scene.buildings.any { it.id == "traced/bing/jx3" })
        assertEquals(1, result.scene.pois.size)
        assertEquals("2号教学楼", result.scene.pois.single().name)
        assertTrue(result.scene.attribution.contains("OpenStreetMap"))
        assertTrue(result.scene.attribution.contains("Bing"))
    }

    @Test
    fun tracedAssetWithWrongCampusIdIsReported() {
        val traced = TRACED_FOR_MERGE.replaceFirst("\"ytlg_main\"", "\"other\"")
        val source = Campus3DMapDataSource { path ->
            if (path.endsWith("campus_3d_traced.json")) traced else MAIN_FOR_MERGE
        }

        val result = source.load("ytlg_main")

        assertEquals("campus_3d_traced_invalid", result.issues.single().code)
        assertEquals(1, result.scene.buildings.size)
        assertEquals(2, result.scene.pois.size)
    }

    @Test
    fun missingAssetReturnsReadableIssue() {
        val result = Campus3DMapDataSource { null }.load("ytlg_main")

        assertEquals("campus_3d_asset_missing", result.issues.single().code)
        assertEquals("ytlg_main", result.scene.campusId)
        assertFalse(result.isValid)
    }

    @Test
    fun invalidGeometryIsReported() {
        val json = """
            {
              "campusId": "ytlg_main",
              "boundary": [{"lat": 37.45, "lng": 121.47}],
              "buildings": [{
                "id": "way/1",
                "outline": [{"lat": 37.45, "lng": 121.47}, {"lat": 37.46, "lng": 121.47}],
                "levels": 0,
                "heightMeters": -1
              }]
            }
        """.trimIndent()

        val result = Campus3DMapDataSource { json }.load("ytlg_main")
        val codes = result.issues.map { it.code }

        assertTrue(codes.contains("campus_3d_boundary_invalid"))
        assertTrue(codes.contains("building_outline_invalid"))
        assertTrue(codes.contains("building_levels_invalid"))
        assertTrue(codes.contains("building_height_invalid"))
        assertFalse(result.isValid)
    }

    private companion object {
        val MAIN_FOR_MERGE = """
            {
              "campusId": "ytlg_main",
              "attribution": "地图数据 © OpenStreetMap 贡献者，ODbL 1.0",
              "center": {"lat": 37.45, "lng": 121.47},
              "boundary": [
                {"lat": 37.44, "lng": 121.46},
                {"lat": 37.47, "lng": 121.46},
                {"lat": 37.47, "lng": 121.49}
              ],
              "buildings": [{
                "id": "way/1",
                "name": "1号教学楼",
                "category": "teaching",
                "levels": 6,
                "heightMeters": 19.8,
                "outline": [
                  {"lat": 37.451, "lng": 121.471},
                  {"lat": 37.452, "lng": 121.471},
                  {"lat": 37.452, "lng": 121.472}
                ]
              }],
              "pois": [
                {"id": "amap/teaching_2", "name": "2号教学楼", "category": "teaching",
                 "lat": 37.4557, "lng": 121.4712, "note": "位置标注"},
                {"id": "amap/teaching_3", "name": "3号教学楼", "category": "teaching",
                 "lat": 37.4554, "lng": 121.4734, "note": "位置标注"}
              ]
            }
        """.trimIndent()

        val TRACED_FOR_MERGE = """
            {
              "campusId": "ytlg_main",
              "attribution": "3号教学楼轮廓为 Bing 正射影像人工描点（非 OSM）",
              "buildings": [{
                "id": "traced/bing/jx3",
                "name": "3号教学楼",
                "sourceName": "三教",
                "category": "teaching",
                "levels": 6,
                "heightMeters": 19.8,
                "outline": [
                  {"lat": 37.4550, "lng": 121.4730},
                  {"lat": 37.4560, "lng": 121.4730},
                  {"lat": 37.4560, "lng": 121.4740}
                ],
                "note": "非 OSM 数据"
              }]
            }
        """.trimIndent()

        val SCENE_JSON = """
            {
              "schemaVersion": 1,
              "campusId": "ytlg_main",
              "name": "烟台理工学院",
              "generatedAt": "2026-10-20",
              "attribution": "地图数据 © OpenStreetMap 贡献者，ODbL 1.0",
              "sourceUrl": "https://www.openstreetmap.org/copyright",
              "center": {"lat": 37.45, "lng": 121.47},
              "bounds": {"minLat": 37.44, "maxLat": 37.46, "minLng": 121.46, "maxLng": 121.48},
              "boundary": [
                {"lat": 37.44, "lng": 121.46},
                {"lat": 37.46, "lng": 121.46},
                {"lat": 37.46, "lng": 121.48}
              ],
              "buildings": [{
                "id": "way/1",
                "name": "1号教学楼",
                "sourceName": "第一教学楼",
                "category": "teaching",
                "levels": 6,
                "levelsEstimated": false,
                "heightMeters": 19.8,
                "outline": [
                  {"lat": 37.451, "lng": 121.471},
                  {"lat": 37.452, "lng": 121.471},
                  {"lat": 37.452, "lng": 121.472},
                  {"lat": 37.451, "lng": 121.472}
                ],
                "holes": [[
                  {"lat": 37.4512, "lng": 121.4712},
                  {"lat": 37.4514, "lng": 121.4712},
                  {"lat": 37.4514, "lng": 121.4714},
                  {"lat": 37.4512, "lng": 121.4714}
                ]]
              }],
              "water": [{
                "id": "way/2",
                "name": "琴心湖",
                "outline": [
                  {"lat": 37.453, "lng": 121.472},
                  {"lat": 37.454, "lng": 121.472},
                  {"lat": 37.454, "lng": 121.473}
                ],
                "label": {"lat": 37.4538, "lng": 121.4725}
              }],
              "green": [{
                "id": "way/3",
                "kind": "grass",
                "outline": [
                  {"lat": 37.452, "lng": 121.472},
                  {"lat": 37.453, "lng": 121.472},
                  {"lat": 37.453, "lng": 121.473}
                ]
              }],
              "pitches": [{
                "id": "way/4",
                "name": "田径场",
                "kind": "track",
                "sport": "running",
                "outline": [
                  {"lat": 37.452, "lng": 121.469},
                  {"lat": 37.453, "lng": 121.469},
                  {"lat": 37.453, "lng": 121.470}
                ],
                "label": {"lat": 37.4525, "lng": 121.4695}
              }],
              "paths": [{
                "id": "way/5",
                "points": [{"lat": 37.451, "lng": 121.470}, {"lat": 37.452, "lng": 121.471}]
              }],
              "zones": [{"name": "学生公寓", "kind": "dorm", "lat": 37.451, "lng": 121.470}],
              "pois": [{
                "id": "amap/teaching_2",
                "name": "2号教学楼",
                "category": "teaching",
                "lat": 37.4557,
                "lng": 121.4712,
                "note": "位置来自公开地图标注；暂无公开楼栋轮廓"
              }]
            }
        """.trimIndent()
    }
}
