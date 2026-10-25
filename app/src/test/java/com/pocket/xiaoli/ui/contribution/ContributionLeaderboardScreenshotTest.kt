package com.pocket.xiaoli.ui.contribution

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.contribution.ContributionBoard
import com.pocket.xiaoli.data.contribution.ContributionCategory
import com.pocket.xiaoli.data.contribution.ContributionEntry
import com.pocket.xiaoli.data.contribution.ContributionMe
import com.pocket.xiaoli.data.contribution.ContributionPeriod
import com.pocket.xiaoli.data.contribution.ContributionUiState
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「校园共建榜」整页基线 + 三条只有语义树能断的状态约定。
 *
 * ## 两张图
 *
 * | 图 | 内容 |
 * |---|---|
 * | 315 | 已加入（Vivid/Rainbow）：主视觉「8 人在榜」+ 纯昵称列表 + 我的高亮行 |
 * | 316 | 同一屏（Minimal/Pure 深色）：没有领奖台之后，长列表在暗底上的节奏 |
 *
 * 2026-10-24 起榜单不再有分数/名次，样例数据按**昵称拼音**排好 —— 如果排序逻辑回退，
 * 图上的顺序会变，一眼就能看出来。
 *
 * 榜单是**样例数据**，只存在于测试里 —— 不许为了让截图好看往正式逻辑塞假数据。
 * `fetchedAt` 写死，避免"离线缓存 09:30"随运行时间变红。
 *
 * ## 语义约定
 *
 * 1. 没加入时只有「加入共建榜」，不能出现「改名 / 退出」；
 * 2. 加入后才有「改名 / 退出」，并说明是按昵称排序（不再显示名次）；
 * 3. 服务端地址没配时显示「榜单还没接上服务器」，而不是一个永远拉不到的空榜。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ContributionLeaderboardScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 2026-10-24 09:30（UTC+8），只用于缓存时间文案。 */
    private val fetchedAt = LocalDateTime.of(2026, 10, 24, 9, 30)
        .toInstant(ZoneOffset.ofHours(8))
        .toEpochMilli()

    /** 按昵称拼音排好的样例（数字 → b → j → l → m → t → z）。 */
    private val entries = listOf(
        ContributionEntry(1, "313 路"),
        ContributionEntry(2, "笔记搬运工"),
        ContributionEntry(3, "不迟到"),
        ContributionEntry(4, "教三常客"),
        ContributionEntry(5, "鲤鱼王"),
        ContributionEntry(6, "摸鱼佬", mine = true),
        ContributionEntry(7, "图书馆钉子户"),
        ContributionEntry(8, "早八人"),
    )

    private fun joinedState() = ContributionUiState(
        enabled = true,
        nickname = "摸鱼佬",
        configured = true,
        category = ContributionCategory.ALL,
        period = ContributionPeriod.WEEK,
        board = ContributionBoard(
            category = ContributionCategory.ALL,
            period = ContributionPeriod.WEEK,
            entries = entries,
            me = ContributionMe("摸鱼佬"),
            fetchedAt = fetchedAt,
            total = entries.size,
        ),
        loading = false,
        error = null,
    )

    private fun render(state: ContributionUiState, dark: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = if (dark) AppearanceMode.PURE else AppearanceMode.RAINBOW,
                skinStyle = if (dark) SkinStyle.MINIMAL else SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                ContributionLeaderboardContent(
                    state = state,
                    onBack = {},
                    onSelectCategory = {},
                    onSelectPeriod = {},
                    onJoin = { null },
                    onRename = { null },
                    onLeave = {},
                    onRefresh = {},
                )
            }
        }
    }

    @Test
    fun `共建榜截图`() {
        // 一个用例里不能 setContent 两次，所以用一份可变状态切两套主题
        val dark = mutableStateOf(false)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = if (dark.value) AppearanceMode.PURE else AppearanceMode.RAINBOW,
                skinStyle = if (dark.value) SkinStyle.MINIMAL else SkinStyle.VIVID,
                darkTheme = dark.value,
            ) {
                ContributionLeaderboardContent(
                    state = joinedState(),
                    onBack = {},
                    onSelectCategory = {},
                    onSelectPeriod = {},
                    onJoin = { null },
                    onRename = { null },
                    onLeave = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/315-contribution-board-light.png")

        composeRule.runOnIdle { dark.value = true }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/316-contribution-board-dark.png")
    }

    @Test
    fun `没加入时只有加入入口 没有改名退出`() {
        render(
            ContributionUiState(
                enabled = false,
                nickname = "",
                configured = true,
            ),
        )

        composeRule.onNodeWithText("还没加入").assertExists()
        composeRule.onNodeWithText("加入共建榜").assertExists()
        composeRule.onNodeWithText("改名").assertDoesNotExist()
        composeRule.onNodeWithText("退出").assertDoesNotExist()
    }

    @Test
    fun `加入后显示已加入 并有改名退出`() {
        render(
            ContributionUiState(
                enabled = true,
                nickname = "摸鱼佬",
                configured = true,
                board = ContributionBoard(
                    category = ContributionCategory.ALL,
                    period = ContributionPeriod.WEEK,
                    entries = entries,
                    me = ContributionMe("摸鱼佬"),
                    fetchedAt = fetchedAt,
                ),
            ),
        )

        composeRule.onNodeWithText("已加入 · 按昵称排序").assertExists()
        composeRule.onNodeWithText("改名").assertExists()
        composeRule.onNodeWithText("退出").assertExists()
        composeRule.onNodeWithText("还没加入").assertDoesNotExist()
    }

    @Test
    fun `没配服务器时说明原因 而不是永远转圈`() {
        render(ContributionUiState(enabled = false, configured = false))

        composeRule.onNodeWithText("榜单还没接上服务器").assertExists()
        composeRule.onNodeWithText("加入共建榜").assertDoesNotExist()
    }
}
