package com.pocket.xiaoli.data.feedback

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.data.SelfHost
import com.pocket.xiaoli.data.announcement.AppFeedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 一次提交的结果。UI 按这个决定「清空输入 / 入队 / 原样留着」。 */
sealed interface SubmitOutcome {

    data object Sent : SubmitOutcome

    /** 网络或服务端临时故障 —— 内容还有救，留在本地队列下次补发。 */
    data class Retryable(val reason: String) : SubmitOutcome

    /** 服务端明确拒收 —— 重试没意义，必须让用户看见并改成手动发送。 */
    data class Rejected(val reason: String) : SubmitOutcome
}

data class FeedbackReply(val text: String, val at: String)

/**
 * 反馈的直传通道：**我们自己的服务器**（`/v1/feedback/submit`）。
 *
 * ## 为什么不再走飞书多维表格
 *
 * 旧实现在 APK 里编了 `app_secret` 与四张表的 token。secret 打进包必然能被反编译拿走，
 * 只能靠「泄露了也不痛」的权限设计兜着；而且回复要人工去表格里翻。
 * 换成自建之后：数据进自己的 Postgres、面板上直接回、APK 里只剩一个公开地址和一个
 * 只用于挡扫描器的 `X-App-Key`。
 *
 * ## 地址从哪来（这也是「换服务器方便」的全部实现）
 *
 * 1. `announce/feed.json` 的 `endpoints.feedback`（**改它不用发版**）；
 * 2. 没有就回落到 `SelfHost.TELEMETRY_URL` + `/v1/feedback`（同一台机器，编译期默认值）。
 *
 * 两边都没有 ⇒ [configured] 为 false ⇒ 反馈页不显示「直接发送」按钮，
 * 用户只剩复制 / 系统分享 / 邮件 / GitHub Issue 四条不需要服务器的通道。
 *
 * ## 存了什么
 *
 * 工单号、分类、正文、可选联系方式、可选设备信息、每 30 天轮换的匿名 id。
 * **没有**学号、IMEI、IP —— 服务端也不记 IP，Nginx 只做限流。
 */
object FeedbackRemote {

    private const val PATH = "/v1/feedback"
    private const val SUBMIT = "$PATH/submit"
    private const val REPLIES = "$PATH/replies"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 反馈接口的**基址**（不含 `/v1/feedback`）。feed 里的优先（可热换），否则用编译期默认值。
     *
     * `endpoints.feedback` 允许写成两种形状，都收：
     * - `https://host`                —— 基址
     * - `https://host/v1/feedback`    —— 完整接口前缀（`docs/反馈与建议.md` 里写的就是这种）
     *
     * 2026-10-25 的真实事故：feed 里填的是后者，而这里直接 `+ "/v1/feedback/submit"`，
     * 于是请求打到 `/v1/feedback/v1/feedback/submit` —— 4 个 404，用户说「反馈发不过来」。
     * 所以现在统一**先归一化成基址**，调用方只拼一次路径。
     */
    fun endpoint(context: Context): String? =
        baseOf(AppFeedStore(context).feedbackEndpoint()) ?: baseOf(SelfHost.TELEMETRY_URL)

    /** 把各种写法收敛成基址；非法（非 http(s)、空）返回 null。纯函数，便于单测。 */
    internal fun baseOf(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (!text.startsWith("https://") && !text.startsWith("http://")) return null
        text = text.trimEnd('/')
        // 已经带了接口路径就去掉；多写几层也一并收掉（`/v1/feedback/submit` 这种手滑）
        while (true) {
            val hit = listOf(SUBMIT, REPLIES, PATH).firstOrNull { text.endsWith(it) } ?: break
            text = text.removeSuffix(hit).trimEnd('/')
        }
        return text.takeIf { it.isNotEmpty() && it != "https:/" && it != "http:/" }
    }

    /** 有没有可以直传的服务器。没有就整块按钮不出现，而不是「点了没反应」。 */
    fun configured(context: Context): Boolean = endpoint(context) != null

    /**
     * 基址 + 接口路径。唯一的拼接点：所有请求都走这里，路径就只会出现一次。
     * （上一版是在两处各自 `+ "/v1/feedback/..."`，改一处漏一处就出了 404。）
     */
    internal fun urlFor(base: String, path: String): String = base.trimEnd('/') + path

    /**
     * 请求体。纯函数，便于单测：字段名就是服务端 `FeedbackSubmit` 的字段名，
     * 两边改一边就要改另一边（`server/.../feedback/FeedbackRoutes.kt`）。
     */
    internal fun submitJson(
        draft: FeedbackDraft,
        ticketId: String,
        diagnostics: String?,
        iid: String,
    ): String {
        val body = JsonObject().apply {
            addProperty("ticket", ticketId)
            addProperty("category", draft.category.name)
            addProperty("content", draft.content.trim())
            addProperty("contact", draft.contact.trim().takeIf { it.isNotBlank() })
            addProperty("iid", iid)
            addProperty("versionCode", BuildConfig.VERSION_CODE)
            addProperty("versionName", BuildConfig.VERSION_NAME)
            // 设备信息只在用户勾了「附带设备信息」时才有值，没勾就一个字段都不带。
            val env = if (draft.includeDiagnostics) diagnostics else null
            addProperty("device", deviceOf(env))
            addProperty("os", osOf(env))
        }
        return body.toString()
    }

    /**
     * 从 `FeedbackFormatter.diagnostics` 的文本里取机型。
     *
     * 为什么不是直接传结构化参数：那段文本是**用户已经在页面上看到**的东西
     * （「附带设备信息：版本 … 机型 …」），从同一份文本里取，界面上写什么就是发什么，
     * 不会出现「勾了但它其实没发」。
     */
    internal fun deviceOf(diagnostics: String?): String? =
        diagnostics?.lineSequence()?.firstOrNull { it.startsWith("机型 ") }
            ?.removePrefix("机型 ")?.trim()?.takeIf { it.isNotBlank() }

    internal fun osOf(diagnostics: String?): String? =
        diagnostics?.lineSequence()?.firstOrNull { it.startsWith("系统 ") }
            ?.removePrefix("系统 ")?.trim()?.takeIf { it.isNotBlank() }

    suspend fun submit(
        context: Context,
        draft: FeedbackDraft,
        ticketId: String,
        diagnostics: String?,
        anonId: String,
    ): SubmitOutcome = withContext(Dispatchers.IO) {
        val url = endpoint(context)
            ?: return@withContext SubmitOutcome.Rejected("这台设备没有配置反馈通道")
        try {
            val request = Request.Builder()
                .url(urlFor(url, SUBMIT))
                .addHeader("X-App-Key", SelfHost.TELEMETRY_KEY)
                .post(submitJson(draft, ticketId, diagnostics, anonId).toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                classify(response.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            SubmitOutcome.Retryable("网络不通：${e.javaClass.simpleName}")
        } catch (e: Exception) {
            SubmitOutcome.Retryable("发送失败：${e.javaClass.simpleName}")
        }
    }

    /**
     * 拉取这些工单号对应的回复。失败一律返回空表 —— 回复是加分项，
     * 不能因为它把整个页面变成错误页；节流由 [FeedbackStore] 负责。
     */
    suspend fun replies(context: Context, tickets: List<String>): Map<String, FeedbackReply> =
        withContext(Dispatchers.IO) {
            val url = endpoint(context) ?: return@withContext emptyMap()
            val wanted = tickets.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(20)
                .toList()
            if (wanted.isEmpty()) return@withContext emptyMap()
            val query = wanted.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            try {
                val request = Request.Builder()
                    .url(urlFor(url, REPLIES) + "?tickets=$query")
                    .addHeader("X-App-Key", SelfHost.TELEMETRY_KEY)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    parseReplies(response.body?.string().orEmpty())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyMap()
            }
        }

    /**
     * 解析 `{"replies":[{"ticket","reply","repliedAt"}]}`。
     *
     * 时间统一转成本地时区的 `yyyy-MM-dd HH:mm`：面板与 App 显示的是同一句话，
     * 用户不该看到 UTC 的 08:00 而手机上是 16:00。
     */
    internal fun parseReplies(json: String): Map<String, FeedbackReply> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return emptyMap()
        val array = runCatching { root.getAsJsonArray("replies") }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, FeedbackReply>()
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            val ticket = obj.string("ticket") ?: continue
            val text = obj.string("reply")?.takeIf { it.isNotBlank() } ?: continue
            out[ticket] = FeedbackReply(text, formatTime(obj.string("repliedAt")))
        }
        return out
    }

    /** ISO-8601（UTC）→ 本地 `yyyy-MM-dd HH:mm`；解析不了就原样给回去。 */
    internal fun formatTime(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(raw))
        }.getOrDefault(raw)
    }

    /**
     * HTTP 码 → 结果。
     *
     * 401/403 判成「拒收」而不是「可重试」：那说明地址或 `X-App-Key` 配错了，
     * 重试一万次也不会通，让用户当场改成复制 / 邮件才有意义。
     */
    internal fun classify(httpCode: Int): SubmitOutcome = when {
        httpCode in 200..299 -> SubmitOutcome.Sent
        httpCode == 429 -> SubmitOutcome.Retryable("今天提交得太多了，明天再试")
        httpCode == 400 || httpCode == 413 ->
            SubmitOutcome.Rejected("服务端没收下这条反馈（$httpCode）")
        httpCode == 401 || httpCode == 403 ->
            SubmitOutcome.Rejected("反馈通道未配置好（$httpCode），请改用复制或邮件")
        else -> SubmitOutcome.Retryable("服务端暂时不可用（$httpCode）")
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { get(key)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()
}
