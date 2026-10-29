package com.pocket.xiaoli.ui.seat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.libraryseat.LibrarySeatLayout
import com.pocket.xiaoli.data.libraryseat.LibrarySeatLibrary
import com.pocket.xiaoli.data.libraryseat.LibrarySeatReservation
import com.pocket.xiaoli.data.libraryseat.LibrarySeatSeat
import com.pocket.xiaoli.data.libraryseat.LibrarySeatUser
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「图书馆座位」三态的快照。
 *
 * ## 为什么这三张值得有
 *
 * 这一页是整 App 里**唯一有"图"的页**（座位图），而那张图此前只有色块、
 * 没有任何一处说"白色可以点、彩色点不动" —— 这种缺东西的问题像素图一眼就能看见，
 * 但在这之前这一页**一张基线都没有**，所以一直没人看见。
 *
 * ## 三态怎么渲
 *
 * 登录态要看 `CookieManager` 里有没有 traceint 的 Cookie，预约与座位图要看接口回包，
 * 测试都造不出来 —— 所以四张卡都是 `internal` 纯入参组件，这里直接喂数据。
 * 未登录那一态例外：Robolectric 里没有 Cookie，整页 `LibrarySeatScreen` 会稳定停在
 * 登录卡上，于是连顶栏与页脚一起截。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class LibrarySeatScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val libraries = listOf(
        LibrarySeatLibrary(id = 1, name = "图书馆主楼", floor = "3F", isOpen = true),
        LibrarySeatLibrary(id = 2, name = "分馆", floor = "2F", isOpen = false),
    )

    /**
     * 八个格子把**每一种状态**都排上：可选、被占、状态 2 / 4 / 5、码表之外的状态。
     * 这样"图例说的和图上画的是不是一回事"才看得出来。
     */
    private val layout = LibrarySeatLayout(
        maxX = 6,
        maxY = 2,
        seats = listOf(
            LibrarySeatSeat(x = 0, y = 0, key = "a1", type = 1, name = "A1", seatStatus = 0, occupied = false),
            LibrarySeatSeat(x = 1, y = 0, key = "a2", type = 1, name = "A2", seatStatus = 0, occupied = true),
            LibrarySeatSeat(x = 2, y = 0, key = "a3", type = 1, name = "A3", seatStatus = 2, occupied = false),
            LibrarySeatSeat(x = 3, y = 0, key = "a4", type = 1, name = "A4", seatStatus = 4, occupied = false),
            LibrarySeatSeat(x = 4, y = 0, key = "a5", type = 1, name = "A5", seatStatus = 5, occupied = false),
            LibrarySeatSeat(x = 5, y = 0, key = "a6", type = 1, name = "A6", seatStatus = 9, occupied = false),
            LibrarySeatSeat(x = 0, y = 1, key = "b1", type = 1, name = "B1", seatStatus = 1, occupied = false),
            LibrarySeatSeat(x = 1, y = 1, key = "b2", type = 1, name = "B2", seatStatus = 0, occupied = false),
        ),
    )

    private val reservation = LibrarySeatReservation(
        status = 1,
        libraryName = "图书馆主楼",
        libraryFloor = "3F",
        seatName = "A-12",
        date = "10-29 08:00",
        expDate = "10-29 18:00",
        diff = "03:24:10",
        holdDate = null,
    )

    private fun wrap(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    /** 未登录：整页（Robolectric 里没有 traceint 的 Cookie，所以这一态是确定的）。 */
    @Test
    fun `未登录整页`() {
        wrap { LibrarySeatScreen(onBack = {}) }
        composeRule.onNodeWithText("还没有登录图书馆座位").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/377-seat-login.png")
    }

    /** 已登录 + 选座：图例必须在，且要说清"其余颜色点不动"。 */
    @Test
    fun `选座与图例`() {
        wrap {
            LazyColumn {
                item {
                    SeatBrowserCard(
                        libraries = libraries,
                        selectedLibId = 1,
                        layout = this@LibrarySeatScreenshotTest.layout,
                        mapLoading = false,
                        selectedSeat = null,
                        actionLoading = false,
                        onSelectLibrary = {},
                        onSelectSeat = {},
                        onReserve = {},
                        onOpenWeb = {},
                    )
                }
            }
        }
        // 八个格子里只有 A1 / B1 / B2 三个是"未占且状态 ≤ 1"
        composeRule.onNodeWithText("本图可点 3 个").assertIsDisplayed()
        composeRule.onNodeWithText("其余颜色是学校页面标的其他状态，点不动。").assertIsDisplayed()
        // 两个馆都在，未开放的那个要标出来（不标就会让人以为点开了没座位是 App 的毛病）
        composeRule.onNodeWithText("分馆 2F（未开放）").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/378-seat-browser.png")
    }

    /** 已有预约：座位号当主角，剩余 / 到期做成胶囊。 */
    @Test
    fun `当前预约`() {
        wrap {
            LazyColumn {
                item {
                    ReservationCard(
                        reservation = reservation,
                        sToken = "tok-1",
                        actionLoading = false,
                        onOpenWeb = {},
                        onHold = {},
                        onHoldConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText("A-12").assertIsDisplayed()
        composeRule.onNodeWithText("剩余 03:24:10").assertIsDisplayed()
        // 改造前卡里有四行灰字（预约日期 / 到期 / 剩余 / 暂离）。换主视觉卡**不是删信息**：
        // 四个字段都还得在，所以逐个钉住。
        composeRule.onNodeWithText("开始 10-29 08:00").assertIsDisplayed()
        composeRule.onNodeWithText("到期 10-29 18:00").assertIsDisplayed()
        composeRule.onNodeWithText("取消预约").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/379-seat-reservation.png")
    }

    /** 深色下的预约主视觉卡（hero 是新加的，深色底上的白字/胶囊对比度只能看图确认）。 */
    @Test
    fun `当前预约 深色`() {
        wrap(dark = true) {
            LazyColumn {
                item {
                    ReservationCard(
                        reservation = reservation,
                        sToken = null,
                        actionLoading = false,
                        onOpenWeb = {},
                        onHold = {},
                        onHoldConfirm = {},
                        onCancel = {},
                    )
                }
            }
        }
        // 没有 sToken 时"取消预约"点不了，就不该占一个按钮位（那是"预约规则"的位置）
        composeRule.onAllNodesWithText("取消预约").assertCountEquals(0)
        composeRule.onNodeWithText("预约规则").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/380-seat-reservation-dark.png")
    }

    /** 学号必须脱敏：这一页原先把接口给的学号整串印出来。 */
    @Test
    fun `登录者卡里的学号要脱敏`() {
        wrap {
            UserCard(LibrarySeatUser(name = "张三", school = "烟台理工学院", studentNo = "202408501222"))
        }
        composeRule.onNodeWithText("学号 ********1222", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("202408501222", substring = true).assertCountEquals(0)
    }
}
