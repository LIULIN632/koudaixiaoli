package com.pocket.xiaoli.data.map.model

import com.pocket.xiaoli.data.map.CampusPoi
import com.pocket.xiaoli.data.map.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CampusMapPoiLinkerTest {

    @Test
    fun linksRoomByBuildingAndRoomName() {
        val poi = CampusPoi(
            id = "p1",
            campusId = "ytlg_main",
            name = "101",
            category = PoiCategory.TEACHING,
            building = "教学楼A",
            floor = "1F",
        )

        val link = CampusMapPoiLinker.link(poi, CampusMapTestFixtures.validData())

        assertEquals("b1", link?.buildingId)
        assertEquals("f1", link?.floorId)
        assertEquals("r1", link?.roomId)
    }

    @Test
    fun linksBuildingWhenPoiHasNoRoom() {
        val poi = CampusPoi(
            id = "p2",
            campusId = "ytlg_main",
            name = "教学楼A",
            category = PoiCategory.TEACHING,
        )

        val link = CampusMapPoiLinker.link(poi, CampusMapTestFixtures.validData())

        assertEquals("b1", link?.buildingId)
        assertNull(link?.roomId)
    }

    @Test
    fun unknownPoiReturnsNull() {
        val poi = CampusPoi(
            id = "p3",
            campusId = "ytlg_main",
            name = "不存在的教室",
            category = PoiCategory.OTHER,
        )

        assertNull(CampusMapPoiLinker.link(poi, CampusMapTestFixtures.validData()))
    }
}