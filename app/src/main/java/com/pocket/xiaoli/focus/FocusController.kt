package com.pocket.xiaoli.focus

import android.content.Context
import android.os.SystemClock
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.dao.FocusSessionDao
import com.pocket.xiaoli.data.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * 番茄钟的**唯一入口**。
 *
 * 起钟 / 暂停 / 继续 / 跳过 / 放弃 / 到点收尾，全部只从这里进 ——
 * 页面、通知、闹钟接收器、开机接收器都是它的调用方，谁都不许自己写快照或自己排闹钟。
 *
 * ## 为什么必须是单例（经 `AppContainer` 拿）
 *
 * 页面与前台服务跑在**同一个进程**里，它们共享 `FocusStore` 的 `StateFlow` 作为"当前状态"的
 * 实时镜像。若服务自己 `new FocusStore(...)`，两个实例的 Flow 就各说各话：
 * 服务把这一段跑完了，页面还停在旧值上，直到用户重进页面才刷新。
 * 所以装配**只发生在 `AppContainer`**，其余代码一律走 [of]。
 *
 * ## 状态变更的三件套
 *
 * 每一次 [apply] 都同时做三件事，**顺序不能换**：
 * 1. 写快照（先落盘，后面崩了也不会丢状态）
 * 2. 排/撤闹钟（到点的唯一保障）
 * 3. 拉起/停掉前台服务并刷新通知（用户能看见的那一部分）
 *
 * ## 落库口径
 *
 * 只有**跑满**的段记 `completed = true`；跳过与放弃记 `false`，且不足
 * [MIN_RECORDED_MS] 的段**根本不落库** —— 否则"点错了立刻跳过"会在统计里
 * 留下几百条一秒长的记录，把柱状图与"今天几个番茄"全带偏。
 */
class FocusController(
    private val context: Context,
    private val store: FocusStore,
    private val sessions: FocusSessionDao,
) {

    val runtime: StateFlow<FocusRuntime?> get() = store.runtime
    val config: StateFlow<FocusConfig> get() = store.config

    /** 当前剩余（暂停中返回冻结值）。给通知与页面读。 */
    fun currentRemainingMs(): Long {
        val snapshot = store.readRuntime() ?: return 0L
        return FocusEngine.remainingMs(snapshot, SystemClock.elapsedRealtime())
    }

    // ── 用户动作 ──

    /** 从空闲态起一轮：永远从**专注段**开始，轮次归 1。 */
    suspend fun startNew(linkedTaskId: Long? = null, linkedTaskTitle: String? = null) {
        val cfg = store.config.value
        val nowWall = System.currentTimeMillis()
        val started = FocusEngine.start(
            phase = FocusPhase.FOCUS,
            config = cfg,
            nowElapsed = SystemClock.elapsedRealtime(),
            nowWall = nowWall,
            roundIndex = 1,
            linkedTaskId = linkedTaskId,
            linkedTaskTitle = linkedTaskTitle,
        )
        apply(started)
    }

    /** 暂停 ↔ 继续。 */
    suspend fun togglePause() {
        val current = store.readRuntime() ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val updated = if (current.isPaused) {
            FocusEngine.resume(current, nowElapsed, System.currentTimeMillis())
        } else {
            FocusEngine.pause(current, nowElapsed)
        }
        apply(updated)
    }

    /**
     * 跳过当前段，直接进下一段。
     *
     * 不管 `autoStartNext` 怎么配：用户**明确按了跳过**，意思就是要往前走。
     * 跳过的段记 `completed = false`，所以它不会算进"今天几个番茄"。
     */
    suspend fun skip() {
        val current = store.readRuntime() ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        record(current, actualEndWallMs = System.currentTimeMillis(), completed = false)
        val next = FocusEngine.next(
            runtime = current,
            config = store.config.value,
            nowElapsed = nowElapsed,
            nowWall = System.currentTimeMillis(),
        )
        apply(next)
    }

    /** 放弃整轮，回到空闲态。当前段记 `completed = false`。 */
    suspend fun abandon() {
        val current = store.readRuntime() ?: return
        record(
            runtime = current,
            actualEndWallMs = System.currentTimeMillis(),
            completed = false,
            nowElapsed = SystemClock.elapsedRealtime(),
        )
        apply(null)
    }

    /** 改时长配置。**正在跑的段不受影响**（它的 `plannedMs` 在起钟时就定死了），下一段生效。 */
    fun updateConfig(newConfig: FocusConfig) {
        store.setConfig(newConfig)
    }

    // ── 系统事件 ──

    /**
     * 段结束闹钟响了。
     *
     * 系统**可能提前投递**（`setExactAndAllowWhileIdle` 允许小幅提前，不精确降级时更是常态），
     * 所以这里先判真实剩余：没到点就按原截止点重排一次，**绝不推进状态**
     *（否则每次提前唤醒都会白送一段休息，用户会看到轮次乱跳）。
     */
    suspend fun onAlarmFired() {
        val current = store.readRuntime()
        if (current == null) {
            // 快照没了（用户放弃 / 清空数据），这枚闹钟就是野的
            FocusAlarmScheduler.cancel(context)
            return
        }
        if (current.isPaused) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!FocusEngine.isExpired(current, nowElapsed)) {
            FocusAlarmScheduler.schedule(context, current.deadlineElapsedMs)
            return
        }
        finishExpiredPhase(current, nowElapsed)
    }

    /**
     * 开机恢复。
     *
     * ⚠️ 这是**唯一**该用 [FocusEngine.rebase] 的地方：单调时钟跨不过重启，
     * 快照里的 `deadlineElapsedMs` 此刻指的是"上一次开机以来"的时刻，直接比较会得出
     * 一个永远走不完的剩余。其余冷路径（进程被杀）单调钟依然有效，重锚反而更差。
     */
    suspend fun onBootRestore() {
        val current = store.readRuntime() ?: return
        val nowElapsed = SystemClock.elapsedRealtime()
        val rebased = FocusEngine.rebase(current, nowElapsed, System.currentTimeMillis())
        if (rebased.isPaused) {
            apply(rebased)
            return
        }
        if (FocusEngine.isExpired(rebased, nowElapsed)) {
            finishExpiredPhase(rebased, nowElapsed)
        } else {
            apply(rebased)
        }
    }

    /**
     * 页面侧的兜底：快照显示已经过点，但闹钟显然没把它推进。
     *
     * 什么情况会这样：用户「强制停止」过应用（系统会把闹钟一并清掉），
     * 之后重新打开。没有这一句，页面会停在一个 `00:00` 且永远不动的段上。
     */
    suspend fun selfHealIfExpired() {
        val current = store.readRuntime() ?: return
        if (current.isPaused) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!FocusEngine.isExpired(current, nowElapsed)) return
        finishExpiredPhase(current, nowElapsed)
    }

    /**
     * 确保前台服务活着（并顺带把通知摆回去）。
     *
     * 给 `MainActivity.onStart` 用：进程被系统回收之后再打开应用，
     * 快照还在但没有任何人在跑，得有人把它重新拉起来。
     */
    fun ensureRunning() {
        val current = store.readRuntime() ?: return
        if (!current.isPaused) FocusAlarmScheduler.schedule(context, current.deadlineElapsedMs)
        FocusTimerService.ensureStarted(context)
        FocusNotifier.notifyTimer(
            context,
            current,
            FocusEngine.remainingMs(current, SystemClock.elapsedRealtime()),
            current.linkedTaskTitle,
        )
    }

    /** 今天是几个番茄 / 累计多久，页面空态与统计头用它。 */
    suspend fun todayStat(): FocusDayStat {
        val today = LocalDate.now()
        val from = FocusStats.startOfDay(today, java.time.ZoneId.systemDefault())
        val list = runCatching { sessions.sessionsSince(from) }.getOrDefault(emptyList())
        return FocusStats.statOf(list, today)
    }

    /**
     * 从 [fromWallMs] 起的历史段变化流，给统计区（本周柱状图 / 当日分段图）用。
     *
     * 放在控制器上而不是让页面直接拿 DAO：历史段的**窗口口径**（本地日期、只数跑满的）
     * 全在 `FocusStats` 与 `FocusController.record` 里，页面不该自己拼查询条件。
     */
    fun observeSince(fromWallMs: Long): Flow<List<FocusSessionEntity>> = sessions.observeSince(fromWallMs)

    // ── 内部 ──

    /** 把"这一段到点了"的全部收尾做掉：落库 → 通知 → 自动进下一段。 */
    private suspend fun finishExpiredPhase(finished: FocusRuntime, nowElapsed: Long) {
        // 跑满的段，结束时刻就是它自己的截止点（不是"现在"）——
        // 手机关机一整晚再开机这条路径上，用"现在"会把昨天的番茄记到今天头上。
        record(finished, actualEndWallMs = finished.deadlineWallClockMs, completed = true)
        val cfg = store.config.value
        val next = FocusEngine.next(
            runtime = finished,
            config = cfg,
            nowElapsed = nowElapsed,
            nowWall = System.currentTimeMillis(),
        )
        announce(finished, next, cfg)
        if (cfg.autoStartNext) apply(next) else apply(null)
    }

    private suspend fun announce(finished: FocusRuntime, next: FocusRuntime, cfg: FocusConfig) {
        val linked = finished.linkedTaskTitle?.takeIf { it.isNotBlank() }
        if (finished.phase == FocusPhase.FOCUS) {
            val doneToday = runCatching { todayStat().completedPomodoros }.getOrDefault(0)
            val text = buildString {
                if (linked != null) append("「").append(linked).append("」")
                append("第 ").append(finished.roundIndex).append(" 个番茄完成")
                if (doneToday > 0) append("，今天共 ").append(doneToday).append(" 个")
                append("。接下来").append(next.phase.label).append(' ')
                append(next.plannedMs / MS_PER_MINUTE).append(" 分钟")
                if (!cfg.autoStartNext) append("（自动开始已关闭）")
            }
            FocusNotifier.notifyResult(context, "专注结束", text)
        } else {
            FocusNotifier.notifyResult(
                context,
                "休息结束",
                if (linked != null) "回到「$linked」，下一个番茄是第 ${next.roundIndex} 个"
                else "回到专注，下一个番茄是第 ${next.roundIndex} 个",
            )
        }
    }

    /**
     * 落一段进历史。
     *
     * @param actualEndWallMs 结束时刻的墙钟。跑满的段传**它自己的截止点**，被中断的传"现在"。
     */
    private suspend fun record(
        runtime: FocusRuntime,
        actualEndWallMs: Long,
        completed: Boolean,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ) {
        val actual = FocusEngine.elapsedMsFor(runtime, nowElapsed)
        if (actual < MIN_RECORDED_MS) return
        val started = runtime.startedAtWallMs
        runCatching {
            sessions.insert(
                FocusSessionEntity(
                    phase = runtime.phase.name,
                    roundIndex = runtime.roundIndex,
                    plannedMs = runtime.plannedMs,
                    actualMs = actual,
                    startedAtWallMs = started,
                    // 用户改过系统时间的话，"现在"可能早于"开始"；钳一下，
                    // 免得统计里出现负长度的段
                    endedAtWallMs = actualEndWallMs.coerceAtLeast(started),
                    completed = completed,
                    linkedTaskId = runtime.linkedTaskId,
                )
            )
        }
    }

    /**
     * 状态变更的三件套。`null` = 会话结束。
     *
     * ⚠️ **先写快照再动闹钟与服务**：反过来的话，写盘失败时闹钟已经排好，
     * 到点会去推进一个根本不存在的段。
     */
    private suspend fun apply(runtime: FocusRuntime?) {
        store.edit(runtime)
        if (runtime == null) {
            FocusAlarmScheduler.cancel(context)
            FocusTimerService.stop(context)
            FocusNotifier.cancelTimer(context)
            return
        }
        if (runtime.isPaused) {
            FocusAlarmScheduler.cancel(context)
        } else {
            FocusAlarmScheduler.schedule(context, runtime.deadlineElapsedMs)
        }
        FocusTimerService.ensureStarted(context)
        FocusNotifier.notifyTimer(
            context,
            runtime,
            FocusEngine.remainingMs(runtime, SystemClock.elapsedRealtime()),
            runtime.linkedTaskTitle,
        )
    }

    companion object {
        /** 短于这个长度的段不落库（见类注释的"落库口径"）。 */
        private const val MIN_RECORDED_MS = 60_000L

        private const val MS_PER_MINUTE = 60_000L

        fun of(context: Context): FocusController =
            (context.applicationContext as PocketXiaoliApplication).appContainer.focusController
    }
}
