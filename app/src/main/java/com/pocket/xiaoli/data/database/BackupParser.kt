package com.pocket.xiaoli.data.database

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.Base64

/** 备份里的一张表：名字 + 逐行「列名 → 值」。 */
data class BackupTable(val name: String, val rows: List<Map<String, Any?>>)

/** 解析后的完整备份内容。 */
data class BackupContents(val tables: List<BackupTable>)

/**
 * 完整备份 JSON → 纯数据。
 *
 * ## 为什么单独抽出来
 *
 * 本项目**没有引 `room-testing`**（迁移体在 JVM 单测里跑不起来），所以按既有规矩
 * （见 `CourseRowRepair` + `CourseRowRepairTest`）：**把决策抽成纯函数再单测**，
 * 真正碰数据库的那一层只剩「按结果执行 SQL」。
 *
 * [DatabaseBackupRestorer.restore] 因此变成「parse → DELETE → INSERT」三步，
 * 而「一个单元格该还原成什么」这条最容易写错的判断，全部落在 [cellValue] 里，
 * 可以由 `BackupRoundtripTest` 直接覆盖，包括 BLOB 的逐字节还原。
 *
 * @throws IllegalArgumentException 不是 JSON、不是对象、缺 `tables` 字段。
 */
object BackupParser {

    fun parse(json: String): BackupContents {
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("不是合法的 JSON 文件", e)
        } ?: throw IllegalArgumentException("备份文件不是 JSON 对象")

        val tablesElement = root.get(BackupFormat.TABLES_KEY)
        if (tablesElement == null || !tablesElement.isJsonObject) {
            throw IllegalArgumentException(
                "不是完整备份文件（缺少 ${BackupFormat.TABLES_KEY} 字段）"
            )
        }

        val tables = tablesElement.asJsonObject.entrySet().mapNotNull { (name, rowsElement) ->
            if (!rowsElement.isJsonArray) return@mapNotNull null
            val rows = rowsElement.asJsonArray.mapNotNull { rowElement ->
                if (!rowElement.isJsonObject) return@mapNotNull null
                rowElement.asJsonObject.entrySet()
                    .associate { (column, value) -> column to cellValue(value) }
            }
            BackupTable(name, rows)
        }
        return BackupContents(tables)
    }

    /**
     * 单个单元格：JSON → 可回写的 Kotlin 值。
     *
     * 返回值只可能是 `null` / [BlobValue] / `Boolean` / `Long` / `Double` / `String`，
     * 与 [DatabaseBackupRestorer.bindValue] 的分支一一对应。
     *
     * ⚠️ 整数与小数必须分开：JSON 只有 `number` 一种数字类型，若一律走 `asDouble`，
     * `id` 这类主键在恢复后会变成 `1.0`，SQLite 的 INTEGER 亲和性会把它写成浮点，
     * 后续 `WHERE id = 1` 查不到。所以按字面里有没有 `.`/`e` 判。
     */
    fun cellValue(element: JsonElement?): Any? {
        if (element == null || element.isJsonNull) return null

        if (element.isJsonObject) {
            val encoded = element.asJsonObject.get(BackupFormat.BLOB_KEY)
            if (encoded != null && encoded.isJsonPrimitive) {
                return try {
                    BlobValue(Base64.getDecoder().decode(encoded.asString))
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "备份里的 ${BackupFormat.BLOB_KEY} 字段不是合法 base64", e
                    )
                }
            }
            // 不是二进制信封的普通对象：原样当字符串写回去（导出端不会产生这种，属于兜底）。
            return element.toString()
        }

        if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            return when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> {
                    val raw = primitive.asString
                    if (raw.contains('.') || raw.contains('e', ignoreCase = true)) {
                        primitive.asDouble
                    } else {
                        primitive.asLong
                    }
                }
                else -> primitive.asString
            }
        }

        return element.toString()
    }
}
