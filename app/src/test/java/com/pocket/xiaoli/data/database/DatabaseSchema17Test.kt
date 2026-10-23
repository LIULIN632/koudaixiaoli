package com.pocket.xiaoli.data.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * `MIGRATION_16_17` 落库后的 schema：`courses` 多了 `shortName` / `colorPinned`，
 * 且 15→16 建的那条 exams 索引**还在**（迁移链是一路接上来的，谁都不许半路丢东西）。
 *
 * ## 为什么锁 schema JSON 而不是跑一次迁移
 *
 * 本项目没引 `room-testing`（`MigrationTestHelper` 要 instrumentation），
 * 所以"迁移后表结构对不对"只能两路守：
 * 1. 这段断言 —— 把 `17.json` 里 `courses` 的列钉死，实体与迁移不一致时红；
 * 2. `CourseRowRepairTest` —— 把**数据修复的决策**钉死（那部分才是真会改坏用户数据的地方）。
 *
 * ⚠️ `shortName` / `colorPinned` 的 `defaultValue` 在实体上**刻意没声明**（`default=` 为空），
 * 而迁移里写了 `DEFAULT ''` / `DEFAULT 0`：Room 的运行时校验只在**实体声明了**默认值时才比对，
 * 所以这样是安全的 —— 与 `schemeId`（MIGRATION_2_3 加列）走的是同一条路。
 */
class DatabaseSchema17Test {

    @Test
    fun coursesHasShortNameAndColorPinned() {
        val db = database() ?: return
        assertEquals("迁移链已到 17", 17, db.getInt("version"))

        val courses = entity(db, "courses") ?: fail("courses missing from schema 17").let { return }
        val columns = courses.getJSONArray("fields")
        val names = (0 until columns.length()).map { columns.getJSONObject(it).getString("columnName") }

        assertTrue("shortName 必须存在（课表简称）", "shortName" in names)
        assertTrue("colorPinned 必须存在（颜色是否用户手选）", "colorPinned" in names)
        // 老列一个都不能少
        listOf("id", "name", "credits", "teacher", "room", "dayOfWeek", "startSlot", "endSlot",
            "weekType", "weeks", "colorIndex", "schemeId").forEach { column ->
            assertTrue("$column 不该消失", column in names)
        }
    }

    @Test
    fun examsIndexSurvivesTheMigrationChain() {
        val db = database() ?: return
        val exams = entity(db, "exams") ?: return
        val indices = exams.optJSONArray("indices")
        assertNotNull("15→16 建的索引不能在 17 里丢掉", indices)
        val names = (0 until indices!!.length()).map { indices.getJSONObject(it).getString("name") }
        assertTrue(
            "index_exams_schemeId_date_time 是 MIGRATION_15_16 建的，17 里必须还在",
            "index_exams_schemeId_date_time" in names,
        )
    }

    private fun database(): JSONObject? {
        val file = schemaFile() ?: fail("schema 17.json not found").let { return null }
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
        val relative = "schemas/com.pocket.xiaoli.data.database.PocketXiaoliDatabase/17.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
