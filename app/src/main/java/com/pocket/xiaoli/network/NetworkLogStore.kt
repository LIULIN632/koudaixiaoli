package com.pocket.xiaoli.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/** 一条网络请求记录 */
data class NetworkLogEntry(
    val id: Long,
    val startedAt: Long,
    val method: String,
    val url: String,
    /** HTTP 状态码；异常时为 null */
    val status: Int?,
    val durationMs: Long,
    /** 请求体字节数；未知为 -1 */
    val requestBytes: Long,
    /** 响应体字节数；未知为 -1 */
    val responseBytes: Long,
    val error: String? = null,
)

/**
 * 网络请求日志（**仅内存**，最多保留 [MAX_ENTRIES] 条）。
 *
 * ## 为什么只放内存
 * 请求 URL 里可能夹带 ticket / token 这类凭据。落盘等于把凭据写进设备存储，
 * 排障收益远不抵这个风险。所以进程重启即清空 —— 也符合「临时看几眼」的用途。
 *
 * ## 用途
 * 教务导入 / 服务跳转出问题时，用户可以在「开发者实验室 → 网络日志」里
 * 直接看到哪一步失败了、返回了什么码，比只描述现象有用得多。
 */
object NetworkLogStore {

    /** 最多保留条数；超出丢弃最旧的 */
    const val MAX_ENTRIES = 200

    private val counter = AtomicLong(0)
    private val lock = Any()

    private val _entries = MutableStateFlow<List<NetworkLogEntry>>(emptyList())

    /** 最新的在最前面 */
    val entries: StateFlow<List<NetworkLogEntry>> = _entries

    /** 记录一条；由拦截器调用，线程安全 */
    fun record(
        method: String,
        url: String,
        status: Int?,
        durationMs: Long,
        requestBytes: Long = -1L,
        responseBytes: Long = -1L,
        error: String? = null,
    ) {
        val entry = NetworkLogEntry(
            id = counter.incrementAndGet(),
            startedAt = System.currentTimeMillis(),
            method = method,
            url = url,
            status = status,
            durationMs = durationMs,
            requestBytes = requestBytes,
            responseBytes = responseBytes,
            error = error,
        )
        synchronized(lock) {
            _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        }
    }

    fun clear() {
        synchronized(lock) { _entries.value = emptyList() }
    }

    /** 仅供单元测试：清空并重置 id 计数 */
    internal fun resetForTest() {
        counter.set(0)
        clear()
    }

    /**
     * 生成可复制的 cURL 命令（URL 已脱敏）。
     *
     * 不包含 Header / 请求体：日志没有记录这些字段，不伪造。
     * 单引号按 shell 规则转义，复制到终端即可执行。
     */
    fun toCurl(entry: NetworkLogEntry): String {
        val method = entry.method.uppercase()
        val safeUrl = redactUrl(entry.url).replace("'", "'\\''")
        return if (method == "GET") "curl '$safeUrl'" else "curl -X $method '$safeUrl'"
    }

    /**
     * 给「导出日志 / 发给开发者」用的文本行。**URL 一律走 [redactUrl]**。
     *
     * 2026-10-23 审计发现的问题：导出的网络日志直接拼了 `entry.url`，而 `redactUrl`
     * 只用在下方的 cURL 里 —— 于是教务 CAS 的 `ticket=ST-…`、学习通的 `token=…`
     * 会跟着导出的 txt 一起离开设备（用户还会主动把它发给别人）。这与
     * 「导出时本机脱敏」的承诺不符。
     *
     * 现在把格式化收在这一个函数里：**任何新的导出入口都调它**，别再自己 `map { e.url }`。
     */
    fun exportLines(): List<String> = entries.value.map { e ->
        val status = e.status?.toString() ?: (e.error?.let { "ERR" } ?: "-")
        // 带上 error：屏幕上「开发者实验室 → 网络日志」本来就显示 `! 原因`，导出却把它丢了。
        // 于是"HTTP 200 但内容被我们拒了"这种请求在导出里和成功请求长得一模一样 ——
        // 2026-10-26 那份导出的节假日八连发就是这么被看成"全部成功"的。
        val reason = e.error?.let { "  ← $it" }.orEmpty()
        "  ${e.method} $status ${e.durationMs}ms  ${redactUrl(e.url)}$reason"
    }

    /** 需要脱敏的查询参数名（小写匹配） */
    private val SENSITIVE_PARAMS = setOf(
        "ticket", "token", "password", "passwd", "pwd", "code", "session", "cookie",
    )

    /**
     * 把 URL 里敏感查询参数的值替换为 `***`，其余原样保留。
     *
     * 刻意**不**整体打码 —— 排障要看的正是路径与参数名（如 `?service=...`），
     * 全删了日志就没用了。
     */
    fun redactUrl(url: String): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        val head = url.substring(0, q)
        val query = url.substring(q + 1)
        val cleaned = query.split('&').joinToString("&") { pair ->
            val eq = pair.indexOf('=')
            if (eq < 0) pair
            else {
                val name = pair.substring(0, eq)
                if (name.lowercase() in SENSITIVE_PARAMS) "$name=***" else pair
            }
        }
        return head + "?" + cleaned
    }
}
