package com.pocket.xiaoli.data.announcement

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「主动提醒更新」这个偏好的默认值与落盘。
 *
 * 默认值是这个开关最要紧的一条：它**必须是开**。默认关等于给所有升级到这个版本的人
 * 静默停掉了更新提示 —— 用户没做任何决定，行为却变了，而且没人会来报（他正嫌烦）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFeedStorePushTest {

    private fun store() = AppFeedStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `默认是开着`() {
        val store = store()
        store.clear()

        assertTrue("新装的机器不该安静地不提醒更新", store.pushEnabled())
    }

    @Test
    fun `关掉之后重开 store 仍然是关的`() {
        val store = store()
        store.clear()

        store.setPushEnabled(false)
        assertFalse(store.pushEnabled())
        // 换一个实例再读：证明真的落到 SharedPreferences，而不是只活在内存里
        assertFalse(store().pushEnabled())

        store.setPushEnabled(true)
        assertTrue(store().pushEnabled())
    }
}
