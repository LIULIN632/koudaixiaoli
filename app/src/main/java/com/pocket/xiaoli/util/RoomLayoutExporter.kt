package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.map.RoomCell

/**
 * 教室分布导出：内置投稿格式（竖线文本）与 CSV。
 *
 * 内置格式与 `docs/BuildingDataFormat.md` 保持一致，可直接贴进
 * `data/map/BuiltinRoomCells.RAW`。纯函数，便于单测。
 */
object RoomLayoutExporter {

    /** 内置格式的表头注释（解析时会跳过 `#` 行） */
    const val BUILTIN_HEADER = "# 楼栋|楼层|房间号|用途|类型|容量|行|列|跨行|跨列"

    /** 竖线文本：`楼栋|楼层|房间号|用途|类型|容量|行|列|跨行|跨列` */
    fun toBuiltinText(cells: List<RoomCell>): String = buildString {
        appendLine(BUILTIN_HEADER)
        cells.forEach { c ->
            appendLine(
                listOf(
                    c.building,
                    c.floor,
                    c.room,
                    c.name,
                    c.roomType.key,
                    if (c.capacity > 0) c.capacity.toString() else "",
                    if (c.row >= 0) c.row.toString() else "",
                    if (c.col >= 0) c.col.toString() else "",
                    c.rowSpan.toString(),
                    c.colSpan.toString(),
                ).joinToString("|")
            )
        }
    }

    /** CSV（类型用中文标签，坐标未设置留空） */
    fun toCsv(cells: List<RoomCell>): String = buildString {
        appendLine("楼栋,楼层,房间号,用途,类型,容量,行,列,跨行,跨列")
        cells.forEach { c ->
            appendLine(
                listOf(
                    c.building,
                    c.floor,
                    c.room,
                    c.name,
                    c.roomType.label,
                    if (c.capacity > 0) c.capacity.toString() else "",
                    if (c.row >= 0) c.row.toString() else "",
                    if (c.col >= 0) c.col.toString() else "",
                    c.rowSpan.toString(),
                    c.colSpan.toString(),
                ).joinToString(",") { escapeCsv(it) }
            )
        }
    }

    private fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
