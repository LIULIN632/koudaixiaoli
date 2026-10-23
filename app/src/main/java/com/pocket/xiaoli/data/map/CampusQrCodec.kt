package com.pocket.xiaoli.data.map

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class CampusQrLocation(
    val campusId: String,
    val buildingId: String,
    val floorId: String? = null,
    val nodeId: String? = null,
)

/** 位置二维码格式：pocketxiaoli://campus-location?v=1&campus=...&building=... */
object CampusQrCodec {

    const val SCHEME = "pocketxiaoli"
    const val HOST = "campus-location"

    fun encode(location: CampusQrLocation): String {
        val query = buildList {
            add("v=1")
            add("campus=" + urlEncode(location.campusId))
            add("building=" + urlEncode(location.buildingId))
            location.floorId?.takeIf { it.isNotBlank() }?.let { add("floor=" + urlEncode(it)) }
            location.nodeId?.takeIf { it.isNotBlank() }?.let { add("node=" + urlEncode(it)) }
        }.joinToString("&")
        return "$SCHEME://$HOST?$query"
    }

    fun decode(raw: String): CampusQrLocation? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null

        val params = uri.query
            ?.split("&")
            ?.mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                urlDecode(part.substring(0, index)) to urlDecode(part.substring(index + 1))
            }
            ?.toMap()
            .orEmpty()

        if (params["v"] != "1") return null
        val campusId = params["campus"]?.takeIf { it.isNotBlank() } ?: return null
        val buildingId = params["building"]?.takeIf { it.isNotBlank() } ?: return null
        return CampusQrLocation(
            campusId = campusId,
            buildingId = buildingId,
            floorId = params["floor"]?.takeIf { it.isNotBlank() },
            nodeId = params["node"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun urlDecode(value: String): String = URLDecoder.decode(value, "UTF-8")
}