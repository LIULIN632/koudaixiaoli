package com.pocket.xiaoli.ui.shower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 预约卡快照 + 去官方 App 的接线。
 *
 * 服务端没有开放预约接口，这张卡只承诺一件真事：把人送到官方 App 完成占位；
 * 选设备仍由上方设备卡承担（不在这里重复入口，避免点了按钮列表在视野外展开）。
 * 文案不能在服务端没要求预约时谎称"需要预约"，所以两个状态各留一张图：
 * `344` 普通态，`345` 服务端明确要预约（描边转 warning）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ShowerBookingCardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `预约卡截图与按钮接线`() {
        var needsBooking by mutableStateOf(false)
        var opened = 0
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(16.dp)
                ) {
                    BookingCard(
                        needsBooking = needsBooking,
                        onOpenOfficial = { opened++ },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/344-shower-booking-normal.png"
        )

        composeRule.onNodeWithText("打开趣智校园").performClick()
        composeRule.runOnIdle {
            assertEquals(1, opened)
            needsBooking = true
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/345-shower-booking-required.png"
        )
    }
}
