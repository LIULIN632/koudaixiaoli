package com.pocket.xiaoli.focus

/**
 * 番茄钟的**纯函数内核**。
 *
 * ## 为什么单独抽出来，且一个 `android.*` 的 import 都不许有
 *
 * 计时最容易错的地方不是 UI，是"剩余时间算得对不对"：暂停后会不会继续掉、锁屏久了会不会漂、
 * 跨重启会不会算成负数或永远走不完。这些全都能用 `nowElapsed` 一个入参覆盖住。
 * 一旦这里 `import android.os.SystemClock` 去拿"现在"，内核就只能在设备/模拟器上验，
 * 上面那些场景一条都测不了 —— 所以**现在几点的决定权一律交给调用方**
 * （`FocusEngineTest` 靠这个才能把时间推到任意位置）。
 *
 * ## ⭐ 唯一不变量
 *
 * **任何时刻的剩余都由 `deadline - now` 算出来，从不累加 tick。**
 *
 * 反过来（每秒 `remaining -= 1000`）会漂：主线程抽风少跑几拍就少算几秒，
 * 锁屏时 `Handler` 被压制就整段不动。绝对截止时间没有这个失效模式 ——
 * 中间漏掉多少次刷新都不影响结果。这也是照抄 `axisrow/pomodoro_android_app`（MIT）
 * 那条"绝对截止时间而非计数器"的落地。
 *
 * ## 两种时钟各管什么
 *
 * | 时钟 | 用在哪 | 为什么 |
 * |---|---|---|
 * | `elapsedRealtime`（单调） | **日常全部** | 抗用户改时间 / 跨时区 / 自动校时 |
 * | `currentTimeMillis`（墙钟） | **只有冷路径**重建基准（[rebase]） | 单调时钟**不跨重启**，重启后它归零 |
 *
 * `FocusRuntime` 两个截止时间都存，就是为这件事。
 */
enum class FocusPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK,
    ;

    /** 休息段（短休 + 长休）的一切判断都汇到这个属性，别再各写一遍 `== SHORT_BREAK || …`。 */
    val isBreak: Boolean get() = this != FOCUS
}

/**
 * 一个会话的时长配置。
 *
 * ⚠️ **每个字段都必须有默认值**：这个类会被 Gson 序列化进 SharedPreferences，
 * 漏一个字段就没有无参构造，Gson 改成 `UnsafeAllocator` 直接反射赋值，
 * **所有**默认值一起作废（`PrefsJsonDefaultsTest` 守这条，见 `memory/01-constraints.md` 2.7）。
 */
data class FocusConfig(
    val focusMinutes: Int = DEFAULT_FOCUS_MINUTES,
    val shortBreakMinutes: Int = DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = DEFAULT_LONG_BREAK_MINUTES,
    val roundsBeforeLongBreak: Int = DEFAULT_ROUNDS_BEFORE_LONG_BREAK,
    val autoStartNext: Boolean = true,
) {
    /**
     * 把越界值钳回可用范围。
     *
     * 配置来自用户输入，`SharedPreferences` 里还可能躺着上个版本写进去的值 ——
     * 0 分钟的段会让 [FocusEngine.plannedMsFor] 立刻过期（页面一闪一闪），
     * 负数更糟。钳在读取边界，而不是让内核到处判。
     */
    fun sanitized(): FocusConfig = FocusConfig(
        focusMinutes = focusMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES),
        shortBreakMinutes = shortBreakMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES),
        longBreakMinutes = longBreakMinutes.coerceIn(MIN_MINUTES, MAX_MINUTES),
        roundsBeforeLongBreak = roundsBeforeLongBreak.coerceIn(1, MAX_ROUNDS),
        autoStartNext = autoStartNext,
    )

    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_ROUNDS_BEFORE_LONG_BREAK = 4
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 180
        const val MAX_ROUNDS = 12
        val DEFAULT = FocusConfig()
    }
}

/**
 * **正在跑的那一段**。整个 App 里"当前番茄钟是什么状态"只有一个真源，
 * 就是它（由 `FocusStore` 持有）。
 *
 * ⚠️ 每个字段都要有默认值 —— 同上，Gson 的坑。
 *
 * @param roundIndex 本会话第几个**专注段**，从 1 起。休息段沿用起它来那一轮的序号，
 *   所以"第几轮"不会因为中间插了个短休而错位（圈点见 [FocusEngine.roundInCycle]）。
 * @param plannedMs 这一段计划跑多久。
 * @param deadlineElapsedMs **绝对**截止时刻，基于 `SystemClock.elapsedRealtime()`。
 * @param deadlineWallClockMs 同一个截止时刻的墙钟表示，**只为重启后重建基准**（见 [FocusEngine.rebase]）。
 * @param startedAtWallMs 这一段**开始那一刻**的墙钟。落库时要用它当 `startedAtWallMs`。
 *   刻意不是 `deadlineWallClockMs - plannedMs`：中间暂停过的话，继续时截止点被重设过
 *   （见 [FocusEngine.resume]），那个减法会把开始时间算到暂停之后去，统计图上这一段就跑偏了。
 * @param pausedRemainingMs 非 null = 暂停中，剩余**冻结**在这个值上；与两个 deadline 无关。
 * @param linkedTaskId 关联的待办；专注段正常跑完时回写它的番茄计数。
 * @param linkedTaskTitle 关联待办的名字，**快照里存一份**只为了让通知能直接写出"专注中 · 写高数"。
 *   刻意不在通知里回查数据库：通知的构建发生在 `startForeground` 的 5 秒窗口内，
 *   那里不适合挂一个 suspend 的 IO。代价是用户在计时过程中改了待办名字，
 *   通知上还是旧名 —— 一段最多 25 分钟，这个代价换掉一次异步竞态是值的。
 */
data class FocusRuntime(
    val phase: FocusPhase = FocusPhase.FOCUS,
    val roundIndex: Int = 1,
    val plannedMs: Long = 0L,
    val deadlineElapsedMs: Long = 0L,
    val deadlineWallClockMs: Long = 0L,
    val startedAtWallMs: Long = 0L,
    val pausedRemainingMs: Long? = null,
    val linkedTaskId: Long? = null,
    val linkedTaskTitle: String? = null,
) {
    /** 暂停中。三处以上要判它，所以给它一个名字而不是让各调用点写 `pausedRemainingMs != null`。 */
    val isPaused: Boolean get() = pausedRemainingMs != null
}

object FocusEngine {

    private const val MS_PER_MINUTE = 60_000L

    /** 某一段配置下的计划时长（毫秒）。 */
    fun plannedMsFor(phase: FocusPhase, config: FocusConfig): Long {
        val minutes = when (phase) {
            FocusPhase.FOCUS -> config.focusMinutes
            FocusPhase.SHORT_BREAK -> config.shortBreakMinutes
            FocusPhase.LONG_BREAK -> config.longBreakMinutes
        }
        return minutes.coerceAtLeast(FocusConfig.MIN_MINUTES).toLong() * MS_PER_MINUTE
    }

    /**
     * 起一段新钟。
     *
     * [nowElapsed] / [nowWall] 由调用方同时给出：两个截止时间必须来自**同一次取样**，
     * 分两次取会在两次取样的间隙里埋进一小段误差（这段误差正好是重启后算错的那个量）。
     */
    fun start(
        phase: FocusPhase,
        config: FocusConfig,
        nowElapsed: Long,
        nowWall: Long,
        roundIndex: Int = 1,
        linkedTaskId: Long? = null,
        linkedTaskTitle: String? = null,
    ): FocusRuntime {
        val planned = plannedMsFor(phase, config)
        return FocusRuntime(
            phase = phase,
            roundIndex = roundIndex.coerceAtLeast(1),
            plannedMs = planned,
            deadlineElapsedMs = nowElapsed + planned,
            deadlineWallClockMs = nowWall + planned,
            startedAtWallMs = nowWall,
            pausedRemainingMs = null,
            linkedTaskId = linkedTaskId,
            linkedTaskTitle = linkedTaskTitle,
        )
    }

    /**
     * 这一段的剩余毫秒。
     *
     * 暂停中直接返回冻结值 —— 暂停期间时间照样在走，只有这个分支能把"走掉的时间"抵掉。
     * 越界（已经过点）钳到 0，**不返回负数**：负数会让进度条反向画、格式化出 `-1:-30`。
     */
    fun remainingMs(runtime: FocusRuntime, nowElapsed: Long): Long {
        runtime.pausedRemainingMs?.let { return it.coerceAtLeast(0L) }
        return (runtime.deadlineElapsedMs - nowElapsed).coerceAtLeast(0L)
    }

    /** 这一段跑过的毫秒（落库的 `actualMs`）：暂停时长天然不计入。 */
    fun elapsedMsFor(runtime: FocusRuntime, nowElapsed: Long): Long =
        (runtime.plannedMs - remainingMs(runtime, nowElapsed)).coerceIn(0L, runtime.plannedMs)

    /** 进度 `0f..1f`。计划时长为 0 的脏快照返回 0 而不是除零。 */
    fun progress(runtime: FocusRuntime, nowElapsed: Long): Float {
        if (runtime.plannedMs <= 0L) return 0f
        val done = elapsedMsFor(runtime, nowElapsed)
        return (done.toDouble() / runtime.plannedMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 这一段是否**已经自然走完**。
     *
     * ⚠️ 暂停中**永远不算过期**，哪怕 `deadlineElapsedMs` 早已过去：
     * 暂停的含义就是"先停在这儿"，用户回来点继续才该往下走。
     */
    fun isExpired(runtime: FocusRuntime, nowElapsed: Long): Boolean =
        !runtime.isPaused && remainingMs(runtime, nowElapsed) == 0L

    /** 该轮在本组里的序号（圈点用），`1..roundsBeforeLongBreak`。 */
    fun roundInCycle(runtime: FocusRuntime, config: FocusConfig): Int {
        val n = config.roundsBeforeLongBreak.coerceAtLeast(1)
        return ((runtime.roundIndex - 1) % n + n) % n + 1
    }

    /**
     * 切到下一段。
     *
     * 规则：专注段结束 → 每跑完 [FocusConfig.roundsBeforeLongBreak] 个走长休，其余走短休；
     * 任何休息段结束 → 回到专注段，`roundIndex + 1`。
     *
     * 关联的待办**跨段沿用**：用户是对着这条任务起钟的，短休回来还在做同一件事。
     */
    fun next(
        runtime: FocusRuntime,
        config: FocusConfig,
        nowElapsed: Long,
        nowWall: Long,
    ): FocusRuntime {
        if (runtime.phase == FocusPhase.FOCUS) {
            val every = config.roundsBeforeLongBreak.coerceAtLeast(1)
            val phase = if (runtime.roundIndex % every == 0) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
            return start(
                phase, config, nowElapsed, nowWall, runtime.roundIndex,
                runtime.linkedTaskId, runtime.linkedTaskTitle,
            )
        }
        return start(
            FocusPhase.FOCUS,
            config,
            nowElapsed,
            nowWall,
            runtime.roundIndex + 1,
            runtime.linkedTaskId,
            runtime.linkedTaskTitle,
        )
    }

    /**
     * 暂停：把此刻的剩余**冻结**下来。
     *
     * 重复暂停是幂等的（返回原值）—— 用户连点两下暂停不该把剩余又算一遍。
     */
    fun pause(runtime: FocusRuntime, nowElapsed: Long): FocusRuntime =
        if (runtime.isPaused) runtime else runtime.copy(pausedRemainingMs = remainingMs(runtime, nowElapsed))

    /**
     * 继续：以冻结的剩余**重新设一个截止点**。
     *
     * 不是"把暂停那段时间加回 deadline" —— 那样一暂停两小时，deadline 就飘到两小时后，
     * 而中间任何一次快照丢失都会把这段时间算进专注时长里。重设截止点没有这个歧义。
     */
    fun resume(runtime: FocusRuntime, nowElapsed: Long, nowWall: Long): FocusRuntime {
        val left = runtime.pausedRemainingMs ?: return runtime
        return runtime.copy(
            deadlineElapsedMs = nowElapsed + left,
            deadlineWallClockMs = nowWall + left,
            pausedRemainingMs = null,
        )
    }

    /**
     * **冷路径专用**：把快照重新锚到本机当前的 `elapsedRealtime` 时基上。
     *
     * 什么时候需要：进程重建 / 开机之后。`elapsedRealtime()` 在开机时归零，
     * 快照里的 [FocusRuntime.deadlineElapsedMs] 于是彻底失去意义 ——
     * 设备上一轮跑了好几天的话，那个数比现在大得多，`deadline - now` 会算出一个
     * **永远走不完的剩余**（这是本设计里最危险的一个失效模式）。
     *
     * ## 为什么无脑用墙钟，不再判一次"到底重启了没"
     *
     * 走到这里的前提已经是"服务没在跑"（服务活着时绝不重锚）。这种冷路径上
     * **墙钟是唯一可能还对的钟**，单调钟已经被排除；再花力气区分"进程被杀"与"整机重启"
     * 只会多一条判据、多一个能错的分支，而两者用同一个修法都是对的。
     * 代价是：用户在这段窗口内**手动改了系统时间**，剩余会有偏差 ——
     * 一个 25 分钟的窗口里，这个代价可以接受。
     *
     * ⚠️ **调用点只应该是开机恢复**（`BootReceiver`）。进程被杀时单调钟依然是对的，
     * 那种情况重锚不会更准，只会把一次本可以抗住"用户改时间"的计时变成依赖墙钟。
     *
     * 暂停中的快照**原样返回**：剩余是冻结值，本来就跟时钟无关。
     */
    fun rebase(runtime: FocusRuntime, nowElapsed: Long, nowWall: Long): FocusRuntime {
        if (runtime.isPaused) return runtime
        val left = (runtime.deadlineWallClockMs - nowWall).coerceAtLeast(0L)
        return runtime.copy(
            deadlineElapsedMs = nowElapsed + left,
            deadlineWallClockMs = nowWall + left,
        )
    }
}
