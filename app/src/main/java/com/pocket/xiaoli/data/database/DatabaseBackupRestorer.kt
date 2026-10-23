package com.pocket.xiaoli.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement

/**
 * 完整 JSON 备份恢复器。
 *
 * 备份格式由 `DeveloperDataExport.databaseToJson()` 生成：
 * `{ "databaseVersion": 13, "tables": { "courses": [ {...} ] } }`。
 *
 * 恢复时采用「先 DELETE 再 INSERT」，在事务中完成；未知表会被忽略，避免误写
 * sqlite 系统表。恢复后应重启 App，让内存 StateFlow 重新读取。
 */
object DatabaseBackupRestorer {

    /**
     * 用 [json] 覆盖数据库内容。
     *
     * ⚠️ 结构不合法时**必须抛异常**，不能静默 return：调用方（`ProfileDialogHost` 的
     * 恢复流程）靠异常决定提示「恢复失败」还是「恢复成功」。历史实现在缺 `tables` 字段
     * 时直接返回，于是用户选了一个不相干的 JSON 文件，界面照样弹「完整备份已恢复，即将重启」——
     * 备份没进去，用户以为进去了。
     */
    fun restore(db: SupportSQLiteDatabase, json: String) {
        // 解析交给纯函数（可单测），这里只负责「DELETE 再 INSERT」。
        val contents = BackupParser.parse(json)

        val existingTables = existingUserTables(db)

        db.beginTransaction()
        try {
            contents.tables.forEach { table ->
                if (table.name !in existingTables) return@forEach
                db.execSQL("DELETE FROM ${quote(table.name)}")
                table.rows.forEach { row -> insertRow(db, table.name, row) }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun existingUserTables(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (isUserTable(name)) names += name
            }
        }
        return names
    }

    /**
     * 备份 / 恢复的业务表白名单判断。
     *
     * `campus_map_corrections`、`special_work_day`、`saved_grades` 等新表
     * 只要不是 Room / Android 系统表，就自动进入完整备份，无需逐个硬编码。
     */
    internal fun isUserTable(name: String): Boolean =
        !name.startsWith("sqlite_") &&
            !name.startsWith("android_") &&
            !name.startsWith("room_")

    private fun insertRow(
        db: SupportSQLiteDatabase,
        table: String,
        row: Map<String, Any?>,
    ) {
        if (row.isEmpty()) return
        val columns = row.keys.toList()
        val columnSql = columns.joinToString(",") { quote(it) }
        val placeholders = columns.joinToString(",") { "?" }
        val statement = db.compileStatement(
            "INSERT INTO ${quote(table)} ($columnSql) VALUES ($placeholders)"
        )
        try {
            columns.forEachIndexed { index, column ->
                bindValue(statement, index + 1, row[column])
            }
            statement.executeInsert()
        } finally {
            statement.close()
        }
    }

    /**
     * 把一个已解析的单元格值绑到 SQL 参数上。
     *
     * 输入类型由 [BackupParser.cellValue] 决定，两边**必须同步改**。
     */
    internal fun bindValue(stmt: SupportSQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null -> stmt.bindNull(index)
            // 二进制列逐字节还原（导出端写成 { "$blob": "<base64>" }）。
            is BlobValue -> stmt.bindBlob(index, value.bytes)
            is Boolean -> stmt.bindLong(index, if (value) 1L else 0L)
            is Double -> stmt.bindDouble(index, value)
            is Float -> stmt.bindDouble(index, value.toDouble())
            is Number -> stmt.bindLong(index, value.toLong())
            else -> stmt.bindString(index, value.toString())
        }
    }

    private fun quote(name: String): String {
        return "\"" + name.replace("\"", "\"\"") + "\""
    }
}

