package com.pocket.xiaoli.ui.dev

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocket.xiaoli.data.database.BlobValue

/** 一张 Room 表的元信息。 */
data class RoomTableInfo(
    val name: String,
    val rowCount: Long,
)

/** 一次表数据读取结果。 */
data class RoomTablePage(
    val tableName: String,
    val columns: List<String>,
    val rows: List<List<Any?>>,
    val totalRows: Long,
)

/**
 * Room 数据查看器的数据访问层。
 *
 * 刻意使用 `sqlite_master` + 原生 `SELECT *`，不逐个实体写 DAO：
 * 这样新增表 / 字段后查看器不用同步维护，也更符合开发者工具的定位。
 * 不会执行任何写操作，避免误改用户真实数据。
 */
object RoomDataViewer {

    const val PREVIEW_LIMIT = 100

    /**
     * 导出时**每页**读取的行数（不是上限）。
     *
     * ⚠️ 历史版本把这个值当成了「每表最多导出多少行」：`exportJson` 只读第一页，
     * 于是任何超过 2000 行的表在完整备份里被**静默截断** —— 不报错、不提示，
     * 恢复回来就是少数据。现在 [exportJson] 会一直翻页读到表尾，这个值只决定
     * 单次查询的规模。
     */
    const val EXPORT_PAGE_SIZE = 2_000

    fun listTables(db: SupportSQLiteDatabase): List<RoomTableInfo> {
        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT LIKE 'android_%' " +
                "AND name NOT LIKE 'room_%' " +
                "ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0)
            }
        }
        return names.map { RoomTableInfo(it, countRows(db, it)) }
    }

    fun countRows(db: SupportSQLiteDatabase, table: String): Long {
        db.query("SELECT COUNT(*) FROM ${quoteIdentifier(table)}").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun readPage(
        db: SupportSQLiteDatabase,
        table: String,
        limit: Int = PREVIEW_LIMIT,
        offset: Int = 0,
    ): RoomTablePage {
        val safeLimit = limit.coerceIn(1, EXPORT_PAGE_SIZE)
        val safeOffset = offset.coerceAtLeast(0)
        val sql = "SELECT * FROM ${quoteIdentifier(table)} LIMIT $safeLimit OFFSET $safeOffset"
        db.query(sql).use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = mutableListOf<List<Any?>>()
            while (cursor.moveToNext()) {
                rows += List(columns.size) { index -> readValue(cursor, index) }
            }
            return RoomTablePage(
                tableName = table,
                columns = columns,
                rows = rows,
                totalRows = countRows(db, table),
            )
        }
    }

    /**
     * 把一张表**全部**行导出为 JSON 数组。
     *
     * 分页读到表尾，不再只取第一页（历史实现的 2000 行静默截断见 [EXPORT_PAGE_SIZE]）。
     * 用 `totalRows` 兜底：万一某次分页返回了满页但表其实已经到底，下一轮会读回空页，
     * 循环随即结束，不会死循环。
     */
    fun exportJson(db: SupportSQLiteDatabase, table: String): String {
        val allRows = mutableListOf<List<Any?>>()
        var columns: List<String> = emptyList()
        var offset = 0
        while (true) {
            val page = readPage(db, table, limit = EXPORT_PAGE_SIZE, offset = offset)
            columns = page.columns
            val next = nextOffset(offset, page.rows.size, EXPORT_PAGE_SIZE) ?: break
            allRows.addAll(page.rows)
            offset = next
        }
        return RoomDataJson.pretty(columns, allRows)
    }

    /**
     * 分页推进规则；返回下一页的 offset，返回 `null` 表示已到表尾。
     *
     * 抽成纯函数是为了能单测「读到表尾」这条判断 —— 历史 bug 正出在这里：
     * 旧实现只读第一页就当读完了，于是超过一页的表在备份里被**静默截断**。
     * 判据只有两条：读回空页 = 到底；读回的不足整页 = 到底。
     */
    internal fun nextOffset(offset: Int, readRows: Int, pageSize: Int): Int? = when {
        readRows <= 0 -> null
        readRows < pageSize -> null
        else -> offset + readRows
    }

    internal fun quoteIdentifier(name: String): String {
        return "\"" + name.replace("\"", "\"\"") + "\""
    }

    private fun readValue(cursor: Cursor, index: Int): Any? {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
            // 二进制列包一层，让 JSON 层能编码成 base64（见 BackupFormat.BLOB_KEY）。
            // 直接写 "<blob>" 是不可逆的：恢复回来会变成这个字符串本身。
            Cursor.FIELD_TYPE_BLOB -> BlobValue(cursor.getBlob(index))
            else -> cursor.getString(index)
        }
    }
}

