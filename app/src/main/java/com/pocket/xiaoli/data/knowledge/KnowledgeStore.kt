package com.pocket.xiaoli.data.knowledge

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 校园知识库的加载入口：惰性加载一次，缓存与内置 assets 择优。
 *
 * ## 为什么必须是 `suspend fun ensureLoaded()`，而不能让调用方读"当前值"
 *
 * 冷启动 → 用户直接进鲤鱼提问时，异步加载还没完成。如果工具读的是
 * `StateFlow.value` 之类，那一刻拿到的是空列表，鲤鱼会一本正经地回答
 * 「知识库暂无内容」—— 用户会以为知识库是空的，而不是"还没加载完"。
 * 所以对外只暴露挂起函数，调用方**被迫等待**。
 *
 * ## 这里为什么不做时效过滤
 *
 * 过滤依赖"今天是哪天"，而 Android 进程可以存活数天。加载时过滤一次会导致
 * 跨天之后仍然把过期通知当现行安排答出来。所以本类只存**原始文档**，
 * 过滤交给 `ExpiryPolicy.apply(documents, LocalDate.now())` 在**每次检索时**执行。
 * 详见设计文档 §6.1。
 */
class KnowledgeStore(private val context: Context) {

    companion object {
        /** 内置的兜底数据（随包发布，离线首启可用）。 */
        const val ASSET_NAME = "knowledge.json"

        private const val TAG = "KnowledgeStore"
        private const val DIR = "knowledge"
        private const val FILE = "knowledge.json"

        /** 远程检查的最小间隔：24 小时。 */
        const val CHECK_INTERVAL_MS: Long = 24 * 60 * 60 * 1000L

        private const val PREFS = "knowledge_store"
        private const val KEY_LAST_CHECK = "last_check_at"
    }

    private val mutex = Mutex()

    @Volatile
    private var loaded: List<KnowledgeDocument>? = null

    /** 加载知识库（未做时效过滤）。并发调用只会真正加载一次。 */
    suspend fun ensureLoaded(): List<KnowledgeDocument> {
        loaded?.let { return it }
        return mutex.withLock {
            loaded?.let { return@withLock it }
            val documents = withContext(Dispatchers.IO) {
                loadIndex()?.let(KnowledgeJsonMapper::toDocuments).orEmpty()
            }
            loaded = documents
            documents
        }
    }

    /**
     * 择优：缓存与内置 assets 里 `version_id` 更大的那个。
     *
     * 为什么不能"有缓存就用缓存"：镜像可能停在旧版本，而 App 内置的
     * `assets/knowledge.json` 是随包发布的新版本 —— 直接采信缓存会把新内容**降级**。
     */
    fun loadIndex(): KnowledgeIndexJson? =
        chooseKnowledgeIndex(cached = readCache(), asset = readAsset())

    /** 读内置 assets。读不到或格式坏掉都返回 `null`（由调用方回落到另一份）。 */
    fun readAsset(): KnowledgeIndexJson? {
        val raw = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrNull()
        return parseKnowledgeIndex(raw)
    }

    /**
     * 读本地缓存。**结构性损坏的文件自愈删除** —— 否则每次冷启动都会重新踩同一个坑。
     */
    fun readCache(): KnowledgeIndexJson? {
        val file = cacheFile()
        if (!file.exists()) return null

        val parsed = parseKnowledgeIndex(runCatching { file.readText() }.getOrNull())
        if (parsed == null) {
            runCatching { file.delete() }
            Log.w(TAG, "本地知识库缓存损坏，已删除待重新拉取")
        }
        return parsed
    }

    /** 缓存文件位置。必须与 `KnowledgeUpdater` 的写入位置一致。 */
    fun cacheFile(): File = File(File(context.filesDir, DIR), FILE)

    /**
     * 距上次检查超过 [CHECK_INTERVAL_MS] 才真正去拉一次；失败静默。
     *
     * 节流是必要的：否则每次冷启动都发请求，离线用户每次都要白等 8 秒连接超时。
     *
     * 时间戳**在拉取之前**就记下来：即使这次失败，24 小时内也不会反复重试打网络。
     *
     * @return 真的拿到了新版本并写入缓存时为 `true`
     */
    suspend fun refreshIfStale(
        updater: KnowledgeUpdater,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return false
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        // 基线是「缓存与内置的较大者」，不是「缓存」—— 见 KnowledgeUpdater 的类注释
        val result = withContext(Dispatchers.IO) { updater.update(baseline = loadIndex()) }
        return when (result) {
            is KnowledgeUpdater.Result.Updated -> {
                invalidate()
                true
            }
            else -> false
        }
    }

    /** 使已加载的内容失效（远程更新写入新缓存后调用）。 */
    internal fun invalidate() {
        loaded = null
    }
}
