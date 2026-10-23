package com.pocket.xiaoli.data.map.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapDataTest {

    @Test
    fun validDataHasNoIssues() {
        val issues = CampusMapValidator.validate(validData())
        assertTrue("expected no issues but got $issues", issues.isEmpty())
    }

    @Test
    fun duplicateRoomIdIsDetected() {
        val data = validData().copy(
            rooms = validData().rooms + validData().rooms.first().copy(name = "102")
        )
        val issues = CampusMapValidator.validate(data)
        assertTrue(issues.any { it.code == "room_id_duplicate" })
    }

    @Test
    fun missingFloorReferenceIsDetected() {
        val data = validData().copy(
            rooms = listOf(validData().rooms.first().copy(floorId = "missing_floor"))
        )
        val issues = CampusMapValidator.validate(data)
        assertTrue(issues.any { it.code == "room_floor_missing" })
    }

    @Test
    fun missingEdgeEndpointIsDetected() {
        val data = validData().copy(
            edges = listOf(CampusEdge(from = "n1", to = "missing_node", meters = 1.0))
        )
        val issues = CampusMapValidator.validate(data)
        assertTrue(issues.any { it.code == "edge_to_missing" })
    }

    @Test
    fun jsonRoundTripKeepsCoreFields() {
        val json = CampusMapJson.toJson(validData())
        val parsed = CampusMapJson.parse(json)

        assertEquals("ytlg_main", parsed.campusId)
        assertEquals("教学楼A", parsed.buildings.firstOrNull()?.name)
        assertEquals("101", parsed.rooms.firstOrNull()?.name)
        assertEquals(1, parsed.edges.size)
    }

    private fun validData(): CampusMapData = CampusMapData(
        campusId = "ytlg_main",
        buildings = listOf(
            CampusBuilding(
                id = "b1",
                name = "教学楼A",
                entranceIds = listOf("e1"),
                floorIds = listOf("f1"),
            )
        ),
        entrances = listOf(
            CampusEntrance(id = "e1", buildingId = "b1", name = "东门")
        ),
        floors = listOf(
            CampusFloor(id = "f1", buildingId = "b1", level = 1, name = "1F", roomIds = listOf("r1"))
        ),
        rooms = listOf(
            CampusRoom(
                id = "r1",
                buildingId = "b1",
                floorId = "f1",
                name = "101",
                type = "classroom",
                nodeId = "n1",
            )
        ),
        nodes = listOf(
            CampusNode(id = "n1", buildingId = "b1", floorId = "f1", kind = "room"),
            CampusNode(id = "n2", buildingId = "b1", floorId = "f1", kind = "corridor"),
        ),
        edges = listOf(
            CampusEdge(from = "n1", to = "n2", meters = 3.0, kind = "corridor")
        ),
    )
}