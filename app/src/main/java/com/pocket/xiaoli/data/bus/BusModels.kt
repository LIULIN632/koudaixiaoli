package com.pocket.xiaoli.data.bus

import com.pocket.xiaoli.core.bus.BusArrival
import com.pocket.xiaoli.core.bus.BusArrivalRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一个公交站（附近站列表里的一项）。
 *
 * [distanceMeters] 是**到本校锚点的直线距离**，不是步行距离 ——
 * 上游 CMD=106 给的就是直线 `dis`，界面文案必须跟着说「直线」，
 * 说「步行 X 米」是在给用户一个我们没算过的数。
 */
data class BusStation(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val distanceMeters: Int = -1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("lat", lat)
        .put("lng", lng)
        .put("distanceMeters", distanceMeters)

    companion object {
        fun fromJson(o: JSONObject): BusStation = BusStation(
            name = o.optString("name"),
            lat = o.optDouble("lat", 0.0),
            lng = o.optDouble("lng", 0.0),
            distanceMeters = o.optInt("distanceMeters", -1),
        )
    }
}

/**
 * 某站某方向的一条线路，以及上游当时报的那一班车的状态。
 *
 * ## 为什么存的是上游原始字段，而不是解析好的 [BusArrival]
 *
 * 这样「缓存 → 内存 → 界面」走的是同一条解析路径
 * （[BusArrivalRules.parse]），离线时读上次缓存也不会绕过判据。
 * 上游字段缺失一律取默认值，读缓存遇到旧格式不至于炸 ——
 * 而到站档位是**每次访问现算**的，所以缓存里不可能存进一个过期的倒计时。
 *
 * 文案字段拿不到就是空串，界面据此不画那一段；**不要用「—」之类的占位符**，
 * 那会被读成「有值但不知道是多少」。
 */
data class BusLine(
    val lineName: String = "",
    /** 1 = 上行，2 = 下行（上游 `upperOrDown`）。 */
    val direction: Int = 1,
    val from: String = "",
    val to: String = "",
    /** 本方向首班，如 `05:30`。 */
    val firstDeparture: String = "",
    /** 本方向末班，如 `21:30`。 */
    val lastDeparture: String = "",
    /** 票价原文（已剥掉「票价:」前缀），如 `1元`。 */
    val fare: String = "",
    val nearText: String = "",
    val nearNum: Int = 0,
    val hasRealtime: Boolean = false,
    /** 0 = 正常运营；非 0 由上游标停运/异常。 */
    val runState: Int = 0,
    /** 第二班的原文（上游 `nexttext`），实测多数线路为空；非空才画。 */
    val nextBusText: String = "",
) {
    /** 到站状态 —— 判据只在 core 那一份，界面不重复实现。 */
    val arrival: BusArrival get() = BusArrivalRules.parse(nearText, nearNum, hasRealtime, runState)

    val directionText: String get() = BusArrivalRules.directionText(from, to)

    /** `05:30-21:30`；任一端缺失返回空串。 */
    val firstLastText: String
        get() = if (firstDeparture.isNotBlank() && lastDeparture.isNotBlank()) {
            "$firstDeparture-$lastDeparture"
        } else {
            ""
        }

    /**
     * 元信息一行：首末班 · 票价。
     *
     * 只列这两样是有取舍的：上游还给运营公司（`烟台市公交集团高新区有限公司`），
     * 但那是九条线路里八条都一样的噪音，等车时用户要的是「几点收车、带不带零钱」。
     */
    val metaText: String
        get() = listOfNotNull(
            firstLastText.takeIf { it.isNotBlank() },
            fare.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    fun toJson(): JSONObject = JSONObject()
        .put("lineName", lineName)
        .put("direction", direction)
        .put("from", from)
        .put("to", to)
        .put("firstDeparture", firstDeparture)
        .put("lastDeparture", lastDeparture)
        .put("fare", fare)
        .put("nearText", nearText)
        .put("nearNum", nearNum)
        .put("hasRealtime", hasRealtime)
        .put("runState", runState)
        .put("nextBusText", nextBusText)

    companion object {
        fun fromJson(o: JSONObject): BusLine = BusLine(
            lineName = o.optString("lineName"),
            direction = o.optInt("direction", 1),
            from = o.optString("from"),
            to = o.optString("to"),
            firstDeparture = o.optString("firstDeparture"),
            lastDeparture = o.optString("lastDeparture"),
            fare = o.optString("fare"),
            nearText = o.optString("nearText"),
            nearNum = o.optInt("nearNum", 0),
            hasRealtime = o.optBoolean("hasRealtime", false),
            runState = o.optInt("runState", 0),
            nextBusText = o.optString("nextBusText"),
        )
    }
}

/** 缓存/解析用的容器：一个站 + 它的若干条线路 + 抓取时间。 */
data class BusBoard(
    val stationName: String = "",
    val lines: List<BusLine> = emptyList(),
    val stations: List<BusStation> = emptyList(),
    /** 上游抓取时刻（毫秒）；0 = 还没有过成功抓取。 */
    val capturedAtMillis: Long = 0L,
    /** 这份板子是刚拉的（true）还是上次缓存的（false）。 */
    val fromNetwork: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("stationName", stationName)
        .put("capturedAtMillis", capturedAtMillis)
        .put("lines", JSONArray().apply { lines.forEach { put(it.toJson()) } })
        .put("stations", JSONArray().apply { stations.forEach { put(it.toJson()) } })

    companion object {
        val EMPTY = BusBoard()

        fun fromJson(o: JSONObject): BusBoard = BusBoard(
            stationName = o.optString("stationName"),
            lines = o.optJSONArray("lines").orEmptyList().map(BusLine::fromJson),
            stations = o.optJSONArray("stations").orEmptyList().map(BusStation::fromJson),
            capturedAtMillis = o.optLong("capturedAtMillis", 0L),
        )

        private fun JSONArray?.orEmptyList(): List<JSONObject> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { optJSONObject(it) }
        }
    }
}
