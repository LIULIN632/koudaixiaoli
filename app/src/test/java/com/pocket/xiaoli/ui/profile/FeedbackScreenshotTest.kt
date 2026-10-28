package com.pocket.xiaoli.ui.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.feedback.FeedbackCategory
import com.pocket.xiaoli.data.feedback.FeedbackDraft
import com.pocket.xiaoli.data.feedback.FeedbackReply
import com.pocket.xiaoli.data.feedback.FeedbackStore
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
 * 反馈页整页基线（2026-10-28 这轮美化之后才有 —— 之前**一张都没有**）。
 *
 * ## 为什么以前录不了、现在能录
 *
 * `FeedbackContent` 吃 `AppContainer`（真 DI，Robolectric 里造不出来），所以这一页长期
 * 只能靠"编译过了"验收。这轮把视觉块拆进 `FeedbackBlocks.kt`（吃数据 + 回调），
 * 于是这里渲的是**生产代码本体**，不是另画的仿制品。
 *
 * ## 三张各钉什么
 *
 * | 图 | 钉的东西 |
 * |---|---|
 * | `357-feedback-light` | 四条渠道格成不成一个模子（图标块 + 名称 + 一句差别）、分类 chip 换行时会不会把字压成竖排 |
 * | `358-feedback-dark-attach` | 深色下展开的设备信息块（`surfaceAlt` 会不会和卡底糊成一片）+ 渠道格的描边在深色上还找不找得到边界 |
 * | `359-feedback-no-server` | 服务端没配好时：主按钮换成「复制反馈内容」、渠道只剩三条（奇数不能把最后一条拉成整行宽）、没有「我提过的」 |
 * | `360/361-feedback-history` | 下半屏（整页图里落在折叠线以下，不单独渲就等于没验）：三种状态胶囊的语义色、开发者回复的引用块、隐私承诺卡 |
 *
 * ⚠️ 时间全部走固定 `NOW`（与 `AccountBindingScreenshotTest.NOW` 同一个值）：
 * 「3 天前提过」这类相对时间直接由 `atMillis` 算，取 UTC 上午，跨时区也是同一天。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FeedbackScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val NOW = 1780826400000L
        const val DAY = 86_400_000L

        val diagnostics = "版本 v1.2.3 (123)\n机型 Google Pixel 5\n系统 Android 13 (API 33)"

        val entries = listOf(
            FeedbackStore.Entry("FB-2K7Q1A", FeedbackCategory.BUG, "课表点开就转圈，一直加载失败", NOW - 3 * DAY),
            FeedbackStore.Entry("FB-8MD3ZP", FeedbackCategory.UX, "周日视图的字太小了，走路时根本看不清", NOW - DAY - 2 * 3_600_000),
            FeedbackStore.Entry("FB-4QW7T2", FeedbackCategory.FEATURE, "想加个空教室预约提醒，抢到之后没人通知我", NOW - 40 * 60_000),
        )

        val pending = mapOf(
            entries[0].ticketId to FeedbackStore.Pending(
                ticketId = entries[0].ticketId,
                category = entries[0].category,
                content = "课表点开就转圈，一直加载失败",
                contact = "",
                includeDiagnostics = true,
                atMillis = entries[0].atMillis,
                diagnostics = diagnostics,
            )
        )

        val replies = mapOf(
            entries[1].ticketId to FeedbackReply(
                "下个版本把周日字号从 11 提到 13，同时给周头加一行高。",
                "10-27 21:40",
            )
        )
    }

    private data class Case(
        val name: String,
        val dark: Boolean,
        val draft: FeedbackDraft,
        val remoteReady: Boolean,
        val showHistory: Boolean,
    )

    private val filled = FeedbackDraft(
        category = FeedbackCategory.BUG,
        content = "导入课表之后，首页的「下一节课」还显示昨天的课。退出重进也一样。",
        contact = "",
    )

    private val cases by lazy {
        listOf(
            Case("357-feedback-light.png", dark = false, draft = filled, remoteReady = true, showHistory = true),
            Case(
                "358-feedback-dark-attach.png",
                dark = true,
                draft = filled.copy(includeDiagnostics = true),
                remoteReady = true,
                showHistory = true,
            ),
            Case("359-feedback-no-server.png", dark = false, draft = filled, remoteReady = false, showHistory = false),
        )
    }

    @Test
    fun `反馈页整页截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val c = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = c.dark,
            ) {
                SettingsSubPageScaffold(title = "反馈", onBack = {}) {
                    FeedbackFormCard(draft = c.draft, sending = false, onDraftChange = {})
                    FeedbackAttachCard(
                        diagnostics = diagnostics,
                        included = c.draft.includeDiagnostics,
                        onToggle = {},
                    )
                    FeedbackSendSection(
                        remoteReady = c.remoteReady,
                        enabled = true,
                        sending = false,
                        onSend = {},
                        onCopy = {},
                        onMail = {},
                        onIssue = {},
                        onShare = {},
                    )
                    if (c.showHistory) {
                        FeedbackHistorySection(
                            entries = entries,
                            pending = pending,
                            replies = replies,
                            sentIds = setOf(entries[1].ticketId, entries[2].ticketId),
                            canCheck = true,
                            checking = false,
                            now = NOW,
                            onCopyEntry = {},
                            onCheckReplies = {},
                        )
                    }
                    FeedbackPrivacyNote()
                }
            }
        }
        cases.forEach { case ->
            current.value = case
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}")
        }
    }

    /**
     * 「我提过的」+ 检查回复 + 隐私承诺 —— 这一屏在整页图里**落在折叠线以下**，
     * 只渲上半页就等于没验（状态胶囊、回复引用块全在图外）。所以单独渲下半屏。
     *
     * 三条账本故意给成三种状态：待重发（黄）/ 有回复（绿 + 引用块）/ 等回复（灰），
     * 加上一张"深色"，钉的是三种语义色在两种底色下分不分得开。
     */
    @Test
    fun `反馈页下半屏截图`() {
        val lower = listOf(
            "360-feedback-history-light.png" to false,
            "361-feedback-history-dark.png" to true,
        )
        val current = mutableStateOf(lower.first())
        composeRule.setContent {
            val (_, dark) = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                SettingsSubPageScaffold(title = "反馈", onBack = {}) {
                    FeedbackHistorySection(
                        entries = entries,
                        pending = pending,
                        replies = replies,
                        sentIds = setOf(entries[1].ticketId, entries[2].ticketId),
                        canCheck = true,
                        checking = false,
                        now = NOW,
                        onCopyEntry = {},
                        onCheckReplies = {},
                    )
                    FeedbackPrivacyNote()
                }
            }
        }
        lower.forEach { case ->
            current.value = case
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.first}")
        }
    }
}
