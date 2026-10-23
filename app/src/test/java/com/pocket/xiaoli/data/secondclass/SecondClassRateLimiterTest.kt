package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SecondClassRateLimiter] 的契约。
 *
 * ⚠️ 这些用例**全程用真实时钟**，不注入任何假时钟 —— 理由写在
 * `SecondClassRateLimiter` 的 KDoc 与架构文档 §9.3：假时钟与 `delay` 的真实时间不同源时，
 * 用例必然时红时绿。这里把间隔调成 [INTERVAL_MS]（400ms）来跑快一点，
 * 断言一律用 `>=` 而不是精确值，避免机器负载造成偶发红。
 */
class SecondClassRateLimiterTest {

    private fun limiter() = SecondClassRateLimiter(minIntervalMs = INTERVAL_MS)

    private fun elapsedMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000L
    }

    @Test
    fun 首次请求不等待() {
        val limiter = limiter()

        val ms = elapsedMs { runBlocking { limiter.acquire() } }

        assertTrue("首次 acquire 不应等待，实测 ${ms}ms", ms < INTERVAL_MS)
    }

    @Test
    fun 连续两次请求之间强制间隔() {
        val limiter = limiter()

        val ms = elapsedMs {
            runBlocking {
                limiter.acquire()
                limiter.acquire()
            }
        }

        assertTrue("两次 acquire 至少要隔开 ${INTERVAL_MS}ms，实测 ${ms}ms", ms >= INTERVAL_MS)
    }

    /**
     * 守门人：闸门必须**横跨 delay**。
     *
     * 若哪天被改成「锁内算等待时长、解锁后再 delay」，三个并发调用会算出同一个等待时长然后一起发出去 ——
     * 总耗时掉到 1 个间隔，本用例就红。这是「看起来在限流、其实没有」最容易漏掉的一种写法。
     */
    @Test
    fun 并发调用被串行化() {
        val limiter = limiter()

        val ms = elapsedMs {
            runBlocking {
                coroutineScope {
                    List(3) { async { limiter.acquire() } }.awaitAll()
                }
            }
        }

        val expected = INTERVAL_MS * 2
        assertTrue("3 个并发 acquire 至少要隔开 ${expected}ms，实测 ${ms}ms", ms >= expected)
    }

    @Test
    fun 退避时长翻倍并封顶() {
        val limiter = limiter()

        assertEquals(2_000L, limiter.backoffMs(1))
        assertEquals(4_000L, limiter.backoffMs(2))
        assertEquals(8_000L, limiter.backoffMs(3))
        // 第 4 次起封顶，不再继续翻倍（避免 attempt 很大时位移溢出）。
        assertEquals(8_000L, limiter.backoffMs(4))
        assertEquals(8_000L, limiter.backoffMs(99))
    }

    @Test
    fun 退避曲线的边界取值被夹住() {
        val limiter = limiter()

        // attempt <= 1（含 0 与负数）一律给基数，不返回 0 —— 返回 0 等于「不等待就重发」。
        assertEquals(2_000L, limiter.backoffMs(1))
        assertEquals(2_000L, limiter.backoffMs(0))
        assertEquals(2_000L, limiter.backoffMs(-5))
    }

    @Test
    fun 等待可被取消而不是阻塞线程() {
        val limiter = limiter()
        lateinit var waiting: Job

        val ms = elapsedMs {
            runBlocking {
                // 占掉第一个名额，让下面那一发必须等满一个间隔。
                limiter.acquire()
                waiting = launch(Dispatchers.Default) { limiter.acquire() }
                delay(CANCEL_AFTER_MS)
                waiting.cancelAndJoin()
            }
        }

        assertTrue("取消后应立即返回（实测 ${ms}ms）", ms < INTERVAL_MS)
        assertTrue("等待中的协程应处于已取消状态", waiting.isCancelled)
    }

    private companion object {
        const val INTERVAL_MS = 400L
        const val CANCEL_AFTER_MS = 60L
    }
}
