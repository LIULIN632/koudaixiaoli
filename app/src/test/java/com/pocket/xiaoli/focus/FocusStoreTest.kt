package com.pocket.xiaoli.focus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 快照真源（[FocusStore]）。
 *
 * ## 为什么这一层要测
 *
 * 它守着两件**坏了也不报错**的事：
 *
 * | 规则 | 写错的后果 |
 * |---|---|
 * | 读不出来就当没有会话（不看 `looksUsable`） | 一个 `plannedMs = 0` 的壳流进内核 = 进度恒 0、永不过期的一段 |
 * | 写入走 [FocusStore.edit] 而不是裸 `prefs.edit()` | Flow 不更新 → 服务把段跑完了，页面还显示在跑 |
 *
 * 跑在 Robolectric 上：prefs 就是本层的实体依赖，换假实现等于把要测的东西换掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val prefs get() = context.getSharedPreferences("pocket_focus", Context.MODE_PRIVATE)

    /** 每个用例从干净的 prefs 开始：快照是单文件全局状态，残留会串味。 */
    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    private fun runtime(
        plannedMs: Long = 25 * 60_000L,
        roundIndex: Int = 2,
        pausedRemainingMs: Long? = null,
        linkedTaskId: Long? = 44L,
        linkedTaskTitle: String? = "写高数作业",
    ) = FocusRuntime(
        phase = FocusPhase.FOCUS,
        roundIndex = roundIndex,
        plannedMs = plannedMs,
        deadlineElapsedMs = 1_000_000L,
        deadlineWallClockMs = 1_700_000_000_000L,
        startedAtWallMs = 1_699_999_000_000L,
        pausedRemainingMs = pausedRemainingMs,
        linkedTaskId = linkedTaskId,
        linkedTaskTitle = linkedTaskTitle,
    )

    @Test
    fun `空 prefs 时没有会话，配置是默认值`() {
        val store = FocusStore(context)
        assertNull(store.runtime.value)
        assertEquals(FocusConfig.DEFAULT, store.config.value)
    }

    @Test
    fun `快照能存能读回，换实例也一样（模拟进程重启）`() {
        val store = FocusStore(context)
        store.edit(runtime())
        assertEquals(runtime(), store.runtime.value)

        // 新实例 = 新进程：只剩磁盘上那份 JSON
        assertEquals(runtime(), FocusStore(context).readRuntime())
    }

    @Test
    fun `暂停中的快照连冻结剩余一起保住`() {
        val store = FocusStore(context)
        store.edit(runtime(pausedRemainingMs = 900_000L))
        val back = FocusStore(context).readRuntime()!!
        assertTrue(back.isPaused)
        assertEquals(900_000L, back.pausedRemainingMs ?: -1L)
    }

    @Test
    fun `关联待办的名字冗余在快照里，重启后还能写进通知`() {
        val store = FocusStore(context)
        store.edit(runtime())
        val back = FocusStore(context).readRuntime()!!
        // 通知要在 startForeground 的 5 秒窗口里直接写出名字，不能回头查库
        assertEquals("写高数作业", back.linkedTaskTitle)
        assertEquals(44L, back.linkedTaskId ?: -1L)
    }

    @Test
    fun `edit 传 null 表示会话结束，磁盘上不留半截记录`() {
        val store = FocusStore(context)
        store.edit(runtime())
        store.edit(null)
        assertNull(store.runtime.value)
        assertNull(FocusStore(context).readRuntime())
        assertFalse(prefs.contains("focus_runtime"))
    }

    @Test
    fun `脏 JSON 读出 null，而不是抛异常`() {
        prefs.edit().putString("focus_runtime", "{ 这不是 JSON").commit()
        assertNull(FocusStore(context).readRuntime())
    }

    @Test
    fun `空格子读出 null`() {
        prefs.edit().putString("focus_runtime", "   ").commit()
        assertNull(FocusStore(context).readRuntime())
    }

    @Test
    fun `结构对但 plannedMs 为 0 的壳视为没有会话`() {
        // 上个版本写进去的壳：Gson 不抛异常，但流进内核就是"永远走不完的一段"
        prefs.edit().putString("focus_runtime", """{"phase":"FOCUS","plannedMs":0}""").commit()
        assertNull(FocusStore(context).readRuntime())
    }

    @Test
    fun `老存档缺字段也能读回，缺的用默认值补齐`() {
        // 只有最早那几个字段：没有 startedAtWallMs / linkedTaskTitle / pausedRemainingMs
        prefs.edit().putString(
            "focus_runtime",
            """{"phase":"SHORT_BREAK","roundIndex":2,"plannedMs":300000,"deadlineElapsedMs":9,"deadlineWallClockMs":10}"""
        ).commit()
        val back = FocusStore(context).readRuntime()!!
        assertEquals(FocusPhase.SHORT_BREAK, back.phase)
        assertEquals(300_000L, back.plannedMs)
        assertEquals(0L, back.startedAtWallMs)
        assertNull(back.linkedTaskTitle)
        assertNull(back.pausedRemainingMs)
        assertFalse(back.isPaused)
    }

    @Test
    fun `配置能存能读回`() {
        val store = FocusStore(context)
        store.setConfig(FocusConfig(focusMinutes = 45, shortBreakMinutes = 10, autoStartNext = false))
        assertEquals(45, store.config.value.focusMinutes)
        assertEquals(10, store.config.value.shortBreakMinutes)
        assertFalse(store.config.value.autoStartNext)

        val back = FocusStore(context).readConfig()
        assertEquals(45, back.focusMinutes)
        assertEquals(10, back.shortBreakMinutes)
        assertEquals(15, back.longBreakMinutes)
        assertFalse(back.autoStartNext)
    }

    @Test
    fun `越界配置读回时被钳住`() {
        // 直接塞一份离谱的 JSON：0 分钟的段会让页面一闪一闪
        prefs.edit().putString(
            "focus_config",
            """{"focusMinutes":0,"shortBreakMinutes":-30,"longBreakMinutes":9999,"roundsBeforeLongBreak":0}"""
        ).commit()
        val back = FocusStore(context).readConfig()
        assertEquals(FocusConfig.MIN_MINUTES, back.focusMinutes)
        assertEquals(FocusConfig.MIN_MINUTES, back.shortBreakMinutes)
        assertEquals(FocusConfig.MAX_MINUTES, back.longBreakMinutes)
        assertEquals(1, back.roundsBeforeLongBreak)
    }

    @Test
    fun `坏掉的配置退回默认，不抛异常`() {
        prefs.edit().putString("focus_config", "半个 JSON").commit()
        assertEquals(FocusConfig.DEFAULT, FocusStore(context).readConfig())
    }

    @Test
    fun `clear 把会话与配置一起清掉`() {
        val store = FocusStore(context)
        store.edit(runtime())
        store.setConfig(FocusConfig(focusMinutes = 30))
        store.clear()
        assertNull(store.runtime.value)
        assertEquals(FocusConfig.DEFAULT, store.config.value)
        // 磁盘上也真的没了
        assertNull(FocusStore(context).readRuntime())
        assertEquals(FocusConfig.DEFAULT, FocusStore(context).readConfig())
    }

    @Test
    fun `publish 只更新内存镜像，不落盘`() {
        val store = FocusStore(context)
        store.publish(runtime())
        assertEquals(runtime(), store.runtime.value)
        // 重启之后什么都没剩下 —— 这就是 publish 与 edit 的区别
        assertNull(FocusStore(context).readRuntime())
    }
}
