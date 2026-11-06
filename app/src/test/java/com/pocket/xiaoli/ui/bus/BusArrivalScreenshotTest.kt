package com.pocket.xiaoli.ui.bus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.bus.BusLine
import com.pocket.xiaoli.data.bus.BusRepository
import com.pocket.xiaoli.data.bus.BusStation
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「公交到站」的外观基线（浅色看板 / 深色看板 / 离线与未开实时 / 加载态）。
 *
 * ## 这四张各钉什么
 *
 * - **394 浅色**：一行里同时有「已到站」「2 站」「8 站」「等待发车」四种档位 ——
 *   这一张最容易看出的问题是**该强调的没强调、不该强调的全红了**；
 *   同时钉住线路名 chip、方向、首末班与票价这三段文字的挤压关系。
 * - **395 深色**：警示色与弱化色在深底上还分不分得开。
 * - **396 离线/未开实时**：这一张钉的是**诚实性** —— 「未开通实时」「停运」
 *   必须显示成说明而不是倒计时，且底部要说清这是上次刷新到的信息。
 * - **397 加载态**：第一次进页面（还没数据）时不能是空白屏。
 *
 * ## 为什么图里没有时间会漂的值
 *
 * `updatedText` 直接传固定字符串（「更新于 08:12」），不读时钟、也不读
 * `BuildConfig.VERSION_NAME` —— 见 `memory/04-ui-verification.md` §八·补。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class BusArrivalScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    /** 2026-11-04 本校站真实线路与站名，到站文案取四种真实形态各一条。 */
    private val boardLines = listOf(
        BusLine(
            lineName = "17路", direction = 2,
            from = "山东商务职业学院南门", to = "火车站",
            firstDeparture = "05:30", lastDeparture = "21:30", fare = "1元",
            nearText = "已到站 [到达 烟台理工学院]", nearNum = 0, hasRealtime = true,
        ),
        BusLine(
            lineName = "68路", direction = 2,
            from = "中医药高专场站", to = "烟台人才市场",
            firstDeparture = "06:00", lastDeparture = "19:00", fare = "1元",
            nearText = "2站 [开往 蓝色智谷]", nearNum = 2, hasRealtime = true,
        ),
        BusLine(
            lineName = "105路", direction = 2,
            from = "石家疃", to = "孙家滩场站",
            firstDeparture = "06:00", lastDeparture = "19:00", fare = "1元",
            nearText = "8站 [开往 金地格林世界北站]", nearNum = 8, hasRealtime = true,
        ),
        BusLine(
            lineName = "67路文登整骨区间", direction = 2,
            from = "牟平中医医院", to = "文登整骨烟台医院场站",
            firstDeparture = "06:00", lastDeparture = "14:30", fare = "1元",
            nearText = "等待发车", nearNum = 100, hasRealtime = true,
        ),
    )

    private val nearby = listOf(
        BusStation(name = "凤凰大街华庭路西", lat = 37.4532748539, lng = 121.4693828155, distanceMeters = 205),
        BusStation(name = "华庭路", lat = 37.4506011213, lng = 121.4716101611, distanceMeters = 358),
        BusStation(name = "橡树湾", lat = 37.4585394911, lng = 121.4748006299, distanceMeters = 595),
        BusStation(name = "烟台理工学院", lat = 37.4576738327, lng = 121.4782500637, distanceMeters = 725),
    )

    @Test
    fun `看板浅色与深色`() {
        setContent(
            BusArrivalUi(
                stationName = "烟台理工学院",
                stations = nearby,
                lines = boardLines,
                updatedText = "更新于 08:12",
                // 在线态：底部不该出现「离线显示」那句
                fromNetwork = true,
            )
        )
        composeRule.onRoot().captureRoboImage("src/test/screenshots/394-bus-board-light.png")
        dark = true
        composeRule.onRoot().captureRoboImage("src/test/screenshots/395-bus-board-dark.png")
    }

    @Test
    fun `离线显示与未开实时`() {
        setContent(
            BusArrivalUi(
                stationName = "烟台理工学院",
                stations = nearby,
                lines = listOf(
                    BusLine(
                        lineName = "km116路", direction = 1,
                        from = "烟台汽车总站", to = "开发区 TIMER城",
                        firstDeparture = "06:00", lastDeparture = "19:30", fare = "2-5元",
                        nearText = "", nearNum = 0, hasRealtime = false,
                    ),
                    BusLine(
                        lineName = "566路", direction = 2,
                        from = "职业学院北门", to = "烟台人才市场",
                        firstDeparture = "05:30", lastDeparture = "19:30", fare = "1元",
                        nearText = "3站", nearNum = 3, hasRealtime = true, runState = 1,
                    ),
                    BusLine(
                        lineName = "68路", direction = 2,
                        from = "中医药高专场站", to = "烟台人才市场",
                        firstDeparture = "06:00", lastDeparture = "19:00", fare = "1元",
                        nearText = "1站 [开往 烟台人才市场]", nearNum = 1, hasRealtime = true,
                        nextBusText = "6站",
                    ),
                ),
                error = "没能连上实时公交，下面是上次刷新到的到站信息",
                updatedText = "更新于 07:58",
                fromNetwork = false,
            )
        )
        composeRule.onRoot().captureRoboImage("src/test/screenshots/396-bus-offline-no-realtime.png")
    }

    @Test
    fun `首次进入的加载态`() {
        setContent(BusArrivalUi(stationName = "烟台理工学院", loading = true))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/397-bus-loading.png")
    }

    @Test
    fun `这个站没查到线路`() {
        setContent(
            BusArrivalUi(
                stationName = "烟台理工学院",
                stations = nearby,
                lines = emptyList(),
                updatedText = "更新于 08:20",
            )
        )
        composeRule.onRoot().captureRoboImage("src/test/screenshots/398-bus-station-empty-lines.png")
    }

    private fun setContent(ui: BusArrivalUi) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides
                        rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    BusArrivalContent(
                        ui = ui,
                        onSelectStation = {},
                        onRefresh = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
