package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页顶栏「通知铃铛 + 未读红点」基线（2026-10 布局修复轮补）。
 *
 * ## 为什么单独给这一小块补图
 *
 * 红点原来写在 `IconButton` 的内容槽里，而按钮的 modifier 链上有 `clip(CircleShape)`：
 * 角标被 `offset` 推到圆外，整枚**裁没了** —— "有未读"这件事在界面上根本看不见。
 * 这类"被父级裁切"的问题读代码能看出来，但**没有图就无法证明修好了**，
 * 所以两个态各留一张：有未读必须看得见一枚红点，无未读必须干净。
 *
 * ## 为什么只截铃铛、不截整条顶栏
 *
 * `HomeClockHeader` 左边是 `HH:mm` 时钟，`LaunchedEffect` 每秒推进一次。整行进基准图，
 * 录制与回放必然因为分钟数不同而对不上（试过：`13:18` vs `13:20`，差异只有那两个数字）。
 * 冻时钟要动生产代码的取时方式，所以改成按节点裁剪 —— 用 `testTag("home-notification-bell")`
 * 把画面切到铃铛这一块，时钟根本不在取景框里，图因此与时间无关。
 * （同一手法见 `WeekHeaderScreenshotTest`。）
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeHeaderUnreadDotScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun renderBell(hasUnread: Boolean, imageName: String) {
        composeRule.setContent {
            PocketXiaoliTheme(skinStyle = SkinStyle.VIVID, darkTheme = false) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                CompositionLocalProvider(LocalCampusLayoutMode provides CampusLayoutMode.MODERN) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surfaceBase)
                            .padding(horizontal = skin.screenH, vertical = 24.dp),
                    ) {
                        HomeClockHeader(onOpenNotifications = {}, hasUnread = hasUnread)
                    }
                }
            }
        }
        composeRule.onNodeWithTag("home-notification-bell").captureRoboImage("src/test/screenshots/$imageName.png")
    }

    @Test
    fun `铃铛 有未读`() = renderBell(hasUnread = true, imageName = "245-home-bell-unread")

    @Test
    fun `铃铛 无未读`() = renderBell(hasUnread = false, imageName = "246-home-bell-read")
}
