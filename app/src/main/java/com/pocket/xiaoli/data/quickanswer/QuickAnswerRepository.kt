package com.pocket.xiaoli.data.quickanswer

import android.content.Context
import com.pocket.xiaoli.data.SelfHost
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 题库的读取门面：**内置题库先上，联网只做覆盖**。
 *
 * ## 为什么题库既打包又联网
 *
 * 打包是为了开箱能用、断网能用；联网是为了**加一道题不用发版**。
 * 题库属于「内容」不属于「功能」，跟着 App 版本走等于每加一章都要让全班更新一次。
 *
 * ## 覆盖规则
 *
 * 远端整份替换，但**只有解析成功且非空**才覆盖 —— 与资料站索引同一条纪律：
 * 镜像挂掉返回一份半截 JSON，不能把本地好题库清空。
 */
class QuickAnswerRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {

    data class State(
        val bank: QuickAnswerBank = QuickAnswerBank.EMPTY,
        val loading: Boolean = false,
        val error: String? = null,
        /** true = 当前题库来自网络/远端缓存；false = 来自打包资源。 */
        val fromRemote: Boolean = false,
    )

    private val _state = MutableStateFlow(State(bank = readBundled()))
    val state: StateFlow<State> = _state.asStateFlow()

    private fun cacheFile(): File = File(context.filesDir, "quickanswer/bank.json")

    private fun readBundled(): QuickAnswerBank = runCatching {
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrNull()?.let(QuickAnswerBank::fromJson) ?: QuickAnswerBank.EMPTY

    private fun readCache(): QuickAnswerBank? = runCatching {
        cacheFile().takeIf { it.isFile }?.readText(Charsets.UTF_8)
    }.getOrNull()?.let(QuickAnswerBank::fromJson)?.takeIf { !it.isEmpty }

    /**
     * 进页面时调一次：先把远端缓存（如果有）换上，再后台刷新。
     *
     * 首次启动的页面必须立刻有内容，所以缓存替换是**同步**的，网络是 [refresh] 的事。
     */
    fun applyCache(): State {
        val cached = readCache() ?: return _state.value
        val next = _state.value.copy(bank = cached, fromRemote = true)
        _state.value = next
        return next
    }

    suspend fun refresh(force: Boolean = false): State {
        val current = _state.value
        if (!force && current.loading) return current
        _state.value = current.copy(loading = true, error = null)
        val fetched = withContext(Dispatchers.IO) { fetch() }
        val next = when {
            fetched == null -> current.copy(loading = false, error = "题库没更新成功，先用手里的这份")
            else -> {
                runCatching {
                    cacheFile().parentFile?.mkdirs()
                    cacheFile().writeText(fetched.raw, Charsets.UTF_8)
                }
                State(bank = fetched.bank, loading = false, error = null, fromRemote = true)
            }
        }
        _state.value = next
        return next
    }

    private data class Fetched(val bank: QuickAnswerBank, val raw: String)

    private suspend fun fetch(): Fetched? {
        for (url in urls()) {
            val hit = runCatching {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val raw = response.body?.string() ?: return@use null
                    QuickAnswerBank.fromJson(raw)?.takeIf { !it.isEmpty }?.let { Fetched(it, raw) }
                }
            }.getOrNull()
            if (hit != null) return hit
        }
        return null
    }

    private fun urls(): List<String> =
        SelfHost.prepend(SelfHost.BASE_URL, "/index/quickanswer/bank.json", MIRRORS)

    companion object {
        private const val ASSET = "quickanswer/bank.json"

        /**
         * 题库镜像链。
         *
         * 复用 `knowledge` 那条自建路径是有意的：题库与知识库同属「作者维护的内容」，
         * 将来上自建服务器时一起搬，不用再改一遍代码。
         */
        internal val MIRRORS = listOf(
            "https://gitee.com/liulin62633/pocket-carp/raw/main/quickanswer/bank.json",
            "https://gitee.com/liulin62633/pocket-carp/raw/master/quickanswer/bank.json",
            "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/quickanswer/bank.json",
        )
    }
}
