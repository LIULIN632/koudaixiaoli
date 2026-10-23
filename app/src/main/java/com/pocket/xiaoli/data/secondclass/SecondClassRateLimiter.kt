package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 到梦空间请求节流器 —— 硬边界 B8（翻页间隔 ≥ 2s + 失败退避）的**唯一执行点**。
 *
 * 为什么必须是一个共享实例，而不是「每个调用点各自 `delay(2000)`」：
 * 后者不是限流，是「每个调用点各自记得睡一下」——漏一处就违约，而且没有任何东西能发现漏了。
 * 所以本类的实例由 DataSource 侧共用一份，调用方没有绕过的入口。
 *
 * ### 不做重试决策
 * 按架构文档 §3.1 的划分，本类**只给延迟、不判断该不该重试**：
 * 「429/5xx 退避 2s→4s→8s、最多 3 次、仍失败则 `Failed`」里的**次数与终局**由 DataSource 决定。
 * [backoffMs] 是纯函数，就是为了让那层能独立测。
 *
 * ### ⚠️ 只有一个时间源，且刻意不做可注入时钟
 * 等待时长由**真实过去的时间**（[System.nanoTime] 单调时钟）算出，再用**可取消的** [delay] 等待。
 * 不要为了好测就加一个 `now: () -> Long` 参数：注入后测试里的假时钟与 `delay` 走的真实时间
 * **不同源**，用例会时红时绿 —— 这是写 `SecondClassCookieJar` 时真踩过的坑，
 * 记在 `docs/superpowers/specs/2026-10-05-secondclass-architecture-design.md` §9.3。
 * 想把用例跑快，就调小 [minIntervalMs]（构造参数），**不要动时间源**。
 *
 * @param minIntervalMs 两次请求之间的最小间隔。生产值 [DEFAULT_MIN_INTERVAL_MS] = 2s。
 * @param backoffBaseMs 退避基数（第 1 次退避的时长）。
 * @param maxBackoffMs 退避上限，也是「再往后不再更长」的封顶值。
 */
class SecondClassRateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val backoffBaseMs: Long = DEFAULT_BACKOFF_BASE_MS,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
) {

    /**
     * 所有调用共用的闸门。
     *
     * ⚠️ 它**故意横跨 [delay]**：持锁等待才能把并发调用真正串行化。
     * 若改成「锁内算出该等多久、解锁后再 delay」，两个并发调用会算出同一个等待时长然后一起发出去 ——
     * 那就等于没有限流。
     */
    private val gate = Mutex()

    /** 上次真正发请求的单调时刻。用 [hasRequested] 而不是哨兵值表示「还没发过」。 */
    private var lastRequestAtMs: Long = 0L
    private var hasRequested: Boolean = false

    /** 等到距离上一次请求满 [minIntervalMs]。首次调用不等待。 */
    suspend fun acquire() {
        gate.withLock {
            if (hasRequested) {
                val waited = nowMs() - lastRequestAtMs
                val remaining = minIntervalMs - waited
                if (remaining > 0) delay(remaining)
            }
            lastRequestAtMs = nowMs()
            hasRequested = true
        }
    }

    /**
     * 第 [attempt] 次退避应等多久（attempt 从 1 开始）。
     *
     * 默认配置下是 2s → 4s → 8s，第 4 次起仍是 8s（封顶）。
     * 纯函数：用例不需要真的睡 14 秒就能验完整条曲线。
     */
    fun backoffMs(attempt: Int): Long {
        if (attempt <= 1) return backoffBaseMs
        // 先夹住移位数，避免 attempt 很大时位移溢出成负数。
        val raw = backoffBaseMs shl (attempt - 1).coerceAtMost(MAX_SHIFT)
        return if (raw <= 0 || raw > maxBackoffMs) maxBackoffMs else raw
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    companion object {
        /** 架构文档 §4.2：连续翻页两次请求间**强制** ≥ 2000 ms。 */
        const val DEFAULT_MIN_INTERVAL_MS = 2_000L
        const val DEFAULT_BACKOFF_BASE_MS = 2_000L
        const val DEFAULT_MAX_BACKOFF_MS = 8_000L

        /** 429/5xx 最多重试 3 次后返回 `Failed`（由 DataSource 侧执行）。 */
        const val MAX_ATTEMPTS = 3

        private const val MAX_SHIFT = 16
    }
}
