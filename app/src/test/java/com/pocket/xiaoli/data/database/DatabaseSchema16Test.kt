package com.pocket.xiaoli.data.database

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 锁住 `exams` 表的索引。
 *
 * ## 为什么值得单独写一个测试
 *
 * 这里踩过一次**迁移与实体不一致**的坑：`MIGRATION_14_15` 建了
 * `(schemeId, completed, date)`，但 `ExamEntity` 上**没有对应的 `@Index`**。
 * 后果不是崩溃 —— Room 运行时校验失败只会打一条 warning，所以它很容易被忽略，
 * 而预期 schema 里没有索引意味着**谁也无法从实体看出来数据库里到底有没有**。
 *
 * 第二个坑是索引**列顺序对错了**：唯一查询是
 *
 * ```sql
 * SELECT * FROM exams WHERE schemeId = :schemeId ORDER BY date, time
 * ```
 *
 * 而 `completed` 被插在了中间 —— 跳过中间列就断了前缀匹配，这条索引对上面那句完全无效。
 * 而且没有任何查询过滤 `completed`（完成态是在内存里筛的）。
 *
 * ## 这个测试保证什么
 *
 * 索引存在、且列顺序恰好是 `schemeId, date, time`（照抄 WHERE + ORDER BY）。
 * 只要有人改动 `ExamEntity` 的 `@Index` 却忘了迁移（或反之），这里就会红。
 */
class DatabaseSchema16Test {

    @Test
    fun examsIndexMatchesTheOnlyQueryPattern() {
        val db = database() ?: return
        assertEquals(16, db.getInt("version"))

        val exams = entity(db, "exams") ?: fail("exams missing from schema 16").let { return }
        val indices = exams.optJSONArray("indices")
        assertTrue("exams 必须声明索引，否则与 MIGRATION_15_16 不一致", indices != null && indices.length() > 0)

        val first = indices!!.getJSONObject(0)
        assertEquals(
            "索引名必须与 MIGRATION_15_16 里 CREATE INDEX 的名字一致，否则运行时校验对不上",
            "index_exams_schemeId_date_time",
            first.getString("name"),
        )

        val columns = first.getJSONArray("columnNames")
        val actual = (0 until columns.length()).map { columns.getString(it) }
        assertEquals(
            "列顺序必须照抄 WHERE(schemeId) + ORDER BY(date, time)；" +
                "把 completed 之类没有查询用到的列插在中间会让索引失效",
            listOf("schemeId", "date", "time"),
            actual,
        )
    }

    @Test
    fun oldIndexIsNotDeclaredAnyMore() {
        val db = database() ?: return
        val exams = entity(db, "exams") ?: return
        val indices = exams.optJSONArray("indices") ?: return
        val names = (0 until indices.length()).map { indices.getJSONObject(it).getString("name") }
        assertTrue(
            "旧的 (schemeId, completed, date) 索引已被 MIGRATION_15_16 删除，实体里不应再声明",
            "index_exams_schemeId_completed_date" !in names,
        )
    }

    private fun database(): JSONObject? {
        val file = schemaFile() ?: fail("schema 16.json not found").let { return null }
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
        val relative = "schemas/com.pocket.xiaoli.data.database.PocketXiaoliDatabase/16.json"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../app/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
