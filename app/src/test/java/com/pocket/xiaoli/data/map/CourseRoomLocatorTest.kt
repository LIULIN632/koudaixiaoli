package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseRoomLocatorTest {

    @Test
    fun locatesBuildingAndRoomFromCourseText() {
        val target = CourseRoomLocator.locate(
            courseRoom = "教学楼A 101",
            campusId = "ytlg_main",
            data = CampusMapTestFixtures.validData(),
        )

        assertEquals("b1", target?.buildingId)
        assertEquals("f1", target?.floorId)
        assertEquals("r1", target?.roomId)
        assertTrue(target?.isLinked == true)
    }

    @Test
    fun locatesUniqueRoomByNumberOnly() {
        val target = CourseRoomLocator.locate(
            courseRoom = "101",
            campusId = "ytlg_main",
            data = CampusMapTestFixtures.validData(),
        )

        assertEquals("b1", target?.buildingId)
        assertEquals("r1", target?.roomId)
    }

    @Test
    fun locatesBuildingEvenWhenRoomIsMissing() {
        val target = CourseRoomLocator.locate(
            courseRoom = "教学楼A",
            campusId = "ytlg_main",
            data = CampusMapTestFixtures.validData(),
        )

        assertEquals("b1", target?.buildingId)
        assertFalse(target?.isLinked == true)
        assertTrue(target?.canOpenBuilding == true)
    }

    @Test
    fun returnsNullForUnknownRoom() {
        assertNull(
            CourseRoomLocator.locate(
                courseRoom = "999",
                campusId = "ytlg_main",
                data = CampusMapTestFixtures.validData(),
            )
        )
    }
}