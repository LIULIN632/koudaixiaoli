package com.pocket.xiaoli.desktop.data

import com.google.gson.JsonParser
import java.time.LocalDate

/** 知识库文档（字段与 `app/src/main/assets/knowledge.json` 的协议一致）。 */
data class KnowledgeDoc(
    val id: String,
    val title: String,
    val category: String,
    val campus: String,
    val expiryPolicy: String,
    val owner: String,
    val sourceLabel: String,
    val effectiveFrom: String,
    val effectiveTo: String,
    val updatedAt: Long,
    val tags: List<String>,
    val body: String,
) {
    /**
     * 文档已过期（`effective_to` 早于今天）。
     *
     * 判定规则在共享核心 `com.pocket.xiaoli.core.knowledge.expiryDecision`，与 Android 端同一份：
     * 最后有效日当天仍然有效、`9999-12-31` 是「永不过期」哨兵、日期写坏了按无期限处理（不抛异常）。
     */
    fun isExpired(today: LocalDate = LocalDate.now()): Boolean =
        com.pocket.xiaoli.core.knowledge.expiryDecision(
            effectiveToIso = effectiveTo,
            hardExpiry = false,
            todayEpochDay = today.toEpochDay(),
        ) is com.pocket.xiaoli.core.knowledge.ExpiryDecision.IncludeExpired
}

/** 校历事件。 */
data class CalendarEvent(
    val title: String,
    val start: String,
    val end: String,
    val kind: String,
    val short: String,
)

/** 校历学期段。 */
data class CalendarTermSpan(
    val name: String,
    val start: String,
    val teachingWeeks: Int,
    val totalWeeks: Int,
    val end: String,
)

data class CalendarFile(
    val academicYear: String,
    val source: String,
    val terms: List<CalendarTermSpan>,
    val events: List<CalendarEvent>,
)

/** 快问快答的一道题。 */
data class QaQuestion(
    val id: String,
    val question: String,
    val answerPoints: List<String>,
    val hint: String,
    val tags: List<String>,
)

data class QaChapter(val index: Int, val title: String, val questions: List<QaQuestion>)

data class QaCourse(
    val id: String,
    val subject: String,
    val name: String,
    val teacher: String,
    val chapters: List<QaChapter>,
)

/**
 * 读取移动端 `app/src/main/assets` 下的 JSON 资产。
 *
 * ⚠️ 用 `JsonParser` 手工取字段而不是 Gson 反射：这些文件是**移动端的既有协议**，
 * 而 Gson 的反射反序列化在字段缺失/类型漂移时会直接抛异常（例如 `updated_at` 将来
 * 从毫秒数改成 ISO 字符串）。手工解析对协议变更有韧性，也不会在桌面端复制一份模型语义。
 */
object Assets {

    private fun textOf(name: String): String? =
        Assets::class.java.getResourceAsStream("/assets/$name")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }

    private fun JsonObject0.str(key: String): String = this.get(key)?.takeIf { !it.isJsonNull }?.asString ?: ""
    private fun JsonObject0.long(key: String): Long = this.get(key)?.takeIf { !it.isJsonNull }?.asLong ?: 0L
    private fun JsonObject0.int(key: String): Int = this.get(key)?.takeIf { !it.isJsonNull }?.asInt ?: 0
    private fun JsonObject0.stringList(key: String): List<String> =
        this.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString } ?: emptyList()

    typealias JsonObject0 = com.google.gson.JsonObject

    val knowledge: List<KnowledgeDoc> by lazy {
        val text = textOf("knowledge.json") ?: return@lazy emptyList()
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return@lazy emptyList()
        val docs = root.get("docs")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@lazy emptyList()
        docs.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            KnowledgeDoc(
                id = obj.str("id"),
                title = obj.str("title"),
                category = obj.str("category"),
                campus = obj.str("campus"),
                expiryPolicy = obj.str("expiry_policy"),
                owner = obj.str("owner"),
                sourceLabel = obj.str("source_label"),
                effectiveFrom = obj.str("effective_from"),
                effectiveTo = obj.str("effective_to"),
                updatedAt = obj.long("updated_at"),
                tags = obj.stringList("tags"),
                body = obj.str("body"),
            )
        }.filter { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    val calendar: CalendarFile? by lazy {
        val text = textOf("calendar/ytlg.json") ?: return@lazy null
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return@lazy null
        val terms = root.get("terms")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            CalendarTermSpan(
                name = obj.str("name"),
                start = obj.str("start"),
                teachingWeeks = obj.int("teachingWeeks"),
                totalWeeks = obj.int("totalWeeks"),
                end = obj.str("end"),
            )
        } ?: emptyList()
        val events = root.get("events")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            CalendarEvent(
                title = obj.str("title"),
                start = obj.str("start"),
                end = obj.str("end").ifBlank { obj.str("start") },
                kind = obj.str("kind").ifBlank { "activity" },
                short = obj.str("short"),
            )
        } ?: emptyList()
        CalendarFile(
            academicYear = root.str("academicYear"),
            source = root.str("source"),
            terms = terms,
            events = events,
        )
    }

    val qaBank: List<QaCourse> by lazy {
        val text = textOf("quickanswer/bank.json") ?: return@lazy emptyList()
        val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return@lazy emptyList()
        val courses = root.get("courses")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@lazy emptyList()
        courses.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val chapters = obj.get("chapters")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { ch ->
                val c = ch.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val questions = c.get("questions")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { q ->
                    val qo = q.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    QaQuestion(
                        id = qo.str("id"),
                        question = qo.str("question"),
                        answerPoints = qo.stringList("answerPoints"),
                        hint = qo.str("hint"),
                        tags = qo.stringList("tags"),
                    )
                } ?: emptyList()
                QaChapter(index = c.int("index"), title = c.str("title"), questions = questions)
            } ?: emptyList()
            QaCourse(
                id = obj.str("id"),
                subject = obj.str("subject"),
                name = obj.str("name"),
                teacher = obj.str("teacher"),
                chapters = chapters,
            )
        }
    }
}
