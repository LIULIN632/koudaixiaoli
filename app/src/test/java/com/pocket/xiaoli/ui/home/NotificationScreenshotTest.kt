package com.pocket.xiaoli.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
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
 * 「消息」页外观基线（MIUIX 顶栏 + 分组小标题 + 圆角卡片行）。
 *
 * ## 为什么要有这一组
 *
 * 2026-10-19 之前「通知」是一个 Material3 `AlertDialog`：一段挤在屏幕中间的小字，
 * 公告超过两条就被对话框宽度压成碎句。改成整页之后最容易翻车的三件事都没法靠
 * 编译发现：**正文 4 行封顶后被截断**、**「有新版本」那行的动作提示与正文撞色**、
 * **空态/失败态和正常态长得一样**。所以钉三张图：
 *
 * 1. 170：有新版 + 两条公告（一条 WARN）—— 常态；
 * 2. 171：同上深色 —— 卡片底色与页底的区别是否还在；
 * 3. 172：空态 —— 「暂无消息」不能和「检查失败」混为一谈。
 *
 * ⚠️ MIUIX 的 `Card` / `IconButton` 走 squircle `RuntimeShader`，Robolectric 渲不出来
 * （见 `memory/03-pitfalls.md` §8），所以本页卡片走全站统一的 `AppCard`（普通 Column），
 * 顶栏用 `SmallTopAppBar`（可渲）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class NotificationScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    private val withContent = AppFeedRepository.State(
        announcements = listOf(
            Announcement(
                id = "beta-test-2026-10",
                level = AnnouncementLevel.WARN,
                title = "内测版招募：装了新版的同学请回一句话",
                body = "这一版主要改更新链路与设置页观感。用着没问题也请回一句「正常」，" +
                    "遇到闪退请把「我的 → 关于 → 导出日志」得到的文件发给我们。",
                url = "https://gitee.com/liulin62633/pocket-carp/issues",
            ),
            Announcement(
                id = "holiday-source",
                level = AnnouncementLevel.INFO,
                title = "节假日日历换了新源",
                body = "校历没更新时，节假日会按国务院公布的安排显示。若发现调休对不上，" +
                    "在「我的 → 设置 → 课表」里手动改一次即可覆盖。",
            ),
        ),
        updateAvailable = true,
        latestVersionCode = 70,
        latestVersionName = "0.1.69",
        apkUrl = "https://gitee.com/liulin62633/pocket-carp/releases/download/v0.1.69/koudaixiaoli_v0.1.69.apk",
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        notes = listOf(
            "消息页改成整页列表，公告正文不再被对话框压成碎句",
            "顶栏可以手动重新检查",
        ),
        lastCheckedAt = 1_760_000_000_000L,
        loaded = true,
    )

    @Test
    fun `消息页外观截图`() {
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
                    NotificationScreen(
                        state = withContent,
                        onBack = {},
                        onMarkAllSeen = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/170-notifications-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/171-notifications-dark.png")
    }

    @Test
    fun `消息页空态截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides
                        rememberNavigationEventDispatcherOwner(parent = null),
                ) {
                    NotificationScreen(
                        state = AppFeedRepository.State(loaded = true, lastCheckedAt = 1_760_000_000_000L),
                        onBack = {},
                        onMarkAllSeen = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/172-notifications-empty.png")
    }
}
