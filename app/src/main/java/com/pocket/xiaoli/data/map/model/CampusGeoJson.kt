package com.pocket.xiaoli.data.map.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** GeoJSON 解析后的通用要素，先不依赖完整 GIS 模型。 */
data class CampusGeoFeature(
    val id: String,
    val name: String,
    val kind: String,
    val geometryType: String,
    val points: List<CampusLatLng>,
)

/**
 * 极简 GeoJSON 解析器。
 *
 * 覆盖当前校园地图需要的：
 * - Point：POI / 入口
 * - LineString：道路
 * - Polygon / MultiPolygon：建筑轮廓（取外环）
 *
 * 不做完整 GeoJSON 规范校验；解析失败返回空列表，由上层决定兜底。
 */
object CampusGeoJson {

    fun parseFeatureCollection(json: String): List<CampusGeoFeature> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return emptyList()
        val features = root.getAsJsonArray("features") ?: return emptyList()
        return features.mapNotNull { element ->
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            parseFeature(obj)
        }
    }

    fun toRoads(json: String): List<CampusRoad> =
        parseFeatureCollection(json)
            .filter { it.geometryType == "LineString" && it.points.size >= 2 }
            .map { CampusRoad(id = it.id, name = it.name, kind = it.kind, points = it.points) }

    fun toPlaces(json: String): List<CampusMapPlace> =
        parseFeatureCollection(json)
            .filter { it.geometryType == "Point" && it.points.isNotEmpty() }
            .map {
                CampusMapPlace(
                    id = it.id,
                    name = it.name,
                    kind = it.kind,
                    lat = it.points.first().lat,
                    lng = it.points.first().lng,
                )
            }

    private fun parseFeature(feature: JsonObject): CampusGeoFeature? {
        val geometry = feature.getAsJsonObject("geometry") ?: return null
        val type = geometry.get("type")?.asString ?: return null
        val coordinates = geometry.getAsJsonArray("coordinates") ?: return null
        val points = parseCoordinates(type, coordinates)
        if (points.isEmpty()) return null

        val properties = feature.getAsJsonObject("properties")
        val id = properties?.get("id")?.asString
            ?: feature.get("id")?.asString
            ?: "geo_${type.lowercase()}_${points.first().lat}_${points.first().lng}"
        val name = properties?.get("name")?.asString.orEmpty()
        val kind = properties?.get("kind")?.asString
            ?: properties?.get("category")?.asString
            ?: properties?.get("type")?.asString
            ?: type.lowercase()

        return CampusGeoFeature(
            id = id,
            name = name,
            kind = kind,
            geometryType = type,
            points = points,
        )
    }

    private fun parseCoordinates(type: String, coordinates: JsonArray): List<CampusLatLng> = when (type) {
        "Point" -> listOfNotNull(parsePosition(coordinates))
        "LineString", "MultiPoint" -> coordinates.mapNotNull { parsePosition(it) }
        "Polygon", "MultiLineString" -> coordinates.firstOrNull()
            ?.asJsonArray
            ?.mapNotNull { parsePosition(it) }
            .orEmpty()
        "MultiPolygon" -> coordinates.firstOrNull()
            ?.asJsonArray
            ?.firstOrNull()
            ?.asJsonArray
            ?.mapNotNull { parsePosition(it) }
            .orEmpty()
        else -> emptyList()
    }

    private fun parsePosition(value: Any?): CampusLatLng? {
        val array = runCatching { (value as JsonArray) }.getOrNull() ?: return null
        if (array.size() < 2) return null
        val lng = runCatching { array.get(0).asDouble }.getOrNull() ?: return null
        val lat = runCatching { array.get(1).asDouble }.getOrNull() ?: return null
        return CampusLatLng(lat = lat, lng = lng)
    }
}