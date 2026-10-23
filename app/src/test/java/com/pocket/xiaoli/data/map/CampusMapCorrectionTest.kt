package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapCorrectionTest {

    @Test
    fun updatesRoomName() {
        val corrected = CampusMapCorrectionApplier.apply(
            data = CampusMapTestFixtures.validData(),
            corrections = listOf(
                CampusMapCorrection(
                    id = "c_room",
                    campusId = "ytlg_main",
                    kind = "room",
                    targetId = "r1",
                    name = "101A",
                )
            ),
        )

        assertEquals("101A", corrected.rooms.first { it.id == "r1" }.name)
    }

    @Test
    fun addsOrUpdatesPlace() {
        val corrected = CampusMapCorrectionApplier.apply(
            data = CampusMapTestFixtures.validData(),
            corrections = listOf(
                CampusMapCorrection(
                    id = "c_place",
                    campusId = "ytlg_main",
                    kind = "place",
                    targetId = "place_library",
                    name = "图书馆",
                    lat = 37.123,
                    lng = 121.456,
                    payload = "library",
                )
            ),
        )

        val place = corrected.places.first { it.id == "place_library" }
        assertEquals("图书馆", place.name)
        assertEquals(37.123, place.lat ?: 0.0, 0.000001)
        assertTrue(place.kind == "library")
    }

    @Test
    fun updatesNodeCoordinates() {
        val corrected = CampusMapCorrectionApplier.apply(
            data = CampusMapTestFixtures.validData(),
            corrections = listOf(
                CampusMapCorrection(
                    id = "c_node",
                    campusId = "ytlg_main",
                    kind = "node",
                    targetId = "n1",
                    lat = 37.111,
                    lng = 121.222,
                )
            ),
        )

        val node = corrected.nodes.first { it.id == "n1" }
        assertEquals(37.111, node.lat ?: 0.0, 0.000001)
        assertEquals(121.222, node.lng ?: 0.0, 0.000001)
    }

    @Test
    fun addsRoomAliasForCourseRoomText() {
        val corrected = CampusMapCorrectionApplier.apply(
            data = CampusMapTestFixtures.validData(),
            corrections = listOf(
                CampusMapCorrection(
                    id = "c_alias",
                    campusId = "ytlg_main",
                    kind = "room_alias",
                    targetId = "r1",
                    name = "三教101",
                )
            ),
        )

        val room = corrected.rooms.first { it.id == "r1" }
        assertTrue(room.aliases.contains("三教101"))
        assertEquals(
            "r1",
            CourseRoomLocator.locate("三教101", "ytlg_main", corrected)?.roomId,
        )
    }
}
