package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapJson
import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapDataSourceTest {

    @Test
    fun loadsJsonFromExpectedAssetPath() {
        val json = CampusMapJson.toJson(CampusMapTestFixtures.validData())
        val source = CampusMapDataSource { path ->
            if (path == "campus_map/ytlg_main/campus_map.json") json else null
        }

        val result = source.load("ytlg_main")

        assertTrue(result.isValid)
        assertEquals("教学楼A", result.data.buildings.first().name)
    }

    @Test
    fun missingAssetReturnsReadableIssue() {
        val source = CampusMapDataSource { null }
        val result = source.load("ytlg_main")

        assertEquals("campus_map_asset_missing", result.issues.first().code)
        assertEquals("ytlg_main", result.data.campusId)
    }

    @Test
    fun mergesRoadsAndPlacesGeoJson() {
        val coreJson = CampusMapJson.toJson(CampusMapTestFixtures.validData())
        val roadsJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "id": "road_1", "name": "主干道" },
                  "geometry": { "type": "LineString", "coordinates": [[121.0, 37.0], [121.001, 37.001]] }
                }
              ]
            }
        """.trimIndent()
        val placesJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "id": "place_1", "name": "图书馆" },
                  "geometry": { "type": "Point", "coordinates": [121.0, 37.0] }
                }
              ]
            }
        """.trimIndent()
        val source = CampusMapDataSource { path ->
            when (path) {
                "campus_map/ytlg_main/campus_map.json" -> coreJson
                "campus_map/ytlg_main/roads.geojson" -> roadsJson
                "campus_map/ytlg_main/places.geojson" -> placesJson
                else -> null
            }
        }

        val result = source.load("ytlg_main")

        assertEquals(1, result.data.roads.size)
        assertEquals(1, result.data.places.size)
        assertTrue(result.isValid)
    }
}