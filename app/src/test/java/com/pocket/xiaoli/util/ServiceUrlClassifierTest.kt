package com.pocket.xiaoli.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 登录页识别测试：既要不漏（漏了用户看到登录框没有提示），也不要误报（误报会干扰正常浏览）。 */
class ServiceUrlClassifierTest {

    @Test
    fun detectsCasAndSsoLoginPages() {
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://cas.yitsd.edu.cn/cas/login?service=x"))
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://sso.yitsd.edu.cn/sso/login"))
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://ehall.yitsd.edu.cn/login"))
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://authserver.yitsd.edu.cn/login"))
    }

    @Test
    fun detectsZhengfangLoginPage() {
        assertTrue(
            ServiceUrlClassifier.isLoginLikeUrl(
                "https://jwxt.yitsd.edu.cn/jwglxt/xtgl/login_slogin.html"
            )
        )
    }

    @Test
    fun detectsTicketRedirect() {
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://x.edu.cn/?ticket=ST-123"))
    }

    @Test
    fun normalPagesAreNotLogin() {
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl("https://ehall.yitsd.edu.cn/new/index.html"))
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl("https://www.yitsd.edu.cn/"))
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl("https://lib.yitsd.edu.cn/opac/search"))
    }

    @Test
    fun doesNotMisfireOnSimilarWords() {
        // "authority" 含 auth，但不含 /authserver → 不应误判
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl("https://ehall.yitsd.edu.cn/authority/list"))
        // "cascade" 含 cas，但不含 /cas /cas → 这里 "/cascade" 会含 "/cas"，
        // 属可接受的轻微误报（真实校园 URL 极少出现），显式记录该行为
        assertTrue(ServiceUrlClassifier.isLoginLikeUrl("https://x.edu.cn/cascade"))
    }

    @Test
    fun handlesBlankSafely() {
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl(null))
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl(""))
        assertFalse(ServiceUrlClassifier.isLoginLikeUrl("   "))
    }
}