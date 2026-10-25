package com.pocket.xiaoli.ui.shower

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.qzxy.QzxyCampusUserInfo
import com.pocket.xiaoli.data.qzxy.QzxyUseCode
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
 * 洗澡页**余额卡**（`UseCodeCard` 的上半张）快照。
 *
 * ## 为什么补这条
 *
 * 2026-10-24 把「校园卡余额」改成「趣智钱包余额」时，这张卡**一条基线都没有** ——
 * 只过了编译和单测，"改完长什么样、有没有和下面的使用码挤在一起"全靠肉眼遗憾。
 * 余额是用户进页面第一眼要看的东西，文案又刚刚被纠正过一次，必须有图守着。
 *
 * ## 四种状态各自守什么
 *
 * | 文件 | 状态 | 守的东西 |
 * |---|---|---|
 * | `330` | 已签约、有余额 | 大数字 + 「趣智钱包实时余额」+ 上次消费 |
 * | `331` | 查询中 | 「查询中…」而不是 `¥0.00`；「正在读取趣智钱包」 |
 * | `332` | 未签约（`amount = null`）| 「未签约」+「未签约免密支付，拿不到实时余额」 |
 * | `333` | 深色 | 34sp 数字与弱化文案在暗底上的对比度 |
 * | `346` | 登录被挤掉（`authExpired`）| 「登录已失效」而不是「未签约」——这是 2026-10-25 用户报的 bug |
 *
 * ⚠️ 未登录不在覆盖范围内：这张卡在 `state.isLoggedIn` 分支里，未登录时页面显示的是
 * `LoginCard`（见 `ShowerScreen` 第 269 行附近的 `if`）。别为了凑状态去渲不可达的页面。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ShowerBalanceScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 一个用例：文件名 + 页面状态 + 是否深色。 */
    private data class Case(
        val name: String,
        val state: ShowerUiState,
        val dark: Boolean = false,
    )

    /**
     * 一份完整的、已开启的使用码。
     *
     * 带上它是为了让快照渲出**整张卡**：余额区下面是使用码区，只看余额区会漏掉
     * "余额变长后有没有把分隔线/使用码推歪"这类问题。
     */
    private val useCode = QzxyUseCode(
        useCode = "27389960",
        useCodeStatus = 1,
        useCodeStartTime = "10-20 18:32",
        resetAvailability = 1,
    )

    private val cases = listOf(
        Case(
            "330-shower-balance-signed",
            ShowerUiState(
                campusBalance = QzxyCampusUserInfo(amount = "16.76", signStatus = 1),
                lastConsume = 0.35,
                useCode = useCode,
            ),
        ),
        Case(
            "331-shower-balance-loading",
            ShowerUiState(loadingAccount = true, loadingUseCode = true),
        ),
        Case(
            "332-shower-balance-unsigned",
            ShowerUiState(
                campusBalance = QzxyCampusUserInfo(signStatus = 0),
                useCode = useCode,
            ),
        ),
        Case(
            "333-shower-balance-dark",
            ShowerUiState(
                campusBalance = QzxyCampusUserInfo(amount = "16.76", signStatus = 1),
                lastConsume = 0.35,
                useCode = useCode,
            ),
            dark = true,
        ),
        Case(
            "346-shower-balance-expired",
            ShowerUiState(
                // 登录被挤掉时余额必须清空：留着旧数字会让人误以为还能洗
                campusBalance = null,
                authExpired = true,
                lastConsume = 0.35,
                useCode = useCode,
            ),
        ),
    )

    @Test
    fun `洗澡余额卡截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                // 页面底色用真实 token（`surfaceBase`），否则卡片与白底/暗底的边界看不出来。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(16.dp)
                ) {
                    UseCodeCard(
                        state = case.state,
                        nowMs = 0L,
                        onCopy = {},
                        onGenerate = {},
                        onFirstClaim = {},
                        onClaim = {},
                        onDiscard = {},
                        onToggle = {},
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
