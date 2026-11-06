package com.pocket.xiaoli.core.calendar

/**
 * 学期阶段与「教学进度」的**平台无关口径**。
 *
 * ## 为什么要有这一层
 *
 * 手机端校历的阶段位（`CalendarPhase`）是**数据驱动**的：假期/考试周优先看校历事件，
 * 只有「学期中/未开学」才看教学周区间；桌面端没有事件表，之前就用「第几周 / 未开始 / 已结束」三档，
 * 且进度条的分母用的是**总周数 26**（含寒暑假），与手机端「教学 18 + 考试 2 = 20 周」的口径对不上——
 * 第 9 周在桌面看起来才过三分之一。
 *
 * 这里把两端**能对齐的那部分**抽出来：
 * - 教学周 / 考试周 / 未开始 / 已结束 四档（假期仍留给有事件表的一端判定）；
 * - 进度分母 = `teachingWeeks + EXAM_WEEKS`，与 [WeekCalendarCore.teachingEndEpochDay] 同源。
 *
 * 日期一律用 epochDay，`commonMain` 不依赖 `java.time`。
 */
object TermWeeksCore {

    /** 学期阶段（不含假期 —— 假期需要校历事件才能判）。 */
    enum class Phase(val label: String) {
        BEFORE("未开始"),
        TEACHING("学期中"),
        EXAM("考试周"),
        AFTER("已结束"),
    }

    /** 考试周闭区间：第 `teachingWeeks + 1` 到 `teachingWeeks + EXAM_WEEKS` 周。 */
    fun examWeekRange(teachingWeeks: Int): IntRange {
        val t = maxOf(0, teachingWeeks)
        return (t + 1)..(t + WeekCalendarCore.EXAM_WEEKS)
    }

    fun isExamWeek(week: Int, teachingWeeks: Int): Boolean = week in examWeekRange(teachingWeeks)

    /**
     * @param week [WeekCalendarCore.weekOf] 的结果（**不夹取**）：null = 开学前；
     *   超过 `teachingWeeks + EXAM_WEEKS` = 学期已结束（可能仍在寒暑假里，见类注释）。
     */
    fun phaseOf(week: Int?, teachingWeeks: Int): Phase {
        val t = maxOf(0, teachingWeeks)
        return when {
            week == null -> Phase.BEFORE
            week <= t -> Phase.TEACHING
            isExamWeek(week, teachingWeeks) -> Phase.EXAM
            else -> Phase.AFTER
        }
    }

    /**
     * 教学进度（0f..1f）：已过完的周数 / `(teachingWeeks + EXAM_WEEKS)`。
     *
     * 学期中按 1/20 递增；考试周进度停在 `teachingWeeks / (teachingWeeks + EXAM_WEEKS)`
     * 之前那一档，避免「考试周把进度条拉满」的错觉。
     */
    fun progressFraction(week: Int?, teachingWeeks: Int): Float {
        val t = maxOf(0, teachingWeeks)
        val total = t + WeekCalendarCore.EXAM_WEEKS
        if (total <= 0) return 0f
        val done = when (val phase = phaseOf(week, teachingWeeks)) {
            Phase.BEFORE -> 0
            Phase.TEACHING -> (week ?: 0).coerceAtMost(t) - 1
            Phase.EXAM -> t
            Phase.AFTER -> total
        }.coerceAtLeast(0)
        return (done.toFloat() / total).coerceIn(0f, 1f)
    }
}
