package com.pocket.xiaoli.data.calendar

import com.pocket.xiaoli.util.CalendarUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek

/**
 * 资产校验 —— 「录错数据」唯一的守门人。
 *
 * 走的是**真实解析路径**（读真文件 → [parseCalendarAsset]），而不是校验 JSON 字面量：
 * DTO 字段名与 Gson 对不上、日期格式写错、区间写反，只有走解析才看得见。
 *
 * 为什么值得写得这么细：格内小字只有约 46dp 宽，`short` 超出 4 字会被**静默裁掉** ——
 * 编译不报错、上屏不报错、截图也未必一眼看出来。这里红了才知道。
 */
class CalendarAssetTest {

    @Test
    fun assetParsesWithTheExpectedShape() {
        val asset = loadAsset()
        assertEquals(CalendarAsset.SCHEMA_VERSION, asset.schema)
        assertEquals("2026-2027", asset.academicYear)
        assertTrue("source 要能指回学校原件", asset.source.contains("烟台理工学院"))
        assertEquals("一学年两个学期（学期是 26 周，第二学期还跨年）", 2, asset.terms.size)
        assertEquals("资产里是**全量 9 条**，不是节选", 9, asset.events.size)
    }

    @Test
    fun everyGridLabelFitsTheCell() {
        loadAsset().events.forEach { event ->
            assertTrue("「${event.title}」的标题不能为空", event.title.isNotBlank())
            assertTrue(
                "「${event.title}」的 short 是「${event.short}」，超过 ${CalendarEvent.GRID_LABEL_MAX} 字" +
                    "会被格内静默裁掉（要么改短，要么改资产里的写法）",
                event.short.isBlank() || event.short.length <= CalendarEvent.GRID_LABEL_MAX,
            )
            assertTrue(
                "「${event.title}」上格子会显示「${event.gridLabel}」，长度不在 1..${CalendarEvent.GRID_LABEL_MAX}",
                event.gridLabel.length in 1..CalendarEvent.GRID_LABEL_MAX,
            )
        }
    }

    @Test
    fun eventsAreWellFormed() {
        loadAsset().events.forEach { event ->
            assertTrue(
                "「${event.title}」的 start(${event.start}) 晚于 end(${event.end})",
                !event.end.isBefore(event.start),
            )
        }
    }

    @Test
    fun kindsAreTheOnesTheSchoolActuallySaid() {
        val asset = loadAsset()
        fun kindOf(title: String): EventKind = asset.events.first { it.title == title }.kind

        // 这组断言的用处：资产里把 kind 写成 "Exams" / "exam " / "假期" 之类时，
        // EventKind.from 会**静默退化成 MILESTONE**，别处一个错都不报 —— 只有这里会红。
        assertEquals(EventKind.EXAM, kindOf("期末考试周"))
        assertEquals(EventKind.VACATION, kindOf("寒假"))
        assertEquals(EventKind.VACATION, kindOf("暑假"))
        assertEquals(EventKind.HOLIDAY, kindOf("春节"))
        assertEquals(EventKind.ACTIVITY, kindOf("校运动会"))
        assertEquals(EventKind.MILESTONE, kindOf("学生报到注册"))
        assertEquals(EventKind.MILESTONE, kindOf("新生报到"))
    }

    @Test
    fun termsAreSelfConsistent() {
        loadAsset().terms.forEach { term ->
            assertEquals(
                "${term.name} 的第 1 周不是周一 —— 全套周次换算（含课表）都假设它是",
                DayOfWeek.MONDAY,
                term.start.dayOfWeek,
            )
            assertEquals(
                "${term.name} 的 end 与 start + totalWeeks 对不上",
                term.start.plusDays(term.totalWeeks * 7L - 1),
                term.end,
            )
            assertTrue(
                "${term.name} 的教学周数不能超过总周数",
                term.teachingWeeks <= term.totalWeeks,
            )
        }
    }

    @Test
    fun semesterWeeksStaysAtTwenty() {
        // ⚠️ 全功能最容易写错的一处：课表口径 = teachingWeeks + 期末考试周数，**不是 totalWeeks**。
        // 换学年录新数据时，若学校改了考试周数，这个等式要重新对一遍（spec §4.4.1）。
        loadAsset().terms.forEach { term ->
            assertEquals(
                "${term.name}：课表口径应为 teachingWeeks + EXAM_WEEKS，" +
                    "写 totalWeeks(${term.totalWeeks}) 会让课表多排几周空列",
                20,
                term.teachingWeeks + CalendarUtils.EXAM_WEEKS,
            )
        }
    }

    @Test
    fun examAndVacationWeeksLineUpWithTheTerm() {
        // 学校原文「第 19–20 周为期末考试时间」「第 21–26 周为寒假」。
        // 用周次反推日期，一次性抓住「周次对、日期录错」这类错误。
        val asset = loadAsset()
        asset.terms.forEach { term ->
            val exam = asset.events.first { it.kind == EventKind.EXAM && it.start.isAfter(term.start) }
            val vacation = asset.events.first { it.kind == EventKind.VACATION && it.start.isAfter(term.start) }

            assertEquals(
                "${term.name} 的期末考试周不是第 ${term.teachingWeeks + 1} 周开始",
                term.start.plusWeeks(term.teachingWeeks.toLong()),
                exam.start,
            )
            assertEquals(
                "${term.name} 的期末考试周没在第 ${term.teachingWeeks + CalendarUtils.EXAM_WEEKS} 周结束 —— " +
                    "CalendarUtils.teachingEndOf 就是按它算的，对不上会让「今天在不在学期里」整体错位",
                CalendarUtils.teachingEndOf(term),
                exam.end,
            )
            assertEquals(
                "${term.name} 的假期不是第 ${term.teachingWeeks + CalendarUtils.EXAM_WEEKS + 1} 周开始",
                term.start.plusWeeks((term.teachingWeeks + CalendarUtils.EXAM_WEEKS).toLong()),
                vacation.start,
            )
        }
    }

    @Test
    fun eventsStayInsideTheAcademicYear() {
        // 下界放到开学前一个月：「报到」在学校明文的开学日之前
        // （2026-08-29/30 报到，下一周 08-31 才是第 1 周）。这条抓的是「年份写成 2027」这类错。
        val asset = loadAsset()
        val lower = asset.terms.first().start.minusMonths(1)
        val upper = asset.terms.last().end
        asset.events.forEach { event ->
            assertTrue("「${event.title}」(${event.start}) 早于学年起点 $lower", !event.start.isBefore(lower))
            assertTrue("「${event.title}」(${event.end}) 晚于学年终点 $upper", !event.end.isAfter(upper))
        }
    }

    @Test
    fun octoberHasNoSchoolEvent() {
        // 2026 年 10 月是**空态基准月**：校历对法定节假日只写「按国家规定执行」，
        // 所以资产里一条 10 月的事件都没有，月历上那些节日全靠在线源合并进来。
        // 截图基线里有一张落在 10 月，就是为了让「列表塌掉」这种事有人发现。
        val october = loadAsset().events.filter { it.start.monthValue == 10 && it.start.year == 2026 }
        assertTrue("10 月本应是空态月，现在多了 ${october.map { it.title }}", october.isEmpty())
    }

    @Test
    fun brokenInputYieldsNullInsteadOfThrowing() {
        assertNull("null 输入", parseCalendarAsset(null))
        assertNull("空串", parseCalendarAsset("   "))
        assertNull("不是 JSON", parseCalendarAsset("{ not json"))
        assertNull("没有事件", parseCalendarAsset("""{"terms":[],"events":[]}"""))
        assertNull(
            "日期格式写错的那条被丢掉后，若一条都不剩就整体为 null",
            parseCalendarAsset(
                """{"terms":[{"name":"x","start":"2026/08/31","end":"2027-02-28",
                   "teachingWeeks":18,"totalWeeks":26}],"events":[]}""",
            ),
        )
    }

    @Test
    fun oneBrokenEventIsDroppedInsteadOfKillingTheWholeAsset() {
        // 单条坏数据只该少一条，不该让整个功能空着 —— 这是 DTO 字段全部可空的理由。
        val parsed = parseCalendarAsset(
            """{"schema":1,"academicYear":"2026-2027","source":"t",
               "terms":[{"name":"第一学期","start":"2026-08-31","end":"2027-02-28",
                 "teachingWeeks":18,"totalWeeks":26}],
               "events":[
                 {"title":"好的","start":"2026-09-05","end":"2026-09-05","kind":"milestone","short":"新生报到"},
                 {"title":"坏的","start":"2026-09-06","end":"2026-09-01","kind":"exam","short":"倒置"}]}""",
        )
        assertNotNull(parsed)
        assertEquals(1, parsed!!.events.size)
        assertEquals("好的", parsed.events.first().title)
    }

    private fun loadAsset(): CalendarAsset {
        val file = assetFile()
        assertNotNull(
            "assets/calendar/ytlg.json 找不到：src/main / app/src/main / ../app/src/main 三个候选路径都没有",
            file,
        )
        val parsed = parseCalendarAsset(file!!.readText())
        assertNotNull("资产解析失败（返回 null）：${file.absolutePath}", parsed)
        return parsed!!
    }

    /**
     * Gradle 的工作目录随 task 变（`testDebugUnitTest` / IDE / 命令行各不相同），
     * 所以三个候选路径**一个都不能少** —— 同 `CampusMapAssetTest.asset()` 的写法。
     */
    private fun assetFile(): File? {
        val relative = "assets/calendar/ytlg.json"
        val candidates = listOf(
            File("src/main/$relative"),
            File("app/src/main/$relative"),
            File("../app/src/main/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
