package com.pocket.xiaoli.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 把每次 OkHttp 请求记进 [NetworkLogStore]。
 *
 * 装上之后**不影响请求行为**：业务异常照常抛出，只是顺手记了一条；
 * 记账本身出错也不会影响主流程（整个记录过程用 runCatching 包住）。
 */
object NetworkLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val start = System.currentTimeMillis()
        return try {
            val response = chain.proceed(request)
            runCatching {
                NetworkLogStore.record(
                    method = request.method,
                    url = request.url.toString(),
                    status = response.code,
                    durationMs = System.currentTimeMillis() - start,
                    requestBytes = request.body?.contentLength() ?: -1L,
                    responseBytes = response.body?.contentLength() ?: -1L,
                )
            }
            response
        } catch (e: Exception) {
            runCatching {
                NetworkLogStore.record(
                    method = request.method,
                    url = request.url.toString(),
                    status = null,
                    durationMs = System.currentTimeMillis() - start,
                    requestBytes = request.body?.contentLength() ?: -1L,
                    error = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""),
                )
            }
            throw e
        }
    }
}