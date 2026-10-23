package com.pocket.xiaoli.data.proactive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 记账本（[ProactiveStore]）。
 *
 * ## 为什么这一层也要测
 *
 * 它决定"这条提醒多久之后才会再说一次"，也就是这个功能**会不会变成骚扰**。而它有两条
 * 极易写反的规则，且写反之后**单测不看就完全看不出来**（界面表现只是"又冒了一次"）：
 *
 * | 规则 | 写反的后果 |
 * |---|---|
 * | `markTapped` **不写**全局时间戳 | 用户逗它一下，正事提醒被憋回 15 分钟 |
 * | `markDismissed` 累加、`markUseful` 清零 | 用户关掉的提醒照样勤快地冒 |
 *
 * 跑在 Robolectric 上：prefs 是本层的实体依赖，用假实现替代它等于把要测的东西换掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProactiveStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** 每个用例从一个干净的 prefs 开始：记账是全局单文件，残留会串味。 */
    @Before
    fun clearPrefs() {
        context.getSharedPreferences("pocket_proactive", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `默认档位是标准`() {
        assertEquals(ProactiveLevel.STANDARD, ProactiveStore(context).level.value)
    }

    @Test
    fun `档位落盘并能读回`() {
        val store = ProactiveStore(context)
        store.setLevel(ProactiveLevel.LOW)
        assertEquals(ProactiveLevel.LOW, store.level.value)

        // 换一个实例读同一个 prefs 文件（模拟进程重启）
        assertEquals(ProactiveLevel.LOW, ProactiveStore(context).level.value)
    }

    @Test
    fun `校历节点游标能存能读进快照`() {
        val store = ProactiveStore(context)
        // 没说过任何节点时是空串 —— 空串不可能等于任何一个 nodeKey，
        // 所以第一次评估天然会把最近的节点当作"还没说过"
        assertEquals("", store.calendarSeenKey())

        store.setCalendarSeenKey("寒假@2027-01-18")
        assertEquals("寒假@2027-01-18", store.calendarSeenKey())
        // 规则层看不见 prefs，只能通过 memory() 这一份快照拿到它
        assertEquals("寒假@2027-01-18", store.memory().lastCalendarSeenKey)
    }

    @Test
    fun `天气去重键按集合存取`() {
        val store = ProactiveStore(context)
        assertTrue("没说过任何天气时是空集合", store.weatherSeenKeys().isEmpty())

        store.rememberWeatherSeenKey("rain@2026-10-23")
        store.rememberWeatherSeenKey("thunder@2026-10-23")

        assertEquals(
            "一天里可能先后有雨、雷两件事，不能像校历那样只留一个游标",
            setOf("rain@2026-10-23", "thunder@2026-10-23"),
            store.weatherSeenKeys(),
        )
    }

    @Test
    fun `天气去重集合不重复且有上限`() {
        val store = ProactiveStore(context)
        store.rememberWeatherSeenKey("rain@2026-10-23")
        store.rememberWeatherSeenKey("rain@2026-10-23")
        assertEquals(1, store.weatherSeenKeys().size)

        repeat(12) { store.rememberWeatherSeenKey("kind$it@2026-10-23") }
        assertEquals("记账不能无限长下去", 8, store.weatherSeenKeys().size)
        assertTrue("被挤掉的是更早的键", "kind11@2026-10-23" in store.weatherSeenKeys())
    }

    @Test
    fun `空天气键不会写进账本`() {
        val store = ProactiveStore(context)
        store.rememberWeatherSeenKey("")
        store.rememberWeatherSeenKey("   ")
        assertTrue(store.weatherSeenKeys().isEmpty())
    }

    @Test
    fun `档位坏值退回默认`() {
        context.getSharedPreferences("pocket_proactive", Context.MODE_PRIVATE)
            .edit().putString("level", "SOMETHING_ELSE").commit()
        // 构造时就会读一次磁盘：坏值不能让 level 变成 null 或崩掉
        assertEquals(ProactiveLevel.STANDARD, ProactiveStore(context).level.value)
    }

    @Test
    fun `冒过一次会同时记下这条规则与全局时间`() {
        val store = ProactiveStore(context)
        store.markShown(ProactiveRules.ID_CLASS)
        val memory = store.memory()
        assertTrue(memory.lastShownAt(ProactiveRules.ID_CLASS) > 0L)
        assertTrue("正事冒泡必须占用全局节流", memory.lastAnyAt > 0L)
    }

    @Test
    fun `全局时间戳不会被当成一条规则`() {
        // `shown_any` 与 `shown_<id>` 共用前缀，收进 map 时会多出一条叫 "any" 的规则；
        // 真出现的话，`cooldownFor("any")` 会拿一小时的默认冷却去挡别的判断
        val store = ProactiveStore(context)
        store.markShown(ProactiveRules.ID_CLASS)
        val memory = store.memory()
        assertNull("不该存在 id 为 any 的规则", memory.lastShown["any"])
        assertEquals(1, memory.lastShown.size)
    }

    @Test
    fun `戳一下不占全局额度`() {
        val store = ProactiveStore(context)
        store.markTapped(ProactiveRules.CHATTER_ID)
        val memory = store.memory()
        assertTrue("要记下这句话说过了", memory.lastShownAt(ProactiveRules.CHATTER_ID) > 0L)
        assertEquals("但绝不能把正事提醒憋回去", 0L, memory.lastAnyAt)
    }

    @Test
    fun `被关掉会累加退避`() {
        val store = ProactiveStore(context)
        store.markDismissed(ProactiveRules.ID_EXAM)
        store.markDismissed(ProactiveRules.ID_EXAM)
        assertEquals(2, store.memory().dismissedCount(ProactiveRules.ID_EXAM))
    }

    @Test
    fun `点开过就清零退避`() {
        val store = ProactiveStore(context)
        store.markDismissed(ProactiveRules.ID_EXAM)
        store.markUseful(ProactiveRules.ID_EXAM)
        assertEquals(0, store.memory().dismissedCount(ProactiveRules.ID_EXAM))
    }

    @Test
    fun `最近说过的闲聊有去重与条数上限`() {
        val store = ProactiveStore(context)
        store.rememberChatterLine("a")
        store.rememberChatterLine("b")
        store.rememberChatterLine("a")
        assertEquals(listOf("a", "b"), store.recentChatter())

        repeat(ProactiveStore.CHATTER_RECENT_MAX + 5) { store.rememberChatterLine("line_$it") }
        assertEquals(ProactiveStore.CHATTER_RECENT_MAX, store.recentChatter().size)
    }

    @Test
    fun `成绩游标首次读取是此刻而不是零`() {
        // 从 0 起算的话，用户装上这个版本的第一眼就是"有 42 门新成绩出了" ——
        // 那些成绩他早就看过，那不是提醒，是骚扰
        val store = ProactiveStore(context)
        val before = System.currentTimeMillis()
        val cursor = store.gradeSeenAt()
        assertTrue(cursor >= before)
        assertEquals("第二次读要稳定，不能每读一次就往后推", cursor, store.gradeSeenAt())
    }

    @Test
    fun `成绩游标推进后按新值算`() {
        val store = ProactiveStore(context)
        store.setGradeSeenAt(1234L)
        assertEquals(1234L, ProactiveStore(context).gradeSeenAt())
    }

    @Test
    fun `余额快照按身份分开存`() {
        val store = ProactiveStore(context)
        store.rememberBalance(ProactiveStore.KIND_CARD, "13800000000", 12.5)
        store.rememberBalance(ProactiveStore.KIND_CARD, "13900000000", 88.0)

        assertEquals(
            12.5,
            store.balanceSnapshot(ProactiveStore.KIND_CARD, "13800000000", 60_000L)!!,
            0.001,
        )
        assertEquals(
            "换了账号就不该念上一个号的余额",
            88.0,
            store.balanceSnapshot(ProactiveStore.KIND_CARD, "13900000000", 60_000L)!!,
            0.001,
        )
        assertNull(store.balanceSnapshot(ProactiveStore.KIND_CARD, "13700000000", 60_000L))
    }

    @Test
    fun `余额快照过期就不再用`() {
        val store = ProactiveStore(context)
        store.rememberBalance(ProactiveStore.KIND_ELECTRICITY, "3号楼|401|", 8.0)
        assertNull(
            "余额是会变的：过期的数字说出来比不说更糟（用户可能刚充过钱）",
            store.balanceSnapshot(ProactiveStore.KIND_ELECTRICITY, "3号楼|401|", maxAgeMs = 0L),
        )
    }

    @Test
    fun `电费与校园卡互不干扰`() {
        val store = ProactiveStore(context)
        store.rememberBalance(ProactiveStore.KIND_CARD, "same-key", 100.0)
        store.rememberBalance(ProactiveStore.KIND_ELECTRICITY, "same-key", 5.0)
        assertEquals(100.0, store.balanceSnapshot(ProactiveStore.KIND_CARD, "same-key", 60_000L)!!, 0.001)
        assertEquals(5.0, store.balanceSnapshot(ProactiveStore.KIND_ELECTRICITY, "same-key", 60_000L)!!, 0.001)
    }

    @Test
    fun `坏掉的余额存档不会崩`() {
        context.getSharedPreferences("pocket_proactive", Context.MODE_PRIVATE)
            .edit().putString("balance_${ProactiveStore.KIND_CARD}|x", "not-a-number|also-not").commit()
        assertNull(ProactiveStore(context).balanceSnapshot(ProactiveStore.KIND_CARD, "x", 60_000L))
    }
}
