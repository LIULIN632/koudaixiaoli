package com.pocket.xiaoli.core.weather

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.roundToInt

/**
 * 天气快照（Open-Meteo 响应的一次完整解读）。
 *
 * ## 为什么数据源是 Open-Meteo
 *
 * 它是 GitHub 上的开源项目（github.com/open-meteo/open-meteo），
 * 提供**免费、免 API Key、HTTPS** 的预报接口；数据许可为 CC BY 4.0，
 * **要求署名** —— 所以页脚必须保留「数据来自 Open-Meteo」那一行，不要删。
 *
 * 本项目不引入任何第三方统计 SDK 的前提也成立：请求里只有坐标，
 * 没有设备号、没有账号、没有用户标识。
 *
 * ## 为什么坐标是「烟台市」而不是某个校区
 *
 * `CampusConfig` 里的校区**没有经纬度**，校园地图数据也明确写着
 * 「不要用猜测值填坐标」。所以这一版按**城市级**坐标请求
 *（[WEATHER_LATITUDE] / [WEATHER_LONGITUDE] 取自 Open-Meteo geocoding
 * 对英文名 "Yantai" 的返回：37.47649, 121.44081），页面上也如实写成「烟台」，
 * 不假装是校区实测。将来若拿到校区坐标，只改这两个常量即可。
 *
 * ## 为什么这一份在共享核心里
 *
 * 解析规则、WMO 代码表与展示换算都是纯逻辑，Android 与桌面端必须给出
 * **同一个数字**；两端各抄一遍迟早会在某个档位上分叉。JSON 这层用 Gson
 *（`jvmAndroidMain` 白名单依赖），不依赖 Android SDK 自带的 `org.json`
 * —— 桌面 JVM 里没有它。
 */
data class WeatherSnapshot(
    val current: WeatherCurrent,
    val hourly: List<WeatherHour>,
    val daily: List<WeatherDay>,
    /** 本地落库时刻，用于 30 分钟节流判定（不是观测时刻）。 */
    val fetchedAt: Long,
)

data class WeatherCurrent(
    /** API 的本地观测时刻，形如 `2026-10-22T22:45`（时区已按 Asia/Shanghai 请求）。 */
    val time: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val precipitation: Double,
    val weatherCode: Int,
    val cloudCover: Int,
    val windSpeed: Double,
    val windDirection: Int,
    val isDay: Boolean,
) {
    val kind: WeatherKind get() = weatherKind(weatherCode)
    val label: String get() = weatherLabel(weatherCode)
}

data class WeatherHour(
    val time: String,
    val temperature: Double,
    val weatherCode: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
) {
    /** `2026-10-22T14:00` → `14:00` */
    val clockLabel: String get() = time.substringAfter('T').take(5)
    val kind: WeatherKind get() = weatherKind(weatherCode)
    val label: String get() = weatherLabel(weatherCode)
}

data class WeatherDay(
    val date: String,
    val weatherCode: Int,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val sunrise: String,
    val sunset: String,
    val precipitationProbabilityMax: Int,
    val uvIndexMax: Double,
) {
    val kind: WeatherKind get() = weatherKind(weatherCode)
    val label: String get() = weatherLabel(weatherCode)

    /** `2026-10-22` → `10/22` */
    val shortDate: String
        get() = date.substringAfter('-', "").let { rest ->
            val month = rest.substringBefore('-')
            val day = rest.substringAfter('-')
            if (month.isBlank() || day.isBlank()) date else "$month/$day"
        }
}

/** WMO 天气现象大类 —— 只影响图标与配色，不参与任何判定。 */
enum class WeatherKind {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDER,
}

/**
 * WMO 天气代码 → 大类。
 *
 * 代码表来自 Open-Meteo 文档的 WMO Weather interpretation codes。
 * 未登记的代码归入 [WeatherKind.CLOUDY]（宁可画一朵云，也不要因未知代码崩溃）。
 */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY_CLOUDY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
    95, 96, 99 -> WeatherKind.THUNDER
    else -> WeatherKind.CLOUDY
}

/** WMO 天气代码 → 中文短标签（页面上直接显示的那一档粒度）。 */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "晴间多云"
    2 -> "多云"
    3 -> "阴"
    45 -> "雾"
    48 -> "雾凇"
    51 -> "小毛毛雨"
    53 -> "毛毛雨"
    55 -> "大毛毛雨"
    56, 57 -> "冻毛毛雨"
    61 -> "小雨"
    63 -> "中雨"
    65 -> "大雨"
    66, 67 -> "冻雨"
    71 -> "小雪"
    73 -> "中雪"
    75 -> "大雪"
    77 -> "雪粒"
    80 -> "阵雨"
    81 -> "强阵雨"
    82 -> "暴雨"
    85 -> "阵雪"
    86 -> "强阵雪"
    95 -> "雷阵雨"
    96, 99 -> "雷暴冰雹"
    else -> "多云"
}

/** 风向角度 → 八方位中文名。 */
fun windDirectionLabel(degrees: Int): String {
    val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    val normalized = ((degrees % 360) + 360) % 360
    return dirs[(((normalized + 22.5) / 45.0).toInt()) % 8]
}

/** 风速（km/h）→ 蒲福风级。 */
fun windScale(speedKmh: Double): Int = when {
    speedKmh < 1 -> 0
    speedKmh < 6 -> 1
    speedKmh < 12 -> 2
    speedKmh < 20 -> 3
    speedKmh < 29 -> 4
    speedKmh < 39 -> 5
    speedKmh < 50 -> 6
    speedKmh < 62 -> 7
    speedKmh < 75 -> 8
    speedKmh < 89 -> 9
    speedKmh < 103 -> 10
    speedKmh < 118 -> 11
    else -> 12
}

/** 紫外线指数 → 中文强度（WHO 分档）。 */
fun uvLabel(index: Double): String = when {
    index < 3 -> "弱"
    index < 6 -> "中等"
    index < 8 -> "强"
    index < 11 -> "很强"
    else -> "极强"
}

/** 温度取整时的中文显示（不带单位，单位由调用方拼）。 */
fun formatTemperature(value: Double): String = value.roundToInt().toString()

/**
 * 7 天列表的星期标签：接口固定从今天开始给 7 天，所以前两条用「今天/明天」，
 * 之后按日期算星期。坏日期退回原文，不让一个格式问题崩掉整页。
 *
 * 公开（而不是 `internal`）：天气页直接用它排版 7 天列表，
 * 逻辑归核心、排版归界面 —— 别让每个端再抄一份星期表。
 */
fun dayLabel(index: Int, date: String): String = when (index) {
    0 -> "今天"
    1 -> "明天"
    else -> runCatching {
        val week = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        week[java.time.LocalDate.parse(date).dayOfWeek.value - 1]
    }.getOrDefault(date)
}

/**
 * 解析 Open-Meteo 的 forecast 响应。
 *
 * 只依赖请求里的字段；遇到结构性损坏时抛异常，由仓储层翻译成一句人话
 *（而不是拿 0 当下雨、拿 0 当晴天地展示）—— 天气这类数据「看起来有值」
 * 比「明确说失败」更容易骗到人。
 *
 * @param now 写入缓存的时刻；由仓储层注入，测试传固定值。
 */
fun parseWeather(json: String, now: Long): WeatherSnapshot {
    val root = JsonParser.parseString(json).asJsonObjectOrThrow("根对象")
    val currentJson = root.requiredObject("current")
    val hourlyJson = root.requiredObject("hourly")
    val dailyJson = root.requiredObject("daily")

    val current = WeatherCurrent(
        time = currentJson.requiredString("time"),
        temperature = currentJson.requiredDouble("temperature_2m"),
        apparentTemperature = currentJson.requiredDouble("apparent_temperature"),
        humidity = currentJson.requiredInt("relative_humidity_2m"),
        precipitation = currentJson.optDouble("precipitation", 0.0),
        weatherCode = currentJson.requiredInt("weather_code"),
        cloudCover = currentJson.optInt("cloud_cover", 0),
        windSpeed = currentJson.optDouble("wind_speed_10m", 0.0),
        windDirection = currentJson.optInt("wind_direction_10m", 0),
        isDay = currentJson.optInt("is_day", 1) == 1,
    )

    val hours = parseHourly(hourlyJson, current.time)
    require(hours.isNotEmpty()) { "hourly 为空" }

    val days = parseDaily(dailyJson)
    require(days.isNotEmpty()) { "daily 为空" }

    return WeatherSnapshot(
        current = current,
        hourly = hours,
        daily = days,
        fetchedAt = now,
    )
}

/**
 * 小时预报只保留「从现在起 24 小时」。
 *
 * 接口从当天 00:00 开始给 168 条（7 天 × 24）。若原样展示，
 * 用户最想看的"接下来几小时"会被塞在横向滚动的中段。
 *
 * ⚠️ 不能用 `System.currentTimeMillis()` 去比较：API 的时刻是**请求时区**的
 * 本地字符串，与手机时区无关。这里直接与 `current.time` 做字符串比较
 *（两侧同为 `YYYY-MM-DDTHH:mm`，字典序即时间序），时区天然一致。
 */
private fun parseHourly(json: JsonObject, currentTime: String): List<WeatherHour> {
    val times = json.requiredArray("time")
    val temps = json.requiredArray("temperature_2m")
    val codes = json.requiredArray("weather_code")
    val probs = json.optionalArray("precipitation_probability")
    val isDay = json.optionalArray("is_day")

    val length = minOf(times.size(), temps.size(), codes.size())
    val start = (0 until length).firstOrNull { times.requiredStringAt(it, "time") >= currentTime } ?: 0
    val end = minOf(length, start + HOURLY_COUNT)

    return (start until end).map { i ->
        WeatherHour(
            time = times.requiredStringAt(i, "time"),
            temperature = temps.requiredDoubleAt(i, "temperature_2m"),
            weatherCode = codes.requiredIntAt(i, "weather_code"),
            precipitationProbability = probs.optIntAt(i, 0),
            isDay = isDay.optIntAt(i, 1) != 0,
        )
    }
}

private fun parseDaily(json: JsonObject): List<WeatherDay> {
    val dates = json.requiredArray("time")
    val codes = json.requiredArray("weather_code")
    val maxes = json.requiredArray("temperature_2m_max")
    val mins = json.requiredArray("temperature_2m_min")
    val sunrises = json.optionalArray("sunrise")
    val sunsets = json.optionalArray("sunset")
    val probs = json.optionalArray("precipitation_probability_max")
    val uvs = json.optionalArray("uv_index_max")

    val length = minOf(dates.size(), codes.size(), maxes.size(), mins.size())

    return (0 until length).map { i ->
        WeatherDay(
            date = dates.requiredStringAt(i, "time"),
            weatherCode = codes.requiredIntAt(i, "weather_code"),
            temperatureMax = maxes.requiredDoubleAt(i, "temperature_2m_max"),
            temperatureMin = mins.requiredDoubleAt(i, "temperature_2m_min"),
            sunrise = sunrises.optStringAt(i),
            sunset = sunsets.optStringAt(i),
            precipitationProbabilityMax = probs.optIntAt(i, 0),
            uvIndexMax = uvs.optDoubleAt(i, 0.0),
        )
    }
}

/*
 * ── Gson 取值的两条纪律（对应原来 org.json 的 getXxx / optXxx）──
 *
 * 1) `required*`：字段缺失、是 JSON null、或类型不对 → 抛异常。
 *    这是「结构损坏宁可报错，也不要拿 0 冒充晴天」这条设计前提的落点，
 *    少了它，页面会把解析失败粉饰成「晴 0℃」。
 * 2) `opt*`：字段缺失 / 是 JSON null / 下标越界 → 用调用方给的默认值。
 *    这些是装饰性字段（云量、风向、可选数组），接口并不保证每次都带；
 *    为它们整页报错反而更糟。
 */

private fun JsonObject.requiredObject(name: String): JsonObject =
    member(name).asJsonObjectOrThrow(name)

private fun JsonObject.requiredArray(name: String): JsonArray =
    member(name).asJsonArrayOrThrow(name)

/** 可选数组：键不存在、或不是数组时返回 null（原来用的是 optJSONArray）。 */
private fun JsonObject.optionalArray(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun JsonObject.requiredString(name: String): String = member(name).asStringOrThrow(name)

private fun JsonObject.requiredDouble(name: String): Double = member(name).asDoubleOrThrow(name)

private fun JsonObject.requiredInt(name: String): Int = member(name).asIntOrThrow(name)

private fun JsonObject.optInt(name: String, fallback: Int): Int =
    runCatching { get(name)?.let { it.asIntOrThrow(name) } ?: fallback }.getOrDefault(fallback)

private fun JsonObject.optDouble(name: String, fallback: Double): Double =
    runCatching { get(name)?.let { it.asDoubleOrThrow(name) } ?: fallback }.getOrDefault(fallback)

private fun JsonObject.member(name: String): JsonElement {
    val element = get(name)
    require(element != null && !element.isJsonNull) { "缺少字段 $name" }
    return element
}

private fun JsonElement.asJsonObjectOrThrow(name: String): JsonObject {
    require(isJsonObject) { "字段 $name 不是对象" }
    return asJsonObject
}

private fun JsonElement.asJsonArrayOrThrow(name: String): JsonArray {
    require(isJsonArray) { "字段 $name 不是数组" }
    return asJsonArray
}

private fun JsonElement.asStringOrThrow(name: String): String {
    require(isJsonPrimitive) { "字段 $name 不是字符串" }
    return asString
}

private fun JsonElement.asDoubleOrThrow(name: String): Double {
    require(isJsonPrimitive) { "字段 $name 不是数值" }
    return runCatching { asDouble }
        .getOrElse { throw IllegalArgumentException("字段 $name 不是数值：$this") }
}

/**
 * 与 org.json 的 `getInt` 一致：整数值才收（`67.5` 报错，不静默截断成 67）。
 *
 * 天气代码、湿度、风向角都是整数语义 —— 悄悄截断会让「代码 2」变成
 * 「代码 2.x」这类看不出来的偏移，宁可让它走异常分支。
 */
private fun JsonElement.asIntOrThrow(name: String): Int {
    val value = asDoubleOrThrow(name)
    val exact = value.toInt()
    require(exact.toDouble() == value) { "字段 $name 不是整数：$value" }
    return exact
}

private fun JsonArray.requiredStringAt(index: Int, name: String): String =
    elementAtOrThrow(index, name).asStringOrThrow(name)

private fun JsonArray.requiredDoubleAt(index: Int, name: String): Double =
    elementAtOrThrow(index, name).asDoubleOrThrow(name)

private fun JsonArray.requiredIntAt(index: Int, name: String): Int =
    elementAtOrThrow(index, name).asIntOrThrow(name)

private fun JsonArray.elementAtOrThrow(index: Int, name: String): JsonElement {
    require(index in 0 until size()) { "数组 $name 越界：index=$index size=${size()}" }
    val element = get(index)
    require(!element.isJsonNull) { "数组 $name 的第 $index 项是 null" }
    return element
}

private fun JsonArray?.optIntAt(index: Int, fallback: Int): Int =
    runCatching { this?.elementAtOrThrow(index, "array")?.asIntOrThrow("array") ?: fallback }
        .getOrDefault(fallback)

private fun JsonArray?.optDoubleAt(index: Int, fallback: Double): Double =
    runCatching { this?.elementAtOrThrow(index, "array")?.asDoubleOrThrow("array") ?: fallback }
        .getOrDefault(fallback)

/** 缺失 / null / 越界都得到空串（原来是 `optString(i).orEmpty()`）。 */
private fun JsonArray?.optStringAt(index: Int): String =
    runCatching { this?.get(index)?.let { if (it.isJsonNull) "" else it.asStringOrThrow("array") } ?: "" }
        .getOrDefault("")

/** 请求的坐标：烟台市（城市级，来源见 [WeatherSnapshot] 的 KDoc）。 */
const val WEATHER_LATITUDE = 37.47649

const val WEATHER_LONGITUDE = 121.44081

/** 一次请求的小时条数上限。 */
private const val HOURLY_COUNT = 24
