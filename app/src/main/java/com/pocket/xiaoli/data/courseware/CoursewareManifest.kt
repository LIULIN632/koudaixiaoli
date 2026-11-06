package com.pocket.xiaoli.data.courseware

import com.google.gson.JsonParser

/**
 * 远端课件的清单模型 —— 与课件仓里的 `courseware/index.json` 一一对应。
 *
 * 清单是**唯一真源**：App 认哪几个课件、各自什么版本、从哪下载、校验值是多少，
 * 全部只从这里读。加一个课件不需要发版，删一个也不需要在客户端留墓碑 ——
 * 这也正是"课件不进 APK"的意义（3 个 3D 课件就能让包从 9.4 MB 涨到 30 MB）。
 *
 * @param size zip 的字节数。**下载前先校验它**：小于缓存里已有文件就说明清单和陈旧缓存打架，
 *   比事后算 sha256 便宜得多。
 * @param sha256 小写十六进制，下载完逐字节核对；不一致就删包重下，绝不"先解压再说"。
 * @param urls 按顺序试的直链（Gitee 主、GitHub 兜底）。全部失败就报错，不猜第三个地址。
 * @param kind `course` = 课件；`shared` = 公共资源（如 three.js 的 `vendor`），
 *   只下载一次、多个课件共用，避免每个包各带 730 KB。
 * @param requires 打开前必须先装好的 shared 包 id（通常是 `vendor`）。
 */
data class CoursewarePackage(
    val id: String,
    val title: String = "",
    val subject: String = "",
    val version: Int = 1,
    val size: Long = 0L,
    val sha256: String = "",
    val urls: List<String> = emptyList(),
    val kind: String = KIND_COURSE,
    val requires: List<String> = emptyList(),
) {
    val isShared: Boolean get() = kind == KIND_SHARED

    companion object {
        const val KIND_COURSE = "course"
        const val KIND_SHARED = "shared"
    }
}

/** 清单本体。`version` 只用于人工排查清单迭代，客户端不做版本比较。 */
data class CoursewareManifest(
    val version: Int = 0,
    val updatedAt: String = "",
    val packages: List<CoursewarePackage> = emptyList(),
)

/**
 * 清单解析。**任何一项不合格就整条丢掉，不整份失败** ——
 * 一条写错的条目不该让全部课件都消失（那会让用户以为"课件没了"）。
 *
 * 判据（都在 [CoursewareManifestTest] 里钉住）：
 * - `id` 必须匹配 `[a-z0-9][a-z0-9-]{1,40}`（同时是解压目录名，绝不能带 `/`、`..`）；
 * - `urls` 全是 `https://`；
 * - `size` 为正；`sha256` 若是 64 位十六进制就保留，否则置空（客户端据此决定是否强校验）。
 */
internal object CoursewareManifestParser {

    private val ID = Regex("^[a-z0-9][a-z0-9-]{1,40}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun parse(text: String?): CoursewareManifest? {
        val body = text?.trim()?.removePrefix("\uFEFF")?.trim().orEmpty()
        if (body.isEmpty()) return null
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return null
        val version = root.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        val updatedAt = root.get("updatedAt")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val array = root.getAsJsonArray("courses") ?: return CoursewareManifest(version, updatedAt)
        val packages = array.mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            if (!ID.matches(id)) return@mapNotNull null
            val urls = obj.getAsJsonArray("urls")
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                ?.filter { it.startsWith("https://") }
                .orEmpty()
            if (urls.isEmpty()) return@mapNotNull null
            val sha = obj.get("sha256")?.takeIf { it.isJsonPrimitive }?.asString?.lowercase().orEmpty()
            CoursewarePackage(
                id = id,
                title = obj.stringOr("title"),
                subject = obj.stringOr("subject"),
                version = obj.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1,
                size = obj.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                sha256 = if (SHA256.matches(sha)) sha else "",
                urls = urls,
                kind = obj.stringOr("kind").ifEmpty { CoursewarePackage.KIND_COURSE },
                requires = obj.getAsJsonArray("requires")
                    ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                    .orEmpty(),
            ).takeIf { it.size > 0 }
        }
        return CoursewareManifest(version, updatedAt, packages)
    }

    private fun com.google.gson.JsonObject.stringOr(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}

/**
 * 服务页那一格真正要显示的东西：**内置和远端在这个类型上合流**。
 *
 * [builtin] 非空 ⇒ 包在 APK 里、永远可用（没网也能打开，这是它们能进课堂的前提）；
 * 否则是远端包，第一次点要下载。UI 只看这个类型，不再区分来源。
 */
data class CoursewareRef(
    val id: String,
    val title: String,
    val subtitle: String,
    val course: String,
    val builtin: Courseware? = null,
    val remote: CoursewarePackage? = null,
) {
    val isBuiltin: Boolean get() = builtin != null
    val requiresDownload: Boolean get() = builtin == null
    val sizeMb: Float get() = ((remote?.size ?: 0L) / 1024f / 1024f)

    companion object {
        fun of(builtin: Courseware): CoursewareRef = CoursewareRef(
            id = builtin.dir,
            title = builtin.title,
            subtitle = builtin.subtitle,
            course = builtin.course,
            builtin = builtin,
        )

        fun of(pkg: CoursewarePackage): CoursewareRef = CoursewareRef(
            id = pkg.id,
            title = pkg.title.ifEmpty { pkg.id },
            subtitle = pkg.subject.ifEmpty { "互动课件" },
            course = pkg.subject,
            remote = pkg,
        )
    }
}
