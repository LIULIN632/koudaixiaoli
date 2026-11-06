package com.pocket.xiaoli.core.weather

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 可选字段的降级路径。
 *
 * 搬进共享核心时唯一没有测试覆盖的分支就是这里：Open-Meteo 的装饰性数组
 * （降水概率、is_day、日出日落、UV）并不保证每次都带，也可能整段是 null。
 * 为它们整页报错比给默认值更糟，所以行为必须是「照常出图，缺的按默认」。
 */
class WeatherOptionalFieldsTest {

    private fun body(withOptional: Boolean, nullOut: Boolean = false): String {
        val hourlyOptional = if (!withOptional) "" else if (nullOut) """,
            "precipitation_probability":[null,null],"is_day":[null,null]""" else """,
            "precipitation_probability":[70,80],"is_day":[0,1]"""
        val dailyOptional = if (!withOptional) "" else if (nullOut) """,
            "sunrise":[null],"sunset":[null],"precipitation_probability_max":[null],"uv_index_max":[null]""" else """,
            "sunrise":["2026-10-22T06:20"],"sunset":["2026-10-22T17:30"],
            "precipitation_probability_max":[65],"uv_index_max":[3.4]"""
        return """
            {"current":{"time":"2026-10-22T21:00","temperature_2m":12.5,"apparent_temperature":11,
            "relative_humidity_2m":60,"weather_code":61,"cloud_cover":80,"wind_speed_10m":9.4,
            "wind_direction_10m":315},
            "hourly":{"time":["2026-10-22T22:00","2026-10-22T23:00"],
            "temperature_2m":[12.5,12.0],"weather_code":[61,61]$hourlyOptional},
            "daily":{"time":["2026-10-22"],"weather_code":[61],
            "temperature_2m_max":[15.0],"temperature_2m_min":[8.0]$dailyOptional}}
        """.trimIndent()
    }

    @Test
    fun `可选数组整段缺失时照常解析`() {
        val snapshot = parseWeather(body(withOptional = false), now = 1L)

        assertEquals(2, snapshot.hourly.size)
        // 缺降水概率按 0%，缺 is_day 按"是白天"（原实现用的是 optInt 默认 1）
        assertEquals(0, snapshot.hourly.first().precipitationProbability)
        assertEquals(true, snapshot.hourly.first().isDay)

        val day = snapshot.daily.first()
        assertEquals("", day.sunrise)
        assertEquals("", day.sunset)
        assertEquals(0, day.precipitationProbabilityMax)
        assertEquals(0.0, day.uvIndexMax, 0.0)
    }

    @Test
    fun `可选数组里是 null 也不炸`() {
        val snapshot = parseWeather(body(withOptional = true, nullOut = true), now = 1L)

        assertEquals(0, snapshot.hourly.first().precipitationProbability)
        assertEquals(true, snapshot.hourly.first().isDay)
        assertEquals("", snapshot.daily.first().sunrise)
        assertEquals(0.0, snapshot.daily.first().uvIndexMax, 0.0)
    }

    @Test
    fun `给了可选字段就用真值`() {
        val snapshot = parseWeather(body(withOptional = true), now = 1L)

        assertEquals(70, snapshot.hourly.first().precipitationProbability)
        assertEquals(false, snapshot.hourly.first().isDay)
        assertEquals("2026-10-22T06:20", snapshot.daily.first().sunrise)
        assertEquals(65, snapshot.daily.first().precipitationProbabilityMax)
        assertEquals(3.4, snapshot.daily.first().uvIndexMax, 0.0)
    }

    @Test
    fun `必需字段缺失仍然报错不粉饰`() {
        // 可选字段可以缺，必需字段缺了必须抛 —— 否则页面会把坏数据画成"晴 0℃"。
        val missingRequired = """
            {"current":{"time":"2026-10-22T21:00","temperature_2m":12.5,"apparent_temperature":11,
            "relative_humidity_2m":60},
            "hourly":{"time":["2026-10-22T22:00"],"temperature_2m":[12.5],"weather_code":[61]},
            "daily":{"time":["2026-10-22"],"weather_code":[61],"temperature_2m_max":[15.0],"temperature_2m_min":[8.0]}}
        """.trimIndent()
        val thrown = runCatching { parseWeather(missingRequired, now = 1L) }.exceptionOrNull()
        assertEquals(true, thrown is IllegalArgumentException)
        assertEquals(true, thrown?.message?.contains("weather_code"))
    }
}
