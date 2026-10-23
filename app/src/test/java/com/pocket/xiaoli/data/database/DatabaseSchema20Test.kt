package com.pocket.xiaoli.data.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `MIGRATION_19_20` 落库后的 schema：学期升成一级实体（`terms`），`schemes` 多了 `termId`，
 * `courses` 多了 `note` / `startMinute` / `endMinute` / `kind`。
 *
 * ## 为什么锁 schema JSON 而不是跑一次迁移
 *
 * 与 [DatabaseSchema17Test] 同因：本项目没引 `room-testing`（`MigrationTestHelper` 要 instrumentation），
 * 所以"迁移后表结构对不对"只能靠锁 schema。**老列一个都不能少** —— 迁移链是一路接上来的。
 *
 * ⚠️ 真会改坏用户数据的那一步（把 `weekType = odd/even` 展开成具体周次再统一置 `all`）
 * 写在 `MIGRATION_19_20` 的游标循环里，属于运行时逻辑，schema 测不出来；
 * 它的取舍是：**宁可让单双周变成"显式周次列表"，也不引入第二份周次表示**（见 memory/02-feature-map.md）。
 */
class DatabaseSchema20Test {

    @Test
    fun termsTableExists() {
        val db = database() ?: return
        assertEquals("迁移链已到 20", 20, db.getInt("version"))

        val terms = entity(db, "terms") ?: fail("terms missing from schema 20").let { return }
        val columns = terms.getJSONArray("fields")
        val names = (0 until columns.length()).map { columns.getJSONObject(it).getString("columnName") }

        listOf("id", "name", "startDate", "totalWeeks", "sortOrder").forEach { column ->
            assertTrue("terms.$column 必须存在", column in names)
        }
    }

    @Test
    fun schemesHasTermId() {
        val db = database() ?: return
        val schemes = entity(db, "schemes") ?: fail("schemes missing").let { return }
        val columns = schemes.getJSONArray("fields")
        val names = (0 until columns.length()).map { columns.getJSONObject(it).getString("columnName") }

        assertTrue("termId 必须存在（方案挂在学期下）", "termId" in names)
        // 老列一个都不能少
        listOf("id", "name", "sortOrder").forEach { column ->
            assertTrue("$column 不该消失", column in names)
        }
    }

    @Test
    fun coursesHasNoteAndMinuteTimeAndKind() {
        val db = database() ?: return
        val courses = entity(db, "courses") ?: fail("courses missing").let { return }
        val columns = courses.getJSONArray("fields")
        val names = (0 until columns.length()).map { columns.getJSONObject(it).getString("columnName") }

        assertTrue("note 必须存在（日程备注 / 课程备注）", "note" in names)
        assertTrue("startMinute 必须存在（分钟级时间，-1=用节次）", "startMinute" in names)
        assertTrue("endMinute 必须存在（分钟级时间，-1=用节次）", "endMinute" in names)
        assertTrue("kind 必须存在（course / agenda）", "kind" in names)

        // 老列一个都不能少
        listOf(
            "id", "name", "credits", "teacher", "room", "dayOfWeek", "startSlot", "endSlot",
            "weekType", "weeks", "colorIndex", "schemeId", "shortName", "colorPinned",
        ).forEach { column ->
            assertTrue("$column 不该消失", column in names)
        }
    }

    private fun database(): JSONObject? {
        val file = schemaFile() ?: fail("schema 20.json not found").let { return null }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    private fun entity(db: JSONObject, tableName: String): JSONObject? {
        val entities = db.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val candidate = entities.getJSONObject(index)
            if (candidate.getString("tableName") == tableName) return candidate
        }
        return null
    }

    private fun schemaFile(): File? {
        val relative = "schemas/com.pocket.xiaoli.data.database.PocketXiaoliDatabase/20.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
