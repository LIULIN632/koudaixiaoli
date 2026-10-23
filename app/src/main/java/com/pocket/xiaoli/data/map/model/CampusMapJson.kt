package com.pocket.xiaoli.data.map.model

import com.google.gson.Gson

/** 校园地图 JSON 序列化：先支持单文件 `CampusMapData`，后续再加 GeoJSON 转换。 */
object CampusMapJson {
    private val gson = Gson()

    fun parse(json: String): CampusMapData =
        runCatching { gson.fromJson(json, CampusMapData::class.java) }
            .getOrNull() ?: CampusMapData()

    fun toJson(data: CampusMapData): String = gson.toJson(data)
}