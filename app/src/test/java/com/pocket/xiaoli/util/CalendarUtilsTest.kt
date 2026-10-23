package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.calendar.CalendarAsset
import com.pocket.xiaoli.data.calendar.CalendarEvent
import com.pocket.xiaoli.data.calendar.CalendarPhase
import com.pocket.xiaoli.data.calendar.CalendarTerm
import com.pocket.xiaoli.data.calendar.EventKind
import com.pocket.xiaoli.data.holiday.HolidayDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 校历纯函数层。
 *
 * 夹具刻意用**与 `assets/calendar/ytlg.json` 同形**的迷你资产，而不是直接读资产文件：
 * 这里要验的是规则（不 coerce、五分支阶段、跨月补格、合并谁赢），
 * 「资产里的数录对了没」由 [com.pocket.xiaoli.data.calendar.CalendarAssetTest] 管。
 */
class CalendarUtilsTest {

    private fun d(value: String): LocalDate = LocalDate.parse(value)

    private val term1 = CalendarTerm("第一学期", d("2026-08-31"), d("2027-02-28"), 18, 26)
    private val term2 = CalendarTerm("第二学期", d("2027-03-01"), d("2027-08-29"), 18, 26)
    private val exam1 = CalendarEvent("期末考试周", d("2027-01-04"), d("2027-01-17"), EventKind.EXAM, "期末考")
    private val winter = CalendarEvent("寒假", d("2027-01-18"), d("2027-02-28"), EventKind.VACATION, "寒假")
    private val summer = CalendarEvent("暑假", d("2027-07-19"), d("2027-08-29"), EventKind.VACATION, "暑假")
    private val springFestival =
        CalendarEvent("春节", d("2027-02-06"), d("2027-02-06"), EventKind.HOLIDAY, "春节")

    private val asset = CalendarAsset(
        schema = CalendarAsset.SCHEMA_VERSION,
        academicYear = "2026-2027",
        source = "test",
        terms = listOf(term1, term2),
        events = listOf(exam1, winter, summer, springFestival),
    )

    // ── 周次 ────────────────────────────────────────────────────────────────

    @Test
    fun legalWeekOfCountsFromTermStart() {
        assertEquals(1, CalendarUtils.legalWeekOf(term1, d("2026-08-31")))
        assertEquals("不足 7 天仍是第 1 周", 1, CalendarUtils.legalWeekOf(term1, d("2026-09-06")))
        assertEquals(2, CalendarUtils.legalWeekOf(term1, d("2026-09-07")))
        assertEquals(7, CalendarUtils.legalWeekOf(term1, d("2026-10-16")))
    }

    @Test
    fun legalWeekOfDoesNotClamp() {
        // 这正是不能直接用 currentWeekFrom 返回值的原因：它末尾 coerceIn(1, totalWeeks)，
        // 寒假里任意一天都会被夹回第 20 周，于是「这天的课」会把 2 月的日子跳到第 20 周。
        assertEquals(21, CalendarUtils.legalWeekOf(term1, d("2027-01-18")))
        assertEquals(26, CalendarUtils.legalWeekOf(term1, d("2027-02-22")))
        assertEquals("已越过 totalWeeks 也不夹", 27, CalendarUtils.legalWeekOf(term1, d("2027-03-01")))
    }

    @Test
    fun legalWeekOfIsNullBeforeTheTermStarts() {
        assertNull("开学前一天没有「第 0 周」这种说法", CalendarUtils.legalWeekOf(term1, d("2026-08-30")))
        assertNull(CalendarUtils.legalWeekOf(term2, d("2027-02-10")))
    }

    // ── 学期归属与阶段 ──────────────────────────────────────────────────────

    @Test
    fun activeTermFallsForwardToTheNextTerm() {
        assertEquals(term1, CalendarUtils.activeTerm(asset, d("2026-10-16")))
        assertEquals(term2, CalendarUtils.activeTerm(asset, d("2027-05-13")))
        assertEquals(
            "寒假里点「按校历校准」要校准到第二学期，而不是已经过完的第一学期",
            term2,
            CalendarUtils.activeTerm(asset, d("2027-02-10")),
        )
        assertEquals("学年之前 → 第一个学期", term1, CalendarUtils.activeTerm(asset, d("2026-08-01")))
        assertNull(CalendarUtils.activeTerm(asset, d("2027-09-30")))
    }

    @Test
    fun calendarPhaseCoversAllFiveBranches() {
        assertEquals(CalendarPhase.BEFORE_TERM, CalendarUtils.calendarPhase(asset, d("2026-08-20")))
        assertEquals(CalendarPhase.IN_TERM, CalendarUtils.calendarPhase(asset, d("2026-10-16")))
        assertEquals(CalendarPhase.EXAM_WEEKS, CalendarUtils.calendarPhase(asset, d("2027-01-05")))
        assertEquals(CalendarPhase.WINTER_VACATION, CalendarUtils.calendarPhase(asset, d("2027-02-10")))
        assertEquals(CalendarPhase.IN_TERM, CalendarUtils.calendarPhase(asset, d("2027-05-13")))
        assertEquals(CalendarPhase.SUMMER_VACATION, CalendarUtils.calendarPhase(asset, d("2027-08-01")))
    }

    @Test
    fun vacationWinsOverExamWhenTheRangesOverlap() {
        // 先判假期。若先判考试周，一条跨界的考试事件就能把整段假期说成「考试周」。
        val overlapping = exam1.copy(end = d("2027-01-20"))
        val odd = asset.copy(events = listOf(overlapping, winter, summer, springFestival))
        assertEquals(CalendarPhase.WINTER_VACATION, CalendarUtils.calendarPhase(odd, d("2027-01-19")))
    }

    @Test
    fun vacationSeasonFallsBackToTheMonthWhenTheNameSaysNothing() {
        val unnamed = CalendarEvent("假期", d("2027-01-18"), d("2027-02-28"), EventKind.VACATION, "")
        val odd = asset.copy(events = listOf(unnamed))
        assertEquals(CalendarPhase.WINTER_VACATION, CalendarUtils.calendarPhase(odd, d("2027-02-10")))
    }

    // ── 月网格 ──────────────────────────────────────────────────────────────

    @Test
    fun monthGridPadsAcrossMonthBoundaries() {
        val october = CalendarUtils.monthGrid(YearMonth.of(2026, 10))
        assertEquals("10/1 是周四 → 前面补 9/28–9/30，共 5 行", 35, october.size)
        assertEquals(d("2026-09-28"), october.first().date)
        assertEquals(d("2026-11-01"), october.last().date)
        assertEquals(31, october.count { it.inMonth })
        assertFalse("补位格要标成非本月，UI 才知道画灰字", october.first().inMonth)
        assertTrue(october.first { it.date == d("2026-10-01") }.inMonth)
        assertEquals(DayOfWeek.MONDAY, october.first().date.dayOfWeek)
    }

    @Test
    fun monthGridShrinksToFourRowsInsteadOfAlwaysReservingSix() {
        // 2021-02：28 天且正好从周一开始。写死 6 行会在下面白留两行 ——
        // 空态要求里明确说了不能留一大片空白。
        val february = CalendarUtils.monthGrid(YearMonth.of(2021, 2))
        assertEquals(28, february.size)
        assertTrue(february.all { it.inMonth })
    }

    // ── 事件取用 ────────────────────────────────────────────────────────────

    @Test
    fun eventKindWeightsMatchTheirDeclaredOrder() {
        EventKind.entries.forEachIndexed { index, kind ->
            assertEquals(
                "${kind.name} 的 weight 必须等于声明次序 —— dominantEvent 就是靠它排序的",
                index,
                kind.weight,
            )
        }
    }

    @Test
    fun dominantEventFollowsThePriorityOrder() {
        val day = d("2027-05-13")
        val all = EventKind.entries.map { CalendarEvent("e-${it.name}", day, day, it, "x") }
        // EventKind 的声明次序即优先次序：exam > holiday > vacation > activity > milestone
        all.indices.forEach { index ->
            assertEquals(
                "去掉前 $index 条之后，该轮到第 ${index + 1} 优先的类别",
                EventKind.entries[index],
                CalendarUtils.dominantEvent(all.drop(index))!!.kind,
            )
        }
        assertNull(CalendarUtils.dominantEvent(emptyList()))
    }

    @Test
    fun eventsOnUsesAClosedInterval() {
        assertEquals(listOf("期末考试周"), CalendarUtils.eventsOn(asset, d("2027-01-04")).map { it.title })
        assertEquals("end 当天也算在内", listOf("期末考试周"), CalendarUtils.eventsOn(asset, d("2027-01-17")).map { it.title })
        assertEquals("start 当天也算在内", listOf("寒假"), CalendarUtils.eventsOn(asset, d("2027-01-18")).map { it.title })
        assertTrue("考试周前一天不该命中", CalendarUtils.eventsOn(asset, d("2027-01-03")).isEmpty())
        assertTrue(CalendarUtils.eventsOn(asset, d("2026-10-16")).isEmpty())
    }

    @Test
    fun eventsInMonthKeepsMultiDayEventsInEveryMonthTheyTouch() {
        val january = CalendarUtils.eventsInMonth(asset, YearMonth.of(2027, 1)).map { it.title }
        val february = CalendarUtils.eventsInMonth(asset, YearMonth.of(2027, 2)).map { it.title }
        assertTrue("寒假 1/18 起，1 月列表要有", january.contains("寒假"))
        assertTrue("寒假跨到 2 月，2 月列表里也得有，否则 2 月看着什么事都没有", february.contains("寒假"))
        assertTrue("春节是 2/6 的单日事件", february.contains("春节"))
    }

    // ── 格子标记（与法定节假日合并） ────────────────────────────────────────

    @Test
    fun dayMarkPrefersTheSchoolCalendarOverTheOnlineSource() {
        val online = listOf(HolidayDay(name = "在线源说这是别的节", date = "2027-02-06", isOffDay = true))
        val mark = CalendarUtils.dayMark(asset, online, d("2027-02-06"))!!
        assertEquals("同一天两个源都有时，以校历为准", "春节", mark.label)
        assertEquals(EventKind.HOLIDAY, mark.kind)
    }

    @Test
    fun dayMarkFallsBackToTheOnlineSourceWhenTheCalendarIsSilent() {
        val online = listOf(HolidayDay(name = "国庆节", date = "2026-10-01", isOffDay = true))
        val mark = CalendarUtils.dayMark(asset, online, d("2026-10-01"))!!
        assertEquals("国庆节", mark.label)
        assertEquals(EventKind.HOLIDAY, mark.kind)
        assertFalse(mark.isAdjustedWorkday)
    }

    @Test
    fun dayMarkMarksAdjustedWorkdaysWithBan() {
        val online = listOf(HolidayDay(name = "调休", date = "2026-10-10", isOffDay = false))
        val mark = CalendarUtils.dayMark(asset, online, d("2026-10-10"))!!
        assertEquals("班", mark.label)
        assertTrue("调休补课日与课表周头同一套语义", mark.isAdjustedWorkday)
    }

    @Test
    fun dayMarkIsNullOnAnOrdinaryDay() {
        assertNull("什么都没有的一天不该画标记", CalendarUtils.dayMark(asset, emptyList(), d("2026-10-16")))
    }

    @Test
    fun dayMarkStillFlagsAdjustedWorkdayWhenTheSchoolCalendarWinsTheLabel() {
        val schoolDay = CalendarEvent("校庆", d("2027-05-13"), d("2027-05-13"), EventKind.ACTIVITY, "校庆")
        val online = listOf(HolidayDay(name = "调休", date = "2027-05-13", isOffDay = false))
        val mark = CalendarUtils.dayMark(asset.copy(events = listOf(schoolDay)), online, d("2027-05-13"))!!
        assertEquals("校庆", mark.label)
        assertTrue("小字归校历，调休标记不能被吞掉", mark.isAdjustedWorkday)
    }

    // ── 校准 ────────────────────────────────────────────────────────────────

    @Test
    fun calibrationTargetUsesTeachingWeeksPlusExamsNotTotalWeeks() {
        val target = CalendarUtils.calibrationTarget(asset, d("2026-10-16"))!!
        assertEquals(d("2026-08-31"), target.semesterStart)
        assertEquals("课表口径：18 + 2 = 20，与课表默认值同口径", 20, target.semesterWeeks)
        assertNotEquals("写 totalWeeks(26) 会让课表多排 6 周空列", term1.totalWeeks, target.semesterWeeks)
    }

    @Test
    fun calibrationTargetInWinterPointsAtTheNextTerm() {
        val target = CalendarUtils.calibrationTarget(asset, d("2027-02-10"))!!
        assertEquals(d("2027-03-01"), target.semesterStart)
    }

    @Test
    fun calibrationTargetIsNullWithoutATerm() {
        assertNull(CalendarUtils.calibrationTarget(asset, d("2027-09-30")))
    }

    @Test
    fun needsCalibrationOnlyWhenTheSettingsDisagree() {
        val target = CalendarUtils.calibrationTarget(asset, d("2026-10-16"))!!
        assertFalse("完全一致时不该长提示带", CalendarUtils.needsCalibration(target, d("2026-08-31"), 20))
        assertTrue("开学日差一天也算不一致（线框里的 9月1日 就是这一例）",
            CalendarUtils.needsCalibration(target, d("2026-09-01"), 20))
        assertTrue("周数不一致", CalendarUtils.needsCalibration(target, d("2026-08-31"), 18))
        assertTrue("从未设过学期开始", CalendarUtils.needsCalibration(target, null, 20))
    }

    // ── 下一个节点（主动气泡用）────────────────────────────────────────────

    @Test
    fun upcomingNodeTakesTheNearestOneThatHasNotStarted() {
        // 10-16 时，考试周(2027-01-04) 比寒假(2027-01-18) 近，所以是它
        assertEquals(exam1, CalendarUtils.upcomingNode(asset, d("2026-10-16")))
        // 考试周一过，寒假就顶上来
        assertEquals(winter, CalendarUtils.upcomingNode(asset, d("2027-01-17")))
    }

    @Test
    fun upcomingNodeCountsTheDayItStarts() {
        // 开始当天必须还能被选中 —— 否则"今天开始"这句永远说不出来，
        // 而那天恰恰是最该说的（一过 0 点就整条消失了）
        assertEquals(exam1, CalendarUtils.upcomingNode(asset, d("2027-01-04")))
    }

    @Test
    fun upcomingNodeIsNullAfterEverythingIsOver() {
        assertNull("最后一个事件（暑假）结束之后就再没有下一个了", CalendarUtils.upcomingNode(asset, d("2027-08-30")))
    }

    @Test
    fun upcomingNodePrefersTheHeavierKindOnTheSameDay() {
        // 同一天两条时挑 kind 更"重"的那条：气泡里那句"XX还有 N 天"只有一个主语，
        // 报出一条无足轻重的条目比不说还糟（真实数据里 02-27/28 就是报到叠在别的事上）
        val sameDay = CalendarAsset(
            schema = CalendarAsset.SCHEMA_VERSION,
            academicYear = "test",
            source = "test",
            terms = listOf(term1),
            events = listOf(
                CalendarEvent("某活动", d("2027-01-04"), d("2027-01-04"), EventKind.ACTIVITY, "活动"),
                CalendarEvent("期末考试周", d("2027-01-04"), d("2027-01-17"), EventKind.EXAM, "期末考"),
            ),
        )
        assertEquals("期末考试周", CalendarUtils.upcomingNode(sameDay, d("2026-12-01"))!!.title)
    }

    @Test
    fun nodeKeyIsTitlePlusStart() {
        // 气泡去重靠这个键。它必须只由 CalendarEvent 自己决定：
        // 采集侧与消费侧各拼一次的话，拼法一旦不一致，那个节点就会无限重播。
        assertEquals("期末考试周@2027-01-04", exam1.nodeKey)
        assertEquals("寒假@2027-01-18", winter.nodeKey)
    }
}
