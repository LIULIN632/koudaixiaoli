package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomLayoutExporterTest {

    private fun room(
        building: String = "1 号楼",
        floor: String = "1 层",
        room: String = "A101",
        name: String = "多媒体教室",
        row: Int = 0,
        col: Int = 1,
    ) = RoomCell(
        id = "x",
        campusId = "ytlg_main",
        building = building,
        floor = floor,
        room = room,
        name = name,
        type = RoomCellType.CLASSROOM.key,
        capacity = 60,
        row = row,
        col = col,
    )

    @Test
    fun builtinTextMatchesDocumentedFormat() {
        val text = RoomLayoutExporter.toBuiltinText(listOf(room()))
        val lines = text.trim().split("\n")
        assertEquals(RoomLayoutExporter.BUILTIN_HEADER, lines[0])
        assertEquals("1 号楼|1 层|A101|多媒体教室|classroom|60|0|1|1|1", lines[1])
    }

    @Test
    fun builtinTextLeavesUnsetCoordinatesBlank() {
        val text = RoomLayoutExporter.toBuiltinText(listOf(room(row = -1, col = -1)))
        assertTrue(text.contains("1 号楼|1 层|A101|多媒体教室|classroom|60|||1|1"))
    }

    @Test
    fun csvHasHeaderAndEscapesComma() {
        val csv = RoomLayoutExporter.toCsv(listOf(room(name = "多媒体,大教室")))
        val lines = csv.trim().split("\n")
        assertEquals("楼栋,楼层,房间号,用途,类型,容量,行,列,跨行,跨列", lines[0])
        assertTrue(lines[1].contains("\"多媒体,大教室\""))
    }
}
