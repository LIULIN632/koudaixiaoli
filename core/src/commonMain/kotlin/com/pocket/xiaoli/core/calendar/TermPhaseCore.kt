package com.pocket.xiaoli.core.calendar

/**
 * 学期**阶段位**（手机端校历顶部那个「学期中 / 考试周 / 寒假」）的共享判定。
 *
 * ## 为什么要有它
 *
 * 手机端 `CalendarUtils.calendarPhase` 是数据驱动的：先看假期事件 → 再看考试事件 → 再看学期区间，
 * 桌面端只有学期区间（能推考试周，推不出寒暑假）。同一门校历，两个端的阶段文字对不上。
 *
 * 这里把「判定顺序」抽成纯函数，**事件表由调用方喂进来**（两端都已经有校历 JSON）：
 *
 * 1. 假期事件覆盖今天 → 寒假 / 暑假；
 * 2. 考试事件覆盖今天 → 考试周；
 * 3. 今天落在某学期的 `[start, teachingEnd]` → 学期中；
 * 4. 其余 → 未开学。
 *
 * 先判假期是刻意的：寒假区间很长，若先判考试周，一条跨界的考试事件就能把整段寒假说成「考试周」。
 * 日期一律 epochDay；「几月」用 `monthOf` 回调注入，`commonMain` 不碰 `java.time`。
 */
object TermPhaseCore {

    enum class Phase(val label: String) {
        BEFORE_TERM("未开学"),
        IN_TERM("学期中"),
        EXAM_WEEKS("考试周"),
        WINTER_VACATION("寒假"),
        SUMMER_VACATION("暑假"),
    }

    enum class Kind { VACATION, EXAM, OTHER }

    /** 闭区间 `[startEpochDay, endEpochDay]`（含首尾）。 */
    data class Window(
        val kind: Kind,
        val startEpochDay: Long,
        val endEpochDay: Long,
        /** 假期名（含「寒」「暑」时优先用它判寒/暑假），其他事件可为空 */
        val name: String = "",
    ) {
        fun covers(day: Long): Boolean = day in startEpochDay..endEpochDay
    }

    /**
     * @param terms 学期区间（`[start, teachingEnd]`，**不是**校历周次表的最后一天）
     * @param monthOf 把 epochDay 换成「几月」（1..12），用于名称判不出寒/暑假时的兜底
     */
    fun phaseOf(
        todayEpochDay: Long,
        terms: List<Window>,
        events: List<Window>,
        monthOf: (Long) -> Int,
    ): Phase {
        events.firstOrNull { it.kind == Kind.VACATION && it.covers(todayEpochDay) }?.let { vacation ->
            return if (isWinter(vacation.name, monthOf(todayEpochDay))) {
                Phase.WINTER_VACATION
            } else {
                Phase.SUMMER_VACATION
            }
        }
        if (events.any { it.kind == Kind.EXAM && it.covers(todayEpochDay) }) return Phase.EXAM_WEEKS
        val inTerm = terms.any { todayEpochDay in it.startEpochDay..it.endEpochDay }
        return if (inTerm) Phase.IN_TERM else Phase.BEFORE_TERM
    }

    /**
     * 寒假还是暑假：名字里带「寒/暑」优先（`寒假`、`暑期实践`），
     * 否则看月份 —— 12/1/2 月算寒假，其余算暑假（与手机端原实现一致）。
     */
    fun isWinter(name: String, month: Int): Boolean = when {
        name.contains("寒") -> true
        name.contains("暑") -> false
        else -> month >= 12 || month <= 2
    }
}
