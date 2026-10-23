package com.pocket.xiaoli.focus

import com.pocket.xiaoli.data.entity.FocusSessionEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 某一天的番茄钟成绩。 */
data class FocusDayStat(
    val date: LocalDate,
    /** 当天**完整跑完**的专注段数。跳过的、放弃的不算 —— 这就是"番茄数"的口径。 */
    val completedPomodoros: Int,
    /** 当天专注段实际累计毫秒（含中途被放弃的那些，因为它们也是真的坐了那么久）。 */
    val focusMs: Long,
)

/**
 * 当日分段图里的一段。
 *
 * [fraction] 是**时长占比**而不是"在一天里的位置" —— 理由见 [FocusStats.segmentsOfDay]。
 */
data class FocusSegment(
    val phase: FocusPhase,
    val actualMs: Long,
    /** 占当日全部记录时长的比例，0..1；同一天各段加起来正好 1。 */
    val fraction: Float,
    val completed: Boolean,
)

/**
 * 打卡热力图里的一格。
 *
 * [date] 为 `null` 表示**这一格不属于"过去"**（本周还没到的那几天，以及为了凑齐整周而多出来的
 * 占位格）—— 画的时候留白，而不是画成"那天没专注"。两者必须区分：一个是"还没到"，
 * 一个是"没做到"，混在一起用户看到的是假的空白。
 */
data class FocusHeatCell(
    val date: LocalDate?,
    /** 那天的专注毫秒（口径同 [FocusStats.statOf]，按**结束**时刻归口）。 */
    val focusMs: Long,
    /** 0（没有）～ 4（最深）；分档见 [FocusStats.heatLevel]。 */
    val level: Int,
)

/**
 * 统计区的**一次性派生结果**。
 *
 * 页面只做一次计算（[focusStatsView]），组件只负责画（`ui/focus/FocusStatsSection`）——
 * 专注页与「我的」页共用同一份口径，不会出现"两个页面的数字不一样"。
 * 观察窗口要覆盖到**热力图的 12 周**，不能只观察 7 天，否则热力图会大片空白。
 */
data class FocusStatsView(
    val streakDays: Int = 0,
    /** 最近 7 天（含今天）的专注合计。 */
    val weekMs: Long = 0L,
    /** 最近 30 天（含今天）的专注合计。 */
    val monthMs: Long = 0L,
    val weekStats: List<FocusDayStat> = emptyList(),
    val todaySegments: List<FocusSegment> = emptyList(),
    val heatmap: List<FocusHeatCell> = emptyList(),
) {
    /** 有没有值得画的东西。全空时统计区只给一句文案，不画空框。 */
    val hasData: Boolean
        get() = weekStats.any { it.focusMs > 0L || it.completedPomodoros > 0 } ||
            todaySegments.isNotEmpty() ||
            heatmap.any { it.focusMs > 0L }
}

/**
 * 统计的**纯函数**部分：窗口边界与聚合。
 *
 * ## 为什么窗口在 Kotlin 里现算，而不是 SQL 里写 `BETWEEN`
 *
 * 三种窗口（今天 / 本周 / 本月）全都**会自己往前走**：应用开着过午夜，"今天"就是新的一天。
 * SQL 里的固定区间冻在查询发起的那一刻，午夜之后界面继续显示昨天 —— 而这是统计页
 * 最容易出的那种"看起来没错"的错。所以 SQL 只提供"从某时刻起"（见 `FocusSessionDao`），
 * 窗口边界的计算留在这里，可以脱离数据库单测（含跨月、跨年、闰年、时区、夏令时）。
 *
 * ⚠️ 一律用**本地日期**切分，不用 UTC：用户在 UTC+8 晚上 11 点跑的番茄应该算在"今天"，
 * 换算成 UTC 就变成下午 3 点、仍属同一天，看似无事 —— 但到了 UTC+8 的凌晨 1 点，
 * UTC 还是前一天，用户的"今天"与界面的"今天"就对不上了。
 */
object FocusStats {

    /** 柱状图与"近 7 天"合计的天数。 */
    const val DAYS_IN_WEEK = 7

    /** "近 30 天"合计的天数（**滚动** 30 天，不是日历月 —— 与柱状图的"最近 7 天"同一套口径）。 */
    const val DAYS_IN_RECENT_MONTH = 30

    /** 热力图画多少周。12 周 ≈ 一个季度，手机上正好放得下。 */
    const val HEATMAP_WEEKS = 12

    /**
     * 热力图深度的分档门槛（毫秒）：25 / 60 / 120 分钟。
     *
     * 用**绝对分钟**而不是"最近 12 周里的最大值"归一化：后者会让同一份数据在不同日子
     * 画出不同深浅的同一格（今天多跑一小时，历史全变浅），用户无法比较"这周比上周如何"。
     */
    private val HEAT_THRESHOLDS_MS = longArrayOf(25 * 60_000L, 60 * 60_000L, 120 * 60_000L)

    /** 今天零点（本机时区）。 */
    fun startOfToday(clock: Clock = Clock.systemDefaultZone()): Long =
        startOfDay(LocalDate.now(clock), clock.zone)

    /** 最近 7 天（含今天）的第一天零点。 */
    fun startOfRecentWeek(clock: Clock = Clock.systemDefaultZone()): Long =
        startOfDay(LocalDate.now(clock).minusDays(6), clock.zone)

    /** 最近 30 天（含今天）的第一天零点。 */
    fun startOfRecentMonth(clock: Clock = Clock.systemDefaultZone()): Long =
        startOfDay(LocalDate.now(clock).minusDays(29), clock.zone)

    /**
     * 最近 [weeks] 周（**含本周**）的第一天零点，且一定是**周一**。
     *
     * 热力图按"一周一列"画，所以左下角那一格必须是周一：窗口起点不能是"今天往前 84 天"
     * （那样每列会错位成"今天所在的那格往下数 7 个"），必须先退到本周周一再退 `weeks - 1` 周。
     */
    fun startOfRecentWeeks(weeks: Int = HEATMAP_WEEKS, clock: Clock = Clock.systemDefaultZone()): Long =
        startOfDay(thisMonday(clock).minusWeeks((weeks - 1).coerceAtLeast(0).toLong()), clock.zone)

    /** 本周周一。 */
    private fun thisMonday(clock: Clock): LocalDate {
        val today = LocalDate.now(clock)
        return today.minusDays((today.dayOfWeek.value - 1).toLong())
    }

    fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfDay(date: LocalDate, zone: ZoneId): Long =
        startOfDay(date.plusDays(1), zone)

    /** 从毫秒时刻还原成本地日期。落库用的是墙钟毫秒，所以要按同一个时区还原。 */
    fun dateOf(wallMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(wallMs).atZone(zone).toLocalDate()

    /** 某一天的聚合。 [completed] 是"完整跑完"的判据，与番茄数口径一致。 */
    fun statOf(
        sessions: List<FocusSessionEntity>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): FocusDayStat {
        val focusOnes = sessions.filter {
            it.phase == FocusPhase.FOCUS.name && dateOf(it.endedAtWallMs, zone) == date
        }
        return FocusDayStat(
            date = date,
            completedPomodoros = focusOnes.count { it.completed },
            focusMs = focusOnes.sumOf { it.actualMs },
        )
    }

    /** 一串日期各自的聚合（图表用）。日期顺序即返回顺序。 */
    fun statsFor(
        sessions: List<FocusSessionEntity>,
        dates: List<LocalDate>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<FocusDayStat> = dates.map { statOf(sessions, it, zone) }

    /** 最近 [days] 天（**含今天**），最早的在前面 —— 柱状图的横轴。 */
    fun recentDates(days: Int, clock: Clock = Clock.systemDefaultZone()): List<LocalDate> {
        val today = LocalDate.now(clock)
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }

    /** 一天的专注累计（所有专注段，不管跑没跑完）。 */
    fun focusMsOf(sessions: List<FocusSessionEntity>): Long =
        sessions.filter { it.phase == FocusPhase.FOCUS.name }.sumOf { it.actualMs }

    /** 连续打卡天数（从今天往回数，遇到没有完整番茄的一天就停）。热力图与"连续 N 天"用它。 */
    fun streakDays(
        sessions: List<FocusSessionEntity>,
        clock: Clock = Clock.systemDefaultZone(),
    ): Int {
        val zone = clock.zone
        val doneDates = sessions
            .filter { it.phase == FocusPhase.FOCUS.name && it.completed }
            .map { dateOf(it.endedAtWallMs, zone) }
            .toHashSet()
        if (doneDates.isEmpty()) return 0
        var day = LocalDate.now(clock)
        // 今天还没打卡不算断：从今天起数，今天没有就从昨天起，昨天也没有才真断
        if (day !in doneDates) day = day.minusDays(1)
        var streak = 0
        while (day in doneDates) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** 两个日期相隔几天，供页面算"今天是本周第几天"这类坐标。 */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** 最近 [days] 天（**含今天**）的逐日聚合，最早在前 —— 柱状图的横轴。 */
    fun dailyTotals(
        sessions: List<FocusSessionEntity>,
        days: Int,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<FocusDayStat> = statsFor(sessions, recentDates(days, clock), clock.zone)

    /** 最近 [days] 天（含今天）的专注**合计**毫秒（"近 7 天 / 近 30 天"那一行数字）。 */
    fun totalFocusMs(
        sessions: List<FocusSessionEntity>,
        days: Int,
        clock: Clock = Clock.systemDefaultZone(),
    ): Long = dailyTotals(sessions, days, clock).sumOf { it.focusMs }

    /** 当天专注毫秒 → 热力图深度 0..4（门槛见 [HEAT_THRESHOLDS_MS]）。 */
    fun heatLevel(focusMs: Long): Int =
        if (focusMs <= 0L) 0 else 1 + HEAT_THRESHOLDS_MS.count { focusMs >= it }

    /**
     * 热力图的格子：**按列**排（一周 7 格），共 [weeks] 列，最后一列是本周。
     *
     * 返回的是扁平表而不是二维数组：Compose 里"先按列分组再画"用 `chunked(7)` 一行就够，
     * 而二维数组的每一层都要建对象。行列顺序即"最左最上 = 最早"，渲染层不需要再排序。
     *
     * 未来那几格 [FocusHeatCell.date] 为 `null`（见该类的说明）。
     */
    fun heatmap(
        sessions: List<FocusSessionEntity>,
        weeks: Int = HEATMAP_WEEKS,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<FocusHeatCell> {
        if (weeks <= 0) return emptyList()
        val zone = clock.zone
        val today = LocalDate.now(clock)
        // 先按天汇总一次：84 格 × 全表扫描会是 O(n²)，攒够几个月就明显卡
        val msByDate = HashMap<LocalDate, Long>()
        sessions.forEach { session ->
            if (session.phase != FocusPhase.FOCUS.name) return@forEach
            val date = dateOf(session.endedAtWallMs, zone)
            msByDate[date] = (msByDate[date] ?: 0L) + session.actualMs
        }
        val firstMonday = thisMonday(clock).minusWeeks((weeks - 1).toLong())
        return (0 until weeks).flatMap { week ->
            (0L until 7L).map { offset ->
                val date = firstMonday.plusDays(week * 7L + offset)
                if (date.isAfter(today)) {
                    FocusHeatCell(date = null, focusMs = 0L, level = 0)
                } else {
                    val ms = msByDate[date] ?: 0L
                    FocusHeatCell(date = date, focusMs = ms, level = heatLevel(ms))
                }
            }
        }
    }

    /**
     * 统计区要用的全部数字，**一次算完**。
     *
     * 调用方（专注页的 ViewModel、「我的」页）各自只需要一个 Room 观察者，
     * 观察窗口用 [startOfRecentWeeks]（覆盖热力图的 12 周，比柱状图的 7 天长）。
     */
    fun focusStatsView(
        sessions: List<FocusSessionEntity>,
        clock: Clock = Clock.systemDefaultZone(),
    ): FocusStatsView = FocusStatsView(
        streakDays = streakDays(sessions, clock),
        weekMs = totalFocusMs(sessions, DAYS_IN_WEEK, clock),
        monthMs = totalFocusMs(sessions, DAYS_IN_RECENT_MONTH, clock),
        weekStats = dailyTotals(sessions, DAYS_IN_WEEK, clock),
        todaySegments = segmentsOfDay(sessions, LocalDate.now(clock), clock.zone),
        heatmap = heatmap(sessions, HEATMAP_WEEKS, clock),
    )

    /**
     * 某个**本地日期**里的所有记录（专注 + 休息）按时间先后排出，并各自算出时长占比。
     *
     * ## 为什么是"占比"而不是"在一天里的位置"
     *
     * 一段 25 分钟的专注只占 24 小时的 1.7% —— 画在一条全天横轴上就是几个像素，
     * 手机竖屏再窄一点直接缩没，"看看今天怎么攒起来的"这个用处也就没了。
     * 所以横条的**总长度代表今天的总时长**，每段按自己的时长得宽度：
     * 专注与休息的配比一眼可见，跑了几段也数得出来。
     *
     * 归口用**开始时刻**所在的那一天：跨过午夜的一段算在它开始的那天，不劈成两半
     *（与 [statOf] 用 `endedAtWallMs` 归口的口径不同，刻意如此：柱状图数的是"哪天完成了
     * 一个番茄"，分段图讲的是"这一段是几点开始的"）。
     *
     * 时长 <= 0 的段丢掉（否则占比里会出现 0 长度或除零）；总时长为 0 时返回**空表**，
     * 由调用方决定空态文案。
     */
    fun segmentsOfDay(
        sessions: List<FocusSessionEntity>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<FocusSegment> {
        val ofDay = sessions
            .filter { dateOf(it.startedAtWallMs, zone) == date && it.actualMs > 0L }
            .sortedBy { it.startedAtWallMs }
        val total = ofDay.sumOf { it.actualMs }
        if (total <= 0L) return emptyList()
        return ofDay.map { session ->
            FocusSegment(
                phase = phaseOf(session.phase),
                actualMs = session.actualMs,
                // 直接按毫秒算比值（不要先转 Float 再除：大毫秒数转 Float 会丢精度）
                fraction = (session.actualMs.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f),
                completed = session.completed,
            )
        }
    }

    /** 库里存的是枚举名；脏数据（改过枚举、手工改过库）不该让统计区崩，退回专注段。 */
    private fun phaseOf(raw: String): FocusPhase =
        FocusPhase.entries.firstOrNull { it.name == raw } ?: FocusPhase.FOCUS
}
