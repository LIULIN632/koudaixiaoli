package com.pocket.xiaoli.data.learning

import com.pocket.xiaoli.network.NetworkLogInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
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
                val collected = mutableListOf<LearningHomework>()
                var lastError: Exception? = null
                refs.forEach { ref ->
                    try {
                        val workUrl = resolveWorkListUrl(ref, cookie) ?: return@forEach
                        collected += parseWorkList(httpGet(workUrl, cookie, courseHomeUrl(ref)), ref.name)
                    } catch (e: Exception) {
                        lastError = e
                    }
                }
                if (collected.isEmpty() && lastError != null) {
                    Result.failure(lastError!!)
                } else {
                    Result.success(collected.distinctBy { it.courseName + "|" + it.title })
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ────────────────────────── 解析 ──────────────────────────

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

    private fun parseWorkList(html: String, courseName: String): List<LearningHomework> {
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
                dueDate = extractDueDate(rowText),
                completed = rowText.contains("已完成") || rowText.contains("已提交")
            )
        }
        return out
    }

    private fun extractDueDate(text: String): String {
        val m = DUE_DATE_REGEX.find(text) ?: return ""
        val year = m.groupValues[1].toIntOrNull() ?: return ""
        val month = m.groupValues[2].toIntOrNull() ?: return ""
        val day = m.groupValues[3].toIntOrNull() ?: return ""
        val date = "%04d-%02d-%02d".format(year, month, day)
        val hour = m.groupValues[4].toIntOrNull() ?: return date
        return "$date %02d:%s".format(hour, m.groupValues[5])
    }

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
