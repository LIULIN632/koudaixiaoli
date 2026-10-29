package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicSummary
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「修课情况」页两条**截图看不出来**的约定（2026-10 用户报障后补）。
 *
 * ## 为什么必须查语义树，不能只靠看图
 *
 * 1. **学号要脱敏**：这一页曾经把学号整串印在汇总卡上（别的页都是 `****1234`）。
 *    改回整串、或改成另一种遮法，**任何基准图都不会红** —— 那只是右侧多了几个字符。
 *    所以判据落在文本上：完整学号在语义树里**一个节点都不许命中**。
 * 2. **「还差 X 学分」只在真缺学分时出现**：线上问题是「进度条满格，下面却写着还差 1 学分」
 *    （原因见 `ZhengfangAcademicApiTest`：那一行读的是类型 id）。数字对不对靠解析层单测，
 *    但**它该不该出现在界面上**只有渲出来才知道。
 *
 * 这两张卡因此从 `private` 提成 `internal` 纯入参组件 —— 整页 `ResultContent` 依赖
 * `AcademicQueryViewModel`，在单测里渲不出来（同 `MascotSettingsSection` 的抽取理由）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AcademicQueryPrivacyTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 与真实调用点同一层容器（有主题、有内边距），避免"条件渲染"被布局差异掩盖 */
    private fun render(content: @Composable () -> Unit) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    content()
                }
            }
        }
    }

    @Test
    fun `汇总卡只显示脱敏学号`() {
        render {
            SummaryCard(
                summary = AcademicSummary(gpa = "3.52", plannedPassed = "40", studentId = "202312345678"),
                progress = null,
            )
        }

        // 学号现在与「计划内通过」同处**一行脚注**（美化时合并的），所以按子串断言：
        // 判据没变 —— 脱敏后的尾 4 位要在，完整学号一个都不许出现。
        composeRule.onNodeWithText("学号 ********5678", substring = true).assertExists()
        composeRule.onAllNodesWithText("202312345678", substring = true).assertCountEquals(0)
    }

    /**
     * 渲一棵"只有一个类型"的树（合并后 `AcademicTypeRow` 就是一行，仍然用它渲）。
     *
     * 2026-10 合并前这里渲的是 `CreditStatsCard`（那张卡片整体），
     * 合并后学分与课程在同一棵树里，所以单行组件的判据改在**行**上 ——
     * 断言的内容一字未改（已获/要求文案、还差、进度条语义）。
     */
    private fun renderRow(type: AcademicTypeInfo, courseCount: Int = 0) {
        render {
            LazyColumn {
                academicTreeSections(
                    types = listOf(type),
                    courses = List(courseCount) {
                        AcademicCourseItem(typeId = type.id, typeName = type.name, courseName = "课$it")
                    },
                    isExpanded = { true },
                    onToggle = {},
                )
            }
        }
    }

    @Test
    fun `学分补齐的类型不显示还差`() {
        renderRow(
            AcademicTypeInfo(
                name = "通识必修",
                id = "101",
                requiredCredit = "10.0",
                earnedCredit = "10.0",
                missedCredit = "",
            )
        )

        // 美化后「已获」与「/ 要求」是**两个节点**（数字给字重、参照系压弱化色），
        // 判据仍是"两档数字都在（正好两处）、且不出现『还差』"。
        composeRule.onAllNodesWithText("10.0", substring = true).assertCountEquals(2)
        composeRule.onAllNodesWithText("还差", substring = true).assertCountEquals(0)
        // 差额搬到进度条右端之后，"补齐"这一档要说"已满"而不是干脆没话
        composeRule.onNodeWithText("已满").assertExists()
    }

    @Test
    fun `真缺学分时条右端说差多少`() {
        renderRow(
            AcademicTypeInfo(
                name = "专业必修",
                id = "102",
                requiredCredit = "40.0",
                earnedCredit = "34.0",
                missedCredit = "6.0",
            )
        )

        composeRule.onNodeWithText("差 6.0").assertExists()
    }

    @Test
    fun `已获得学分为 0 的模块也要有进度条`() {
        // 2026-10 用户报「为零分的课程类型没有进度条」：
        // 0% 的条**必须画** —— "这门模块一门都没过"是这页最该被看见的状态。
        // ⚠️ 判据落在**语义树**上：进度条是画出来的，而"整条轨道很淡"与"根本没画"在图上很难分辨，
        // `ProgressBarRangeInfo` 则是确定存在的语义。
        renderRow(
            AcademicTypeInfo(
                name = "专业核心课程模块", id = "t1",
                requiredCredit = "19.0", earnedCredit = "0.0", missedCredit = "19.0",
            )
        )

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f))).assertExists()
        // 0% 的条只剩轨道，在图上和一条分隔线几乎分不开 —— 所以这一档必须有字。
        // （真实出处：「专业核心课程模块」0.0 / 19.0，渲 24 节点的真树才看出来。）
        composeRule.onNodeWithText("还没开始").assertExists()
    }

    @Test
    fun `修满的模块不显示还差 0 学分`() {
        // 数据层现在会保留 `未获得学分 0.0`（那是学校给的结论），所以"要不要显示还差"
        // 必须由界面按**数值**判断 —— 字符串比较 `!= "0"` 会漏掉 `0.0`。
        renderRow(
            AcademicTypeInfo(
                name = "语言与工具类课程", id = "t1",
                requiredCredit = "16.0", earnedCredit = "16.0", missedCredit = "0.0",
            )
        )

        composeRule.onAllNodesWithText("差 0", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("已满").assertExists()
    }

    @Test
    fun `超修的模块说超了多少而不是刚好满`() {
        // 真实数据：「通识选修课程模块」已获 10.0 / 要求 8.0。
        // 改造前它和"刚好修满"在界面上**完全一样**（满格绿条、没有药丸）——
        // 超修是学生会拿来问"我这块能不能少修一门"的信息，不该被抹平。
        renderRow(
            AcademicTypeInfo(
                name = "通识选修课程模块", id = "m2",
                requiredCredit = "8.0", earnedCredit = "10.0", missedCredit = "0.0",
            )
        )

        composeRule.onNodeWithText("超 2.0").assertExists()
        composeRule.onNodeWithText("已满").assertDoesNotExist()
    }

    @Test
    fun `要求零分母说不限而不是零百分比`() {
        // 真实数据：「其他选修课程」已获 4.0 / 要求 0.0 —— 学校的意思是"不设要求"。
        // 照抄成「4.0 / 0.0」会被读成 0%，与事实正好相反。
        renderRow(
            AcademicTypeInfo(
                name = "其他选修课程", id = "m3",
                requiredCredit = "0.0", earnedCredit = "4.0", missedCredit = "0.0",
            )
        )

        // 「已获」与「/ 要求」是两个节点，所以只能验后半句
        composeRule.onNodeWithText(" / 不限", substring = true).assertExists()
        composeRule.onNodeWithText("4.0", substring = true).assertExists()
        // 没有分母就没有进度：不画条、也不给一个假的百分比
        composeRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f))).assertCountEquals(0)
    }

    // ── 展开/收起（2026-10 用户要求能收起来；随后又把"课程类型"与"课程明细"合并成一棵树） ──

    /** 两个类型 + 两条课程的一棵树，带真实的展开状态 */
    private fun renderSections() {
        val types = listOf(
            AcademicTypeInfo(name = "通识必修课程模块", id = "m1", depth = 1),
            AcademicTypeInfo(name = "思想政治类课程", id = "t1", depth = 2, parentId = "m1"),
        )
        val courses = listOf(
            AcademicCourseItem(typeName = "思想政治类课程", typeId = "t1", courseName = "思想道德修养与法律基础"),
            AcademicCourseItem(typeName = "思想政治类课程", typeId = "t1", courseName = "形势与政策"),
        )
        render {
            val expandedIds = remember { mutableStateListOf<String>() }
            LazyColumn {
                academicTreeSections(
                    types = types,
                    courses = courses,
                    isExpanded = { expandedIds.contains(it) },
                    onToggle = { id ->
                        if (expandedIds.contains(id)) expandedIds.remove(id) else expandedIds.add(id)
                    },
                )
            }
        }
    }

    @Test
    fun `课程默认收起 点类型行才展开`() {
        renderSections()

        // 默认收起：类型行在（带学分），课程不在
        composeRule.onNodeWithText("思想政治类课程").assertExists()
        composeRule.onNodeWithText("思想道德修养与法律基础").assertDoesNotExist()

        composeRule.onNodeWithText("思想政治类课程").performClick()
        composeRule.onNodeWithText("思想道德修养与法律基础").assertExists()
        composeRule.onNodeWithText("形势与政策").assertExists()

        // 再点一次收起
        composeRule.onNodeWithText("思想政治类课程").performClick()
        composeRule.onNodeWithText("思想道德修养与法律基础").assertDoesNotExist()
    }

    @Test
    fun `没取到课程的类型 行照旧显示且不可点`() {
        render {
            LazyColumn {
                academicTreeSections(
                    types = listOf(AcademicTypeInfo(name = "创新创业实践教育模块", id = "p1", depth = 1)),
                    courses = emptyList(),
                    isExpanded = { false },
                    onToggle = {},
                )
            }
        }

        composeRule.onNodeWithText("创新创业实践教育模块").assertExists()
        // ⚠️ 文案是"**学校暂无**"而不是"未取到"：这个节点在学校自己的页面上就没有明细链接，
        // 写"未取到"会让用户以为是 App 出错了（用户 2026-10 正是这么报的）。
        composeRule.onNodeWithText("（学校暂无该类型的课程明细）").assertExists()
    }

    @Test
    fun `收起一个平台 它的子模块与课程类型都不再渲染`() {
        val types = listOf(
            AcademicTypeInfo(name = "主修", id = "root", requiredCredit = "170", earnedCredit = "95.0"),
            AcademicTypeInfo(name = "通识教育平台", id = "p1", parentId = "root", depth = 1),
            AcademicTypeInfo(name = "通识必修课程模块", id = "m1", parentId = "p1", depth = 2),
            AcademicTypeInfo(name = "思想政治类课程", id = "t1", parentId = "m1", depth = 3),
        )
        // 收起由调用方（页面）算好后传进来 —— 这里模拟"p1 被收起"
        render {
            LazyColumn {
                academicTreeSections(
                    types = visibleAcademicTypes(types, setOf("p1")),
                    courses = emptyList(),
                    isExpanded = { true },
                    onToggle = {},
                )
            }
        }

        composeRule.onNodeWithText("通识教育平台").assertExists() // 被收起的自己还在（还要能点开）
        composeRule.onNodeWithText("通识必修课程模块").assertDoesNotExist()
        composeRule.onNodeWithText("思想政治类课程").assertDoesNotExist()
    }

    @Test
    fun `筛选 chip 的触摸高度达标 且角标跟着数据走`() {
        render {
            AcademicFilterBar(
                filter = CourseFilter.ALL,
                query = "",
                badges = mapOf(
                    CourseFilter.UNTAKEN to 12,
                    CourseFilter.PASSED to 31,
                    CourseFilter.SHORT_CREDIT to 5,
                ),
                onFilter = {},
                onQuery = {},
            )
        }

        // 角标：点之前就知道会筛出多少（"点了发现是空的"最劝退）
        composeRule.onNodeWithText("未修 / 未过 12").assertExists()
        composeRule.onNodeWithText("缺学分 5").assertExists()
        // 触摸高度：chip 天然矮，靠内边距估的高度在系统字号变大后就破了（本页展开键同一个坑）
        composeRule.onNodeWithText("未修 / 未过 12").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun `点筛选 chip 会回调对应的口径`() {
        var picked: CourseFilter? = null
        render {
            AcademicFilterBar(
                filter = CourseFilter.ALL,
                query = "",
                badges = emptyMap(),
                onFilter = { picked = it },
                onQuery = {},
            )
        }

        composeRule.onNodeWithText("已通过").performClick()

        assertEquals(CourseFilter.PASSED, picked)
    }

    @Test
    fun `展开键的触摸高度达标`() {
        val types = listOf(
            AcademicTypeInfo(name = "主修", id = "root"),
            AcademicTypeInfo(name = "通识教育平台", id = "p1", parentId = "root", depth = 1),
            // 给「通识教育平台」一个子节点，否则它自己没有展开键（只有能展开的行才可点）
            AcademicTypeInfo(name = "通识必修课程模块", id = "m1", parentId = "p1", depth = 2),
        )
        render {
            LazyColumn {
                academicTreeSections(types = types, courses = emptyList(), isExpanded = { true }, onToggle = {})
            }
        }

        // ⚠️ 断言要落在**可点的那一行**上，而不是行里的文字：
        // `toggleable` 不会像 Material 组件那样自动补最小触摸尺寸，本项目底线 44dp。
        // （v1 版打在文字节点上、量到 22dp —— 看着像 bug，其实是量错了对象。）
        // 这一行同时说明它**合并成了一个可读屏节点**（整行念成一句话，而不是散成三段）。
        composeRule.onNode(hasClickAction() and hasText("通识教育平台", substring = true))
            .assertHeightIsAtLeast(44.dp)
        // 没有子节点、也没有课程的行不该有展开键（点不开还弹个箭头才是真的坑）
        composeRule.onAllNodes(hasClickAction() and hasText("通识必修课程模块", substring = true))
            .assertCountEquals(0)
    }

    /**
     * **整张卡都是展开键**，不只是卡里的标题行（2026-10-29 用户点名"点空白展不开"）。
     *
     * 卡有边界，用户就按边界理解可点区。这一条钉的是"点击挂在卡上"这件事：
     * 挂在标题行上量到 44dp，挂在卡上量到 ~69dp（标题行 + 进度条那一行 + 卡内边距），
     * 所以断言取 60dp —— 两边都有余量，不是刀口。
     * 顺带钉"卡里只有一个可点节点"：嵌套两个按钮会让点空白和点标题行走出不同的东西。
     */
    @Test
    fun `整张平台卡都是展开键`() {
        val types = listOf(
            AcademicTypeInfo(name = "主修", id = "root", requiredCredit = "170.0", earnedCredit = "95.0"),
            AcademicTypeInfo(
                name = "通识教育平台", id = "p1", parentId = "root", depth = 1,
                requiredCredit = "53.5", earnedCredit = "49.5",
            ),
            AcademicTypeInfo(name = "通识必修课程模块", id = "m1", parentId = "p1", depth = 2),
        )
        render {
            LazyColumn {
                academicTreeSections(types = types, courses = emptyList(), isExpanded = { true }, onToggle = {})
            }
        }

        val card = composeRule.onNode(hasClickAction() and hasText("通识教育平台", substring = true))
        card.assertHeightIsAtLeast(60.dp)
        composeRule.onAllNodes(hasClickAction() and hasText("通识教育平台", substring = true))
            .assertCountEquals(1)
    }
}
