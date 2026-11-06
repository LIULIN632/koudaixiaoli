package com.pocket.xiaoli.desktop.data

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 单实例守卫：第二次启动不再开第二个窗口，而是把已有窗口叫到前面来。
 *
 * ## 为什么必须有
 *
 * 数据是**整文件**写盘的（见 [Store.persist]）：每个进程启动时把 JSON 读进内存，之后每次
 * 改动都把整份内存写回去。两个进程同时开着，后写的那个会静默覆盖前一个的全部改动 —— 
 * 而「开机自启」打开后，开机一个 + 手动双击图标一个正好会撞上，用户看到的就是一觉醒来课表
 * 或待办少了半截，且没有任何报错。
 *
 * ## 为什么用回环 TCP 而不是文件锁
 *
 * 文件锁只能判「有没有别人」，没法告诉那个人「请你把自己弹出来」。一个端口同时做这两件事。
 *
 * ## 端口被别的服务占了怎么办
 *
 * 握手要比对 [MAGIC]：对不上就认定是陌生人，放弃守卫照常启动，绝不误判成「已有实例」而
 * 让用户以为程序打不开。只监听 127.0.0.1，不对外。
 *
 * 端口可用 `-Dpocket.instance.port=` 覆盖，测试用 0（系统分配）来避免碰真实例。
 */
class InstanceGuard(
    private val requestedPort: Int = System.getProperty("pocket.instance.port", DEFAULT_PORT.toString()).toInt(),
    private val magic: String = MAGIC,
    private val timeoutMillis: Int = 400,
) {
    enum class Outcome {
        /** 本机没有实例，已占住端口，正常启动。 */
        Primary,

        /** 已有实例并收到激活请求，当前进程应当退出。 */
        HandedOff,

        /** 端口被非本程序的服务占着，守卫失效但照常启动。 */
        PortTakenByStranger,
    }

    private var listener: ServerSocket? = null
    private var worker: Thread? = null

    /** 实际占用的端口；未启动或系统分配时才有意义，主要给测试看。 */
    val boundPort: Int get() = listener?.localPort ?: -1

    /** 请求监听的端口（回环），用于日志与排障。 */
    val port: Int get() = requestedPort

    /**
     * 抢占单实例。已有实例时返回 [Outcome.HandedOff]（并已把激活请求发过去），
     * 否则占住端口并开始在回环上接受请求。
     */
    fun acquire(onActivate: () -> Unit): Outcome {
        when (requestActivation()) {
            Probe.Already -> return Outcome.HandedOff
            Probe.Stranger -> return Outcome.PortTakenByStranger
            Probe.Free -> Unit
        }
        val bound = runCatching { bind() }
            .getOrElse {
                // 探测与绑定之间可能刚好有第二个实例抢先：再探一次，按结果定性。
                return if (requestActivation() == Probe.Already) Outcome.HandedOff else Outcome.PortTakenByStranger
            }
        listener = bound
        worker = Thread({ serve(bound, onActivate) }, "pocket-instance-guard").apply {
            isDaemon = true
            start()
        }
        return Outcome.Primary
    }

    /** 释放端口（进程退出其实会自己放掉，这里给测试用）。 */
    fun release() {
        runCatching { listener?.close() }
        listener = null
    }

    private fun bind(): ServerSocket =
        ServerSocket(requestedPort, 8, InetAddress.getLoopbackAddress())

    private enum class Probe { Already, Stranger, Free }

    /** 连一次回环端口：连不上=Free，握手对上=Already，对不上=Stranger。 */
    private fun requestActivation(): Probe {
        if (requestedPort <= 0) return Probe.Free // 测试用系统分配端口，不去撞别人
        val socket = try {
            Socket().apply {
                connect(
                    java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort),
                    timeoutMillis,
                )
            }
        } catch (e: IOException) {
            return Probe.Free
        }
        return socket.use { talk(it, "$ACTIVATE$magic") }
    }

    /** 发一行、读一行；只在拿到预期应答时认为对面是本程序。 */
    private fun talk(socket: Socket, line: String): Probe {
        runCatching {
            socket.soTimeout = timeoutMillis
            socket.getOutputStream().apply {
                write((line + "\n").toByteArray(Charsets.UTF_8))
                flush()
            }
        }.onFailure { return Probe.Stranger }
        // 按行读而不是 readBytes()：readBytes 要等 EOF，而对面的实例读完请求才回话，
        // 双方都等对方先关管道就锁死了。
        val reader = runCatching { socket.getInputStream().reader(Charsets.UTF_8).buffered() }.getOrNull()
        val reply = runCatching { reader?.readLine() }.getOrNull()
        return if (reply == "$OK$magic") Probe.Already else Probe.Stranger
    }

    private fun serve(server: ServerSocket, onActivate: () -> Unit) {
        while (!server.isClosed) {
            val connection = runCatching { server.accept() }.getOrNull() ?: return
            runCatching { replyAndActivate(connection, onActivate) }
        }
    }

    private fun replyAndActivate(connection: Socket, onActivate: () -> Unit) {
        connection.soTimeout = timeoutMillis
        val request = runCatching {
            connection.getInputStream().reader(Charsets.UTF_8).buffered().readLine()
        }.getOrNull()
        val accepted = request != null && request.startsWith(ACTIVATE) && request.removePrefix(ACTIVATE) == magic
        runCatching {
            connection.getOutputStream().apply {
                write(((if (accepted) OK else NO) + magic + "\n").toByteArray(Charsets.UTF_8))
                flush()
            }
            connection.close()
        }
        if (accepted) onActivate()
    }

    private companion object {
        const val DEFAULT_PORT = 41827
        const val MAGIC = "pocket-xiaoli-desktop/1"
        const val ACTIVATE = "ACTIVATE "
        const val OK = "OK "
        const val NO = "NO "
    }
}
