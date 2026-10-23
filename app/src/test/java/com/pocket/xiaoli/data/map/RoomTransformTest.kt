package com.pocket.xiaoli.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 房间拖动 / 拆分 / 合并。 */
class RoomTransformTest {

    private fun cell(
        id: String,
        room: String,
        row: Int = 0,
        col: Int = 0,
        rowSpan: Int = 1,
        colSpan: Int = 1,
    ) = RoomCell(
        id = id,
        campusId = "c1",
        building = "A",
        floor = "1",
        room = room,
        row = row,
        col = col,
        rowSpan = rowSpan,
        colSpan = colSpan,
    )

    @Test
    fun moveCellToFreePosition() {
        val moved = RoomDirectorySearch.moveCell(listOf(cell("a", "101")), "a", 2, 3)
        assertEquals(2, moved[0].row)
        assertEquals(3, moved[0].col)
    }

    @Test
    fun moveCellRejectsOverlap() {
        val original = listOf(cell("a", "101"), cell("b", "102", col = 1))
        assertSame(original, RoomDirectorySearch.moveCell(original, "a", 0, 1))
    }

    @Test
    fun splitCellCreatesUnitCellsWithUniqueRooms() {
        val result = RoomDirectorySearch.splitCell(listOf(cell("a", "201", rowSpan = 2, colSpan = 2)), "a")
        assertEquals(4, result.size)
        assertEquals(4, result.map { it.room }.toSet().size)
        assertTrue(result.all { it.rowSpan == 1 && it.colSpan == 1 })
    }

    @Test
    fun mergeAdjacentCombinesRightNeighbor() {
        val cells = listOf(cell("a", "101"), cell("b", "102", col = 1))
        val merged = RoomDirectorySearch.mergeAdjacent(cells, "a", RoomDirectorySearch.RoomMergeDirection.RIGHT)
        assertEquals(1, merged.size)
        assertEquals(2, merged[0].colSpan)
        assertEquals("101", merged[0].room)
    }

    @Test
    fun mergeWithoutNeighborKeepsOriginal() {
        val original = listOf(cell("a", "101"))
        assertSame(original, RoomDirectorySearch.mergeAdjacent(original, "a", RoomDirectorySearch.RoomMergeDirection.RIGHT))
    }
}
