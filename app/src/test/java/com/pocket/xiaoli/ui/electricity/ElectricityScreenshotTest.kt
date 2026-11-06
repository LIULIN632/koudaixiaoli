package com.pocket.xiaoli.ui.electricity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.electricity.BalanceSample
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.data.electricity.UsageStats
import com.pocket.xiaoli.data.electricity.usageFootnote
import com.pocket.xiaoli.data.electricity.usageStatsOf
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 电费页的五张基线 + 只有语义树能断的状态约定。
 *
 * | 图 | 状态 |
 * |---|---|
 * | 317 | 正常余额（Vivid/Rainbow） |
 * | 318 | 同一屏（Minimal/Pure 深色） |
 * | 319 | 低余额（12.40，低于 20 元提醒线） |
 * | 320 | 欠费（-3.20） |
 * | 321 | 未绑定宿舍 |
 *
 * 余额是**样例数据**，只存在于测试里 —— 不许为了让截图好看往正式逻辑塞假数据。
 * 更新时间写死，避免截图随运行时间变化。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ElectricityScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fetchedAt = LocalDateTime.of(2026, 10, 24, 9, 30)
        .toInstant(ZoneOffset.ofHours(8))
        .toEpochMilli()

    private fun state(
        balance: String? = "36.80",
        weChat: Boolean = true,
        supported: Boolean = true,
        unbound: Boolean = false,
        /** 默认**空统计**：那三张老基线（317-321）里整张用量卡不出现，画面逐字不变。 */
        usage: UsageStats = UsageStats(),
    ) = ElectricityViewState(
        supported = supported,
        unbound = unbound,
        campusName = "南校区",
        dormLabel = "10 号楼 602",
        dormCode = "BS10S0602",
        detail = balance?.let {
            DormElectricityBalance(
                balance = it,
                accountNo = "BS10S0602",
                address = "北校10楼南602",
                accountId = "acc-1",
                itemId = "item-1",
                matchedByCode = true,
                fetchedAtMillis = fetchedAt,
                fromCache = false,
            )
        },
        loading = false,
        error = null,
        weChatInstalled = weChat,
        updatedText = if (balance == null) null else "10-24 09:30 · 实时",
        usage = usage,
    )

    private fun render(state: ElectricityViewState, dark: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = if (dark) AppearanceMode.PURE else AppearanceMode.RAINBOW,
                skinStyle = if (dark) SkinStyle.MINIMAL else SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                ElectricityContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onRefresh = {},
                    onPayment = {},
                    onCopyLink = {},
                    onOpenPortal = {},
                )
            }
        }
    }

    @Test
    fun `正常余额截图 明暗两套`() {
        // 一个用例里不能 setContent 两次，所以用一份可变状态切两套主题
        val dark = mutableStateOf(false)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = if (dark.value) AppearanceMode.PURE else AppearanceMode.RAINBOW,
                skinStyle = if (dark.value) SkinStyle.MINIMAL else SkinStyle.VIVID,
                darkTheme = dark.value,
            ) {
                ElectricityContent(
                    state = state(),
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onRefresh = {},
                    onPayment = {},
                    onCopyLink = {},
                    onOpenPortal = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/317-electricity-light.png")

        composeRule.runOnIdle { dark.value = true }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/318-electricity-dark.png")
    }

    @Test
    fun `低余额截图`() {
        // 默认提醒线是 10 元（2026-11-05 从 20 下调），所以低余额样例给 6.40 才落在线下
        render(state(balance = "6.40"))

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/319-electricity-low.png")

        composeRule.onNodeWithText("余额偏低").assertExists()
        composeRule.onNodeWithText("去微信缴费").assertExists()
    }

    @Test
    fun `十九块四不再被当成低余额`() {
        // 用户原话：「电费剩余 19 元就开始提醒了」。默认线下不应该再弹这张卡。
        render(state(balance = "19.40"))

        composeRule.onNodeWithText("余额偏低").assertDoesNotExist()
        composeRule.onNodeWithText("去微信缴费").assertExists()
    }

    @Test
    fun `欠费截图`() {
        render(state(balance = "-3.20"))

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/320-electricity-overdraft.png")

        composeRule.onNodeWithText("已经欠费").assertExists()
        composeRule.onNodeWithText("已欠费").assertExists()
    }

    @Test
    fun `没装微信时给复制链接 不给微信按钮`() {
        render(state(weChat = false))

        composeRule.onNodeWithText("复制缴费链接").assertExists()
        composeRule.onNodeWithText("去缴费").assertExists()
        composeRule.onNodeWithText("去微信缴费").assertDoesNotExist()
    }

    @Test
    fun `未绑定宿舍截图 并说明下一步`() {
        render(
            state(balance = null, supported = false, unbound = true),
        )

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/321-electricity-unbound.png")

        composeRule.onNodeWithText("先绑定宿舍").assertExists()
        composeRule.onNodeWithText("打开电费入口").assertExists()
        composeRule.onNodeWithText("去微信缴费").assertDoesNotExist()
    }

    // ── 用量趋势卡（原料是本机每日余额采样，不是上游给的历史）──────────────

    /**
     * 固定的八天下降序列。**日期是写死的常数**：走势图要是按"今天"画的，
     * 基线每天都会红（`memory/04-ui-verification.md` 那条纪律）。
     */
    private fun eightDayUsage(): UsageStats = usageStatsOf(
        samples = listOf(60.0, 52.0, 45.0, 40.0, 33.0, 27.0, 20.0, 14.0)
            .mapIndexed { index, value -> BalanceSample(20_260L + index, value) },
        todayDay = 20_267L,
        lowBalanceThreshold = 10.0,
    )

    @Test
    fun `有走势时画折线并报出日均与还能用几天`() {
        val usage = eightDayUsage()
        // 这条同时是"卡片真的按统计出现"的语义断言，不看像素
        assertTrue(usage.plottable)
        render(state(balance = "14.00", usage = usage))

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/400-electricity-usage-trend.png")

        composeRule.onNodeWithText("用量趋势").assertExists()
        composeRule.onNodeWithText("日均花费").assertExists()
        composeRule.onNodeWithText("还能用").assertExists()
        composeRule.onNodeWithText("清空").assertExists()
        // 口径必须在卡上：这几行是推算，不是后勤给的读数
        composeRule.onNodeWithText(usageFootnote(usage), substring = true).assertExists()
    }

    /**
     * 只有一天记录：**不许画线**，只说还要再攒一天。
     *
     * 这条守的是这个功能最容易做错的地方 —— 数据不够时随手渲染出一条贴底的线，
     * 读起来正好就是"钱快用光了"，而事实是"我还不知道"。
     */
    @Test
    fun `只有一天记录时说还看不出来而不是画一条线`() {
        val usage = usageStatsOf(
            samples = listOf(BalanceSample(20_260L, 42.0)),
            todayDay = 20_260L,
        )
        render(state(balance = "42.00", usage = usage))

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/401-electricity-usage-pending.png")

        composeRule.onNodeWithText("用量趋势").assertExists()
        composeRule.onNodeWithText("已有 1 天记录，再查一天就能看到余额变化").assertExists()
        composeRule.onNodeWithText("日均花费").assertDoesNotExist()
    }

    /** 一条采样都没有 → 整张卡不出现，页面与加这个功能之前逐字相同（317-321 那几张基线因此没动）。 */
    @Test
    fun `没有采样时不出现用量卡`() {
        render(state())

        composeRule.onNodeWithText("用量趋势").assertDoesNotExist()
        composeRule.onNodeWithText("日均花费").assertDoesNotExist()
    }
}
