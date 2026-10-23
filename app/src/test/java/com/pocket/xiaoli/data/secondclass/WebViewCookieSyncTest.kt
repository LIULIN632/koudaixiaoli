package com.pocket.xiaoli.data.secondclass

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [WebViewCookieSync] 与真实 `CookieManager` 的交互。
 *
 * 用 Robolectric 而不是纯 JVM：这里要验证的正是"平台 API 的真实行为"——
 * `setCookie` 接不接受我们拼的 `Set-Cookie`、`Max-Age=0` 能不能删掉一条 cookie、
 * `getCookie` 还回来的串能不能被 `Cookie.parseAll` 解回来。
 * 这些用假对象测等于没测。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class WebViewCookieSyncTest {

    private val site: HttpUrl = "https://www.5idream.net/".toHttpUrl()
    private val detail: HttpUrl = "https://www.5idream.net/activity/detail?id=1".toHttpUrl()

    /** 本测试里创建过的 jar，`@After` 里逐个回收各自的 IO 线程。 */
    private val created = mutableListOf<SecondClassCookieJar>()

    @Before
    fun setUp() {
        // ⚠️ 这两行是**必须**的，不是优化。
        //
        // Robolectric 的测试方法跑在它自己的"主线程"（`SDK 30 Main Thread`）上，
        // 而测试又要用 `runBlocking` 把挂起函数跑完。两者相遇时：
        // `runBlocking` 在"已是主线程"的线程上会退化成 `Dispatchers.Main` 的事件循环，
        // 于是 `withContext(Dispatchers.Main)` 派发的任务永远排不到 ——
        // Gradle 的 `Test worker` 等 Robolectric 主线程、主线程等这个协程，**进程挂死**。
        // （实测：日志停住 30+ 分钟，jstack 可见完整死锁环。）
        //
        // 换成 `Unconfined`（同线程直接执行、不排队）就绕开了这个陷阱。
        cookieDispatcher = Dispatchers.Unconfined
        SecondClassCookieJar.testDispatcher = Dispatchers.Unconfined
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    @After
    fun tearDown() {
        cookieDispatcher = Dispatchers.Main
        SecondClassCookieJar.testDispatcher = null
        created.forEach { it.close() }
        created.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    private fun jar(
        store: SecondClassCookieStore? = null,
        flushIntervalMs: Long = 0L,
    ): SecondClassCookieJar =
        SecondClassCookieJar(store, SecondClassCookieJar.DEFAULT_DOMAINS, flushIntervalMs)
            .also { created += it }

    // ───────────────── ① 拉取：WebView → OkHttp ─────────────────

    @Test
    fun 从WebView拉到的cookie用根路径而不是URL目录() = runBlocking {
        // 关键在于 url 不是根路径。若照搬 Cookie.parseAll 的 default-path 算法，
        // path 会被算成 `/activity`，随后对 `/api/list` 的请求就不带这条 cookie ——
        // 表现为"登录成功了但接口说未登录"，且只在某些路径下复现。
        CookieManager.getInstance().setCookie(site.toString(), "SESSIONID=abc; Path=/")

        val snapshot = WebViewCookieSync.readSnapshot(detail)

        assertEquals(1, snapshot.size)
        assertEquals("SESSIONID", snapshot[0].name)
        assertEquals("abc", snapshot[0].value)
        assertEquals("/", snapshot[0].path)
        assertEquals("www.5idream.net", snapshot[0].domain)
    }

    @Test
    fun 没有登录态时快照为空且不抛异常() = runBlocking {
        assertTrue(WebViewCookieSync.readSnapshot(site).isEmpty())

        val jar = jar()
        assertFalse(WebViewCookieSync.ensureSynced(jar, site))
    }

    @Test
    fun ensureSynced把WebView的cookie灌进OkHttp并能被请求取到() = runBlocking {
        CookieManager.getInstance().setCookie(site.toString(), "SESSIONID=abc; Path=/")
        val jar = jar()

        assertTrue(WebViewCookieSync.ensureSynced(jar, site))

        val sent = jar.loadForRequest("https://www.5idream.net/api/activity/list".toHttpUrl())
        assertEquals(listOf("SESSIONID"), sent.map { it.name })
        assertEquals("abc", sent.first().value)
    }

    @Test
    fun ensureSynced在已有cookie时不做多余同步但能强制刷新() = runBlocking {
        CookieManager.getInstance().setCookie(site.toString(), "SESSIONID=old; Path=/")
        val jar = jar()
        assertTrue(WebViewCookieSync.ensureSynced(jar, site))

        // 用户在 WebView 里重新登录，换了新会话。
        CookieManager.getInstance().setCookie(site.toString(), "SESSIONID=new; Path=/")

        // 不强制：不重复拉，保留 OkHttp 侧现值（可能是服务端刚续期的更新值）。
        WebViewCookieSync.ensureSynced(jar, site)
        assertEquals("old", jar.loadForRequest(site).first().value)

        // 强制（例如用户手动点了"重新登录"）：以 WebView 为准。
        WebViewCookieSync.ensureSynced(jar, site, force = true)
        assertEquals("new", jar.loadForRequest(site).first().value)
    }

    // ───────────────── ② 回写：OkHttp → WebView ─────────────────

    @Test
    fun 续期后的cookie能写回WebView并保住Path() = runBlocking {
        val jar = jar()
        // 模拟一次响应带来的持久 cookie（服务端续期）。
        jar.saveFromResponse(site, listOf(persistent("SESSIONID", "renewed", path = "/")))

        val pushed = WebViewCookieSync.applyToWebView(site, jar.syncableSnapshot())

        assertEquals(1, pushed)
        val raw = CookieManager.getInstance().getCookie(site.toString())
        assertNotNull(raw)
        assertTrue("回写的值必须落到 CookieManager：$raw", raw!!.contains("SESSIONID=renewed"))
        // Path=/ 保住了：换到子路径也要能取到（少了 Path 就会被挂到当前目录下）。
        val onDetail = CookieManager.getInstance().getCookie(detail.toString())
        assertTrue("Path 丢了会让子路径取不到：$onDetail", onDetail!!.contains("SESSIONID=renewed"))
    }

    @Test
    fun 会话cookie与HttpOnly不回写且不计为失败() = runBlocking {
        val jar = jar()
        jar.saveFromResponse(
            site,
            listOf(
                session("JSESSIONID", "s"),
                persistent("token", "t", httpOnly = true),
                persistent("normal", "n"),
            ),
        )

        val pushed = WebViewCookieSync.applyToWebView(site, jar.syncableSnapshot())

        // 只有 normal 写进去了；另外两条被跳过，**但不算失败**（返回值只数真正写入的）。
        assertEquals(1, pushed)
        val raw = CookieManager.getInstance().getCookie(site.toString()).orEmpty()
        assertTrue(raw.contains("normal=n"))
        assertFalse(raw.contains("JSESSIONID"))
    }

    @Test
    fun 已被服务端撤销的cookie会向WebView下发删除指令() = runBlocking {
        CookieManager.getInstance().setCookie(site.toString(), "SESSIONID=stale; Path=/")
        val jar = jar()
        // 服务端登出：`SESSIONID=; Max-Age=0` —— OkHttp 解析出来的原始 `expiresAt` 是
        // `Long.MIN_VALUE`（与"非持久"同一个哨兵），只有 `persistent` 位能区分它俩。
        // 这里刻意走**真实解析**路径而不是手搓一个 `expiresAt = 1L` 的假 cookie：
        // 手搓的假数据构造不出那个组合，而这正是线上登出时的真实形态。
        jar.saveFromResponse(site, listOf(okhttp3.Cookie.parse(site, "SESSIONID=; Max-Age=0")!!))

        val cleaned = WebViewCookieSync.clearWebView(jar, site)

        assertEquals(1, cleaned)
        // ⚠️ 断言"旧值没了"，而**不是**"这条 cookie 从 `getCookie` 里消失了"。
        //
        // 各平台的删除表现不一样（实测）：真机上 `Max-Age=0` 会把这条 cookie 整个删掉，
        // 而 Robolectric 的 `CookieManager` 只是把它**置成空值**（`getCookie` 仍返回
        // `SESSIONID=`）。这正是本用例第一次写成 `assertFalse(after.contains("SESSIONID"))`
        // 时红掉的原因 —— 那条断言测的是平台的实现细节，不是我们的语义。
        // 真正的语义是"拿到 WebView 的那份会话已经作废、旧值不再可用"，
        // 而它对两种实现都成立。
        val after = CookieManager.getInstance().getCookie(site.toString()).orEmpty()
        assertFalse("删除指令没生效，WebView 里还留着旧会话值：$after", after.contains("stale"))
    }

    // ───────────────── ③ Set-Cookie 渲染（纯函数） ─────────────────

    @Test
    fun 持久cookie渲染出Expires与MaxAge() {
        val now = 1_700_000_000_000L
        val header = WebViewCookieSync.formatSetCookie(
            SecondClassCookie(
                name = "sid",
                value = "v",
                domain = ".5idream.net",
                path = "/",
                expiresAt = now + 3_600_000L,
                secure = true,
            ),
            now = now,
        )

        assertEquals(
            // ⚠️ 这个日期是**算出来的**，不是随手写的：`1_700_000_000_000` ms 是
            // 2023-11-14T22:13:20Z，加 1 小时 = 23:13:20 GMT（**不是** 15 日 00:13:20，
            // 我第一版就按"想当然"写成了 15 日，测试直接红）。
            "sid=v; Domain=5idream.net; Path=/; Expires=Tue, 14 Nov 2023 23:13:20 GMT; Max-Age=3600; Secure",
            header,
        )
    }

    @Test
    fun 过期cookie渲染成删除指令() {
        val header = WebViewCookieSync.formatSetCookie(
            // ⚠️ 用 1L（任何早于"现在"的时刻）而不是 0L：0L 是 [SESSION] 之外的一个
            // 真实时刻语义，而 SESSION 现在等于 `Long.MIN_VALUE`。用 0L 会得到一条
            // "9999 年之前过期"的普通 cookie，而不是删除指令。
            SecondClassCookie(expiresAt = 1L, name = "sid", value = "", domain = "www.5idream.net"),
            now = 1_700_000_000_000L,
        )

        assertEquals("sid=; Domain=www.5idream.net; Path=/; Max-Age=0", header)
    }

    @Test
    fun 请求头里的多条cookie全都要解出来() {
        // 这是**手写解析**的存在理由：`okhttp3.Cookie.parseAll` 按 Set-Cookie 语义解这一行时，
        // 会把 `b=2` 当成 `a=1` 的一个属性而**吞掉**，只还回一条 cookie。
        // 而请求头语义下它就是两条。用假数据直接测解析器，不依赖 CookieManager。
        val parsed = WebViewCookieSync.parseRequestCookieHeader(
            "SESSIONID=abc; _csrf=xyz; theme=dark",
            host = "www.5idream.net",
        )

        assertEquals(listOf("SESSIONID", "_csrf", "theme"), parsed.map { it.name })
        assertEquals(listOf("abc", "xyz", "dark"), parsed.map { it.value })
        // 属性按站点级补全，绝不按 URL 目录补 —— 否则 `/api/*` 的请求会漏带 cookie。
        assertTrue(parsed.all { it.path == "/" })
        assertTrue(parsed.all { it.domain == "www.5idream.net" })
        // 请求头不携带过期信息 ⇒ 一律当会话 cookie（保守，不会被当成永久有效）。
        assertTrue(parsed.all { it.isSession })
    }

    @Test
    fun 请求头里的畸形段被跳过而不是抛异常() {
        val parsed = WebViewCookieSync.parseRequestCookieHeader(
            " ; good=1; ; =novalue; also_good=2 ; ",
            host = "www.5idream.net",
        )

        assertEquals(listOf("good", "also_good"), parsed.map { it.name })
        assertEquals(listOf("1", "2"), parsed.map { it.value })
    }

    @Test
    fun 会话cookie不渲染出任何过期属性() {
        val header = WebViewCookieSync.formatSetCookie(
            SecondClassCookie(name = "sid", value = "v", domain = "www.5idream.net"),
        )

        assertEquals("sid=v; Domain=www.5idream.net; Path=/", header)
    }

    @Test
    fun 含非法字符的值被丢弃而不是发出畸形头() {
        // 值里带 `;` 会截断整条 Set-Cookie，让后面的属性变成垃圾数据。
        // 宁可不回写这一条，也不发一条会破坏整个 Cookie 头的头出去。
        assertNull(
            WebViewCookieSync.formatSetCookie(
                SecondClassCookie(name = "sid", value = "a;b", domain = "www.5idream.net"),
            ),
        )
        assertNull(
            WebViewCookieSync.formatSetCookie(
                SecondClassCookie(name = "", value = "v", domain = "www.5idream.net"),
            ),
        )
    }

    // ───────────────── 辅助 ─────────────────

    private fun persistent(
        name: String,
        value: String,
        path: String = "/",
        httpOnly: Boolean = false,
    ) = okhttp3.Cookie.Builder()
        .name(name)
        .value(value)
        .domain("www.5idream.net")
        .path(path)
        .expiresAt(System.currentTimeMillis() + 3_600_000L)
        .apply { if (httpOnly) httpOnly() }
        .build()

    private fun session(name: String, value: String) = okhttp3.Cookie.Builder()
        .name(name)
        .value(value)
        .domain("www.5idream.net")
        .path("/")
        .build()

    private fun expired(name: String, value: String) = okhttp3.Cookie.Builder()
        .name(name)
        .value(value)
        .domain("www.5idream.net")
        .path("/")
        .expiresAt(1L)
        .build()
}
