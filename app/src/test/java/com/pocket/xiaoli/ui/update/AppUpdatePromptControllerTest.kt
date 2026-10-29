package com.pocket.xiaoli.ui.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
import com.pocket.xiaoli.network.AppUpdateChecker
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「更新发出来会不会主动弹」的判定。
 *
 * 这块没法靠真机之外的方式观察：打开 App 时弹没弹、关掉后再开还会不会弹，
 * 全靠进程生命周期决定。所以把规则钉在单测里：
 *
 * | 规则 | 写反的后果 |
 * |---|---|
 * | 普通更新每次打开 App 弹一次 | 不更新就永远看不到提示，等于没做 |
 * | 同一次前台会话里关掉后不再弹 | feed 每次刷新都把抽屉重新糊到用户脸上 |
 * | 退到后台再回来算一次新的打开 | 从别的 App 切回来永远看不到，用户以为不再提醒 |
 * | 换了新版本要重新弹 | 新版本发了却要等下次打开才提醒 |
 * | 强更不受本次已提示影响 | 该逼着升级的时候被刚关掉的普通提示压住 |
 * | 有必须确认的公告时先让公告说话 | 两层窗口叠在一起，抽屉把公告盖掉 |
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdatePromptControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun controller() = AppUpdatePromptController(
        context = context,
        repository = AppFeedRepository(context),
        checker = AppUpdateChecker(),
    )

    private fun state(
        versionCode: Int = 74,
        force: Boolean = false,
        blocking: Announcement? = null,
        loaded: Boolean = true,
        push: Boolean = true,
    ) = AppFeedRepository.State(
        announcements = listOfNotNull(blocking),
        updateAvailable = true,
        forceUpdate = force,
        blocking = blocking,
        latestVersionCode = versionCode,
        latestVersionName = "0.1.$versionCode",
        apkUrl = "https://example.com/app.apk",
        notes = listOf("改了什么"),
        loaded = loaded,
        pushEnabled = push,
    )

    @Test
    fun `同一次启动内关掉后不再弹`() {
        val prompt = controller()

        prompt.autoShow(state())
        assertNotNull(prompt.info)

        prompt.dismiss()
        assertNull(prompt.info)

        // 同一次启动里 feed 状态再刷新几次，也不能重复弹
        prompt.autoShow(state())
        assertNull(prompt.info)
    }

    @Test
    fun `退到后台再回来会重新弹`() {
        val prompt = controller()
        prompt.autoShow(state())
        prompt.dismiss()
        prompt.autoShow(state())
        assertNull(prompt.info)

        prompt.onBackground()
        prompt.autoShow(state())
        assertNotNull(prompt.info)
    }

    @Test
    fun `重启 App 会再主动弹一次`() {
        val prompt = controller()
        prompt.autoShow(state())
        prompt.dismiss()

        // 重启 App：新的控制器实例没有「本次已弹过」的记忆
        val restarted = controller()
        restarted.autoShow(state())
        assertNotNull(restarted.info)
    }

    @Test
    fun `换了新版本会重新主动弹`() {
        val prompt = controller()
        prompt.autoShow(state(versionCode = 74))
        prompt.dismiss()

        prompt.autoShow(state(versionCode = 75))
        assertNotNull(prompt.info)
    }

    @Test
    fun `强更不受本次已提示影响`() {
        val prompt = controller()
        prompt.autoShow(state(versionCode = 74))
        prompt.dismiss()

        prompt.autoShow(state(versionCode = 74, force = true))

        assertNotNull(prompt.info)
        assertTrue(prompt.info!!.force)
    }

    @Test
    fun `有必须确认的公告时先不弹`() {
        val critical = Announcement(
            id = "maintenance",
            level = AnnouncementLevel.CRITICAL,
            title = "今晚停机维护",
            body = "",
        )

        val prompt = controller()
        prompt.autoShow(state(blocking = critical))

        assertNull(prompt.info)
    }

    @Test
    fun `没加载完或版本号为零时不弹`() {
        val prompt = controller()

        prompt.autoShow(state(loaded = false))
        assertNull(prompt.info)

        prompt.autoShow(state(versionCode = 0))
        assertNull(prompt.info)
    }

    // ── 「主动提醒更新」开关（设置 → 更新，2026-10-29）──

    /** 关掉之后普通更新不再自己弹出来 —— 这正是"不想更新的人别被打扰"要的效果。 */
    @Test
    fun `关掉主动提醒后普通更新不自动弹`() {
        val prompt = controller()

        prompt.autoShow(state(push = false))

        assertNull(prompt.info)
    }

    /**
     * 强更**不受这个开关管**。
     *
     * 那条说的是"你这个版本已经不能用了"（接口断版、数据格式变了），不是一个可以商量的新功能。
     * 写反的后果是：用户关掉提醒后停在坏版本上，连"该升级了"都不知道 ——
     * 与灰度那几条同一个思路（`minSupported` 不吃灰度，也不吃用户的偏好）。
     */
    @Test
    fun `关掉主动提醒后强更仍然弹`() {
        val prompt = controller()

        prompt.autoShow(state(force = true, push = false))

        assertNotNull(prompt.info)
    }

    /** 开着（默认）时照常弹 —— 这条守住的是"新字段的默认值必须是打扰照旧"。 */
    @Test
    fun `默认开着时照常弹`() {
        val prompt = controller()

        prompt.autoShow(state())

        assertNotNull(prompt.info)
    }
}
