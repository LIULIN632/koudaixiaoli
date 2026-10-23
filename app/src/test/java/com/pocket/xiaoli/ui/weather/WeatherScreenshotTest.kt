package com.pocket.xiaoli.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.weather.WeatherCurrent
import com.pocket.xiaoli.data.weather.WeatherDay
import com.pocket.xiaoli.data.weather.WeatherHour
import com.pocket.xiaoli.data.weather.WeatherSnapshot
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 天气页的 JVM 快照基线。
 *
 * ## 为什么必须渲真图
 *
 * 这一页的风险全是"语义树看不出来"的那类：
 * - 7 天列表挤了 6 个元素（星期、图标、天气名、温度区间条、最高、最低），
 *   360dp 下谁被压扁只有图能看见；
 * - 区间条是 Canvas 按整数比例画的，比例算错不会抛异常，只会画出一根
 *   和数字对不上的条；
 * - hero 的 46sp 大数字 + 单位是同一段文字，换行/对齐问题同样只能看图。
 *
 * ## 数据固定
 *
 * 时刻、温度、日期全部写死（不读系统时间、不联网），否则基线每天漂一次。
 * 数值取自 2026-10-22 对烟台的一次真实响应，形状与线上一致。
 *
 * ## 命名与门禁
 *
 * 与仓库其他快照同规：默认 verify 逐像素比对；
 * 观感有意变更后用 `.\gradlew :app:testDebugUnitTest -PrecordScreenshots` 重录。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WeatherScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(
        dark: Boolean = false,
        skin: SkinStyle = SkinStyle.VIVID,
        width: Dp? = null,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = skin,
                darkTheme = dark,
            ) {
                // 与 ServiceSubPageScreenshotTest 同规：只改字号缩放，density 保持设备值，
                // 等价于用户把系统「字体大小」调大一档。1.0× 不套这一层，保证基线不漂。
                val scaled: @Composable () -> Unit = if (fontScale == 1f) {
                    content
                } else {
                    val density = LocalDensity.current
                    {
                        CompositionLocalProvider(
                            LocalDensity provides Density(density.density, fontScale),
                        ) {
                            content()
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    if (width != null) {
                        Box(Modifier.width(width).fillMaxHeight()) {
                            WeatherPage(scaled)
                        }
                    } else {
                        WeatherPage(scaled)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun WeatherPage(content: @Composable () -> Unit) {
        val skin = LocalSkin.current
        val colors = LocalPocketXiaoliColors.current
        Column(modifier = Modifier.fillMaxSize()) {
            ServicePageTopBar(
                title = "天气",
                onBack = {},
                actions = {
                    // 与线上 WeatherScreen 同形：静态的刷新图标（快照不驱动加载态）。
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新天气",
                            tint = colors.textSecondary,
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = skin.spaceLg)
                    .padding(top = skin.spaceSm, bottom = skin.spaceXxl),
            ) {
                content()
            }
        }
    }

    @Test
    fun `天气页 顶部`() {
        render { WeatherContent(sampleSnapshot()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/260-weather-vivid.png")
    }

    @Test
    fun `天气页 滚动到下半屏`() {
        render { WeatherContent(sampleSnapshot()) }
        // 滚到页脚署名的第二行（整页最底），`今日详情` 整张卡与两行署名会一起进入视野；
        // 只滚到「今日详情」标题的话标题会贴在屏幕最底，卡里的瓦片一张都拍不到。
        composeRule.onNodeWithText("按烟台市坐标请求（非校区实测）· 更新于 22:45").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/261-weather-vivid-bottom.png")
    }

    @Test
    fun `天气页 深色`() {
        render(dark = true) { WeatherContent(sampleSnapshot()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/262-weather-dark.png")
    }

    @Test
    fun `天气页 极简皮肤`() {
        render(skin = SkinStyle.MINIMAL) { WeatherContent(sampleSnapshot()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/263-weather-minimal.png")
    }

    @Test
    fun `天气页 缓存兜底提示`() {
        render {
            WeatherContent(
                snapshot = sampleSnapshot(),
                staleMessage = "无法连接天气服务，请检查网络后重试",
            )
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/264-weather-stale.png")
    }

    /** 360dp 窄屏：守 7 天列表的六列挤压与 hero 的三枚胶囊。 */
    @Test
    fun `天气页 360dp 窄屏`() {
        render(width = 360.dp) { WeatherContent(sampleSnapshot()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/265-narrow360-weather.png")
    }

    /**
     * 系统字体放大 1.3×：7 天行有 5 个固定宽度的列（46 / 22 / 58 / 30 / 30），
     * 字号一放大首先被压的是「晴间多云」这种四字天气名和 `17°`。
     * 这张基线守的是它们**换行/截断后仍然不压到温度条**。
     */
    @Test
    fun `天气页 字体放大`() {
        render(fontScale = 1.3f) { WeatherContent(sampleSnapshot()) }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/266-largefont-weather.png")
    }

    /** 固定时刻的样例：与线上响应同形，不含任何系统时间。 */
    private fun sampleSnapshot(): WeatherSnapshot {
        val start = LocalDateTime.of(2026, 10, 22, 23, 0)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        val temps = listOf(
            11.8, 11.4, 11.0, 10.6, 10.2, 10.0, 10.6, 12.1,
            13.8, 15.2, 16.5, 17.3, 17.8, 18.0, 17.6, 16.9,
            15.8, 14.6, 13.5, 12.8, 12.2, 11.7, 11.3, 11.0,
        )
        val codes = listOf(
            1, 1, 2, 2, 3, 3, 3, 2,
            2, 1, 1, 0, 0, 0, 1, 1,
            2, 2, 3, 3, 3, 2, 1, 1,
        )
        val probs = listOf(
            0, 0, 0, 10, 10, 20, 20, 20,
            10, 0, 0, 0, 0, 0, 0, 10,
            10, 20, 20, 10, 0, 0, 0, 0,
        )
        val hours = temps.indices.map { i ->
            val time = start.plusHours(i.toLong())
            WeatherHour(
                time = time.format(formatter),
                temperature = temps[i],
                weatherCode = codes[i],
                precipitationProbability = probs[i],
                isDay = time.hour in 7..17,
            )
        }

        val days = listOf(
            WeatherDay("2026-10-22", 3, 16.9, 7.8, "2026-10-22T06:08", "2026-10-22T17:08", 0, 4.45),
            WeatherDay("2026-10-23", 3, 17.7, 10.7, "2026-10-23T06:09", "2026-10-23T17:07", 20, 3.35),
            WeatherDay("2026-10-24", 1, 18.4, 9.1, "2026-10-24T06:10", "2026-10-24T17:05", 0, 4.4),
            WeatherDay("2026-10-25", 3, 19.3, 14.4, "2026-10-25T06:11", "2026-10-25T17:04", 2, 4.35),
            WeatherDay("2026-10-26", 1, 18.3, 14.8, "2026-10-26T06:12", "2026-10-26T17:03", 2, 3.5),
            WeatherDay("2026-10-27", 2, 16.7, 6.8, "2026-10-27T06:13", "2026-10-27T17:02", 4, 4.25),
            WeatherDay("2026-10-28", 51, 17.5, 5.7, "2026-10-28T06:14", "2026-10-28T17:01", 0, 3.6),
        )

        return WeatherSnapshot(
            current = WeatherCurrent(
                time = "2026-10-22T22:45",
                temperature = 12.3,
                apparentTemperature = 9.9,
                humidity = 67,
                precipitation = 0.0,
                weatherCode = 1,
                cloudCover = 24,
                windSpeed = 9.4,
                windDirection = 167,
                isDay = false,
            ),
            hourly = hours,
            daily = days,
            fetchedAt = 1_700_000_000_000L,
        )
    }
}
