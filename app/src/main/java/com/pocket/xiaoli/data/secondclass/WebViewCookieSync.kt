package com.pocket.xiaoli.data.secondclass

import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 本模块所有协程切主线程的地方都走这个调度器。
 *
 * ## 为什么是一个可替换的变量，而不是直接写 `Dispatchers.Main`
 *
 * `CookieManager` 必须在主线程访问，生产环境这里就是 `Dispatchers.Main`。但
 * **Robolectric 测试跑在它自己的"主线程"上**（栈里叫 `SDK 30 Main Thread`），
 * 而测试方法本身如果再用 `runBlocking` 包协程，就会踩上 Kotlin 官方文档点名的那个坑：
 * `runBlocking` 在已是主线程的线程上会退化为 `Dispatchers.Main` 的**事件循环**，
 * 于是 `withContext(Dispatchers.Main)` 派发的任务永远排不到 ——
 * 表现为 Gradle 的 `Test worker` 等 Robolectric 主线程、Robolectric 主线程等这个协程，
 * **整个测试进程挂死**（实测：跑到 `ensureSynced` 的用例时日志停住 30+ 分钟，jstack 可见死锁环）。
 *
 * 所以测试在 `@Before` 里把它换成 `Dispatchers.Unconfined`（同线程直接执行，不排队）。
 * 生产代码**不要**动它。
 */
internal var cookieDispatcher: CoroutineDispatcher = Dispatchers.Main

/**
 * `CookieManager`（WebView 侧）与 [SecondClassCookieJar]（OkHttp 侧）的**双向**同步。
 *
 * ```
 *  ① 拉取  readSnapshot(url) ──────────────► jar.applyWebViewSnapshot(url, ...)
 *          WebView 登录后，把它的 cookie 灌进 OkHttp
 *
 *  ② 回写  jar.syncableSnapshot() ─────────► applyToWebView(url, ...)
 *          服务端续期后，把新 cookie 写回 WebView，避免两边会话分叉
 *
 *  ③ 清理  clearWebView(jar, ...) ─────────► 两边同时退出
 * ```
 *
 * ## 四个必须知道的平台细节
 *
 * 1. **`getCookie` 只给一行字符串**：`"a=1; b=2"`，属性（domain/path/expires/secure）
 *    全部丢失。所以这里要**自己补默认值**，而不是照抄（见 [readSnapshot] 的注释）。
 *
 * 2. **`setCookie` 没有"按域删除"的 API**：删除只能靠写一条 `Max-Age=0`
 *    的同名 cookie。`WebImportViewModel.kt:181` 已经踩过并记录了这一点。
 *
 * 3. **`CookieManager` 必须在主线程访问**，而且它是**全 App 单例**：
 *    `WebImportScreen.kt:260` 记着"清一处会顺手清掉别的服务的登录态"。
 *    所以 [clearWebView] **绝不用 `removeAllCookies`**。
 *
 * 4. **HttpOnly 拿得到、写不回**：`getCookie` 是 App 侧 API，不受 HttpOnly 限制，
 *    所以拉取方向能拿到它；但 `setCookie` 对 HttpOnly 静默拒绝，回写方向写不进去。
 *    这不是 bug，是浏览器安全模型的必然结果 —— 代码里把它当**预期**处理，不当错误。
 */
object WebViewCookieSync {

    private const val TAG = "WebViewCookieSync"

    // ══════════════════════════ ① 拉取：WebView → OkHttp ══════════════════════════

    /**
     * 读 WebView 当前的 cookie，**补全属性**后还回结构化列表。
     *
     * 解析与属性补全的口径全部集中在 [parseRequestCookieHeader] —— 那里解释了
     * 为什么不能借 `okhttp3.Cookie.parseAll` 来解这一行（请求头与响应头的分号语义相反）。
     */
    suspend fun readSnapshot(url: HttpUrl): List<SecondClassCookie> = withContext(cookieDispatcher) {
        val raw = runCatching { CookieManager.getInstance().getCookie(url.toString()) }
            .onFailure { Log.w(TAG, "读取 WebView Cookie 失败", it) }
            .getOrNull()
        if (raw.isNullOrBlank()) return@withContext emptyList()
        parseRequestCookieHeader(raw, url.host)
    }

    /**
     * 把 `Cookie:` 请求头的那一行拆成多条 cookie。
     *
     * ## 为什么手写，不用 `okhttp3.Cookie.parseAll`
     *
     * `parseAll(url, Headers)` 读的是 **`Set-Cookie` 响应头**（它内部就是按
     * `headers.values("Set-Cookie")` 取的，实测喂别的头名会抛
     * `IllegalArgumentException: Unexpected header`）。而两种头的分号语义**正好相反**：
     *
     * | 头 | `a=1; b=2` 的含义 |
     * |---|---|
     * | `Cookie`（我们这里拿到的） | **两条** cookie |
     * | `Set-Cookie` | **一条** cookie 带一个叫 `b` 的属性 |
     *
     * 也就是说这条路**根本不存在**一个"正确的 Headers 封装"能救回来 ——
     * 用响应头语义去解请求头，必然要么丢 cookie、要么把 `b=2` 当成属性吞掉。
     * 所以这里按请求头语义手写：`;` 分隔、每段按**第一个** `=` 切。
     *
     * 顺带两个同样只有手写才好办的点：
     * - **属性按站点级补全**：请求头里根本没有 `Path` 信息，若照搬 RFC 6265 的
     *   default-path 算法会按 URL 目录补出 `/activity`，于是对 `/api/list` 的请求就
     *   不带这条 cookie（表现为"登录成功了但接口说未登录"，且只在某些路径复现）。
     *   这里一律 `Path=/`，即"这个站点全域都带"。
     * - **domain 不带前导点**：不主动扩到兄弟子域。站点用哪个 host，就只认哪个 host。
     */
    internal fun parseRequestCookieHeader(raw: String, host: String): List<SecondClassCookie> =
        raw.split(';')
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val eq = trimmed.indexOf('=')
                if (eq <= 0) return@mapNotNull null // 没有名字的残段（如开头多一个分号）直接丢
                val name = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (name.isEmpty()) return@mapNotNull null
                SecondClassCookie(
                    name = name,
                    value = value,
                    domain = host,
                    path = "/",
                    // 请求头不携带任何过期信息 ⇒ 一律按**会话 cookie** 处理。
                    // 这是保守选择：会话 cookie 不会被 JSON 落盘当成永久有效，也不会被
                    // 误当成"可回写"（回写会覆盖掉服务端刚下发的持久化指令）。
                    expiresAt = SESSION,
                )
            }

    /**
     * 【便捷入口】OkHttp 侧还没拿到该域的 cookie 时，从 WebView 拉一次。
     *
     * 这是"数据源 B"的常规开场动作：用户可能刚在 WebView 里登录完，
     * 也可能上次登录的 cookie 已随进程丢失 —— 两种情况下 OkHttp 侧都是空的。
     *
     * @return 是否拿到了至少一条 cookie（拿不到就说明用户没在 WebView 里登录过）
     */
    suspend fun ensureSynced(
        jar: SecondClassCookieJar,
        url: HttpUrl,
        force: Boolean = false,
    ): Boolean {
        if (!force && jar.hasCookiesFor(url)) return true
        val snapshot = readSnapshot(url)
        if (snapshot.isEmpty()) return false
        jar.applyWebViewSnapshot(url, snapshot)
        Log.i(TAG, "已从 WebView 同步 ${snapshot.size} 条 cookie：${describe(snapshot)}")
        return true
    }

    // ══════════════════════════ ② 回写：OkHttp → WebView ══════════════════════════

    /**
     * 把 OkHttp 侧的新 cookie 写回 WebView。
     *
     * ⚠️ **快照的一致性由调用时机保证**：调用方应当紧接着响应处理读取快照并立刻回写，
     * 中间不要插入其它写入。本方法只负责"给什么写什么"，不自己取快照 ——
     * 因为它跑在主线程，而取快照会碰 jar 的锁，两者职责分开才不会有锁序问题。
     *
     * @return 实际写入的条数。**不含被跳过的**会话 cookie / HttpOnly cookie（那不算失败）。
     */
    suspend fun applyToWebView(url: HttpUrl, cookies: List<SecondClassCookie>): Int =
        withContext(cookieDispatcher) {
            if (cookies.isEmpty()) return@withContext 0
            val manager = CookieManager.getInstance()
            val now = System.currentTimeMillis()
            var pushed = 0
            var skipped = 0
            for (cookie in cookies) {
                if (!cookie.canBePushedToWebView) {
                    // 会话 cookie 与 HttpOnly 写不进去，**这是预期行为不是错误**。
                    skipped++
                    continue
                }
                val header = formatSetCookie(cookie, now)
                if (header == null) {
                    skipped++
                    continue
                }
                // ⚠️ `CookieManager.setCookie(String, String)` 的返回类型是 **void**，不是 boolean
                // （从 android.jar 的 class 常量池读出：`setCookie (Ljava/lang/String;Ljava/lang/String;)V`）。
                // 所以"写成功了吗"**无法从这里得知** —— 失败只有两种表现：抛异常，或静默忽略。
                // 这里只把"没有抛异常"当成已下发；真正的验证靠 `getCookie` 回读。
                val delivered: Boolean = try {
                    manager.setCookie(cookie.urlForCookie(), header)
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "回写 WebView Cookie 抛异常：${cookie.name}", e)
                    false
                }
                if (delivered) pushed++ else skipped++            }
            runCatching { manager.flush() }
            if (skipped > 0) {
                Log.i(TAG, "回写 WebView：成功 $pushed 条，跳过 $skipped 条（会话/HttpOnly/畸形值，属预期）")
            }
            pushed
        }

    /**
     * 生成一条 `Set-Cookie` 头。
     *
     * ⚠️ **不能直接用 `Cookie.toString()`**：它的输出还带 `SameSite` 一类我们无法控制、
     * 且各版本 WebView 解析容忍度不同的片段。这里逐字段显式拼，行为完全可预期，也能单测。
     *
     * @return 已过期 / 被撤销的 cookie 返回**删除指令**（`Max-Age=0`）—— 服务端登出时
     *   顺手把 WebView 里那份也删掉，而不是留一条死 cookie。畸形值返回 `null`。
     */
    internal fun formatSetCookie(cookie: SecondClassCookie, now: Long = System.currentTimeMillis()): String? {
        if (cookie.name.isBlank()) return null
        val value = sanitizeValue(cookie.value) ?: return null
        return buildString {
            append(cookie.name).append('=').append(value)
            val domain = cookie.domain.trim().trimStart('.')
            if (domain.isNotEmpty()) append("; Domain=").append(domain)
            append("; Path=").append(cookie.path.ifBlank { "/" })
            if (cookie.isExpired(now)) {
                // 删除语义：**只能**下发 `Max-Age=0`，绝不能同时带上 `Expires`。
                //
                // ⚠️ 这是实测出来的，不是"多给一个提示更保险"：
                // 删除指令写成 `Expires=Thu, 01 Jan 1970 ...; Max-Age=0` 时，
                // **CookieManager 会整条忽略它**（Robolectric 的 `CookieManager` 实测：
                // 值仍是旧的 `stale`，即删除完全没发生）。而单独下发 `Max-Age=0` 时，
                // 旧值确实被清掉了。也就是说两件事一起写不是"双保险"，是**互相打架**。
                //
                // `CookieManager` 没有按域删除的 API，删除只能靠这条同名 `Max-Age=0`
                //（`WebImportViewModel.kt:181` 也踩过并记录了这一点）。
                append("; Max-Age=0")
            } else if (!cookie.isSession) {
                append("; Expires=").append(httpDate(cookie.expiresAt))
                append("; Max-Age=").append(((cookie.expiresAt - now) / 1000L).coerceAtLeast(0L))
            }
            if (cookie.secure) append("; Secure")
            if (cookie.httpOnly) append("; HttpOnly")
        }
    }

    // ══════════════════════════ ③ 清理 ══════════════════════════

    /**
     * 清掉 WebView 里**属于本模块**的 cookie，并同步清空 OkHttp 侧的 jar。
     *
     * ### 为什么不用 `CookieManager.removeAllCookies()`
     *
     * 它是全 App 单例，那一下会把图书馆座位、学习平台、教务的登录态一起清掉
     * （`WebImportScreen.kt:260` 已经踩过这个坑）。这里只对 jar 里已知的 cookie
     * 逐条下发 `Max-Age=0`。
     *
     * ### 这个方法的**已知缺口**（很重要，别当成已解决）
     *
     * `CookieManager` **没有枚举 API**，所以"WebView 里有、但我们 jar 里没有"的 cookie
     * 清不掉 —— 典型场景是：用户登录后在 WebView 里又触发了其它子域下发 cookie，
     * 而我们还没同步过。补这个缺口只有两条路，都有代价：
     *
     * | 做法 | 代价 |
     * |---|---|
     * | `removeAllCookies()` | 清掉别的服务的登录态（**不可接受**） |
     * | `removeSessionCookies()` | 仍是全局作用域，会删别的服务的会话 cookie（**默认关闭**） |
     *
     * 所以这里的立场是：**默认只清我们确知的**，把残余风险交给"退出后重进会重新拉快照"
     * 这条常规路径覆盖。真需要彻底清时由调用方显式传
     * [alsoRemoveAllSessionCookies] 并接受代价。
     *
     * @param alsoRemoveAllSessionCookies 额外调用 `removeSessionCookies()`。
     *   默认 `false`；只有"用户明确要求彻底退出登录"时才传 `true`。
     */
    suspend fun clearWebView(
        jar: SecondClassCookieJar,
        vararg urls: HttpUrl,
        alsoRemoveAllSessionCookies: Boolean = false,
    ): Int {
        val now = System.currentTimeMillis()
        // 两类都要下发删除指令，缺一类就有残留：
        //   ① jar 里**已经作废**的（服务端登出下发的 `Max-Age=0`）—— 靠 [SecondClassCookieJar.revocations]；
        //   ② jar 里**仍然有效**的（用户主动退出登录）—— 压成过期再发。
        // ⚠️ 不要图省事只做 ①，也别指望 [SecondClassCookieJar.syncableSnapshot] 能带上 ①：
        // 它按"未过期"过滤，恰恰把最需要删的那些滤掉了。
        val revoked = (
            jar.revocations() +
                jar.syncableSnapshot(now).map { it.copy(expiresAt = 0L) }
            ).distinctBy { it.key }
        var cleaned = 0
        withContext(cookieDispatcher) {
            val manager = CookieManager.getInstance()
            for (cookie in revoked) {
                val header = formatSetCookie(cookie, now) ?: continue
                // 优先用调用方给的 URL（它的域就是真实登录域），没有就按 cookie 自己的域还原。
                val target = urls.firstOrNull { it.host.endsWith(cookie.domain.trimStart('.')) }
                    ?.toString()
                    ?: cookie.urlForCookie()
                // ⚠️ `setCookie` 返回 void（见 `applyToWebView` 的注释），**无法**知道有没有真的写进去。
                // 所以这里统计的是"**已下发**多少条删除指令"，不是"WebView 里确实删掉了多少条" ——
                // 把计数器说成"成功删除数"会让人误以为它验证过结果。真正的验证只能靠 `getCookie` 回读。
                try {
                    manager.setCookie(target, header)
                    cleaned++
                } catch (e: Exception) {
                    Log.w(TAG, "下发删除指令抛异常：${cookie.name}", e)
                }
            }
            if (alsoRemoveAllSessionCookies) {
                // ⚠️ 全局作用域：会删掉库里其它服务的会话 cookie。见方法注释的取舍表。
                runCatching { manager.removeSessionCookies(null) }
                    .onFailure { Log.w(TAG, "removeSessionCookies 失败", it) }
            }
            // ⚠️ 这两处都要显式收尾成 Unit：`runCatching` 的最后一句若作为 lambda 的返回值，
            // 会把整个 `withContext` 的返回类型推成 `Any`，而外层函数声明的是 `Int`。
            runCatching { manager.flush() }.onFailure { Log.w(TAG, "CookieManager.flush 失败", it) }
            Unit
        }
        val names = describe(revoked)
        jar.clear()
        Log.i(TAG, "已清理 WebView 侧第二课堂 Cookie：下达 $cleaned 条删除指令（$names）")
        return cleaned
    }

    // ══════════════════════════ 工具 ══════════════════════════

    /**
     * cookie 值里的 `;` `,` `"` `\` 在 `Set-Cookie` 里会截断整条头或被当作引号包裹。
     * 会话 id 不含这些字符，命中就说明上游数据有问题 —— 丢弃这条，
     * 而不是把一条会破坏整个 Cookie 头的畸形数据发出去。
     *
     * **刻意不打日志**：这里可能被每个响应调用一次，打日志会变成"日志刷屏"，
     * 而它本身不是错误（合法 cookie 值就那么几种字符）。
     */
    private fun sanitizeValue(value: String): String? =
        value.takeUnless { v ->
            v.any { it == ';' || it == ',' || it == '"' || it == '\\' || it == '\n' || it == '\r' }
        }

    /** RFC 1123 / HTTP-date。`Set-Cookie` 的 `Expires` 只认 GMT。 */
    private fun httpDate(epochMillis: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(Date(epochMillis))

    /** 日志用的一行摘要，**只含 cookie 名，不含值**（值等价于用户凭据）。 */
    fun describe(cookies: List<SecondClassCookie>): String =
        if (cookies.isEmpty()) "(空)" else cookies.joinToString { it.name }
}

/**
 * 把 cookie 还原成一个"用来定位它"的 URL。
 *
 * `CookieManager.setCookie(url, ...)` 需要 URL 才能确定域，而 domain 与 path 已经在
 * `Set-Cookie` 头里显式给了，所以这里只拼 scheme 与 host：
 * **不能**把 path 拼进 URL（那会让 setCookie 把 cookie 挂到那个路径下）。
 */
private fun SecondClassCookie.urlForCookie(): String = "https://${domain.trim().trimStart('.')}/"
