package com.pocket.xiaoli.data.library

import android.content.Context
import com.pocket.xiaoli.data.SelfHost
import com.pocket.xiaoli.network.ApkArtifact
import com.pocket.xiaoli.network.ApkDownloader
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.Request

/**
 * 资料站的数据门面。
 *
 * ## 链路（与更新链同构，只是文件换成课件）
 *
 * ```
 * library/index.json  ← 自建 / Gitee raw(main) / Gitee raw(master) / GitHub raw
 * Release 附件        ← 每份资料一个 URL，按 urls 顺序换源
 * ```
 *
 * ## 为什么先吐缓存再联网
 *
 * 与 [com.pocket.xiaoli.data.announcement.AppFeedRepository] 同一条纪律：索引缓存是**本地事实**，
 * 进页面必须立刻有东西可看；联网只是去校正它。所以 [state] 的初值来自缓存/内置资源，
 * 联网成功才替换。
 *
 * ## 为什么坏索引不覆盖好缓存
 *
 * 曾经在更新链上踩过：镜像返回旧数据把本地缓存覆盖掉，故障一直续到用户清缓存。
 * 这里用同一道闸：解析失败 → 保留缓存；`generatedAt` 更旧 → 保留缓存。
 */
class LibraryRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {

    data class State(
        val loading: Boolean = false,
        val files: List<LibraryFile> = emptyList(),
        val docs: List<LibraryDoc> = emptyList(),
        val generatedAt: String = "",
        val checkedAtMillis: Long = 0L,
        val error: String? = null,
        /** 这份索引是从网上拉的（true）还是缓存/内置（false）。 */
        val fromNetwork: Boolean = false,
    ) {
        val isEmpty: Boolean get() = files.isEmpty() && docs.isEmpty()
        val index: LibraryIndex get() = LibraryIndex(generatedAt, files, docs)
    }

    private val _state = MutableStateFlow(readInitial())
    val state: StateFlow<State> = _state.asStateFlow()

    /** 缓存文件：`filesDir/library/index.json`。放 filesDir 不放进 cacheDir —— 系统清缓存不该清掉索引。 */
    private fun cacheFile(): File = File(context.filesDir, "library/index.json")

    /** 下载落点：`cacheDir/library/`。FileProvider 的 `cache-path` 已经覆盖这里，可以直接分享/打开。 */
    fun downloadDir(): File = File(context.cacheDir, "library").apply { mkdirs() }

    suspend fun refresh(force: Boolean = false): State {
        val current = _state.value
        if (!force && !current.isEmpty && !isStale(current.checkedAtMillis)) return current
        _state.value = current.copy(loading = true, error = null)
        val fetched = withContext(Dispatchers.IO) { fetchIndex() }
        val next: State = if (fetched == null) {
            current.copy(loading = false, error = "没能连上资料库，先显示上次的索引")
        } else {
            val (index, fromNetwork) = fetched
            val rejected = acceptReason(index, current)
            if (rejected != null) {
                current.copy(loading = false, error = rejected)
            } else {
                // 只有被接受的索引才写缓存：坏/空/旧的索引不许把好缓存冲掉，
                // 否则故障会一直续到用户清数据（更新链上踩过这个坑）。
                runCatching {
                    cacheFile().parentFile?.mkdirs()
                    cacheFile().writeText(rawOf(index), Charsets.UTF_8)
                }
                State(
                    loading = false,
                    files = index.files,
                    docs = index.docs,
                    generatedAt = index.generatedAt,
                    checkedAtMillis = System.currentTimeMillis(),
                    error = null,
                    fromNetwork = fromNetwork,
                )
            }
        }
        _state.value = next
        return next
    }

    private fun isStale(checkedAt: Long): Boolean =
        checkedAt <= 0L || System.currentTimeMillis() - checkedAt > TTL_MILLIS

    /** 读缓存；缓存缺失或坏掉就退回打包进 assets 的空索引。 */
    private fun readInitial(): State {
        val cached = runCatching {
            cacheFile().takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }.getOrNull()
        val fromCache = cached?.let { LibraryIndex.fromJson(it) }
        if (fromCache != null) {
            return State(files = fromCache.files, docs = fromCache.docs, generatedAt = fromCache.generatedAt, fromNetwork = false)
        }
        val bundled = runCatching {
            context.assets.open("library/index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()?.let { LibraryIndex.fromJson(it) } ?: LibraryIndex.EMPTY
        return State(files = bundled.files, docs = bundled.docs, generatedAt = bundled.generatedAt)
    }

    /** 拉索引：返回 (索引, 是否来自网络)；全挂返回 null。 */
    private suspend fun fetchIndex(): Pair<LibraryIndex, Boolean>? {
        for (url in indexUrls()) {
            val parsed = runCatching {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let(LibraryIndex::fromJson)
                }
            }.getOrNull()
            if (parsed != null) {
                return parsed to true
            }
        }
        return null
    }


    /**
     * 缓存写的就是解析后的规范形态，省得再存一份原文。
     *
     * ⚠️ 手写字符串拼接在这里是错的：`docs[].body` 一定有换行，手写的 `escape()`
     * 只处理引号与反斜杠，写出来的 JSON 会坏掉 —— 下次启动读缓存就解析失败，
     * 于是"索引看起来每天都在退回到内置空索引"。所以用 [JSONObject] 序列化。
     */
    private fun rawOf(index: LibraryIndex): String {
        val filesJson = JSONArray()
        index.files.forEach { file ->
            filesJson.put(
                JSONObject()
                    .put("id", file.id)
                    .put("name", file.name)
                    .put("subject", file.subject)
                    .put("course", file.course)
                    .put("kind", file.kind)
                    .put("sizeBytes", file.sizeBytes)
                    .put("sha256", file.sha256.orEmpty())
                    .put("updatedAt", file.updatedAt)
                    .put("urls", JSONArray(file.urls))
                    .put("tags", JSONArray(file.tags))
                    .put("summary", file.summary)
                    .put("textRef", file.textRef)
                    .put("provider", file.provider),
            )
        }
        val docsJson = JSONArray()
        index.docs.forEach { doc ->
            docsJson.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("title", doc.title)
                    .put("summary", doc.summary)
                    .put("body", doc.body)
                    .put("subject", doc.subject)
                    .put("course", doc.course)
                    .put("kind", doc.kind)
                    .put("tags", JSONArray(doc.tags))
                    .put("updatedAt", doc.updatedAt)
                    .put("provider", doc.provider),
            )
        }
        return JSONObject()
            .put("generatedAt", index.generatedAt)
            .put("files", filesJson)
            .put("docs", docsJson)
            .toString()
    }

    private fun indexUrls(): List<String> = SelfHost.libraryIndexUrls(MIRRORS)

    /**
     * 下载一份资料：按 [LibraryFile.urls] 顺序换源，下完校验大小与 SHA-256。
     *
     * @return 成功时给本地文件，失败时给一句能贴到界面上的人话。
     */
    suspend fun download(
        file: LibraryFile,
        onProgress: (Int) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!file.downloadable) return@withContext Result.failure(IllegalStateException("这份资料还没上传"))
        val target = File(downloadDir(), file.name.replace('/', '_'))
        // 已经下过且校验通过就别再下一遍。
        if (target.isFile && verify(target, file) == null) return@withContext Result.success(target)
        var lastError = "下载失败"
        val downloader = ApkDownloader(client)
        for (url in file.urls) {
            // 资料站只吃百分比：total 未知（percent = null）时不转发，与升级前的行为一致
            val error = downloader.download(url, target) { progress ->
                progress.percent?.let(onProgress)
            }
            if (error != null) {
                lastError = error
                continue
            }
            val bad = verify(target, file)
            if (bad == null) return@withContext Result.success(target)
            lastError = bad
            target.delete()
        }
        Result.failure(IllegalStateException(lastError))
    }

    /** 校验：大小（索引里写了才查）与 SHA-256（写了才查）。返回 null = 通过。 */
    fun verify(target: File, file: LibraryFile): String? {
        if (!target.isFile) return "文件没下完整"
        if (file.sizeBytes > 0L && target.length() != file.sizeBytes) {
            return "文件大小不对（${target.length()} 字节，应为 ${file.sizeBytes}）"
        }
        val expected = ApkArtifact.normalizeSha256(file.sha256)
        if (expected.isNotEmpty()) {
            val actual = ApkArtifact.normalizeSha256(ApkArtifact.sha256(target))
            if (actual != expected) return "文件校验不通过，可能下坏了"
        }
        return null
    }

    companion object {

        /**
         * 这份拉下来的索引能不能替换当前状态？返回 null = 可以，否则返回一句要显示给人看的理由。
         *
         * 两道闸：①空索引不清库（镜像还没同步时最常见）；②`generatedAt` 更旧的不用
         * （镜像边缘缓存会吐上一版索引）。
         *
         * 放 companion 是为了脱离 Context 单测。
         */
        internal fun acceptReason(incoming: LibraryIndex, current: State): String? {
            if (incoming.isEmpty && current.files.isNotEmpty()) {
                return "资料库暂时是空的，先显示上次的索引"
            }
            val old = incoming.generatedAt
            val now = current.generatedAt
            if (old.isNotEmpty() && now.isNotEmpty() && old < now) {
                return "镜像还在返回旧索引（$old < $now），本次不覆盖本地记录"
            }
            return null
        }

        /** 索引 30 分钟过期，与更新链的 TTL 一致。 */
        internal const val TTL_MILLIS = 30L * 60L * 1000L

        /**
         * 索引镜像链。
         *
         * ⚠️ `main` 必须在 `master` 前面 —— Gitee 上这两个分支会分叉，只推 `main` 时
         * `raw/master` 会一直吐旧版本（见 `memory/03-pitfalls.md` 第十节）。
         * 故意**不带** `?t=` 破缓存参数：索引不是发版数据，晚一分钟没关系，省一次回源。
         */
        internal val MIRRORS = listOf(
            "https://gitee.com/liulin62633/pocket-carp/raw/main/library/index.json",
            "https://gitee.com/liulin62633/pocket-carp/raw/master/library/index.json",
            "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/library/index.json",
        )
    }
}
