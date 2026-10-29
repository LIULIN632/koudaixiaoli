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
import com.pocket.xiaoli.ui.theme.ServiceAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * **真实**培养方案（烟台理工 · 工程管理，24 个节点）—— 学分与层级照抄
     * `参考/课程.txt`（用户导出的教务页面原文），不是编的小样。
     *
     * 为什么非得用真实形状：这一轮要改的是「平台 / 模块 / 课程类型**三级各长什么样**」，
     * 四行小样里每级只有一两个节点，看不出三件事 ——
     * ① 二十几条横杠叠在一起是不是一堵墙；② 数字列对不对得齐（`95.0 / 170` 与
     * `4.0 / 0.0` 的斜杠根本不在一条线上）；③ **超修**（通识选修 10.0 / 要求 8.0）
     * 和"刚好修满"在界面上有没有区别。
     *
     * 两个同名节点（通识必修与通识选修下各一个「身心健康类课程」）也照抄了 ——
     * 它是"分组必须按 id 不能按名字"那条规矩的真实出处。
     */
    private val realTypes = listOf(
        AcademicTypeInfo("主修", "root", "170.0", "95.0", "75.0"),
        AcademicTypeInfo("通识教育平台", "p1", "53.5", "49.5", "4.0", requiredCount = "35", reachedCount = "29", missedCount = "6", parentId = "root", depth = 1),
        AcademicTypeInfo("通识必修课程模块", "m1", "45.5", "39.5", "6.0", parentId = "p1", depth = 2),
        AcademicTypeInfo("思想政治类课程", "t1", "18.0", "17.0", "1.0", requiredCount = "14", reachedCount = "12", missedCount = "2", parentId = "m1", depth = 3),
        AcademicTypeInfo("语言与工具类课程", "t2", "16.0", "16.0", "0.0", parentId = "m1", depth = 3),
        AcademicTypeInfo("身心健康类课程", "t3", "5.5", "1.5", "4.0", parentId = "m1", depth = 3),
        AcademicTypeInfo("国防军事类课程", "t4", "2.0", "2.0", "0.0", parentId = "m1", depth = 3),
        AcademicTypeInfo("职业规划类课程", "t5", "4.0", "3.0", "1.0", parentId = "m1", depth = 3),
        // 超修：已获 10.0 > 要求 8.0（模块下两条叶子同样超）
        AcademicTypeInfo("通识选修课程模块", "m2", "8.0", "10.0", "0.0", parentId = "p1", depth = 2),
        AcademicTypeInfo("美育类课程", "t6", "2.0", "4.0", "0.0", parentId = "m2", depth = 3),
        AcademicTypeInfo("身心健康类课程", "t7", "2.0", "2.0", "0.0", parentId = "m2", depth = 3),
        // 学校没给要求学分（0.0）却给了已获 4.0 —— 没有分母就没有进度
        AcademicTypeInfo("其他选修课程", "m3", "0.0", "4.0", "0.0", parentId = "p1", depth = 2),
        AcademicTypeInfo("学科专业教育平台", "p2", "76.5", "36.5", "40.0", parentId = "root", depth = 1),
        AcademicTypeInfo("学科基础课程模块", "m4", "45.5", "36.5", "9.0", parentId = "p2", depth = 2),
        AcademicTypeInfo("专业核心课程模块", "m5", "19.0", "0.0", "19.0", parentId = "p2", depth = 2),
        AcademicTypeInfo("专业任选课程模块", "m6", "4.0", "0.0", "4.0", parentId = "p2", depth = 2),
        AcademicTypeInfo("专业限选课程模块", "m7", "8.0", "0.0", "8.0", parentId = "p2", depth = 2),
        AcademicTypeInfo("方向一数字建造", "t8", "8.0", "0.0", "8.0", parentId = "m7", depth = 3),
        AcademicTypeInfo("方向二全过程造价管理", "t9", "8.0", "0.0", "8.0", parentId = "m7", depth = 3),
        AcademicTypeInfo("实践教育平台", "p3", "40.0", "9.0", "31.0", parentId = "root", depth = 1),
        AcademicTypeInfo("综合素质教育模块", "m8", "2.0", "2.0", "0.0", parentId = "p3", depth = 2),
        AcademicTypeInfo("专业实验实践课程模块", "m9", "34.0", "7.0", "27.0", parentId = "p3", depth = 2),
        AcademicTypeInfo("创新创业实践教育模块", "m10", "4.0", "0.0", "4.0", parentId = "p3", depth = 2),
    )

    /** 每个**叶子节点**各给一门已修课：让树按生产默认态渲染（父展开、叶子收起、不喊"暂无明细"）。 */
    private val realCourses = realTypes
        .filter { it.id != "root" && realTypes.none { child -> child.parentId == it.id } }
        .map { type ->
            AcademicCourseItem(
                typeName = type.name, typeId = type.id, courseName = "示例课程",
                credit = "2.0", nature = "必修", maxGrade = "83", score = "83", status = "已修",
            )
        }

    /** 渲真实树的一段（`from`/`to` 是节点下标切片）；父节点展开、叶子收起 = 生产默认。 */
    private fun renderRealTree(from: Int, to: Int, file: String, dark: Boolean = false) {
        val slice = realTypes.subList(from, to)
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    LazyColumn {
                        academicTreeSections(
                            types = slice,
                            courses = realCourses,
                            // 生产默认：有子节点的展开（一眼看到整棵树），叶子收起（课程按需再展开）
                            isExpanded = { id -> slice.any { it.parentId == id } },
                            onToggle = {},
                            // ⚠️ 段色按**全量** realTypes 算：按切片重算的话，
                            // 下半段里的「学科专业教育平台」会拿到上半段第一个平台的颜色
                            platformAccents = platformAccents(creditSegments(realTypes)),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(file)
    }

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
                            // 2026-10-29 新增：成绩分布（构成条说"还差多少"，这张说"已修的分长什么样"）
                            AcademicGradeCard(courses)
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
                            platformAccents = platformAccents(creditSegments(types)),
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

    /**
     * 「还差 N 门」只说一遍。
     *
     * ⚠️ 这里**不**走 `renderPage()`：这一条是**每一行自己**的约定（药丸与它右边那行小字），
     * 而整页很长 —— 上面多一张卡就把这行推到折叠线以下，`LazyColumn` 不再组合它，
     * 断言就从"抓到重复"变成"抱怨找不到节点"。2026-10-29 挂上成绩分布卡时正是这样红的。
     */
    @Test
    fun `还差门数只说一遍`() {
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
                    LazyColumn {
                        academicTreeSections(
                            types = types,
                            courses = courses,
                            isExpanded = { true },
                            onToggle = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

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
    /** 渲这张表；`narrow` = 套一层 328dp 的宽度约束（= 360dp 真机去掉页面左右 16dp 后剩下的那一档）。 */
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
                        Box(modifier = Modifier.width(328.dp).fillMaxHeight()) { AcademicCourseTable(courses) }
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
     * 课程表的**窄屏**版：328dp = 360dp 真机上页面左右各 16dp 之后剩下的那一档。
     *
     * ⚠️ 上一版这里套的是 360dp，而测试机（393dp）去掉左右 16dp 后本来就有 361dp 可用 ——
     * 约束几乎没生效，这张基线和宽屏那张**长得一模一样**，等于没测。
     * 现在按真机换算后的内容宽来，窄屏才真的会触发"让掉绩点列"。
     *
     * 守的是两件事：① 窄屏让位的是绩点（由成绩推得），学分/成绩/状态一门都不能少；
     * ② 表头与数据行**同时**让位 —— 只让一边就是"表头有绩点、行里没有"的错列。
     */
    @Test
    fun `课程表 窄屏让掉绩点列`() {
        setTable(narrow = true)

        composeRule.onAllNodesWithText("绩点").assertCountEquals(0)
        composeRule.onNodeWithText("学分").assertIsDisplayed()
        composeRule.onNodeWithText("成绩").assertIsDisplayed()
        composeRule.onNodeWithText("状态").assertIsDisplayed()
        // 让位之后课名才写得下：这一行最主要的东西就是它
        composeRule.onNodeWithText("思想道德修养与法律基础").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/364-academic-course-table-narrow.png")
    }

    /**
     * 成绩分布卡：把这一页**已经出分**的课分档（构成条回答"还差多少学分"，这张回答"已修的分长什么样"）。
     *
     * 两条只有这张图能守住的约定：
     * ① 取的是「最大成绩」那一列 —— 夹具里挂科那门 `成绩 51 / 最大成绩 55`，
     *    读错列时分布会跟着错（换一门 61/55 就从 `<60` 跳到 `60-79`）；
     * ② 在修 / 未修的两门**不占档位**，图尾那句"共 N 门有分数记录"就是这一卡的口径，
     *    它替用户回答了"为什么四档加起来比课程总数少"。
     */
    @Test
    fun `成绩分布卡只数已出分的课`() {
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
                ) { AcademicGradeCard(courses) }
            }
        }
        composeRule.waitForIdle()

        assertEquals(2, gradeDistributionOf(courses).scored)
        composeRule.onNodeWithText("共 2 门有分数记录").assertIsDisplayed()
        composeRule.onNodeWithText("<60").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/365-academic-grade-distribution.png")
    }

    /**
     * 真实树的**上半段**（主修 → 通识教育平台 → 必修/选修模块与其课程类型）。
     *
     * 存在的理由：这一页此前的基线全在折叠线以上，**树本体一张图都没有** ——
     * 而"二十几条横杠是不是变成一堵墙""超修看不看得出来"只有渲整段才看得出来。
     */
    @Test
    fun `真实树 上半段 平台与模块`() {
        renderRealTree(0, 12, "src/test/screenshots/366-academic-tree-upper.png")
    }

    /** 真实树的**下半段**（学科专业教育平台 / 实践教育平台）：整块 0 分的模块与超修模块都在这一段。 */
    @Test
    fun `真实树 下半段 专业与实践平台`() {
        renderRealTree(12, realTypes.size, "src/test/screenshots/367-academic-tree-lower.png")
    }

    /**
     * 真实树的**深色**两半（2026-10-29 补）。
     *
     * 为什么值得单独出图：差额标签是新加的，五档里有三档靠语义色说话
     *（超修/已满 = 绿、差/还没开始 = 橙）。橙色压在深色卡底上够不够醒目，
     * `ThemeContrastTest` 只能算对比度数值，"读起来像不像警告色"还是得看像素。
     */
    @Test
    fun `真实树 深色 上半段`() {
        renderRealTree(0, 12, "src/test/screenshots/369-academic-tree-upper-dark.png", dark = true)
    }

    @Test
    fun `真实树 深色 下半段`() {
        renderRealTree(12, realTypes.size, "src/test/screenshots/370-academic-tree-lower-dark.png", dark = true)
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

    /** 超修的那一段：实心比例可以封顶，**显示值不能替学校改数**。 */
    @Test
    fun `超修的段不把学校给的数改小`() {
        val over = listOf(
            AcademicTypeInfo(name = "通识教育平台", id = "p1", depth = 1, requiredCredit = "53.5", earnedCredit = "49.5"),
            AcademicTypeInfo(name = "通识选修课程模块", id = "p2", depth = 1, requiredCredit = "8.0", earnedCredit = "10.0"),
        )

        val segments = creditSegments(over)

        // 曾经这里写的是 `coerceIn(0f, required)` ⇒ 图例显示「8.0 / 8.0 已满」，
        // 学校给的 10.0 被界面改小了，超修这件事整页都没地方说。
        assertEquals(10.0f, segments[1].earned, 0.01f)
        // 但画实心时仍然封顶：超修那段不能把后面的段挤没
        assertEquals(1f, segments[1].ratio!!, 0.01f)

        // 顺手渲一张：图例右端该说"超 2.0"（与树里那一行同一个词）
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { CreditCompositionBar(segments) }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("超 2.0").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/368-academic-credit-composition-over.png")
    }

    /**
     * 平台行的颜色**跟着构成条走**（2026-10-29）。
     *
     * 构成条把各平台排成不同色的一段，往下滚树里又是这些平台 —— 原先树里全是同一个主色，
     * 同一个东西在一页上换了身衣服，两处对不上。现在按 **id** 借色（借在卡片底色与描边上；
     * 进度条按层级给色，不参与身份）。
     * 按 id 不按名字：培养方案里真有同名节点（两个「身心健康类课程」）。
     */
    @Test
    fun `平台行的段色按 id 对上构成条`() {
        val accents = platformAccents(creditSegments(realTypes))

        // 三个平台各拿一档，且与构成条里同一段是同一档
        assertEquals(ServiceAccent.ACADEMIC, accents.getValue("p1"))
        assertEquals(ServiceAccent.QUERY, accents.getValue("p2"))
        assertEquals(ServiceAccent.LIFE, accents.getValue("p3"))
        // 只有平台层进表：模块层没有自己的段，界面拿 `parentId` 查父平台那一档（查不到 = 灰）；
        // 叶子层同理，但它连父平台都隔了一级，所以整条链上只有平台级有颜色。
        assertFalse(accents.containsKey("m1"))
        assertFalse(accents.containsKey("t1"))
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
