package com.pocket.xiaoli.data.secondclass

import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 会话的取得与解析：`dmkj_web_token` → [SecondClassSession] → `checkToken` 的用户信息。
 *
 * ⚠️ 这里的 `CHECK_TOKEN_OK_BODY` 是**按实测到的字段名合成**的（`id` / `schoolid` / `activatflag`
 * 来自 `index.js` / `login.js` 的消费代码，见架构文档 §11.9），**不是抓包样本** ——
 * 真正的样本要等有会话时才能取。`UNAUTHORIZED_BODY` 则是**实测抓取的真实响应**。
 */
class SecondClassSessionTest {

    private val site: HttpUrl = "https://www.5idream.net/".toHttpUrl()

    private val created = mutableListOf<SecondClassCookieJar>()

    @Before
    fun setUp() {
        // 让"落盘是异步的"在单测里变成同步完成（同 SecondClassCookieJarTest 的做法）。
        SecondClassCookieJar.testDispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        SecondClassCookieJar.testDispatcher = null
        created.forEach { it.close() }
        created.clear()
    }

    /** 不落盘的 jar：本文件只关心"取得到 / 取不到"，与落盘无关。 */
    private fun jar(): SecondClassCookieJar =
        SecondClassCookieJar(store = null, strictDomains = setOf("5idream.net"), flushIntervalMs = 0L)
            .also { created += it }

    private fun cookie(name: String, value: String, expiresAt: Long = SESSION, path: String = "/") =
        SecondClassCookie(name = name, value = value, domain = "5idream.net", path = path, expiresAt = expiresAt)

    // ─────────────────── ⚠️ 这一条是整个文件存在的理由 ───────────────────

    /**
     * 守门人：**会话 cookie 必须能被读到**。
     *
     * `dmkj_web_token` 是会话 cookie（平台用 `$.cookie(..., {path:"/"})` 写入，没带 `expires`）。
     * 而 `syncableSnapshot()` 按 `canBePushedToWebView = !isSession && !httpOnly` 过滤，
     * **恰好把它滤掉** —— 拿它读 token 会永远得到 null，于是永远判"未登录"，
     * 而 WebView 里明明登着。这条用例把**两面**都钉住：
     * 快照里没有它，而 [SecondClassCookieJar.valueOf] 必须能取到它。
     */
    @Test
    fun `会话 cookie 不在 syncable 快照里但必须能被读到`() {
        val jar = jar()
        jar.applyWebViewSnapshot(site, listOf(cookie("dmkj_web_token", "tok-123")))

        assertTrue(
            "前提：会话 cookie 本来就不该出现在 syncableSnapshot 里（那是回写 WebView 用的快照）",
            jar.syncableSnapshot().none { it.name == "dmkj_web_token" },
        )
        assertEquals("tok-123", jar.valueOf("dmkj_web_token"))
        assertEquals("tok-123", SecondClassSession.tokenFrom(jar))
    }

    @Test
    fun `已撤销的 cookie 取不到`() {
        val jar = jar()
        jar.applyWebViewSnapshot(site, listOf(cookie("dmkj_web_token", "old", expiresAt = REVOKED)))

        assertNull("撤销的哨兵是 0（早于任何真实时刻），必须被排除", jar.valueOf("dmkj_web_token"))
    }

    @Test
    fun `已过期的持久 cookie 取不到`() {
        val jar = jar()
        jar.applyWebViewSnapshot(site, listOf(cookie("dmkj_web_token", "old", expiresAt = 1_000L)))

        assertNull(jar.valueOf("dmkj_web_token", now = 2_000L))
    }

    @Test
    fun `空值的 cookie 不算数`() {
        val jar = jar()
        jar.applyWebViewSnapshot(site, listOf(cookie("dmkj_web_token", "")))

        assertNull(jar.valueOf("dmkj_web_token"))
    }

    @Test
    fun `没有登录过时 tokenFrom 是 null 而不是空串`() {
        assertNull(SecondClassSession.tokenFrom(jar()))
    }

    // ─────────────────────────── checkToken 的解析 ───────────────────────────

    /** 合成样本：字段名取自 `index.js` / `login.js` 的消费代码（§11.9）。 */
    private val checkTokenOkBody = """{"result":{"id":"123456","schoolid":"45","activatflag":"1"}}"""

    /** 实测抓取的真实响应（未登录）。 */
    private val unauthorizedBody = """{"code":"400","result":"token不能为空","success":false}"""

    @Test
    fun `checkToken 成功时取出用户标识`() {
        val session = SecondClassSession.parseCheckToken(checkTokenOkBody, "tok-123").okValue()

        assertEquals("tok-123", session.token)
        assertEquals("123456", session.userId)
        assertEquals("45", session.schoolId)
        assertTrue(session.hasToken)
        assertTrue("activatflag 不是 2，学生功能可用", session.studentFeaturesAvailable)
    }

    /**
     * 守门人：**未登录必须原样是 `Unauthorized`**，不能在这里变成一句"登录失败"。
     *
     * UI 靠这一支决定显示"请重新登录"还是"加载失败"——架构文档 §4 点名这是最容易做错的一处。
     */
    @Test
    fun `checkToken 未登录时是 Unauthorized 而不是 Failed`() {
        assertEquals(
            SecondClassResult.Unauthorized,
            SecondClassSession.parseCheckToken(unauthorizedBody, "tok-123"),
        )
    }

    /**
     * 平台自己那条"学生菜单不渲染"的判据：`activatflag == "2"`。
     *
     * 见首页内联的 `headerTmpl`（`{{if activatflag && activatflag == 2}}` 时不渲染学生菜单）
     * 与 `login.js`（同样把它送回首页）。
     */
    @Test
    fun `activatflag 为 2 时学生功能判为不可用`() {
        val body = """{"result":{"id":"1","schoolid":"45","activatflag":"2"}}"""

        val session = SecondClassSession.parseCheckToken(body, "t").okValue()

        assertTrue("token 本身是有的", session.hasToken)
        assertTrue("但学生功能不可用", !session.studentFeaturesAvailable)
    }

    @Test
    fun `activatflag 缺失时不拦用户`() {
        val session = SecondClassSession.parseCheckToken("""{"result":{"id":"1"}}""", "t").okValue()

        assertNull(session.activateFlag)
        assertTrue("拿不到这个字段就不该拦着用户，让请求结果说话", session.studentFeaturesAvailable)
    }

    @Test
    fun `成功响应里没有 result 时是 Failed`() {
        val result = SecondClassSession.parseCheckToken("""{"rows":[]}""", "t")

        assertTrue(result is SecondClassResult.Failed)
        assertTrue((result as SecondClassResult.Failed).message.contains("result"))
    }

    @Test
    fun `拿到整页 HTML 时也是 Failed 而不是崩溃`() {
        val result = SecondClassSession.parseCheckToken("<!DOCTYPE html><html>", "t")

        assertTrue(result is SecondClassResult.Failed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> SecondClassResult<T>.okValue(): T {
        assertTrue("期望 Ok，实际是 $this", this is SecondClassResult.Ok<*>)
        return (this as SecondClassResult.Ok<T>).value
    }
}
