package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusBuilding
import com.pocket.xiaoli.data.map.model.CampusFloor
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import com.pocket.xiaoli.data.map.model.CampusMapValidator
import com.pocket.xiaoli.data.map.model.CampusRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusMapRoomBridgeTest {

    @Test
    fun overlayEmptyBaseCreatesBuildingFloorRoomsAndNodes() {
        val cell = RoomCell(
            id = "local_1",
            campusId = "ytlg_main",
            building = "1号教学楼",
            floor = "2",
            room = "S201",
            type = RoomCellType.CLASSROOM.key,
        )

        val merged = CampusMapRoomBridge.overlay(
            base = CampusMapData(campusId = "ytlg_main"),
            campusId = "ytlg_main",
            cells = listOf(cell),
        )

        assertEquals(1, merged.buildings.size)
        assertEquals("1号教学楼", merged.buildings.first().name)
        assertEquals(1, merged.floors.size)
        assertEquals("2层", merged.floors.first().name)
        assertEquals("S201", merged.rooms.first().name)
        assertEquals("roomcell:n:1号教学楼|2|s201", merged.rooms.first().nodeId)
        assertEquals(1, merged.nodes.size)
        assertTrue(CampusMapValidator.validate(merged).isEmpty())
    }

    @Test
    fun overlayDoesNotDuplicateExistingRoom() {
        val merged = CampusMapRoomBridge.overlay(
            base = CampusMapTestFixtures.validData(),
            campusId = "ytlg_main",
            cells = listOf(
                RoomCell(
                    id = "local_1",
                    campusId = "ytlg_main",
                    building = "教学楼A",
                    floor = "1F",
                    room = "101",
                )
            ),
        )

        assertEquals(1, merged.rooms.size)
        assertEquals("r1", merged.rooms.first().id)
    }

    @Test
    fun toRoomCellsMapsMapRoomsForFloorPanel() {
        val cells = CampusMapRoomBridge.toRoomCells(
            CampusMapData(
                campusId = "ytlg_main",
                buildings = listOf(CampusBuilding(id = "b1", name = "教学楼A")),
                floors = listOf(CampusFloor(id = "f1", buildingId = "b1", level = 3, name = "3F")),
                rooms = listOf(
                    CampusRoom(id = "r1", buildingId = "b1", floorId = "f1", name = "301"),
                ),
            )
        )

        assertEquals(1, cells.size)
        assertEquals("教学楼A", cells.first().building)
        assertEquals("3F", cells.first().floor)
        assertEquals("301", cells.first().room)
    }

    @Test
    fun overlayRecognizesFloorWrittenAsLevelOrName() {
        val base = CampusMapData(
            campusId = "ytlg_main",
            buildings = listOf(CampusBuilding(id = "b1", name = "1号教学楼")),
            floors = listOf(CampusFloor(id = "f1", buildingId = "b1", level = 2, name = "2层")),
            rooms = listOf(CampusRoom(id = "r1", buildingId = "b1", floorId = "f1", name = "S201")),
        )

        val merged = CampusMapRoomBridge.overlay(
            base = base,
            campusId = "ytlg_main",
            cells = listOf(
                RoomCell(
                    id = "local_1",
                    campusId = "ytlg_main",
                    building = "1号教学楼",
                    floor = "2",
                    room = "S201",
                )
            ),
        )

        assertEquals(1, merged.rooms.size)
        assertEquals("r1", merged.rooms.first().id)
    }

    @Test
    fun roomCellRoomCanReceiveAliasCorrection() {
        val bridged = CampusMapRoomBridge.overlay(
            base = CampusMapData(campusId = "ytlg_main"),
            campusId = "ytlg_main",
            cells = listOf(
                RoomCell(
                    id = "local_1",
                    campusId = "ytlg_main",
                    building = "1号教学楼",
                    floor = "2",
                    room = "S201",
                )
            ),
        )
        val room = bridged.rooms.first()
        val corrected = CampusMapCorrectionApplier.apply(
            data = bridged,
            corrections = listOf(
                CampusMapCorrection(
                    id = "c_alias",
                    campusId = "ytlg_main",
                    kind = "room_alias",
                    targetId = room.id,
                    name = "三教S201",
                )
            ),
        )

        assertEquals("三教S201", corrected.rooms.first().aliases.single())
        assertEquals(
            room.id,
            CourseRoomLocator.locate("三教S201", "ytlg_main", corrected)?.roomId,
        )
    }
}
