package com.pocket.xiaoli.data.courseware

import android.content.Context
import com.pocket.xiaoli.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** 服务页那一格的状态：要不要下载、下到几成、为什么失败。 */
data class CoursewareRow(
    val ref: CoursewareRef,
    val installedVersion: Int = 0,
    val progress: Int = -1,
    val error: String? = null,
) {
    val installed: Boolean get() = ref.isBuiltin || installedVersion > 0
    val busy: Boolean get() = progress in 0..99
    val needsUpdate: Boolean get() = ref.remote != null && installedVersion in 1 until (ref.remote.version)
}

data class CoursewareState(
    val rows: List<CoursewareRow> = emptyList(),
    val refreshing: Boolean = false,
    val checkedAt: Long = 0L,
    /** 清单拉不到时的一句话原因（不显示给普通用户当错误，只作页脚说明）。 */
    val note: String? = null,
)

/**
 * 课件的"目录 + 下载"门面。**服务页只跟它打交道**，不自己拼 URL、不自己解压。
 *
 * ## 清单从哪来（和 feed 一样的思路）
 *
 * `https://gitee.com/liulin62633/pocket-carp-courseware/raw/master/courseware/index.json`
 * 为主，`main` 与 GitHub raw 兜底；6 小时 TTL，下拉/进页可强制刷新。
 * 拉不到就用本地缓存的上一份 —— 已经下好的课件照常能打开（它们的文件在磁盘上，
 * 跟清单是否新鲜无关），这正是"没网也能上课"的底线。
 *
 * ## 为什么不写后台轮询
 *
 * 课件不是每日必看的东西，为它常驻一个 WorkManager 任务不值当：进服务页时拉一次、
 * 打开课件时确认一次，就够了。省电也省服务端配额。
 */
object CoursewareRepository {

    private const val TTL_MS = 6 * 60 * 60 * 1000L
    private const val TOTAL_BUDGET_BYTES = 300L * 1024 * 1024

    private val MIRRORS = listOf(
        "https://gitee.com/liulin62633/pocket-carp-courseware/raw/master/courseware/index.json",
        "https://gitee.com/liulin62633/pocket-carp-courseware/raw/main/courseware/index.json",
        "https://raw.githubusercontent.com/LIULIN632/pocket-carp-courseware/main/courseware/index.json",
    )

    private val _state = MutableStateFlow(CoursewareState(rows = builtinRows()))
    val state: StateFlow<CoursewareState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var store: CoursewareStore? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        store = CoursewareStore(app)
        val cached = store?.let { it.manifestText()?.let { text -> CoursewareManifestParser.parse(text) } }
        _state.value = buildState(cached, store?.manifestAt() ?: 0L, note = null)
    }

    /**
     * 拉一次清单。[force] 时绕过 TTL、并给 URL 加时间戳（Gitee raw 有 60 秒 CDN 缓存）。
     * 失败**不抛**：保留上一份清单，只在 [CoursewareState.note] 里留一句原因。
     */
    suspend fun refresh(force: Boolean = false): CoursewareState = withContext(Dispatchers.IO) {
        val store = store ?: return@withContext _state.value
        val fresh = System.currentTimeMillis() - store.manifestAt() < TTL_MS
        if (!force && fresh && _state.value.note == null) return@withContext _state.value

        _state.value = _state.value.copy(refreshing = true)
        val text = fetchManifestText(bustCache = force)
        val manifest = CoursewareManifestParser.parse(text)
        if (manifest == null) {
            _state.value = _state.value.copy(refreshing = false, note = "清单暂时拉不到，显示的是上次的结果")
            return@withContext _state.value
        }
        val now = System.currentTimeMillis()
        store.saveManifest(text!!, now)
        _state.value = buildState(manifest, now, note = null).copy(refreshing = false)
        _state.value
    }

    private fun fetchManifestText(bustCache: Boolean): String? {
        for (url in MIRRORS) {
            val target = if (!bustCache) url else "$url?t=${System.currentTimeMillis()}"
            val request = Request.Builder().url(target)
                .header("User-Agent", "PocketXiaoli-Android/${BuildConfig.VERSION_NAME}")
                .build()
            val text = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull()
            if (!text.isNullOrBlank() && text.contains("\"courses\"")) return text
        }
        return null
    }

    /** 下载并解压某个课件（含它依赖的 shared 包）。失败返回原因，不抛异常。 */
    suspend fun install(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val store = store ?: return@withContext Result.failure(IllegalStateException("未初始化"))
        val root = coursewareRoot(appContext ?: return@withContext Result.failure(IllegalStateException("未初始化")))
        val pkg = packages().firstOrNull { it.id == id }
            ?: return@withContext Result.failure(IllegalStateException("清单里没有这个课件"))

        try {
            for (dependency in pkg.requires) {
                val dep = packages().firstOrNull { it.id == dependency }
                    ?: return@withContext Result.failure(IllegalStateException("缺少依赖 $dependency"))
                if (!installedOk(store, root, dep)) {
                    val result = downloadAndUnzip(store, root, dep, isDependency = true)
                    if (result.isFailure) return@withContext result
                }
            }
            if (installedOk(store, root, pkg)) {
                store.touch(pkg.id)
                updateRow(pkg.id) { it.copy(installedVersion = pkg.version, progress = -1, error = null) }
                return@withContext Result.success(Unit)
            }
            val result = downloadAndUnzip(store, root, pkg, isDependency = false)
            if (result.isFailure) return@withContext result

            val removed = store.evict(TOTAL_BUDGET_BYTES, protect = setOf(pkg.id) + pkg.requires)
            removed.forEach { removedId ->
                runCatching { CoursewareDownloader.targetDir(root, removedId).deleteRecursively() }
            }
            _state.value = buildState(manifest(), store.manifestAt(), note = _state.value.note)
            Result.success(Unit)
        } catch (c: CancellationException) {
            updateRow(id) { it.copy(progress = -1) }
            throw c
        } catch (t: Throwable) {
            updateRow(id) { it.copy(progress = -1, error = t.javaClass.simpleName) }
            Result.failure(t)
        }
    }

    fun manifest(): CoursewareManifest? =
        store?.manifestText()?.let { CoursewareManifestParser.parse(it) }

    fun packages(): List<CoursewarePackage> = manifest()?.packages.orEmpty()

    fun isInstalled(id: String): Boolean {
        val store = store ?: return false
        val root = appContext?.let { coursewareRoot(it) } ?: return false
        val pkg = packages().firstOrNull { it.id == id }
        return if (pkg != null) installedOk(store, root, pkg) else false
    }

    // ── 内部 ───────────────────────────────────────────────────────────────

    private suspend fun downloadAndUnzip(
        store: CoursewareStore,
        root: File,
        pkg: CoursewarePackage,
        isDependency: Boolean,
    ): Result<Unit> {
        if (!isDependency) updateRow(pkg.id) { it.copy(progress = 0, error = null) }
        val zip = File(root.parentFile, "${pkg.id}.part.zip")
        var lastError = "下载失败"

        for (url in pkg.urls) {
            val ok = runCatching { download(url, zip, pkg.size) { percent ->
                if (!isDependency) updateRow(pkg.id) { it.copy(progress = percent) }
            } }.getOrElse { t ->
                lastError = t.javaClass.simpleName
                false
            }
            if (!ok) continue

            if (pkg.size > 0 && zip.length() != pkg.size) {
                lastError = "大小不符"
                zip.delete()
                continue
            }
            if (pkg.sha256.isNotEmpty() && CoursewareDownloader.sha256(zip) != pkg.sha256) {
                lastError = "校验失败"
                zip.delete()
                continue
            }
                        val problem = CoursewareDownloader.unzip(
                zip = zip,
                dest = CoursewareDownloader.targetDir(root, pkg.id),
                requireIndex = !pkg.isShared,
            )
            zip.delete()
            if (problem != null) {
                lastError = problem
                continue
            }
            store.markInstalled(pkg.id, pkg.version, pkg.size)
            updateRow(pkg.id) { it.copy(installedVersion = pkg.version, progress = -1, error = null) }
            return Result.success(Unit)
        }
        updateRow(pkg.id) { it.copy(progress = -1, error = lastError) }
        return Result.failure(IllegalStateException(lastError))
    }

    private fun download(url: String, target: File, expectedSize: Long, onProgress: (Int) -> Unit): Boolean {
        val request = Request.Builder().url(url)
            .header("User-Agent", "PocketXiaoli-Android/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
            if (total > CoursewareDownloader.MAX_ZIP_BYTES) return false
            target.delete()
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read * 100 / total).toInt().coerceIn(0, 99))
                    }
                }
            }
        }
        return true
    }

    private fun installedOk(store: CoursewareStore, root: File, pkg: CoursewarePackage): Boolean {
        val entry = store.installedOf(pkg.id) ?: return false
        if (entry.version != pkg.version) return false
        val dir = CoursewareDownloader.targetDir(root, pkg.id)
        // shared 包（vendor）没有 index.html，判据降级为"目录在且非空"
        return if (pkg.isShared) dir.isDirectory && (dir.list()?.isNotEmpty() == true) else File(dir, "index.html").isFile
    }

    private fun updateRow(id: String, transform: (CoursewareRow) -> CoursewareRow) {
        _state.value = _state.value.copy(
            rows = _state.value.rows.map { if (it.ref.id == id) transform(it) else it },
        )
    }

    private fun buildState(manifest: CoursewareManifest?, checkedAt: Long, note: String?): CoursewareState {
        val store = this.store
        val installed = store?.installed().orEmpty()
        val remote = manifest?.packages.orEmpty().filter { !it.isShared }
        val builtinIds = Courseware.entries.map { it.dir }.toSet()
        val rows = buildList {
            Courseware.entries.forEach { builtin ->
                // 内置课件也可能有远端新版本：这时 ref 同时带 builtin 与 remote，
                // UI 会显示"有新版本，点一下更新"，下载后同名目录覆盖内置那份的读取优先级。
                val upgrade = remote.firstOrNull { it.id == builtin.dir }
                add(
                    CoursewareRow(
                        ref = upgrade?.let { CoursewareRef.of(builtin).copy(remote = it) }
                            ?: CoursewareRef.of(builtin),
                        installedVersion = installed[builtin.dir]?.version ?: 0,
                    ),
                )
            }
            remote.filter { it.id !in builtinIds }.forEach { pkg ->
                add(
                    CoursewareRow(
                        ref = CoursewareRef.of(pkg),
                        installedVersion = installed[pkg.id]?.version ?: 0,
                    ),
                )
            }
        }
        return CoursewareState(rows = rows, checkedAt = checkedAt, note = note)
    }

    private fun builtinRows(): List<CoursewareRow> = Courseware.entries.map { CoursewareRow(CoursewareRef.of(it)) }
}
