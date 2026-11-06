package com.pocket.xiaoli.ui.onboarding

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.school.SchoolOption
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首次选校页：整页外观 + 三条交互约定。
 *
 * ## 这几张钉什么
 *
 * - `340/341`：浅色 / 深色整页。这一页现在是**两组**（校园服务已收录 / 暂未收录），
 *   钉的是"分组标题在、未收录那组的图标明显弱一档" —— 如果两组看起来一样醒目，
 *   用户就会以为山东理工也有电费、洗衣这些入口。
 * - `399`：已经选过一所时的样子（描边 + 勾）。从第二步返回时看不出选过，
 *   用户会再点一次、再登录一次。
 * - 交互只测**回传的是哪一所**：这个 id 会直接进 `switchAcademicSchool`，
 *   选错学校 = 后面所有教务请求打到别的学校，而截图看不出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FirstRunOnboardingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 校名与城市取真实名单里的条目；只给四所（两组各两所）—— 判据与数量由 `SchoolOptionsTest` 钉。
     *
     * `protocolVerified` 说的是**这套协议**跑通过没有（决定副标题挂不挂「待验证」）。
     * "这所学校本身测过没有"是另一件事（`LOGIN_PROVEN_SCHOOL_IDS`），它不进这里 ——
     * 逐校实测目前只有 1 所，挂到每行就等于 12 行里 11 个相同标记，标记会变装饰。
     */
    private val options = listOf(
        SchoolOption("YIT", "烟台理工学院", "烟台", "正方教务", hasCampusConfig = true, protocolVerified = true),
        SchoolOption("YTU", "烟台大学", "烟台", "正方教务", hasCampusConfig = true, protocolVerified = true),
        SchoolOption("SDUT", "山东理工大学", "淄博", "正方教务", hasCampusConfig = false, protocolVerified = true),
        SchoolOption("YJIT", "燕京理工学院", "廊坊", "强智教务", hasCampusConfig = false, protocolVerified = false),
    )

    @Test
    fun `首次选校页截图`() {
        setContent(selected = null)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/340-first-run-school-light.png")
        dark.value = true
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/341-first-run-school-dark.png")
    }

    @Test
    fun `已选过一所时那张卡带选中态`() {
        dark.value = false
        setContent(selected = "YJIT")
        // 只靠描边颜色算没做完：读屏用户也要知道哪一所已选，所以断言语义节点
        composeRule.onNodeWithContentDescription("已选择").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("已选择").assertCountEquals(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/399-first-run-school-selected.png")
    }

    @Test
    fun `点未收录校园服务的学校回传那所的 id`() {
        var picked: SchoolOption? = null
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(options = options, onPick = { picked = it }, onSkip = {})
            }
        }
        composeRule.onNodeWithText("山东理工大学").performClick()
        composeRule.waitForIdle()
        assertEquals("SDUT", picked?.id)
    }

    @Test
    fun `点烟大回传烟大的 id 而不是校区 id`() {
        var picked: SchoolOption? = null
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(options = options, onPick = { picked = it }, onSkip = {})
            }
        }
        composeRule.onNodeWithText("烟台大学").performClick()
        composeRule.waitForIdle()
        assertEquals("YTU", picked?.id)
    }

    @Test
    fun `先随便看看不选学校`() {
        var picked: SchoolOption? = null
        var skipped = false
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(options = options, onPick = { picked = it }, onSkip = { skipped = true })
            }
        }
        composeRule.onNodeWithText("先随便看看").performClick()
        composeRule.waitForIdle()
        assertNull(picked)
        assertEquals(true, skipped)
    }

    @Test
    fun `强智那条链路当场写着待验证`() {
        setContent(selected = null)
        // 副标题是 `城市 · 教务系统 · 待验证`：强智从未跑通过真账号，不当面说清，
        // 用户登录失败时只会怀疑自己的账号。
        // ⚠️ 用 assertExists 而不是 assertIsDisplayed —— 这张卡在最下面，
        // 是否落在首屏随字号与皮肤变，而"写没写这句话"与在不在屏内无关。
        composeRule.onNodeWithText("待验证", substring = true).assertExists()
        // 两所已收录的都标「烟台 · 正方教务」，而只有强智那条带「待验证」——
        // 数量本身就是判据：3 所里 1 所待验证
        composeRule.onAllNodesWithText("烟台 · 正方教务", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("待验证", substring = true).assertCountEquals(1)
    }

    private val dark = mutableStateOf(false)

    private fun setContent(selected: String?) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark.value,
            ) {
                FirstRunSchoolPicker(
                    options = options,
                    selectedSchoolId = selected,
                    onPick = {},
                    onSkip = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
