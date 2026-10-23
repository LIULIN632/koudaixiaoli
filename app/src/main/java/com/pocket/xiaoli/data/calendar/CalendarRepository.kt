package com.pocket.xiaoli.data.calendar

import android.content.Context
import com.google.gson.Gson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 校历资产的读取与解析。
 *
 * 资产是**随包**的（`assets/calendar/ytlg.json`），与 `assets/knowledge.json` 同一做法：
 * 不联网、不落盘缓存。读一次放在进程里就够 —— 而且**冷启动时不做任何 IO**，
 * 因为仓库层用 `by lazy` 挂它，只有真的打开校历页、或气泡来要信号时才会去读。
 *
 * 读不出来就返回 null，交由页面显示空态：校历缺失只该是「这个功能空着」，
 * 不该让 App 起不来，也不该让气泡抛异常。
 */
class CalendarRepository(private val context: Context) {

    @Volatile
    private var cached: CalendarAsset? = null

    @Volatile
    private var loaded: Boolean = false

    /** 读一次、解析一次，之后走内存。解析失败返回 null（不抛）。 */
    fun asset(): CalendarAsset? {
        if (loaded) return cached
        synchronized(this) {
            if (loaded) return cached
            val raw = runCatching {
                context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            }.getOrNull()
            cached = parseCalendarAsset(raw)
            loaded = true
            return cached
        }
    }

    companion object {
        /** 与 `assets/knowledge.json` 同级同做法；换学年就是换掉这个文件的内容。 */
        const val ASSET_NAME: String = "calendar/ytlg.json"
    }
}

private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/*
 * 下面三个 DTO 的字段**全部可空**，这是刻意的。
 *
 * Gson 用 Unsafe 绕过构造器分配对象，Kotlin 的「非空类型 + 默认值」在它眼里都不存在：
 * 字段缺失时会把 null 塞进一个声明为非空的 String，读取时才炸（而且栈里看不出是解析问题）。
 * 声明成可空、在映射函数里显式兜住，错误就变成「这条事件被丢掉」而不是「整页崩掉」。
 */

private data class AssetJson(
    val schema: Int? = null,
    val academicYear: String? = null,
    val source: String? = null,
    val terms: List<TermJson>? = null,
    val events: List<EventJson>? = null,
)

private data class TermJson(
    val name: String? = null,
    val start: String? = null,
    val end: String? = null,
    val teachingWeeks: Int? = null,
    val totalWeeks: Int? = null,
)

private data class EventJson(
    val title: String? = null,
    val start: String? = null,
    val end: String? = null,
    val kind: String? = null,
    val short: String? = null,
)

/**
 * 把资产原文解析成 [CalendarAsset]，任何一步不成立就返回 null。
 *
 * 单独抽成 internal 纯函数（不碰 `Context`）是为了让资产校验单测**走真实解析路径**：
 * 测试读真文件 → 调这个函数 → 断言。只校验 JSON 字面量的话，
 * DTO 字段名与 Gson 对不上这类错误测试是看不见的。
 */
internal fun parseCalendarAsset(raw: String?): CalendarAsset? {
    if (raw.isNullOrBlank()) return null
    val json = runCatching { Gson().fromJson(raw, AssetJson::class.java) }.getOrNull() ?: return null
    val terms = json.terms.orEmpty().mapNotNull { it.toTerm() }
    val events = json.events.orEmpty().mapNotNull { it.toEvent() }
    if (terms.isEmpty() || events.isEmpty()) return null
    return CalendarAsset(
        schema = json.schema ?: CalendarAsset.SCHEMA_VERSION,
        academicYear = json.academicYear.orEmpty(),
        source = json.source.orEmpty(),
        terms = terms.sortedBy { it.start },
        events = events.sortedBy { it.start },
    )
}

private fun TermJson.toTerm(): CalendarTerm? {
    val from = parseIsoDate(start) ?: return null
    val to = parseIsoDate(end) ?: return null
    return CalendarTerm(
        name = name.orEmpty(),
        start = from,
        end = to,
        teachingWeeks = teachingWeeks ?: 0,
        totalWeeks = totalWeeks ?: 0,
    )
}

private fun EventJson.toEvent(): CalendarEvent? {
    val from = parseIsoDate(start) ?: return null
    val to = parseIsoDate(end) ?: return null
    // 区间反了的事件直接丢掉：留着会让 covers() 永远为假，无声无息地少一条。
    if (to.isBefore(from)) return null
    return CalendarEvent(
        title = title.orEmpty(),
        start = from,
        end = to,
        kind = EventKind.from(kind.orEmpty()),
        short = short.orEmpty(),
    )
}

private fun parseIsoDate(value: String?): LocalDate? =
    runCatching { LocalDate.parse(value.orEmpty().trim(), ISO) }.getOrNull()
