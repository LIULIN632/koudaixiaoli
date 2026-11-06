package com.pocket.xiaoli.desktop.data

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 单实例守卫。
 *
 * 全部用系统分配的临时端口（0 → 再从 boundPort 拿实际值），绝不碰真实例的 41827。
 */
class InstanceGuardTest {

    // 陌生服务端在自己的线程里登记已接受的连接，所以列表必须线程安全
    private val open = java.util.Collections.synchronizedList(mutableListOf<AutoCloseable>())

    @AfterTest
    fun tearDown() {
        open.toList().forEach { runCatching { it.close() } }
        open.clear()
    }

    @Test
    fun `空端口时自己是主实例`() {
        val fired = AtomicInteger()
        val first = InstanceGuard(0, timeoutMillis = 300)
        open.add(AutoCloseable { first.release() })
        assertEquals(InstanceGuard.Outcome.Primary, first.acquire { fired.incrementAndGet() })
        assertTrue(first.boundPort > 0, "应当占到一个真实端口")
    }

    @Test
    fun `第二个实例把激活请求交给第一个然后自己退场`() {
        val fired = AtomicInteger()
        val first = InstanceGuard(0, timeoutMillis = 300)
        open.add(AutoCloseable { first.release() })
        first.acquire { fired.incrementAndGet() }

        val second = InstanceGuard(first.boundPort, timeoutMillis = 300)
        assertEquals(InstanceGuard.Outcome.HandedOff, second.acquire {})
        // 没抢到端口就不该占着
        assertEquals(-1, second.boundPort)

        repeat(20) { if (fired.get() == 0) Thread.sleep(50) }
        assertEquals(1, fired.get(), "已有实例应当收到一次激活请求")
    }

    @Test
    fun `连发两次请求就唤起两次`() {
        val fired = AtomicInteger()
        val first = InstanceGuard(0, timeoutMillis = 300)
        open.add(AutoCloseable { first.release() })
        first.acquire { fired.incrementAndGet() }

        repeat(2) { InstanceGuard(first.boundPort, timeoutMillis = 300).acquire {} }
        repeat(20) { if (fired.get() < 2) Thread.sleep(50) }
        assertEquals(2, fired.get())
    }

    @Test
    fun `端口被不相干的服务占着时不误判成已有实例`() {
        // 对面秒回一句无关话：握手对不上，只能当陌生人，照常启动
        val stranger = ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress())
        open.add(stranger)
        Thread {
            while (!stranger.isClosed) {
                val socket = runCatching { stranger.accept() }.getOrNull() ?: break
                runCatching {
                    socket.getOutputStream().write("HELLO\n".toByteArray())
                    socket.getOutputStream().flush()
                    socket.close()
                }
            }
        }.apply { isDaemon = true }.start()

        val guard = InstanceGuard(stranger.localPort, timeoutMillis = 300)
        open.add(AutoCloseable { guard.release() })
        assertEquals(InstanceGuard.Outcome.PortTakenByStranger, guard.acquire {})
        assertFalse(guard.boundPort > 0, "陌生人占着端口时不该以为自己占了")
    }

    @Test
    fun `对面连上但不回话也算陌生人而不是已有实例`() {
        val silent = ServerSocket(0, 4, java.net.InetAddress.getLoopbackAddress())
        open.add(silent)
        Thread {
            while (!silent.isClosed) {
                val socket = runCatching { silent.accept() }.getOrNull() ?: break
                // 只接不回：靠 soTimeout 结束，不能让守卫一直等
                open.add(AutoCloseable { runCatching { socket.close() } })
            }
        }.apply { isDaemon = true }.start()

        val guard = InstanceGuard(silent.localPort, timeoutMillis = 200)
        open.add(AutoCloseable { guard.release() })
        assertEquals(InstanceGuard.Outcome.PortTakenByStranger, guard.acquire {})
    }

    @Test
    fun `释放端口后下一个实例能接手`() {
        val first = InstanceGuard(0, timeoutMillis = 300)
        val port = first.acquire {}.let { first.boundPort }
        first.release()

        val second = InstanceGuard(port, timeoutMillis = 300)
        open.add(AutoCloseable { second.release() })
        assertEquals(InstanceGuard.Outcome.Primary, second.acquire {})
    }
}
