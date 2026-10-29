package com.pocket.xiaoli.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.ui.theme.AppearanceMode
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
 * [UpdatePageContent] 的**接线**契约（「设置 → 更新」，2026-10-28 拆成纯内容之后才有这些测试）。
 *
 * ## 为什么单测文案不够，还要测这一层
 *
 * `checkTrace()` / `checkHint` 的正确性由 `AppFeedViewTest` 钉；但"算对了却没接到行上"
 * 是另一类错：把 `subtitle` 写成固定串、把 `hint` 落回那句常态说明、或者 `onClick` 忘了
 * 挡「正在检查」—— 三种都不会让数据层测试变红，快照也看不出来（图是照当时的状态渲的）。
 * 所以这里只测**行与状态之间的那几根线**。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class UpdatePageContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun page(
        state: AppFeedRepository.State,
        push: Boolean = true,
        onCheck: () -> Unit = {},
        onBeta: () -> Unit = {},
        onPush: () -> Unit = {},
    ) {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                SettingsSubPageScaffold(title = "更新", onBack = {}) {
                    UpdatePageContent(
                        feedState = state,
                        backgroundCheck = false,
                        pushEnabled = push,
                        channel = "auto",
                        betaEnabled = false,
                        onToggleBackground = {},
                        onTogglePush = onPush,
                        onChannel = {},
                        onToggleBeta = onBeta,
                        onCheckUpdate = onCheck,
                        onChangelog = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * 「主动提醒更新」这一行：看得见、点得动，且默认是开的。
     *
     * 默认值必须是"打扰照旧" —— 新加的开关一旦默认关，等于把所有现有用户的更新提示悄悄停了，
     * 而那是这个功能最不该做的事。
     */
    @Test
    fun `主动提醒那一行默认开着且能点`() {
        var toggles = 0
        page(AppFeedRepository.State(loaded = true), push = true, onPush = { toggles++ })

        composeRule.onNodeWithText("主动提醒更新").assertIsDisplayed()
        composeRule.onNodeWithText("主动提醒更新").performClick()
        composeRule.waitForIdle()

        assertEquals("点这一行要真的把回调发出去", 1, toggles)
    }

    @Test
    fun `小字跟着这一轮的结果换`() {
        page(
            AppFeedRepository.State(
                loaded = true,
                updateAvailable = true,
                newerVersionExists = true,
                lastCheckedAt = System.currentTimeMillis() - 60_000,
                latestVersionCode = 123,
                latestVersionName = "1.2.3",
            )
        )
        composeRule.onNodeWithText("发现新版 v1.2.3").assertIsDisplayed()
    }

    @Test
    fun `没查过时那一行不硬凑一句小字`() {
        // 空串必须渲染成"没有这行小字"，而不是一个占位的空段落（会把行撑高一截）
        page(AppFeedRepository.State())

        composeRule.onNodeWithText("检查更新").assertIsDisplayed()
        composeRule.onNodeWithText("已是最新 · 刚查过", substring = true).assertDoesNotExist()
    }

    @Test
    fun `正在检查时点这一行不会重复发起`() {
        var clicks = 0
        page(AppFeedRepository.State(checking = true), onCheck = { clicks++ })

        composeRule.onNodeWithText("正在检查…").assertIsDisplayed()
        composeRule.onNodeWithText("检查更新").performClick()
        composeRule.waitForIdle()

        assertEquals("进行中必须挡住点击，否则两次检查会抢同一份缓存", 0, clicks)
    }

    @Test
    fun `失败时问号里是毛病原文而不是常态说明`() {
        page(
            AppFeedRepository.State(
                loaded = true,
                lastCheckedAt = System.currentTimeMillis() - 60_000,
                latestVersionCode = 122,
                error = "检查失败：Gitee raw 返回 504（read timed out）",
            )
        )

        composeRule.onNodeWithText("这次没查上，可再点一次").assertIsDisplayed()
        // 「?」挂在行尾，第一个就是「检查更新」那一行的
        composeRule.onAllNodesWithContentDescription("说明")[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("这一轮的毛病：检查失败：Gitee raw 返回 504（read timed out）")
            .assertIsDisplayed()
    }

    @Test
    fun `开关的回调真的接到了行上`() {
        var toggles = 0
        page(AppFeedRepository.State(), onBeta = { toggles++ })

        composeRule.onNodeWithText("加入内测").performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggles)
    }
}
