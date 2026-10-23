package com.pocket.xiaoli.data.knowledge

import android.content.Context
import com.pocket.xiaoli.data.SelfHost
import com.pocket.xiaoli.network.NetworkLogInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 校园知识库热更新 —— 结构照抄 `SchoolIndexUpdater`，但**刻意不复用它**：
 * 那个类属于 B 轨道导入引擎，`CLAUDE.md` 明确「要动它之前必须先确认产品意图已变」。
 * 这里接受一份结构重复，换零回归风险（设计文档决策记录 D6）。
 *
 * 四条与它一致的机制：
 * - 多镜像回退：按序尝试，第一个 200 生效
 * - 协议版本校验：远程 `protocol_version` 高于客户端 → 拒绝（需升级 App）
 * - 数据版本校验：`version_id` 字典序；远程更旧视为异常，拒绝写入（防回退）
 * - 延迟写入：先下载校验、再写临时文件 + rename 原子替换，半成品不落盘
 *
 * ⚠️ **一处与它不同的关键点**：比较基线由调用方传入，而调用方给的是
 * 「缓存与内置 assets 的较大者」（`chooseKnowledgeIndex`）。
 * `SchoolIndexUpdater` 只看 filesDir 缓存 —— 照抄的话，第一次更新时缓存为空就直接写入，
 * 万一镜像停在旧版本而 App 内置的 assets 是新的，就会把新内容**降级覆盖**。
 */
class KnowledgeUpdater(context: Context) {

    companion object {
        private const val DIR = "knowledge"
        private const val FILE = "knowledge.json"

        /**
         * 多镜像回退链。自建服务器（`SelfHost.BASE_URL`）配置后**插在最前面**，
         * 其余一个不动 —— 与学校索引同一条规则：不是替换，是加一层。
         *
         * 与 `SchoolIndexUpdater.INDEX_URLS` 分开维护：两者协议版本与升级节奏无关。
         *
         * ⚠️ 2026-10-14 仓库已落地：Gitee `pocket-carp`（master）/ GitHub `koudaixiaoli`（main）。
         * 旧名 `PocketXiaoliTimetable` 实测 404（详见 `SchoolIndexUpdater.GITEE_MIRRORS` 同一段说明）。
         */
        val URLS: List<String> = SelfHost.knowledgeUrls(
            listOf(
                "https://gitee.com/liulin62633/pocket-carp/raw/main/knowledge.json",
                "https://gitee.com/liulin62633/pocket-carp/raw/master/knowledge.json",
                "https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/knowledge.json",
                "https://cdn.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
                "https://fastly.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
                "https://gcore.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/knowledge.json",
            ),
        )
    }

    private val appContext = context.applicationContext

    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Updated(val versionId: String, val docCount: Int) : Result()
        data object UpToDate : Result()
        data class Failed(val message: String) : Result()
    }

    /** 拉取并校验远程知识库，通过后原子写入本地。阻塞调用，放 IO 线程。 */
    fun update(baseline: KnowledgeIndexJson?): Result {
        var lastError = "无法连接知识库服务器"

        for (url in URLS) {
            val raw = try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        lastError = "网络错误（HTTP ${response.code}）"
                        null
                    }
                }
            } catch (e: Exception) {
                lastError = "无法连接知识库服务器"
                null
            }
            if (raw == null) continue

            // 镜像返回垃圾（HTML 错误页、被劫持的响应）不算"拿到了数据"，继续试下一个
            val remote = parseKnowledgeIndex(raw) ?: continue
            return writeIfNewer(remote, raw, baseline)
        }
        return Result.Failed(lastError)
    }

    /** 解析 + 版本校验 + 延迟写入。 */
    private fun writeIfNewer(
        remote: KnowledgeIndexJson,
        raw: String,
        baseline: KnowledgeIndexJson?,
    ): Result {
        when (val decision = evaluateKnowledgeIndex(remote, baseline)) {
            is KnowledgeDecision.Rejected -> return Result.Failed(decision.message)
            KnowledgeDecision.UpToDate -> return Result.UpToDate
            KnowledgeDecision.Apply -> Unit
        }

        // 延迟写入：临时文件 + rename 原子替换
        return try {
            val dir = File(appContext.filesDir, DIR)
            dir.mkdirs()
            val tmp = File(dir, "tmp_${System.currentTimeMillis()}.json")
            tmp.writeText(raw)
            val target = File(dir, FILE)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return Result.Failed("知识库写入失败")
                }
            }
            Result.Updated(remote.versionId, remote.docs.size)
        } catch (e: Exception) {
            Result.Failed("知识库写入失败：${e.message ?: "未知错误"}")
        }
    }
}
