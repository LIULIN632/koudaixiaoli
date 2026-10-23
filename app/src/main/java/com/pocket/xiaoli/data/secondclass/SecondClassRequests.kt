package com.pocket.xiaoli.data.secondclass

import okhttp3.FormBody
import okhttp3.Request

/**
 * 到梦空间请求的**构造**（纯函数，不发网络）。
 *
 * 为什么把它从 [SecondClassApi] 里单独拆出来：**它是唯一能在没有网络的情况下被断言的环节**
 * （本仓库没有 MockWebServer），而"发的是不是 POST"恰恰是本模块最容易静默出错的契约。
 * 拆出来之后它就有了真正的守卫（`SecondClassApiTest`），而不是靠开发者的记性。
 */
internal object SecondClassRequests {

    /**
     * 页面自己的 AJAX 带这个头（jQuery 默认行为），**我们也带**。
     *
     * ⚠️ 但**别把它当成必需的**。2026-10-10 的对照实测（§11.12）：
     *
     * | 请求 | 结果 |
     * |---|---|
     * | `GET /certificate/certificate/scorelist`（**不带**该头） | `text/html` —— **整页首页** |
     * | `POST /token/checkToken`（**不带**该头） | `application/json` —— 正常 JSON |
     * | 同 URL + 该头 | `application/json` —— 正常 JSON |
     *
     * 也就是说：**决定成败的是 method 是 POST，而不是这个头**。
     * （这里早先写着"少了这个头服务端会返回首页"—— 那是**拿 GET 的观察去解释 POST 的场景**，是错的。）
     *
     * 那为什么还带着？为了**与页面完全同形**：服务端、WAF 或中间层将来若开始看它，
     * 我们不必再改一次；而带着它没有任何代价。
     */
    const val HEADER_AJAX = "X-Requested-With"

    const val HEADER_AJAX_VALUE = "XMLHttpRequest"

    /**
     * 构造一个与页面完全同形的 POST：表单编码（`application/x-www-form-urlencoded`）。
     *
     * 与页面同形是有意义的：jQuery 的 `$.ajax({type:"POST", data:{…}})` 默认就是表单编码 +
     * 自动带 [HEADER_AJAX]，服务端与中间层（若有）看到的就是同一个请求。
     * 用 JSON body 去试是**没有依据的改动**，别为了"更现代"而改。
     *
     * [baseUrl] 末尾的 `/` 会被规整掉；[path] 不要以 `/` 开头（照 `g_const.path + "activity/…"` 的写法）。
     */
    fun formPost(baseUrl: String, path: String, params: Map<String, String> = emptyMap()): Request {
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        val body = FormBody.Builder().apply {
            params.forEach { (name, value) -> add(name, value) }
        }.build()
        return Request.Builder()
            .url(url)
            .header(HEADER_AJAX, HEADER_AJAX_VALUE)
            .post(body)
            .build()
    }
}
