package com.pocket.xiaoli.network

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.webkit.CookieManager

/**
 * Cookie 持久化存储 — 从 WebView 提取 Cookie 供 OkHttp 复用。
 * 对应优化方案中的 CookieStore 设计。
 */
object CookieStore {

    private const val KEY_SAVED_COOKIES = "saved_cookies"
    private const val KEY_SERVICE_COOKIE_PREFIX = "service_cookie_"

    private var cookie: String? = null
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("egg_rice_cookies", Context.MODE_PRIVATE)
        cookie = prefs?.getString(KEY_SAVED_COOKIES, null)
    }

    fun save(cookieStr: String) {
        cookie = cookieStr
        prefs?.edit()?.putString(KEY_SAVED_COOKIES, cookieStr)?.apply()
    }

    fun get(): String? = cookie

    /** 从 WebView CookieManager 提取指定 URL 的 Cookie 并保存 */
    fun extractFromWebView(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrEmpty()) {
            save(cookies)
        }
        return cookies
    }

    /**
     * 按 host 保存服务页 WebView 登录态。
     *
     * 为什么需要它：部分服务页只下发 session cookie，WebView 的 CookieManager
     * 在进程重启后可能把它当会话 cookie 丢掉。这里额外落一份盘，下次打开同一
     * host 时再注入，实现登录一次后常态免登。
     */
    fun saveServiceCookies(url: String?, cookieStr: String?) {
        val host = hostOf(url) ?: return
        val value = cookieStr?.trim().orEmpty()
        if (value.isBlank()) return
        prefs?.edit()?.putString(KEY_SERVICE_COOKIE_PREFIX + host, value)?.apply()
    }

    /** 读取指定 URL 对应的服务页登录态。 */
    fun getServiceCookies(url: String?): String? {
        val host = hostOf(url) ?: return null
        return prefs?.getString(KEY_SERVICE_COOKIE_PREFIX + host, null)
    }

    /** 清除所有已保存的 Cookie */
    fun clear() {
        cookie = null
        val editor = prefs?.edit() ?: return
        editor.remove(KEY_SAVED_COOKIES)
        prefs?.all?.keys
            ?.filter { it.startsWith(KEY_SERVICE_COOKIE_PREFIX) }
            ?.forEach { editor.remove(it) }
        editor.apply()
    }

    /**
     * 只清除某个 host 的落盘登录态。
     *
     * 与 [clear] 的区别：换账号 / 登录态坏了时，用户只想退出**这一个**服务，
     * 不该顺手把图书馆、其它服务页的登录态一起清掉。
     */
    fun clearServiceCookies(url: String?) {
        val host = hostOf(url) ?: return
        prefs?.edit()?.remove(KEY_SERVICE_COOKIE_PREFIX + host)?.apply()
    }

    /** 检查是否有已保存的 Cookie */
    fun hasCookies(): Boolean = !cookie.isNullOrEmpty()

    private fun hostOf(url: String?): String? {
        val raw = url?.trim().orEmpty()
        if (raw.isBlank()) return null
        val parsed = runCatching {
            Uri.parse(if (raw.contains("://")) raw else "https://" + raw)
        }.getOrNull() ?: return null
        return parsed.host?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
    }
}
