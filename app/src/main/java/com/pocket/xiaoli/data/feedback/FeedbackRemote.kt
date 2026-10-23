package com.pocket.xiaoli.data.feedback

import com.google.gson.JsonObject
import com.pocket.xiaoli.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 可选渠道：把反馈写进一张**表格**（飞书多维表格）。
 *
 * ## 为什么是"可选"
 *
 * 这条链路要三个凭据（app_id / app_secret / app_token + table_id），而凭据不可能
 * 塞进公开仓库。`gradle.properties` 里留空 ⇒ [configured] 为 false ⇒ 反馈页
 * **整个「直接发送」按钮不出现**，用户只剩复制 / 邮件 / GitHub 三条真通道。
 * 本地填上凭据重打一版，按钮才长出来 —— 没配置的功能不该在界面上装样子。
 *
 * ## 红线说明
 *
 * 项目约束禁止上传用户数据。这里**不自动上报任何东西**：只有用户亲手点了
 * 「直接发送」，且「附带设备信息」由他自己决定，才会有一次 POST。
 * 与崩溃诊断同性质（用户知情、可关闭），而不是埋点。
 *
 * ## 字段
 *
 * 多维表格的列名必须与之完全一致（中文）：工单号 / 类型 / 内容 / 联系方式 / 版本 / 平台。
 * 前端字段拼装是纯函数 [recordJson]，可单测；网络那层失败只回一句话，不重试。
 */
object FeedbackRemote {

    private const val FEISHU_BASE = "https://open.feishu.cn/open-apis"

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpireAt: Long = 0L

    val configured: Boolean
        get() = BuildConfig.FEEDBACK_FEISHU_APP_ID.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_APP_SECRET.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_APP_TOKEN.isNotBlank() &&
            BuildConfig.FEEDBACK_FEISHU_TABLE_ID.isNotBlank()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** 一条记录的 fields。列名改这里之前先改表格。 */
    fun recordJson(draft: FeedbackDraft, ticketId: String, nowMillis: Long, diagnostics: String?): String {
        val fields = JsonObject().apply {
            addProperty("工单号", ticketId)
            addProperty("类型", draft.category.label)
            addProperty("内容", draft.content.trim())
            addProperty("联系方式", draft.contact.trim())
            addProperty("版本", BuildConfig.VERSION_NAME)
            addProperty("平台", "Android")
        }
        if (draft.includeDiagnostics && !diagnostics.isNullOrBlank()) {
            // 多维表格的文本列存不下换行以外的富文本，直接平铺进去即可。
            fields.addProperty("环境", diagnostics)
        }
        val body = JsonObject().apply { add("fields", fields) }
        return body.toString()
    }

    /** 成功返回 null，失败返回一句给用户看的话。 */
    suspend fun submit(draft: FeedbackDraft, ticketId: String, nowMillis: Long, diagnostics: String?): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = tenantToken() ?: return@runCatching "没有拿到上传凭据（检查 gradle.properties）"
                val url = "$FEISHU_BASE/bitable/v1/apps/${BuildConfig.FEEDBACK_FEISHU_APP_TOKEN}" +
                    "/tables/${BuildConfig.FEEDBACK_FEISHU_TABLE_ID}/records"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(recordJson(draft, ticketId, nowMillis, diagnostics).toRequestBody(JSON))
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful && text.contains("\"code\":0")) null
                    else "表格拒收（HTTP ${response.code}）"
                }
            }.getOrElse { "网络不通：${it.javaClass.simpleName}" }
        }

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
                val text = response.body?.string().orEmpty()
                val match = TOKEN.find(text) ?: return null
                val token = match.groupValues[1]
                cachedToken = token
                tokenExpireAt = System.currentTimeMillis() + 25 * 60 * 1000L
                token
            }
        }.getOrNull()
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val TOKEN = Regex("\"tenant_access_token\"\\s*:\\s*\"([^\"]+)\"")
}
