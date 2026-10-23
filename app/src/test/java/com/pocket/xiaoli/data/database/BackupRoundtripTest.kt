package com.pocket.xiaoli.data.database

import com.pocket.xiaoli.ui.dev.RoomDataJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「导出 → 再导入」往返一致性。
 *
 * ## 为什么是这个测试
 *
 * 发布门禁要求「用户数据可删除、**可备份、可恢复**」，而完整备份此前**一个端到端用例都没有**
 * （`DatabaseBackupRestorerTest` 只断言了 `isUserTable`）。本项目没引 `room-testing`，
 * 迁移体在 JVM 里跑不起来，所以按既有规矩把判断抽成纯函数
 * （[BackupParser.cellValue]）来覆盖 —— 这里测的正是「一个单元格导出后再导入还是原来那个值」。
 *
 * 三处历史数据损失，本测试各守一条：
 * 1. BLOB 被写成字符串 `"<blob>"`，恢复回来是文本而不是二进制 → [BlobValue] 往返。
 * 2. 主键被写成浮点（JSON 只有一种 number 类型）→ `id` 必须还原成 `Long`。
 * 3. 空的 `tables` / 不相干的 JSON 被当成功 → [BackupParser.parse] 必须抛异常。
 */
class BackupRoundtripTest {

    private val columns =
        listOf("id", "title", "weeks", "score", "note", "payload", "empty")

    /** 覆盖字符串 / 整数 / 小数 / null / 二进制 / 引号 / 换行 / 负数 / 零长度二进制。 */
    private fun sampleRows(): List<List<Any?>> = listOf(
        listOf(1L, "高等数学", 3L, 91.5, null, BlobValue(byteArrayOf(0, 1, 2, 127, -1)), null),
        listOf(2L, "大学物理", 0L, 0.0, "含\"引号\"", BlobValue(ByteArray(0)), null),
        listOf(-9_000_000_000L, "", 20L, -0.5, "多行\n文本", BlobValue(byteArrayOf(10)), null),
    )

    private fun export(rows: List<List<Any?>>): String =
        """{"databaseVersion":18,"tables":{"courses":${RoomDataJson.pretty(columns, rows)}}}"""

    @Test
    fun `导出再导入，每个单元格都还原成原来的值`() {
        val rows = sampleRows()
        val contents = BackupParser.parse(export(rows))

        val table = contents.tables.single()
        assertEquals("courses", table.name)
        assertEquals(rows.size, table.rows.size)

        rows.forEachIndexed { rowIndex, original ->
            columns.forEachIndexed { columnIndex, name ->
                assertEquals(
                    "第 $rowIndex 行 / $name 列",
                    original[columnIndex],
                    table.rows[rowIndex][name],
                )
            }
        }
    }

    @Test
    fun `二进制列逐字节还原，不是字符串`() {
        val original = byteArrayOf(0, 1, 2, -1, -128, 127)
        val json = export(listOf(listOf(1L, "x", 1L, 1.0, null, BlobValue(original), null)))

        val value = BackupParser.parse(json).tables.single().rows.single()["payload"]

        assertTrue("期望还原成 BlobValue，实际是 ${value?.javaClass?.name}", value is BlobValue)
        assertTrue((value as BlobValue).bytes.contentEquals(original))
    }

    @Test
    fun `整数不会被还原成小数`() {
        val json = export(listOf(listOf(7L, "x", 1L, 1.0, null, null, null)))

        val row = BackupParser.parse(json).tables.single().rows.single()

        // JSON 里只有 number 一种数字类型；若不按字面区分，id 会变成 7.0，
        // SQLite 的 INTEGER 亲和性会把它写成浮点，后续 WHERE id = 7 就查不到了。
        assertEquals(7L, row["id"])
        assertEquals(1.0, row["score"])
    }

    @Test
    fun `空表也能导出再导入`() {
        val contents = BackupParser.parse(export(emptyList()))
        assertEquals(0, contents.tables.single().rows.size)
    }

    @Test
    fun `缺 tables 字段的文件必须报错，不能静默当成恢复成功`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupParser.parse("""{"databaseVersion":18}""")
        }
        assertTrue(
            "错误信息要能让用户看懂，实际是「${error.message}」",
            error.message?.contains("tables") == true,
        )
    }

    @Test
    fun `不是 JSON 的文件必须报错`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupParser.parse("这不是 JSON")
        }
    }

    @Test
    fun `JSON 数组不是备份文件`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupParser.parse("[]")
        }
    }
}
