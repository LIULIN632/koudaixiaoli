package com.pocket.xiaoli.data.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DatabaseSchema13Test {

    @Test
    fun schema13ContainsCampusMapCorrectionsTable() {
        val file = schemaFile() ?: fail("schema 13.json not found").let { return }
        val root = JSONObject(file.readText())
        val database = root.getJSONObject("database")
        assertEquals(13, database.getInt("version"))

        val entities = database.getJSONArray("entities")
        var table: JSONObject? = null
        for (index in 0 until entities.length()) {
            val candidate = entities.getJSONObject(index)
            if (candidate.getString("tableName") == "campus_map_corrections") {
                table = candidate
                break
            }
        }
        val correctionTable = table ?: fail("campus_map_corrections missing from schema 13").let { return }

        val columns = mutableSetOf<String>()
        val fields = correctionTable.getJSONArray("fields")
        for (index in 0 until fields.length()) {
            columns += fields.getJSONObject(index).getString("columnName")
        }

        assertTrue(
            columns.containsAll(
                setOf(
                    "id", "campus_id", "kind", "target_id", "name", "lat", "lng",
                    "building_id", "floor_id", "payload", "updated_at",
                )
            )
        )
        assertTrue(correctionTable.getString("createSql").contains("TABLE_NAME"))
    }

    private fun schemaFile(): File? {
        val relative = "schemas/com.pocket.xiaoli.data.database.PocketXiaoliDatabase/13.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}