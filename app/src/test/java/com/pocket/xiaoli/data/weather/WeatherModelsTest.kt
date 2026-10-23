package com.pocket.xiaoli.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天气解析与 WMO 映射的纯函数单测。
 *
 * 这两层最容易"看起来对"：代码表错一位，页面只是把小雨画成雪，
 * 不崩、不报错，只有拿真实响应逐字段比对才发现。所以这里全部用
 * **Open-Meteo 真实响应裁剪出来的报文**，而不是随手编的 JSON。
 */
class WeatherModelsTest {

    @Test
    fun `WMO 代码映射到八个大类`() {
        assertEquals(WeatherKind.CLEAR, weatherKind(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, weatherKind(1))
        assertEquals(WeatherKind.PARTLY_CLOUDY, weatherKind(2))
        assertEquals(WeatherKind.CLOUDY, weatherKind(3))
        assertEquals(WeatherKind.FOG, weatherKind(45))
        assertEquals(WeatherKind.FOG, weatherKind(48))
        assertEquals(WeatherKind.DRIZZLE, weatherKind(51))
        assertEquals(WeatherKind.DRIZZLE, weatherKind(57))
        assertEquals(WeatherKind.RAIN, weatherKind(61))
        assertEquals(WeatherKind.RAIN, weatherKind(82))
        assertEquals(WeatherKind.SNOW, weatherKind(71))
        assertEquals(WeatherKind.SNOW, weatherKind(86))
        assertEquals(WeatherKind.THUNDER, weatherKind(95))
        assertEquals(WeatherKind.THUNDER, weatherKind(99))
        // 未登记代码不能当成"晴"（0 是晴），否则一个未知天气会被画成大太阳。
        assertEquals(WeatherKind.CLOUDY, weatherKind(42))
    }

    @Test
    fun `天气中文标签覆盖常见代码`() {
        assertEquals("晴", weatherLabel(0))
        assertEquals("多云", weatherLabel(2))
        assertEquals("阴", weatherLabel(3))
        assertEquals("雾", weatherLabel(45))
        assertEquals("小雨", weatherLabel(61))
        assertEquals("阵雨", weatherLabel(80))
        assertEquals("小雪", weatherLabel(71))
        assertEquals("雷阵雨", weatherLabel(95))
        assertEquals("多云", weatherLabel(-1))
    }

    @Test
    fun `风向按八方位取最近的一档`() {
        assertEquals("北", windDirectionLabel(0))
        assertEquals("东北", windDirectionLabel(45))
        assertEquals("东", windDirectionLabel(90))
        assertEquals("东南", windDirectionLabel(135))
        assertEquals("南", windDirectionLabel(180))
        assertEquals("西南", windDirectionLabel(225))
        assertEquals("西", windDirectionLabel(270))
        assertEquals("西北", windDirectionLabel(315))
        // 边界与环绕：350° 仍是北；-90° 等价于 270° 西。
        assertEquals("北", windDirectionLabel(350))
        assertEquals("西", windDirectionLabel(-90))
    }

    @Test
    fun `风速换算蒲福风级`() {
        assertEquals(0, windScale(0.5))
        assertEquals(1, windScale(5.9))
        assertEquals(2, windScale(8.0))
        assertEquals(3, windScale(15.0))
        assertEquals(4, windScale(25.0))
        assertEquals(12, windScale(150.0))
    }

    @Test
    fun `紫外线分档与 WHO 一致`() {
        assertEquals("弱", uvLabel(0.0))
        assertEquals("中等", uvLabel(3.0))
        assertEquals("强", uvLabel(6.0))
        assertEquals("很强", uvLabel(8.0))
        assertEquals("极强", uvLabel(11.0))
    }

    @Test
    fun `解析真实响应形状`() {
        val snapshot = parseWeather(SAMPLE_JSON, now = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, snapshot.fetchedAt)
        assertEquals(37.47649, WEATHER_LATITUDE, 0.00001)
        assertEquals(121.44081, WEATHER_LONGITUDE, 0.00001)

        val current = snapshot.current
        assertEquals("2026-10-22T22:45", current.time)
        assertEquals(12.3, current.temperature, 0.001)
        assertEquals(9.9, current.apparentTemperature, 0.001)
        assertEquals(67, current.humidity)
        assertEquals(1, current.weatherCode)
        assertEquals("晴间多云", current.label)
        assertEquals(WeatherKind.PARTLY_CLOUDY, current.kind)
        assertTrue(!current.isDay)
        assertEquals(24, current.cloudCover)
        assertEquals(9.4, current.windSpeed, 0.001)
        assertEquals(167, current.windDirection)

        // 当前时刻是 22:45，22:00 那条已经过去，24 小时条应从 23:00 开始。
        assertEquals(2, snapshot.hourly.size)
        assertEquals("23:00", snapshot.hourly.first().clockLabel)
        assertEquals("2026-10-23T00:00", snapshot.hourly.last().time)
        assertEquals(10, snapshot.hourly.first().precipitationProbability)

        assertEquals(2, snapshot.daily.size)
        val today = snapshot.daily.first()
        assertEquals("2026-10-22", today.date)
        assertEquals("10/22", today.shortDate)
        assertEquals(16.9, today.temperatureMax, 0.001)
        assertEquals(7.8, today.temperatureMin, 0.001)
        assertEquals("2026-10-22T06:08", today.sunrise)
        assertEquals("2026-10-22T17:08", today.sunset)
        assertEquals(4.45, today.uvIndexMax, 0.001)
        assertEquals("阴", today.label)
    }

    @Test
    fun `小时条只保留从当前时刻起的 24 条`() {
        // 构造 30 条：前 3 条早于当前时刻，应被裁掉。
        val json = hourlyJson(startHour = 20, count = 30, currentTime = "2026-10-22T22:45")
        val snapshot = parseWeather(json, now = 1L)
        assertEquals(24, snapshot.hourly.size)
        assertEquals("23:00", snapshot.hourly.first().clockLabel)
    }

    @Test
    fun `结构损坏时抛异常而不是拿 0 冒充晴`() {
        assertThrows(RuntimeException::class.java) { parseWeather("{}", 1L) }
        assertThrows(RuntimeException::class.java) {
            parseWeather("""{"current":{"time":"2026-10-22T22:45","temperature_2m":1}}""", 1L)
        }
    }

    private fun hourlyJson(startHour: Int, count: Int, currentTime: String): String {
        val times = (0 until count).joinToString(",") { i ->
            val hour = (startHour + i) % 24
            val day = if (startHour + i >= 24) "2026-10-23" else "2026-10-22"
            "\"${day}T${hour.toString().padStart(2, '0')}:00\""
        }
        val temps = (0 until count).joinToString(",") { "1$it" }
        val codes = (0 until count).joinToString(",") { "1" }
        val probs = (0 until count).joinToString(",") { "0" }
        val isDay = (0 until count).joinToString(",") { "0" }
        return """
            {"current":{"time":"$currentTime","temperature_2m":1,"apparent_temperature":1,
            "relative_humidity_2m":1,"weather_code":1},
            "hourly":{"time":[$times],"temperature_2m":[$temps],"weather_code":[$codes],
            "precipitation_probability":[$probs],"is_day":[$isDay]},
            "daily":{"time":["2026-10-22"],"weather_code":[1],"temperature_2m_max":[2],
            "temperature_2m_min":[1],"precipitation_probability_max":[0],"uv_index_max":[1]}}
        """.trimIndent()
    }

    @Test
    fun `日期标签今天明天之后按星期`() {
        assertEquals("今天", dayLabel(index = 0, date = "2026-10-22"))
        assertEquals("明天", dayLabel(index = 1, date = "2026-10-23"))
        // 2026-10-24 是周六。
        assertEquals("周六", dayLabel(index = 2, date = "2026-10-24"))
        assertEquals("周日", dayLabel(index = 3, date = "2026-10-25"))
        // 坏日期不能崩，退回原始字符串。
        assertEquals("bad", dayLabel(index = 4, date = "bad"))
    }

    @Test
    fun `温度取整用于展示`() {
        assertEquals("12", formatTemperature(12.3))
        assertEquals("13", formatTemperature(12.6))
        assertEquals("-3", formatTemperature(-3.2))
    }

    companion object {
        /** Open-Meteo forecast 真实响应裁剪（2026-10-22 实拉，字段未改动）。 */
        private val SAMPLE_JSON = """
            {
              "latitude": 37.50439,
              "longitude": 121.39813,
              "timezone": "Asia/Shanghai",
              "current": {
                "time": "2026-10-22T22:45",
                "interval": 900,
                "temperature_2m": 12.3,
                "relative_humidity_2m": 67,
                "apparent_temperature": 9.9,
                "is_day": 0,
                "precipitation": 0.0,
                "weather_code": 1,
                "cloud_cover": 24,
                "wind_speed_10m": 9.4,
                "wind_direction_10m": 167
              },
              "hourly": {
                "time": ["2026-10-22T22:00", "2026-10-22T23:00", "2026-10-23T00:00"],
                "temperature_2m": [12.1, 11.8, 11.5],
                "weather_code": [1, 1, 2],
                "precipitation_probability": [0, 10, 20],
                "is_day": [0, 0, 0]
              },
              "daily": {
                "time": ["2026-10-22", "2026-10-23"],
                "weather_code": [3, 1],
                "temperature_2m_max": [16.9, 17.7],
                "temperature_2m_min": [7.8, 10.7],
                "sunrise": ["2026-10-22T06:08", "2026-10-23T06:09"],
                "sunset": ["2026-10-22T17:08", "2026-10-23T17:07"],
                "precipitation_probability_max": [0, 20],
                "uv_index_max": [4.45, 3.35]
              }
            }
        """.trimIndent()
    }
}
