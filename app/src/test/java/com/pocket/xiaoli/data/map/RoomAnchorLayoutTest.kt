package com.pocket.xiaoli.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 固定锚点房间的语义化排布。 */
class RoomAnchorLayoutTest {

    private fun cell(
        id: String,
        room: String,
        type: RoomCellType,
    ) = RoomCell(
        id = id,
        campusId = "c1",
        building = "A",
        floor = "1",
        room = room,
        type = type.key,
    )

    @Test
    fun anchorTypesAreRecognized() {
        assertTrue(RoomAnchorLayout.isAnchor(RoomCellType.ENTRANCE))
        assertTrue(RoomAnchorLayout.isAnchor(RoomCellType.STAIR))
        assertTrue(RoomAnchorLayout.isAnchor(RoomCellType.RESTROOM))
        assertTrue(RoomAnchorLayout.isAnchor(RoomCellType.CORRIDOR))
        assertFalse(RoomAnchorLayout.isAnchor(RoomCellType.CLASSROOM))
    }

    @Test
    fun preferredColumnsKeepAnchorsAtEdges() {
        assertEquals(listOf(0, 3), RoomAnchorLayout.preferredColumns(RoomCellType.ENTRANCE, 4))
        assertEquals(listOf(3, 0), RoomAnchorLayout.preferredColumns(RoomCellType.ELEVATOR, 4))
        assertEquals(listOf(0, 1, 2, 3), RoomAnchorLayout.preferredColumns(RoomCellType.CORRIDOR, 4))
    }

    @Test
    fun anchorsTakePreferredColumnsBeforeClassrooms() {
        val grid = RoomDirectorySearch.layoutGrid(
            listOf(
                cell("c", "101", RoomCellType.CLASSROOM),
                cell("e", "入口", RoomCellType.ENTRANCE),
                cell("s", "楼梯", RoomCellType.STAIR),
            ),
            columns = 4,
        )
        assertEquals("入口", grid.cellAt(0, 0)?.room)
        assertEquals("楼梯", grid.cellAt(0, 3)?.room)
        assertEquals("101", grid.cellAt(0, 1)?.room)
    }
}
