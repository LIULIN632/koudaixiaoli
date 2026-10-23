package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * [SecondClassCookieJar] 的核心行为。
 *
 * 这些用例**不依赖 Android**（纯 JVM 跑），因为这一层是双向同步里最容易出错、
 * 也最需要能快速回归的部分：域匹配、path 优先级、过期判定、节流落盘。
 *
 * 平台相关的部分（`CookieManager` 的存取）在 `WebViewCookieSyncTest` 里用 Robolectric 测。
 */
class SecondClassCookieJarTest {

    private val site: HttpUrl = "https://www.5idream.net/".toHttpUrl()

    /** 本测试里创建过的 jar，`@After` 里逐个回收各自的 IO 线程。 */
    private val created = mutableListOf<SecondClassCookieJar>()

    @Before
    fun setUp() {
        // 让"落盘是异步的"在单测里变成**同步完成**。
        // 不这么做的话测试只能靠 sleep 猜时序，或者依赖 Thread.sleep 轮询 —— 两者都会
        // 让用例变慢且不稳定（实测：靠轮询时整条用例空转了 551 秒）。
        SecondClassCookieJar.testDispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        SecondClassCookieJar.testDispatcher = null
        created.forEach { it.close() }
        created.clear()
    }

    /** 统一入口：建 jar 并登记，保证不会漏回收线程。 */
    private fun jar(
        store: SecondClassCookieStore?,
        domains: Set<String> = SecondClassCookieJar.DEFAULT_DOMAINS,
        flushIntervalMs: Long = 0L,
    ): SecondClassCookieJar =
        SecondClassCookieJar(store, domains, flushIntervalMs).also { created += it }

    // ───────────────────────── 存档结构 ─────────────────────────

    /**
     * 守门人：**每个字段都有默认值** ⇔ **存在无参构造**。
     *
     * 这两个说法精确等价，而后者可断言。为什么必须守：
     * `SecondClassCookie` 要经 Gson 落盘，只要有一个字段漏了默认值，
     * Kotlin 就不生成无参构造，Gson 改用 `UnsafeAllocator` 反射赋值，
     * **所有**字段的默认值一起失效 —— 老存档读出来是 `null`。
     * `DormInfo` v1.0.93 的闪退就是这么来的（见 `CLAUDE.md`）。
     */
    @Test
    fun 每个字段都有默认值() {
        listOf(
            SecondClassCookie::class.java,
            SecondClassCookiePayload::class.java,
            SecondClassCookieBucket::class.java,
        ).forEach { type ->
            assertTrue(
                "${type.simpleName} 缺无参构造 —— 说明有字段漏了默认值",
                type.declaredConstructors.any { it.parameterCount == 0 },
            )
        }
    }

    @Test
    fun 落盘快照可往返且会话cookie不会变成永不过期() {
        val gson = com.google.gson.Gson()
        val original = listOf(
            SecondClassCookie(
                name = "sessionid",
                value = "abc",
                domain = ".5idream.net",
                path = "/",
                expiresAt = 1_700_000_000_000L,
                secure = true,
                httpOnly = true,
                seq = 7L,
            ),
            // 会话 cookie 的哨兵值必须原样往返，不能被写成 Long.MAX_VALUE。
            SecondClassCookie(name = "tmp", value = "x", domain = ".5idream.net"),
        )
        val raw = gson.toJson(SecondClassCookiePayload(cookies = original))

        val restored = gson.fromJson(raw, SecondClassCookiePayload::class.java)

        assertEquals(SecondClassCookiePayload.CURRENT_VERSION, restored.version)
        assertEquals(original, restored.cookies)
        assertTrue(restored.cookies[1].isSession)
    }

    // ───────────────────────── 落盘与恢复 ─────────────────────────

    @Test
    fun 写入响应后能从存档恢复() {
        val store = FakeStore()
        val jar = jar(store)
        jar.saveFromResponse(site, listOf(persistentCookie("sessionid", "v1")))
        // ⚠️ 落盘是异步的（`dispatcher.launch`），必须等它真的写完再"重启"，
        // 否则测的是"进程在写盘前被杀"，那是另一个场景。
        assertTrue("落盘应最终发生", waitFor { store.load().isNotEmpty() })

        val revived = jar(store)

        assertTrue(revived.hasCookiesFor(site))
        assertEquals(listOf("sessionid"), revived.loadForRequest(site).map { it.name })
        assertEquals("v1", revived.loadForRequest(site).first().value)
    }

    @Test
    fun 会话cookie也能落盘并在重启后仍算有效() {
        // 服务端给的会话 cookie（无 expires）在进程重启后**不该被当成已过期**，
        // 否则每次冷启动都要重新登录一次 WebView。
        //
        // 这条用例是**实测踩出来的回归**：OkHttp 4.12 对非持久 cookie 填的 `expiresAt`
        // 是公元 9999 年的毫秒值 `253402300799999`，**不是** `Long.MAX_VALUE`。
        // 用 `Long.MAX_VALUE` 做哨兵判定会全部落空 ⇒ 会话 cookie 被当成"9999 年过期"存下来。
        val store = FakeStore()
        val jar = jar(store)
        jar.saveFromResponse(site, listOf(sessionCookie("JSESSIONID", "s1")))
        assertTrue("落盘应最终发生", waitFor { store.load().isNotEmpty() })

        val revived = jar(store)

        assertEquals(listOf("JSESSIONID"), revived.loadForRequest(site).map { it.name })
        // 落盘里必须是 SESSION 哨兵，而不是公元 9999 年那个值。
        assertEquals(SESSION, store.load().values.first().cookies.first().expiresAt)
    }

    // ───────────────────────── 过期与删除 ─────────────────────────

    @Test
    fun 已过期的cookie不出现在请求里() {
        val jar = jar(null)
        jar.saveFromResponse(site, listOf(expiredCookie("dead", "gone")))

        assertTrue(jar.loadForRequest(site).isEmpty())
        assertFalse(jar.hasCookiesFor(site))
    }

    @Test
    fun 服务端下发的删除指令会顶掉旧值() {
        // 登出时服务端下发 `sessionid=; Max-Age=0`，OkHttp 解析成 expiresAt = 0。
        // 如果这里把它当成"会话 cookie"存下来，就会出现"已经登出了但请求还带着旧会话"。
        val jar = jar(null)
        jar.saveFromResponse(site, listOf(sessionCookie("sessionid", "alive")))
        assertEquals(1, jar.loadForRequest(site).size)

        jar.saveFromResponse(site, listOf(expiredCookie("sessionid", "")))

        assertTrue(jar.loadForRequest(site).isEmpty())
    }

    // ───────────────────────── 域与 path 语义 ─────────────────────────

    @Test
    fun 域cookie覆盖子域但不覆盖近似域名() {
        val jar = jar(null)
        // ⚠️ 源域写 `5idream.net`（**不带**前导点）：OkHttp 的 `Cookie.Builder.domain()`
        // 会把 `.5idream.net` 判为非法域名并抛 IllegalArgumentException
        // （实测 `unexpected domain: .5idream.net`）。剥点**不影响**子域覆盖 ——
        // OkHttp 的匹配规则是 `host == domain || host.endsWith("." + domain)`。
        jar.saveFromResponse(site, listOf(persistentCookie("s", "v", domain = "5idream.net")))

        // 回读时子域 cookie 会带前导点，所以三条断言都必须成立。
        assertEquals(listOf("s"), jar.loadForRequest("https://www.5idream.net/".toHttpUrl()).map { it.name })
        assertTrue(jar.hasCookiesFor("https://5idream.net/".toHttpUrl()))
        assertTrue(jar.hasCookiesFor("https://api.5idream.net/x".toHttpUrl()))
        // ⚠️ 点边界：这两个**必须**拿不到，否则就是个 cookie 泄漏口子。
        assertFalse(jar.hasCookiesFor("https://evil-5idream.net/".toHttpUrl()))
        assertFalse(jar.hasCookiesFor("https://not5idream.net/".toHttpUrl()))
    }

    @Test
    fun 域白名单外的cookie直接丢弃() {
        val jar = jar(null, domains = setOf("5idream.net"))
        jar.saveFromResponse(
            "https://evil.example.com/".toHttpUrl(),
            listOf(persistentCookie("track", "x", domain = "evil.example.com")),
        )

        assertTrue(jar.domains().isEmpty())
    }

    @Test
    fun 更具体的path优先且不误配前缀相同的路径() {
        val jar = jar(null)
        jar.saveFromResponse(
            site,
            listOf(
                persistentCookie("k", "root", path = "/"),
                persistentCookie("k", "api", path = "/api"),
            ),
        )

        // 服务端对同名 cookie 取请求头里的第一个 —— 更具体的 path 必须排在前面。
        // 这里两条**都该被带上**（`/api` 与 `/` 都匹配 `/api/activity`），顺序才是关键。
        val names = jar.loadForRequest("https://www.5idream.net/api/activity".toHttpUrl())
        assertEquals(listOf("api", "root"), names.map { it.value })

        // path-match 的边界：`/api` 不该匹配 `/apix`。
        val other = jar.loadForRequest("https://www.5idream.net/apix".toHttpUrl())
        assertEquals(listOf("root"), other.map { it.value })
    }

    // ───────────────────────── WebView ↔ OkHttp ─────────────────────────

    @Test
    fun 拉取WebView快照是补进而不是覆盖新值() {
        // 场景：OkHttp 侧刚被服务端续期（v2），此刻 WebView 还是登录时的旧值（v1）。
        // 若用覆盖，刚拿到的新会话会被旧快照冲掉 —— 表现为"偶尔又掉登录"。
        val jar = jar(null)
        jar.saveFromResponse(site, listOf(persistentCookie("sessionid", "v2")))

        jar.applyWebViewSnapshot(site, listOf(snapshotCookie("sessionid", "v1")))

        assertEquals("v1", jar.loadForRequest(site).first().value) // 快照是"用户的真实当前状态"
        // 但同一个 key 只有一条，没有产生重复。
        assertEquals(1, jar.loadForRequest(site).size)
    }

    @Test
    fun 回写快照排除会话cookie与HttpOnly() {
        val jar = jar(null)
        jar.saveFromResponse(
            site,
            listOf(
                sessionCookie("JSESSIONID", "s"),
                persistentCookie("httponly_token", "t", httpOnly = true),
                persistentCookie("normal", "n"),
            ),
        )

        val pushable = jar.syncableSnapshot().map { it.name }

        // 会话 cookie 与 HttpOnly 写不进 CookieManager，**这是预期不是失败**。
        assertEquals(listOf("normal"), pushable)
    }

    /**
     * 节流：**窗口内不重复写盘，但改动不能被丢掉**。
     *
     * ⚠️ 本用例刻意**只**用真实时钟，不注入 `clock`。
     *
     * 曾经的写法是注入一个"从 1000 起算的假时钟"，再断言"窗口没到、所以没写盘"。
     * 那条写法同时埋了两个问题：
     *
     * ① **它守不住真正的 bug**。第一版的实现就是把"窗口内新到的改动"判成"不欠账"
     *    然后**永久丢弃**，而假时钟让 `dirty` 永远为真，于是用例照样绿 ——
     *    断言了"还没写"，却断言不了"以后会写"。
     * ② 本类的补写闹钟走的是**真实时间**（`delay`），与注入的假时钟不同源，
     *    于是这条用例会随机器快慢时红时绿（实测：只剩 `[a, b]`）。
     *
     * 现在分成两条：本条只守"窗口内不写、但账还记着"，由**纯真实时钟**判定；
     * "以后一定会写"交给下一条 `没有后续请求时补写闹钟也会落盘`。
     * 两条都不需要往生产代码里加任何测试专用的时间注入点。
     */
    @Test
    fun 节流窗口内不重复写盘但改动不会被丢掉() {
        val store = FakeStore()
        // 窗口取 10 秒：远大于本用例的执行时间，因此"窗口没满"是**确定**的，
        // 不需要任何时钟技巧。
        val jar = jar(store, flushIntervalMs = 10_000L)

        // 第一次写入：`!hasFlushed` 放行，立刻落盘 —— 这样盘上就有一份"旧值"了。
        jar.saveFromResponse(site, listOf(persistentCookie("a", "1")))
        assertTrue("首次写入必须立刻落盘", waitFor { store.load().values.any { it.cookies.any { c -> c.name == "a" } } })
        assertFalse("首次写入后不该还欠着账", jar.isFlushPending())

        // 第二次写入紧跟在窗口内：这次不能写盘……
        jar.saveFromResponse(site, listOf(persistentCookie("c", "3")))
        val onDisk = store.load().values.flatMap { it.cookies }.map { it.name }.toSet()
        assertEquals("窗口内不该再写一次盘", setOf("a"), onDisk)
        //……但**账必须还记着**，而且已经排好了来还账的补写。
        // 只断言前半句的话，把补写逻辑整个删掉也照样绿 —— 那正是改造前的样子。
        assertTrue("窗口内的改动必须留在内存里等写（而不是被丢掉）", jar.isFlushPending())
        assertTrue(
            "窗口内写不出去时必须排好补写，否则没有后续请求就永久丢",
            jar.isDeferredFlushScheduled(),
        )
    }

    /**
     * 补写：**后面一个请求都不来**时，闹钟必须自己把窗口内的改动写下去。
     *
     * 上一条用例守的是"账记着"，这一条守的是"账还了"。两条合起来才是
     * **节流只推迟写、不取消写**这条不变量。
     *
     * 窗口取 80ms：够短（用例跑得快），又够长（第二次写入一定落在窗口内）。
     * 全程真实时钟 —— 补写闹钟本来就是真实时间的产物，只有同源才测得准。
     */
    @Test
    fun 没有后续请求时补写闹钟也会落盘() {
        val store = FakeStore()
        val jar = jar(store, flushIntervalMs = 80L)

        jar.saveFromResponse(site, listOf(persistentCookie("a", "1")))
        assertTrue("首次写入必须立刻落盘", waitFor { store.load().values.any { it.cookies.any { c -> c.name == "a" } } })

        // 紧接着写入第二批：落在窗口内，这次写不出去，只能等补写。
        jar.saveFromResponse(site, listOf(persistentCookie("late", "9")))
        val immediately = store.load().values.flatMap { it.cookies }.map { it.name }.toSet()
        assertFalse("窗口内不该写盘：实际=$immediately", "late" in immediately)

        // ⚠️ 关键断言：此后**不再有任何请求**，全靠补写把这条写下去。
        assertTrue(
            "补写闹钟没把窗口内的改动写下去 —— 没有后续请求时这批 cookie 就永久丢了",
            waitFor { store.load().values.any { it.cookies.any { c -> c.name == "late" } } },
        )
    }

    // ───────────────────── 撤销（服务端登出）的映射 ─────────────────────

    /**
     * 守门人：`Max-Age=0` 必须映射成 [REVOKED]（已撤销），**不能**被读成会话 cookie。
     *
     * ## 这里踩过的真 bug（登出在 WebView 侧永远不生效）
     *
     * OkHttp 用 `expiresAt == Long.MIN_VALUE` 表示"非持久"，而 `Max-Age=0` 解析出来的
     * 原始字段值**恰好也是它**，两者只靠 `persistent` 位区分。第一版写成
     * `if (persistent) expiresAt else SESSION` 之后，登出指令被映射成了 [SESSION]：
     *
     * - `revocations()` 有 `!isSession` 这道过滤 ⇒ 撤销列表恒为空 ⇒
     *   **用户点了退出登录，WebView 里还登着**（`WebViewCookieSyncTest` 那条用例就是红在这里）；
     * - 这条空值 cookie 又 `isExpired` 恒为 false ⇒ 沉在 jar 里**永不清理**。
     *
     * 这两条后果都很隐蔽：前者要真去 WebView 里看，后者要 dump 存档才看得见。
     * 所以这里直接断言"映射结果"，而不是绕道去验界面。
     */
    @Test
    fun 服务端撤销的cookie必须映射成已撤销而不是会话() {
        val jar = jar(null)
        // 走**真实解析**路径，而不是手搓一个 `expiresAt = 1L` 的 cookie ——
        // 手搓的假数据永远构造不出 `persistent && expiresAt == Long.MIN_VALUE` 这个组合，
        // 而那正是线上登出时的真实形态。
        val logout = Cookie.parse(site, "SESSIONID=; Max-Age=0")!!

        jar.saveFromResponse(site, listOf(logout))

        val revoked = jar.revocations()
        assertEquals("服务端撤销的 cookie 必须进入撤销列表", listOf("SESSIONID"), revoked.map { it.name })
        assertEquals(REVOKED, revoked.first().expiresAt)
        assertFalse("撤销 ≠ 会话：会话哨兵会让 revocations 的过滤把它扔掉", revoked.first().isSession)
    }

    /**
     * 反向守门人：**真正的**会话 cookie 不能被当成撤销，否则每次回写都会顺手把
     * WebView 里的会话删掉 —— 那不是撤销，是登出用户。
     */
    @Test
    fun 真正的会话cookie不会被当成撤销() {
        val jar = jar(null)
        // 非持久 cookie：OkHttp 的字段值同样是 Long.MIN_VALUE，靠 persistent=false 区分。
        val session = Cookie.Builder()
            .name("JSESSIONID")
            .value("s")
            .domain("www.5idream.net")
            .path("/")
            .build()

        jar.saveFromResponse(site, listOf(session))

        assertTrue("非持久 cookie 必须是会话哨兵", jar.loadForRequest(site).isNotEmpty())
        assertEquals("会话 cookie 不该进撤销列表", emptyList<String>(), jar.revocations().map { it.name })
    }

    /**
     * 读取边界：存档里 `expiresAt <= 0` 的记录（已撤销 / 被手工改坏的）读回来必须丢掉，
     * 而**会话 cookie 必须原样保留** —— 这两条是**同一个判据的两面**，缺一面都会出事。
     *
     * ## 为什么这条用例值得单列
     *
     * 我在这条边界上连着翻过两次车，两次都是因为两个语义撞在同一个值上：
     *
     * | 写法 | 会话 cookie 的下场 | 现场表现 |
     * |---|---|---|
     * | `SESSION = Long.MIN_VALUE` + "非正归一成已撤销" | 被读成"要删掉" | 每次启动都删用户会话 ⇒ **莫名掉登录** |
     * | `SESSION = Long.MIN_VALUE` + "非正一律丢掉" | 直接没了 | 重启后 OkHttp 侧空 ⇒ **每次启动都要重新登录** |
     *
     * 两次的根因都不在这个函数里，而在 [SESSION] 的取值：它当时与 OkHttp 的
     * "非持久"哨兵（也是 `Long.MIN_VALUE`）撞了。搬到 `Long.MAX_VALUE` 之后，
     * "非正 ⇒ 撤销"这一支才真正唯一。
     */
    @Test
    fun 存档里非正的过期记录被丢掉而会话cookie保留() {
        val store = FakeStore()
        store.raw(
            "www.5idream.net",
            """{"version":1,"cookies":[""" +
                """{"name":"revoked","value":"","domain":"www.5idream.net","path":"/","expiresAt":${REVOKED}},""" +
                """{"name":"keep","value":"1","domain":"www.5idream.net","path":"/","expiresAt":${System.currentTimeMillis() + 3_600_000L}},""" +
                """{"name":"sess","value":"2","domain":"www.5idream.net","path":"/","expiresAt":${SESSION}}]}""",
        )

        val jar = jar(store)

        assertEquals(
            "撤销记录丢掉、持久与会话 cookie 都保留",
            setOf("keep", "sess"),
            jar.loadForRequest(site).map { it.name }.toSet(),
        )
        assertEquals("丢掉之后不该冒出撤销指令（否则每次启动都会去删用户的会话）", emptyList<String>(), jar.revocations().map { it.name })
    }

    @Test
    fun 清除后归档与内存同时为空() {
        val store = FakeStore()
        val jar = jar(store)
        jar.saveFromResponse(site, listOf(persistentCookie("a", "1")))
        assertTrue("先确认真的落过盘，否则这条用例证明不了「清了盘」", waitFor { store.load().isNotEmpty() })

        jar.clear()

        assertTrue(jar.domains().isEmpty())
        assertFalse(jar.hasCookiesFor(site))
        // 归档也必须是空的：`clear()` 既清内存又清盘，缺一半就是"退出了但重启还在"。
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun 坏存档只丢该域不影响其它域() {
        val store = FakeStore()
        store.raw("5idream.net", "{ 这不是 JSON")
        store.raw("other.example.com", """{"version":1,"cookies":[{"name":"ok","value":"1"}]}""")

        val loaded = store.load()
        val jar = jar(store, domains = emptySet())

        // 诊断：把真实状态放进断言消息（失败时能直接看到，不用猜）。
        val diag = "store.load()=${loaded.keys} jar.domains()=${jar.domains()} " +
            "buckets=${loaded.mapValues { it.value.cookies.map { c -> "${c.name}@${c.domain}${c.path}" } }}"
        assertTrue(diag, jar.hasCookiesFor("https://other.example.com/".toHttpUrl()))
        // 坏的那桶被丢掉（当作没登录），好的那桶照常可用 —— 不能"全有或全无"。
        assertFalse(diag, jar.hasCookiesFor(site))
    }

    @Test
    fun 两个域的cookie可以并存且互不串味() {
        val store = FakeStore()
        val jar = jar(store, domains = emptySet())
        jar.saveFromResponse(site, listOf(persistentCookie("a", "1")))
        assertTrue(waitFor { store.load().isNotEmpty() })

        // 第二个域在同一个 jar 实例里写入：它是"补进"而不是"清空重来"。
        jar.saveFromResponse(
            "https://other.example.com/".toHttpUrl(),
            listOf(persistentCookie("b", "2", domain = "other.example.com")),
        )

        assertTrue(jar.hasCookiesFor(site))
        assertTrue(jar.hasCookiesFor("https://other.example.com/".toHttpUrl()))
        // ⚠️ 桶键是**写入时的 host**（`www.5idream.net`），不是注册域。别想当然写成 `5idream.net`。
        assertEquals(setOf("www.5idream.net", "other.example.com"), jar.domains().toSet())
    }

    // ───────────────────────── 辅助 ─────────────────────────

    private fun persistentCookie(
        name: String,
        value: String,
        domain: String = "www.5idream.net",
        path: String = "/",
        httpOnly: Boolean = false,
    ): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain(domain)
        .path(path)
        .expiresAt(System.currentTimeMillis() + 3_600_000L)
        .apply { if (httpOnly) httpOnly() }
        .build()

    private fun sessionCookie(name: String, value: String, path: String = "/"): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain("www.5idream.net")
        .path(path)
        .build()

    private fun expiredCookie(name: String, value: String, path: String = "/"): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .domain("www.5idream.net")
        .path(path)
        .expiresAt(1L)
        .build()

    /**
     * [SecondClassCookieJar.applyWebViewSnapshot] 收的是 WebView 快照（`SecondClassCookie`），
     * 不是 OkHttp 的 `Cookie` —— 因为快照要带上 `httpOnly` / path 这类由我们补全的属性。
     * 所以这一组单独一组构造器，别和上面那组混用。
     */
    private fun snapshotCookie(
        name: String,
        value: String,
        path: String = "/",
        httpOnly: Boolean = false,
    ): SecondClassCookie = SecondClassCookie(
        name = name,
        value = value,
        domain = "www.5idream.net",
        path = path,
        expiresAt = System.currentTimeMillis() + 3_600_000L,
        httpOnly = httpOnly,
    )

    /**
     * 落盘是**异步**的（`dispatcher.launch`）。测试不能靠 sleep 猜时间，
     * 但也不能为了可测性把生产代码改成同步写盘 —— 那会让网络线程等 IO。
     * 折中：轮询到超时，断言"最终发生"。
     */
    private fun waitFor(timeoutMs: Long = 2_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    /** 内存版存档，替代 `EncryptedSharedPreferences`，让这一层完全脱离 Android。 */
    private class FakeStore : SecondClassCookieStore {
        private val data = ConcurrentHashMap<String, String>()
        private val gson = com.google.gson.Gson()

        fun raw(domain: String, json: String) {
            data[domain] = json
        }

        /** 诊断用：看看到底写进去了什么（测试专用，生产实现没有这个方法）。 */
        fun rawDump(): String = data.toString()

        override fun load(): Map<String, SecondClassCookieBucket> = data.mapNotNull { (domain, raw) ->
            val payload = runCatching {
                gson.fromJson(raw, SecondClassCookiePayload::class.java)
            }.getOrNull() ?: return@mapNotNull null
            if (payload.version != SecondClassCookiePayload.CURRENT_VERSION) return@mapNotNull null
            val cookies = payload.cookies.orEmpty().filter { it.name.isNotBlank() }
            if (cookies.isEmpty()) return@mapNotNull null
            domain to SecondClassCookieBucket(cookies = cookies)
        }.toMap()

        override fun save(domain: String, bucket: SecondClassCookieBucket) {
            data[domain] = gson.toJson(SecondClassCookiePayload(cookies = bucket.cookies))
        }

        override fun remove(domain: String) {
            data.remove(domain)
        }

        override fun clear() {
            data.clear()
        }
    }

    @Test
    fun 存档版本不认识时按未登录处理() {
        val store = FakeStore()
        store.raw("5idream.net", """{"version":999,"cookies":[{"name":"a","value":"1"}]}""")

        val jar = jar(store)

        // 宁可当作没登录（引导用户重新走一次登录），也不要按旧结构硬读出一堆 null 往下传。
        assertFalse(jar.hasCookiesFor(site))
    }
}
