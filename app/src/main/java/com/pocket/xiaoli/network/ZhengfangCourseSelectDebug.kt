package com.pocket.xiaoli.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 一次选课查询的原始往来（**仅内存**，供开发者实验室核对字段名）。
 *
 * ## 为什么需要它
 * 选课接口的字段名是按正方 `colModel` 的 `name` 写的，理论上是对的；但教务系统不同部署、
 * 二次开发、版本升级都会让**容器名或字段名漂移**。漂移的表现是最难查的一类故障：
 * **登录成功、页面不报错、列表就是空的** —— 没有异常、没有错误码，单测也覆盖不到（喂的是我自己造的 JSON）。
 *
 * 所以每次查询都把「请求体 + 响应原文」留一份，解析出 0 条时它就是唯一能定位原因的证据。
 * 这是用户明确要求的：*"解析失败时把原始响应存进 debug 面板，你截图/导出给我，我照着改第二版"*。
 *
 * ## 为什么只放内存、为什么只保留几条
 * 与 [NetworkLogStore] 同一条理由：响应里可能夹带学号等凭据。落盘等于把它写进设备存储，
 * 而排障收益只在"出事那一次"。保留条数少 + 单条截断，避免一份 100 KB+ 的响应把内存撑起来。
 * 进程重启即清空 —— 这是「临时看几眼」的工具，不是日志系统。
 */
object ZhengfangCourseSelectDebug {

    /** 最多保留几次查询（预选可选 / 预选已选 / 自主选课 各一次就够看了） */
    const val MAX_ENTRIES = 6

    /** 单条响应最多存多少字符（预选整表约 100 KB，留足余量但不上不封顶） */
    const val MAX_RESPONSE_CHARS = 200_000

    data class Entry(
        /** 人类可读的来源，如「课程预选-可选」 */
        val label: String,
        val requestUrl: String,
        val requestBody: String,
        val responseText: String,
        /** 本次解析出多少条 —— 0 才是要看原文的信号 */
        val parsedCount: Int,
        /** 服务端声明的总条数 */
        val totalResult: Int,
        val atMillis: Long,
    ) {
        /** 是否可疑：拿到了响应却一条都没解析出来 */
        val suspicious: Boolean get() = parsedCount == 0 && responseText.isNotBlank()
    }

    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    /** 最新的在最前面 */
    val entries: StateFlow<List<Entry>> = _entries

    fun record(
        label: String,
        requestUrl: String,
        requestBody: String,
        responseText: String,
        parsedCount: Int,
        totalResult: Int,
    ) {
        val entry = Entry(
            label = label,
            requestUrl = requestUrl,
            requestBody = requestBody,
            responseText = responseText.take(MAX_RESPONSE_CHARS),
            parsedCount = parsedCount,
            totalResult = totalResult,
            atMillis = System.currentTimeMillis(),
        )
        synchronized(lock) { _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES) }
    }

    fun clear() {
        synchronized(lock) { _entries.value = emptyList() }
    }

    /** 仅供单元测试：清空 */
    internal fun resetForTest() = clear()

    /** 学生身份字段名（这些键的值必须打码） */
    private val IDENTITY_FIELD = Regex(
        """("(?:xh_id|xh|xhxh|xsxh|xm|XM|xh_id)"\s*:\s*")([^"]*)(")""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 导出成可复制的文本。
     *
     * 只脱敏**身份字段的值**，**刻意保留字段名与课程内容** —— 排障要看的正是
     * 「教务到底把课程名放在哪个键上」，把结构也打掉就没用了。
     * 与 [NetworkLogStore.redactUrl] 同一取舍：脱敏值，不脱敏结构。
     */
    fun exportText(): String {
        val list = _entries.value
        if (list.isEmpty()) return ""
        return buildString {
            appendLine("── 选课原始响应（${list.size} 条，最新在前）──")
            appendLine("（学号 / 姓名等身份字段的值已打码，字段名与课程内容保留）")
            for (e in list) {
                appendLine()
                appendLine("【${e.label}】解析出 ${e.parsedCount} 条，服务端声明 ${e.totalResult} 条")
                appendLine("URL: ${e.requestUrl}")
                appendLine("请求体: ${e.requestBody}")
                appendLine("响应原文（截断至 ${MAX_RESPONSE_CHARS} 字符）:")
                appendLine(redactIdentityFields(e.responseText))
            }
        }
    }

    /** 把身份字段的值换成 `***`；结构与其他字段保持原样 */
    fun redactIdentityFields(text: String): String =
        IDENTITY_FIELD.replace(text) { m -> m.groupValues[1] + "***" + m.groupValues[3] }
}
