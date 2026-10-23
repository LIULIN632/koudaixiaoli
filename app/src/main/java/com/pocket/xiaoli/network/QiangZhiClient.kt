package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.entity.CourseEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class QiangZhiSchool(
    val name: String,
    val url: String
)

/**
 * 强智教务系统原生导入客户端
 *
 * 主链路（app.do 移动端 API）：
 *   1. authUser        → token（明文学号密码，无需加密/验证码）
 *   2. getCurrentTime  → 当前学期 xnxqid + 总周数
 *   3. getKbcxAzc      → 逐周拉取课表 JSON，解析合并
 *
 * 兜底链路（Web 端）：
 *   1. LoginToXk       → Base64(学号)%%%Base64(密码) → 会话 Cookie
 *   2. xskb_list.do    → 课表 HTML
 *   3. .kbcontent 解析 → 课程
 */
class QiangZhiClient {

    private val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; SM-G9600 Build/QP1A.190711.020; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.198 Mobile Safari/537.36"

    // ── 内存 CookieJar：Web 兜底登录后靠 Set-Cookie 维持会话，缺了它会永远拿回登录页 ──
    private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()
    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val result = mutableListOf<Cookie>()
            val now = System.currentTimeMillis()
            for ((host, hostCookies) in cookieStore) {
                if (url.host == host || url.host.endsWith(".$host")) {
                    for (cookie in hostCookies.values) {
                        if (cookie.expiresAt == 0L || cookie.expiresAt > now) result.add(cookie)
                    }
                }
            }
            return result
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                if (cookie.name.isNotEmpty()) hostCookies[cookie.name] = cookie
            }
        }
    }

    private val client: OkHttpClient by lazy {
        // 与 ZhengfangClient 同一套策略：严格为主 + 仅校区域名兜底。
        val trustManager = SchoolTlsPolicy.trustManager()
        OkHttpClient.Builder()
            .addInterceptor(NetworkLogInterceptor)
            .sslSocketFactory(
                SchoolTlsPolicy.sslContext(trustManager).socketFactory,
                trustManager,
            )
            .hostnameVerifier(SchoolTlsPolicy.hostnameVerifier())
            .cookieJar(cookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(20))
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                chain.proceed(builder.build())
            }
            .build()
    }

    // ── URL 标准化 ──

    /** 从 getCurrentTime 响应推导开学日期：currentTime - (zc - 1) * 7 天 */
    internal fun deriveSemesterStart(semesterJson: JSONObject?): String? {
        val currentWeek = semesterJson?.optInt("zc", 0) ?: 0
        if (currentWeek !in 1..60) return null
        val currentTimeStr = semesterJson?.optString("currentTime")
            ?.ifBlank { semesterJson.optString("dqsj") } ?: ""
        val datePart = currentTimeStr.substringBefore(" ").substringBefore("T")
        return runCatching {
            LocalDate.parse(datePart).minusWeeks((currentWeek - 1).toLong()).toString()
        }.getOrNull()
    }

    /** 裸域名 → app.do 端点；已是 /app.do 则保持；/jsxsd 路径 → 去掉路径 */
    fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.endsWith("/app.do")) return trimmed
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return try {
            val uri = URI(withScheme)
            val host = uri.host ?: return ""
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$port/app.do"
        } catch (_: Exception) {
            ""
        }
    }

    /** Web 端根地址（去掉 /app.do 与子路径） */
    private fun webBaseUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl).removeSuffix("/app.do")
        return try {
            val uri = URI(normalized)
            val host = uri.host ?: return normalized
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            normalized
        }
    }

    // ── HTTP 基础 ──

    private fun get(url: String, token: String? = null): Response {
        val builder = Request.Builder().url(url).get()
        if (!token.isNullOrBlank()) builder.header("token", token)
        return client.newCall(builder.build()).execute()
    }

    private fun postForm(url: String, body: String): Response {
        return client.newCall(
            Request.Builder()
                .url(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                .build()
        ).execute()
    }

    private fun Response.bodyText(): String = body?.string() ?: ""

    private fun String.looksLikeHtml(): Boolean =
        trimStart().startsWith("<html", ignoreCase = true) ||
            trimStart().startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimStart().startsWith("<script", ignoreCase = true)

    // ── 登录主流程 ──

    suspend fun login(
        school: QiangZhiSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit = {}
    ): LoginResult = withContext(Dispatchers.IO) {
        var lastResult: LoginResult? = null
        for (attempt in 1..2) {
            if (attempt > 1) onProgress("网络不稳定，正在重试……")
            val result = tryLogin(school, username, password, onProgress)
            if (result.success || result.error == null) return@withContext result
            val isNetworkError = result.error.contains("无法连接") ||
                result.error.contains("超时") ||
                result.error.contains("网络") ||
                result.error.contains("中断") ||
                result.error.contains("SSL") ||
                result.error.contains("地址无法解析")
            if (!isNetworkError) return@withContext result
            lastResult = result
        }
        lastResult ?: LoginResult(false, "未知错误")
    }

    private suspend fun tryLogin(
        school: QiangZhiSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit
    ): LoginResult {
        val baseUrl = normalizeBaseUrl(school.url)
        if (baseUrl.isBlank()) return LoginResult(false, "学校教务地址无效")

        try {
            onProgress("正在连接教务系统……")
            if (!currentCoroutineContext().isActive) throw CancellationException()

            // 1. API 登录获取 token
            onProgress("正在登录教务系统……")
            val authUrl = buildUrl(baseUrl, mapOf(
                "method" to "authUser",
                "xh" to username,
                "pwd" to password
            ))
            val authBody = get(authUrl).use { it.bodyText() }

            if (authBody.isBlank() || authBody.looksLikeHtml()) {
                // API 未开放 → Web 兜底
                return loginViaWeb(baseUrl, username, password, school.name, onProgress)
            }

            val token = try {
                JSONObject(authBody).optString("token")
            } catch (_: Exception) {
                ""
            }
            if (token.isBlank() || token == "-1") {
                val msg = runCatching { JSONObject(authBody).optString("msg") }.getOrNull()
                if (!msg.isNullOrBlank() && msg.length < 60) {
                    return LoginResult(false, "登录失败：$msg")
                }
                return LoginResult(false, "登录失败，请检查学号和密码是否正确")
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()

            // 2. 获取当前学期
            onProgress("正在获取学期信息……")
            val timeUrl = buildUrl(baseUrl, mapOf(
                "method" to "getCurrentTime",
                "currDate" to LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            ))
            val timeBody = get(timeUrl, token).use { it.bodyText() }

            val timeJson = runCatching { JSONObject(timeBody) }.getOrNull()
            val dataJson = timeJson?.takeIf { it.has("data") && !it.isNull("data") }?.optJSONObject("data")
            val semesterJson = dataJson ?: timeJson

            // xnxqid 缺失时直接报错，不要拿显示名(xnxqmc)当 id 去查询（会白跑几十次请求）
            val xnxqid = semesterJson?.optString("xnxqid")?.takeIf { it.isNotBlank() } ?: ""
            val totalWeek = semesterJson?.optInt("totalweek", semesterJson.optInt("zcs", 20))
                ?.takeIf { it in 1..60 } ?: 20

            // 推导开学日期：currentTime - (zc - 1) * 7 天
            val semesterStart = deriveSemesterStart(semesterJson)

            if (xnxqid.isBlank()) {
                return LoginResult(false, "未能获取学期信息，请稍后重试或使用Web导入")
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()

            // 3. 逐周拉取课表并合并
            onProgress("正在获取课表……")
            val apiCourses = fetchAllWeeks(baseUrl, token, username, xnxqid, totalWeek, onProgress)
            if (apiCourses.isNotEmpty()) {
                val courses = mergeAndConvert(apiCourses)
                if (courses.isNotEmpty()) {
                    return LoginResult(
                        success = true,
                        cookies = null,
                        courses = courses,
                        semesterStart = semesterStart,
                        semesterWeeks = totalWeek
                    )
                }
            }

            // API 未返回有效数据 → Web 兜底
            return loginViaWeb(baseUrl, username, password, school.name, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            return LoginResult(false, translateError(msg))
        }
    }

    private suspend fun fetchAllWeeks(
        baseUrl: String,
        token: String,
        username: String,
        xnxqid: String,
        totalWeek: Int,
        onProgress: (String) -> Unit
    ): List<RawCourse> {
        val all = mutableListOf<RawCourse>()
        // 熔断：连续 3 周网络异常即抛出，避免静默返回残缺课表
        var consecutiveNetworkErrors = 0
        for (week in 1..totalWeek) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (week > 1 && week % 5 == 1) onProgress("正在获取第 $week 周课表……")
            val url = buildUrl(baseUrl, mapOf(
                "method" to "getKbcxAzc",
                "xh" to username,
                "xnxqid" to xnxqid,
                "zc" to week.toString()
            ))
            val body = try {
                get(url, token).use { it.bodyText() }
            } catch (e: IOException) {
                consecutiveNetworkErrors++
                if (consecutiveNetworkErrors >= 3) throw e
                continue
            } catch (_: Exception) {
                continue
            }
            consecutiveNetworkErrors = 0
            if (body.isBlank() || body.looksLikeHtml()) continue

            val array = parseArray(body)
            if (array == null) continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parseApiCourse(item)?.let { all.add(it) }
            }
        }
        return all
    }

    private fun parseArray(body: String): JSONArray? {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("[")) {
            return runCatching { JSONArray(body) }.getOrNull()
        }
        return runCatching {
            val obj = JSONObject(body)
            val data = obj.opt("data") ?: return null
            when (data) {
                is JSONArray -> data
                is JSONObject -> data.optJSONArray("list") ?: data.optJSONArray("rows")
                else -> null
            }
        }.getOrNull()
    }

    // ── API 课程解析 ──

    internal data class RawCourse(
        var name: String = "",
        var teacher: String = "",
        var place: String = "",
        var day: Int = 0,
        var weeks: List<Int> = emptyList(),
        var sections: List<Int> = emptyList()
    )

    internal fun parseApiCourse(item: JSONObject): RawCourse? {
        val name = item.optString("kcmc").trim()
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        if (name.isBlank() || name.length < 2) return null

        val teacher = item.optString("jsxm").trim()
        val place = item.optString("jsmc").trim()

        // 周次
        var weeks = parseWeeks(item.optString("kkzc"))
        val sjbz = item.optString("sjbz")
        if (weeks.isEmpty() && item.optString("kkzc").isBlank()) {
            weeks = parseWeeks(item.optString("zc"))
        }
        when {
            item.optString("kkzc").contains("单") || sjbz == "1" ->
                weeks = weeks.filter { it % 2 == 1 }
            item.optString("kkzc").contains("双") || sjbz == "2" ->
                weeks = weeks.filter { it % 2 == 0 }
        }

        // 节次
        var sections = parseSectionList(item.optString("jcs")
            .ifBlank { item.optString("jc") }
            .ifBlank { item.optString("jcsj") })

        // 星期
        var day = item.optInt("xqj", item.optInt("day", 0))
        if (day !in 1..7) day = parseDayFromText(item.optString("xqjmc").ifBlank { item.optString("xq") })

        // kcsj 兜底：格式 x0a0b（星期x 第0a-0b节）
        val kcsj = item.optString("kcsj")
        if (kcsj.length >= 5) {
            if (day !in 1..7) day = kcsj.substring(0, 1).toIntOrNull() ?: 0
            if (sections.isEmpty()) {
                val startNode = kcsj.substring(1, 3).toIntOrNull() ?: 0
                val endNode = kcsj.substring(3, 5).toIntOrNull() ?: 0
                if (startNode > 0 && endNode >= startNode) {
                    sections = (startNode..endNode).toList()
                }
            }
        }

        // 时间兜底推断节次
        if (sections.isEmpty()) {
            sections = inferSectionsByTime(item.optString("kssj"), item.optString("jssj"))
        }

        if (day !in 1..7 || weeks.isEmpty() || sections.isEmpty()) return null
        return RawCourse(
            name = name,
            teacher = teacher,
            place = place,
            day = day,
            weeks = weeks.distinct().sorted(),
            sections = sections.distinct().sorted()
        )
    }

    /** 合并周次并转为 CourseEntity：key = 课程|教师|教室|星期|节次 */
    internal fun mergeAndConvert(rawList: List<RawCourse>): List<CourseEntity> {
        val merged = LinkedHashMap<String, RawCourse>()
        for (raw in rawList) {
            val key = "${raw.name}|${raw.teacher}|${raw.place}|${raw.day}|${raw.sections.joinToString(",")}"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = raw
            } else {
                existing.weeks = (existing.weeks + raw.weeks).distinct().sorted()
            }
        }
        return merged.values.flatMap { raw ->
            val sections = raw.sections.sorted()
            val weekType = computeWeekType(raw.weeks)
            val weekStr = raw.weeks.joinToString(",") { it.toString() }
            // 连续节次 → 一个区间；非连续节次（如 1,5）拆成多门课，避免渲染出幽灵课时
            val isContiguous = sections.size == 1 || (sections.last() - sections.first() + 1 == sections.size)
            if (isContiguous) {
                val startSlot = sections.first().coerceIn(1, 12)
                val endSlot = sections.last().coerceIn(startSlot, 12)
                listOf(CourseEntity(
                    name = raw.name,
                    teacher = raw.teacher,
                    room = raw.place,
                    dayOfWeek = raw.day,
                    startSlot = startSlot,
                    endSlot = endSlot,
                    weekType = weekType,
                    weeks = weekStr,
                    colorIndex = (raw.day * 3 + startSlot) % 15
                ))
            } else {
                sections.map { sec ->
                    val slot = sec.coerceIn(1, 12)
                    CourseEntity(
                        name = raw.name,
                        teacher = raw.teacher,
                        room = raw.place,
                        dayOfWeek = raw.day,
                        startSlot = slot,
                        endSlot = slot,
                        weekType = weekType,
                        weeks = weekStr,
                        colorIndex = (raw.day * 3 + slot) % 15
                    )
                }
            }
        }
    }

    // ── Web 兜底 ──

    private suspend fun loginViaWeb(
        baseUrl: String,
        username: String,
        password: String,
        schoolName: String,
        onProgress: (String) -> Unit
    ): LoginResult {
        try {
            onProgress("API 不可用，尝试网页方式……")
            val webBase = webBaseUrl(baseUrl)
            val encoded = Base64.getEncoder().encodeToString(username.toByteArray()) + "%%%" +
                Base64.getEncoder().encodeToString(password.toByteArray())
            val loginUrl = "$webBase/jsxsd/xk/LoginToXk"
            val loginBody = postForm(loginUrl, "userAccount=&userPassword=&encoded=${URLEncoder.encode(encoded, "UTF-8")}").use { it.bodyText() }

            if (loginBody.contains("密码") || loginBody.contains("失败") || loginBody.contains("error")) {
                return LoginResult(false, "登录失败，请检查学号和密码是否正确")
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()

            onProgress("登录成功，正在获取课表……")
            val html = get("$webBase/jsxsd/xskb/xskb_list.do").use { it.bodyText() }
            if (html.contains("登录") && html.contains("userAccount")) {
                return LoginResult(false, "网页会话已过期，请重试")
            }

            val courses = parseHtmlCourses(html)
            if (courses.isEmpty()) {
                return LoginResult(false, "未能获取课表数据，可能本学期未选课或课表为空")
            }
            return LoginResult(true, cookies = null, courses = courses)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            if (msg.contains("登录")) return LoginResult(false, msg)
            return LoginResult(false, "导入失败：${translateError(msg)}")
        }
    }

    /** 解析强智 Web 课表 HTML（.kbcontent 单元格） */
    fun parseHtmlCourses(html: String): List<CourseEntity> {
        if (html.isBlank()) return emptyList()
        val courses = mutableListOf<CourseEntity>()
        try {
            val doc = org.jsoup.Jsoup.parse(html)
            val cells = doc.select(".kbcontent")
            for (cell in cells) {
                val id = cell.id()
                val digits = id.filter { it.isDigit() }
                if (digits.isEmpty()) continue
                val day = digits.first().digitToIntOrNull() ?: continue
                if (day !in 1..7) continue

                val blocks = cell.html().split("---------------------")
                for (block in blocks) {
                    val course = parseKbContentBlock(block, day) ?: continue
                    courses.add(course)
                }
            }
        } catch (_: Exception) { }
        return courses
    }

    private fun parseKbContentBlock(blockHtml: String, day: Int): CourseEntity? {
        val tempDoc = org.jsoup.Jsoup.parseBodyFragment(blockHtml.trim())
        val body = tempDoc.body()
        if (body.text().isBlank()) return null

        var name = ""
        var teacher = ""
        var room = ""
        var weekRaw = ""

        val fonts = body.select("font")
        if (fonts.isNotEmpty()) {
            // 策略 A：标准强智（title 属性标记字段）
            val teacherNode = fonts.firstOrNull { it.attr("title") == "老师" }
            val roomNode = fonts.firstOrNull { it.attr("title") == "教室" }
            val weekNode = fonts.firstOrNull { it.attr("title") == "周次(节次)" }
            if (teacherNode != null || roomNode != null || weekNode != null) {
                name = body.ownText().trim()
                if (name.isBlank()) {
                    name = body.textNodes().firstOrNull { it.text().trim().isNotBlank() }?.text()?.trim() ?: ""
                }
                teacher = teacherNode?.text()?.trim() ?: ""
                room = roomNode?.text()?.trim() ?: ""
                weekRaw = weekNode?.text()?.trim() ?: ""
            } else {
                // 策略 B：无 title，按 font 顺序 老师/周次/教室
                name = body.ownText().trim()
                if (name.isBlank()) {
                    val prev = fonts[0].previousSibling()
                    name = if (prev is org.jsoup.nodes.TextNode) prev.text().trim() else ""
                }
                teacher = fonts.getOrNull(0)?.text()?.trim() ?: ""
                weekRaw = fonts.getOrNull(1)?.text()?.trim() ?: ""
                room = fonts.getOrNull(2)?.text()?.trim() ?: ""
            }
        } else {
            // 策略 C：纯文本，行序 课程名/老师/周次/教室
            val lines = tempDoc.text().split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                name = lines[0]
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    when {
                        line.contains("周") || line.contains("节") -> weekRaw = line
                        line.contains("室") || line.contains("楼") || line.contains("区") -> room = line
                        teacher.isBlank() -> teacher = line
                    }
                }
                if (weekRaw.isBlank() && lines.size >= 3) {
                    teacher = lines.getOrElse(1) { "" }
                    weekRaw = lines.getOrElse(2) { "" }
                    room = lines.getOrElse(3) { "" }
                }
            }
        }

        name = name.replace("&nbsp;", "").trim()
        if (name.isBlank() || weekRaw.isBlank()) return null

        // 节次 [03-04节] 或 (1-2节)
        var startSlot = 0
        var endSlot = 0
        val sectionMatch = Regex("""[\[\(](\d+)-(\d+)节[]\)]""").find(weekRaw)
        if (sectionMatch != null) {
            startSlot = sectionMatch.groupValues[1].toIntOrNull() ?: 0
            endSlot = sectionMatch.groupValues[2].toIntOrNull() ?: 0
            if (endSlot < startSlot) endSlot = startSlot
        }
        if (startSlot <= 0) return null

        // 周次：括号前部分，如 "1-16周" / "1-8,10-16(单)"
        val weekStr = weekRaw.substringBefore("(").substringBefore("（").trim()
        val weeks = parseWeeks(weekStr)
        if (weeks.isEmpty()) return null
        // 括号内单双标记
        val oddMarker = weekRaw.contains("单")
        val evenMarker = weekRaw.contains("双")
        val finalWeeks = weeks.filter {
            if (oddMarker) it % 2 == 1 else if (evenMarker) it % 2 == 0 else true
        }
        if (finalWeeks.isEmpty()) return null

        return CourseEntity(
            name = name,
            teacher = teacher.ifBlank { "未知教师" },
            room = room.ifBlank { "未知教室" },
            dayOfWeek = day,
            startSlot = startSlot,
            endSlot = endSlot,
            weekType = computeWeekType(finalWeeks),
            weeks = finalWeeks.joinToString(",") { it.toString() },
            colorIndex = (day * 3 + startSlot) % 15
        )
    }

    // ── 工具 ──

    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$baseUrl?$query"
    }

    private fun parseSectionList(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val normalized = text.replace("第", "").replace("节", "")
            .replace("—", "-").replace("－", "-").replace("～", "-").replace("至", "-")
        val sections = mutableListOf<Int>()
        for (part in normalized.split(",", "，", "、")) {
            val trimmed = part.trim()
            if (trimmed.isBlank()) continue
            if (trimmed.contains("-")) {
                val range = trimmed.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (range.size >= 2) {
                    val start = range.first()
                    val end = range.last()
                    if (start > 0 && end >= start) for (s in start..end) sections.add(s)
                }
            } else if (trimmed.length >= 4 && trimmed.all { it.isDigit() }) {
                trimmed.chunked(2).forEach { chunk ->
                    chunk.toIntOrNull()?.takeIf { it > 0 }?.let { sections.add(it) }
                }
            } else {
                trimmed.toIntOrNull()?.takeIf { it > 0 }?.let { sections.add(it) }
            }
        }
        return sections.distinct().sorted()
    }

    private fun parseDayFromText(text: String): Int {
        if (text.isBlank()) return 0
        return when {
            text.contains("一") -> 1
            text.contains("二") -> 2
            text.contains("三") -> 3
            text.contains("四") -> 4
            text.contains("五") -> 5
            text.contains("六") -> 6
            text.contains("日") || text.contains("天") || text.contains("七") -> 7
            else -> 0
        }
    }

    private fun inferSectionsByTime(startTime: String, endTime: String): List<Int> {
        if (startTime.isBlank()) return emptyList()
        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val start = LocalTime.parse(startTime, formatter)
            val end = if (endTime.isNotBlank()) LocalTime.parse(endTime, formatter) else null
            val startSection = when {
                start >= LocalTime.of(19, 0) -> 9
                start >= LocalTime.of(14, 0) -> 5
                start >= LocalTime.of(12, 0) -> 5
                else -> 1
            }
            val durationMinutes = if (end != null && end.isAfter(start)) {
                java.time.Duration.between(start, end).toMinutes().toInt()
            } else {
                45
            }
            val sectionCount = kotlin.math.ceil(durationMinutes / 45.0).toInt().coerceAtLeast(1)
            (startSection..(startSection + sectionCount - 1)).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun translateError(msg: String): String {
        val lower = msg.lowercase()
        return when {
            lower.contains("ssl") || lower.contains("certificate") || lower.contains("pkix") ->
                "SSL证书验证失败，正在自动处理中，请重试"
            lower.contains("unknownhost") || lower.contains("unable to resolve host") ->
                "无法解析教务系统地址，请确认学校教务网址正确"
            lower.contains("connect") && (lower.contains("refused") || lower.contains("timeout")) ->
                "无法连接到教务服务器，请检查网络连接或确认学校教务地址"
            lower.contains("timeout") || lower.contains("timed out") ->
                "连接教务系统超时，请检查网络或稍后重试"
            lower.contains("reset") || lower.contains("connection reset") ->
                "教务服务器重置了连接，可能是触发了防火墙保护，请稍后重试"
            lower.contains("eof") || lower.contains("unexpected end") ->
                "教务服务器连接中断，请重试或检查网络稳定性"
            lower.contains("404") -> "教务系统页面不存在，该学校可能更换了教务系统地址"
            lower.contains("500") -> "教务系统服务器内部错误，请稍后重试"
            lower.contains("502") || lower.contains("503") ->
                "教务系统暂时不可用，可能是服务器繁忙或正在维护"
            lower.contains("403") -> "教务系统拒绝访问，可能需要校园网环境或VPN"
            lower.contains("401") -> "认证失败，学号或密码错误"
            else -> "网络连接失败，请检查网络和教务地址后重试"
        }
    }
}
