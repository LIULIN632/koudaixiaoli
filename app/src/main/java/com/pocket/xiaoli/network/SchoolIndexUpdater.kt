package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.SelfHost
import android.content.Context
import com.pocket.xiaoli.data.SchoolJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 学校索引（JSON）热更新——对齐时光课表 GitUpdater 的经验，用 OkHttp 简化实现：
 * - 多镜像回退：国内网络可达性差异大（jsDelivr 常被墙），按序尝试多个镜像，第一个 200 生效
 * - 协议版本校验：远程 `protocol_version` > 客户端 → 忽略（需升级 App）
 * - 数据版本校验：`version_id` 用 `TIME_YYYYMMDDHHMMSS_XXX` 时间戳字符串字典序比较；
 *   远程更旧视为数据异常，拒绝写入（防回退）
 * - 延迟写入：先下载到内存、校验通过再写入 filesDir（临时文件 + rename 原子替换），
 *   半成品/损坏索引不会落盘
 *
 * 索引由 tools/generate_school_index.ps1 生成，提交到 GitHub 仓库默认分支（master）后经镜像分发。
 */
class SchoolIndexUpdater(context: Context) {

    companion object {
        const val CLIENT_PROTOCOL_VERSION = 1

        /**
         * Gitee 镜像（国内直连免代理可达）——回退链首位。
         * 仓库：https://gitee.com/liulin62633/pocket-carp （公开仓库，仅存 school_index.json）
         * 同时列 master/main：分支名不符时是快速 404，不会浪费超时。
         *
         * ⚠️ 2026-10-14 已落地：Gitee `liulin62633/pocket-carp`（master）、
         * GitHub `LIULIN632/koudaixiaoli`（**默认分支是 main**）。
         * 实测旧名 `PocketXiaoliTimetable` 全部 404 —— 也就是热更新曾经整条断链、
         * App 一直靠内置 assets 兜底。自建地址（`SelfHost.BASE_URL`）不受影响。
         */
        private val GITEE_MIRRORS = listOf(
            "https://gitee.com/liulin62633/pocket-carp/raw/main/school_index.json",
            "https://gitee.com/liulin62633/pocket-carp/raw/master/school_index.json",
        )

        /**
         * 多镜像回退链（注意：GitHub 仓库默认分支是 main，Gitee 是 master）。
         *
         * 自建服务器（`SelfHost.BASE_URL`）配置后**插在最前面**，其余一个不动：
         * 自家可达时走自家，自家挂了照旧回落到 Gitee / GitHub / jsDelivr ——
         * 这也是"自建"不会把 App 弄瞎的原因。
         */
        val INDEX_URLS: List<String> = SelfHost.indexUrls(
            buildList {
                addAll(GITEE_MIRRORS)
                add("https://raw.githubusercontent.com/LIULIN632/koudaixiaoli/main/school_index.json")
                add("https://cdn.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/school_index.json")
                add("https://fastly.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/school_index.json")
                add("https://gcore.jsdelivr.net/gh/LIULIN632/koudaixiaoli@main/school_index.json")
            }
        )

        private const val INDEX_DIR = "school_index"
        private const val INDEX_FILE = "school_index.json"
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .addInterceptor(NetworkLogInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    sealed class Result {
        data class Updated(val versionId: String, val schoolCount: Int) : Result()
        data object UpToDate : Result()
        data class Failed(val message: String) : Result()
    }

    /** 拉取并校验远程索引，通过后原子写入本地（阻塞调用，放 IO 线程） */
    fun update(): Result {
        // 多镜像按序尝试，第一个拿到有效响应的生效
        var lastError = "无法连接索引服务器"
        for (url in INDEX_URLS) {
            val json = try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "网络错误（HTTP ${resp.code}）"
                        null
                    } else resp.body?.string()
                }
            } catch (e: Exception) {
                lastError = "无法连接索引服务器"
                null
            }
            if (json != null) return writeIfNewer(json)
        }
        return Result.Failed(lastError)
    }

    /** 解析 + 版本校验 + 延迟写入 */
    private fun writeIfNewer(remoteJson: String): Result {
        val remote = runCatching { Gson().fromJson(remoteJson, SchoolIndex::class.java) }.getOrNull()
            ?: return Result.Failed("索引数据解析失败")

        when (val decision = evaluateIndex(remote, loadLocalIndex())) {
            is IndexDecision.Rejected -> return Result.Failed(decision.message)
            IndexDecision.UpToDate -> return Result.UpToDate
            IndexDecision.Apply -> Unit
        }

        // 延迟写入：临时文件 + rename 原子替换
        return try {
            val dir = File(appContext.filesDir, INDEX_DIR)
            dir.mkdirs()
            val tmp = File(dir, "tmp_${System.currentTimeMillis()}.json")
            tmp.writeText(remoteJson)
            val target = File(dir, INDEX_FILE)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return Result.Failed("索引写入失败")
                }
            }
            val count = remote.schools.values.sumOf { it.size }
            Result.Updated(remote.versionId, count)
        } catch (e: Exception) {
            Result.Failed("索引写入失败：${e.message ?: "未知错误"}")
        }
    }

    /** 读取本地已生效的索引（无则返回 null；结构性损坏的文件自愈删除） */
    fun loadLocalIndex(): SchoolIndex? {
        val f = File(File(appContext.filesDir, INDEX_DIR), INDEX_FILE)
        if (!f.exists()) return null
        return try {
            val idx = Gson().fromJson(f.readText(), SchoolIndex::class.java)
            // Gson 绕过 Kotlin 空安全，缺字段会塞 null（声明为非空也挡不住）→ 用可空中转做防御
            val schools: Map<String, List<SchoolJson>>? = idx.schools
            if (schools == null) {
                android.util.Log.w("SchoolIndexUpdater", "本地索引结构损坏(schools=null)，已删除待重新拉取")
                f.delete()
                null
            } else idx
        } catch (e: Exception) {
            android.util.Log.w("SchoolIndexUpdater", "本地索引解析失败，已删除: ${e.message}")
            f.delete()
            null
        }
    }
}

/** 索引更新决策（纯逻辑，可单测）：协议版本 / 时间戳版本 / 防回退 */
internal sealed class IndexDecision {
    /** 通过校验，可写入 */
    data object Apply : IndexDecision()
    /** 已是最新，无需写入 */
    data object UpToDate : IndexDecision()
    /** 拒绝写入（协议过高或远程更旧），附原因 */
    data class Rejected(val message: String) : IndexDecision()
}

internal fun evaluateIndex(remote: SchoolIndex, local: SchoolIndex?): IndexDecision {
    // A. 协议版本：远程 > 客户端 → 忽略（需升级 App）
    if (remote.protocolVersion > SchoolIndexUpdater.CLIENT_PROTOCOL_VERSION) {
        return IndexDecision.Rejected("索引协议版本过高，请更新 App 后再试")
    }
    // B. 数据版本（TIME_ 时间戳字符串字典序比较；远程更旧 = 数据异常，拒绝写入防回退）
    val localVersion = local?.versionId
    if (localVersion != null && remote.versionId == localVersion) return IndexDecision.UpToDate
    if (localVersion != null && remote.versionId < localVersion) {
        return IndexDecision.Rejected("远程索引版本过旧，已忽略")
    }
    return IndexDecision.Apply
}

/** 学校索引清单（与 tools/generate_school_index.ps1 输出一致） */
data class SchoolIndex(
    @SerializedName("protocol_version") val protocolVersion: Int = 1,
    @SerializedName("version_id") val versionId: String = "",
    val schools: Map<String, List<SchoolJson>> = emptyMap()
)
