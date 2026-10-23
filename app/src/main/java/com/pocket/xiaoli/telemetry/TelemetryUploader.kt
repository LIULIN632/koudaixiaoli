package com.pocket.xiaoli.telemetry

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 把一批事件 POST 到运营中心。
 *
 * **它自己不管开关、不管队列** —— 那些在 [Telemetry] 与 [TelemetryStore] 里。
 * 这里只回答一个问题：这一批发出去、还能不能丢？所以它能脱离 Android 用
 * MockWebServer 测（见 `TelemetryUploaderTest`）。
 */
class TelemetryUploader(
    private val baseUrl: String,
    private val appKey: String,
    private val client: OkHttpClient = defaultClient(),
) {

    /** 没配地址或没配 key 就当没这个功能：**一个请求都不会发**。 */
    val configured: Boolean get() = baseUrl.isNotBlank() && appKey.isNotBlank()

    /**
     * @return true 表示这一批可以清掉（收下了，或者服务端明确说这批发不进去）；
     *         false 表示值得留着下次再发（限流、5xx、网络错误）。
     *
     * 400 系里除了 429 都当"丢掉"：整批校验不过说明客户端造了一条服务端不认的数据，
     * 无限重试只会让它永远堵在队头，把后面正常的事件一起饿死。
     */
    fun upload(installId: String, versionCode: Int, osVersion: Int, events: List<String>): Boolean {
        if (!configured || events.isEmpty()) return true
        val body = buildBody(installId, versionCode, osVersion, events)
        if (body.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return true

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + PATH)
            .header("X-App-Key", appKey)
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody(JSON))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                when {
                    code in 200..299 -> true
                    code == 429 -> false
                    code in 400..499 -> true
                    else -> false
                }
            }
        } catch (_: Exception) {
            // 没网、超时、DNS 挂了：都值得下次再说
            false
        }
    }

    /** 纯函数，便于单测直接比对字节。字段名与服务端 `BatchPayload` 一一对应。 */
    internal fun buildBody(
        installId: String,
        versionCode: Int,
        osVersion: Int,
        events: List<String>,
    ): String = buildString {
        append("{\"v\":1,\"iid\":\"").append(installId).append('"')
        append(",\"vc\":").append(versionCode)
        append(",\"os\":").append(osVersion)
        append(",\"events\":[").append(events.joinToString(",")).append("]}")
    }

    companion object {
        const val PATH = "/v1/batch"

        /** 与服务端一致：单批 ≤32 KB、≤50 条 */
        const val MAX_BYTES = 32 * 1024
        const val MAX_EVENTS = 50

        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
