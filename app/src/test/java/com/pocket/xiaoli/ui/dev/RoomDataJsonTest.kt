package com.pocket.xiaoli.ui.dev

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room 数据查看器 JSON 格式化：列顺序、空值与数字类型。 */
class RoomDataJsonTest {

    @Test
    fun prettyRowKeepsColumnOrder() {
        val json = RoomDataJson.prettyRow(
            columns = listOf("id", "name", "credits"),
            row = listOf(1L, "高数", 3.5),
        )
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals(listOf("id", "name", "credits"), obj.keySet().toList())
        assertEquals(1L, obj.get("id").asLong)
        assertEquals("高数", obj.get("name").asString)
        assertEquals(3.5, obj.get("credits").asDouble, 0.0)
    }

    @Test
    fun prettyRowsWrapsArray() {
        val json = RoomDataJson.pretty(
            columns = listOf("id"),
            rows = listOf(listOf(1L), listOf(2L)),
        )
        val array = JsonParser.parseString(json).asJsonArray
        assertEquals(2, array.size())
        assertEquals(2L, array[1].asJsonObject.get("id").asLong)
    }

    @Test
    fun nullBecomesJsonNull() {
        val json = RoomDataJson.prettyRow(
            columns = listOf("teacher"),
            row = listOf(null),
        )
        val obj = JsonParser.parseString(json).asJsonObject
        assertTrue(obj.get("teacher").isJsonNull)
    }

    @Test
    fun missingCellBecomesJsonNull() {
        val json = RoomDataJson.prettyRow(
            columns = listOf("a", "b"),
            row = listOf("only-a"),
        )
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("only-a", obj.get("a").asString)
        assertTrue(obj.get("b").isJsonNull)
    }
}
