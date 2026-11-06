package com.pocket.xiaoli.ui.util

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * 外部链接打开的行为约定。
 *
 * 用户报的故障：点「四六级」→ 系统弹出「打开方式」选择框 → 取消 → 再点「普通话考试」没有反应。
 * 真机无法本地复现，所以这里守的是**能在 JVM 上证明的三件事**：
 *
 * 1. **发起 Intent 必须晚于当前输入事件一轮**（[打开请求不立即拉起外部Activity]）——
 *    这是本次修复的核心：在点击回调里同步 `startActivity`，选择框会抢走焦点，
 *    取消回来时 Compose 可能还停在"按下未抬起"，之后所有点击都被吞掉；
 * 2. **连续两次都要真的发出去**（[连续两次打开都会发起Intent]）——不允许任何"防连点"守卫吃掉第二次；
 * 3. **打不开必须说话**（[没有可用应用时提示并复制链接]）——静默失败正是"点了没反应"的来源。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExternalLinksTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val url = "https://cet.neea.edu.cn/"

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun 打开请求不立即拉起外部Activity() {
        ExternalLinks.open(app, url)

        // 还没到下一轮消息：此刻不该有任何 Activity 被拉起。
        assertNull(shadowOf(app).nextStartedActivity)

        idle()

        assertEquals(url, shadowOf(app).nextStartedActivity?.data.toString())
    }

    @Test
    fun 连续两次打开都会发起Intent() {
        ExternalLinks.open(app, url)
        ExternalLinks.open(app, "https://bm.cltt.org/")
        idle()

        // Robolectric 的队列顺序不保证，比集合：两次都得真的发出去才算过。
        val fired = setOf(
            shadowOf(app).nextStartedActivity?.data.toString(),
            shadowOf(app).nextStartedActivity?.data.toString()
        )
        assertEquals(setOf(url, "https://bm.cltt.org/"), fired)
    }

    @Test
    fun 空链接不发起任何Intent() {
        assertFalse(ExternalLinks.open(app, "   "))
        idle()
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun 没有可用应用时提示并复制链接() {
        // Robolectric 默认"任何 Intent 都算能处理"，打开检查才会真的抛 ActivityNotFoundException
        shadowOf(app).checkActivities(true)

        assertTrue(ExternalLinks.open(app, "pocketscheme://no-such-app/x"))
        idle()

        assertNull(shadowOf(app).nextStartedActivity)
        assertEquals("没有可打开该链接的应用，链接已复制", ShadowToast.getTextOfLatestToast())
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("pocketscheme://no-such-app/x", clipboard.primaryClip?.getItemAt(0)?.text)
    }

    @Test
    fun 包装过的Context能找到Activity() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped = ContextThemeWrapper(activity, android.R.style.Theme_DeviceDefault)

        assertSame(activity, wrapped.findActivity())
        assertSame(activity, activity.findActivity())
        assertNull(app.findActivity())
    }

    @Test
    fun 有Activity时不加NEW_TASK() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        ExternalLinks.open(activity, url)
        idle()

        val started: Intent = shadowOf(activity).nextStartedActivity
        assertEquals(url, started.data.toString())
        assertEquals(0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun 没有Activity时加NEW_TASK() {
        ExternalLinks.open(app, url)
        idle()

        val started: Intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
