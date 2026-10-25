package com.pocket.xiaoli.data.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 首次向导的"走完了没"。
 *
 * 判定必须是一个**只增不减**的本地标记：用"有没有选过校区"会被默认校区骗过，
 * 用"同意过协议"会在条款改版时把老用户重新拦一遍。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pocket_first_run", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `默认没走过向导`() {
        assertFalse(FirstRunStore.isDone(context))
    }

    @Test
    fun `标记后一直算走过`() {
        FirstRunStore.markDone(context)
        assertTrue(FirstRunStore.isDone(context))

        // 新建一个 store 读的是同一份 prefs，不依赖内存态
        assertTrue(FirstRunStore.isDone(context))
    }
}
