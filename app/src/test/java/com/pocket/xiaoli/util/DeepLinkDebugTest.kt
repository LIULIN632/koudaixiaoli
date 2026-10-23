package com.pocket.xiaoli.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deep Link 调试解析：内部路由、外部链接与非法输入。 */
class DeepLinkDebugTest {

    @Test
    fun resolvesInternalTab() {
        val target = DeepLinkDebug.resolve("pocketxiaoli://timetable")
        assertTrue(target is DeepLinkTarget.Route)
        val route = target as DeepLinkTarget.Route
        assertEquals("tab", route.routeKey)
        assertEquals("timetable", route.args["tab"])
    }

    @Test
    fun resolvesMapQueryWithDecoding() {
        val route = DeepLinkDebug.resolve("pocketxiaoli://map?q=A101%20%E4%B8%89%E6%A5%BC") as DeepLinkTarget.Route
        assertEquals("campus_map", route.routeKey)
        assertEquals("A101 三楼", route.args["q"])
    }

    /**
     * 番茄钟的倒计时通知就是点这个链接回来的。
     *
     * ⚠️ 通知那条 PendingIntent 带了 `?n=<时间戳>`（见 `FocusNotifier`），
     * 所以这里连同带参数的写法一起固定住：多了个查询参数不能变成 `Invalid`，
     * 否则**应用在前台时点通知**会只弹一句"未知深链"。
     */
    @Test
    fun resolvesFocusTimer() {
        val route = DeepLinkDebug.resolve("pocketxiaoli://focus") as DeepLinkTarget.Route
        assertEquals("focus", route.routeKey)
        assertTrue(route.args.isEmpty())
    }

    @Test
    fun focusTimerWithNonceStillResolves() {
        val route = DeepLinkDebug.resolve("pocketxiaoli://focus?n=1729000000000") as DeepLinkTarget.Route
        assertEquals("focus", route.routeKey)
    }

    @Test
    fun httpBecomesExternal() {
        val target = DeepLinkDebug.resolve("https://jwxt.yitsd.edu.cn/login")
        assertTrue(target is DeepLinkTarget.External)
        assertEquals("https://jwxt.yitsd.edu.cn/login", (target as DeepLinkTarget.External).url)
    }

    @Test
    fun unknownSchemeIsInvalid() {
        val target = DeepLinkDebug.resolve("ftp://example.com")
        assertTrue(target is DeepLinkTarget.Invalid)
    }

    @Test
    fun blankUriIsInvalid() {
        val target = DeepLinkDebug.resolve("   ")
        assertTrue(target is DeepLinkTarget.Invalid)
        assertTrue(DeepLinkDebug.describe(target).contains("无效"))
    }
}
