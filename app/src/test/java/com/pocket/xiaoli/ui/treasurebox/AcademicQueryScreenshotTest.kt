package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicSummary
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「修课情况」**整页**基线 + 两条只有语义树能断的约定。
 *
 * ## 为什么要整页基线
 *
 * 这一页此前**一张基线图都没有** —— 于是"零分模块没有进度条""同一个视图画了两遍"
 * "还差 N 门 说了两遍"这类问题，全得靠用户先发现（`docs/UI验收手册.md` 里那条
 * 「反馈说这一页不好看时，要补**整页**基线」就是为这种情况写的）。
 *
 * 整页由**纯入参组件**拼出来（`SummaryCard` / `CourseStatsCard` / `AcademicFilterBar` /
 * `academicTreeSections` 都是 internal 的），所以这里能渲真页面而不需要 ViewModel。
 *
 * ## 判据里最值钱的两条（都不是像素能看见的）
 *
 * 1. **「还差 N 门」只说一遍**：美化时它曾同时出现在药丸和下面那行小字里 ——
 *    图上是两处灰字，人眼扫过去不一定当回事，但它正是用户反复抱怨的"重复信息"；
 * 2. **0% 的条必须存在**（`ProgressBarRangeInfo`）：那是"一门都没过"的模块，
 *    只在图上判断"轨道淡不淡"是不可靠的。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AcademicQueryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val types = listOf(
        AcademicTypeInfo(
            name = "主修", id = "root", requiredCredit = "170", earnedCredit = "95.0", missedCredit = "75.0",
        ),
        AcademicTypeInfo(
            name = "通识教育平台", id = "p1", parentId = "root", depth = 1,
            requiredCredit = "53.5", earnedCredit = "49.5", missedCredit = "4.0",
            requiredCount = "35", reachedCount = "29", missedCount = "6",
        ),
        AcademicTypeInfo(
            name = "思想政治类课程", id = "t1", parentId = "p1", depth = 2,
            requiredCredit = "18", earnedCredit = "17.0", missedCredit = "1.0",
            requiredCount = "14", reachedCount = "12", missedCount = "2",
            totalCourses = "3", passedCourses = "1",
        ),
        AcademicTypeInfo(
            name = "专业核心课程模块", id = "t2", parentId = "root", depth = 1,
            requiredCredit = "19.0", earnedCredit = "0.0", missedCredit = "19.0",
            requiredCount = "9", reachedCount = "0", missedCount = "9",
        ),
    )

    private val courses = listOf(
        AcademicCourseItem(
            typeName = "思想政治类课程", typeId = "t1", courseName = "思想道德修养与法律基础",
            credit = "2.0", nature = "必修", category = "本科", maxGrade = "83", score = "83",
            gradePoint = "3.3", hours = "理论(2.0)", status = "已修", term = "2024-2025 第1学期",
        ),
        AcademicCourseItem(
            typeName = "思想政治类课程", typeId = "t1", courseName = "形势与政策",
            credit = "2.0", nature = "必修", category = "本科", status = "未修",
            suggestedTerm = "2026-2027 第1学期",
        ),
        // 在修 / 未过 各一条：**这正是旧实现分不开的两档**
        //（旧代码按 `在读` 判，而解析层给的词是「在修」⇒ 在修的课与未修一样是灰的）。
        // 基线里没有"在修"的课，这个 bug 就永远看不出来 —— 现在它在这张图上。
        AcademicCourseItem(
            typeName = "思想政治类课程", typeId = "t1", courseName = "马克思主义基本原理",
            credit = "3.0", nature = "必修", category = "本科", status = "在修",
            hours = "理论(3.0)", term = "2025-2026 第2学期",
        ),
        AcademicCourseItem(
            typeName = "思想政治类课程", typeId = "t1", courseName = "中国近现代史纲要",
            credit = "2.0", nature = "必修", category = "本科", status = "未过",
            maxGrade = "55", score = "51", makeupScore = "55", term = "2023-2024 第2学期",
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
                    val expanded = remember { mutableStateListOf("t1") }
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            // 常显操作行也在基线里：两个按钮与「已修课程」同排，
                            // 360dp 屏上会不会挤掉只有整页图能确认。
                            AcademicActionsHeader(onCopyDiagnostics = {}, onLogout = {})
                        }
                        item {
                            SummaryCard(
                                summary = AcademicSummary(
                                    gpa = "3.42", plannedTotal = "48", plannedPassed = "41",
                                    plannedFailed = "2", plannedMissed = "5", plannedIn = "1",
                                    unplannedPassed = "6", unplannedFailed = "1",
                                    studentId = "202408501222",
                                ),
                                progress = CreditProgress(earned = 95f, required = 170f),
                            )
                        }
                        item {
                            // `CourseStatsCard` 是 private —— 这一页的计划内/计划外统计格
                            // 由 `SummaryCard` 那条路径一同覆盖，这里不为了截一张图去改生产代码的可见性。
                        }
                        item {
                            // 2026-10-28 新增：学分构成条（段宽=要求学分，实心=已获）
                            CreditCompositionBar(creditSegments(types))
                        }
                        item {
                            AcademicFilterBar(
                                filter = CourseFilter.ALL,
                                query = "",
                                badges = mapOf(
                                    CourseFilter.UNTAKEN to 6,
                                    CourseFilter.PASSED to 41,
                                    CourseFilter.SHORT_CREDIT to 2,
                                ),
                                onFilter = {},
                                onQuery = {},
                            )
                        }
                        item {
                            // 小节标题（含「展开全部课程」）也在基线里 —— 它是这一页的
                            // 主要动作入口，不渲它这张基线就漏掉一块。
                            AcademicTreeHeader(
                                typeCount = types.size,
                                courseGroupCount = 1,
                                expandedCount = if (expanded.contains("t1")) 1 else 0,
                                onToggleAll = {},
                            )
                        }
                        academicTreeSections(
                            types = types,
                            courses = courses,
                            isExpanded = { expanded.contains(it) },
                            onToggle = { id ->
                                if (expanded.contains(id)) expanded.remove(id) else expanded.add(id)
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `整页基线`() {
        renderPage()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/141-academic-page.png")
    }

    @Test
    fun `整页基线 深色`() {
        renderPage(dark = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/142-academic-page-dark.png")
    }

    @Test
    fun `还差门数只说一遍`() {
        renderPage()

        // 药丸里一处（「还差 6 门」）；下面那行上下文里**不该**再出现一次。
        // 美化时这里曾经是两处 —— 正是用户反复报的"重复信息"。
        composeRule.onAllNodesWithText("还差 6 门", substring = true).assertCountEquals(1)
        // 上下文行仍要说清"门次/到达"这类来龙去脉
        composeRule.onNodeWithText("门次 35 · 到达 29", substring = true).assertExists()
    }

    /**
     * **四档课程状态色并排**（2026-10 用户要求「正在修课和未修课也用不同颜色隔开」）。
     *
     * 单独一张的原因：这一页很长，四档状态散在列表各处，**没有一张图能同时看到它们** ——
     * 而"四档看起来是不是真的分得开"只能靠并排看。左边还有各档的状态色条。
     */
    @Test
    fun `课程状态色四档`() {
        val toneType = AcademicTypeInfo(name = "思想政治类课程", id = "tt", depth = 2)
        val toneCourses = listOf(
            AcademicCourseItem(
                typeName = "思想政治类课程", typeId = "tt", courseName = "思想道德修养与法律基础",
                credit = "2.0", maxGrade = "83", status = "已修", gradePoint = "3.3",
                hours = "理论(2.0)", nature = "必修", term = "2024-2025 第1学期",
            ),
            AcademicCourseItem(
                typeName = "思想政治类课程", typeId = "tt", courseName = "马克思主义基本原理",
                credit = "3.0", status = "在修", hours = "理论(3.0)", nature = "必修",
                term = "2025-2026 第2学期",
            ),
            AcademicCourseItem(
                typeName = "思想政治类课程", typeId = "tt", courseName = "形势与政策",
                credit = "2.0", status = "未修", nature = "必修", suggestedTerm = "2026-2027 第1学期",
            ),
            AcademicCourseItem(
                typeName = "思想政治类课程", typeId = "tt", courseName = "中国近现代史纲要",
                credit = "2.0", maxGrade = "55", score = "51", status = "未过",
                makeupScore = "55", retakeScore = "62", nature = "必修", term = "2023-2024 第2学期",
            ),
        )
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    LazyColumn {
                        academicTreeSections(
                            types = listOf(toneType),
                            courses = toneCourses,
                            isExpanded = { true },
                            onToggle = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/143-course-status-tones.png")
    }

    /**
     * 课程表：**一行一门，脚注与四档状态都在**。
     *
     * 表格化最怕的不是难看而是**错列** —— 错列在像素图上看着挺整齐，只有语义树能抓到；
     * 而"挂科补考的两条成绩"和"在修"这两档状态是改造前卡片里明确有的信息，
     * 换成表格时最容易顺手丢掉，所以在这里各钉一条。
     *
     * ⚠️ 这里**单独渲这张表**而不是在整页里找它：整页很长，`LazyColumn` 只组合
     * 视口内的项，表格落在折叠线以下时 `assertIsDisplayed` 与 `assertExists` 都会红 ——
     * 那种红是布局位置问题，不是这张表的问题。
     */
    /** 渲这张表；`narrow` = 套一层 360dp 的宽度约束（页边距与卡片内边距都在表内部）。 */
    private fun setTable(narrow: Boolean = false) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (narrow) {
                        Box(modifier = Modifier.width(360.dp).fillMaxHeight()) { AcademicCourseTable(courses) }
                    } else {
                        AcademicCourseTable(courses)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `课程表一行一门且脚注不丢`() {
        setTable()

        // 表头只出现一次（列名在别处都是"平均学分绩点"这类整词，不会撞）
        composeRule.onAllNodesWithText("绩点").assertCountEquals(1)
        composeRule.onNodeWithText("思想道德修养与法律基础").assertIsDisplayed()
        // 挂科补考：教务同一行给两列（成绩 51 / 最大成绩 55），只显高的那个就是把信息丢了
        composeRule.onNodeWithText("原 51 补 55").assertIsDisplayed()
        // 没修过的课没有实际学期，给的是学校的**建议**学期（两者是不同的列，不能混）
        composeRule.onNodeWithText("必修 · 建议 2026-2027 第1学期").assertIsDisplayed()
        // 四档状态各归其位；"已修"若出现两次就说明状态被说了两遍（改造前的卡片就是这样）
        composeRule.onNodeWithText("在修").assertIsDisplayed()
        composeRule.onAllNodesWithText("已修").assertCountEquals(1)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/363-academic-course-table.png")
    }

    /**
     * 课程表的 **360dp 窄屏**版。
     *
     * 列宽是**定值**（学分 40 / 成绩 54 / 绩点 42 / 状态 46 = 182dp），剩下的都归课程名 ——
     * 360dp 上课程名只剩约 105dp，长课名会不会折成三行、把行高优势吃回去，
     * 只有渲出来才看得出来（数字列本身不会被挤，它们右对齐且留白在左边）。
     */
    @Test
    fun `课程表 360dp 窄屏`() {
        setTable(narrow = true)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/364-academic-course-table-narrow.png")
    }

    /** 学分构成条：段宽按**要求学分**，所以拿不到分母的节点必须整段不参与（不是画成 0 宽）。 */
    @Test
    fun `学分构成条的取数规则`() {
        val segments = creditSegments(types)

        // 夹具里 depth==1 的有两个：通识教育平台(53.5) 与 专业核心课程模块(19.0)
        assertEquals(listOf("通识教育平台", "专业核心课程模块"), segments.map { it.name })
        // 段宽之比 = 要求学分之比（不是"两条一样宽"）
        assertEquals(53.5f, segments.first().required, 0.01f)
        assertEquals(49.5f, segments.first().earned, 0.01f)
        assertEquals(0f, segments[1].earned, 0.01f)
        // 0% 也要给出 ratio=0f（那是"一门都没过"，必须看得见），缺分母的不参与
        assertEquals(0f, segments[1].ratio!!, 0.01f)
        assertTrue("根节点（depth 0）不参与构成条，否则和平台层重复计数", segments.none { it.name == "主修" })
        assertTrue(
            "叶子层（depth 2）同样不参与",
            segments.none { it.name == "思想政治类课程" },
        )
    }

    @Test
    fun `没有分母时构成条不编数据`() {
        val noRequired = listOf(
            AcademicTypeInfo(name = "其他选修课程", id = "p1", depth = 1, requiredCredit = "0.0", earnedCredit = "4.0"),
            AcademicTypeInfo(name = "没给要求的平台", id = "p2", depth = 1, earnedCredit = "3.0"),
        )

        assertEquals(emptyList<CreditSegment>(), creditSegments(noRequired))
    }
}
