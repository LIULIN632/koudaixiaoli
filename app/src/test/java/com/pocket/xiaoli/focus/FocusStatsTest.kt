package com.pocket.xiaoli.focus

import com.pocket.xiaoli.data.entity.FocusSessionEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 统计的窗口与聚合（[FocusStats]）。
 *
 * ## 为什么窗口边界值得单测
 *
 * 三种窗口全都**自己往前走**：应用开着过午夜，"今天"就是新的一天。写成 SQL 里的固定
 * `BETWEEN` 之后，界面会继续显示昨天 —— 而这在页面上长得完全正常（数还是那些数），
 * 只有跨过午夜才看得出来。所以边界在 Kotlin 里现算，也就可以在这里用固定时钟覆盖
 * 跨天、跨月、跨年与时区。
 *
 * 时区固定成 `Asia/Shanghai`（UTC+8，无夏令时）：下面那条"凌晨的番茄算哪天"
 * 正是靠它才区分得开。
 */
class FocusStatsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 本机时间 2026-10-16 10:30。 */
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-16T02:30:00Z"), zone)

    private fun session(
        endedAt: String,
        phase: FocusPhase = FocusPhase.FOCUS,
        completed: Boolean = true,
        actualMs: Long = 25 * 60_000L,
    ) = FocusSessionEntity(
        phase = phase.name,
        roundIndex = 1,
        plannedMs = 25 * 60_000L,
        actualMs = actualMs,
        startedAtWallMs = Instant.parse(endedAt).toEpochMilli() - 25 * 60_000L,
        endedAtWallMs = Instant.parse(endedAt).toEpochMilli(),
        completed = completed,
    )

    // ── 窗口边界 ──

    @Test
    fun `今天零点按本地时区算`() {
        // 2026-10-16 00:00 +08:00 == 2026-10-15 16:00 UTC
        assertEquals(
            Instant.parse("2026-10-15T16:00:00Z").toEpochMilli(),
            FocusStats.startOfToday(clock),
        )
    }

    @Test
    fun `本周是最近 7 天含今天，本月是最近 30 天含今天`() {
        assertEquals(FocusStats.startOfDay(LocalDate.of(2026, 10, 10), zone), FocusStats.startOfRecentWeek(clock))
        assertEquals(FocusStats.startOfDay(LocalDate.of(2026, 9, 17), zone), FocusStats.startOfRecentMonth(clock))
    }

    @Test
    fun `recentDates 含今天且最早的在前面`() {
        val dates = FocusStats.recentDates(7, clock)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 10, 10), dates.first())
        assertEquals(LocalDate.of(2026, 10, 16), dates.last())
    }

    @Test
    fun `daysBetween 就是两个日期的天数差`() {
        assertEquals(6L, FocusStats.daysBetween(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16)))
        assertEquals(0L, FocusStats.daysBetween(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun `endOfDay 就是次日零点`() {
        val day = LocalDate.of(2026, 10, 16)
        assertEquals(FocusStats.startOfDay(LocalDate.of(2026, 10, 17), zone), FocusStats.endOfDay(day, zone))
    }

    // ── 日期归口 ──

    @Test
    fun `凌晨的番茄算本地的那一天，不是 UTC 那天`() {
        // 本地 2026-10-16 00:30 == UTC 2026-10-15 16:30
        val wall = Instant.parse("2026-10-15T16:30:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 16), FocusStats.dateOf(wall, zone))
        // 按 UTC 就算成前一天了 —— 这就是不能拿 UTC 切窗口的原因
        assertEquals(LocalDate.of(2026, 10, 15), FocusStats.dateOf(wall, ZoneOffset.UTC))
    }

    // ── 聚合口径 ──

    @Test
    fun `番茄数只数跑满的专注段，时长含被中断的那些`() {
        val day = LocalDate.of(2026, 10, 16)
        val sessions = listOf(
            session("2026-10-16T01:00:00Z", completed = true, actualMs = 25 * 60_000L),
            session("2026-10-16T02:00:00Z", completed = false, actualMs = 10 * 60_000L),
            // 休息段不进番茄数，也不进专注时长
            session("2026-10-16T02:30:00Z", phase = FocusPhase.SHORT_BREAK, completed = true, actualMs = 5 * 60_000L),
            // 昨天的段不算今天
            session("2026-10-15T02:00:00Z", completed = true),
        )
        val stat = FocusStats.statOf(sessions, day, zone)
        assertEquals(1, stat.completedPomodoros)
        // 被放弃的那一段也是真的坐了 10 分钟，所以计入时长
        assertEquals(35 * 60_000L, stat.focusMs)
    }

    @Test
    fun `没有记录的一天是 0，不返回 null`() {
        val stat = FocusStats.statOf(emptyList(), LocalDate.of(2026, 10, 16), zone)
        assertEquals(0, stat.completedPomodoros)
        assertEquals(0L, stat.focusMs)
    }

    @Test
    fun `statsFor 按传入的日期顺序给出每天的聚合`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z"),
            session("2026-10-14T01:00:00Z"),
        )
        val dates = listOf(LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16))
        val stats = FocusStats.statsFor(sessions, dates, zone)
        assertEquals(3, stats.size)
        assertEquals(listOf(1, 0, 1), stats.map { it.completedPomodoros })
        assertEquals(dates, stats.map { it.date })
    }

    @Test
    fun `focusMsOf 不管跑没跑完，但只算专注段`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z", completed = true, actualMs = 25 * 60_000L),
            session("2026-10-16T02:00:00Z", completed = false, actualMs = 10 * 60_000L),
            session("2026-10-16T03:00:00Z", phase = FocusPhase.LONG_BREAK, actualMs = 15 * 60_000L),
        )
        assertEquals(35 * 60_000L, FocusStats.focusMsOf(sessions))
    }

    // ── 连续打卡 ──

    @Test
    fun `连续三天都有完整番茄就是 3 天`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z"),
            session("2026-10-15T01:00:00Z"),
            session("2026-10-14T01:00:00Z"),
            session("2026-10-11T01:00:00Z"), // 断档，不该被跳过去接上
        )
        assertEquals(3, FocusStats.streakDays(sessions, clock))
    }

    @Test
    fun `今天还没打卡不算断，从昨天开始数`() {
        val sessions = listOf(
            session("2026-10-15T01:00:00Z"),
            session("2026-10-14T01:00:00Z"),
        )
        assertEquals(2, FocusStats.streakDays(sessions, clock))
    }

    @Test
    fun `昨天今天都没有就断了`() {
        val sessions = listOf(session("2026-10-13T01:00:00Z"))
        assertEquals(0, FocusStats.streakDays(sessions, clock))
    }

    @Test
    fun `没跑完的段不给打卡`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z", completed = false),
            session("2026-10-15T01:00:00Z", completed = true),
        )
        // 今天那段没跑完 → 相当于今天还没打卡 → 从昨天数 = 1
        assertEquals(1, FocusStats.streakDays(sessions, clock))
    }

    @Test
    fun `没有记录时是 0`() {
        assertEquals(0, FocusStats.streakDays(emptyList(), clock))
    }

    // ── 柱状图：最近 7 天的逐日聚合 ──

    @Test
    fun `dailyTotals 是最近 7 天含今天、最早在前，窗口外的段不算`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z"), // 本地 10-16
            session("2026-10-12T01:00:00Z"), // 本地 10-12
            session("2026-10-11T01:00:00Z"), // 本地 10-11
            session("2026-10-09T01:00:00Z"), // 本地 10-09，在 7 天窗口之外
        )
        val stats = FocusStats.dailyTotals(sessions, 7, clock)
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 11),
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 16),
            ),
            stats.map { it.date },
        )
        assertEquals(listOf(0, 1, 1, 0, 0, 0, 1), stats.map { it.completedPomodoros })
        // 10-09 那段落在窗口外，所以一根柱子都不该被它抬高
        assertEquals(3, stats.sumOf { it.completedPomodoros })
    }

    @Test
    fun `dailyTotals 要 0 天就是空表，不是 7 天`() {
        assertEquals(emptyList<FocusDayStat>(), FocusStats.dailyTotals(listOf(session("2026-10-16T01:00:00Z")), 0, clock))
    }

    // ── 分段图：时长占比 ──

    @Test
    fun `segmentsOfDay 按时间顺序给出占比，只挑当天`() {
        val day = LocalDate.of(2026, 10, 16)
        val sessions = listOf(
            session("2026-10-16T01:00:00Z", actualMs = 25 * 60_000L),
            session("2026-10-16T02:00:00Z", phase = FocusPhase.SHORT_BREAK, actualMs = 5 * 60_000L),
            session("2026-10-15T01:00:00Z"), // 昨天的不算
        )
        val segments = FocusStats.segmentsOfDay(sessions, day, zone)
        assertEquals(2, segments.size)
        assertEquals(listOf(FocusPhase.FOCUS, FocusPhase.SHORT_BREAK), segments.map { it.phase })
        assertEquals(listOf(25 * 60_000L, 5 * 60_000L), segments.map { it.actualMs })
        // 25/30 与 5/30
        assertEquals(0.8333f, segments[0].fraction, 1e-3f)
        assertEquals(0.1667f, segments[1].fraction, 1e-3f)
        assertEquals(1f, segments.sumOf { it.fraction.toDouble() }.toFloat(), 1e-3f)
    }

    @Test
    fun `分段图按开始时刻归口，跨午夜的一段算在开始那天`() {
        // 本地 10-16 23:50 开始、跑了 25 分钟 → 本地 10-17 00:15 结束
        val crossing = session("2026-10-16T16:15:00Z")
        assertEquals(
            1,
            FocusStats.segmentsOfDay(listOf(crossing), LocalDate.of(2026, 10, 16), zone).size,
        )
        // ⚠️ 与柱状图的口径**刻意不同**：柱状图按 endedAtWallMs 归口，跨午夜这段算 10-17 的番茄
        assertEquals(1, FocusStats.statOf(listOf(crossing), LocalDate.of(2026, 10, 17), zone).completedPomodoros)
        assertEquals(emptyList<FocusSegment>(), FocusStats.segmentsOfDay(listOf(crossing), LocalDate.of(2026, 10, 17), zone))
    }

    @Test
    fun `时长为 0 的段不进分段图，也不让占比除零`() {
        val day = LocalDate.of(2026, 10, 16)
        val zero = session("2026-10-16T01:00:00Z", actualMs = 0L)
        assertEquals(emptyList<FocusSegment>(), FocusStats.segmentsOfDay(listOf(zero), day, zone))
        // 一段 0 毫秒 + 一段正常：只有那一段进图，且它占满整条
        val segments = FocusStats.segmentsOfDay(
            listOf(zero, session("2026-10-16T02:00:00Z", actualMs = 10 * 60_000L)),
            day,
            zone,
        )
        assertEquals(1, segments.size)
        assertEquals(1f, segments[0].fraction, 1e-6f)
    }

    @Test
    fun `分段图空列表给空表`() {
        assertEquals(emptyList<FocusSegment>(), FocusStats.segmentsOfDay(emptyList(), LocalDate.of(2026, 10, 16), zone))
    }

    @Test
    fun `库里存了不认识的阶段名时退回专注段，不抛异常`() {
        val dirty = FocusSessionEntity(
            phase = "POMODORO", // 改过枚举 / 手工改过库留下的
            roundIndex = 1,
            plannedMs = 25 * 60_000L,
            actualMs = 25 * 60_000L,
            startedAtWallMs = Instant.parse("2026-10-16T00:35:00Z").toEpochMilli(),
            endedAtWallMs = Instant.parse("2026-10-16T01:00:00Z").toEpochMilli(),
            completed = true,
        )
        val segments = FocusStats.segmentsOfDay(listOf(dirty), LocalDate.of(2026, 10, 16), zone)
        assertEquals(listOf(FocusPhase.FOCUS), segments.map { it.phase })
    }

    // ── 「我的」页那枚磁贴的副标题（focusTileHint）──

    @Test
    fun `磁贴副标题：今天一次都没跑过就直说没开始`() {
        // 不显示「今天 0 秒」：那看着像坏了，也说不清是"没跑"还是"没记上"
        assertEquals("今天还没开始", focusTileHint(FocusDayStat(LocalDate.of(2026, 10, 16), 0, 0L)))
    }

    @Test
    fun `磁贴副标题：说过多久而不是说几个番茄`() {
        assertEquals(
            "今天 25 分钟",
            focusTileHint(FocusDayStat(LocalDate.of(2026, 10, 16), 1, 25 * 60_000L)),
        )
        // 超过一小时要说成"1 小时 20 分"，与统计区、今日卡用的是同一台格式化（humanDuration）
        assertEquals(
            "今天 1 小时 20 分",
            focusTileHint(FocusDayStat(LocalDate.of(2026, 10, 16), 3, 80 * 60_000L)),
        )
    }

    @Test
    fun `磁贴副标题：不足一分钟也如实说秒数，不吞成没开始`() {
        assertEquals(
            "今天 40 秒",
            focusTileHint(FocusDayStat(LocalDate.of(2026, 10, 16), 0, 40_000L)),
        )
    }

    // ── 热力图 ──

    @Test
    fun `热力图分档：25 分钟、1 小时、2 小时是三处台阶`() {
        assertEquals(0, FocusStats.heatLevel(0L))
        // 跑了 1 秒也算"有"（第 1 档），否则"跑了两分钟就放弃"那天看起来像没来过
        assertEquals(1, FocusStats.heatLevel(1_000L))
        assertEquals(1, FocusStats.heatLevel(24 * 60_000L))
        assertEquals(2, FocusStats.heatLevel(25 * 60_000L))
        assertEquals(2, FocusStats.heatLevel(59 * 60_000L))
        assertEquals(3, FocusStats.heatLevel(60 * 60_000L))
        assertEquals(4, FocusStats.heatLevel(120 * 60_000L))
        assertEquals(4, FocusStats.heatLevel(500 * 60_000L))
    }

    @Test
    fun `热力图是 12 列 7 行，左下角是周一，最后一格是今天所在那周`() {
        val cells = FocusStats.heatmap(emptyList(), clock = clock)
        assertEquals(84, cells.size)
        // 2026-10-16 是周五 → 本周周一 = 10-12；往前 11 周 = 2026-07-27
        assertEquals(LocalDate.of(2026, 7, 27), cells.first().date)
        assertEquals(LocalDate.of(2026, 7, 27).dayOfWeek, cells.first().date?.dayOfWeek)
        // 到 10-16 为止都有日期，之后（10-17、10-18）是未来 → null
        assertEquals(LocalDate.of(2026, 10, 16), cells[81].date)
        assertEquals(null, cells[82].date)
        assertEquals(null, cells[83].date)
    }

    @Test
    fun `热力图按天汇总专注毫秒（休息段不算），未来那几格不给日期`() {
        val sessions = listOf(
            // 10-16 +08:00 早上的两段专注（25 + 20 分钟）
            session("2026-10-16T01:00:00Z"),
            session("2026-10-16T01:30:00Z", actualMs = 20 * 60_000L),
            // 同一天的休息段：不该进热力图
            session("2026-10-16T02:00:00Z", phase = FocusPhase.SHORT_BREAK, actualMs = 5 * 60_000L),
        )
        val cells = FocusStats.heatmap(sessions, clock = clock)
        val today = cells[81]
        assertEquals(LocalDate.of(2026, 10, 16), today.date)
        assertEquals(45 * 60_000L, today.focusMs)
        assertEquals(2, today.level) // 45 分钟 → 第二档
        // 昨天没有记录：有日期（过去），但 focusMs=0、level=0 —— 与"未来"那种 null 不同
        assertEquals(LocalDate.of(2026, 10, 15), cells[80].date)
        assertEquals(0L, cells[80].focusMs)
    }

    @Test
    fun `热力图要 0 周就是空表，不会因为除零或负数崩`() {
        assertEquals(emptyList<FocusHeatCell>(), FocusStats.heatmap(emptyList(), weeks = 0, clock = clock))
        assertEquals(emptyList<FocusHeatCell>(), FocusStats.heatmap(emptyList(), weeks = -3, clock = clock))
    }

    @Test
    fun `热力图窗口起点是本周周一再往前 11 周，用来定查询范围`() {
        // 2026-09-17 往后到今天不足 12 周，但窗口从 07-27 起 ⇒ 够热力图用
        assertEquals(
            FocusStats.startOfDay(LocalDate.of(2026, 7, 27), zone),
            FocusStats.startOfRecentWeeks(clock = clock),
        )
    }

    // ── 区间合计与"一次派生" ──

    @Test
    fun `近 7 天与近 30 天合计只加专注段`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z"), // 今天
            session("2026-10-14T01:00:00Z"), // 本轮 7 天窗口内
            session("2026-10-01T01:00:00Z"), // 只在 30 天窗口内
            session("2026-08-01T01:00:00Z"), // 两个窗口都不算
            session("2026-10-16T02:00:00Z", phase = FocusPhase.LONG_BREAK, actualMs = 15 * 60_000L),
        )
        assertEquals(50 * 60_000L, FocusStats.totalFocusMs(sessions, FocusStats.DAYS_IN_WEEK, clock))
        assertEquals(75 * 60_000L, FocusStats.totalFocusMs(sessions, FocusStats.DAYS_IN_RECENT_MONTH, clock))
    }

    @Test
    fun `一次派生把概览、柱状图、分段图与热力图装进同一份数据`() {
        val sessions = listOf(
            session("2026-10-16T01:00:00Z"),
            session("2026-10-15T01:00:00Z"),
            session("2026-10-14T01:00:00Z"),
        )
        val view = FocusStats.focusStatsView(sessions, clock)
        assertEquals(3, view.streakDays) // 今天、昨天、前天各一个
        assertEquals(75 * 60_000L, view.weekMs)
        assertEquals(75 * 60_000L, view.monthMs)
        assertEquals(7, view.weekStats.size)
        assertEquals(1, view.todaySegments.size)
        assertEquals(84, view.heatmap.size)
        assertEquals(true, view.hasData)
    }

    @Test
    fun `没有任何记录时 hasData 是假（页面据此只留一句话，不画空框）`() {
        val view = FocusStats.focusStatsView(emptyList(), clock)
        assertEquals(false, view.hasData)
        assertEquals(0, view.streakDays)
        assertEquals(0L, view.weekMs)
        assertEquals(84, view.heatmap.size)
    }
}
