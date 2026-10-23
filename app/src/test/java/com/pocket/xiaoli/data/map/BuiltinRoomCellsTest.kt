package com.pocket.xiaoli.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内置教室文本格式解析测试。使用者投稿数据后，靠这些用例保证不被格式问题坑到。 */
class BuiltinRoomCellsTest {

    @Test
    fun parsesMinimalLine() {
        val cells = BuiltinRoomCells.parse("1号教学楼|1|S101")
        assertEquals(1, cells.size)
        val c = cells[0]
        assertEquals("1号教学楼", c.building)
        assertEquals("1", c.floor)
        assertEquals("S101", c.room)
        assertEquals(RoomCellType.CLASSROOM, c.roomType)
        assertEquals(1, c.rowSpan)
        assertEquals(1, c.colSpan)
        assertEquals("未给坐标应为 -1（自动排布）", -1, c.row)
        assertEquals(-1, c.col)
    }

    @Test
    fun parsesFullLine() {
        val c = BuiltinRoomCells.parse("三教|2|B201|多媒体教室|lab|60|0|1|2|3")[0]
        assertEquals("多媒体教室", c.name)
        assertEquals(RoomCellType.LAB, c.roomType)
        assertEquals(60, c.capacity)
        assertEquals(0, c.row)
        assertEquals(1, c.col)
        assertEquals(2, c.rowSpan)
        assertEquals(3, c.colSpan)
    }

    @Test
    fun skipsCommentsAndBlankLines() {
        val text = """
            # 这是注释
            1号教学楼|1|S101

            2号教学楼|1|S201
        """.trimIndent()
        assertEquals(2, BuiltinRoomCells.parse(text).size)
    }

    @Test
    fun dropsLinesWithTooFewColumns() {
        assertTrue(BuiltinRoomCells.parse("1号教学楼|1").isEmpty())
        assertTrue(BuiltinRoomCells.parse("1号教学楼|1|").isEmpty())
        assertTrue(BuiltinRoomCells.parse("").isEmpty())
    }

    @Test
    fun unknownTypeFallsBackToClassroom() {
        assertEquals(RoomCellType.CLASSROOM, BuiltinRoomCells.parse("三教|1|A1||teleporter")[0].roomType)
    }

    @Test
    fun halfCoordinateIsDiscarded() {
        // 只给行不给列 → 视为未设置，避免布局算法收到非法中间态
        val c = BuiltinRoomCells.parse("三教|1|A1||||2|")[0]
        assertEquals(-1, c.row)
        assertEquals(-1, c.col)
    }

    @Test
    fun spanIsClamped() {
        val c = BuiltinRoomCells.parse("三教|1|A1||||0|0|99|0")[0]
        assertEquals(RoomDirectorySearch.MAX_SPAN, c.rowSpan)
        assertEquals(1, c.colSpan)
    }

    @Test
    fun duplicateRoomInSameBuildingAndFloorIsDeduped() {
        val text = "三教|1|A1\n三教|1|A1"
        assertEquals(1, BuiltinRoomCells.parse(text).size)
    }

    @Test
    fun idIsStableAndPrefixed() {
        val id = BuiltinRoomCells.builtinId("三教", "1", "A1")
        assertEquals("builtin:三教/1/A1", id)
        assertEquals(id, BuiltinRoomCells.parse("三教|1|A1")[0].id)
    }

    @Test
    fun whitespaceInFieldsIsTrimmed() {
        val c = BuiltinRoomCells.parse("  三教 | 1 | A1  ")[0]
        assertEquals("三教", c.building)
        assertEquals("1", c.floor)
        assertEquals("A1", c.room)
    }

    @Test
    fun builtinDataCoversFirstTeachingBuildingFloors() {
        val cells = BuiltinRoomCells.all.filter { it.building == "1号教学楼" }

        assertEquals(109, cells.size)
        assertEquals(
            mapOf("1" to 18, "2" to 24, "3" to 18, "4" to 23, "5" to 26),
            cells.groupingBy { it.floor }.eachCount(),
        )
        assertTrue(
            "4F 应包含指引牌上的 413–424",
            (413..424).all { number -> cells.any { it.floor == "4" && it.room == "$number" } },
        )
        assertTrue(
            "5F 应保留 513-1 / 513-2 这类分间号",
            cells.any { it.floor == "5" && it.room == "513-1" } &&
                cells.any { it.floor == "5" && it.room == "513-2" },
        )
    }

    @Test
    fun builtinFirstTeachingLayoutPlacesEveryRoomOnFourColumns() {
        val floors = BuiltinRoomCells.all.filter { it.building == "1号教学楼" }

        floors.groupBy { it.floor }.forEach { (floor, cells) ->
            val layout = RoomDirectorySearch.layoutGrid(cells, columns = 4)
            assertEquals("$floor 层不应丢房间", cells.size, layout.placements.size)

            val occupied = mutableSetOf<Pair<Int, Int>>()
            layout.placements.forEach { placement ->
                for (row in placement.row until placement.row + placement.rowSpan) {
                    for (col in placement.col until placement.col + placement.colSpan) {
                        assertTrue(
                            "$floor 层格子重叠：${placement.cell.room}",
                            occupied.add(row to col),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun builtinFirstTeachingFloor4MatchesBoardZones() {
        val cells = BuiltinRoomCells.all.filter {
            it.building == "1号教学楼" && it.floor == "4"
        }
        val layout = RoomDirectorySearch.layoutGrid(cells, columns = 4)

        fun roomAt(row: Int, col: Int): String? = layout.cellAt(row, col)?.room
        assertEquals("北区卫生间", roomAt(0, 0))
        assertEquals("413", roomAt(1, 0))
        assertEquals("424", roomAt(12, 0))
        assertEquals("406", roomAt(0, 3))
        assertEquals("401", roomAt(5, 3))
        assertEquals("东北楼梯", roomAt(0, 1))
        assertEquals("西南楼梯", roomAt(10, 2))
    }
}