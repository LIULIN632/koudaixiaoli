package com.pocket.xiaoli.util

/**
 * 服务 URL 分类器。
 *
 * 背景：烟理的多数校园服务都需要认证（智慧校园统一身份认证），
 * 内置 WebView 首次打开时常常先落在登录页。识别出来后可以给用户一句明确提示，
 * 而不是让其面对一个"看起来是空页面"的登录框。
 *
 * 纯函数、无 Android 依赖，便于单元测试。
 */
object ServiceUrlClassifier {

    /**
     * 登录页特征。只取路径段/查询级特征，避免域名或普通词误判
     * （例如 `/authority` 不会命中，因为它不含 `/authserver`）。
     */
    private val LOGIN_MARKERS = listOf(
        "/login", "login.", "login?",
        "/signin", "/sign-in", "/logon",
        "/cas", "/sso", "/authserver", "/passport",
        "ticket=",
    )

    /** 该 URL 是否像登录/认证页 */
    fun isLoginLikeUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return LOGIN_MARKERS.any { lower.contains(it) }
    }
}