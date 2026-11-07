package com.pocket.xiaoli.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pocket.xiaoli.data.entity.defaultTimeSlots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertEquals as eq
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 节次表在 v22 之后是**按校过滤**的。这条测试守住一个很贵的线上症状：
 * 读回空表 ⇒ 网格行数为 0 ⇒ 所有课程卡片叠在同一处（用户看到的是"导入后课程都挤在一起"）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimeSlotVisibilityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clean() {
        PocketXiaoliDatabase.closeForTest()
        context.deleteDatabase("pocket_xiaoli.db")
        context.getSharedPreferences("pocket_xiaoli_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @After
    fun tearDown() {
        PocketXiaoliDatabase.closeForTest()
    }

    @Test
    fun `新装库插入默认节次后能按校读回来`() = runBlocking {
        val db = PocketXiaoliDatabase.getInstance(context)
        db.courseDao().insertTimeSlots(defaultTimeSlots)

        val slots = db.courseDao().getAllTimeSlots().first()

        eq("默认节次表必须能被读回来（否则网格塌成一行）", defaultTimeSlots.size, slots.size)
    }

    @Test
    fun `app_state 里没有当前学校时也不会读到空表`() = runBlocking {
        // 模拟 v22 之前的老库：行已存在，但 schoolId 是空的（或 app_state 还没对齐）
        val db = PocketXiaoliDatabase.getInstance(context)
        db.openHelper.writableDatabase.execSQL("DELETE FROM app_state")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO time_slots (slot, startTime, endTime, schoolId) VALUES (1,'08:00','08:45','')",
        )

        val slots = db.courseDao().getAllTimeSlots().first()

        assertEquals("空 schoolId 的行也必须能读到（否则用户看到的是空网格）", 1, slots.size)
    }
}
