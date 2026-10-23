package com.pocket.xiaoli.ui.secondclass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.secondclass.SecondClassGrowthRecord
import com.pocket.xiaoli.data.secondclass.SecondClassTranscript
import com.pocket.xiaoli.data.secondclass.SecondClassTranscriptModule
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalAppearanceMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
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
 * 第二课堂成绩单页的四态基线。
 *
 * ## 为什么要基线（而不是只写单测）
 *
 * 这一页是"把第三方桌面网页换成原生界面"的产物（架构文档 §11.11），
 * 它的价值全在**排版**上：学分够不够醒目、模块的"已得 / 要求"会不会挤成两行、
 * 长记录标题有没有被截成看不懂的半句。这些**单测查不出来**，只有把图渲出来才看得见
 * （同 `100/101/102-course-cell-*` 的教训）。
 *
 * ## ⚠️ 关于下面这些**测试固定数据**
 *
 * 它们是**测试夹具，不是产品数据**：只活在 `src/test` 里，
 * 既不会进 `assets/`、也不会随包发布（`CLAUDE.md` 约束 2「禁止模拟数据」管的是产物）。
 * 模块名用了烟理现行的**六大模块**（出处见架构文档 §11.2），因为那正是界面要面对的真实口径。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SecondClassTranscriptScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 测试夹具：一份"有学分、有记录"的成绩单。 */
    private val sample = SecondClassTranscript(
        totalCredit = "62.5",
        modules = listOf(
            SecondClassTranscriptModule(name = "思想政治", earnedCredit = "12", requiredCredit = "12"),
            SecondClassTranscriptModule(name = "创新创业", earnedCredit = "8", requiredCredit = "12"),
            SecondClassTranscriptModule(name = "社会实践", earnedCredit = "10", requiredCredit = "10"),
            SecondClassTranscriptModule(name = "志愿服务", earnedCredit = "6", requiredCredit = "8"),
            SecondClassTranscriptModule(name = "文体活动", earnedCredit = "20", requiredCredit = "20"),
            SecondClassTranscriptModule(name = "劳动教育", earnedCredit = "6.5", requiredCredit = "8"),
        ),
        // 成长记录是**平行的第二张表**（另一个端点）。这里的顺序刻意与模块顺序一致，
        // 好让这次模型改动**不改变渲染结果** —— verify 模式下那四张基线会证明这一点。
        records = listOf(
            SecondClassGrowthRecord(
                id = "1",
                title = "青年大学习主题团课（第十二季）",
                credit = "2",
                module = "思想政治",
                occurredAt = "2026-03-12",
            ),
            SecondClassGrowthRecord(
                id = "2",
                title = "校园文明志愿服务（东门岗）",
                credit = "1.5",
                module = "志愿服务",
                occurredAt = "2026-05-20",
            ),
        ),
    )

    private val cases = listOf(
        "120-secondclass-transcript" to SecondClassTranscriptState.Loaded(sample),
        "121-secondclass-transcript-empty" to SecondClassTranscriptState.Loaded(SecondClassTranscript()),
        "122-secondclass-need-login" to SecondClassTranscriptState.NeedLogin,
        "123-secondclass-failed" to SecondClassTranscriptState.Failed(
            message = "读取第二课堂数据失败（HTTP 500）",
            detail = "java.io.IOException: unexpected end of stream",
        ),
    )

    /**
     * ⚠️ 四张图刻意用**同一次 `setContent`** 驱动（`setContent` 一个测试只能调一次，
     * 第二次会抛 "has already set content"）—— 同 `HomeCourseCardScreenshotTest`。
     */
    @Test
    fun `成绩单四态截图`() {
        val current = mutableStateOf(cases.first().second)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
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
                        SecondClassTranscriptContent(state = current.value)
                    }
                }
            }
        }

        cases.forEach { (name, state) ->
            composeRule.runOnIdle { current.value = state }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        }
    }

    /**
     * 守门人：胶囊动作的触摸区必须 ≥ 44dp。
     *
     * ⚠️ 这条**在截图上看不出来**（一颗 30dp 的胶囊和 44dp 的在图里几乎一样），
     * 但手指点不准。判据落在语义树上 —— 同 `ProactiveBubbleScreenshotTest.关闭键的触摸高度达标`、
     * `AssistantMessageAreaScreenshotTest` 的 chips。
     */
    @Test
    fun `去登录的触摸高度达标`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                SecondClassTranscriptContent(state = SecondClassTranscriptState.NeedLogin)
            }
        }

        composeRule.onNodeWithText("打开登录页").assertHeightIsAtLeast(44.dp)
    }

    /**
     * 「已得 / 要求」的拼法：缺哪一半就只显示另一半，**绝不编造 0**。
     *
     * 编造 0 会把"服务端没给要求学分"说成"要求 0"，那是**说错话**而不是缺信息；
     * 而且单位未确认（烟理是积分置换学分），任何补零都可能在错的量纲上补。
     */
    @Test
    fun `学分文本缺一半时不补零`() {
        assertEquals("12 / 12", moduleCreditText(SecondClassTranscriptModule("a", "12", "12")))
        assertEquals("8", moduleCreditText(SecondClassTranscriptModule("a", earnedCredit = "8")))
        assertEquals("要求 12", moduleCreditText(SecondClassTranscriptModule("a", requiredCredit = "12")))
        assertEquals("--", moduleCreditText(SecondClassTranscriptModule("a")))
        assertEquals("--", moduleCreditText(SecondClassTranscriptModule("a", "  ", "")))
    }
}
