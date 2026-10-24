package com.pocket.xiaoli.data.feedback

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 一次提交的结果。UI 按这个决定「清空输入 / 入队 / 原样留着」。 */
sealed interface SubmitOutcome {

    data object Sent : SubmitOutcome

    /** 网络、凭据或服务端临时故障 —— 内容还有救，留在本地队列下次补发。 */
    data class Retryable(val reason: String) : SubmitOutcome

    /** 表格明确拒收（多半是列名对不上）—— 重试没意义，必须让用户看见。 */
    data class Rejected(val reason: String) : SubmitOutcome
}

data class FeedbackReply(val text: String, val at: String)

/**
 * 可选渠道：把反馈写进一张**表格**（飞书多维表格），并从另一张只读表取回复。
 *
 * ## 为什么是"可选"
 *
 * 这条链路要凭据（app_id / app_secret / 两张表的 token + table_id），而凭据不可能
 * 塞进公开仓库。`gradle.properties` 里留空 ⇒ [configured] 为 false ⇒ 反馈页
 * **整个「直接发送」按钮不出现**，用户只剩复制 / 邮件 / GitHub 三条真通道。
 *
 * ## 两张表，各给一半权限
 *
 * app_secret 打进 APK 一定能被反编译拿到，所以按「泄露了也不痛」设计：
 *
 * | 表 | 应用权限 | 里面有什么 |
 * |---|---|---|
 * | 反馈表（[configured]） | **只写** | 用户原文、联系方式、匿名 ID、可选设备信息 |
 * | 回复表（[replyConfigured]） | **只读** | 工单号、回复正文、回复时间 |
 *
 * 两张开在不同的多维表格文档里，各自加应用为协作者（一张可编辑、一张只读），
 * 不依赖飞书的高级权限。**绝不能**把回复字段并进反馈表 —— 那等于给读权限开了整张反馈表。
 *
 * ## 字段
 *
 * 反馈表列名必须一字不差：工单号 / 类型 / 内容 / 联系方式 / 匿名ID / 版本 / 平台，
 * 勾了「附带设备信息」时多一个 环境。回复表列名：工单号 / 回复 / 回复时间。
 */
object FeedbackRemote {

    private const val FEISHU_BASE = "https://open.feishu.cn/open-apis"
    private const val F_TICKET = "工单号"
    private const val F_REPLY = "回复"
    private const val F_REPLY_AT = "回复时间"
    private const val MAX_REPLY_PAGES = 5

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpireAt: Long = 0L

    val configured: Boolean
        get() = BuildConfig.FEEDBACK_FEISHU_APP_ID.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_APP_SECRET.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_APP_TOKEN.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_TABLE_ID.isNotBlank()

    /** 回复表是独立的只读文档；没配就整块藏起来，不做"半个回复功能"。 */
    val replyConfigured: Boolean
        get() = configured &&
            BuildConfig.FEEDBACK_FEISHU_REPLY_APP_TOKEN.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_REPLY_TABLE_ID.isNotBlank()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 一条记录的 fields。列名改这里之前先改表格。 */
    fun recordJson(
        draft: FeedbackDraft,
        ticketId: String,
        diagnostics: String?,
        anonId: String,
    ): String {
        val fields = JsonObject().apply {
            addProperty("工单号", ticketId)
            addProperty("类型", draft.category.label)
            addProperty("内容", draft.content.trim())
            addProperty("联系方式", draft.contact.trim())
            addProperty("匿名ID", anonId)
            addProperty("版本", BuildConfig.VERSION_NAME)
            addProperty("平台", "Android")
        }
        if (draft.includeDiagnostics && !diagnostics.isNullOrBlank()) {
            // 多维表格的文本列存不下富文本，直接平铺进去即可。
            fields.addProperty("环境", diagnostics)
        }
        val body = JsonObject().apply { add("fields", fields) }
        return body.toString()
    }

    suspend fun submit(
        draft: FeedbackDraft,
        ticketId: String,
        diagnostics: String?,
        anonId: String,
    ): SubmitOutcome = withContext(Dispatchers.IO) {
        val token = tenantToken() ?: return@withContext SubmitOutcome.Retryable("没有拿到上传凭据")
        try {
            val url = "$FEISHU_BASE/bitable/v1/apps/${BuildConfig.FEEDBACK_FEISHU_APP_TOKEN}" +
                "/tables/${BuildConfig.FEEDBACK_FEISHU_TABLE_ID}/records"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .post(recordJson(draft, ticketId, diagnostics, anonId).toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                val json = parse(response.body?.string().orEmpty())
                classify(
                    httpCode = response.code,
                    code = json.intOr("code", -1),
                    message = plainText(json.get("msg")),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SubmitOutcome.Retryable("网络不通：${e.javaClass.simpleName}")
        } catch (e: Exception) {
            SubmitOutcome.Rejected("发送失败：${e.javaClass.simpleName}")
        }
    }

    /**
     * 拉取这些工单号对应的回复。失败一律返回空表 —— 回复是加分项，不能因为它
     * 把整个页面变成错误页；节流由 [FeedbackStore] 负责。
     */
    suspend fun replies(tickets: List<String>): Map<String, FeedbackReply> =
        withContext(Dispatchers.IO) {
            if (!replyConfigured) return@withContext emptyMap()
            val wanted = tickets.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(20)
                .toSet()
            if (wanted.isEmpty()) return@withContext emptyMap()
            val token = tenantToken() ?: return@withContext emptyMap()

            // 多行文本的 contains 一次只能放一个值，所以每个工单号一个条件、OR 起来。
            val conditions = JsonArray().apply {
                wanted.forEach { id ->
                    add(
                        JsonObject().apply {
                            addProperty("field_name", F_TICKET)
                            addProperty("operator", "contains")
                            add("value", JsonArray().apply { add(id) })
                        }
                    )
                }
            }
            val filter = JsonObject().apply {
                addProperty("conjunction", "or")
                add("conditions", conditions)
            }

            val found = linkedMapOf<String, FeedbackReply>()
            var pageToken: String? = null
            var useFilter = true
            var pages = 0
            while (pages < MAX_REPLY_PAGES) {
                val json = searchRecords(token, filter.takeIf { useFilter }, pageToken) ?: break
                val code = json.intOr("code", -1)
                if (code != 0) {
                    // 工单号若是公式/查找引用字段，filter 会报 1254018。
                    // 回复表本身是"可读也无所谓"的表，去掉 filter 拉一次再本地匹配。
                    if (useFilter) {
                        useFilter = false
                        pageToken = null
                        continue
                    }
                    break
                }
                val data = json.objectOrNull("data") ?: break
                collectReplies(data.arrayOrNull("items"), wanted, found)
                if (found.size >= wanted.size) break
                pages++
                val hasMore = data.booleanOr("has_more", false)
                pageToken = data.stringOr("page_token").takeIf { hasMore && it.isNotBlank() }
                if (pageToken == null) break
            }
            found
        }

    /** 成功返回 null，失败返回一句给用户看的话。 */
    private fun tenantToken(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpireAt) return it }
        val payload = JsonObject().apply {
            addProperty("app_id", BuildConfig.FEEDBACK_FEISHU_APP_ID)
            addProperty("app_secret", BuildConfig.FEEDBACK_FEISHU_APP_SECRET)
        }.toString()
        val request = Request.Builder()
            .url("$FEISHU_BASE/auth/v3/tenant_access_token/internal")
            .post(payload.toRequestBody(JSON))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val json = parse(response.body?.string().orEmpty())
                if (json.intOr("code", -1) != 0) return@use null
                val token = json.stringOr("tenant_access_token")
                if (token.isBlank()) return@use null
                // 用服务端给的 expire，提前 5 分钟换，别写死一个窗口。
                val expireSeconds = json.intOr("expire", 7200)
                cachedToken = token
                tokenExpireAt = System.currentTimeMillis() +
                    (expireSeconds - 300).coerceAtLeast(60) * 1000L
                token
            }
        }.getOrNull()
    }

    private fun searchRecords(
        token: String,
        filter: JsonObject?,
        pageToken: String?,
    ): JsonObject? {
        val url = buildString {
            append("$FEISHU_BASE/bitable/v1/apps/${BuildConfig.FEEDBACK_FEISHU_REPLY_APP_TOKEN}")
            append("/tables/${BuildConfig.FEEDBACK_FEISHU_REPLY_TABLE_ID}/records/search?page_size=100")
            if (!pageToken.isNullOrBlank()) {
                append("&page_token=").append(URLEncoder.encode(pageToken, "UTF-8"))
            }
        }
        val body = JsonObject().apply { if (filter != null) add("filter", filter) }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                parse(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun collectReplies(
        items: JsonArray?,
        wanted: Set<String>,
        into: MutableMap<String, FeedbackReply>,
    ) {
        items?.forEach { element ->
            val fields = element.asJsonObject.objectOrNull("fields") ?: return@forEach
            val ticket = plainText(fields.get(F_TICKET)).trim()
            if (ticket.isEmpty() || ticket !in wanted) return@forEach
            val text = plainText(fields.get(F_REPLY))
            if (text.isBlank()) return@forEach
            into[ticket] = FeedbackReply(text, plainText(fields.get(F_REPLY_AT)))
        }
    }

    /**
     * 把 HTTP 状态和飞书业务码翻成三种结果。
     *
     * 401/403 单独算「可重试」而不是拒收：凭据吊销后旧版本会一直失败，
     * 但用户升级前后提交的文字不该被丢掉——留在本机，升级后再补发。
     */
    internal fun classify(httpCode: Int, code: Int, message: String): SubmitOutcome = when {
        httpCode == 401 || httpCode == 403 ->
            SubmitOutcome.Retryable(message.ifBlank { "凭据失效（HTTP $httpCode）" })
        httpCode == 429 || httpCode >= 500 ->
            SubmitOutcome.Retryable(message.ifBlank { "服务暂时不可用（HTTP $httpCode）" })
        httpCode !in 200..299 ->
            SubmitOutcome.Rejected(message.ifBlank { "表格拒收（HTTP $httpCode）" })
        code == 0 -> SubmitOutcome.Sent
        code in 99991660..99991670 ->
            SubmitOutcome.Retryable(message.ifBlank { "凭据失效（$code）" })
        else -> SubmitOutcome.Rejected(message.ifBlank { "表格拒收（$code）" })
    }

    private fun parse(raw: String): JsonObject =
        runCatching { JsonParser.parseString(raw).asJsonObject }.getOrDefault(JsonObject())

    /** 飞书文本字段可能是字符串，也可能是 `[{type:text,text:"..."}]` 富文本数组。 */
    internal fun plainText(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        return when {
            element.isJsonPrimitive -> {
                val primitive = element.asJsonPrimitive
                if (primitive.isNumber) maybeMillis(primitive.asLong) else primitive.asString
            }
            element.isJsonArray -> element.asJsonArray.joinToString("") { plainText(it) }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.stringOr("text").ifBlank { obj.stringOr("name") }
            }
            else -> ""
        }
    }

    /** 飞书日期字段回来是毫秒时间戳，直接显示一串数字用户看不懂。 */
    private fun maybeMillis(value: Long): String =
        if (value > 10_000_000_000L) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(value))
        } else {
            value.toString()
        }

    private fun JsonObject.intOr(key: String, fallback: Int): Int =
        runCatching { get(key)?.asInt ?: fallback }.getOrDefault(fallback)

    private fun JsonObject.stringOr(key: String): String =
        runCatching { get(key)?.asString.orEmpty() }.getOrDefault("")

    private fun JsonObject.booleanOr(key: String, fallback: Boolean): Boolean =
        runCatching { get(key)?.asBoolean ?: fallback }.getOrDefault(fallback)

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        runCatching { get(key)?.asJsonObject }.getOrNull()

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        runCatching { get(key)?.asJsonArray }.getOrNull()

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
