package com.pocket.xiaoli.desktop.data

import com.pocket.xiaoli.core.calendar.BUILTIN_HOLIDAYS_2026
import com.pocket.xiaoli.core.calendar.HolidayDay
import com.pocket.xiaoli.core.calendar.parseHolidayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 节假日数据：**内置优先可用，在线可选更新**。
 *
 * - 内置：`core/calendar/BuiltinHolidays2026.kt`（国务院 2026 通知，编译期常量，断网也有）；
 * - 覆盖：`<数据目录>/holidays/2026.json`（holiday-cn 同格式），存在且能解析就用它；
 * - 更新：点「更新」或开启「自动更新」时拉 jsDelivr 镜像，解析成功才落盘（原子替换），失败保持旧数据。
 */
object HolidayStore {
    private const val YEAR = 2026
    private const val URL_TEMPLATE = "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val file: File get() = File(Store.dataDir, "holidays/$YEAR.json")

    /** 当前生效的数据。UI 层用 [version] 做 key 触发重组。 */
    var days: List<HolidayDay> = loadInitial()
        private set

    /** 最近一次落盘时间（0 = 只用内置）。 */
    var lastUpdated: Long = file.takeIf { it.exists() }?.lastModified() ?: 0L
        private set

    /** 每次数据变化 +1；Compose 里 `remember(version) { ... }` 或读它触发刷新。 */
    var version: Int = 0
        private set

    val hasNetworkCopy: Boolean get() = file.exists()

    val summary: String
        get() = "$YEAR · ${days.count { it.isOff }} 天放假 / ${days.count { !it.isOff }} 天调休" +
            if (hasNetworkCopy) " · 已更新" else " · 内置"

    private fun loadInitial(): List<HolidayDay> {
        val text = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()
        if (!text.isNullOrBlank()) {
            val parsed = runCatching { parseHolidayJson(text, expectYear = YEAR) }.getOrDefault(emptyList())
            if (parsed.size >= 10) return parsed
        }
        return BUILTIN_HOLIDAYS_2026
    }

    fun reload() {
        days = loadInitial()
        lastUpdated = file.takeIf { it.exists() }?.lastModified() ?: 0L
        version++
    }

    /** 在线更新。成功返回新增/覆盖的条数；失败返回异常，调用方负责提示。 */
    suspend fun updateFromNetwork(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(URL_TEMPLATE.format(YEAR)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                val parsed = parseHolidayJson(text, expectYear = YEAR)
                require(parsed.size >= 10) { "只解析到 ${parsed.size} 条，疑似格式变了" }
                val target = file
                target.parentFile?.mkdirs()
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeText(text)
                if (target.exists()) target.delete()
                check(tmp.renameTo(target)) { "落盘失败" }
                days = parsed
                lastUpdated = target.lastModified()
                version++
                parsed.size
            }
        }
    }
}
