package com.pocket.xiaoli.ui.focus

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.focus.FocusConfig
import com.pocket.xiaoli.focus.FocusController
import com.pocket.xiaoli.focus.FocusDayStat
import com.pocket.xiaoli.focus.FocusEngine
import com.pocket.xiaoli.focus.FocusRuntime
import com.pocket.xiaoli.focus.FocusStats
import com.pocket.xiaoli.focus.FocusStatsView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 专注页的 ViewModel。
 *
 * ## 它**不持有**计时状态
 *
 * 真源在 [FocusController] → `FocusStore` 的 SharedPreferences 快照里，
 * 页面只是它的一面镜子。这样"锁屏→进程被回收→从通知点回来"时，
 * 页面拿到的永远是盘上那个截止点，而不是"ViewModel 活得比进程久吗"这种运气问题。
 *
 * ## 「现在几点了」为什么要打拍子
 *
 * 剩余时间的唯一算法是 `deadline - now`（见 `FocusEngine` 的注释）——
 * 也就是说**它不需要谁来累加**，只需要有人定期把 `now` 读出来。
 * 所以这里的心跳只做一件事：把 `SystemClock.elapsedRealtime()` 推一个新值，触发重组。
 * 暂停与空闲时**不打拍子**（省电，也避免没意义的重组）。
 *
 * @param nowElapsed 单调钟，做成参数是为了单测能喂假时间。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    private val controller: FocusController,
    private val nowElapsed: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    val runtime: StateFlow<FocusRuntime?> = controller.runtime
    val config: StateFlow<FocusConfig> = controller.config

    /** 心跳值：单调钟的一次读数。只为触发重组，本身不是状态。 */
    private val tick = MutableStateFlow(nowElapsed())

    /** 当前剩余（毫秒）。空闲为 0。 */
    val remainingMs: StateFlow<Long> = combine(runtime, tick) { snapshot, now ->
        snapshot?.let { FocusEngine.remainingMs(it, now) } ?: 0L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 本段进度 0f..1f。 */
    val progress: StateFlow<Float> = combine(runtime, tick) { snapshot, now ->
        snapshot?.let { FocusEngine.progress(it, now) } ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    private val _today = MutableStateFlow(FocusDayStat(LocalDate.now(), 0, 0L))
    val today: StateFlow<FocusDayStat> = _today.asStateFlow()

    // 统计区用的历史段。**一个窗口一次查询**：热力图要 12 周，比柱状图的 7 天长，
    // 所以窗口按热力图取（`focusStatsView` 自己按日期切出 7 天 / 30 天 / 今天的那几份）。
    private val recentSessions = controller.observeSince(FocusStats.startOfRecentWeeks())

    /**
     * 统计区的全部数字（概览 + 热力图 + 柱状图 + 分段图），**一次派生**。
     *
     * 与「我的」页共用同一个 [FocusStats.focusStatsView]，两边数字不会打架。
     */
    val stats: StateFlow<FocusStatsView> = recentSessions
        .map { FocusStats.focusStatsView(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusStatsView())

    init {
        // 心跳：只在"有会话且没暂停"时打拍子。
        // 250ms 是刻意的折中 —— 秒级显示用 1000ms 会在进位那一刻看起来卡一下，
        // 再快就是白烧电。
        viewModelScope.launch {
            runtime.flatMapLatest { snapshot ->
                if (snapshot == null || snapshot.isPaused) {
                    flowOf(Unit)
                } else {
                    flow {
                        while (true) {
                            emit(Unit)
                            delay(TICK_MS)
                        }
                    }
                }
            }.collect { tick.value = nowElapsed() }
        }
        // 会话一变（起钟 / 换段 / 结束）就刷新今日数：落库发生在 controller 里，
        // 页面不做乐观累加，免得"跳过没落库"和"落库了"两种情况下数字不一致。
        viewModelScope.launch {
            runtime.collect {
                tick.value = nowElapsed()
                refreshToday()
            }
        }
        // 进页面时兜底：用户「强制停止」过应用的话，快照会停在一个已经过点的段上，
        // 没有这一下，页面会显示 00:00 且永远不动。
        viewModelScope.launch { controller.selfHealIfExpired() }
    }

    fun start(linkedTaskId: Long? = null, linkedTaskTitle: String? = null) {
        viewModelScope.launch { controller.startNew(linkedTaskId, linkedTaskTitle) }
    }

    fun togglePause() {
        viewModelScope.launch { controller.togglePause() }
    }

    fun skip() {
        viewModelScope.launch { controller.skip() }
    }

    fun abandon() {
        viewModelScope.launch { controller.abandon() }
    }

    /** 从后台回到页面时调一次：补齐心跳 + 兜底收尾。 */
    fun onResume() {
        tick.value = nowElapsed()
        viewModelScope.launch {
            controller.selfHealIfExpired()
            refreshToday()
        }
    }

    private suspend fun refreshToday() {
        _today.value = controller.todayStat()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FocusViewModel(container.focusController) as T
        }
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
