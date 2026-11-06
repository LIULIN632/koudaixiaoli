package com.pocket.xiaoli.data.learning

import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.network.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 学习平台（学习通 / 智慧树）数据拉取。
 *
 * 说明：
 * - 只使用用户自己账号登录后的 Cookie，请求的是本人可见的数据。
 * - 学习通作业列表接口需要 enc 签名参数；本项目不猜测签名算法，
 *   而是从「课程主页 HTML」里提取现成的、带 enc 的作业入口链接再请求。
 * - 所有失败都会带上 HTTP 状态码与最终 URL，方便定位是「登录失效」还是「解析不到」。
 */
interface LearningPlatformClient {
    val platform: LearningPlatform

    suspend fun fetchCourses(cookie: String): Result<List<LearningCourse>>

    suspend fun fetchExams(cookie: String): Result<List<LearningExam>>

    suspend fun fetchHomework(cookie: String): Result<List<LearningHomework>>
}

private const val FETCH_CONCURRENCY = 4

private const val UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private fun defaultLearningHttpClient(): OkHttpClient = OkHttpClient.Builder()

    .addInterceptor(NetworkLogInterceptor)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private val DUE_DATE_REGEX =
    Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?""")

class ChaoxingClient(
    private val httpClient: OkHttpClient = defaultLearningHttpClient()
) : LearningPlatformClient {

    override val platform: LearningPlatform = LearningPlatform.CHAOXING

    private data class CourseRef(
        val name: String,
        val courseId: String,
        val clazzId: String,
        val cpi: String,
        val openc: String
    )

    private class LoginExpiredException(detail: String) :
        Exception("学习通登录状态已失效，请重新网页登录。$detail")

    // ────────────────────────── 公开能力 ──────────────────────────

    override suspend fun fetchCourses(cookie: String): Result<List<LearningCourse>> =
        withContext(Dispatchers.IO) {
            try {
                val html = httpGet(courseListUrl(), cookie, REFERER_VISIT, ajax = true)
                val courses = parseCourseList(html)
                if (courses.isEmpty()) {
                    Result.failure(IllegalStateException("课程列表请求成功但解析不到课程，可能是页面结构变化"))
                } else {
                    Result.success(courses)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchExams(cookie: String): Result<List<LearningExam>> =
        Result.failure(LearningApiNotConfiguredException(LearningPlatform.CHAOXING))

    override suspend fun fetchHomework(cookie: String): Result<List<LearningHomework>> =
        withContext(Dispatchers.IO) {
            try {
                val listHtml = httpGet(courseListUrl(), cookie, REFERER_VISIT, ajax = true)
                val refs = parseCourseRefs(listHtml)
                if (refs.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("课程列表解析不到课程（Cookie 可能不完整），请重新登录学习通")
                    )
                }
                // 并发拉取：原来是 forEach 串行，每门课要 2 次请求（课程主页找签名链接 + 作业列表），
                // 10 门课就是 20 次排队。现在 4 路并发，并用 ChaoxingWorkLinks 缓存签名链接，
                // 命中缓存时每门课只剩 1 次请求。
                val gate = Semaphore(FETCH_CONCURRENCY)
                val batches = coroutineScope {
                    refs.map { ref ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                runCatching { fetchHomeworkOf(ref, cookie) }.getOrDefault(emptyList())
                            }
                        }
                    }.awaitAll()
                }
                val collected = batches.flatten()
                if (collected.isEmpty() && refs.isNotEmpty()) {
                    Result.failure(
                        IllegalStateException("${refs.size} 门课都没拉到作业，可能是登录态失效或页面结构变化")
                    )
                } else {
                    Result.success(collected.distinctBy { it.courseName + "|" + it.title })
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ────────────────────────── 解析 ──────────────────────────

    /**
     * 拉一门课的作业。
     *
     * 顺序：**先用缓存里的签名链接**（命中即 1 次请求）→ 失败/空再用课程主页现取一次。
     * 缓存只在解析成功后写，避免把一次 403 的链接记住。
     */
    private fun fetchHomeworkOf(ref: CourseRef, cookie: String): List<LearningHomework> {
        val key = ChaoxingWorkLinks.key(ref.courseId, ref.clazzId, ref.cpi)
        ChaoxingWorkLinks.get(key)?.let { cached ->
            val html = runCatching { httpGet(cached, cookie, courseHomeUrl(ref)) }.getOrNull()
            if (html != null && looksLikeWorkList(html)) {
                return parseWorkList(html, ref.name)
            }
            // 拿到 403 / 登录页 / 改版页：这条缓存已经是死链了，删掉，
            // 否则下一次同步还要先白等一次失败请求才回退。
            ChaoxingWorkLinks.remove(key)
        }
        val workUrl = resolveWorkListUrl(ref, cookie) ?: return emptyList()
        val html = httpGet(workUrl, cookie, courseHomeUrl(ref))
        ChaoxingWorkLinks.put(key, workUrl)
        return parseWorkList(html, ref.name)
    }

    /**
     * 判断"这是作业列表页"而不是登录页/错误页。
     *
     * 不能只看解析结果是否为空：这门课确实没作业时结果也是空，
     * 那种情况缓存是有效的，删了反而每轮都重新解析。
     */
    private fun looksLikeWorkList(html: String): Boolean =
        html.contains("doWork", ignoreCase = true) ||
            html.contains("getAllWork", ignoreCase = true) ||
            html.contains("作业", ignoreCase = false)

    private fun courseListUrl(): String =
        COURSE_LIST_URL + "?v=" + System.currentTimeMillis()

    private fun parseCourseList(html: String): List<LearningCourse> =
        parseCourseRefs(html).map { ref ->
            LearningCourse(
                platform = LearningPlatform.CHAOXING,
                name = ref.name,
                teacher = null,
                link = courseHomeUrl(ref)
            )
        }

    private fun parseCourseRefs(html: String): List<CourseRef> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<CourseRef>()

        doc.select("ul.course-list > li").forEach { item ->
            val name = item.selectFirst("div:nth-child(2) h3 a span")
                ?.attr("title")?.trim().orEmpty()
                .ifBlank { item.selectFirst("div:nth-child(2) h3 a")?.text()?.trim().orEmpty() }
            val href = item.select("a[href]").map { it.attr("href") }
                .firstOrNull { it.contains("courseId", ignoreCase = true) }
                ?: item.selectFirst("a[href]")?.attr("href")
                ?: return@forEach
            toCourseRef(name, href)?.let { out += it }
        }

        if (out.isEmpty()) {
            doc.select("a[href]").forEach { a ->
                val href = a.attr("href")
                if (!href.contains("courseId", ignoreCase = true)) return@forEach
                toCourseRef(a.text().trim(), href)?.let { out += it }
            }
        }
        return out.distinctBy { it.courseId + "_" + it.clazzId }
    }

    private fun toCourseRef(name: String, href: String): CourseRef? {
        val q = parseQuery(href)
        val courseId = q["courseid"] ?: return null
        return CourseRef(
            name = name.ifBlank { q["coursename"].orEmpty() },
            courseId = courseId,
            clazzId = q["clazzid"] ?: q["classid"].orEmpty(),
            cpi = q["cpi"].orEmpty(),
            openc = q["openc"].orEmpty()
        )
    }

    private fun courseHomeUrl(ref: CourseRef): String =
        COURSE_HOME + "?courseId=" + ref.courseId +
            "&clazzid=" + ref.clazzId +
            "&cpi=" + ref.cpi +
            "&openc=" + ref.openc

    /** 从课程主页 HTML 中提取带 enc 的作业入口链接（不猜签名算法） */
    private fun resolveWorkListUrl(ref: CourseRef, cookie: String): String? {
        val html = httpGet(courseHomeUrl(ref), cookie, REFERER_VISIT)
        val href = Jsoup.parse(html).select("a[href]")
            .map { it.attr("href") }
            .firstOrNull { it.contains("getAllWork", ignoreCase = true) }
        return href?.takeIf { it.isNotBlank() }?.let { absolutize(it) }
    }

    private fun parseWorkList(html: String, courseName: String): List<LearningHomework> =
        ChaoxingHomeworkParser.parseWorkList(html, courseName)

    private fun extractDueDate(text: String): String =
        ChaoxingHomeworkParser.extractDueDate(text)

    // ────────────────────────── HTTP ──────────────────────────

    private fun httpGet(
        url: String,
        cookie: String,
        referer: String? = null,
        ajax: Boolean = false
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("Cookie", cookie)
            .header("User-Agent", UA)
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        if (ajax) builder.header("X-Requested-With", "XMLHttpRequest")
        if (!referer.isNullOrBlank()) builder.header("Referer", referer)

        httpClient.newCall(builder.build()).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "请求失败 HTTP " + response.code + "（" + shortUrl(finalUrl) + "）"
                )
            }
            ensureLoggedIn(finalUrl, body)
            return body
        }
    }

    /**
     * 只在「确定是登录页」时才判定失效，避免正常页面里出现 passport 字样就误报。
     */
    private fun ensureLoggedIn(finalUrl: String, body: String) {
        val urlLower = finalUrl.lowercase()
        if (urlLower.contains("passport2.chaoxing.com") ||
            urlLower.contains("/login") ||
            urlLower.contains("fanyalogin")
        ) {
            throw LoginExpiredException("（被跳转到 " + shortUrl(finalUrl) + "）")
        }
        if (body.contains("passport2.chaoxing.com/login", ignoreCase = true) ||
            body.contains("fanyalogin", ignoreCase = true)
        ) {
            throw LoginExpiredException("（页面要求重新登录）")
        }
        if (body.isBlank()) {
            throw LoginExpiredException("（服务端返回空内容）")
        }
    }

    private fun shortUrl(url: String): String =
        url.substringBefore('?').removePrefix("https://").removePrefix("http://")

    private fun parseQuery(url: String): Map<String, String> {
        val start = url.indexOf('?')
        if (start < 0) return emptyMap()
        val query = url.substring(start + 1).substringBefore('#')
        val map = mutableMapOf<String, String>()
        query.split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) map[part.substring(0, eq).lowercase()] = part.substring(eq + 1)
        }
        return map
    }

    private fun absolutize(href: String): String = when {
        href.startsWith("http://") || href.startsWith("https://") -> href
        href.startsWith("//") -> "https:$href"
        href.startsWith("/") -> "https://mooc1.chaoxing.com$href"
        else -> "https://mooc1.chaoxing.com/$href"
    }

    private companion object {
        const val COURSE_LIST_URL = "https://mooc2-ans.chaoxing.com/visit/courses/list"
        const val COURSE_HOME = "https://mooc1.chaoxing.com/mycourse/studentcourse"
        const val REFERER_VISIT = "https://mooc2-ans.chaoxing.com/visit/interaction"
    }
}

class ZhihuishuClient(
    private val httpClient: OkHttpClient = defaultLearningHttpClient()
) : LearningPlatformClient {
    override val platform: LearningPlatform = LearningPlatform.ZHIHUISHU

    override suspend fun fetchCourses(cookie: String): Result<List<LearningCourse>> =
        Result.failure(LearningApiNotConfiguredException(LearningPlatform.ZHIHUISHU))

    override suspend fun fetchExams(cookie: String): Result<List<LearningExam>> =
        Result.failure(LearningApiNotConfiguredException(LearningPlatform.ZHIHUISHU))

    override suspend fun fetchHomework(cookie: String): Result<List<LearningHomework>> =
        Result.failure(LearningApiNotConfiguredException(LearningPlatform.ZHIHUISHU))
}

/**
 * 学习通作业列表的**HTML 解析**（无网络，纯函数）。
 *
 * 单独抽出来只为离线可测：抓取链路要真实账号，解析不需要 —— 页面结构一变，
 * `ChaoxingHomeworkParserTest` 会先红，而不是等用户"拉了但全是空的"。
 *
 * ## 截止时间的两种写法
 *
 * 1. `2026-11-05` / `2026/11/05` / `2026.11.05`，可带 ` HH:mm`（正则直接吃）；
 * 2. `11月5日` / `2026年11月5日`，可带 ` HH:mm` —— 日期部分交给 `ExamCountdown.parseDate`
 *    （无年份按"半年规则"补年），与考试倒计时同一口径：同一份页面上两处"今天"必须是同一天。
 *
 * 都解析不出就返回空串：**不编一个截止日期**，首页那行就不显示倒计时、也不排提醒。
 */
internal object ChaoxingHomeworkParser {

    private val CN_TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")

    fun parseWorkList(
        html: String,
        courseName: String,
        today: LocalDate = LocalDate.now(),
    ): List<LearningHomework> {
        val doc = Jsoup.parse(html)
        val out = mutableListOf<LearningHomework>()
        val seen = mutableSetOf<String>()
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href")
            if (!href.contains("doWork", ignoreCase = true)) return@forEach
            val title = a.text().trim()
            if (title.isBlank()) return@forEach
            val rowText = a.closest("tr")?.text() ?: a.parent()?.text().orEmpty()
            if (!seen.add(title)) return@forEach
            out += LearningHomework(
                platform = LearningPlatform.CHAOXING,
                courseName = courseName,
                title = title,
                dueDate = extractDueDate(rowText, today),
                completed = rowText.contains("已完成") || rowText.contains("已提交")
            )
        }
        if (out.isNotEmpty()) return out

        // 回退：`mooc2/work/list` 的表格行把「标题 ; 状态」写在 aria-label 上，
        // 行内不一定有 doWork 链接（未开始/已锁定的作业就是）。社区实现（chaoxing-mcp）
        // 正是按这个属性读的，比依赖链接更稳。
        doc.select("[aria-label]").forEach { row ->
            val label = row.attr("aria-label").trim()
            if (!label.contains(";") && !label.contains("；")) return@forEach
            val parts = label.split(';', '；').map { it.trim() }.filter { it.isNotBlank() }
            if (parts.isEmpty()) return@forEach
            val title = parts.first()
            if (title.length < 2) return@forEach
            if (!seen.add(title)) return@forEach
            val status = parts.getOrElse(1) { "" }
            out += LearningHomework(
                platform = LearningPlatform.CHAOXING,
                courseName = courseName,
                title = title,
                dueDate = extractDueDate(row.text(), today),
                completed = status.contains("已完成") || status.contains("已提交")
            )
        }
        return out
    }

    fun extractDueDate(text: String, today: LocalDate = LocalDate.now()): String {
        DUE_DATE_REGEX.find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return ""
            val month = m.groupValues[2].toIntOrNull() ?: return ""
            val day = m.groupValues[3].toIntOrNull() ?: return ""
            val date = "%04d-%02d-%02d".format(year, month, day)
            val hour = m.groupValues[4].toIntOrNull() ?: return date
            return "$date %02d:%s".format(hour, m.groupValues[5])
        }
        val date = ExamCountdown.parseDate(text, today) ?: return ""
        val normalized = "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        val time = CN_TIME_REGEX.find(text) ?: return normalized
        val hour = time.groupValues[1].toIntOrNull() ?: return normalized
        return "$normalized %02d:%s".format(hour, time.groupValues[2])
    }
}
