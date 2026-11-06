package com.pocket.xiaoli.core.academic

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

// 课表抓取从 ZhengfangClient 拆出（原文件 967 行，超过项目 800 行门槛）。
// 仍是同一套会话：通过 get/post 复用 Cookie，解析交给 academic 包里的解析器。

// ── V8 schedule fetching (HTTP calls) — delegates parsing to ZhengfangScheduleParser / ZhengfangHtmlParser ──

internal suspend fun ZhengfangClient.fetchScheduleV8(baseUrl: String, username: String, onProgress: (String) -> Unit): List<ParsedCourse> {
    onProgress("正在通过 API 获取课表……")
    val now = java.time.LocalDate.now()
    val (xnm, xqm) = if (now.monthValue >= 8 || now.monthValue <= 1) {
        now.year.toString() to "3"
    } else {
        (now.year - 1).toString() to "12"
    }

    val semesterPairs = listOf(
        xnm to xqm,
        (if (xqm == "3") xnm else (xnm.toInt() - 1).toString()) to (if (xqm == "3") "12" else "3")
    ).distinct()

    val apiUrlPatterns = listOf(
        "$baseUrl/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
        "$baseUrl/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N121603",
        "$baseUrl/kbcx/xskbcx_cxXsgrkb.html"
    )
    val bodyTemplates = listOf(
        "xnm={xnm}&xqm={xqm}&kzlx=ck&xsdm={xsdm}&kclbdm=",
        "xnm={xnm}&xqm={xqm}&xsdm={xsdm}",
        "xnm={xnm}&xqm={xqm}&xh={xh}",
        "xnm={xnm}&xqm={xqm}",
        "xn={xnm}&xq={xqm}"
    )

    val totalAttempts = apiUrlPatterns.size * bodyTemplates.size * semesterPairs.size
    var attempt = 0
    var lastBody = ""
    // 熔断：连续 3 次网络异常立即停止并抛出真实错误，避免百次请求空转 30 分钟
    var consecutiveNetworkErrors = 0

    // 成功组合记忆（ZhengfangImportMemory）：上次该校导入成功的 (URL 模式, body 模板)
    // 优先试，命中则 1 次请求完成；失败自动回退全量试错并更新记忆。
    val memory = ImportMemoryBridge.load(baseUrl)
    var memoryPhase = memory != null // true=只试记忆组合；试完后切 false 走全量
    fun isMemoryCombo(pattern: String, tmpl: String): Boolean =
        memory != null && pattern == memory.first && tmpl == memory.second

    for (apiPattern in apiUrlPatterns) {
        for (bodyTmpl in bodyTemplates) {
            if (memoryPhase) {
                // 记忆阶段：只请求记忆组合，其余跳过（不产生网络请求）
                if (!isMemoryCombo(apiPattern, bodyTmpl)) continue
            } else {
                // 全量阶段：跳过已试过的记忆组合，避免重复请求
                if (isMemoryCombo(apiPattern, bodyTmpl)) continue
            }
            for ((sn, sq) in semesterPairs) {
                if (!currentCoroutineContext().isActive) return emptyList()
                val url = apiPattern
                val body = bodyTmpl.replace("{xnm}", sn).replace("{xqm}", sq).replace("{xsdm}", username).replace("{xh}", username)
                attempt++
                onProgress("正在请求课表接口 ($attempt/$totalAttempts)……")
                val respBody = try {
                    post(url, body).use { it.body?.string() ?: "" }
                } catch (e: IOException) {
                    consecutiveNetworkErrors++
                    if (memoryPhase) memoryPhase = false // 记忆组合失败，回退全量
                    if (consecutiveNetworkErrors >= 3) throw e
                    continue
                } catch (_: Exception) {
                    if (memoryPhase) memoryPhase = false
                    continue
                }
                consecutiveNetworkErrors = 0
                lastBody = respBody

                var courses = ZhengfangScheduleParser.parseJsonSchedule(respBody)
                if (courses.isNotEmpty()) {
                    // 仅在解析出课程时更新学期信息，避免被空响应/上学期响应覆盖成错误开学日
                    lastSemesterInfo = parseZhengfangSemester(respBody)
                    ImportMemoryBridge.save(baseUrl, apiPattern, bodyTmpl)
                    AcademicLog.d("ZhengfangClient",
                        "schedule fetched in $attempt request(s) via API url=${apiPattern.substringAfter(baseUrl)} body=${bodyTmpl.take(48)}")
                    return courses
                }
                if (memoryPhase) memoryPhase = false // 记忆组合已试完（未成功），切全量

                val looksLikeJson = respBody.trimStart().startsWith("{") || respBody.trimStart().startsWith("[")
                if (looksLikeJson && respBody.length > 100) {
                    AcademicLog.d("ZhengfangClient",
                        "JSON response (${respBody.length} chars) but parseJsonSchedule returned 0 courses. Preview: ${respBody.take(300)}")
                }

                if (respBody.contains("<table", ignoreCase = true) ||
                    respBody.contains("kbgrid_table") || respBody.contains("kblist_table") ||
                    respBody.contains("timetable_con")) {
                    courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(respBody)
                    if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseScheduleHtmlV8List(respBody)
                    if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseHtmlScheduleTable(respBody)
                    if (courses.isNotEmpty()) {
                        lastSemesterInfo = parseZhengfangSemester(respBody)
                        ImportMemoryBridge.save(baseUrl, apiPattern, bodyTmpl)
                        AcademicLog.d("ZhengfangClient",
                            "schedule fetched in $attempt request(s) via HTML fallback url=${apiPattern.substringAfter(baseUrl)}")
                        return courses
                    }
                }
            }
        }
    }

    onProgress("API 未返回数据，尝试页面解析……")
    val v8Html = fetchScheduleV8HtmlPage(baseUrl, xnm, xqm, username)
    if (v8Html.isNotEmpty()) {
        var courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(v8Html)
        if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseScheduleHtmlV8List(v8Html)
        if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseHtmlScheduleTable(v8Html)
        if (courses.isEmpty() && lastBody.isNotEmpty() &&
            (lastBody.contains("<table", ignoreCase = true) || lastBody.contains("kbgrid") || lastBody.contains("timetable_con"))) {
            courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(lastBody)
            if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseScheduleHtmlV8List(lastBody)
            if (courses.isEmpty()) courses = ZhengfangHtmlParser.parseHtmlScheduleTable(lastBody)
        }
        return courses
    }

    if (lastBody.isNotEmpty()) {
        var courses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(lastBody)
        if (courses.isNotEmpty()) return courses
        courses = ZhengfangHtmlParser.parseScheduleHtmlV8List(lastBody)
        if (courses.isNotEmpty()) return courses
        return ZhengfangHtmlParser.parseHtmlScheduleTable(lastBody)
    }

    return emptyList()
}

internal fun ZhengfangClient.fetchScheduleV8HtmlPage(baseUrl: String, xnm: String, xqm: String, username: String): String {
    val gnCodes = listOf("N2151", "N121603", "N121601")
    val urlPatterns = listOf(
        "/kbcx/xskbcx_cxXskbcx.html",
        "/kbcx/xskbcx_cxKbcx.html",
        "/xskbcx.aspx",
        "/xsgrkbcx.aspx",
        "/xsdjkbcx.aspx"
    )
    val bodyParams = listOf(
        "xnm=$xnm&xqm=$xqm&kzlx=ck&xsdm=$username&kclbdm=",
        "xnm=$xnm&xqm=$xqm&xsdm=$username",
        "xnm=$xnm&xqm=$xqm&xh=$username",
        "xnm=$xnm&xqm=$xqm",
        "xn=$xnm&xq=$xqm"
    )
    var consecutiveNetworkErrors = 0

    for (pattern in urlPatterns) {
        for (gnCode in gnCodes) {
            val url = "$baseUrl$pattern?gnmkdm=$gnCode"
            for (body in bodyParams) {
                val html = try {
                    post(url, body).use { it.body?.string() ?: "" }
                } catch (e: IOException) {
                    consecutiveNetworkErrors++
                    if (consecutiveNetworkErrors >= 3) throw e
                    continue
                } catch (_: Exception) {
                    continue
                }
                consecutiveNetworkErrors = 0
                if (html.length > 500 &&
                    (html.contains("table", ignoreCase = true) || html.contains("课程") ||
                     html.contains("课表") || html.contains("kbgrid") || html.contains("timetable_con"))) {
                    return html
                }
            }
        }
    }
    for (pattern in urlPatterns.take(2)) {
        for (gnCode in gnCodes.take(1)) {
            val html = try {
                get("$baseUrl$pattern?gnmkdm=$gnCode&xnm=$xnm&xqm=$xqm").use { it.body?.string() ?: "" }
            } catch (e: IOException) {
                consecutiveNetworkErrors++
                if (consecutiveNetworkErrors >= 3) throw e
                continue
            } catch (_: Exception) {
                continue
            }
            consecutiveNetworkErrors = 0
            if (html.length > 500 &&
                (html.contains("table", ignoreCase = true) || html.contains("课程") ||
                 html.contains("课表") || html.contains("kbgrid") || html.contains("timetable_con"))) {
                return html
            }
        }
    }
    return ""
}

// ── Legacy (non-V8) schedule fetching ──

internal suspend fun ZhengfangClient.fetchScheduleLegacy(baseUrl: String, username: String, onProgress: (String) -> Unit): List<ParsedCourse> {
    onProgress("正在尝试 API 获取课表……")
    val v8Courses = fetchScheduleV8(baseUrl, username) { }
    if (v8Courses.isNotEmpty()) return v8Courses

    onProgress("正在尝试 V8 页面解析……")
    val now = java.time.LocalDate.now()
    val (xnm, xqm) = if (now.monthValue >= 8 || now.monthValue <= 1) {
        now.year.toString() to "3"
    } else {
        (now.year - 1).toString() to "12"
    }
    val v8Html = fetchScheduleV8HtmlPage(baseUrl, xnm, xqm, username)
    if (v8Html.isNotEmpty()) {
        val v8GridCourses = ZhengfangHtmlParser.parseScheduleHtmlV8Grid(v8Html)
        if (v8GridCourses.isNotEmpty()) return v8GridCourses
        val v8ListCourses = ZhengfangHtmlParser.parseScheduleHtmlV8List(v8Html)
        if (v8ListCourses.isNotEmpty()) return v8ListCourses
    }

    onProgress("正在通过页面解析课表……")
    val scheduleUrls = listOf(
        "$baseUrl/xsgrkbcx.aspx",
        "$baseUrl/xskbcx.aspx",
        "$baseUrl/xsdjkbcx.aspx",
        "$baseUrl/wsxk/xskbcx.aspx"
    )
    for (scheduleUrl in scheduleUrls) {
        val html = try {
            get(scheduleUrl).use { it.body?.string() ?: "" }
        } catch (_: Exception) { continue }
        if (html.length > 500 && ("课程" in html || "课表" in html || "Table1" in html || "table" in html.lowercase())) {
            val courses = ZhengfangHtmlParser.parseHtmlScheduleTable(html)
            if (courses.isNotEmpty()) return courses
        }
    }

    try {
        val mainHtml = get("$baseUrl/xs_main.aspx").use { it.body?.string() ?: "" }
        val linkMatch = Regex("""(?:xskbcx|xsdjkbcx|xsgrkbcx)\.aspx[^"']*""").find(mainHtml)
        if (linkMatch != null) {
            val link = linkMatch.value
            val fullUrl = if (link.startsWith("http")) link else "$baseUrl/$link"
            val schedHtml = get(fullUrl).use { it.body?.string() ?: "" }
            val courses = ZhengfangHtmlParser.parseHtmlScheduleTable(schedHtml)
            if (courses.isNotEmpty()) return courses
        }
    } catch (_: Exception) {}

    return emptyList()
}
