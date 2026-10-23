package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.secondclass.SecondClassActivity
import com.pocket.xiaoli.data.secondclass.SecondClassActivityStatus
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
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
 * 「我的活动」列表的四态基线（含深色）。
 *
 * 这一页和成绩单页是**同一批**的产物（把桌面网页换成原生界面，架构文档 §11.11），
 * 所以要守的东西也一样：长标题会不会被截成看不懂的半句、状态胶囊会不会把标题挤没、
 * 缺字段的活动会不会显示成一行空白。**这些只有把图渲出来才看得见。**
 *
 * 下面这些是**测试夹具，不进产物**（`CLAUDE.md` 约束 2 管的是产物，不是 `src/test`）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SecondClassActivityListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sample = listOf(
        SecondClassActivity(
            id = "1",
            title = "第十届大学生科技节——软件设计竞赛",
            status = SecondClassActivityStatus.SIGNUP,
            startAt = "2026-10-12 14:00",
            endAt = "2026-10-12 17:00",
            credit = "3",
            location = "三教 S552",
            organizer = "信息工程学院团总支",
        ),
        SecondClassActivity(
            id = "2",
            title = "校园文明志愿服务（东门岗）",
            status = SecondClassActivityStatus.ONGOING,
            startAt = "2026-10-10 08:00",
            endAt = "2026-10-10 18:00",
            credit = "1.5",
            location = "东门",
            organizer = "校青年志愿者协会",
        ),
        SecondClassActivity(
            id = "3",
            title = "青年大学习主题团课（第十二季）",
            status = SecondClassActivityStatus.ENDED,
            startAt = "2026-03-12 19:00",
            credit = "2",
            organizer = "校团委",
        ),
        // 状态不认识 + 字段全缺：什么都不该编造出来（见 statusLabel 的说明）。
        SecondClassActivity(id = "4", title = "劳动教育实践：校园绿化养护"),
    )

    private data class Case(val name: String, val state: SecondClassActivityListState, val dark: Boolean = false)

    private val cases = listOf(
        Case("130-secondclass-activity-list", SecondClassActivityListState.Loaded(sample)),
        Case("131-secondclass-activity-empty", SecondClassActivityListState.Loaded(emptyList())),
        Case(
            "132-secondclass-activity-sparse",
            SecondClassActivityListState.Loaded(
                listOf(
                    SecondClassActivity(id = "4", title = "劳动教育实践：校园绿化养护"),
                    SecondClassActivity(id = "5", title = "已取消的活动示例", status = SecondClassActivityStatus.CANCELLED),
                ),
            ),
        ),
        Case("133-secondclass-activity-dark", SecondClassActivityListState.Loaded(sample), dark = true),
    )

    /** ⚠️ 四张图用**同一次 `setContent`** 驱动（`setContent` 一个测试只能调一次）。 */
    @Test
    fun `我的活动四态截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = case.dark,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                CompositionLocalProvider(
                    LocalAppearanceMode provides AppearanceMode.RAINBOW,
                    LocalCampusLayoutMode provides CampusLayoutMode.MODERN,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surfaceBase)
                            .padding(vertical = skin.spaceMd),
                    ) {
                        SecondClassActivityListContent(state = case.state)
                    }
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }

    /**
     * 守门人：**不认识的状态不编造文案**。
     *
     * 若哪天有人把 `UNKNOWN` 改成返回"未知"，这条会红 —— 那正是要拦的：
     * 把我们的无知当成信息推给用户，不如什么都不显示。
     */
    @Test
    fun `不认识的状态不给文案`() {
        assertNull(statusLabel(SecondClassActivityStatus.UNKNOWN))
        assertEquals("可报名", statusLabel(SecondClassActivityStatus.SIGNUP))
        assertEquals("进行中", statusLabel(SecondClassActivityStatus.ONGOING))
        assertEquals("已结束", statusLabel(SecondClassActivityStatus.ENDED))
        assertEquals("已取消", statusLabel(SecondClassActivityStatus.CANCELLED))
    }
}
