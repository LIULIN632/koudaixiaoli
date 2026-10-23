package com.pocket.xiaoli.data.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * 智慧校园统一认证（CAS）的**原生登录** —— 学的是「聚在工大」那一套
 * （`参考/聚在工大/logic/network/repo/CasLoginRepository.kt` 的机制）：
 *
 * ```
 * GET  authserver/login?service=<目标>
 *      → HTML 里拿 execution / lt / dllt / _eventId / pwdDefaultEncryptSalt（密码用的 AES 盐）
 *      → Set-Cookie 里拿 JSESSIONID
 * POST authserver/login?service=<目标>   （username + AES 加密后的密码 + 那几个隐藏字段）
 *      → 302 的 Location 里带 ticket=ST-…
 * GET  <那个 Location>（带 ticket）
 *      → 目标站点下发自己的会话 cookie → 之后 WebView 直接免登
 * ```
 *
 * 有了它，"打开服务 → 面对登录页 → 手输"就变成"点一下就好"；
 * 而且 CAS 会话一旦拿到，**换其它服务只要再走一次 302 拿 ticket，不用再输密码**
 * （这就是聚在工大"一次登录扇出到 7+ 个系统"的原理，扇出入口见 [fanOut]）。
 *
 * ## 为什么密码要"加密"成这样（2026-09-28 从真实 encrypt.js 逐行读出来的）
 *
 * ```js
 * function encryptAES(data, salt) {
 *     return _gas(_rds(64) + data, salt, _rds(16));   // 明文前垫 64 个随机字符，IV 也是随机 16 个
 * }
 * function _gas(data, key0, iv0) {
 *     key = Utf8.parse(key0.trim()); iv = Utf8.parse(iv0);
 *     return AES-CBC-Pkcs7(data, key, iv).toString();   // 纯 Base64，没有 Salted__ 头
 * }
 * var $_chars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';   // 注意：去掉了 I L O 0 1 这些易混字符
 * ```
 *
 * 那 64 个随机字符是**填充**：服务端只取尾部解析。所以密文每次都不一样，
 * 单测不能用"跑一次记下来的密文"当期望值 —— 见 `CasLoginTest` 里用真实 CryptoJS
 * 固定随机源算出来的那一条向量（实现必须逐字节对上它）。
 */
object CasLogin {

    /** 统一认证服务器（来源：门户受保护地址 302 的 Location，见 ServiceAudit 第七节）。 */
    const val AUTH_SERVER = "http://authserver.yitsd.edu.cn"

    /**
     * 本机存这套账号用的 key。
     *
     * **与教务分开存**：智慧校园（今日校园/易校园都靠它）与教务是两套系统，
     * 初始密码规则虽然一样，但改过密码之后经常只改了一边 ——
     * 共用一个密码字段的话，改教务密码会把今日校园的登录一起弄坏。
     */
    const val CREDENTIAL_KEY = "cas_authserver"

    private const val LOGIN_PATH = "/authserver/login"

    /** 与线上 `encrypt.js` 里的 `$_chars` 完全一致（没有 I/L/O/0/1）。 */
    internal const val RANDOM_CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    sealed interface Outcome {
        /**
         * 登录成功。[serviceCookies] 是**目标站点**下发的会话 cookie（给 WebView 注入用），
         * [authCookies] 是 CAS 自己的会话 cookie（留着给 [fanOut] 免密登录其它服务）。
         */
        data class Success(
            val serviceCookies: String,
            val authCookies: String,
            val ticket: String,
        ) : Outcome

        /** 这次登录要求图片验证码 —— 原生这条路走不通，交给内置网页让用户填。 */
        data object CaptchaRequired : Outcome

        data class Failed(val message: String) : Outcome
    }

    internal data class LoginForm(
        val action: String,
        val execution: String,
        val lt: String?,
        val dllt: String?,
        val eventId: String?,
        val rmShown: String?,
        val salt: String?,
        val captchaRequired: Boolean,
    )

    /** 登录页地址：`service` 为空时登的就是门户首页。 */
    fun loginUrl(service: String?): String {
        val target = service?.takeIf { it.isNotBlank() } ?: TodayCampusPortal.ORIGIN + "/new/index.html"
        return "$AUTH_SERVER$LOGIN_PATH?service=" + URLEncoder.encode(target, "UTF-8")
    }

    /**
     * 原生登录。`service` 传目标站点（例如门户首页），拿到 ticket 后顺路把该站点的会话也换回来。
     */
    suspend fun login(
        studentId: String,
        password: String,
        service: String? = null,
        random: Random = Random.Default,
    ): Outcome = withContext(Dispatchers.IO) {
        try {
            if (studentId.isBlank() || password.isBlank()) {
                return@withContext Outcome.Failed("没有可用的学号或密码")
            }
            val serviceUrl = service?.takeIf { it.isNotBlank() }
                ?: TodayCampusPortal.ORIGIN + "/new/index.html"
            val pageUrl = loginUrl(service)
            val client = client()

            // ① 登录页：隐藏字段 + 会话
            val pageResponse = client.newCall(
                Request.Builder().url(pageUrl).header("User-Agent", USER_AGENT).build()
            ).execute()
            val pageHtml = pageResponse.body?.string().orEmpty()
            val pageCookies = cookieHeaderOf(pageResponse.headers("Set-Cookie"))
            val form = parseLoginForm(pageHtml, pageUrl)
                ?: return@withContext Outcome.Failed("登录页结构无法识别（学校可能改了认证方式）")
            if (form.captchaRequired) return@withContext Outcome.CaptchaRequired

            // ② 提交账号密码（密码按页面自己的规则加密）
            val encrypted = form.salt?.takeIf { it.isNotBlank() }?.let { salt ->
                encryptPassword(
                    password = password,
                    salt = salt,
                    prefix = randomString(64, random),
                    iv = randomString(16, random),
                )
            } ?: password
            val bodyBuilder = FormBody.Builder()
                .add("username", studentId)
                .add("password", encrypted)
                .add("_eventId", form.eventId ?: "submit")
            form.lt?.let { bodyBuilder.add("lt", it) }
            form.dllt?.let { bodyBuilder.add("dllt", it) }
            form.rmShown?.let { bodyBuilder.add("rmShown", it) }
            bodyBuilder.add("execution", form.execution)

            val postResponse = client.newCall(
                Request.Builder()
                    .url(form.action)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", pageUrl)
                    .header("Cookie", pageCookies)
                    .post(bodyBuilder.build())
                    .build()
            ).execute()
            val authCookies = mergeCookies(pageCookies, cookieHeaderOf(postResponse.headers("Set-Cookie")))
            val location = postResponse.header("Location")
            val ticket = extractTicket(location)
                ?: return@withContext Outcome.Failed("账号或密码没有被接受（也可以先用网页登录一次看看提示）")

            // ③ 用 ticket 换目标站点的会话 cookie
            val serviceCookies = runCatching {
                val serviceResponse = client.newCall(
                    Request.Builder()
                        .url(location!!)
                        .header("User-Agent", USER_AGENT)
                        .header("Cookie", authCookies)
                        .build()
                ).execute()
                cookieHeaderOf(serviceResponse.headers("Set-Cookie"))
            }.getOrDefault("")

            // ★ 关键一步：两个域的会话**也要写进 WebView 的 cookie 库**。
            //
            // 只落 CookieStore（给 OkHttp 用）是不够的：内置网页读的是 CookieManager。
            // 不写进去，"一键登录"之后点开任何服务，WebView 仍旧会跳到统一认证的登录页 ——
            // 而工大那套"一次登录扇出多系统"最要紧的正是这一环：CAS 会话进了浏览器的 cookie 库，
            // 后面每个服务的 302 都能自己换到 ticket，用户不会再看到登录页。
            writeCookiesToWebView(AUTH_SERVER, authCookies)
            writeCookiesToWebView(serviceUrl, serviceCookies)

            Outcome.Success(
                serviceCookies = serviceCookies,
                authCookies = authCookies,
                ticket = ticket,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: "登录请求失败")
        }
    }

    /**
     * 扇出：已经登录过的情况下，换另一个服务的 ticket —— **不用再输一次密码**。
     *
     * 这正是聚在工大"一次登录扇出多系统"的做法（它叫 `loginGoTo(service=…)`）。
     *
     * @param authCookies [Outcome.Success.authCookies]（CAS 域的会话）
     * @return 目标站点地址（带 ticket），失败返回 null
     */
    suspend fun fanOut(service: String, authCookies: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client().newCall(
                    Request.Builder()
                        .url(loginUrl(service))
                        .header("User-Agent", USER_AGENT)
                        .header("Cookie", authCookies)
                        .build()
                ).execute()
                response.header("Location")?.takeIf { extractTicket(it) != null }
            }.getOrNull()
        }

    /**
     * 清掉本机的「门户 + 统一认证」登录态（换账号 / 登录态坏了时用）。
     *
     * 只清这两个 host：别的服务页（图书馆、洗衣等）的登录态是无辜的，
     * 不该因为退出今日校园被一起清掉。清完连内存缓存一起作废，否则界面还会显示已登录。
     */
    fun clearLocalSessions() {
        val cookieManager = android.webkit.CookieManager.getInstance()
        listOf(TodayCampusPortal.ORIGIN, AUTH_SERVER).forEach { host ->
            cookieManager.getCookie(host)
                ?.split(";")
                ?.mapNotNull { it.substringBefore("=").trim().takeIf { name -> name.isNotBlank() } }
                ?.forEach { name -> cookieManager.setCookie(host, "$name=; Max-Age=0; path=/") }
            com.pocket.xiaoli.network.CookieStore.clearServiceCookies(host)
        }
        cookieManager.flush()
        TodayCampusPortal.clearCache()
    }

    // ── 纯函数（可单测，也是跨实现比对的落点）──

    internal fun parseLoginForm(html: String, baseUrl: String = AUTH_SERVER): LoginForm? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return null
        val form = doc.getElementById("casLoginForm") ?: return null
        fun hidden(name: String): String? =
            form.selectFirst("input[name=$name]")?.attr("value")?.takeIf { it.isNotBlank() }

        val salt = doc.getElementById("pwdDefaultEncryptSalt")?.attr("value")
            ?.takeIf { it.isNotBlank() }
        // 验证码：真正的输入框在 #hidenCaptchaDiv 里（平时隐藏），
        // 需要时页面会把它塞进 #cpatchaDiv —— 所以判"#cpatchaDiv 里有没有东西"，不看模板
        val captchaBlock = doc.getElementById("cpatchaDiv")
        val captchaRequired = captchaBlock != null &&
            (captchaBlock.selectFirst("input") != null || captchaBlock.selectFirst("img") != null)

        return LoginForm(
            action = form.attr("abs:action").ifBlank { "$baseUrl$LOGIN_PATH" },
            execution = hidden("execution") ?: "e1s1",
            lt = hidden("lt"),
            dllt = hidden("dllt"),
            eventId = hidden("_eventId"),
            rmShown = hidden("rmShown"),
            salt = salt,
            captchaRequired = captchaRequired,
        )
    }

    /**
     * 与线上 `encrypt.js` 逐字节一致的实现（AES/CBC/Pkcs7 + 纯 Base64）。
     * `prefix` / `iv` 显式传入，是为了能在单测里固定随机源、拿真实 CryptoJS 的输出来比对。
     */
    internal fun encryptPassword(password: String, salt: String, prefix: String, iv: String): String {
        val keySpec = SecretKeySpec(salt.trim().toByteArray(Charsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(iv.toByteArray(Charsets.UTF_8))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val plain = (prefix + password).toByteArray(Charsets.UTF_8)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain))
    }

    internal fun randomString(length: Int, random: Random): String =
        buildString {
            repeat(length) { append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)]) }
        }

    /** 从 302 的 Location 里取 `ticket=ST-…`（没有就说明这次没登成）。 */
    internal fun extractTicket(location: String?): String? {
        val raw = location?.trim().orEmpty()
        if (raw.isBlank()) return null
        val marker = "ticket="
        val start = raw.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val value = raw.substring(start + marker.length)
            .substringBefore('&')
            .substringBefore('#')
            .trim()
        return value.takeIf { it.isNotBlank() }
    }

    /** 把多行 Set-Cookie 收成一行 `a=b; c=d`（同名以**后者**为准：后下发的才是最新的）。 */
    internal fun cookieHeaderOf(setCookies: List<String>): String {
        val pairs = LinkedHashMap<String, String>()
        setCookies.forEach { raw ->
            val first = raw.substringBefore(';')
            val name = first.substringBefore('=').trim()
            val value = first.substringAfter('=', "").trim()
            if (name.isNotBlank()) pairs[name] = value
        }
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * 把 `a=b; c=d` 这样一行 cookie 写进 WebView 的 cookie 库。
     *
     * 拆开逐个写、而不是整串塞给 `setCookie`：那个 API 收的是**一个** cookie，
     * 整串塞进去只会解析出第一个。原始属性（domain / secure）在 [cookieHeaderOf] 里已经丢掉，
     * 这里统一补 `path=/` —— 门户与统一认证这两个域的会话本来就是整站有效的。
     */
    internal fun writeCookiesToWebView(url: String, cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        runCatching {
            val manager = android.webkit.CookieManager.getInstance()
            manager.setAcceptCookie(true)
            cookieHeader.split(";").forEach { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=', "").trim()
                if (name.isNotBlank()) manager.setCookie(url, "$name=$value; path=/")
            }
            manager.flush()
        }
    }

    private fun mergeCookies(vararg headers: String): String {
        val pairs = LinkedHashMap<String, String>()
        headers.filter { it.isNotBlank() }.forEach { header ->
            header.split(";").forEach { part ->
                val name = part.substringBefore('=').trim()
                val value = part.substringAfter('=', "").trim()
                if (name.isNotBlank()) pairs[name] = value
            }
        }
        return pairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** 自己管 cookie：CAS 域与目标站点是两台机器，不能让 OkHttp 的 CookieJar 混在一起。 */
    private fun client(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}
