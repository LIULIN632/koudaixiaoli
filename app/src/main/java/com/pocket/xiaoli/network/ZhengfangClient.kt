package com.pocket.xiaoli.network

import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.util.RsaUtil
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ZhengfangSchool(
    val name: String,
    val url: String,
    val isV8: Boolean = true
)

data class LoginResult(
    val success: Boolean,
    val error: String? = null,
    val cookies: String? = null,
    val courses: List<CourseEntity>? = null,
    val needsCaptcha: Boolean = false,
    val captchaBase64: String? = null,
    val semesterStart: String? = null,
    val semesterWeeks: Int? = null
)

data class CaptchaResult(
    val base64: String,
    val refresh: suspend () -> CaptchaResult
)

class ZhengfangClient {
    private val gson = Gson()
    private val cookieStore = ConcurrentHashMap<String, ConcurrentHashMap<String, Cookie>>()

    internal data class SemesterInfo(val start: String?, val weeks: Int?)

    private var lastSemesterInfo: SemesterInfo? = null
    internal val latestSemesterInfo: SemesterInfo? get() = lastSemesterInfo

    /** 从课表 JSON 容错解析当前周(zc)与总周数(totalweek)，推导开学日 */
    internal fun parseZhengfangSemester(body: String): SemesterInfo? {
        if (body.isBlank() || !body.trimStart().startsWith("{")) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            fun jsonInt(key: String): Int {
                val el = root.get(key) ?: return 0
                if (el.isJsonNull) return 0
                return if (el.isJsonPrimitive) el.asString.toIntOrNull() ?: 0 else 0
            }
            var zc = jsonInt("zc")
            var totalweek = jsonInt("totalweek")
            if (zc == 0 && root.has("xqjcxx") && root.get("xqjcxx").isJsonArray) {
                val arr = root.getAsJsonArray("xqjcxx")
                if (arr.size() > 0 && arr[0].isJsonObject) {
                    val obj = arr[0].asJsonObject
                    if (zc == 0) zc = obj.get("zc")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                        ?.asString?.toIntOrNull() ?: 0
                    if (totalweek == 0) totalweek = obj.get("totalweek")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                        ?.asString?.toIntOrNull() ?: 0
                }
            }
            val start = if (zc in 1..60) {
                java.time.LocalDate.now().minusWeeks((zc - 1).toLong()).toString()
            } else null
            val weeks = totalweek.takeIf { it in 1..60 }
            if (zc == 0 && weeks == null) return null
            SemesterInfo(start, weeks)
        } catch (_: Exception) {
            null
        }
    }

    private val cookieJar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val result = mutableListOf<Cookie>()
            val now = System.currentTimeMillis()
            for ((host, hostCookies) in cookieStore) {
                if (url.host == host || url.host.endsWith(".$host") ||
                    (host.startsWith(".") && url.host.endsWith(host))) {
                    for (cookie in hostCookies.values) {
                        if (cookie.expiresAt == 0L || cookie.expiresAt > now) {
                            result.add(cookie)
                        }
                    }
                }
            }
            return result
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val hostCookies = cookieStore.getOrPut(url.host) { ConcurrentHashMap() }
            for (cookie in cookies) {
                if (cookie.name.isNotEmpty()) {
                    hostCookies[cookie.name] = cookie
                }
            }
        }
    }

    private val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/62.0.3202.89 Safari/537.36"

    private val client: OkHttpClient by lazy {
        // 严格为主 + 仅校区域名兜底（见 SchoolTlsPolicy 的类注释与 docs/安全审计-TLS.md）。
        // 历史实现是「信任一切证书 + 不校验主机名」，而这条客户端承担教务账号密码登录。
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
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .readTimeout(java.time.Duration.ofSeconds(20))
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url
                val path = url.encodedPath

                val builder = req.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Cache-Control", "no-cache, no-store")
                    .header("Pragma", "no-cache")

                if (path.contains("/xtgl/")) {
                    val origin = "${url.scheme}://${url.host}" +
                            (if (url.port != 80 && url.port != 443) ":${url.port}" else "")
                    val prefix = path.substring(0, path.indexOf("/xtgl/"))
                    builder.header("Referer", "$origin$prefix/xtgl/login_slogin.html")
                }
                if (path.contains("/kbcx/") || path.contains("/xskbcx") || path.contains("/xsgrkbcx")) {
                    val origin = "${url.scheme}://${url.host}" +
                            (if (url.port != 80 && url.port != 443) ":${url.port}" else "")
                    val prefix = path.substringBefore("/kbcx/").ifEmpty { path.substringBefore("/xskbcx") }
                        .ifEmpty { path.substringBefore("/xsgrkbcx") }
                    val refererPath = if (path.contains("gnmkdm")) path else "$path?gnmkdm=N2151"
                    builder.header("Referer", "$origin$refererPath")
                    builder.header("X-Requested-With", "XMLHttpRequest")
                }
                if (path.contains("/xsxy/")) {
                    val origin = "${url.scheme}://${url.host}" +
                            (if (url.port != 80 && url.port != 443) ":${url.port}" else "")
                    val prefix = path.substringBefore("/xsxy/")
                    builder.header("Referer", "$origin$prefix/xsxy/xsxyqk_cxXsxyqkIndex.html?gnmkdm=N105515&layout=default")
                }
                // 课程预选（kcyx）/ 自主选课（xsxk）：这两页的数据是 jqGrid 发的 AJAX，
                // 服务端按 Referer 判定"请求来自哪个功能页"。入口页与 AJAX 端点同名
                // （`kcyx_cxYxkcIndex.html` / `zzxkyzb_cxZzxkYzbIndex.html`），
                // 只是不带 doType / queryModel 那些查询参数 —— 那个形态正好就是它的 Referer。
                // 证据：`参考/课程预选_files/xskcyx.js` 第 12 行 `url: _path + '/kcyx/kcyx_cxYxkcListIndex.html'`。
                if (path.contains("/kcyx/") || path.contains("/xsxk/")) {
                    val origin = "${url.scheme}://${url.host}" +
                            (if (url.port != 80 && url.port != 443) ":${url.port}" else "")
                    val isPreSelect = path.contains("/kcyx/")
                    val marker = if (isPreSelect) "/kcyx/" else "/xsxk/"
                    val prefix = path.substringBefore(marker)
                    val refererPath = if (isPreSelect) {
                        "$prefix/kcyx/kcyx_cxYxkcIndex.html?doType=details&gnmkdm=N253525&layout=default"
                    } else {
                        "$prefix/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default"
                    }
                    builder.header("Referer", "$origin$refererPath")
                    builder.header("X-Requested-With", "XMLHttpRequest")
                }

                chain.proceed(builder.build())
            }
            .build()
    }

    private val FORM_TYPE = "application/x-www-form-urlencoded".toMediaType()

    fun getCookiesForUrl(baseUrl: String): String {
        val url = baseUrl.toHttpUrl()
        return cookieJar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }

    internal fun get(url: String): Response {
        return client.newCall(Request.Builder().url(url).get().build()).execute()
    }

    internal fun post(url: String, body: String): Response {
        return client.newCall(
            Request.Builder()
                .url(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body.toRequestBody(FORM_TYPE))
                .build()
        ).execute()
    }

    /**
     * 取页面隐藏域的值（`id=` 或 `name=` 两种属性顺序都吃）。
     *
     * `internal` 而非 `private`：选课那两条 AJAX 的请求体是**由首页隐藏域拼出来的**
     * （学年 `xkxnm`、学期 `xkxqm`、年级 `njdm_id`、专业 `zyh_id` … 全是服务端直出的），
     * 所以 [ZhengfangCourseSelectApi] 必须复用这一份解析，而不是再抄一遍正则。
     */
    internal fun extractHiddenField(html: String, fieldName: String): String {
        val patterns = listOf(
            Regex("""(?:id|name)=["']${Regex.escape(fieldName)}["'][^>]*value=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""value=["']([^"']*)["'][^>]*(?:id|name)=["']${Regex.escape(fieldName)}["']""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null) return m.groupValues[1]
        }
        return ""
    }

    private fun extractCsrftoken(html: String): String {
        val patterns = listOf(
            Regex("""(?:id|name)=["']csrftoken["'][^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""value=["']([^"']+)["'][^>]*(?:id|name)=["']csrftoken["']""", RegexOption.IGNORE_CASE),
            Regex("""(?:id|name)=["']_csrf["'][^>]*value=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(html)
            if (m != null) return m.groupValues[1]
        }
        return ""
    }

    private fun checkLoginError(body: String): String? {
        if (body.isEmpty()) return null
        try {
            val json = JsonParser.parseString(body).asJsonObject
            if (json.has("result") && json.get("result").asString == "error")
                return json.get("message")?.asString ?: "登录失败"
            if (json.has("status") && json.get("status").asString == "error")
                return json.get("message")?.asString ?: "登录失败"
            if (json.has("result") && json.get("result").asString == "success") return null
            if (json.has("status") && json.get("status").asString == "success") return null
        } catch (_: Exception) {}
        if (Regex("""密码.*错误|用户名.*错误|密码.*不正确|用户名.*不正确|帐号.*错误|帐号.*不存在|账号.*错误|账号.*不存在|用户不存在|密码错误""").containsMatchIn(body))
            return "用户名或密码错误，请检查后重试"
        if (Regex("""验证码.*错误|验证码.*不正确|校验码.*错误|yzm.*错误""").containsMatchIn(body))
            return "验证码错误，请重新输入"
        if (Regex("""系统.*维护|暂停.*服务""").containsMatchIn(body))
            return "教务系统正在维护中，请稍后再试"
        if ("xs_main" in body || "xsgr" in body || "欢迎" in body || "wdyy_szbtn" in body || "退出" in body)
            return null
        if ("TextBox1" in body || "yhm" in body)
            return "登录失败，请检查学号和密码是否正确"
        return null
    }

    private fun isCampusPortal(html: String): Boolean {
        val markers = listOf(
            "校园网", "上网认证", "Portal", "portal", "深澜", "srun",
            "锐捷", "Ruijie", "华为", "Huawei", "认证登录", "上网登录",
            "ePortal", "Dr.COM", "inode", "iNode", "城市热点"
        )
        val markerCount = markers.count { it in html }
        val isNotJw = "xtgl" !in html && "yhm" !in html && "csrftoken" !in html &&
                "TextBox1" !in html && "login_slogin" !in html
        return markerCount >= 2 || (markerCount >= 1 && isNotJw)
    }

    private fun isRedirectedToOtherSystem(html: String, finalUrl: String, baseUrl: String): Boolean {
        if (finalUrl.isNotEmpty() && baseUrl.isNotEmpty()) {
            try {
                val finalHost = finalUrl.toHttpUrl().host
                val baseHost = baseUrl.toHttpUrl().host
                if (finalHost != baseHost &&
                    !finalHost.endsWith(baseHost) && !baseHost.endsWith(finalHost))
                    return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun urlEncode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /**
     * 判断响应是否为验证码图片。Content-Type 明确为 image 类型时直接信任；
     * 无/未知 Content-Type 时用图片魔数严格校验（PNG/JPEG/GIF），
     * 避免把「无验证码学校」的错误响应/HTML 提示误判成验证码（曾导致弹无用验证码窗）。
     */
    private fun looksLikeCaptchaImage(bytes: ByteArray, contentType: String, contentLength: Int): Boolean {
        if (bytes.isEmpty() || bytes.size <= 80) return false
        val ct = contentType.lowercase()
        if (ct.contains("text/html") || ct.contains("application/json") || ct.contains("text/plain")) return false
        if (ct.contains("image")) return true
        // 无 Content-Type：魔数校验（PNG 89 50 4E 47 / JPEG FF D8 / GIF 47 49 46）
        return bytes.size >= 4 &&
            ((bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) ||
             (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) ||
             (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()))
    }

    /** 登录并拉取课表（教务导入用） */
    suspend fun login(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null
    ): LoginResult = loginWithRetry(school, username, password, onProgress, onCaptcha, fetchSchedule = true)

    /**
     * 只登录建立会话，不拉课表（成绩查询等仅需会话的场景用）。
     * 登录成功后 Cookie 保留在实例内，后续用同一 client 调成绩接口即可。
     */
    suspend fun loginOnly(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit = {},
        onCaptcha: (suspend (CaptchaResult) -> String)? = null
    ): LoginResult = loginWithRetry(school, username, password, onProgress, onCaptcha, fetchSchedule = false)

    private suspend fun loginWithRetry(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit,
        onCaptcha: (suspend (CaptchaResult) -> String)?,
        fetchSchedule: Boolean
    ): LoginResult {
        var lastResult: LoginResult? = null
        for (attempt in 1..2) {
            if (attempt > 1) onProgress("网络不稳定，正在重试……")
            val result = tryLogin(school, username, password, onProgress, onCaptcha, fetchSchedule)
            if (result.success || result.error == null) return result
            val isNetworkError = result.error.contains("无法连接") ||
                    result.error.contains("超时") ||
                    result.error.contains("网络") ||
                    result.error.contains("中断") ||
                    result.error.contains("SSL") ||
                    result.error.contains("地址无法解析")
            if (!isNetworkError) return result
            lastResult = result
        }
        return lastResult ?: LoginResult(false, "未知错误")
    }

    private suspend fun tryLogin(
        school: ZhengfangSchool,
        username: String,
        password: String,
        onProgress: (String) -> Unit,
        onCaptcha: (suspend (CaptchaResult) -> String)?,
        fetchSchedule: Boolean = true
    ): LoginResult = withContext(Dispatchers.IO) {
        cookieStore.clear()

        val baseUrl = school.url.trimEnd('/')

        try {
            onProgress("正在连接教务系统……")
            val loginPageUrls = if (school.isV8) {
                listOf("$baseUrl/xtgl/login_slogin.html")
            } else {
                listOf(
                    "$baseUrl/xtgl/login_slogin.html",
                    "$baseUrl/default2.aspx",
                    "$baseUrl/"
                )
            }
            var loginHtml = ""
            var loginFinalUrl = ""
            for (loginUrl in loginPageUrls) {
                val body = get(loginUrl).use { res ->
                    loginFinalUrl = res.request.url.toString()
                    res.body?.string() ?: ""
                }
                if (body.length > 100) {
                    loginHtml = body; break
                }
            }

            if (!currentCoroutineContext().isActive) throw CancellationException()

            if (loginHtml.length < 100)
                return@withContext LoginResult(false, "无法连接教务系统，请检查网络和地址是否正确")

            if (isCampusPortal(loginHtml))
                return@withContext LoginResult(false, "检测到校园网认证页面，\n请先连接校园网络或VPN后重试")

            if (isRedirectedToOtherSystem(loginHtml, loginFinalUrl, baseUrl))
                return@withContext LoginResult(false, "教务系统地址可能已变更，\n请联系学校确认教务系统网址")

            val csrftoken = extractCsrftoken(loginHtml)
            val viewState = extractHiddenField(loginHtml, "__VIEWSTATE")
            val viewStateGenerator = extractHiddenField(loginHtml, "__VIEWSTATEGENERATOR")
            val eventValidation = extractHiddenField(loginHtml, "__EVENTVALIDATION")

            if (!school.isV8) {
                if (loginHtml.contains("TextBox") || loginHtml.contains("yhm") || loginHtml.contains("xh") ||
                    viewState.isNotEmpty()) {
                } else if (csrftoken.isEmpty() && viewState.isEmpty()) {
                    return@withContext LoginResult(false, "无法识别教务登录页面，\n该学校可能使用了不同的教务系统版本")
                }
            } else {
                if (csrftoken.isEmpty() && !loginHtml.contains("xtgl"))
                    return@withContext LoginResult(false, "无法识别教务登录页面，\n该学校可能使用了不同的教务系统版本")
            }

            onProgress("正在获取加密公钥……")
            var modulus = ""
            var exponent = ""
            try {
                val pkeyBody = get("$baseUrl/xtgl/login_getPublicKey.html?time=${System.currentTimeMillis()}")
                    .use { it.body?.string() ?: "" }
                var json = JsonParser.parseString(pkeyBody).asJsonObject
                if (!json.has("modulus") && json.has("data")) {
                    json = json.getAsJsonObject("data")
                }
                modulus = json.get("modulus")?.asString ?: ""
                exponent = json.get("exponent")?.asString ?: "10001"
            } catch (_: Exception) { }

            if (!currentCoroutineContext().isActive) throw CancellationException()

            val captchaUrls = if (school.isV8) {
                listOf("$baseUrl/xtgl/login_getCaptcha.html")
            } else {
                listOf(
                    "$baseUrl/xtgl/login_getCaptcha.html",
                    "$baseUrl/CheckCode.aspx",
                    "$baseUrl/sys/ValidateCode.aspx",
                    "$baseUrl/validateCode.jsp"
                )
            }
            onProgress("正在检测验证码……")
            var captchaBytes = ByteArray(0)
            var captchaSuccess = false
            for (captchaAttempt in 1..4) {
                if (!currentCoroutineContext().isActive) throw CancellationException()
                var sawAbsent = false
                var threwAny = false
                try {
                    for (captchaUrl in captchaUrls) {
                        val (responseBytes, probe) = try {
                            get("$captchaUrl?time=${System.currentTimeMillis()}").use { res ->
                                val bytes = res.body?.bytes() ?: ByteArray(0)
                                val contentType = res.header("Content-Type") ?: ""
                                val contentLength = res.header("Content-Length")?.toIntOrNull() ?: 0
                                bytes to captchaProbe(res.code, looksLikeCaptchaImage(bytes, contentType, contentLength))
                            }
                        } catch (e: Exception) {
                            // 单个地址网络异常：先记下来，继续试下一个地址（老版正方有 4 个候选路径）
                            threwAny = true
                            continue
                        }
                        captchaBytes = responseBytes
                        when (probe) {
                            CaptchaProbe.IMAGE -> { captchaSuccess = true }
                            CaptchaProbe.ABSENT -> { sawAbsent = true }
                            CaptchaProbe.TRANSIENT -> { threwAny = true }
                        }
                        if (captchaSuccess) break
                    }
                } catch (e: Exception) {
                    if (captchaAttempt >= 4) throw e
                }
                if (captchaSuccess) break

                // ⭐ 关键判断：服务端**明确答复过**、而且没有一处是图片 ⇒ 这个学校根本不用验证码。
                // 以前这里会继续重试满 4 次（每次 delay 800ms），用户每次进教务都要空等约 2.4 秒，
                // 还会看到「验证码加载失败，正在重试(1/3)」——2026-10-23 用户报的就是这个。
                if (sawAbsent && !threwAny) {
                    onProgress("该教务系统无需验证码，正在登录……")
                    break
                }
                if (captchaAttempt < 4) {
                    onProgress("验证码加载失败，正在重试(${captchaAttempt}/3)……")
                    delay(800)
                }
            }

            val hasCaptcha = captchaSuccess && captchaBytes.size > 80

            // 加密公钥获取失败时直接报错（fail-closed），绝不发送明文密码
            if (modulus.isEmpty()) {
                return@withContext LoginResult(false, "获取加密密钥失败，请重试")
            }
            val encryptedPwd = RsaUtil.encrypt(password, modulus, exponent)

            var captchaCode = ""
            if (hasCaptcha) {
                if (onCaptcha == null) return@withContext LoginResult(false, "该教务系统需要输入验证码")
                val captchaB64 = Base64.getEncoder().encodeToString(captchaBytes)

                val refreshCaptchaUrls = captchaUrls
                fun makeCaptchaResult(b64: String): CaptchaResult = CaptchaResult(b64) {
                    withContext(Dispatchers.IO) {
                        var refreshBytes = ByteArray(0)
                        var refreshSuccess = false
                        for (retry in 1..4) {
                            if (!currentCoroutineContext().isActive) throw CancellationException()
                            try {
                                for (refreshUrl in refreshCaptchaUrls) {
                                    refreshBytes = get("$refreshUrl?time=${System.currentTimeMillis()}").use { res ->
                                        val bytes = res.body?.bytes() ?: ByteArray(0)
                                        val refreshContentType = res.header("Content-Type") ?: ""
                                        val refreshContentLength = res.header("Content-Length")?.toIntOrNull() ?: 0
                                        val looksLikeImage = looksLikeCaptchaImage(bytes, refreshContentType, refreshContentLength)
                                        if (looksLikeImage) { refreshSuccess = true }
                                        bytes
                                    }
                                    if (refreshSuccess) break
                                }
                                if (refreshSuccess) break
                            } catch (_: Exception) {
                                // 刷新失败不致命：返回当前拿到的内容，让上层继续展示旧图，
                                // 绝不把「验证码刷新失败」升级成登录失败
                                if (retry >= 4) break
                            }
                            if (retry < 4) delay(600)
                        }
                        makeCaptchaResult(Base64.getEncoder().encodeToString(refreshBytes))
                    }
                }
                val captchaResult = makeCaptchaResult(captchaB64)
                captchaCode = onCaptcha(captchaResult)
            }

            onProgress("正在登录……")
            val (loginBody, loginPostUrl) = if (school.isV8 || csrftoken.isNotEmpty()) {
                buildString {
                    if (csrftoken.isNotEmpty()) {
                        append("csrftoken=").append(urlEncode(csrftoken))
                        append("&")
                    }
                    append("yhm=").append(urlEncode(username))
                    append("&mm=").append(urlEncode(encryptedPwd))
                    append("&language=zh_CN")
                    if (captchaCode.isNotEmpty()) {
                        append("&yzm=").append(urlEncode(captchaCode))
                    }
                } to "$baseUrl/xtgl/login_slogin.html?time=${System.currentTimeMillis()}"
            } else {
                buildString {
                    if (viewState.isNotEmpty()) {
                        append("__VIEWSTATE=").append(urlEncode(viewState)).append("&")
                    }
                    if (viewStateGenerator.isNotEmpty()) {
                        append("__VIEWSTATEGENERATOR=").append(urlEncode(viewStateGenerator)).append("&")
                    }
                    if (eventValidation.isNotEmpty()) {
                        append("__EVENTVALIDATION=").append(urlEncode(eventValidation)).append("&")
                    }
                    append("txtUserName=").append(urlEncode(username)).append("&")
                    append("TextBox2=").append(urlEncode(encryptedPwd))
                    append("&RadioButtonList1=%D1%A7%C9%FA")
                    append("&Button1=")
                    if (captchaCode.isNotEmpty()) {
                        append("&txtSecretCode=").append(urlEncode(captchaCode))
                    }
                } to "$baseUrl/default2.aspx"
            }

            val loginBody2 = post(loginPostUrl, loginBody).use { it.body?.string() ?: "" }

            if (!currentCoroutineContext().isActive) throw CancellationException()

            var errorMsg = checkLoginError(loginBody2)
            if (errorMsg != null && captchaCode.isNotEmpty()) {
                // 降级重试：很多学校正常登录不需要验证码（探测误判/仅错误密码时才触发验证码）。
                // 本次带了验证码仍失败时，用不带验证码的方式再试一次，避免误判导致登录卡死。
                val fallbackBody = if (school.isV8 || csrftoken.isNotEmpty()) {
                    buildString {
                        if (csrftoken.isNotEmpty()) {
                            append("csrftoken=").append(urlEncode(csrftoken))
                            append("&")
                        }
                        append("yhm=").append(urlEncode(username))
                        append("&mm=").append(urlEncode(encryptedPwd))
                        append("&language=zh_CN")
                    }
                } else {
                    buildString {
                        if (viewState.isNotEmpty()) {
                            append("__VIEWSTATE=").append(urlEncode(viewState)).append("&")
                        }
                        if (viewStateGenerator.isNotEmpty()) {
                            append("__VIEWSTATEGENERATOR=").append(urlEncode(viewStateGenerator)).append("&")
                        }
                        if (eventValidation.isNotEmpty()) {
                            append("__EVENTVALIDATION=").append(urlEncode(eventValidation)).append("&")
                        }
                        append("txtUserName=").append(urlEncode(username)).append("&")
                        append("TextBox2=").append(urlEncode(encryptedPwd))
                        append("&RadioButtonList1=%D1%A7%C9%FA")
                        append("&Button1=")
                    }
                }
                val fallbackBody2 = post(loginPostUrl, fallbackBody).use { it.body?.string() ?: "" }
                if (!currentCoroutineContext().isActive) throw CancellationException()
                val fallbackError = checkLoginError(fallbackBody2)
                if (fallbackError == null) {
                    // 无验证码登录成功：该校确实不需要验证码，采用本次结果
                    onProgress("无需验证码，已直接登录")
                    errorMsg = null
                }
                // 降级仍失败：保留原始错误（该校确实要求验证码，提示用户重新输入）
            }
            if (errorMsg != null) return@withContext LoginResult(false, errorMsg)

            val courses: List<CourseEntity>?
            if (fetchSchedule) {
                onProgress("登录成功，正在获取课表……")
                // 总 deadline：即使服务器缓慢，整个课表抓取也在 120s 内结束，杜绝 30 分钟空转
                courses = withTimeout(120_000) {
                    if (school.isV8) {
                        fetchScheduleV8(baseUrl, username, onProgress)
                    } else {
                        fetchScheduleLegacy(baseUrl, username, onProgress)
                    }
                }
                if (courses.isEmpty()) return@withContext LoginResult(false, "未能获取课表数据，可能本学期未选课或课表为空")
            } else {
                // 只登录场景：不拉课表，courses 保持 null
                courses = null
            }

            LoginResult(
                true,
                cookies = getCookiesForUrl(baseUrl),
                courses = courses,
                semesterStart = lastSemesterInfo?.start,
                semesterWeeks = lastSemesterInfo?.weeks
            )
        } catch (e: TimeoutCancellationException) {
            // 必须先于 CancellationException 捕获，否则会被当成协程取消静默吞掉
            LoginResult(false, "获取课表超时，请检查网络后重试")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "未知错误"
            LoginResult(false, translateError(msg))
        }
    }

    // ── V8 schedule fetching (HTTP calls) — delegates parsing to ZhengfangScheduleParser / ZhengfangHtmlParser ──

    private suspend fun fetchScheduleV8(baseUrl: String, username: String, onProgress: (String) -> Unit): List<CourseEntity> {
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
        val memory = ZhengfangImportMemory.load(baseUrl)
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
                        ZhengfangImportMemory.save(baseUrl, apiPattern, bodyTmpl)
                        android.util.Log.d("ZhengfangClient",
                            "schedule fetched in $attempt request(s) via API url=${apiPattern.substringAfter(baseUrl)} body=${bodyTmpl.take(48)}")
                        return courses
                    }
                    if (memoryPhase) memoryPhase = false // 记忆组合已试完（未成功），切全量

                    val looksLikeJson = respBody.trimStart().startsWith("{") || respBody.trimStart().startsWith("[")
                    if (looksLikeJson && respBody.length > 100) {
                        android.util.Log.d("ZhengfangClient",
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
                            ZhengfangImportMemory.save(baseUrl, apiPattern, bodyTmpl)
                            android.util.Log.d("ZhengfangClient",
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

    private fun fetchScheduleV8HtmlPage(baseUrl: String, xnm: String, xqm: String, username: String): String {
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

    private suspend fun fetchScheduleLegacy(baseUrl: String, username: String, onProgress: (String) -> Unit): List<CourseEntity> {
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

    private fun translateError(msg: String): String {
        val lower = msg.lowercase()
        return when {
            lower.contains("验证码") -> "验证码刷新失败，请重试"
            lower.contains("ssl") || lower.contains("certificate") || lower.contains("pkix") ->
                "SSL证书验证失败，正在自动处理中，请重试"
            lower.contains("connect") && (lower.contains("refused") || lower.contains("timeout")) ->
                "无法连接到教务服务器，请检查网络连接或确认学校教务地址"
            lower.contains("unknownhost") || lower.contains("unable to resolve host") ->
                "无法解析教务系统地址，请确认学校教务网址正确"
            lower.contains("timeout") || lower.contains("timed out") ->
                "连接教务系统超时，请检查网络或稍后重试"
            lower.contains("eof") || lower.contains("unexpected end") ->
                "教务服务器连接中断，请重试或检查网络稳定性"
            lower.contains("reset") || lower.contains("connection reset") ->
                "教务服务器重置了连接，可能是触发了防火墙保护，请稍后重试"
            lower.contains("404") -> "教务系统页面不存在，该学校可能更换了教务系统地址"
            lower.contains("500") -> "教务系统服务器内部错误，请稍后重试"
            lower.contains("502") || lower.contains("503") ->
                "教务系统暂时不可用，可能是服务器繁忙或正在维护"
            lower.contains("403") -> "教务系统拒绝访问，可能需要校园网环境或VPN"
            lower.contains("401") -> "认证失败，学号或密码错误"
            lower.contains("redirect") || lower.contains("too many") ->
                "教务系统重定向次数过多，请检查地址是否正确"
            lower.contains("broken pipe") || lower.contains("ioexception") ->
                "网络连接中断，请检查网络稳定性后重试"
            else -> "网络连接失败，请检查网络和教务地址后重试"
        }
    }
}

/**
 * 一次验证码探测的结论。
 *
 * - [CaptchaProbe.IMAGE]：拿到了图片，需要用户输入；
 * - [CaptchaProbe.ABSENT]：服务端**明确答复**了（2xx~4xx），但内容不是图片
 *   ⇒ 这个学校本来就不用验证码；
 * - [CaptchaProbe.TRANSIENT]：5xx 或压根没答复 ⇒ 可能是临时故障，值得再试。
 *
 * 抽成纯函数是为了能单测：这个判断写反的后果是「要么白等三次重试，要么永远弹验证码窗」，
 * 两者都不会在编译期暴露。
 */
internal enum class CaptchaProbe { IMAGE, ABSENT, TRANSIENT }

/** 见 [CaptchaProbe]。 */
internal fun captchaProbe(statusCode: Int, looksLikeImage: Boolean): CaptchaProbe = when {
    looksLikeImage -> CaptchaProbe.IMAGE
    // 2xx/3xx/4xx：服务端明确答复过（404/403 就是「这个地址没有验证码」），再试也没意义
    statusCode in 200..499 -> CaptchaProbe.ABSENT
    // 5xx / 0（异常被吞掉）：当作临时故障
    else -> CaptchaProbe.TRANSIENT
}
