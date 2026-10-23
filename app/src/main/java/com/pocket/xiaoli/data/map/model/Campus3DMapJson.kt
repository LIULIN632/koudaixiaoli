package com.pocket.xiaoli.data.map.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 3D 沙盘 JSON 解析。
 *
 * 用手写字段映射而不是 Gson 反射：Gson 会绕过 Kotlin 构造器，
 * 缺字段时非空类型会变成 null，手写映射可以稳定落到默认值。
 */
object Campus3DMapJson {

    fun parse(json: String): Campus3DScene =
        runCatching { parseScene(JsonParser.parseString(json).asJsonObject) }
            .getOrNull() ?: Campus3DScene()

    private fun parseScene(root: JsonObject): Campus3DScene = Campus3DScene(
        schemaVersion = root.int("schemaVersion", 1),
        campusId = root.string("campusId"),
        name = root.string("name"),
        generatedAt = root.string("generatedAt"),
        attribution = root.string("attribution"),
        sourceUrl = root.string("sourceUrl"),
        center = root.obj("center")?.point(),
        bounds = root.obj("bounds")?.let {
            Campus3DBounds(
                minLat = it.double("minLat"),
                maxLat = it.double("maxLat"),
                minLng = it.double("minLng"),
                maxLng = it.double("maxLng"),
            )
        },
        boundary = root.points("boundary"),
        buildings = root.objects("buildings").map(::parseBuilding),
        water = root.objects("water").map(::parseWater),
        green = root.objects("green").map(::parseGreen),
        pitches = root.objects("pitches").map(::parsePitch),
        paths = root.objects("paths").map(::parsePath),
        zones = root.objects("zones").map(::parseZone),
        pois = root.objects("pois").map(::parsePoi),
    )

    private fun parseBuilding(o: JsonObject) = Campus3DBuilding(
        id = o.string("id"),
        name = o.string("name"),
        sourceName = o.string("sourceName"),
        category = o.string("category"),
        levels = o.int("levels", 1),
        levelsEstimated = o.bool("levelsEstimated"),
        heightMeters = o.double("heightMeters"),
        outline = o.points("outline"),
        holes = o.pointLists("holes"),
        note = o.string("note"),
    )

    private fun parseWater(o: JsonObject) = Campus3DWater(
        id = o.string("id"),
        name = o.string("name"),
        outline = o.points("outline"),
        label = o.obj("label")?.point(),
    )

    private fun parseGreen(o: JsonObject) = Campus3DGreen(
        id = o.string("id"),
        kind = o.string("kind"),
        outline = o.points("outline"),
    )

    private fun parsePitch(o: JsonObject) = Campus3DPitch(
        id = o.string("id"),
        name = o.string("name"),
        kind = o.string("kind"),
        sport = o.string("sport"),
        outline = o.points("outline"),
        label = o.obj("label")?.point(),
    )

    private fun parsePath(o: JsonObject) = Campus3DPath(
        id = o.string("id"),
        points = o.points("points"),
    )

    private fun parseZone(o: JsonObject) = Campus3DZone(
        name = o.string("name"),
        kind = o.string("kind"),
        lat = o.double("lat"),
        lng = o.double("lng"),
    )

    private fun parsePoi(o: JsonObject) = Campus3DPoi(
        id = o.string("id"),
        name = o.string("name"),
        category = o.string("category"),
        lat = o.double("lat"),
        lng = o.double("lng"),
        note = o.string("note"),
    )

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

    private fun JsonObject.int(name: String, fallback: Int): Int =
        number(name)?.toInt() ?: fallback

    private fun JsonObject.double(name: String, fallback: Double = 0.0): Double =
        number(name) ?: fallback

    private fun JsonObject.number(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    private fun JsonObject.bool(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    private fun JsonObject.obj(name: String): JsonObject? =
        (get(name) as? JsonObject)

    private fun JsonObject.objects(name: String): List<JsonObject> =
        (get(name) as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    private fun JsonObject.points(name: String): List<Campus3DPoint> =
        objects(name).mapNotNull { it.pointOrNull() }

    private fun JsonObject.pointLists(name: String): List<List<Campus3DPoint>> =
        (get(name) as? JsonArray)?.mapNotNull { element ->
            (element as? JsonArray)?.mapNotNull { it.pointOrNull() }
                ?.takeIf { it.isNotEmpty() }
        } ?: emptyList()

    private fun JsonObject.point(): Campus3DPoint? = pointOrNull()

    private fun JsonElement.pointOrNull(): Campus3DPoint? {
        val o = this as? JsonObject ?: return null
        val lat = o.number("lat") ?: return null
        val lng = o.number("lng") ?: return null
        return Campus3DPoint(lat = lat, lng = lng)
    }
}
