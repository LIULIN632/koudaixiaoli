package com.pocket.xiaoli.data.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusGeoJsonTest {

    @Test
    fun parsesLineStringAsRoad() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "id": "road_1", "name": "主干道", "kind": "primary" },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [[121.0, 37.0], [121.001, 37.001]]
                  }
                }
              ]
            }
        """.trimIndent()

        val roads = CampusGeoJson.toRoads(json)

        assertEquals(1, roads.size)
        assertEquals("road_1", roads.first().id)
        assertEquals("主干道", roads.first().name)
        assertEquals(2, roads.first().points.size)
        assertEquals(37.0, roads.first().points.first().lat, 0.000001)
    }

    @Test
    fun parsesPointAsPlace() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "id": "place_1", "name": "图书馆", "category": "library" },
                  "geometry": { "type": "Point", "coordinates": [121.0, 37.0] }
                }
              ]
            }
        """.trimIndent()

        val places = CampusGeoJson.toPlaces(json)

        assertEquals(1, places.size)
        assertEquals("图书馆", places.first().name)
        assertEquals("library", places.first().kind)
        assertEquals(37.0, places.first().lat ?: 0.0, 0.000001)
    }

    @Test
    fun invalidJsonReturnsEmpty() {
        assertTrue(CampusGeoJson.parseFeatureCollection("not json").isEmpty())
        assertTrue(CampusGeoJson.toRoads("{}").isEmpty())
    }
}