package com.pocket.xiaoli.data.contribution

import com.pocket.xiaoli.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 校园共建榜的唯一出口。
 *
 * ## 隐私边界（改这块之前先读）
 *
 * - **默认关**：没点「加入」时 [record] 第一行就返回，不产生 cid、不写队列、不发请求。
 * - 只上传「昵称 + 动作名 + eid」；课表、教室号、笔记正文**一个字都不传**。
 * - F-Droid 构建（`-Pfdroid=true`）整个功能关闭：`configured == false`，
 *   设置入口也不出现 —— 与匿名统计同一条纪律。
 * - 退出榜单会先停止本机一切上报（断网也能立刻生效），再由 [flushLocked]
 *   补发 `POST /v1/contribution/leave` 删除服务端记录。
 *
 * ## 为什么上报是"动作"而不是"分数"
 *
 * 分值在服务端查表。客户端只说要记什么，不参与算分 —— 否则改包就能刷榜。
 */
class ContributionRepository(
    private val store: ContributionStore,
    private val client: ContributionClient,
    private val fdroid: Boolean = BuildConfig.FDROID,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 队列与「退出」共用一把锁：任何时刻只有一个 flush 在动账本。 */
    private val gate = Mutex()

    private val cached = store.cachedBoard()

    private val _state = MutableStateFlow(
        ContributionUiState(
            enabled = !fdroid && store.enabled(),
            nickname = store.nickname(),
            configured = !fdroid && client.configured,
            category = cached?.category ?: ContributionCategory.ALL,
            period = cached?.period ?: ContributionPeriod.WEEK,
            board = cached,
        ),
    )

    val state: StateFlow<ContributionUiState> = _state.asStateFlow()

    // ── 界面动作 ────────────────────────────────────────────────────────

    fun selectCategory(category: ContributionCategory) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category, board = null, error = null) }
        refresh()
    }

    fun selectPeriod(period: ContributionPeriod) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, board = null, error = null) }
        refresh()
    }

    /** 拉榜单。分类 / 周期在中途被改掉时，旧响应直接丢弃，免得把错的数据贴上屏。 */
    fun refresh() {
        if (!_state.value.configured) return
        val category = _state.value.category
        val period = _state.value.period
        _state.update { it.copy(loading = true, error = null) }

        scope.launch {
            val cid = if (_state.value.enabled) store.contributorIdOrNull() else null
            val board = client.leaderboard(category, period, cid)
            if (board == null) {
                _state.update { current ->
                    if (current.category != category || current.period != period) current
                    else current.copy(loading = false, error = "网络不通，稍后再试")
                }
                return@launch
            }
            store.cacheBoard(board)
            _state.update { current ->
                if (current.category != category || current.period != period) current
                else current.copy(loading = false, board = board, error = null)
            }
        }
    }

    /** 加入榜单。成功返回 null，失败返回一句给 Snackbar / 对话框看的话。 */
    fun join(rawNickname: String): String? {
        if (!_state.value.configured) return "这个版本没有连接榜单服务器"
        val nickname = ContributionNickname.normalize(rawNickname)
            ?: return ContributionNickname.errorText(rawNickname)
        store.setNickname(nickname)
        store.setEnabled(true)
        store.contributorId()
        _state.update { it.copy(enabled = true, nickname = nickname, error = null) }
        refresh()
        return null
    }

    fun rename(rawNickname: String): String? {
        if (!_state.value.enabled) return "先加入榜单"
        val nickname = ContributionNickname.normalize(rawNickname)
            ?: return ContributionNickname.errorText(rawNickname)
        store.setNickname(nickname)
        _state.update { it.copy(nickname = nickname) }
        val cid = store.contributorIdOrNull() ?: return null
        scope.launch {
            client.rename(cid, nickname)
            refresh()
        }
        return null
    }

    /**
     * 退出榜单：本机立刻停止上报并清掉个人痕迹；服务端的删除请求排队补发。
     * 断网时用户也能"退得掉"，不会出现"点了退出但还在偷偷上报"。
     */
    fun leave() {
        store.contributorIdOrNull()?.let { store.setPendingLeave(it) }
        store.clearPersonal()
        _state.update {
            it.copy(
                enabled = false,
                nickname = "",
                board = null,
                loading = false,
                error = null,
            )
        }
        scope.launch {
            gate.withLock { flushLocked() }
            // 退出后仍能看公开榜单：重新拉一次（不带 cid），别让页面停在「还没拉到榜单」
            refresh()
        }
    }

    /**
     * 记一次贡献。完全异步、永不抛出 —— 榜单坏了不许影响导入 / 记笔记这些主功能。
     */
    fun record(action: ContributionAction) {
        if (fdroid || !client.configured || !store.enabled()) return
        scope.launch {
            gate.withLock {
                if (store.contributorIdOrNull() == null) return@withLock
                if (!store.takeDailySlot(action, now())) return@withLock
                store.enqueue(PendingReport(action, UUID.randomUUID().toString()))
                flushLocked()
            }
        }
    }

    /** 网络恢复后补发（界面下拉刷新时也可以调）。 */
    fun flush() {
        if (fdroid || !client.configured) return
        scope.launch { gate.withLock { flushLocked() } }
    }

    // ── 内部 ────────────────────────────────────────────────────────────

    private fun flushLocked() {
        // 先处理退出：即使已经查不到 cid（clearPersonal 清掉了），也要把旧记录删掉。
        store.pendingLeave()?.let { cid ->
            if (client.leave(cid)) store.clearPendingLeave()
        }

        if (!store.enabled()) return
        val cid = store.contributorIdOrNull() ?: return
        val nickname = store.nickname().takeIf { it.isNotBlank() } ?: return

        var remaining = store.pending()
        var sent = 0
        while (remaining.isNotEmpty() && sent < MAX_SEND_PER_FLUSH) {
            val head = remaining.first()
            when (client.report(cid, nickname, head.action, head.eid)) {
                ContributionClient.Delivery.ACCEPTED,
                ContributionClient.Delivery.DROP,
                -> {
                    remaining = remaining.drop(1)
                    sent++
                }
                ContributionClient.Delivery.RETRY -> {
                    store.writeQueue(remaining)
                    return
                }
            }
        }
        store.writeQueue(remaining)
    }

    private companion object {
        /** 一次最多发 20 条，剩下的留给下次 —— 别让一个补发循环把流量吃满。 */
        const val MAX_SEND_PER_FLUSH = 20
    }
}
