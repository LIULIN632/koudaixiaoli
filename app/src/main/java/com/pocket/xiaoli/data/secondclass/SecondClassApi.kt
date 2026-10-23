package com.pocket.xiaoli.data.secondclass

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.cancellation.CancellationException

/**
 * 到梦空间的**传输层**：把"一次查询"变成"一次 POST + 一个已校验的 JSON 根对象"。
 *
 * 它只做四件事，每件都有实测依据（架构文档 §11.6 / §11.9 / §4.2）：
 *
 * | 做什么 | 依据 |
 * |---|---|
 * | 带 `X-Requested-With` 的表单 POST | 不带该头服务端返回**整页首页 HTML** |
 * | 每次发送前过 [SecondClassRateLimiter] | B8 是硬边界 —— **重试不是绕过它的理由** |
 * | 429/5xx 指数退避 2s→4s→8s，最多 3 次 | §4.2 的限流契约 |
 * | 用 [SecondClassEnvelope] 判"成功 / 未登录 / 失败" | 成功响应**没有 `code`**、鉴权失败**仍是 HTTP 200** |
 *
 * ### 为什么 `send` 是注入的
 *
 * 本仓库没有 MockWebServer（`app/build.gradle.kts` 的测试依赖里没有它，我也没有擅自加）。
 * 把"发送"抽成一个函数，**退避、重试次数、错误映射、`Unauthorized` 的透传**就都能在
 * 纯 JVM 下被证明 —— 否则这些逻辑只有上真机才知道对不对，而它们恰好是最容易写错的部分。
 *
 * ⚠️ 这与 `CLAUDE.md` 记的那条教训不冲突：那条警告的是**注入第二个"时间源"**
 * （假时钟 + 真 `delay` ⇒ 用例时红时绿）。`send` 不是时间源，延迟仍然全部走
 * [SecondClassRateLimiter] 的真实 `delay`，用例只把间隔调小。
 *
 * @param send 真正发出去的那一步。生产用 [okHttpSender]。
 * @param limiter **必须与数据源共用同一个实例**（B8 的执行点，见 `SecondClassRateLimiter` 的 KDoc）。
 */
class SecondClassApi(
    private val send: suspend (Request) -> SecondClassResult<String>,
    private val limiter: SecondClassRateLimiter = SecondClassRateLimiter(),
    private val baseUrl: String = BASE_URL,
) {

    /**
     * 发一次表单 POST 并解析出 JSON 根对象。
     *
     * 返回 `Ok(root)` 时调用方拿到的**已经是一个校验过的 JSON 对象**（不是字符串）；
     * `Unauthorized` 表示会话失效（由 Repository 收敛）；其余是失败。
     */
    suspend fun postForm(path: String, params: Map<String, String> = emptyMap()): SecondClassResult<JsonObject> {
        val request = SecondClassRequests.formPost(baseUrl, path, params)
        var retries = 0
        while (true) {
            limiter.acquire()
            val outcome = send(request).mapValidated { body -> SecondClassEnvelope.validate(body) }
            val retryable = (outcome as? SecondClassResult.Failed)?.code.isRetryable()
            if (retryable && retries < SecondClassRateLimiter.MAX_ATTEMPTS) {
                retries++
                // delay 可取消：用户在退避期间离开页面 ⇒ 协程取消 ⇒ 不再发请求（§4.2）。
                delay(limiter.backoffMs(retries))
                continue
            }
            return outcome
        }
    }

    companion object {
        /** 数据端点的主机。⚠️ **不是** `api.5idream.net`（那个只用于图片上传的 OSS policy，见 §11.9）。 */
        const val BASE_URL = "https://www.5idream.net"

        // ─────────── 端点（全部实测自公开页面 JS，出处 §11.9） ───────────

        /** 我参与的活动。参数：`userid` / `rows` / `page`。 */
        const val PATH_MY_JOIN = "activity/activity/myjoin"

        /** 活动详情。参数：`activityId`（出处：`activitydetail.js`）。 */
        const val PATH_ACTIVITY_INFO = "activity/activity/activityinfo"

        /** 成绩单（学分/模块）。参数：`userid` / `rows` / `page`。 */
        const val PATH_SCORE_LIST = "certificate/certificate/scorelist"

        /** 成长记录。参数：`userid` / `rows` / `page`。 */
        const val PATH_RECORD_LIST = "certificate/certificate/recordlist"

        /** 校验会话并取回用户信息（`result.id` / `schoolid` / `activatflag`）。参数：`token`。 */
        const val PATH_CHECK_TOKEN = "token/checkToken"

        /**
         * 只有 429 与 5xx 值得重试 —— 与 §4.2 的契约逐字对应。
         *
         * ⚠️ **不要**把 4xx 加进来：`code`/HTTP 4xx 是"请求本身有问题"，重试只是把同一个错误发三遍。
         */
        private fun Int?.isRetryable(): Boolean = this == 429 || (this != null && this in 500..599)

        /**
         * 生产用的发送实现：真发 OkHttp，把响应体原样交回（校验在 [SecondClassEnvelope] 里做）。
         *
         * ⚠️ 传进来的 [client] **必须**用第二课堂自己的 `SecondClassCookieJar` 作为 CookieJar
         * （不要与教务/图书馆共用：CookieJar、超时、限流策略完全不同，共用会互相污染 Cookie）。
         *
         * ⚠️ 关于 401/403：到梦空间的"未登录"是 **HTTP 200 + `code:"400"`**（实测），
         * 这里的 401/403 → [SecondClassResult.Unauthorized] 是一条**防御性**映射
         * ——平台**没有被观察到**会用这两个状态码。加它的理由是：万一哪天用了，
         * 用户看到的应该是"请重新登录"而不是"加载失败"。**判未登录的主信号仍然是信封里的 `code`。**
         */
        fun okHttpSender(client: OkHttpClient): suspend (Request) -> SecondClassResult<String> = { request ->
            withContext(Dispatchers.IO) {
                try {
                    client.newCall(request).execute().use { response ->
                        httpResult(response.code, response.body?.string().orEmpty())
                    }
                } catch (e: CancellationException) {
                    // 取消要原样上抛：吞掉它会让"用户离开页面"被当成一次网络失败。
                    throw e
                } catch (e: Exception) {
                    SecondClassResult.Failed(null, e.message ?: e.javaClass.simpleName)
                }
            }
        }
    }
}

/**
 * HTTP 状态码 → 结果。抽成纯函数只为一件事：**让它可测**。
 *
 * 放在 `okHttpSender` 里面的话，401/403 那条分支就永远没有用例能碰到它
 * （本仓库没有 MockWebServer，见 `SecondClassApi` 的 KDoc）。
 *
 * ⚠️ 判"未登录"的**主信号**仍然是响应体里的 `code == "400"`（实测：鉴权失败也是 HTTP 200）；
 * 这里的 401/403 → [SecondClassResult.Unauthorized] 是**防御性**映射，平台没被观察到会用它们。
 */
internal fun httpResult(code: Int, body: String): SecondClassResult<String> = when {
    code in 200..299 -> SecondClassResult.Ok(body)
    code == 401 || code == 403 -> SecondClassResult.Unauthorized
    else -> SecondClassResult.Failed(code, "HTTP $code")
}
