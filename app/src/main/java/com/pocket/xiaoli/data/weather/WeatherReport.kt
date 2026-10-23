package com.pocket.xiaoli.data.weather

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 把天气快照写成一段**给模型读**的中文报告。
 *
 * 为什么不在工具里让模型自己看 JSON：字段名、WMO 代码、单位换算是三件模型最容易
 * 记错的事（把 12.3 说成毫米、把代码 3 说成晴）。这里一次性翻成「阴 12℃，
 * 西北风 2 级」这种人话，模型只负责取舍与措辞。
 *
 * 只保留 12 小时与今明两天：足够回答「要带伞吗 / 明天冷不冷」，
 * 又不会把 4000 字的结果预算吃掉。
 */
fun weatherReport(snapshot: WeatherSnapshot): String = buildString {
    val current = snapshot.current
    append("烟台天气（Open-Meteo，")
    append(current.time.substringAfter('T').take(5))
    append(" 更新）：\n")
    append("现在：").append(current.label).append(" ")
    append(temperatureText(current.temperature)).append("℃")
    append("，体感 ").append(temperatureText(current.apparentTemperature)).append("℃")
    append("，湿度 ").append(current.humidity).append("%")
    append("，").append(windDirectionLabel(current.windDirection)).append("风 ")
    append(windScale(current.windSpeed)).append(" 级")
    if (current.precipitation > 0) {
        append("，当前降水 ").append(current.precipitation).append(" mm")
    }
    append("。\n")

    if (snapshot.hourly.isNotEmpty()) {
        append("未来 12 小时：\n")
        snapshot.hourly.take(REPORT_HOURS).forEach { hour ->
            append("· ").append(hour.clockLabel).append(" ")
                .append(hour.label).append(" ")
                .append(temperatureText(hour.temperature)).append("℃")
            if (hour.precipitationProbability > 0) {
                append("（降水概率 ").append(hour.precipitationProbability).append("%）")
            }
            append("\n")
        }
    }

    snapshot.daily.take(REPORT_DAYS).forEachIndexed { index, day ->
        append(dayLabel(index, day.date)).append("（").append(day.shortDate).append("）：")
        append(day.label).append("，")
        append(temperatureText(day.temperatureMin)).append("~")
        append(temperatureText(day.temperatureMax)).append("℃，")
        append("降水概率 ").append(day.precipitationProbabilityMax).append("%，")
        append("紫外线 ").append(temperatureText(day.uvIndexMax))
        append("（").append(uvLabel(day.uvIndexMax)).append("）。\n")
    }

    append("以上按烟台市坐标请求，不是校区实测；缓存不超过 30 分钟。")
}

/**
 * 一条可直接交给 `ProactiveRules` 的天气提醒。
 *
 * [key] 是**同一件事**的去重键（类别 + 日期）：雨下一整晚时每次评估得到的键相同，
 * 不会反复冒泡；第二天同一时段的雨则是新键。采集侧拿**已经说过的键集合**过滤 ——
 * 与校历节点同一种做法（见 `ProactiveStore.calendarSeenKey`），只是天气一天内
 * 可能有雨、雷、雪几种不同的事，所以存的是一个小集合而不是单个游标。
 *
 * 气泡点开直接进天气页（与「快上课 → 课表」同一个理由）：小时降水条比复述更有用，
 * 而且天气页不依赖鲤鱼是否已配置 API Key。
 */
data class WeatherAlert(
    /** 气泡文案，短句。 */
    val text: String,
    /** 同一件事的去重键（`kind@date`）。 */
    val key: String,
)

/**
 * 从最后一份天气快照里找出**眼下值得开口**的那件事。
 *
 * ## 铁律：这是一个纯函数，不联网
 *
 * `ProactiveEvaluator` 每 60 秒调一次；如果这里去拉天气，就变成了"为提醒发请求"，
 * 冷启动时气泡会等网络，用户看到的是延迟而不是提醒。所以快照由首页/天气页
 * 拉取后放进 `WeatherRepository.snapshot`，这里只读。
 *
 * ## 过期的天气不说
 *
 * 快照超过 [MAX_ALERT_AGE_MS] 或跨了一天，一律返回 null —— 拿昨晚的雨提醒今天
 * 出门带伞，比不提醒更糟（正是 `ProactiveRules` 那条「冷却不负责这件事说过了」的同类问题）。
 *
 * ## 判定顺序
 *
 * 正在下的雷/雪/雨 > 未来 12 小时内第一场够格的降水 > 当天高温 > 当天低温 > 紫外线。
 * 降水优先于当天的温度/紫外线：带不带伞是马上要做出的决定，防晒和防暑可以晚一点说。
 */
fun buildWeatherAlert(
    snapshot: WeatherSnapshot?,
    seenKeys: Set<String>,
    now: LocalDateTime,
    nowMs: Long = System.currentTimeMillis(),
): WeatherAlert? {
    val s = snapshot ?: return null
    if (nowMs - s.fetchedAt > MAX_ALERT_AGE_MS) return null
    val today = now.toLocalDate().toString()
    if (s.current.time.take(10) != today) return null

    val currentKind = s.current.kind
    if (currentKind == WeatherKind.THUNDER ||
        currentKind == WeatherKind.SNOW ||
        (currentKind == WeatherKind.RAIN && s.current.precipitation > 0)
    ) {
        val key = alertKey(currentKind, s.current.time.take(10))
        if (key in seenKeys) return null
        return WeatherAlert(text = ongoingText(currentKind), key = key)
    }

    val startHour = now.truncatedTo(ChronoUnit.HOURS)
    val upcoming = s.hourly.firstOrNull { hour ->
        val time = parseHour(hour.time) ?: return@firstOrNull false
        !time.isBefore(startHour) &&
            time.isBefore(startHour.plusHours(ALERT_HOURS)) &&
            isAlerting(hour)
    }
    if (upcoming != null) {
        val key = alertKey(upcoming.kind, upcoming.time.take(10))
        if (key !in seenKeys) {
            val hourTime = parseHour(upcoming.time) ?: startHour
            return WeatherAlert(text = upcomingText(upcoming.kind, hourTime), key = key)
        }
    }

    val day = s.daily.firstOrNull { it.date == today } ?: return null
    return when {
        day.temperatureMax >= HEAT_C -> WeatherAlert(
            text = "今天最高 ${temperatureText(day.temperatureMax)}℃，注意防暑补水",
            key = "heat@${day.date}",
        )

        day.temperatureMin <= COLD_C -> WeatherAlert(
            text = "今天最低 ${temperatureText(day.temperatureMin)}℃，注意保暖",
            key = "cold@${day.date}",
        )

        day.uvIndexMax >= UV_ALERT -> WeatherAlert(
            text = "今天紫外线${uvLabel(day.uvIndexMax)}，出门记得防晒",
            key = "uv@${day.date}",
        )

        else -> null
    }?.takeIf { it.key !in seenKeys }
}

/** 未来多少小时内找降水。与文案里的「未来几小时」对齐。 */
private const val ALERT_HOURS = 12L

/**
 * 快照最长可用多久。
 *
 * 仓储的缓存 TTL 是 30 分钟，但用户可能一直停在别的页面。2 小时是"天气还大致成立"
 * 的边界：再久就可能已经转晴或雨停，那时的提醒是在说假话。
 */
private const val MAX_ALERT_AGE_MS = 2 * 60 * 60 * 1000L

/** 高温/低温阈值（℃），取常见气象预警的量级。 */
private const val HEAT_C = 35.0
private const val COLD_C = -5.0

/** WHO 紫外线指数 7 起为「强」，对应需要防晒的提醒。 */
private const val UV_ALERT = 7.0

private fun isAlerting(hour: WeatherHour): Boolean = when (hour.kind) {
    WeatherKind.THUNDER, WeatherKind.SNOW -> true
    WeatherKind.RAIN -> hour.precipitationProbability >= 50
    WeatherKind.DRIZZLE -> hour.precipitationProbability >= 60
    else -> false
}

private fun alertKey(kind: WeatherKind, hour: String): String =
    "${kind.name.lowercase()}@$hour"

private fun parseHour(value: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(value) }.getOrNull()

private fun ongoingText(kind: WeatherKind): String = when (kind) {
    WeatherKind.THUNDER -> "正在打雷，尽量别在户外"
    WeatherKind.SNOW -> "正在下雪，路面湿滑慢点走"
    else -> "正在下雨，出门记得带伞"
}

private fun upcomingText(kind: WeatherKind, time: LocalDateTime): String {
    val clock = time.toLocalTime().toString()
    return when (kind) {
        WeatherKind.THUNDER -> "$clock 前后有雷阵雨，注意安全"
        WeatherKind.SNOW -> "$clock 起有雪，路面湿滑慢点走"
        else -> "$clock 起有雨，记得带伞"
    }
}

private fun temperatureText(value: Double): String = formatTemperature(value)

private const val REPORT_HOURS = 12
private const val REPORT_DAYS = 2
