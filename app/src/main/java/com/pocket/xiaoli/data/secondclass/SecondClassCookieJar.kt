package com.pocket.xiaoli.data.secondclass

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * 第二课堂专用的 OkHttp Cookie 容器，**同时是 WebView 登录态的落地处**。
 *
 * ```
 *   WebView 登录成功
 *        │  CookieManager.getCookie(url)          ← 只有 "a=1; b=2"，没有属性
 *        ▼
 *   [ WebViewCookieSync.readSnapshot ]  造出 domain/path/secure，还回结构化 Cookie
 *        │
 *        ▼
 *   [ SecondClassCookieJar.applyWebViewSnapshot ]  ← 本类
 *        │  loadForRequest(url)
 *        ▼
 *   OkHttp 请求（活动列表 / 详情 / 成绩单）
 *        │  saveFromResponse(url, cookies)       ← 服务端续期后的 Set-Cookie
 *        ▼
 *   [ WebViewCookieSync.applyToWebView ]  生成 Set-Cookie 并逐条 setCookie
 *        │
 *        ▼
 *   CookieManager（下次 WebView 打开仍是登录态）
 * ```
 *
 * ## 为什么"双向"是必须的，而不是"登录时同步一次就够"
 *
 * 登录时把 cookie 抄进 OkHttp 只解决**第一次**请求。此后服务器会在响应里续期
 * （换新的 session id、延长过期时间），**只有 OkHttp 这一侧知道**。如果不同步回 WebView：
 *
 * - WebView 里那份还是登录时的旧值 → 用户下次点开网页版又要登录一次；
 * - 更糟的是**两个会话同时存在**，服务端可能判定为异常登录。
 *
 * 反过来，用户也可能在 WebView 里重新登录（会话过期后重新输验证码、换账号），
 * 那一侧的 cookie 只有 `CookieManager` 知道。所以两边都必须能推给对方。
 *
 * ## 线程模型
 *
 * OkHttp 的 `loadForRequest` / `saveFromResponse` 跑在**网络线程**，
 * `CookieManager` 则**必须**在主线程访问。本类内部：
 *
 * - 数据用 [ReentrantReadWriteLock] 保护（读多写少，正好对应读写锁）；
 * - 回写 WebView 的动作派发到主线程（由 `WebViewCookieSync` 完成），不阻塞请求；
 * - 落盘派发到**单线程 IO**，并且节流（见 [flushIfDue]）。
 *
 * ⚠️ **读与写不能交叉**：回写 WebView 时外层持有的读锁覆盖"读取快照 + 逐条 setCookie"
 * 整个过程，否则用户可能在 WebView 里看到"一半旧一半新"的 cookie 集。
 *
 * @param strictDomains 归属域白名单（小写、可带前导点）。非空时**只接受**这些域及其子域的
 *   cookie。必须是白名单而不是黑名单：这个模块只服务用户本人的一个站点，
 *   把"接受谁"写死比"拒绝谁"更安全 —— 黑名单漏一个就是把第三方域的 cookie 也收进来了。
 * @param flushIntervalMs 落盘节流窗口。默认 500 ms，与 `docs/架构-Agent设计.md` 里
 *   PersistentCookieJar 的口径一致。
 * @param clock 注入时钟，**只为测试节流**；生产不要动它。
 */
class SecondClassCookieJar(
    private val store: SecondClassCookieStore?,
    private val strictDomains: Set<String> = DEFAULT_DOMAINS,
    private val flushIntervalMs: Long = DEFAULT_FLUSH_INTERVAL_MS,
    @Volatile var clock: () -> Long = System::currentTimeMillis,
) : CookieJar {

    private val lock = ReentrantReadWriteLock()
    private val buckets = LinkedHashMap<String, SecondClassCookieBucket>()

    /** 严格单调的写入序号，**只**用于让同 path 长度的 cookie 有稳定的先后次序。 */
    private val sequence = AtomicLong(0)

    private val dispatcher = CoroutineScope(SupervisorJob() + CoroutineName("SecondClassCookieJar"))
    private val executor: ExecutorService = newCookieIoExecutor()
    private val ioDispatcher: CoroutineDispatcher = testDispatcher ?: executor.asCoroutineDispatcher()

    /** 是否已排好一次延迟补写。见 [scheduleDeferredFlush]。 */
    private val deferredFlushScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        // 回读落盘时**不再过一遍白名单**：桶键在写入时已经筛过了，而白名单问的是
        // "接不接受这个来源下发的 cookie"，不是"这个域还在不在管辖内"。
        // 在这里复用白名单会导致"存档里明明有、却被静默丢掉"（已踩：存档里有别的域时整桶消失）。
        //
        // 但必须**重新归零**每个桶的状态：
        // - cookie 的 `domain`/`path` 缺失时要能自愈。那种 cookie 会变成"永远匹配不上"
        //   （`domainMatches(host, "")` 恒为 false）—— 老版本存档、或将来给 JSON 加字段时
        //   读到 null 就会落进这个坑（`CLAUDE.md` 的 Gson 默认值陷阱）。
        //   域缺失时用**桶键**兜底，因为桶键本身就是写入时的 host。
        // - 实时状态字段（dirty / lastDirtyAt / lastFlushedAt）**绝不能**从存档恢复：
        //   它们描述的是"本次进程内还没做的事"，恢复过去的 true 会让启动后就白白写一次盘。
        store?.load()?.forEach { (domain, bucket) ->
            buckets[domain] = SecondClassCookieBucket(
                cookies = bucket.cookies.mapNotNull { sanitize(it, domain) },
            )
        }
    }

    /**
     * 补全一条从存档读出来的 cookie 的缺失属性；**撤销记录直接丢弃**。
     *
     * ### 为什么丢的是"非正"这一支，而不是丢"会话"
     *
     * 现在 [SESSION] = `Long.MAX_VALUE`、[REVOKED] = `0`，三支互不重叠：
     * 非正 ⇒ 已撤销（一次性、不该跨进程存活，丢掉）、`Long.MAX_VALUE` ⇒ 会话
     * （**必须保留**：真实需求是"会话 cookie 也要能跨进程存活"，
     * 否则重启就要重新登录，`会话cookie也能落盘并在重启后仍算有效` 就是守它的）、
     * 其余 ⇒ 真实时刻。
     *
     * ⚠️ 我在这条边界上翻过两次车，两次都是因为两个语义撞在同一个值上：
     * ① 把 `<= 0` 归一成"已撤销" ⇒ 老存档里的会话 cookie 被读成"要删掉"；
     * ② 把 `<= 0` 一律丢掉 ⇒ 会话 cookie 一起没了，重启就掉登录。
     * 所以真正的修法不在这个函数里，而在 [SESSION] 的取值上 —— 这里只是照三支写。
     *
     * @return `null` 表示这条记录该被丢弃，调用方用 `mapNotNull` 过滤。
     */
    private fun sanitize(cookie: SecondClassCookie, fallbackDomain: String): SecondClassCookie? {
        if (cookie.expiresAt <= 0L) return null
        return cookie.copy(
            name = cookie.name.trim(),
            domain = cookie.domain.trim().trimStart('.').ifBlank { fallbackDomain.trimStart('.') },
            path = cookie.path.trim().ifBlank { "/" },
        )
    }

    // ────────────────────────────── OkHttp 侧 ──────────────────────────────

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clock()
        val host = url.host.lowercase(Locale.ROOT)
        return lock.read {
            buckets.values.asSequence()
                .flatMap { it.cookies.asSequence() }
                .filter { domainMatches(host, it.domain) && pathMatches(url.encodedPath, it.path) }
                .filterNot { it.isExpired(now) }
                // 长 path 在前：服务端对同名 cookie 取请求头里的**第一个**，而 RFC 6265 要求
                // 更具体的 path 优先。顺序错了就是"深路径下用了站点根的值"。
                // 同 path 长度时**新的在前**，靠写入序号 [SecondClassCookie.seq] 定序 ——
                // 它是显式写入的，所以结果可测，不依赖 Map 的遍历顺序。
                .sortedWith(compareByDescending<SecondClassCookie> { it.path.length }.thenByDescending { it.seq })
                .mapNotNull { runCatching { it.toOkHttpCookie() }.getOrNull() }
                .toList()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host.lowercase(Locale.ROOT)
        val accepted = cookies.filter { domainMatches(host, it.domain) }
        if (accepted.isEmpty()) return
        lock.write {
            for (cookie in accepted) {
                val bucketKey = bucketKeyOf(cookie.domain)
                val bucket = buckets.getOrPut(bucketKey) { SecondClassCookieBucket() }
                val incoming = cookie.toSecondClassCookie()
                buckets[bucketKey] = bucket.copy(
                    cookies = upsert(bucket.cookies, incoming),
                    dirty = true,
                    lastDirtyAt = clock(),
                )
            }
        }
        flushIfDue()
    }

    // ─────────────────────── WebView → OkHttp（拉取） ───────────────────────

    /**
     * 用 WebView 当前快照**补进** OkHttp 的 cookie。
     *
     * 是"补进"（合并）而不是"覆盖"：OkHttp 侧可能持有服务端刚续期、而 WebView 还没有的值。
     * 覆盖会让刚拿到的新会话被旧快照冲掉，表现为"偶尔又掉登录"。
     *
     * 唯一例外是**账号切换**：调用方必须先 [clear]，否则同一域下的旧账号 cookie 仍在。
     *
     * ⚠️ 这是**用户手工行为**（在 WebView 里登录成功），所以立刻强制落盘：
     * 用户刚输完验证码，随后进程被杀也要能免登。
     */
    fun applyWebViewSnapshot(url: HttpUrl, snapshot: List<SecondClassCookie>) {
        if (snapshot.isEmpty()) return
        val host = url.host.lowercase(Locale.ROOT)
        val accepted = snapshot.filter { domainMatches(host, it.domain) && it.name.isNotBlank() }
        if (accepted.isEmpty()) return
        lock.write {
            for (cookie in accepted) {
                val bucketKey = bucketKeyOf(cookie.domain)
                val bucket = buckets.getOrPut(bucketKey) { SecondClassCookieBucket() }
                buckets[bucketKey] = bucket.copy(
                    cookies = upsert(bucket.cookies, cookie),
                    dirty = true,
                    lastDirtyAt = clock(),
                )
            }
        }
        flush(force = true)
    }

    // ─────────────────────── OkHttp → WebView（回写） ───────────────────────

    /**
     * 取出**已经作废、需要从 WebView 删掉**的 cookie。
     *
     * ## 为什么单开一个方法，而不是让 [syncableSnapshot] 带上它们
     *
     * 服务端登出时下发 `SESSIONID=; Max-Age=0`，OkHttp 把它解析成一条
     * `expiresAt` 在过去某刻的 cookie。这条 cookie **永远不会**出现在
     * [syncableSnapshot] 里（那里按"未过期"过滤），于是：
     *
     * - WebView 里那份旧会话**删不掉** —— 用户点了"退出登录"，网页里还是登着；
     * - 更糟的是它会被 [hasCookiesFor] 判为"没登录"，UI 引导重新登录，
     *   而 WebView 一打开又把人当已登录，两边状态长期分叉。
     *
     * 所以撤销是**独立的一类同步**：判据是"`expiresAt` 是一个**具体**的过去时刻"。
     *
     * ⚠️ `!it.isSession` 这一道如今是**冗余的**（[SecondClassCookie.isExpired] 自己就有
     * `!isSession` 短路）。保留它是为了在有人把那个短路"化简"掉时仍然拦得住 ——
     * 会话 cookie 若被当成"已过期"，每次回写都会把 WebView 里所有会话 cookie 删掉，
     * 那不是撤销，是登出用户。
     *
     * ⚠️ 本段**曾经**写着「[SESSION] = `Long.MIN_VALUE`，哨兵恰好也'早于现在'」——**那个值已经搬走了**。
     * 见 `SecondClassCookie.SESSION` 的 KDoc 与架构文档 §9.1：`Long.MIN_VALUE` 与 OkHttp 的
     * "非持久"哨兵撞车，害得**登出彻底失效**。现在 `SESSION = Long.MAX_VALUE`，**大于**任何真实时刻，
     * 所以"早于现在"那条理由不再成立。**不要按旧值去推理，更不要"改回去"。**
     */
    fun revocations(): List<SecondClassCookie> = lock.read {
        buckets.values.asSequence()
            .flatMap { it.cookies.asSequence() }
            .filter { !it.isSession && it.isExpired(clock()) && it.name.isNotBlank() }
            .distinctBy { it.key }
            .toList()
    }

    /** 取出**可以回写** WebView 的 cookie 快照。
     *
     * 过滤掉会话 cookie 与 HttpOnly —— 它们写不进去也不该写（见
     * [SecondClassCookie.canBePushedToWebView]）。这**不是**同步失败，不要当错误上报。
     * 已作废的 cookie 走 [revocations] 那条通道（否则登出指令发不出去）。
     */
    fun syncableSnapshot(now: Long = clock()): List<SecondClassCookie> = lock.read {
        buckets.values.asSequence()
            .flatMap { it.cookies.asSequence() }
            .filter { it.canBePushedToWebView && !it.isExpired(now) }
            .toList()
    }

    /**
     * 当前是否持有某个域下任一未过期的 cookie。用它做"能不能走原生查询"的初判。
     *
     * 只看**有没有**，不校验服务端是否已踢掉会话 —— 后者只有真发一次请求才知道，
     * 判定归 Repository 层（`Unauthorized` → 引导重新登录）。
     */
    fun hasCookiesFor(url: HttpUrl): Boolean {
        val now = clock()
        val host = url.host.lowercase(Locale.ROOT)
        return lock.read {
            buckets.values.asSequence()
                .flatMap { it.cookies.asSequence() }
                .any { domainMatches(host, it.domain) && !it.isExpired(now) }
        }
    }

    /**
     * 按名字取一条**当前可用**的 cookie 值 —— **包含会话 cookie**。
     *
     * ## 为什么必须单开一个方法，不能复用 [syncableSnapshot]
     *
     * [syncableSnapshot] 的过滤条件是 [SecondClassCookie.canBePushedToWebView]，而它是
     * `!isSession && !httpOnly` —— 也就是说**会话 cookie 根本不在那份快照里**。
     * 而本模块最要紧的一条 cookie（`dmkj_web_token`）恰恰是会话 cookie：
     * 平台的 `login.js` 用 `$.cookie("dmkj_web_token", tok, {path:"/"})` 写入，**没带 `expires`**。
     *
     * 拿 [syncableSnapshot] 去读 token 的后果很好认、也很难查：**永远读到 `null`**，
     * 于是永远判"未登录"，而 WebView 里明明登着。
     * （token 确实进得来：用户手工登录走 [applyWebViewSnapshot]，那里只查域与名字。）
     *
     * ## 判据
     *
     * - 只按名字匹配：本 jar 按 [DEFAULT_DOMAINS] 严格限域，进来的必然是该域的 cookie；
     * - **排除已过期与已撤销**：撤销的哨兵是 `0`（见 [REVOKED]），它落在
     *   [SecondClassCookie.isExpired] 的"非会话"那一支，会被正确剔除；
     * - 同名多条时取**最后写入**的那条（[SecondClassCookie.seq] 最大）。
     */
    fun valueOf(name: String, now: Long = clock()): String? = lock.read {
        buckets.values.asSequence()
            .flatMap { it.cookies.asSequence() }
            .filter { it.name == name && it.value.isNotBlank() && !it.isExpired(now) }
            .maxByOrNull { it.seq }
            ?.value
    }

    // ────────────────────────────── 维护 ──────────────────────────────

    /**
     * 清空全部 cookie。**退出账号 / 换账号时必须调用**。
     *
     * `CookieManager` 那一侧的清理是 `WebViewCookieSync.clearWebView()` 的职责 ——
     * 本类碰不到 WebView 的状态，两处都要清，缺一处就是"退出了但还登着"。
     */
    fun clear() {
        lock.write { buckets.clear() }
        runCatching { store?.clear() }
            .onFailure { Log.w(TAG, "清除第二课堂 Cookie 落盘失败", it) }
    }

    /** 供日志/调试的域列表，**不含任何 cookie 值**。 */
    fun domains(): List<String> = lock.read { buckets.keys.toList() }

    /**
     * 现在是否存在"该写但还没写"的桶。**只给测试用**（生产代码不需要这个观测点）。
     *
     * 把它单独暴露出来的理由：节流逻辑曾经真错过一次（见 [flushIfDue]），
     * 而那个错误从外部表现上只是"盘里的值旧了一点"，很容易被别的断言掩盖。
     *
     * ⚠️ 注意它与 [isDeferredFlushScheduled] 是**两个**概念：
     * - 本方法是"欠着账"（数据还在内存里，盘上是旧的）；
     * - 那个是"已经排好了补写的闹钟"（保证欠账最终会还）。
     *
     * 只断言前者会漏掉"欠着账但没人来还"这种最坏情况 ——
     * 所以两条用例要把它们**成对**断言。
     */
    internal fun isFlushPending(): Boolean = lock.read { buckets.values.any { it.dirty } }

    /**
     * 是否已经排好了一次**延迟补写**。见 [scheduleDeferredFlush]。
     *
     * 它是"节流只推迟、不取消"这条不变量唯一可断言的外部表现：
     * 没有它，`throw`-free 地把补写逻辑删掉，用例仍会全绿。
     */
    internal fun isDeferredFlushScheduled(): Boolean = deferredFlushScheduled.get()


    // ────────────────────────────── 内部 ──────────────────────────────

    /**
     * 节流落盘。**两个判据必须同时成立**：
     *
     * 1. 某个桶 `dirty`（真的有未落盘的改动）；
     * 2. 它的改动已经离开节流窗口（`now - lastDirtyAt >= flushIntervalMs`）。
     *
     * ### 为什么不是"只在 dirty 上做文章"
     *
     * 只靠 dirty 会退化成"每个响应都写盘" —— 一次列表翻页 + 详情预取会在几百毫秒内
     * 触发十几次 `saveFromResponse`，每次都序列化 + 加密写盘纯属浪费
     * （`EncryptedSharedPreferences` 每次 `create()` 有 50–200 ms 密钥派生开销，
     * 见 `docs/架构-Agent设计.md:153`）。
     *
     * ### ⚠️ 这里踩过一个真 bug，别再退回去
     *
     * 第一版拿 `now - updatedAt >= 窗口` 当"欠不欠账"的判据，而 `updatedAt` 是
     * **每次写入都刷成当前时刻**的。结果：窗口内新到的 cookie 让 `updatedAt` 又变成 now，
     * 差值恒为 0 ⇒ 判定"不欠账" ⇒ 这一批改动**永远不会被写盘**。
     * 节流于是变成了"永久丢弃"，而且只在"写入间隔小于窗口"时才复现（真实使用里最常见）。
     *
     * 关键区别：**"改动的时刻"（lastDirtyAt）与"上次落盘的时刻"（lastFlushedAt）
     * 是两个不同的量**，两个都要参与判据 —— 具体见 [windowElapsed] 的说明。
     * `落盘被节流但不会永远不写` 这条用例就是它的守门人。
     *
     * ### ⚠️ 第二个坑：**没有后续请求 = 这次的欠账没人来还**
     *
     * 本方法只在 `saveFromResponse` 里被调用。于是"窗口内的最后一次改动"落到一个
     * 尴尬位置：这一次判定不够格写盘，而**之后不会再有事件**来重新判定
     * （用户看完列表就退出了）—— 那批续期后的 cookie 就永远停在内存里，
     * 进程一杀就丢，表现为"刚登录完过一会儿又要重新登录"。
     *
     * 修法见 [scheduleDeferredFlush]：不够格要写盘时，**排一个"窗口满"的补写闹钟**，
     * 让节流只推迟写、不取消写。
     */
    private fun flushIfDue() {
        val now = clock()
        val canFlushNow = lock.read { buckets.values.any { it.dirty && windowElapsed(it, now) } }
        if (canFlushNow) {
            flush(force = false)
            return
        }
        // 还有脏桶却不够格 → 一定有"这次没写出去"的改动，必须留一个补写。
        val wouldFlushLater = lock.read { buckets.values.any { it.dirty } }
        if (wouldFlushLater) scheduleDeferredFlush()
    }

    /**
     * 排一次延迟补写：等满一个节流窗口后再尝试落盘。
     *
     * ## 为什么必须有它
     *
     * [flushIfDue] 只在新响应到达时才被问一次，所以"窗口内的最后一次改动"没有任何
     * 后续事件来触发它落盘。补写把这个洞堵上：**节流只推迟写、不取消写**。
     *
     * ## 为什么用 `delay` 而不是定时器/Handler
     *
     * 本类已经有一个**单线程** IO 调度器（[newCookieIoExecutor] 的注释解释了为什么
     * 不用 `Dispatchers.IO`），`delay` 在它上面挂起不会占住线程；
     * 而另起 Handler/Timer 就等于又造一套时序，还得自己处理取消。
     *
     * ## 为什么只需一个在飞的补写
     *
     * 补写是"再判定一次"，不是"写某一份快照" —— 到点后它自己会重新看当前桶，
     * 所以多个改动共用一个闹钟不会漏。用 [deferredFlushScheduled] 保证不会越攒越多
     * （每次响应排一个的话，一次翻页就能排十几条重复任务）。
     */
    private fun scheduleDeferredFlush() {
        if (deferredFlushScheduled.get()) return
        if (!deferredFlushScheduled.compareAndSet(false, true)) return
        dispatcher.launch(ioDispatcher) {
            try {
                kotlinx.coroutines.delay(flushIntervalMs)
                flush(force = false)
            } finally {
                // ⚠️ 必须放回 false，否则**一次**补写之后这个实例就再也排不出补写了 ——
                // 那正是"节流退化成永久丢弃"的第 2 版复现。
                deferredFlushScheduled.set(false)
            }
        }
    }


    /**
     * 时间闸门是否已过。[bucket] 必须传当前时刻的桶快照。
     *
     * 判据 = **距上次落盘 ≥ 窗口** 且 **距最后一次改动 ≥ 窗口**。
     *
     * ## ⚠️ 两个条件都不能省，这里踩过两次真 bug
     *
     * **① 只看"距最后一次改动"会退化成永久丢弃。** `lastDirtyAt` 每次写入都被刷成 now，
     * 所以只要请求持续快于窗口（真实使用里最常见：翻页 + 预取），差值恒为 0，
     * 这一批改动**永远不会**被写盘。第一版就是这么写的。
     *
     * **② 只看"距上次落盘"会在突发期写太多次。** 那样每过窗口就写一次，
     * 一次翻页十几条响应会写成好几趟，节流等于没做。加上"距最后一次改动"这一条，
     * 突发期内一直不写，**静默满一个窗口后写一次** —— 这正是"每次交互一个写窗口"的语义。
     *
     * `!hasFlushed` 单独放行：首次写入时 `lastDirtyAt` 刚被刷成 now，
     * 纯时间差判据会把"登录后拿到的第一批 cookie"一直卡到下次操作才写。
     */
    private fun windowElapsed(bucket: SecondClassCookieBucket, now: Long): Boolean =
        !bucket.hasFlushed ||
            (now - bucket.lastFlushedAt >= flushIntervalMs && now - bucket.lastDirtyAt >= flushIntervalMs)

    /**
     * 把桶写盘。
     *
     * `dirty` 只在**成功交给 IO 之后**才清掉 —— 写盘失败时它保持置位，
     * 于是窗口过后下一次响应会重试，而不是静默丢一次更新。
     *
     * @param force 忽略时间闸门，立刻写（用于用户手工登录产生的快照 —— 那一刻
     *   进程随时可能被杀，不能等窗口）。
     *
     * ⚠️ **写入顺序依赖 [ioDispatcher] 是单线程**：任务按提交顺序执行，
     * 所以先提交的旧快照一定先落盘、后提交的新快照后落盘，不会出现
     * "新值被旧值覆盖"。若将来把它换成多线程派发器，这个保证就没了 ——
     * 那时必须改成"在 IO 线程里重新取当前桶"来消除竞态。
     */
    private fun flush(force: Boolean) {
        val target = store ?: return
        val now = clock()
        val pending: List<Pair<String, SecondClassCookieBucket>> = lock.write {
            // ⚠️ 这里**绝不能**写 `return@write` 或裸 `return`。
            //
            // 为什么裸 `return` 是灾难：`ReentrantReadWriteLock.write` 是 **inline** 函数
            // （`kotlin.concurrent.write`），lambda 里裸 `return` 会从**外层 `flush` 直接返回** ——
            // 于是 `pending` 拿不到值、后面的落盘永不执行，而调用方只看到"写了但没落盘"。
            //
            // 为什么 `return@write` 也不行（实测踩过）：它同样是对**外层函数**的非局部返回，
            // 在那条路径上 `pending` 变成 null，测试直接空转到超时（单条用例跑了 551 秒）。
            //
            // 所以这里写成 `if/else` 表达式，靠 lambda 的**尾表达式**给出结果。
            val due = buckets.filter { force || (it.value.dirty && windowElapsed(it.value, now)) }
            if (due.isEmpty()) {
                emptyList()
            } else {
                due.forEach { (domain, bucket) ->
                    buckets[domain] = bucket.copy(dirty = false, lastFlushedAt = now, hasFlushed = true)
                }
                due.map { it.key to it.value }
            }
        }
        if (pending.isEmpty()) return
        dispatcher.launch(ioDispatcher) {
            // 显式 catch：落盘失败只记日志，绝不影响正在进行的请求。
            runCatching { pending.forEach { (domain, bucket) -> target.save(domain, bucket) } }
                .onFailure {
                    Log.w(TAG, "第二课堂 Cookie 落盘失败，已重新标记待写以便重试", it)
                    // 失败必须把 dirty 放回去，否则这批更新永久丢失。
                    // 注意**不要**回退 lastFlushedAt：它记的是"上次成功"，回退会让时间闸门误判。
                    lock.write {
                        pending.forEach { (domain, _) ->
                            buckets[domain]?.let { buckets[domain] = it.copy(dirty = true) }
                        }
                    }
                }
        }
    }

    /**
     * [SecondClassCookie.expiresAt] 的归一：**非正的时刻一律收成 [REVOKED]（0）**。
     *
     * ## ⚠️ 判定顺序是关键，反过来写就等于没写
     *
     * 最容易写错的一版是"先判 `expiresAt == SESSION` 就原样返回"——那样 `Long.MIN_VALUE`
     * 会命中最前面那一支被当成会话哨兵，而这正是要修的那个 bug（哨兵冲突见 [REVOKED]）。
     * 所以**先收 `<= 0`**：`Long.MIN_VALUE` 落在这一支，才轮得到后面的正数放行。
     *
     * ## 为什么收成 `0` 而不是别的负值
     *
     * `0` 是 `Cookie.Builder.expiresAt(0)` 那条"明确过期"路径的值，语义最稳；
     * 而负时间戳在 `expiresAt - now` 这类算术里会溢出（`Long.MIN_VALUE - now` 回绕成正数，
     * `.coerceAtLeast(0)` 也救不回来）。
     *
     * 调用方**只有一处**：从 OkHttp 响应进来的**持久** cookie（`persistent == true`）。
     * 非持久那一支直接给 [SESSION]，不走本方法 —— 这一句区分就是整个 bug 的修法
     * （OkHttp 的"非持久"哨兵恰好也是 `Long.MIN_VALUE`，光看值分不出来）。
     */
    private fun normalizeExpiry(expiresAt: Long): Long = if (expiresAt <= 0L) REVOKED else expiresAt

    /**
     * 桶的键：去掉前导点、转小写，让 `.5idream.net` 与 `5idream.net` 落进同一个桶。
     */
    private fun bucketKeyOf(domain: String): String = domain.trimStart('.').lowercase(Locale.ROOT)

    /**
     * 覆盖式写入：同 [SecondClassCookie.key] 的原位替换（保持列表的写入顺序），
     * 新条目追加到末尾。顺序稳定才能让 [loadForRequest] 的排序结果可测。
     *
     * ⚠️ 这里**刻意不过滤过期条目**：服务端登出时下发的 `Max-Age=0` 就是一条
     * `expiresAt = REVOKED` 的空值 cookie，它必须能在桶里被 [revocations] 读到，
     * 才能给 WebView 下发删除指令。过滤掉它就等于"点了退出还登着"。
     * 它的清理发生在**读档边界**（见 [sanitize]）：那种记录不该跨进程存活。
     */
    private fun upsert(current: List<SecondClassCookie>, incoming: SecondClassCookie): List<SecondClassCookie> {
        val index = current.indexOfFirst { it.key == incoming.key }
        return if (index < 0) {
            current + incoming.copy(seq = sequence.incrementAndGet())
        } else {
            current.toMutableList().also { it[index] = incoming.copy(seq = current[index].seq) }
        }
    }

    /**
     * 域匹配。[cookieDomain] 为 `null` 表示"这个桶键本身是不是我们管的域"（加载存档时用）。
     *
     * 规则与浏览器一致：`5idream.net` 覆盖 `www.5idream.net` 与 `5idream.net`，
     * **不覆盖** `evil-5idream.net`（必须落在点边界上，否则就是个 cookie 泄漏口子）。
     *
     * [cookieDomain] 为 `null` 表示"判断这个**主机**能不能被原生查询"，此时只过白名单。
     * 非 null 表示"这个来源下发的 cookie 要不要收"，**先过白名单再过域边界**。
     */
    private fun domainMatches(host: String, cookieDomain: String?): Boolean {
        val target = (cookieDomain ?: host).trimStart('.').lowercase(Locale.ROOT)
        if (target.isEmpty()) return false
        if (strictDomains.isNotEmpty()) {
            val allowed = strictDomains.any { domain ->
                val normalized = domain.trimStart('.').lowercase(Locale.ROOT)
                target == normalized || target.endsWith(".$normalized")
            }
            if (!allowed) return false
        }
        if (cookieDomain == null) return true
        return host == target || host.endsWith(".$target")
    }

    /** RFC 6265 的 path-match：`/a` 匹配 `/a`、`/a/b`，**不**匹配 `/ab`。 */
    private fun pathMatches(requestPath: String, cookiePath: String): Boolean {
        val cp = cookiePath.ifBlank { "/" }
        if (requestPath == cp) return true
        if (!requestPath.startsWith(cp)) return false
        return cp.endsWith("/") || requestPath.getOrNull(cp.length) == '/'
    }

    private fun SecondClassCookie.toOkHttpCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .apply {
            // ⚠️ 会话 cookie **绝不能**调 `expiresAt()`：
            // OkHttp 用 `expiresAt == Long.MAX_VALUE` 表示"会话 cookie"，而
            // `Cookie.Builder.expiresAt(0)` 造出来的是一条**已过期**的 cookie（`persistent == true`）。
            // 所以 here 只能"要么给真过期时刻，要么什么都不做"。
            if (!isSession) expiresAt(expiresAt)
            if (httpOnly) httpOnly()
            if (secure) secure()
        }
        // ⚠️ `Cookie.Builder.domain()` 会把前导点判为非法域名并**抛异常**
        //（实测：`IllegalArgumentException: unexpected domain: .5idream.net`），
        // 所以这里必须剥掉点。剥掉不等于"只匹配精确主机"—— OkHttp 的 domain 匹配
        // 用的是"host == domain || host endsWith "." + domain"，子域仍然覆盖。
        .domain(domain.trim().trimStart('.'))
        .path(path.ifBlank { "/" })
        .build()

    /**
     * ⚠️ **必须**用 `Cookie.persistent` 判定，不能拿 `expiresAt` 去比 `Long.MAX_VALUE`。
     *
     * 实测（OkHttp 4.12）：非持久 cookie 的 `expiresAt` 是 **`253402300799999`**
     * （公元 9999-12-31T23:59:59.999Z 的毫秒值），**不是** `Long.MAX_VALUE`。
     * 拿 `Long.MAX_VALUE` 比会全部落空 ⇒ 会话 cookie 被当成"公元 9999 年过期"存下来，
     * 于是重启后它永不过期，变成一条幽灵会话；而 `syncableSnapshot()` 又会把它算成可回写，
     * 结果回写一批根本写不进 CookieManager 的会话 cookie。
     *
     * ## ⚠️ 这里还有一个更隐蔽的坑：`Long.MIN_VALUE` 是**两个**语义
     *
     * `persistent == false` 时字段值是 `Long.MIN_VALUE`（OkHttp 的"非持久"哨兵），
     * 而 `Max-Age=0` 解析出来的**字段值也是它** —— 两者靠 `persistent` 区分。
     * 所以非持久那一支要显式给 [SESSION]，持久那一支必须走 [normalizeExpiry]，
     * **不能**写成 `if (persistent) expiresAt else SESSION` 就完事（那是登出失效的原因）。
     *
     * 剥掉前导点：`Cookie.domain` 的子域 cookie 会带点，而出栈时 `toOkHttpCookie()`
     * 也会剥点 —— 从入口就规范，避免两处不一致。
     */
    private fun Cookie.toSecondClassCookie(): SecondClassCookie = SecondClassCookie(
        name = name,
        value = value,
        domain = domain.trim().trimStart('.'),
        path = path,
        expiresAt = if (persistent) normalizeExpiry(expiresAt) else SESSION,
        secure = secure,
        httpOnly = httpOnly,
    )

    /**
     * 释放本实例独有的 IO 线程。**生产代码不需要调**（那是进程级资源），
     * 存在的意义是让单测创建几十个 jar 实例后不会留下几十个线程。
     */
    fun close() {
        executor.shutdownNow()
    }

    companion object {
        private const val TAG = "SecondClassCookieJar"
        private const val DEFAULT_FLUSH_INTERVAL_MS = 500L

        /** 只接受到梦空间自己的域。见构造参数 `strictDomains` 的说明。 */
        val DEFAULT_DOMAINS: Set<String> = setOf("5idream.net")

        /**
         * 每个 jar 自己一个单线程 executor，用来落盘。
         *
         * 为什么**不**用一个 `companion object` 里的共享 executor：那会让 jar 实例之间
         * 共享线程，测试里几十个实例的写入互相排队，且无法在实例销毁时回收。
         *
         * 为什么**不**用 [kotlinx.coroutines.Dispatchers.Main]：落盘不是 UI 工作，
         * 放主线程只会在写盘时卡住 UI。`CookieManager` 才需要主线程，那一步由
         * `WebViewCookieSync` 内部单独切（见 `cookieDispatcher`）。
         *
         * 为什么**不**用 [kotlinx.coroutines.Dispatchers.IO]：它在线程饥饿时会让
         * 落盘任务无限排队，而落盘时序是我们节流逻辑的输入之一，需要可预期。
         */
        internal fun newCookieIoExecutor(): ExecutorService =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "secondclass-cookie-io").apply { isDaemon = true }
            }

        /**
         * 测试注入点：把落盘调度器换成 [kotlinx.coroutines.Dispatchers.Unconfined]，
         * 让"写盘是异步的"这个事实在单测里变成同步完成，避免用 sleep 猜时序。
         *
         * 生产代码**不要**动它。
         */
        @Volatile
        internal var testDispatcher: kotlinx.coroutines.CoroutineDispatcher? = null

    }
}
