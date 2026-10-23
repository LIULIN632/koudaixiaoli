package com.pocket.xiaoli.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 番茄钟内核（[FocusEngine]）。
 *
 * ## 为什么这些用例值得写
 *
 * 内核里每一条规则**写反了都不会崩**，只会以"时间悄悄不对"的方式表现出来：
 * 暂停后剩余继续掉、锁屏半小时后显示的还是 24:59、重启后一段永远走不完。
 * 这些在界面上都长得像"正常倒计时"，只有把时钟推进到特定位置才看得出来 ——
 * 所以内核不碰 `android.*`，时间一律由用例喂进去（见 `FocusEngine` 的 KDoc）。
 */
class FocusEngineTest {

    private val config = FocusConfig()

    /** 一段 25 分钟的专注：从 `nowElapsed` 起。 */
    private fun focus(
        nowElapsed: Long = 1_000L,
        nowWall: Long = WALL,
        linkedTaskId: Long? = null,
        linkedTaskTitle: String? = null,
    ) = FocusEngine.start(
        phase = FocusPhase.FOCUS,
        config = config,
        nowElapsed = nowElapsed,
        nowWall = nowWall,
        linkedTaskId = linkedTaskId,
        linkedTaskTitle = linkedTaskTitle,
    )

    // ── 剩余 / 进度 ──

    @Test
    fun `剩余由截止时间算出，与中途刷新多少次无关`() {
        val runtime = focus(nowElapsed = 0L)
        assertEquals(FOCUS_MS, FocusEngine.remainingMs(runtime, 0L))
        // 跳到任意位置，结果只取决于 deadline - now：中间"漏掉"多少次刷新都一样
        assertEquals(FOCUS_MS - 60_000L, FocusEngine.remainingMs(runtime, 60_000L))
        assertEquals(1L, FocusEngine.remainingMs(runtime, FOCUS_MS - 1L))
    }

    @Test
    fun `过点剩余钳到 0，不返回负数`() {
        val runtime = focus(nowElapsed = 0L)
        assertEquals(0L, FocusEngine.remainingMs(runtime, FOCUS_MS))
        assertEquals(0L, FocusEngine.remainingMs(runtime, FOCUS_MS + 999_999L))
        // 负数会让进度条反向画、格式化成 -1:-30
        assertTrue(FocusEngine.remainingMs(runtime, Long.MAX_VALUE) >= 0L)
    }

    @Test
    fun `进度钳在 0 到 1，脏快照不除零`() {
        val runtime = focus(nowElapsed = WALL_ELAPSED)
        assertEquals(0f, FocusEngine.progress(runtime, WALL_ELAPSED), 0.0001f)
        assertEquals(0.5f, FocusEngine.progress(runtime, WALL_ELAPSED + FOCUS_MS / 2), 0.0001f)
        assertEquals(1f, FocusEngine.progress(runtime, WALL_ELAPSED + FOCUS_MS * 10), 0.0001f)
        // 时钟倒退（now 早于开始）也不能越界
        assertEquals(0f, FocusEngine.progress(runtime, 0L), 0.0001f)
        // plannedMs = 0 的壳：返回 0 而不是 NaN
        assertEquals(0f, FocusEngine.progress(FocusRuntime(plannedMs = 0L), WALL_ELAPSED), 0.0001f)
    }

    // ── 过期 ──

    @Test
    fun `到点即过期，但只要没到就不算`() {
        val runtime = focus(nowElapsed = 0L)
        assertFalse(FocusEngine.isExpired(runtime, FOCUS_MS - 1L))
        assertTrue(FocusEngine.isExpired(runtime, FOCUS_MS))
        assertTrue(FocusEngine.isExpired(runtime, FOCUS_MS + 1L))
    }

    @Test
    fun `暂停中永不过期，哪怕截止时间早就过去了`() {
        val runtime = focus(nowElapsed = 0L)
        val paused = FocusEngine.pause(runtime, 60_000L)
        // 暂停的含义就是"先停在这儿"，用户回来点继续才该往下走
        assertFalse(FocusEngine.isExpired(paused, FOCUS_MS + 86_400_000L))
    }

    // ── 暂停 / 继续 ──

    @Test
    fun `暂停把剩余冻结，时间照走也不掉`() {
        val runtime = focus(nowElapsed = 0L)
        val paused = FocusEngine.pause(runtime, 60_000L)
        assertEquals(FOCUS_MS - 60_000L, paused.pausedRemainingMs)
        assertEquals(FOCUS_MS - 60_000L, FocusEngine.remainingMs(paused, 60_000L))
        // 一小时后回来，剩余还是那个数
        assertEquals(FOCUS_MS - 60_000L, FocusEngine.remainingMs(paused, 3_660_000L))
    }

    @Test
    fun `重复暂停是幂等的`() {
        val runtime = focus(nowElapsed = 0L)
        val once = FocusEngine.pause(runtime, 60_000L)
        // 连点两下暂停不该把剩余又算一遍
        assertEquals(once, FocusEngine.pause(once, 120_000L))
    }

    @Test
    fun `继续时重设截止点，不是把暂停时长加回去`() {
        val runtime = focus(nowElapsed = 0L)
        val paused = FocusEngine.pause(runtime, 60_000L)
        val left = FOCUS_MS - 60_000L
        val resumed = FocusEngine.resume(paused, nowElapsed = 3_660_000L, nowWall = WALL + 3_600_000L)
        assertEquals(3_660_000L + left, resumed.deadlineElapsedMs)
        assertEquals(WALL + 3_600_000L + left, resumed.deadlineWallClockMs)
        assertFalse(resumed.isPaused)
        // 暂停的那一小时不算专注时长
        assertEquals(60_000L, FocusEngine.elapsedMsFor(resumed, 3_660_000L))
    }

    @Test
    fun `暂停时长不计入实际专注时长`() {
        val runtime = focus(nowElapsed = 0L)
        val paused = FocusEngine.pause(runtime, 60_000L)
        assertEquals(60_000L, FocusEngine.elapsedMsFor(paused, 99_999_999L))
        assertEquals(0L, FocusEngine.elapsedMsFor(runtime, 0L))
        // 越界也钳在计划时长内
        assertEquals(FOCUS_MS, FocusEngine.elapsedMsFor(runtime, FOCUS_MS * 3))
    }

    // ── 切段 ──

    @Test
    fun `第 4 个专注段之后走长休，其余走短休`() {
        fun nextAfterRound(round: Int): FocusPhase {
            val runtime = focus(nowElapsed = 0L).copy(roundIndex = round)
            return FocusEngine.next(runtime, config, 0L, WALL).phase
        }
        assertEquals(FocusPhase.SHORT_BREAK, nextAfterRound(1))
        assertEquals(FocusPhase.SHORT_BREAK, nextAfterRound(2))
        assertEquals(FocusPhase.SHORT_BREAK, nextAfterRound(3))
        assertEquals(FocusPhase.LONG_BREAK, nextAfterRound(4))
        // 第二组照旧：第 8 个之后又是长休
        assertEquals(FocusPhase.SHORT_BREAK, nextAfterRound(5))
        assertEquals(FocusPhase.LONG_BREAK, nextAfterRound(8))
    }

    @Test
    fun `休息段结束回到专注段且轮次加一`() {
        val shortBreak = FocusEngine.start(FocusPhase.SHORT_BREAK, config, 0L, WALL, roundIndex = 2)
        val back = FocusEngine.next(shortBreak, config, 0L, WALL)
        assertEquals(FocusPhase.FOCUS, back.phase)
        assertEquals(3, back.roundIndex)

        val longBreak = FocusEngine.start(FocusPhase.LONG_BREAK, config, 0L, WALL, roundIndex = 4)
        val afterLong = FocusEngine.next(longBreak, config, 0L, WALL)
        assertEquals(FocusPhase.FOCUS, afterLong.phase)
        assertEquals(5, afterLong.roundIndex)
    }

    @Test
    fun `两段时长不同，切段用的是各自的配置`() {
        assertEquals(FOCUS_MS, FocusEngine.plannedMsFor(FocusPhase.FOCUS, config))
        assertEquals(5 * 60_000L, FocusEngine.plannedMsFor(FocusPhase.SHORT_BREAK, config))
        assertEquals(15 * 60_000L, FocusEngine.plannedMsFor(FocusPhase.LONG_BREAK, config))
        // 切段之后 plannedMs 与两个 deadline 一起换掉，不是沿用上一段的
        val next = FocusEngine.next(focus(nowElapsed = 0L), config, nowElapsed = 100L, nowWall = WALL + 100L)
        assertEquals(5 * 60_000L, next.plannedMs)
        assertEquals(100L + 5 * 60_000L, next.deadlineElapsedMs)
        assertEquals(WALL + 100L, next.startedAtWallMs)
    }

    @Test
    fun `关联待办跨段沿用`() {
        val runtime = focus(nowElapsed = 0L, linkedTaskId = 7L, linkedTaskTitle = "写高数作业")
        val shortBreak = FocusEngine.next(runtime, config, 0L, WALL)
        // 用户是对着这条任务起钟的，短休回来还在做同一件事
        assertEquals("写高数作业", shortBreak.linkedTaskTitle)
        assertEquals(7L, shortBreak.linkedTaskId ?: -1L)
        val back = FocusEngine.next(shortBreak, config, 0L, WALL)
        assertEquals("写高数作业", back.linkedTaskTitle)
        assertEquals(7L, back.linkedTaskId ?: -1L)
        // 起新钟（不传关联）时是干净的
        assertNull(focus(nowElapsed = 0L).linkedTaskId)
    }

    // ── 圈点 ──

    @Test
    fun `圈点在本组里循环`() {
        fun cycleOf(round: Int) = FocusEngine.roundInCycle(focus(0L).copy(roundIndex = round), config)
        assertEquals(1, cycleOf(1))
        assertEquals(4, cycleOf(4))
        // 第 5 个专注段又是这一组的第 1 个
        assertEquals(1, cycleOf(5))
        assertEquals(2, cycleOf(6))
    }

    // ── 冷路径 ──

    @Test
    fun `冷路径用墙钟重锚，把重启归零的单调钟纠正回来`() {
        // 快照：墙钟上还剩 15 分钟（23:45 的 deadline，现在 23:30）
        val runtime = focus(nowElapsed = 0L, nowWall = WALL)
        val rebooted = FocusEngine.rebase(
            runtime,
            nowElapsed = 5_000L,               // 刚开机，单调钟几乎是 0
            nowWall = WALL + 10 * 60_000L,     // 墙钟走过了 10 分钟
        )
        assertEquals(15 * 60_000L, FocusEngine.remainingMs(rebooted, 5_000L))
        assertEquals(5_000L + 15 * 60_000L, rebooted.deadlineElapsedMs)
        // 不重锚的话剩余还是"开机前那一刻"的 25 分钟 —— 重启归零的单调钟把
        // deadline 变成了一个再也走不到的数，这就是必须重锚的原因
        assertEquals(FOCUS_MS - 5_000L, FocusEngine.remainingMs(runtime, 5_000L))
    }

    @Test
    fun `冷路径已经过点则剩余归零，不返回负数`() {
        val runtime = focus(nowElapsed = 0L, nowWall = WALL)
        val late = FocusEngine.rebase(runtime, nowElapsed = 0L, nowWall = WALL + FOCUS_MS * 2)
        assertEquals(0L, FocusEngine.remainingMs(late, 0L))
        assertTrue(FocusEngine.isExpired(late, 0L))
    }

    @Test
    fun `暂停中的快照重锚前后一模一样`() {
        val paused = FocusEngine.pause(focus(nowElapsed = 0L), 60_000L)
        // 剩余是冻结值，本来就跟时钟无关 —— 重锚不该碰它
        assertEquals(paused, FocusEngine.rebase(paused, nowElapsed = 999_999L, nowWall = WALL + 999_999L))
    }

    // ── 配置 ──

    @Test
    fun `配置钳回可用范围`() {
        val dirty = FocusConfig(
            focusMinutes = 0,
            shortBreakMinutes = -5,
            longBreakMinutes = 9_999,
            roundsBeforeLongBreak = 0,
        ).sanitized()
        assertEquals(FocusConfig.MIN_MINUTES, dirty.focusMinutes)
        assertEquals(FocusConfig.MIN_MINUTES, dirty.shortBreakMinutes)
        assertEquals(FocusConfig.MAX_MINUTES, dirty.longBreakMinutes)
        assertEquals(1, dirty.roundsBeforeLongBreak)
        // 合法值原样通过
        assertEquals(FocusConfig.DEFAULT, FocusConfig.DEFAULT.sanitized())
    }

    @Test
    fun `默认配置就是 25 5 15 4`() {
        assertEquals(25, config.focusMinutes)
        assertEquals(5, config.shortBreakMinutes)
        assertEquals(15, config.longBreakMinutes)
        assertEquals(4, config.roundsBeforeLongBreak)
        assertTrue(config.autoStartNext)
    }

    private companion object {
        const val WALL = 1_700_000_000_000L
        const val WALL_ELAPSED = 1_000L
        const val FOCUS_MS = 25 * 60_000L
    }
}
