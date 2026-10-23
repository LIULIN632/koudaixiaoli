package com.pocket.xiaoli.data.library

import org.json.JSONArray
import org.json.JSONObject

/**
 * 资料站的一个文件。
 *
 * ## 为什么没有「上传者」「下载次数」
 *
 * 资料站是**作者单向分发**的：`announce/feed.json` 那套链路已经证明「静态 JSON + Release 附件」
 * 够用且零成本（Gitee 单仓附件 1GB、单文件 100MB、不需要备案）。
 * 一旦加上上传者/统计，就得有后端、有审核、有备案 —— 那是另一个产品。
 *
 * @param subject 学科，第一层分组（如「数学」「计算机」「外语」）。留空归到「未分学科」。
 * @param urls 下载地址，**按优先级排列**。多填几个是容灾：Gitee 挂了还有 GitHub。
 *   空列表 = 索引里有、文件还没传上来，UI 要明确说「暂不可下载」而不是点了没反应。
 * @param tags 别名，让「高数」也能搜到「高等数学」。
 */
data class LibraryFile(
    val id: String,
    val name: String,
    val subject: String = "",
    val course: String = "",
    val kind: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    val urls: List<String> = emptyList(),
    val updatedAt: String = "",
    val tags: List<String> = emptyList(),
) {
    /** 扩展名（小写，不含点）；没有扩展名时为空串。 */
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    val downloadable: Boolean get() = urls.isNotEmpty()

    /**
     * 第一层分组：学科（数学 / 计算机 / 外语 …）。
     *
     * 这一层是照岱宗资料站的做法加的 —— 它的目录树顶层就是"分类文件夹"，
     * 进去才是课程，再进去才是文件。我们压缩成两级标题：**学科 → 课程 → 文件**。
     */
    val subjectGroup: String get() = subject.ifBlank { UNCATEGORIZED_SUBJECT }

    /** 第二层分组：课程。没填课程的文件归到「未分类」，避免出现空标题。 */
    val group: String get() = course.ifBlank { UNCATEGORIZED }

    val sizeText: String get() = formatSize(sizeBytes)

    companion object {
        const val UNCATEGORIZED = "未分类"
        const val UNCATEGORIZED_SUBJECT = "未分学科"

        /** 人类可读的字节数：`1.2 MB`。未知大小（0）返回空串，让 UI 自己决定不画。 */
        fun formatSize(bytes: Long): String = when {
            bytes <= 0L -> ""
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024} KB"
            else -> {
                val mb = bytes / (1024.0 * 1024.0)
                val text = if (mb >= 10) "%.0f".format(mb) else "%.1f".format(mb)
                "$text MB"
            }
        }
    }
}

/** `library/index.json` 的完整内容。 */
data class LibraryIndex(
    val generatedAt: String = "",
    val files: List<LibraryFile> = emptyList(),
) {
    val isEmpty: Boolean get() = files.isEmpty()

    /** 出现过的课程，按文件顺序去重（作者写索引时的顺序就是他想排的顺序）。 */
    fun courses(): List<String> = files.map { it.group }.distinct()

    /** 出现过的学科，同样按文件顺序去重 —— 索引里的顺序就是展示顺序，不另设枚举表。 */
    fun subjects(): List<String> = files.map { it.subjectGroup }.distinct()

    /**
     * 本地打分搜索。
     *
     * 为什么在本地算：索引只有几十到几百条，一次拉全量比让服务器做搜索简单得多，
     * 而且**断网也能搜已缓存的索引**。打分沿用资料站调研里那套分层思路：
     * 标题前缀 > 标题包含 > 别名/课程/类型包含。
     */
    fun search(query: String, limit: Int = 50): List<LibraryFile> {
        val q = normalize(query)
        if (q.isEmpty()) return files.take(limit)
        return files
            .mapNotNull { file -> scoreOf(file, q)?.let { file to it } }
            .sortedWith(compareByDescending<Pair<LibraryFile, Int>> { it.second }.thenBy { it.first.name })
            .map { it.first }
            .take(limit)
    }

    private fun scoreOf(file: LibraryFile, q: String): Int? {
        val name = normalize(file.name)
        val aliasHit = file.tags.any { normalize(it).contains(q) }
        val subjectHit = normalize(file.subject).contains(q)
        val courseHit = normalize(file.course).contains(q)
        val kindHit = normalize(file.kind).contains(q)
        return when {
            name.startsWith(q) -> 40
            name.contains(q) -> 30
            aliasHit -> 20
            subjectHit -> 12
            courseHit -> 10
            kindHit -> 6
            else -> null
        }
    }

    companion object {
        val EMPTY = LibraryIndex()

        /** 全角/半角混着搜、大小写混着搜都要能命中，所以先统一形态。 */
        internal fun normalize(raw: String): String =
            raw.lowercase().replace(" ", "").replace("\u3000", "").trim()

        /**
         * 解析索引。**任何一处坏掉都返回 null**，由调用方决定用缓存还是内置空索引 ——
         * 宁可显示「还没资料」，也不要显示半份标题空空的列表。
         */
        fun fromJson(raw: String): LibraryIndex? = runCatching {
            val root = JSONObject(raw)
            val array: JSONArray = root.optJSONArray("files") ?: JSONArray()
            val files = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseFile(obj)?.let(::add)
                }
            }
            LibraryIndex(
                generatedAt = root.optString("generatedAt", ""),
                files = files,
            )
        }.getOrNull()

        private fun parseFile(obj: JSONObject): LibraryFile? {
            val name = obj.optString("name", "").trim()
            val id = obj.optString("id", "").trim().ifEmpty { name }
            if (name.isEmpty()) return null
            val urls = buildList {
                obj.optJSONArray("urls")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val u = arr.optString(i, "").trim()
                        if (u.isNotEmpty()) add(u)
                    }
                }
                // 只写了一个 `url` 的索引也认，省得作者为了多镜像才写数组。
                obj.optString("url", "").trim().takeIf { it.isNotEmpty() && isEmpty() }?.let(::add)
            }
            return LibraryFile(
                id = id,
                name = name,
                subject = obj.optString("subject", "").trim(),
                course = obj.optString("course", "").trim(),
                kind = obj.optString("kind", "").trim(),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                sha256 = obj.optString("sha256", "").trim().takeIf { it.isNotEmpty() },
                urls = urls,
                updatedAt = obj.optString("updatedAt", "").trim(),
                tags = obj.optJSONArray("tags").toStringList(),
            )
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (i in 0 until length()) {
                    optString(i, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }
    }
}
