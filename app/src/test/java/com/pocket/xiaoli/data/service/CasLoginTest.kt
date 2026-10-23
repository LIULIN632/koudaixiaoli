package com.pocket.xiaoli.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 原生 CAS 登录的两块硬骨头：**登录页解析**与**密码加密**。
 *
 * 两者都对着真实来源验，不按想象写：
 * - 登录页片段 [REAL_LOGIN_HTML] 是 2026-09-28 从
 *   `http://authserver.yitsd.edu.cn/authserver/login?service=…` 抓下来的原文（保留结构）；
 * - 密码加密的期望值由**线上那份 `encrypt.js` 里的真实 CryptoJS** 跑出来
 *   （固定随机源，见 [encryptMatchesOnlineCryptoJs]）。
 */
class CasLoginTest {

    private val REAL_LOGIN_HTML = """
        <html><body>
        <form id="casLoginForm" class="fm-v clearfix amp-login-form" role="form"
              action="/authserver/login?service=http%3A%2F%2Fehall.yitsd.edu.cn%2Fnew%2Findex.html" method="post">
            <input id="username" name="username" placeholder="用户名" class="auth_input" type="text" value=""/>
            <input id="password" placeholder="密码" class="auth_input" type="password" value="" autocomplete="off"/>
            <input id="passwordEncrypt" name="password" style="display:none;" type="text" value=""/>
            <p id="cpatchaDiv">
            </p>
            <input type="checkbox" name="rememberMe" id="rememberMe"/>
            <button type="submit" class="auth_login_btn primary full_width">登录</button>
            <input type="hidden" name="lt" value="LT-5958638-XHCwkKuWHXrwz6i4eQYwm19yEn4xiW1790571326739-S20h-cas"/>
            <input type="hidden" name="dllt" value="userNamePasswordLogin"/>
            <input type="hidden" name="execution" value="e1s1"/>
            <input type="hidden" name="_eventId" value="submit"/>
            <input type="hidden" name="rmShown" value="1">
            <input type="hidden" id="pwdDefaultEncryptSalt" value="hdA32uN6vqmY2GAO"/>
            <div id="hidenCaptchaDiv" style="display: none;">
                <input type="text" placeholder="验证码" id="captchaResponse" name="captchaResponse"
                       class="auth_input captcha-input"/>
                <img id="captchaImg" class="captcha-img" alt="验证码" title="验证码"/>
            </div>
        </form>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesRealLoginPage() {
        val form = CasLogin.parseLoginForm(REAL_LOGIN_HTML)

        assertEquals("e1s1", form?.execution)
        assertEquals("LT-5958638-XHCwkKuWHXrwz6i4eQYwm19yEn4xiW1790571326739-S20h-cas", form?.lt)
        assertEquals("userNamePasswordLogin", form?.dllt)
        assertEquals("submit", form?.eventId)
        assertEquals("1", form?.rmShown)
        assertEquals("hdA32uN6vqmY2GAO", form?.salt)
        // 模板里的 #hidenCaptchaDiv 不算"要验证码" —— 只有它被塞进 #cpatchaDiv 才算
        assertFalse(form?.captchaRequired == true)
        assertTrue(form?.action?.contains("/authserver/login") == true)
    }

    @Test
    fun detectsRequiredCaptcha() {
        // 页面在需要验证码时会把输入框塞进 #cpatchaDiv（平时它是空的，模板在 #hidenCaptchaDiv 里）
        val withCaptcha = REAL_LOGIN_HTML.replace(
            "<p id=\"cpatchaDiv\">",
            "<p id=\"cpatchaDiv\"><input type=\"text\" id=\"captchaResponse\"/>",
        )
        assertTrue(CasLogin.parseLoginForm(withCaptcha)?.captchaRequired == true)

        assertNull(CasLogin.parseLoginForm(""))
        assertNull(CasLogin.parseLoginForm("<html>学校换了认证方式</html>"))
    }

    /**
     * 期望值来自线上 `encrypt.js`（真实 CryptoJS）在固定随机源下的输出：
     *
     * salt=hdA32uN6vqmY2GAO · 明文前缀='A'×64 · IV='B'×16 · 密码=Ytlg@123456
     *
     * 这条向量是"我的 Kotlin AES 与学校页面的 JS AES 是否逐字节一致"的唯一凭据 ——
     * 差一个字节，服务端就只会回"用户名或密码错误"，而那种失败最难查。
     */
    @Test
    fun encryptMatchesOnlineCryptoJs() {
        val encrypted = CasLogin.encryptPassword(
            password = "Ytlg@123456",
            salt = "hdA32uN6vqmY2GAO",
            prefix = "A".repeat(64),
            iv = "B".repeat(16),
        )

        assertEquals(
            "x5ajHLVvyjN+le4f4++ZLoYFVWIfX+vnwzSBW3qxoIGUewAg6Eo64fQicpSz5LfHeg9yT0yU5931F7c7kdpSc" +
                "CglxsAkhk0U9Ktg12615+g=",
            encrypted,
        )
    }

    @Test
    fun randomStringUsesTheSameCharsetAsThePage() {
        // 页面那串没有 I L O 0 1：字符集不一致会导致密文虽合法但服务端解不出
        repeat(40) {
            val value = CasLogin.randomString(16, Random(it))
            assertEquals(16, value.length)
            assertTrue(value.all { c -> c in CasLogin.RANDOM_CHARS })
        }
        assertEquals(64, CasLogin.randomString(64, Random(1)).length)
    }

    @Test
    fun extractsTicketFromRedirect() {
        assertEquals(
            "ST-123-abc",
            CasLogin.extractTicket(
                "http://ehall.yitsd.edu.cn/new/index.html?ticket=ST-123-abc"
            ),
        )
        assertEquals(
            "ST-1",
            CasLogin.extractTicket("http://x/y?ticket=ST-1&other=2"),
        )
        assertNull(CasLogin.extractTicket("http://x/y?a=b"))
        assertNull(CasLogin.extractTicket(null))
        assertNull(CasLogin.extractTicket(""))
    }

    @Test
    fun collectsCookiesWithLaterValuesWinning() {
        val header = CasLogin.cookieHeaderOf(
            listOf(
                "JSESSIONID=abc; path=/; HttpOnly",
                "SF_cookie_9=29536661; path=/",
                "JSESSIONID=def; path=/",
            )
        )
        assertEquals("JSESSIONID=def; SF_cookie_9=29536661", header)
    }

    @Test
    fun loginUrlCarriesEncodedServiceTarget() {
        val url = CasLogin.loginUrl("http://ehall.yitsd.edu.cn/new/index.html")
        assertTrue(url.startsWith("http://authserver.yitsd.edu.cn/authserver/login?service="))
        assertTrue("service 必须被编码", url.contains("http%3A%2F%2Fehall.yitsd.edu.cn"))
    }
}
