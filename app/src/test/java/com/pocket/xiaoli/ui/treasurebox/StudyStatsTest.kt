package com.pocket.xiaoli.ui.treasurebox

import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyStatsTest {

    private fun grade(name: String, score: String, credit: String, gpa: String) =
        SavedGradeEntity(courseName = name, totalScore = score, credits = credit, gpa = gpa)

    @Test
    fun savedGradeStatsAggregates() {
        val stats = SavedGradeStats.of(
            listOf(
                grade("高数", "90", "5", "4.0"),
                grade("英语", "58", "3", "0"),
                grade("体育", "80", "1", "3.0"),
            )
        )
        assertEquals(3, stats.count)
        assertEquals(9f, stats.totalCredit, 0.001f)
        assertEquals(76f, stats.averageScore!!, 0.001f)
        assertEquals(7f / 3f, stats.averageGpa!!, 0.001f)
        assertEquals(2f / 3f, stats.passRate!!, 0.001f)
    }

    @Test
    fun savedGradeStatsSkipsInvalidNumbers() {
        val stats = SavedGradeStats.of(
            listOf(
                grade("A", "优秀", "3.0", ""),
                grade("B", "", "2", "3.5"),
            )
        )
        assertEquals(2, stats.count)
        assertEquals(5f, stats.totalCredit, 0.001f)
        assertNull(stats.averageScore)
        assertEquals(3.5f, stats.averageGpa!!, 0.001f)
        assertNull(stats.passRate)
    }

    @Test
    fun savedGradeStatsEmpty() {
        val stats = SavedGradeStats.of(emptyList())
        assertEquals(0, stats.count)
        assertEquals(0f, stats.totalCredit, 0.001f)
        assertNull(stats.averageScore)
        assertNull(stats.passRate)
    }

    @Test
    fun creditProgressComputesRatio() {
        val progress = CreditProgress.of(
            listOf(
                AcademicTypeInfo(name = "必修", id = "1", requiredCredit = "100", earnedCredit = "80"),
                AcademicTypeInfo(name = "选修", id = "2", requiredCredit = "60", earnedCredit = "30"),
            )
        )!!
        assertEquals(110f, progress.earned, 0.001f)
        assertEquals(160f, progress.required, 0.001f)
        assertEquals(110f / 160f, progress.ratio!!, 0.001f)
    }

    @Test
    fun creditProgressWithoutRequirementHasNoRatio() {
        val progress = CreditProgress.of(
            listOf(AcademicTypeInfo(name = "必修", id = "1", earnedCredit = "12"))
        )!!
        assertEquals(12f, progress.earned, 0.001f)
        assertNull(progress.ratio)
    }

    @Test
    fun creditProgressEmptyOrAllBlankReturnsNull() {
        assertNull(CreditProgress.of(emptyList()))
        assertNull(CreditProgress.of(listOf(AcademicTypeInfo(name = "x", id = "1"))))
    }

    // ── 门次 / 课程门数文案（这行字只有"渲出来"或"断言在这里"才看得到） ──

    @Test
    fun countSummaryShowsGateAndCourses() {
        // 用户学校实际值：思想政治类课程 要求门次 14 / 到达 12 / 未达到 2 / 共 14 门 通过 12 门
        val type = AcademicTypeInfo(
            name = "思想政治类课程", id = "11", requiredCredit = "18.0", earnedCredit = "17.0",
            requiredCount = "14", reachedCount = "12", missedCount = "2",
            totalCourses = "14", passedCourses = "12",
        )
        assertEquals("门次 14 · 到达 12 · 还差 2 门 · 课程通过 12/14 门", type.countSummary())
    }

    @Test
    fun countSummaryHidesZeroShortfall() {
        val type = AcademicTypeInfo(
            name = "语言与工具类课程", id = "12",
            requiredCount = "6", reachedCount = "6", missedCount = "0",
            totalCourses = "7", passedCourses = "6",
        )
        // 门次够了就不提"还差"，但门数（7 门里过了 6 门）照旧要说
        assertEquals("门次 6 · 到达 6 · 课程通过 6/7 门", type.countSummary())
    }

    @Test
    fun countSummaryBlankWhenNoGateData() {
        // 老部署的页面没有这一段 → 整行不渲染（而不是显示"门次 0"）
        assertEquals("", AcademicTypeInfo(name = "必修", id = "1").countSummary())
    }

    @Test
    fun withGateCountsMatchesTheSchoolPageNumbers() {
        // 学校页面上「共（N）门 通过（N）门」是前端数出来的（`calcXf()`：xdzt ∈ {4,5,6} 算通过），
        // 源 HTML 里只有 `<font name='zms'>0</font>` 占位 —— 我们自己数，口径要**逐字一致**。
        // 参考值来自「另存为完整网页」的真实页面：思想政治类课程 = 共 14 门 / 通过 12 门。
        val courses = List(12) { course("过了$it", "t1", status = "已修", score = "83") } +
            listOf(
                course("未过的一门", "t1", status = "未过", maxGrade = "55"),
                course("还没修的一门", "t1", status = "未修"),
            )

        val type = AcademicTypeInfo(
            name = "思想政治类课程", id = "t1",
            requiredCount = "14", reachedCount = "12", missedCount = "2",
        ).withGateCounts(courses)

        assertEquals("14", type.totalCourses)
        assertEquals("12", type.passedCourses)
        // 卡片第二行拼出来应与学校页面同一句话（顺序也一致）
        assertEquals("门次 14 · 到达 12 · 还差 2 门 · 课程通过 12/14 门", type.countSummary())
    }

    @Test
    fun withGateCountsCountsSubstitutedCoursesAsPassed() {
        // 学校把 5/6（校内被替代课程 / 校内课程替代）也算「通过」，码表要跟它一致，
        // 否则同一页上会出现"门次够了但通过数少一门"的自相矛盾。
        val courses = listOf(
            course("替代来的", "t1", status = "校内课程替代"),
            course("跨校的", "t1", status = "校内被替代课程"),
            course("在读的", "t1", status = "在修"),
        )

        val type = AcademicTypeInfo(name = "t", id = "t1").withGateCounts(courses)

        assertEquals("3", type.totalCourses)
        assertEquals("2", type.passedCourses)
    }

    @Test
    fun withGateCountsLeavesTypeAloneWhenNothingFetched() {
        // 没取到明细的类型不要写成 "0 门"：那是"没数据"，与"门数为 0"是两件事
        val type = AcademicTypeInfo(name = "创新创业实践教育模块", id = "p1", requiredCount = "2")
        val same = type.withGateCounts(emptyList())

        assertEquals("", same.totalCourses)
        assertEquals("", same.passedCourses)
        assertEquals("门次 2", same.countSummary())
    }

    @Test
    fun countSummaryCanHideShortfallWhenShownAsPill() {
        // 树里「还差 N 门」已经做成药丸，上下文行不该再说一遍（渲图抓到过一次重复）
        val type = AcademicTypeInfo(
            name = "通识教育平台", id = "p1",
            requiredCount = "35", reachedCount = "29", missedCount = "6",
        )
        assertEquals("门次 35 · 到达 29 · 还差 6 门", type.countSummary())
        assertEquals("门次 35 · 到达 29", type.countSummary(includeShortfall = false))
    }

    @Test
    fun statusToneSeparatesInProgressFromUntaken() {
        // 2026-10 用户要求「正在修课和未修课也用不同颜色隔开」。
        // ⚠️ 旧实现按 `status.contains("在读")` 判颜色，而解析层给的词是「**在修**」
        //（学校码表 XDZT=1 的 i18n 值）⇒ 在修的课落进 else 分支，跟未修一样是灰的。
        // 这个 bug 在图上完全看不出来（两行都是灰的，看着像"还没修"），只有断言档位才抓得到。
        assertEquals(CourseStatusTone.IN_PROGRESS, course("甲", "t1", status = "在修").statusTone())
        // 教务另一种说法也要认
        assertEquals(CourseStatusTone.IN_PROGRESS, course("乙", "t1", status = "在读").statusTone())
        assertEquals(CourseStatusTone.UNTAKEN, course("丙", "t1", status = "未修").statusTone())
        assertEquals(CourseStatusTone.UNTAKEN, course("丁", "t1").statusTone())
        assertEquals(CourseStatusTone.PASSED, course("戊", "t1", status = "已修").statusTone())
        assertEquals(CourseStatusTone.PASSED, course("己", "t1", status = "校内课程替代").statusTone())
        assertEquals(CourseStatusTone.FAILED, course("庚", "t1", status = "未过").statusTone())
        // 四档必须两两不同，否则"用颜色隔开"就白做了
        val tones = listOf("在修", "未修", "已修", "未过").map { course("x", "t1", status = it).statusTone() }
        assertEquals(4, tones.distinct().size)
    }

    // ── 筛选与搜索（纯函数） ──

    private fun course(
        name: String,
        typeId: String,
        status: String = "",
        score: String = "",
        maxGrade: String = "",
    ) = AcademicCourseItem(
        typeName = "类型$typeId",
        typeId = typeId,
        courseName = name,
        status = status,
        score = score,
        maxGrade = maxGrade,
    )

    @Test
    fun untakenAndPassedFollowStatusFirst() {
        // 状态是教务自己下的结论，优先级最高
        assertTrue(course("甲", "t1", status = "未修").isUntaken())
        assertTrue(course("乙", "t1", status = "未过").isUntaken())
        assertTrue(course("丙", "t1", status = "已修", score = "83").isPassed())
        assertTrue(!course("丁", "t1", score = "55").isPassed())
        assertTrue(course("戊", "t1", score = "及格").isPassed())
        // ⚠️ 未过 + 补考过（最高分≥60）：状态优先，**不算通过** —— 不然最该看到的课会被筛掉
        assertTrue(!course("己", "t1", status = "未过", maxGrade = "75").isPassed())
        // 状态与成绩全空 → 还没读
        assertTrue(course("庚", "t1").isUntaken())
        // 有状态但既非"未"也非"已/通过"（在修之类）→ 两边都不算
        assertTrue(!course("辛", "t1", status = "在修").isUntaken())
        assertTrue(!course("辛", "t1", status = "在修").isPassed())
    }

    @Test
    fun filterUntakenKeepsOnlyNotYetTakenCourses() {
        val types = listOf(AcademicTypeInfo(name = "模块", id = "t1"))
        val courses = listOf(
            course("已修的课", "t1", status = "已修", score = "83"),
            course("还没修的课", "t1", status = "未修"),
        )

        val (keptTypes, keptCourses) = filterAcademicView(types, courses, CourseFilter.UNTAKEN, "")

        assertEquals(listOf("还没修的课"), keptCourses.map { it.courseName })
        assertEquals(listOf("模块"), keptTypes.map { it.name })
    }

    @Test
    fun filterShortCreditKeepsWholeTypeOfCourses() {
        val types = listOf(
            AcademicTypeInfo(name = "满了的", id = "t1", missedCredit = ""),
            AcademicTypeInfo(name = "缺学分的", id = "t2", missedCredit = "6.0"),
        )
        val courses = listOf(
            course("A", "t1", status = "已修", score = "83"),
            course("B", "t2", status = "已修", score = "83"),
            course("C", "t2", status = "未修"),
        )

        val (keptTypes, keptCourses) = filterAcademicView(types, courses, CourseFilter.SHORT_CREDIT, "")

        assertEquals(listOf("缺学分的"), keptTypes.map { it.name })
        // 类型级筛选：这块的课全留下（"这块缺 6 分，这些都是它的课"）
        assertEquals(listOf("B", "C"), keptCourses.map { it.courseName })
    }

    @Test
    fun searchMatchesCourseNameOrTypeName() {
        val types = listOf(
            AcademicTypeInfo(name = "通识必修课程模块", id = "t1"),
            AcademicTypeInfo(name = "专业核心课程模块", id = "t2"),
        )
        val courses = listOf(
            course("形势与政策", "t1", status = "已修", score = "89"),
            course("钢结构", "t2", status = "未修"),
        )

        assertEquals(
            listOf("形势与政策"),
            filterAcademicView(types, courses, CourseFilter.ALL, "形势").second.map { it.courseName }
        )
        // 按类型名命中：该类型整块的课都留下
        assertEquals(
            listOf("钢结构"),
            filterAcademicView(types, courses, CourseFilter.ALL, "专业核心").second.map { it.courseName }
        )
        assertTrue(filterAcademicView(types, courses, CourseFilter.ALL, "不存在的课").second.isEmpty())
    }

    @Test
    fun filterKeepsAncestorsSoHierarchyStaysReadable() {
        // 命中叶子时祖先要一起带上 —— 否则屏幕上是一堆缩进很深、看不出属于谁的行
        val types = listOf(
            AcademicTypeInfo(name = "主修", id = "root"),
            AcademicTypeInfo(name = "通识教育平台", id = "p1", parentId = "root", depth = 1),
            AcademicTypeInfo(name = "通识必修课程模块", id = "m1", parentId = "p1", depth = 2),
            AcademicTypeInfo(name = "思想政治类课程", id = "t1", parentId = "m1", depth = 3),
        )
        val courses = listOf(course("形势与政策", "t1", status = "未修"))

        val (keptTypes, _) = filterAcademicView(types, courses, CourseFilter.UNTAKEN, "")

        assertEquals(
            listOf("主修", "通识教育平台", "通识必修课程模块", "思想政治类课程"),
            keptTypes.map { it.name }
        )
    }

    @Test
    fun noFilterReturnsEverythingUnchanged() {
        val types = listOf(AcademicTypeInfo(name = "t", id = "t1"))
        val courses = listOf(course("A", "t1"))
        val (keptTypes, keptCourses) = filterAcademicView(types, courses, CourseFilter.ALL, "")
        assertTrue(keptTypes === types && keptCourses === courses)
    }

    @Test
    fun badgeCountsTellHowManyBeforeTapping() {
        val types = listOf(
            AcademicTypeInfo(name = "缺的", id = "t1", missedCredit = "1.0"),
            AcademicTypeInfo(name = "满的", id = "t2"),
        )
        val courses = listOf(
            course("A", "t1", status = "未修"),
            course("B", "t1", status = "已修", score = "83"),
        )

        val badges = filterBadgeCounts(types, courses)

        assertEquals(1, badges[CourseFilter.UNTAKEN])
        assertEquals(1, badges[CourseFilter.PASSED])
        assertEquals(1, badges[CourseFilter.SHORT_CREDIT])
    }

    // ── 学分差额（树行与「学分构成」那张卡共用的一条规则）──

    @Test
    fun creditGapJudgesOverEarnedBeforeFull() {
        // 真实数据：「通识选修课程模块」已获 10.0 / 要求 8.0。
        // 先算 required - earned 会得到负数并落进"没有缺口"⇒ 超修在界面上完全隐身。
        val gap = creditGap("10.0", "8.0")
        assertEquals(CreditGapKind.OVER, gap?.kind)
        assertEquals("超 2.0", gap?.label)
    }

    @Test
    fun creditGapNamesZeroEarnedSeparately() {
        // 0% 的条只剩轨道，图上与一条分隔线难分 —— 这一档必须有自己的话
        assertEquals(CreditGapKind.NONE, creditGap("0.0", "19.0")?.kind)
        assertEquals("还没开始", creditGap("0.0", "19.0")?.label)
    }

    @Test
    fun creditGapNeverLeaksFloatTail() {
        // 53.5 - 49.5 在 Float 里带尾巴，界面只能出现一位小数
        assertEquals("差 4.0", creditGap("49.5", "53.5")?.label)
    }

    @Test
    fun creditGapTreatsExactFullAsFullNotZeroGap() {
        // 解析层会保留 `未获得学分 0.0`（那是学校给的结论）⇒ 按数值判，不许出现"差 0.0"
        assertEquals(CreditGapKind.FULL, creditGap("16.0", "16.0")?.kind)
        assertEquals("已满", creditGap("16.0", "16.0")?.label)
    }

    @Test
    fun creditGapSaysUnlimitedOnlyWhenSchoolActuallySaidZero() {
        // 要求 0.0 = 学校不设要求（真实数据：其他选修课程）；要求整串缺失（老部署）
        // 是"没数据"，那时连"不限"都不该说。
        assertEquals(CreditGapKind.UNLIMITED, creditGap("4.0", "0.0")?.kind)
        assertNull(creditGap("4.0", ""))
        assertNull(creditGap("", ""))
    }
}
