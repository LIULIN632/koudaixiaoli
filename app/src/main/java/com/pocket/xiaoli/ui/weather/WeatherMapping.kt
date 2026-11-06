package com.pocket.xiaoli.ui.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.pocket.xiaoli.core.weather.WeatherKind
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 天气的**纯映射**：WMO 天气种类 → 图标与着色，ISO 时刻 → 显示的分钟。
 *
 * 2026-11-06 从 `WeatherScreen.kt` 搬出来：那一页因为天气要按城市取数又长了二十行，
 * 越过 `tools/lint_design.py` 的 800 行门槛 —— 那个数字只许降不许升，
 * 所以把它抹掉不算改进，把与"这一页怎么画"无关的映射拿走才算。
 * 同包搬迁，调用点一行都不用改。
 */

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
internal fun clockOf(iso: String?): String =
    iso?.substringAfter('T')?.take(5)?.takeIf { it.isNotBlank() } ?: "—"
