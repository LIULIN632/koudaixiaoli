package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SecondClassApi] 的契约：**请求形状**、**退避重试**、**错误映射**。
 *
 * 这些都不需要网络：把"发送"这一步换成假的，退避与重试就变成可断言的。
 * 换来的是本模块最容易静默写错的两处 —— **发成 GET**（服务端会返回整页首页 HTML）、
 * 以及重试悄悄绕过限流。
 *
 * ⚠️ 全程用**真实的 `delay`**（只把间隔调小），不注入假时钟 —— 理由见 `SecondClassRateLimiter` 的 KDoc。
 */
class SecondClassApiTest {

    private val successBody = """{"rows":[],"records":"0","page":"1"}"""

    /** 实测抓取的真实未登录响应。 */
    private val unauthorizedBody = """{"code":"400","result":"token不能为空","success":false}"""

    private fun limiter(
        minIntervalMs: Long = 0,
        backoffBaseMs: Long = 20,
        maxBackoffMs: Long = 40,
    ) = SecondClassRateLimiter(minIntervalMs, backoffBaseMs, maxBackoffMs)

    private suspend fun elapsedMs(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000L
    }

    // ─────────────────────── 请求形状（那个"看不出错在哪"的坑） ───────────────────────

    /**
     * 守门人：**必须是 POST**（`method` 那条断言才是关键），并且是表单体 + 与页面同形的 AJAX 头。
     *
     * ⚠️ 别把 `X-Requested-With` 当成成败关键：2026-10-10 的对照实测（§11.12）表明
     * **不带它的 POST 照样返回 JSON**，而**不带它的 GET 返回整页首页 HTML** ——
     * 决定成败的是 method。带着它只为与页面同形。
     */
    @Test
    fun `请求必须是带 AJAX 头的表单 POST`() = runBlocking {
        val captured = mutableListOf<Request>()
        val api = SecondClassApi(
            send = { captured += it; SecondClassResult.Ok(successBody) },
            limiter = limiter(),
        )

        api.postForm(
            SecondClassApi.PATH_MY_JOIN,
            mapOf("userid" to "123", "rows" to "20", "page" to "1"),
        )

        val request = captured.single()
        assertEquals("POST", request.method)
        assertEquals("https://www.5idream.net/activity/activity/myjoin", request.url.toString())
        assertEquals(
            "与页面同形（jQuery 的 AJAX 也带它）；⚠️ 但实测决定成败的是 POST 本身，见 §11.12",
            "XMLHttpRequest",
            request.header(SecondClassRequests.HEADER_AJAX),
        )
        val body = request.body as FormBody
        assertEquals("参数走表单体，与页面里的 jQuery 同形", 3, body.size)
        assertEquals("userid", body.name(0))
        assertEquals("123", body.value(0))
        assertEquals("page", body.name(2))
        assertEquals("1", body.value(2))
    }

    @Test
    fun `baseUrl 与 path 的斜杠不会被拼成双斜杠`() = runBlocking {
        val captured = mutableListOf<Request>()
        val api = SecondClassApi(
            send = { captured += it; SecondClassResult.Ok(successBody) },
            limiter = limiter(),
            baseUrl = "https://www.5idream.net/",
        )

        api.postForm("/certificate/certificate/scorelist")

        assertEquals(
            "https://www.5idream.net/certificate/certificate/scorelist",
            captured.single().url.toString(),
        )
    }

    // ─────────────────────────── 响应判定 ───────────────────────────

    @Test
    fun `成功响应解析出 JSON 根对象`() = runBlocking {
        val api = SecondClassApi(send = { SecondClassResult.Ok(successBody) }, limiter = limiter())

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertTrue("成功响应里没有 code，所以判成功", result is SecondClassResult.Ok)
    }

    @Test
    fun `实测的未登录响应原样是 Unauthorized 且不重试`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = { calls++; SecondClassResult.Ok(unauthorizedBody) },
            limiter = limiter(),
        )

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertEquals(SecondClassResult.Unauthorized, result)
        assertEquals("会话失效重试没有意义", 1, calls)
    }

    /** 拿到整页 HTML（漏了 AJAX 头的现场）—— 是失败，且不重试。 */
    @Test
    fun `整页 HTML 是失败且不重试`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = { calls++; SecondClassResult.Ok("<!DOCTYPE html><html>") },
            limiter = limiter(),
        )

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertTrue(result is SecondClassResult.Failed)
        assertEquals(1, calls)
    }

    // ─────────────────────────── 退避与重试 ───────────────────────────

    @Test
    fun `HTTP 400 不重试`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = { calls++; SecondClassResult.Failed(400, "HTTP 400") },
            limiter = limiter(),
        )

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertEquals(SecondClassResult.Failed(400, "HTTP 400"), result)
        assertEquals("4xx 是请求本身有问题，重试只是把同一个错误发三遍", 1, calls)
    }

    @Test
    fun `429 会退避后重试并最终成功`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = {
                calls++
                if (calls == 1) SecondClassResult.Failed(429, "HTTP 429") else SecondClassResult.Ok(successBody)
            },
            limiter = limiter(),
        )

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertTrue(result is SecondClassResult.Ok)
        assertEquals(2, calls)
    }

    @Test
    fun `连续 5xx 最多重试三次`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = { calls++; SecondClassResult.Failed(503, "HTTP 503") },
            limiter = limiter(),
        )

        val result = api.postForm(SecondClassApi.PATH_MY_JOIN)

        assertEquals(SecondClassResult.Failed(503, "HTTP 503"), result)
        assertEquals("1 次首发 + 3 次重试（§4.2：最多 3 次）", 1 + SecondClassRateLimiter.MAX_ATTEMPTS, calls)
    }

    /**
     * 守门人：**重试也要过限流**。
     *
     * B8 是硬边界，而"重试"是最容易被写在外面的那条路（"反正是失败重发，不用等"）。
     * 这条用真实时间断言：首发 + 退避 + 第二次发送，至少要等够 [SecondClassRateLimiter] 的最小间隔。
     */
    @Test
    fun `重试也要过限流而不是绕过它`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = {
                calls++
                if (calls == 1) SecondClassResult.Failed(429, "HTTP 429") else SecondClassResult.Ok(successBody)
            },
            limiter = SecondClassRateLimiter(minIntervalMs = 120, backoffBaseMs = 10, maxBackoffMs = 10),
        )

        val ms = elapsedMs { api.postForm(SecondClassApi.PATH_MY_JOIN) }

        assertTrue("第二次发送必须再等一个限流间隔，实测 ${ms}ms", ms >= 120)
    }

    /** 退避期间用户离开页面 ⇒ 协程取消 ⇒ **不再发请求**（§4.2 的最后一行）。 */
    @Test
    fun `退避期间取消就不再发请求`() = runBlocking {
        var calls = 0
        val api = SecondClassApi(
            send = { calls++; SecondClassResult.Failed(503, "HTTP 503") },
            limiter = limiter(backoffBaseMs = 400, maxBackoffMs = 800),
        )

        lateinit var job: Job
        val ms = elapsedMs {
            // 需要 coroutineScope 才拿得到 launch 的作用域（elapsedMs 的 lambda 没有 CoroutineScope 接收者）。
            coroutineScope {
                job = launch(Dispatchers.Default) { api.postForm(SecondClassApi.PATH_MY_JOIN) }
                delay(60)
                job.cancelAndJoin()
            }
        }

        assertTrue("取消后应立即返回，实测 ${ms}ms", ms < 400)
        assertTrue(job.isCancelled)
        assertEquals("取消之后不该再发第二次", 1, calls)
    }

    // ─────────────────────── 状态码 → 结果（纯函数） ───────────────────────

    /**
     * 抽成纯函数就是为了这一条：`okHttpSender` 里的分支没有 MockWebServer 就碰不到。
     *
     * ⚠️ 401/403 → `Unauthorized` 是**防御性**映射（平台实测用的是 HTTP 200 + `code:"400"`），
     * 加它的理由见 `httpResult` 的 KDoc：万一哪天用上了，用户该看到"请重新登录"而不是"加载失败"。
     */
    @Test
    fun `状态码到结果的映射`() {
        assertEquals(SecondClassResult.Ok("body"), httpResult(200, "body"))
        assertEquals(SecondClassResult.Ok(""), httpResult(204, ""))
        assertEquals(SecondClassResult.Unauthorized, httpResult(401, ""))
        assertEquals(SecondClassResult.Unauthorized, httpResult(403, ""))
        assertEquals(SecondClassResult.Failed(500, "HTTP 500"), httpResult(500, "boom"))
    }
}
