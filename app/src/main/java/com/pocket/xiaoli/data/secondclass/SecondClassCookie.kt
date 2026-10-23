package com.pocket.xiaoli.data.secondclass

/**
 * ## 为什么需要这个类，而不是一个 `Map<String, String>`
 *
 * WebView 的 `CookieManager.getCookie(url)` 只还回一行 `"a=1; b=2"` —— **没有**过期时间、
 * 没有 path、没有 domain、没有 secure/httpOnly。只保存那一行字符串（本项目
 * `network/CookieStore.kt` 就是这么做的）会导致三类必然的错：
 *
 * 1. **会话态无法判定**：不知道 cookie 还有多久过期，就没法判断"还能不能走原生查询"，
 *    只能每次请求都重新 `getCookie`，把同步开销压在最热的路径上；
 * 2. **回写 WebView 时会重新造属性**：`Set-Cookie` 少了 `Path`，浏览器会把它挂到当前路径下，
 *    于是同一个会话在 WebView 里时有时无（表现为"刚登录好，进活动列表又要登录"）；
 * 3. **HttpOnly 无法区分**：HttpOnly 的会话 cookie `CookieManager.setCookie` **写不进去**，
 *    不区分就会每次回写都白写一遍、日志刷满，还误判成"同步失败"。
 *
 * 所以这里保留 OkHttp `Cookie` 需要的**全部**属性，只做一件事：
 * 在「CookieManager 的一行字符串」和「带属性的结构化 Cookie」之间来回翻译。
 *
 * ## ⚠️ 每个字段都必须有默认值
 *
 * 这个类要经 Gson 落盘（见 [SecondClassCookieStore]）。Gson 的规则是：
 * **只要有一个字段没有默认值，整个类就丢掉无参构造**，于是改用 `UnsafeAllocator` 直接反射赋值，
 * **所有**字段的默认值一起失效 —— 老存档读出来是 `null` 而不是默认值。
 * `DormInfo` v1.0.93 的闪退就是这么来的（`CLAUDE.md` 有专节）。
 * `SecondClassCookieJarTest.每个字段都有默认值` 是守门人。
 */
data class SecondClassCookie(
    val name: String = "",
    val value: String = "",
    /**
     * 归属域，**不带前导点**（`5idream.net`，而不是 `.5idream.net`）。
     *
     * 为什么不留前导点：`okhttp3.Cookie.Builder.domain()` 把带点的值判为非法域名并
     * **抛 `IllegalArgumentException`**（实测 `unexpected domain: .5idream.net`）。
     * 剥点**不影响子域覆盖** —— OkHttp 的匹配规则是
     * `host == domain || host.endsWith("." + domain)`，`5idream.net` 照样覆盖 `www.5idream.net`。
     *
     * 用字符串而不是 `HttpUrl`：`HttpUrl` 没有值语义的 equals/hashCode，
     * 序列化后也还原不回来（它的 `toString()` 是 URL，不是 host）。
     */
    val domain: String = "",
    val path: String = "/",
    /** 绝对过期时刻（`System.currentTimeMillis()` 口径）。[SESSION] 表示会话 cookie。 */
    val expiresAt: Long = SESSION,
    val secure: Boolean = false,
    /**
     * 是否 HttpOnly。
     *
     * 这个字段**只用于过滤** —— `CookieManager.setCookie` 对 HttpOnly 的 cookie 会静默拒绝。
     * 留着它是为了"知道这条回写不进去"，而不是为了回写它。
     */
    val httpOnly: Boolean = false,
    /**
     * 写入序号，**纯粹的排序稳定性用**，不是业务字段。
     *
     * 为什么需要：同一 path 长度下，服务端对同名 cookie 取请求头里的**第一个**，
     * 而"谁更新"必须由写入顺序决定。若靠 `LinkedHashMap` 的遍历顺序，
     * 它取决于桶的插入次序（跨域时不稳定），排序结果就不可测了。
     */
    val seq: Long = 0L,
) {
    /** 会话 cookie：浏览器关闭即失效，服务端不给过期时间。 */
    val isSession: Boolean get() = expiresAt == SESSION

    /** 是否已过期。[now] 显式传入而不是内部取当前时间 —— 让判定可测。 */
    fun isExpired(now: Long): Boolean = !isSession && expiresAt <= now

    /**
     * 这个 cookie 能不能由我们回写进 WebView 的 `CookieManager`。
     *
     * 两个"不能"：
     * - **会话 cookie**：浏览器侧本来就不需要它落盘，回写是多余动作；
     * - **HttpOnly**：`CookieManager.setCookie` 静默拒收（不抛异常、返回值也不告诉你失败）。
     *
     * 这两种情况**不等于同步失败**，所以调用方跳过它们，而不是打错误日志。
     */
    val canBePushedToWebView: Boolean get() = !isSession && !httpOnly

    /** 同一条 cookie 的身份。**domain + path + name** 三元组（RFC 6265 的 cookie 唯一键）。 */
    val key: String get() = domain.trimStart('.').lowercase() + "|" + path + "|" + name

    /**
     * 渲染成 `Cookie:` 请求头里的一项：`a=1`。
     *
     * `Cookie.toString()` 也能用，但它还会带上 `expires` / `domain` / `secure` 等属性 ——
     * 那些属于 `Set-Cookie`，出现在请求头里是协议错误。
     */
    fun toRequestPair(): String = "$name=$value"
}

/**
 * [SecondClassCookie.expiresAt] 的哨兵值：会话 cookie，无绝对过期时刻。
 *
 * ## ⚠️ 必须是 `Long.MAX_VALUE` —— 这个值改过一次，改的原因是**两个语义撞了**
 *
 * 第一版用的是 `Long.MIN_VALUE`，理由是"它永远早于任何真实时刻，漏判只会退化成
 * 当成过期丢掉"。这个理由**对**，但它忽略了一件事：**OkHttp 也用 `Long.MIN_VALUE`
 * 表示"非持久"**，而服务端登出下发的 `Max-Age=0` 解析出来的字段值**恰好也是它**
 * （实测 OkHttp 4.12：`Cookie.parse(url, "SID=; Max-Age=0")` → `persistent=true`，
 * 字段值 `Long.MIN_VALUE`）。两者只靠 `persistent` 位区分。
 *
 * 于是同一个 `Long.MIN_VALUE` 在**存档**里变成了二义：分不出"这是会话 cookie"
 * 还是"这是撤销（要删掉）"。后果是两条，都很难查：
 *
 * - 撤销被读成会话 ⇒ `revocations()` 空转 ⇒ **用户点了退出，WebView 里还登着**；
 * - 会话被读成撤销 ⇒ 每次启动都去删用户的会话 ⇒ **莫名掉登录**。
 *
 * 修法：把会话哨兵搬到 `Long.MAX_VALUE` —— 它**大于任何真实时间戳**
 * （`System.currentTimeMillis()` 要到公元 292278994 年才会追上），因此
 * "非正 ⇒ 已撤销、等于 `Long.MAX_VALUE` ⇒ 会话、其余 ⇒ 真实时刻"三支互不重叠，
 * 存档也不再丢信息。见 [REVOKED]。
 *
 * ## 为什么读作 `Long.MAX_VALUE` 不会把别处算坏
 *
 * - [SecondClassCookie.isSession] 是显式比较，不走算术；
 * - [SecondClassCookie.isExpired] 有 `!isSession` 短路，不会真去比大小；
 * - `formatSetCookie` / `toOkHttpCookie` 也都在 `isSession` 上分支 —— 会话 cookie
 *   **永远不会**把 `Long.MAX_VALUE` 当作过期时刻拿去格式化。
 *
 * 唯一要求：`expiresAt - now` 这类算术**只能出现在非会话分支**。这一点由上面三处短路保证。
 */
const val SESSION: Long = Long.MAX_VALUE

/**
 * 「这条 cookie 已被服务端撤销」的过期时刻。
 *
 * 它就是 `0L`：早于任何真实时刻，因此
 * [SecondClassCookie.isExpired] 恒为真、[SecondClassCookieJar.revocations] 必然收下它、
 * `formatSetCookie` 必然渲染成 `Max-Age=0`。
 *
 * 为什么收成 `0` 而不是别的负值：`0` 是 `Cookie.Builder.expiresAt(0)` 那条
 * "明确过期"路径的值，语义最稳；而负时间戳在 `expiresAt - now` 这类算术里会溢出
 * （`Long.MIN_VALUE - now` 回绕成正数，`.coerceAtLeast(0)` 救不回来）。
 */
const val REVOKED: Long = 0L

/** 待写盘的 cookie 快照。[version] 用于将来结构变更时的识别与丢弃。 */
data class SecondClassCookiePayload(
    val version: Int = CURRENT_VERSION,
    val cookies: List<SecondClassCookie> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

/**
 * 该域的 cookie 桶。
 *
 * 几个时间/状态字段是**节流落盘**的全部状态，语义必须分清（它们曾经被我混成一个，
 * 导致"窗口内新到的 cookie 被永久跳过"的真 bug）：
 *
 * @param dirty "有未落盘的改动"。落盘**成功**才清位；失败要放回去重试。
 * @param lastDirtyAt 最近一次被改动的时刻，用来算"改动是否还落在节流窗口内"。
 * @param lastFlushedAt 最近一次**成功**落盘的时刻（仅用于观测/日志，判据不依赖它）。
 * @param hasFlushed 是否曾经成功落过盘。
 *
 * ⚠️ 为什么必须是独立的 [hasFlushed] 布尔位，而不是拿 `lastFlushedAt == 0L` 当"从没写过"：
 * `lastFlushedAt` 的初值恰好是 0，而 `lastDirtyAt` 在**首次写入**时就被刷成当前时刻，
 * 于是 `now - lastDirtyAt` 恒为 0 ⇒ 判定"窗口未过" ⇒ **登录后拿到的第一批 cookie
 * 一条都不会落盘**，要等下一个请求（秒级，甚至用户不再操作就永远不写）才补上。
 * 用独立布尔位就不会被"0 也是个合法时间戳"这件事骗到。
 */
data class SecondClassCookieBucket(
    val cookies: List<SecondClassCookie> = emptyList(),
    val dirty: Boolean = false,
    val lastDirtyAt: Long = 0L,
    val lastFlushedAt: Long = 0L,
    val hasFlushed: Boolean = false,
)
