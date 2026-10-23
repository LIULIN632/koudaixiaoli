package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.weather.WeatherSnapshot
import com.pocket.xiaoli.data.weather.formatTemperature
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.ui.weather.weatherIcon
import com.pocket.xiaoli.ui.weather.weatherTint

/**
 * 首页时钟旁的天气胶囊：图标 + 「阴 12°」。点开进天气页。
 *
 * ## 为什么只放温度、不放大段描述
 *
 * 用户的原话是"在时间旁边也显示上天气" —— 他要的是**扫一眼**。首页的下一条内容
 * 是搜索框和下一节课，头部再长就会把这两件正事挤下去。所以这里只保留
 * 现象（图标 + 两字标签）与温度，湿度/风/降水概率都留给天气页。
 *
 * ## 为什么视觉和热区是两层
 *
 * 胶囊本身按内容包高（约 26dp），但可点元素必须有 44dp 热区（见 `invisibleTouchTarget`）。
 * 把 `heightIn(44)` 直接套在带背景的那一层上，会得到一个 44dp 高的"胖胶囊"、
 * 和旁边 28sp 的时钟不协调。所以外层只负责热区与按压反馈，内层负责外观。
 * 热区是**占位**的，不去偷邻居的点击。
 *
 * 没有数据（首次加载中 / 断网且无缓存）时整个胶囊不出现 —— 一个空壳或转圈
 * 比没有更让人分心，天气页自己会说明失败原因。
 */
@Composable
internal fun HomeWeatherChip(
    snapshot: WeatherSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    val current = snapshot.current
    val tint = weatherTint(current.kind, current.isDay)
    val pillShape = RoundedCornerShape(50)
    // 深色底上同透明度会"糊"进背景，所以暗色模式给得更高一点。
    val topAlpha = if (isDark) 0.32f else 0.20f
    val bottomAlpha = if (isDark) 0.18f else 0.10f

    Box(
        modifier = modifier
            .invisibleTouchTarget()
            .miuixPress(onClick = onClick)
            .testTag("home-weather-chip"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            tint.copy(alpha = topAlpha),
                            tint.copy(alpha = bottomAlpha),
                        ),
                    ),
                )
                // 可点元素要有可见边界：淡染底在浅色页底上很容易只剩"一片颜色"，
                // 一圈同色描边把它钉成一颗按钮。
                .border(1.dp, tint.copy(alpha = if (isDark) 0.45f else 0.30f), pillShape)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = weatherIcon(current.kind, current.isDay),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${current.label} ${formatTemperature(current.temperature)}°",
                style = PocketTypography.caption,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}
