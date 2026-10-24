package com.pocket.xiaoli.ui.electricity

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
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
        render(state(balance = "12.40"))

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/319-electricity-low.png")

        composeRule.onNodeWithText("余额偏低").assertExists()
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
}
