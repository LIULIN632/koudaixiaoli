package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.RoomCell
import org.junit.Assert.assertEquals
import org.junit.Test

class Campus3DFloorPlanTest {

    @Test
    fun floorLabelNormalizesCommonSpellings() {
        assertEquals("1F", floorLabel("1"))
        assertEquals("1F", floorLabel("1F"))
        assertEquals("1F", floorLabel("1 层"))
        assertEquals("1F", floorLabel("第1层"))
        assertEquals("B1", floorLabel("b1"))
        assertEquals("1F", floorLabel(""))
    }

    @Test
    fun floorOptionsMergeOsmLevelsAndRecordedFloors() {
        val cells = listOf(
            room(floor = "3 层"),
            room(floor = "B1"),
        )

        assertEquals(
            listOf("1F", "2F", "3F", "4F", "5F", "6F", "B1"),
            floorOptionsFor(levels = 6, cells = cells),
        )
    }

    private fun room(floor: String) = RoomCell(
        id = "test-$floor",
        campusId = "ytlg_main",
        building = "综合楼",
        floor = floor,
        room = "101",
    )
}
