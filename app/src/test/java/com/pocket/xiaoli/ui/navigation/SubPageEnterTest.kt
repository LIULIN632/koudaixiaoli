package com.pocket.xiaoli.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「哪些状态翻转该滑一下」的判据。
 *
 * 值得钉住的是那条**排除**：「我的」页上报的是 `hasSubPage || dialogsVisible`
 * （底栏需要这个合并值），但弹窗开合不该推动整页滑动 ——
 * 少排除一次，用户就会看到"页面在往后退"和弹窗淡入同时发生。
 * 这条只能靠人记住的话，就会忘，所以写成断言。
 */
class SubPageEnterTest {

    @Test
    fun `服务页开二级页才滑`() {
        assertTrue(shouldSlideOnSubPage("services", hasOpenSubPage = true))
    }

    @Test
    fun `停在一级不滑`() {
        assertFalse(shouldSlideOnSubPage("services", hasOpenSubPage = false))
        assertFalse(shouldSlideOnSubPage("timetable", hasOpenSubPage = false))
    }

    @Test
    fun `课表页开二级页同样滑`() {
        assertTrue(shouldSlideOnSubPage("timetable", hasOpenSubPage = true))
    }

    @Test
    fun `我的页排除`() {
        // 它的 flag 里混着 dialogsVisible：弹窗开合会误判成"换了整页"。
        assertFalse(shouldSlideOnSubPage("profile", hasOpenSubPage = true))
    }

    @Test
    fun `路由未就绪时不误关`() {
        // NavHost 切换瞬间 currentDestination 可能是 null，此时按"非 profile"处理，
        // 不能因为拿不到路由就把服务页的滑入也掐掉。
        assertTrue(shouldSlideOnSubPage(null, hasOpenSubPage = true))
    }
}
