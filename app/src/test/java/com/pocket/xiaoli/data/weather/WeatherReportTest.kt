package com.pocket.xiaoli.data.weather

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 天气的两种"说人话"：给模型读的报告、给气泡用的提醒。
 *
 * 两者都**不联网、不读磁盘**，所以可以完全用构造出来的快照钉住。
 * 重点测的是**什么时候闭嘴**：快照过期、跨天、已经说过的键、天气本来就好好的 ——
 * 这些分支任何一条写错，表现都是"它拿昨晚的雨提醒你今天带伞"，比不提醒更糟。
 */
class WeatherReportTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 23, 9, 20)
    private val today: LocalDate = now.toLocalDate()
    private val nowMs = 1_790_000_000_000L

    private fun snapshot(
        currentCode: Int = 1,
        precipitation: Double = 0.0,
        hours: List<WeatherHour> = emptyList(),
        max: Double = 20.0,
        min: Double = 10.0,
        uv: Double = 3.0,
        day: LocalDate = today,
        fetchedAt: Long = nowMs,
        hourlyRainProbabilityMax: Int = 0,
    ) = WeatherSnapshot(
        current = WeatherCurrent(
            time = "${day}T09:20",
            temperature = 15.0,
            apparentTemperature = 14.0,
            humidity = 60,
            precipitation = precipitation,
            weatherCode = currentCode,
            cloudCover = 20,
            windSpeed = 8.0,
            windDirection = 180,
            isDay = true,
        ),
        hourly = hours,
        daily = listOf(
            WeatherDay(
                date = day.toString(),
                weatherCode = 1,
                temperatureMax = max,
                temperatureMin = min,
                sunrise = "${day}T06:10",
                sunset = "${day}T17:10",
                precipitationProbabilityMax = hourlyRainProbabilityMax,
                uvIndexMax = uv,
            )
        ),
        fetchedAt = fetchedAt,
    )

    private fun hour(
        at: Int,
        code: Int,
        probability: Int = 0,
        day: LocalDate = today,
    ) = WeatherHour(
        time = "%sT%02d:00".format(day, at),
        temperature = 15.0,
        weatherCode = code,
        precipitationProbability = probability,
        isDay = at in 6..18,
    )

    // ── 给模型读的报告 ──

    @Test
    fun `报告把现在的天气翻成人话并带来源`() {
        val report = weatherReport(
            snapshot(hours = listOf(hour(at = 10, code = 61, probability = 70))),
        )

        assertTrue("必须说明数据来自哪里，CC BY 4.0 要求署名", report.contains("烟台天气（Open-Meteo，09:20 更新）"))
        assertTrue(report.contains("现在：晴间多云 15℃"))
        assertTrue(report.contains("湿度 60%"))
        assertTrue(report.contains("南风 2 级"))
        assertTrue(report.contains("· 10:00 小雨 15℃（降水概率 70%）"))
        assertTrue(report.contains("今天（10/23）：晴间多云，10~20℃"))
        assertTrue("城市级数据不许假装成校区实测", report.contains("按烟台市坐标请求，不是校区实测"))
        assertTrue("模型要能承认数据可能已经过时", report.contains("缓存不超过 30 分钟"))
    }

    @Test
    fun `正在降水时报告里写明降水量`() {
        val report = weatherReport(snapshot(currentCode = 61, precipitation = 0.5))
        assertTrue(report.contains("当前降水 0.5 mm"))
    }

    // ── 正在下雨 / 下雪 / 打雷 ──

    @Test
    fun `正在下雨提醒带伞`() {
        val alert = buildWeatherAlert(
            snapshot = snapshot(currentCode = 61, precipitation = 0.4),
            seenKeys = emptySet(),
            now = now,
            nowMs = nowMs,
        )
        assertEquals("正在下雨，出门记得带伞", alert?.text)
        assertEquals("rain@$today", alert?.key)
    }

    @Test
    fun `正在打雷优先说雷而不是雨`() {
        val alert = buildWeatherAlert(
            snapshot = snapshot(currentCode = 95),
            seenKeys = emptySet(),
            now = now,
            nowMs = nowMs,
        )
        assertEquals("正在打雷，尽量别在户外", alert?.text)
        assertEquals("thunder@$today", alert?.key)
    }

    @Test
    fun `正在下雪提醒路面湿滑`() {
        val alert = buildWeatherAlert(
            snapshot = snapshot(currentCode = 71),
            seenKeys = emptySet(),
            now = now,
            nowMs = nowMs,
        )
        assertEquals("正在下雪，路面湿滑慢点走", alert?.text)
        assertEquals("snow@$today", alert?.key)
    }

    @Test
    fun `雨云但没在下雨不算正在下雨`() {
        // weather_code 是"天气现象"，precipitation 才是"此刻有没有落下来"：
        // 只看 code 会在雨停之后继续说"正在下雨"
        assertNull(
            buildWeatherAlert(
                snapshot = snapshot(currentCode = 61, precipitation = 0.0),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            ),
        )
    }

    // ── 未来 12 小时的降水 ──

    @Test
    fun `未来有雨按小时提醒`() {
        val alerts = buildWeatherAlert(
            snapshot = snapshot(hours = listOf(hour(at = 14, code = 61, probability = 70))),
            seenKeys = emptySet(),
            now = now,
            nowMs = nowMs,
        )
        assertEquals("14:00 起有雨，记得带伞", alerts?.text)
        assertEquals("rain@$today", alerts?.key)
    }

    @Test
    fun `雨的概率卡在五成`() {
        assertNull(
            "49% 的雨说出来只会让人白带伞或者白担心",
            buildWeatherAlert(
                snapshot = snapshot(hours = listOf(hour(at = 14, code = 61, probability = 49))),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            "50% 是边界，必须说",
            "rain@$today",
            buildWeatherAlert(
                snapshot = snapshot(hours = listOf(hour(at = 14, code = 61, probability = 50))),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            )?.key,
        )
    }

    @Test
    fun `毛毛雨的门槛更高`() {
        assertNull(
            buildWeatherAlert(
                snapshot = snapshot(hours = listOf(hour(at = 14, code = 53, probability = 55))),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            "drizzle@$today",
            buildWeatherAlert(
                snapshot = snapshot(hours = listOf(hour(at = 14, code = 53, probability = 60))),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            )?.key,
        )
    }

    @Test
    fun `只看未来十二小时且不含已经过去的小时`() {
        val stale = snapshot(
            hours = listOf(
                hour(at = 8, code = 61, probability = 90),
                hour(at = 22, code = 61, probability = 90),
            ),
        )
        assertNull(
            "8 点已经过去、22 点还在 12 小时之外，都不该现在提醒",
            buildWeatherAlert(stale, emptySet(), now, nowMs),
        )
    }

    @Test
    fun `同一场雨换个小时不改变去重键`() {
        val early = buildWeatherAlert(
            snapshot(hours = listOf(hour(at = 14, code = 61, probability = 70))),
            emptySet(),
            now,
            nowMs,
        )
        val late = buildWeatherAlert(
            snapshot(hours = listOf(hour(at = 18, code = 61, probability = 80))),
            emptySet(),
            now,
            nowMs,
        )
        assertEquals(
            "雨下一整晚时键必须稳定，否则每次评估都会再冒一次",
            early?.key,
            late?.key,
        )
    }

    @Test
    fun `降水排在当天高温前面`() {
        val alert = buildWeatherAlert(
            snapshot = snapshot(
                hours = listOf(hour(at = 14, code = 61, probability = 70)),
                max = 37.0,
            ),
            seenKeys = emptySet(),
            now = now,
            nowMs = nowMs,
        )
        assertEquals("rain@$today", alert?.key)
    }

    // ── 当天的温度与紫外线 ──

    @Test
    fun `高温提醒`() {
        val alert = buildWeatherAlert(snapshot(max = 36.0), emptySet(), now, nowMs)
        assertEquals("今天最高 36℃，注意防暑补水", alert?.text)
        assertEquals("heat@$today", alert?.key)
    }

    @Test
    fun `低温提醒`() {
        val alert = buildWeatherAlert(snapshot(min = -6.0), emptySet(), now, nowMs)
        assertEquals("今天最低 -6℃，注意保暖", alert?.text)
        assertEquals("cold@$today", alert?.key)
    }

    @Test
    fun `紫外线强时提醒防晒`() {
        val alert = buildWeatherAlert(snapshot(uv = 8.0), emptySet(), now, nowMs)
        assertEquals("今天紫外线很强，出门记得防晒", alert?.text)
        assertEquals("uv@$today", alert?.key)
    }

    @Test
    fun `天气好时一句话都不说`() {
        assertNull(buildWeatherAlert(snapshot(), emptySet(), now, nowMs))
    }

    // ── 过期、跨天与去重 ──

    @Test
    fun `没有快照就不说`() {
        assertNull(buildWeatherAlert(null, emptySet(), now, nowMs))
    }

    @Test
    fun `超过两小时的快照不说`() {
        assertNull(
            "拿两小时前的雨提醒现在带伞，很可能雨已经停了",
            buildWeatherAlert(
                snapshot = snapshot(currentCode = 61, precipitation = 0.5, fetchedAt = nowMs - 3 * 60 * 60 * 1000L),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `跨天的快照不说`() {
        assertNull(
            "昨晚的雨不能拿来提醒今天",
            buildWeatherAlert(
                snapshot = snapshot(day = today.minusDays(1), currentCode = 61, precipitation = 0.5),
                seenKeys = emptySet(),
                now = now,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `说过的键不再冒泡`() {
        assertNull(
            buildWeatherAlert(
                snapshot = snapshot(currentCode = 61, precipitation = 0.5),
                seenKeys = setOf("rain@$today"),
                now = now,
                nowMs = nowMs,
            ),
        )
        assertNull(
            buildWeatherAlert(
                snapshot = snapshot(max = 36.0),
                seenKeys = setOf("heat@$today"),
                now = now,
                nowMs = nowMs,
            ),
        )
    }
}
