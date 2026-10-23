package com.pocket.xiaoli.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.weather.WeatherDay
import com.pocket.xiaoli.data.weather.WeatherHour
import com.pocket.xiaoli.data.weather.WeatherKind
import com.pocket.xiaoli.data.weather.WeatherRepository
import com.pocket.xiaoli.data.weather.WeatherSnapshot
import com.pocket.xiaoli.data.weather.dayLabel
import com.pocket.xiaoli.data.weather.formatTemperature
import com.pocket.xiaoli.data.weather.uvLabel
import com.pocket.xiaoli.data.weather.windDirectionLabel
import com.pocket.xiaoli.data.weather.windScale
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ErrorCard
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import java.util.Locale

/**
 * 天气页（服务页「生活服务 → 天气」的原生子页）。
 *
 * ## 数据与坐标的诚实边界
 *
 * 数据来自 Open-Meteo（开源、免 Key、CC BY 4.0，页脚保留署名）。
 * 坐标是**烟台市**（见 `WeatherSnapshot` 的 KDoc），不是校区实测 ——
 * 所以页面写「烟台 · 城市天气」，不写「本校区」。校园数据里没有经纬度，
 * 拿猜测值填坐标会让温度看起来"精确到楼"，那是假精度。
 *
 * ## 三种状态的优先级
 *
 * 1. 有数据（哪怕来自上次缓存）→ **照常渲染**，缓存兜底时顶部加一条黄色提示；
 * 2. 没数据 + 正在拉 → 转圈；
 * 3. 没数据 + 失败 → ErrorCard + 重试。
 *
 * 第 1 条是刻意与 `AppFeedRepository` 对齐的：天气页最常见的打开场景是
 * 课间看一眼，网络抖一下就把整页换成错误提示，比显示 40 分钟前的温度更糟。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    repository: WeatherRepository,
    onBack: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    var result by remember { mutableStateOf<WeatherRepository.Result?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var forceNext by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        loading = true
        result = repository.load(force = forceNext)
        forceNext = false
        loading = false
    }

    fun refresh() {
        if (loading) return
        forceNext = true
        refreshTick++
    }

    val success = result as? WeatherRepository.Result.Success
    val failure = result as? WeatherRepository.Result.Failure
    val snapshot = success?.snapshot ?: failure?.cached
    val staleMessage = if (failure != null && failure.cached != null) failure.message else null

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "天气",
                onBack = onBack,
                actions = {
                    IconButton(onClick = ::refresh, enabled = !loading) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.accentMain,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新天气",
                                tint = colors.textSecondary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg)
                .padding(top = skin.spaceSm, bottom = skin.spaceXxl),
        ) {
            when {
                snapshot != null -> WeatherContent(
                    snapshot = snapshot,
                    staleMessage = staleMessage,
                )

                loading || result == null -> WeatherLoading()

                failure != null -> {
                    Spacer(Modifier.height(skin.spaceXl))
                    ErrorCard(
                        message = failure.message,
                        onRetry = ::refresh,
                    )
                }
            }
        }
    }
}

/**
 * 页面正文（与网络状态解耦，便于快照测试直接喂固定数据）。
 *
 * @param staleMessage 非 null 时在顶部显示「显示的是上次的天气」提醒。
 */
@Composable
internal fun WeatherContent(
    snapshot: WeatherSnapshot,
    staleMessage: String? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceLg),
    ) {
        if (staleMessage != null) {
            StaleBanner(message = staleMessage)
        }

        WeatherHero(snapshot)

        Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            ServiceSectionLabel(
                title = "接下来 24 小时",
                accent = ServiceAccent.QUERY,
                trailing = {
                    Text(
                        text = "横向滑动",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                },
            )
            HourlyStrip(snapshot)
        }

        Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            ServiceSectionLabel(
                title = "未来 7 天",
                accent = ServiceAccent.QUERY,
                trailing = {
                    Text(
                        text = "温度区间",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                },
            )
            DailyCard(snapshot.daily)
        }

        Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            ServiceSectionLabel(
                title = "今日详情",
                accent = ServiceAccent.QUERY,
            )
            DetailsCard(snapshot)
        }

        Attribution(snapshot)
    }
}

// ─────────────────────────── 主视觉 ───────────────────────────

@Composable
private fun WeatherHero(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    val today = snapshot.daily.firstOrNull()
    val skin = LocalSkin.current

    ServiceHeroCard(accent = ServiceAccent.QUERY) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "烟台 · 城市天气",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    Text(
                        text = if (current.isDay) "白天" else "夜间",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(skin.spaceXs))
                ServiceHeroCount(
                    number = formatTemperature(current.temperature),
                    unit = "°C",
                    numberSize = 46.sp,
                )
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    text = "${current.label} · 体感 ${formatTemperature(current.apparentTemperature)}°C",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            WeatherGlyph(
                icon = weatherIcon(current.kind, current.isDay),
                size = 88.dp,
            )
        }

        Spacer(Modifier.height(skin.spaceLg))

        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            ServiceHeroChip(text = "湿度 ${current.humidity}%")
            ServiceHeroChip(
                text = "${windDirectionLabel(current.windDirection)}风 ${windScale(current.windSpeed)} 级",
            )
            ServiceHeroChip(
                text = "降水 ${snapshot.hourly.firstOrNull()?.precipitationProbability ?: 0}%",
            )
        }

        if (today != null) {
            Spacer(Modifier.height(skin.spaceLg))
            HorizontalDivider(color = Color.White.copy(alpha = 0.16f), thickness = 0.5.dp)
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                text = "今日 ${formatTemperature(today.temperatureMin)}° ~ " +
                    "${formatTemperature(today.temperatureMax)}° · " +
                    "降水概率 ${today.precipitationProbabilityMax}%",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.86f),
            )
        }
    }
}

@Composable
private fun WeatherGlyph(icon: ImageVector, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

// ─────────────────────────── 24 小时 ───────────────────────────

@Composable
private fun HourlyStrip(snapshot: WeatherSnapshot) {
    val skin = LocalSkin.current
    val accent = serviceAccentColor(ServiceAccent.QUERY)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        snapshot.hourly.forEachIndexed { index, hour ->
            HourCell(
                hour = hour,
                isFirst = index == 0,
                accent = accent,
            )
        }
    }
}

@Composable
private fun HourCell(
    hour: WeatherHour,
    isFirst: Boolean,
    accent: Color,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val shape = RoundedCornerShape(skin.radiusChip)
    val highlighted = isFirst
    val tint = weatherTint(hour.kind, hour.isDay)

    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(shape)
            .background(
                if (highlighted) {
                    accent.copy(alpha = if (isDark) 0.30f else 0.14f)
                } else {
                    colors.surfaceCard
                },
            )
            .then(
                if (highlighted) {
                    Modifier.border(1.dp, accent.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = skin.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = hour.clockLabel,
            fontSize = 11.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (highlighted) colors.textPrimary else colors.textTertiary,
        )
        Spacer(Modifier.height(skin.spaceSm))
        Icon(
            imageVector = weatherIcon(hour.kind, hour.isDay),
            contentDescription = hour.label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(skin.spaceSm))
        Text(
            text = "${formatTemperature(hour.temperature)}°",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(3.dp))
        if (hour.precipitationProbability >= 20) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = colors.info,
                    modifier = Modifier.size(9.dp),
                )
                Spacer(Modifier.width(1.dp))
                Text(
                    text = "${hour.precipitationProbability}%",
                    fontSize = 10.sp,
                    color = colors.info,
                )
            }
        } else {
            // 保留与有降水那行相同的高度，否则同一排格子的温度基线会错开。
            Spacer(Modifier.height(13.dp))
        }
    }
}

// ─────────────────────────── 7 天 ───────────────────────────

@Composable
private fun DailyCard(days: List<WeatherDay>) {
    if (days.isEmpty()) return
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val weekMin = days.minOf { it.temperatureMin }
    val weekMax = days.maxOf { it.temperatureMax }

    AppCard {
        Column(modifier = Modifier.padding(vertical = skin.spaceXs)) {
            days.forEachIndexed { index, day ->
                DailyRow(
                    day = day,
                    index = index,
                    weekMin = weekMin,
                    weekMax = weekMax,
                )
                if (index != days.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = skin.spaceLg),
                        color = colors.borderDivider,
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyRow(
    day: WeatherDay,
    index: Int,
    weekMin: Double,
    weekMax: Double,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isToday = index == 0
    val tint = weatherTint(day.kind, isDay = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isToday) colors.surfaceHighlight else Color.Transparent)
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(46.dp)) {
            Text(
                text = dayLabel(index, day.date),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isToday) colors.accentMain else colors.textPrimary,
            )
            Text(
                text = day.shortDate,
                fontSize = 10.sp,
                color = colors.textTertiary,
            )
        }
        Icon(
            imageVector = weatherIcon(day.kind, isDay = true),
            contentDescription = day.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = day.label,
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.width(66.dp),
        )
        TemperatureRangeBar(
            min = day.temperatureMin,
            max = day.temperatureMax,
            weekMin = weekMin,
            weekMax = weekMax,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = skin.spaceSm),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = "${formatTemperature(day.temperatureMax)}°",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
        Text(
            text = "${formatTemperature(day.temperatureMin)}°",
            fontSize = 12.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

/**
 * 一周温度区间条：整周的极值作为总长，每天的区间是其中一段。
 *
 * 冷端到暖端用两个**主题派生色**渐变（QUERY 蓝 → PAYMENT 暖），
 * 不写死天气蓝/橙，换配色主题时整条会一起变。
 */
@Composable
private fun TemperatureRangeBar(
    min: Double,
    max: Double,
    weekMin: Double,
    weekMax: Double,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val cool = serviceAccentColor(ServiceAccent.QUERY)
    val warm = serviceAccentColor(ServiceAccent.PAYMENT)
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    val start = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f)
    val end = ((max - weekMin) / span).toFloat().coerceIn(start, 1f)
    val track = colors.surfaceAlt

    Canvas(modifier = modifier.height(6.dp)) {
        val radius = size.height / 2f
        drawRoundRect(color = track, cornerRadius = CornerRadius(radius))
        val x0 = size.width * start
        val x1 = (size.width * end).coerceAtLeast(x0 + size.height)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(cool, warm),
                startX = x0,
                endX = x1,
            ),
            topLeft = Offset(x0, 0f),
            size = Size((x1 - x0).coerceAtMost(size.width - x0), size.height),
            cornerRadius = CornerRadius(radius),
        )
    }
}

// ─────────────────────────── 详情 ───────────────────────────

@Composable
private fun DetailsCard(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    val today = snapshot.daily.firstOrNull()
    val skin = LocalSkin.current
    val tiles = listOf(
        Detail("体感温度", "${formatTemperature(current.apparentTemperature)}°", Icons.Outlined.Thermostat),
        Detail("湿度", "${current.humidity}%", Icons.Outlined.WaterDrop),
        Detail(
            "风",
            "${windDirectionLabel(current.windDirection)}风 ${windScale(current.windSpeed)} 级",
            Icons.Outlined.Air,
        ),
        Detail("降水量", String.format(Locale.US, "%.1f mm", current.precipitation), Icons.Outlined.Umbrella),
        Detail("云量", "${current.cloudCover}%", Icons.Outlined.Cloud),
        Detail(
            "紫外线",
            today?.let { String.format(Locale.US, "%.1f", it.uvIndexMax) + " · " + uvLabel(it.uvIndexMax) }
                ?: "—",
            Icons.Outlined.WbSunny,
        ),
        Detail("日出", clockOf(today?.sunrise), Icons.Outlined.WbTwilight),
        Detail("日落", clockOf(today?.sunset), Icons.Outlined.NightsStay),
    )

    AppCard {
        Column(
            modifier = Modifier.padding(skin.spaceSm),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            tiles.chunked(2).forEach { rowTiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    rowTiles.forEach { tile ->
                        DetailTile(tile, Modifier.weight(1f))
                    }
                    if (rowTiles.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private data class Detail(val label: String, val value: String, val icon: ImageVector)

@Composable
private fun DetailTile(tile: Detail, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = serviceAccentColor(ServiceAccent.QUERY)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.surfaceAlt)
            .padding(horizontal = skin.spaceSm, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tile.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Column {
            Text(tile.label, fontSize = 11.sp, color = colors.textTertiary)
            Text(
                text = tile.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
    }
}

// ─────────────────────────── 附属信息 ───────────────────────────

@Composable
private fun StaleBanner(message: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(skin.radiusCard)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceCard)
            .border(1.dp, colors.warning.copy(alpha = 0.35f), shape)
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = "$message。下面显示的是上次获取的天气",
            fontSize = 12.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Attribution(snapshot: WeatherSnapshot) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = skin.spaceXs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "天气数据来自 Open-Meteo · CC BY 4.0",
            fontSize = 11.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "按烟台市坐标请求（非校区实测）· 更新于 " +
                snapshot.current.time.substringAfter('T').take(5),
            fontSize = 11.sp,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WeatherLoading() {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = colors.accentMain, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(text = "正在获取烟台天气…", fontSize = 13.sp, color = colors.textSecondary)
        }
    }
}

// ─────────────────────────── 纯映射 ───────────────────────────

internal fun weatherIcon(kind: WeatherKind, isDay: Boolean): ImageVector = when (kind) {
    WeatherKind.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    WeatherKind.PARTLY_CLOUDY -> if (isDay) Icons.Outlined.WbCloudy else Icons.Outlined.NightsStay
    WeatherKind.CLOUDY -> Icons.Outlined.Cloud
    WeatherKind.FOG -> Icons.Outlined.CloudQueue
    WeatherKind.DRIZZLE -> Icons.Outlined.Grain
    WeatherKind.RAIN -> Icons.Outlined.WaterDrop
    WeatherKind.SNOW -> Icons.Outlined.AcUnit
    WeatherKind.THUNDER -> Icons.Outlined.Thunderstorm
}

/** 图标着色：晴/多云走暖色，云雨走蓝，雷走紫，雪走提示蓝。全部由主题派生。 */
@Composable
internal fun weatherTint(kind: WeatherKind, isDay: Boolean): Color {
    val colors = LocalPocketXiaoliColors.current
    return when (kind) {
        WeatherKind.CLEAR,
        WeatherKind.PARTLY_CLOUDY,
        -> if (isDay) serviceAccentColor(ServiceAccent.PAYMENT) else serviceAccentColor(ServiceAccent.STUDY)

        WeatherKind.CLOUDY,
        WeatherKind.FOG,
        WeatherKind.DRIZZLE,
        WeatherKind.RAIN,
        -> serviceAccentColor(ServiceAccent.QUERY)

        WeatherKind.SNOW -> colors.info
        WeatherKind.THUNDER -> serviceAccentColor(ServiceAccent.STUDY)
    }
}

/** `2026-10-22T06:08` → `06:08`；缺失时给一个明确的占位，而不是空白。 */
private fun clockOf(iso: String?): String =
    iso?.substringAfter('T')?.take(5)?.takeIf { it.isNotBlank() } ?: "—"
