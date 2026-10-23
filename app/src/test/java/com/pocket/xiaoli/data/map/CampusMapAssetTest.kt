package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusGeoJson
import com.pocket.xiaoli.data.map.model.CampusMapJson
import com.pocket.xiaoli.data.map.model.CampusMapValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class CampusMapAssetTest {

    @Test
    fun ytlgMainAssetParsesAndValidates() {
        val file = asset("campus_map.json") ?: fail("campus_map.json not found").let { return }
        val data = CampusMapJson.parse(file.readText())

        assertEquals("ytlg_main", data.campusId)
        assertTrue("至少应包含真实楼栋名称骨架", data.buildings.size >= 15)
        assertTrue(data.buildings.any { it.name == "三教" })
        assertTrue(data.buildings.any { it.name == "图书馆楼" })
        assertTrue("楼栋名骨架不应引入校验错误", CampusMapValidator.validate(data).isEmpty())
    }

    @Test
    fun emptyGeoJsonAssetsParseAsEmpty() {
        val roads = asset("roads.geojson") ?: fail("roads.geojson not found").let { return }
        val places = asset("places.geojson") ?: fail("places.geojson not found").let { return }

        assertTrue(CampusGeoJson.toRoads(roads.readText()).isEmpty())
        assertTrue(CampusGeoJson.toPlaces(places.readText()).isEmpty())
    }

    private fun asset(name: String): File? {
        val relative = "assets/campus_map/ytlg_main/$name"
        val candidates = listOf(
            File("src/main/$relative"),
            File("app/src/main/$relative"),
            File("../app/src/main/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}