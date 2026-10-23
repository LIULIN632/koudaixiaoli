package com.pocket.xiaoli.util

import com.pocket.xiaoli.data.calendar.CalendarAsset
import com.pocket.xiaoli.data.calendar.CalendarEvent
import com.pocket.xiaoli.data.calendar.CalendarPhase
import com.pocket.xiaoli.data.calendar.CalendarTerm
import com.pocket.xiaoli.data.calendar.EventKind
import com.pocket.xiaoli.data.holiday.HolidayDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** 月历里的一格。[inMonth] 为假表示它是相邻月补位的灰字。 */
data class MonthCell(val date: LocalDate, val inMonth: Boolean)

/**
 * 格子里那点标记：小字 + 类别 + 是否调休上班日。
 *
 * 三样一起返回，因为它们**必须同源**：先算小字、再单独问「这天是不是调休」，
 * 两处会各自去翻一遍节假日列表，迟早对不上。
 */
data class DayMark(
    val label: String,
    val kind: EventKind,
    val isAdjustedWorkday: Boolean,
)

/** 「按校历校准」要写进学期设置的那两个值。 */
data class CalibrationTarget(
    val semesterStart: LocalDate,
    val semesterWeeks: Int,
)

/**
 * 校历的纯函数层 —— 不依赖 Android，不碰 IO。
 *
 * 周次、阶段、月网格、与法定节假日合并全在这里算完：UI 只负责画，仓库只负责读。
 * 这也是整个功能里**唯一能在没有设备的情况下被完整验证**的部分。
 */
object CalendarUtils {

    /**
     * 期末考试周数（校历原文「第 19–20 周为期末考试时间」）。
     *
     * 它只用来把 [CalendarTerm.teachingWeeks] 换算成课表口径的 `semesterWeeks`：
     * 18 + 2 = 20，正好等于 App 现在的默认值 —— 这不是巧合，是同一个口径。
     */
    const val EXAM_WEEKS: Int = 2

    /**
     * 校历口径的「第几周」，**不 coerce**。
     *
     * 不能直接用 [currentWeekFrom] 的返回值：它末尾有 `coerceIn(1, totalWeeks)`，
     * 寒假里任意一天都会被夹回第 20 周 —— 校历页据此判「这天的课跳不跳得过去」时，
     * 会把 2 月的日子当成本学期第 20 周，跳过去看到一片空课表还以为是坏了。
     *
     * 做法是把上限放到 `Int.MAX_VALUE` 去调**同一个函数**，而不是在这儿重抄一遍 `floorDiv`：
     * 「不足 7 天算第 1 周」这条语义只能有一处定义。
     *
     * @return 开学前的日期返回 null —— 没有「第 0 周」这种说法。
     */
    fun legalWeekOf(term: CalendarTerm, date: LocalDate): Int? {
        if (date.isBefore(term.start)) return null
        return currentWeekFrom(term.start, Int.MAX_VALUE, date)
    }

    /**
     * **课表口径**的学期结束日：第 `teachingWeeks + EXAM_WEEKS` 周的周日。
     *
     * 与 [CalendarTerm.end] 不是一个东西 —— 后者是校历周次表的最后一天（26 周，把寒暑假也算进去了）。
     *
     * 「今天在不在学期里」一律用这个判。用 [CalendarTerm.end] 的后果已经实测过一次：
     * 第一学期的 `end` 是 2027-02-28，于是**寒假里的每一天都被算进第一学期**，
     * 寒假点「按校历校准」会去校准一个早已过完的学期（单测 `activeTermFallsForwardToTheNextTerm` 抓的就是这个）。
     */
    fun teachingEndOf(term: CalendarTerm): LocalDate =
        term.start.plusWeeks((term.teachingWeeks + EXAM_WEEKS).toLong()).minusDays(1)

    /**
     * 该日期归属的学期。
     *
     * 落在某个学期的**教学 + 考试**区间内 → 它；落在之外 → **下一个**学期
     * （寒假里点「按校历校准」要校准到第二学期；校准到一个已经过完的学期没有意义）；再也没有 → null。
     */
    fun activeTerm(asset: CalendarAsset, date: LocalDate): CalendarTerm? {
        asset.terms.firstOrNull { !date.isBefore(it.start) && !date.isAfter(teachingEndOf(it)) }?.let { return it }
        return asset.terms.filter { it.start.isAfter(date) }.minByOrNull { it.start }
    }

    /**
     * 顶部右侧那个阶段位。
     *
     * 判定顺序：假期 > 考试周 > 落在学期区间内 > 未开学。
     * 先判假期是为了不受边界事件影响 —— 寒假区间很长，若先判考试周，
     * 一条跨界的考试事件就能把整个 2 月说成「考试周」。
     */
    fun calendarPhase(asset: CalendarAsset, date: LocalDate): CalendarPhase {
        asset.events
            .firstOrNull { it.kind == EventKind.VACATION && it.covers(date) }
            ?.let { vacation ->
                return if (isWinterVacation(vacation, date)) {
                    CalendarPhase.WINTER_VACATION
                } else {
                    CalendarPhase.SUMMER_VACATION
                }
            }
        if (asset.events.any { it.kind == EventKind.EXAM && it.covers(date) }) {
            return CalendarPhase.EXAM_WEEKS
        }
        val inTerm = asset.terms.any { !date.isBefore(it.start) && !date.isAfter(teachingEndOf(it)) }
        return if (inTerm) CalendarPhase.IN_TERM else CalendarPhase.BEFORE_TERM
    }

    /**
     * 寒假还是暑假。
     *
     * 先读名字（资产里就叫「寒假」「暑假」），名字里认不出来才退回按月份猜。
     * 顺序不能反：靠月份是猜的，靠名字是读的 —— 南半球式的小概率数据不该让名字失效。
     */
    private fun isWinterVacation(vacation: CalendarEvent, date: LocalDate): Boolean {
        val name = vacation.short.ifBlank { vacation.title }
        return when {
            name.contains("寒") -> true
            name.contains("暑") -> false
            else -> date.monthValue >= 12 || date.monthValue <= 2
        }
    }

    /**
     * 某个月的月历格子，含前后补位。
     *
     * 行数**不写死 6 行**：一个正好从周一开始的 28 天月只占 4 行，
     * 写死 6 行会在下面留两行空白（设计要求里明确写了空态不能留一大片空白）。
     */
    fun monthGrid(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<MonthCell> {
        val first = month.atDay(1)
        val leading = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        val rows = (leading + month.lengthOfMonth() + 6) / 7
        val start = first.minusDays(leading.toLong())
        return (0 until rows * 7).map { offset ->
            val date = start.plusDays(offset.toLong())
            MonthCell(date = date, inMonth = YearMonth.from(date) == month)
        }
    }

    /** 落在这一天的事件，按 [EventKind.weight] 升序（越靠前越优先上格子）。 */
    fun eventsOn(asset: CalendarAsset, date: LocalDate): List<CalendarEvent> =
        asset.events.filter { it.covers(date) }.sortedBy { it.kind.weight }

    /** 这一天最该上格子的那一条；没有则 null。 */
    fun dominantEvent(events: List<CalendarEvent>): CalendarEvent? =
        events.minByOrNull { it.kind.weight }

    /**
     * 与某个月有交集的事件，按开始日期升序。
     *
     * 用**交集**而不是「`start` 落在本月」：跨月的假期（寒假 1/18–2/28）
     * 在 2 月的列表里也必须出现，否则 2 月看起来什么事都没有。
     */
    fun eventsInMonth(asset: CalendarAsset, month: YearMonth): List<CalendarEvent> {
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        return asset.events
            .filter { !it.end.isBefore(first) && !it.start.isAfter(last) }
            .sortedBy { it.start }
    }

    /**
     * 离 [today] 最近的那条**还没开始**的事件（今天开始的也算）。
     *
     * 主动气泡的「校历节点」信号用它。排序只看开始日；同一天有多条时取
     * [EventKind.weight] 小的那条 —— "明天开始"这句话只有一个主语，
     * 挑错那个主语比不说还糟。窗口（提前几天说）不在这里卡，那是规则层的事。
     */
    fun upcomingNode(asset: CalendarAsset, today: LocalDate): CalendarEvent? =
        asset.events
            .filter { !it.start.isBefore(today) }
            .minWithOrNull(compareBy({ it.start }, { it.kind.weight }))

    /**
     * 格子里显示什么。
     *
     * 合并规则：**同一天校历赢** —— 校历是学校口径，在线源是国务院口径，
     * 两者不一致时以学校为准。校历这天没有条目，才去看在线源的放假日，
     * 最后才是调休上班日。
     *
     * 复用既有的 [HolidayCalendar.offDayOf] / [HolidayCalendar.isAdjustedWorkday]，
     * 不另造一套判定 —— 那两个函数课表周头也在用，语义必须只有一处。
     */
    fun dayMark(asset: CalendarAsset, holidays: List<HolidayDay>, date: LocalDate): DayMark? {
        dominantEvent(eventsOn(asset, date))?.let { event ->
            return DayMark(
                label = event.gridLabel,
                kind = event.kind,
                isAdjustedWorkday = HolidayCalendar.isAdjustedWorkday(holidays, date),
            )
        }
        HolidayCalendar.offDayOf(holidays, date)?.let { off ->
            return DayMark(
                // 在线源的节日名不受我们控制，长过格子就裁 —— 但先到这里说明校历没录它，
                // 裁掉也比撑破格子好（校历自己的 short 由资产校验单测硬管，不走这条）。
                label = off.name.ifBlank { "休" }.take(CalendarEvent.GRID_LABEL_MAX),
                kind = EventKind.HOLIDAY,
                isAdjustedWorkday = false,
            )
        }
        if (HolidayCalendar.isAdjustedWorkday(holidays, date)) {
            return DayMark(label = "班", kind = EventKind.MILESTONE, isAdjustedWorkday = true)
        }
        return null
    }

    /**
     * 「按校历校准」要写的两个值。
     *
     * ⚠️ [CalibrationTarget.semesterWeeks] 写的是 `teachingWeeks + EXAM_WEEKS`，
     * **不是** [CalendarTerm.totalWeeks]。前者 = 20，与课表现有默认值同口径
     * （校历「第 19–20 周期末考试」就是课表要排到第 20 周）；后者 = 26，
     * 写进去课表会多出 6 周空列，周次选择器还能翻到寒假里。
     *
     * 这是整个功能里最容易写错的一处，资产校验单测顺手断言了 `teachingWeeks + EXAM_WEEKS == 20`。
     */
    fun calibrationTarget(asset: CalendarAsset, today: LocalDate): CalibrationTarget? {
        val term = activeTerm(asset, today) ?: return null
        return CalibrationTarget(
            semesterStart = term.start,
            semesterWeeks = term.teachingWeeks + EXAM_WEEKS,
        )
    }

    /** 学期设置与校历是否对不上 —— 只有对不上才长那条提示带。 */
    fun needsCalibration(
        target: CalibrationTarget,
        semesterStart: LocalDate?,
        semesterWeeks: Int,
    ): Boolean = semesterStart != target.semesterStart || semesterWeeks != target.semesterWeeks
}
