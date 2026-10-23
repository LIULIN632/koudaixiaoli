package com.pocket.xiaoli.ui.dev

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.pocket.xiaoli.data.database.BackupFormat
import com.pocket.xiaoli.data.database.BlobValue
import java.util.Base64

/**
 * Room 数据查看器的 JSON 格式化。
 *
 * 只依赖列名与单元格值，不接触 Android / Room 类型，因此可以被 JVM 单测覆盖。
 * 保持列顺序，便于开发者对照表结构排查问题。
 */
object RoomDataJson {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()

    /** 一行数据格式化为 JSON 对象。 */
    fun prettyRow(columns: List<String>, row: List<Any?>): String {
        return gson.toJson(toJsonObject(columns, row))
    }

    /** 多行数据格式化为 JSON 数组。 */
    fun pretty(columns: List<String>, rows: List<List<Any?>>): String {
        val array = JsonArray()
        rows.forEach { array.add(toJsonObject(columns, it)) }
        return gson.toJson(array)
    }

    private fun toJsonObject(columns: List<String>, row: List<Any?>): JsonObject {
        val obj = JsonObject()
        columns.forEachIndexed { index, column ->
            obj.add(column, toJsonElement(row.getOrNull(index)))
        }
        return obj
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        // 二进制列编码成 { "$blob": "<base64>" }，恢复端按同一约定解回 BLOB。
        // ⚠️ 不能落到下面的 toString() 分支：那会得到 "[B@1a2b3c"，既不可读也不可还原。
        is BlobValue -> JsonObject().apply {
            addProperty(
                BackupFormat.BLOB_KEY,
                Base64.getEncoder().encodeToString(value.bytes),
            )
        }
        else -> JsonPrimitive(value.toString())
    }
}

