package com.pocket.xiaoli.ui.courseselect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.network.CourseSelectItem
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
 * 「选课」页的整页基线 + 两条只有语义树能断的约定。
 *
 * 这一页此前**一张基线图都没有**：圆角、余量条、已选/停开徽标都是各写各的，
 * 改一次样式没有任何回归信号。整页 `LoggedInContent` 依赖 ViewModel、在 Robolectric 里起不来，
 * 所以这里用开放成 `internal` 的纯组件拼出同一结构。
 *
 * 基线里刻意并排放三种课：
 * - **已选**（主色条 + 已选徽标）
 * - **已满**（红色余量条 + 已满字样）
 * - **已停开**（红色条 + 已停开徽标，优先于"已选"）
 *
 * 这三档是选课决策里最要紧的结论，**并排**才能看出它们是不是真的分得开。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CourseSelectScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val courses = listOf(
        CourseSelectItem(
            kch = "B1001003", kcmc = "大学英语（三）",
            kclbmc = "通识必修", kcxzmc = "必修",
            xf = "3", zxs = "48", xsxxxx = "3",
            jsxm = "李晓梅", zcmc = "副教授",
            sxrs = "60", yxrs = "58", sxrsmc = "58/60",
            sksj = "周一 1-2节 1-16周", jxdd = "综合楼 A201",
            chosen = true,
        ),
        CourseSelectItem(
            kch = "B2002010", kcmc = "数据结构",
            kclbmc = "专业核心", kcxzmc = "必修",
            xf = "4", zxs = "64", xsxxxx = "4",
            jsxm = "王建国", zcmc = "教授",
            sxrs = "120", yxrs = "90", sxrsmc = "90/120",
            sksj = "周二 3-4节 1-16周", jxdd = "信息楼 B305",
        ),
        // 已停开 + 已选同时出现：状态条必须让位给更紧急的"停开"
        CourseSelectItem(
            kch = "B3003021", kcmc = "马克思主义基本原理",
            kclbmc = "通识必修", kcxzmc = "必修",
            xf = "3", zxs = "48",
            jsxm = "张丽",
            sxrs = "40", yxrs = "40", sxrsmc = "40/40",
            sftkzt = "1",
            chosen = true,
        ),
    )

    private fun renderPage(dark: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp)
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            CourseListHero(
                                courses = courses,
                                chosenCount = 2,
                                isPreSelect = true,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChipText(
                                    text = "全部 3 门 · 已选 2 门",
                                    active = false,
                                    onClick = {},
                                )
                                FilterChipText(
                                    text = "只看已选（2）",
                                    active = true,
                                    onClick = {},
                                )
                            }
                        }
                        items(courses) { CourseSelectCard(it) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `整页基线`() {
        renderPage()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/313-course-select-page.png")
    }

    @Test
    fun `整页基线 深色`() {
        renderPage(dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/314-course-select-page-dark.png")
    }

    /** 满员 / 停开 / 已选三种结论必须同屏可见（它们决定"选不选得上"） */
    @Test
    fun `满员 停开 已选三种结论可见`() {
        renderPage()

        composeRule.onNodeWithText("已满").assertExists()
        composeRule.onNodeWithText("已停开").assertExists()
        // 精确匹配「已选」= 课程卡右上角那枚徽标（hero 与筛选胶囊里是「已选 2 门」这类长文案）；
        // 样本里两门已选，所以正好两枚。
        composeRule.onAllNodesWithText("已选").assertCountEquals(2)
    }

    /**
     * 余量条必须带 `ProgressBarRangeInfo`：条形是画上去的，
     * "满员那条是不是 100%"只能靠语义确认（与「修课情况」的 0% 进度条同一条纪律）。
     */
    @Test
    fun `满员课的余量是百分之百`() {
        renderPage()

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f))).assertExists()
    }
}
