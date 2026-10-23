package com.pocket.xiaoli.data.database

/**
 * 完整备份的 JSON 结构约定。
 *
 * 导出端 [com.pocket.xiaoli.ui.dev.DeveloperDataExport.databaseToJson] 与恢复端
 * [DatabaseBackupRestorer] 共用这里的键名；两边**必须同步改**，否则恢复会静默丢列。
 *
 * 结构：
 * ```
 * {
 *   "exportedAt": 1730000000000,
 *   "databaseVersion": 18,
 *   "tables": { "courses": [ { "id": 1, ... } ] }
 * }
 * ```
 */
object BackupFormat {

    /** 导出时间戳（毫秒）。 */
    const val EXPORTED_AT_KEY = "exportedAt"

    /** 导出时的 Room schema 版本，方便恢复前判断新旧。 */
    const val DATABASE_VERSION_KEY = "databaseVersion"

    /** 表名 → 行数组。 */
    const val TABLES_KEY = "tables"

    /**
     * 二进制列的标记键。
     *
     * BLOB 不能直接塞进 JSON，所以编码成 `{ "$blob": "<base64>" }`。
     * ⚠️ 历史版本把它写成字符串 `"<blob>"`，**恢复回来是一个字符串而不是二进制**，
     * 属于不可逆的数据损失。改成 base64 后可以逐字节还原。
     */
    const val BLOB_KEY = "\$blob"
}

/**
 * 数据库里读出来的二进制列值。
 *
 * 单独包一层是为了让 [com.pocket.xiaoli.ui.dev.RoomDataJson] 能把它和普通字符串
 * 区分开 —— 否则「本来就是字符串 `"<blob>"` 的用户数据」与「二进制列」会混成一样。
 */
class BlobValue(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is BlobValue && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "BlobValue(${bytes.size} bytes)"
}
