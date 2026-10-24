package com.pocket.xiaoli.data.contribution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 共建榜本地账本（[ContributionStore]）。
 *
 * 这一层守的都是「坏了不报错」的约定：
 *
 * | 约定 | 写错的后果 |
 * |---|---|
 * | 默认关、加入前没有 cid | 用户没同意就产生标识 |
 * | 退出保留 `pendingLeave` | 断网退出后再也不会删服务端记录 |
 * | 日切按 UTC+8 | 晚上 8 点后本地以为还是昨天，上限提前用完 |
 * | 队列坏行跳过、超长裁旧 | 一条脏记录让整条补发链条停住 |
 * | 缓存字段全可空 | 一次 schema 变更把榜单页带崩 |
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContributionStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val prefs get() = context.getSharedPreferences("pocket_contribution", Context.MODE_PRIVATE)

    /** 2026-10-24 00:00（UTC+8）。固定日期，避免用例随运行日漂移。 */
    private val dayStart = LocalDate.of(2026, 10, 24)
        .atStartOfDay(ZoneOffset.ofHours(8))
        .toInstant()
        .toEpochMilli()

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `默认关 没有昵称 没有 cid 没有队列`() {
        val store = ContributionStore(context)
        assertFalse(store.enabled())
        assertEquals("", store.nickname())
        assertNull(store.contributorIdOrNull())
        assertTrue(store.pending().isEmpty())
        assertNull(store.cachedBoard())
    }

    @Test
    fun `cid 只在加入后生成，换实例不换号`() {
        val store = ContributionStore(context)
        // 没调 contributorId() 之前，磁盘上不该出现任何标识
        assertFalse(prefs.contains("cid"))

        val first = store.contributorId()
        assertNotNull(first)
        assertEquals(first, store.contributorId())
        // 新实例 = 进程重启，必须还是同一个 cid（否则积分每次重启清零）
        assertEquals(first, ContributionStore(context).contributorId())
    }

    @Test
    fun `退出时 pendingLeave 能存能清`() {
        val store = ContributionStore(context)
        store.setPendingLeave("c-1")
        assertEquals("c-1", store.pendingLeave())
        store.clearPendingLeave()
        assertNull(store.pendingLeave())
    }

    @Test
    fun `clearPersonal 清个人痕迹但保留删除钥匙`() {
        val store = ContributionStore(context)
        store.setEnabled(true)
        store.setNickname("鲤鱼王")
        store.contributorId()
        store.enqueue(PendingReport(ContributionAction.NOTE_SAVE, "e-1"))
        store.takeDailySlot(ContributionAction.NOTE_SAVE, dayStart)
        store.cacheBoard(
            ContributionBoard(
                category = ContributionCategory.NOTES,
                period = ContributionPeriod.ALL,
                entries = listOf(ContributionEntry(1, "鲤鱼王", 5, mine = true)),
                me = ContributionMe(1, 5, "鲤鱼王"),
                fetchedAt = dayStart,
            ),
        )
        store.setPendingLeave("c-1")

        store.clearPersonal()

        assertFalse(store.enabled())
        assertEquals("", store.nickname())
        assertNull(store.contributorIdOrNull())
        assertTrue(store.pending().isEmpty())
        assertNull(store.cachedBoard())
        // 日计数也归零：重新加入应当从今天的第一次开始
        assertTrue(store.takeDailySlot(ContributionAction.NOTE_SAVE, dayStart))
        // 删除钥匙必须留着，否则断网退出永远删不掉服务端记录
        assertEquals("c-1", store.pendingLeave())
    }

    @Test
    fun `每日上限按动作分别计，跨日重置`() {
        val store = ContributionStore(context)

        repeat(ContributionAction.ROOM_CELL_ADD.dailyCap) {
            assertTrue("第 ${it + 1} 次应当通过", store.takeDailySlot(ContributionAction.ROOM_CELL_ADD, dayStart))
        }
        assertFalse(store.takeDailySlot(ContributionAction.ROOM_CELL_ADD, dayStart))

        // 另一个动作有自己的额度，不共享计数
        assertTrue(store.takeDailySlot(ContributionAction.NOTE_SAVE, dayStart))

        // 同一个时刻仍算今天：23:59 还是受限
        assertFalse(
            store.takeDailySlot(
                ContributionAction.ROOM_CELL_ADD,
                dayStart + 86_400_000L - 1,
            ),
        )
        // 跨过 UTC+8 零点就重置
        assertTrue(store.takeDailySlot(ContributionAction.ROOM_CELL_ADD, dayStart + 86_400_000L))
    }

    @Test
    fun `日切按 UTC+8 而不是本机时区`() {
        // 2026-10-23 16:30 UTC == 2026-10-24 00:30 UTC+8
        val lateNight = LocalDate.of(2026, 10, 23)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli() + (16L * 60 + 30) * 60_000L
        assertEquals(
            LocalDate.of(2026, 10, 24),
            ContributionStore.dayOf(lateNight),
        )
        // 同一刻减 31 分钟就回到 23:59（前一天）
        assertEquals(
            LocalDate.of(2026, 10, 23),
            ContributionStore.dayOf(lateNight - 31 * 60_000L),
        )
    }

    @Test
    fun `队列按加入顺序补发，坏行直接跳过`() {
        val store = ContributionStore(context)
        prefs.edit()
            .putString(
                "queue",
                "note_save|e-1\n乱码\nroom_cell_add|e-2\nnote_save|\nbogus|e-3\ncourse_import|e-4",
            )
            .commit()

        assertEquals(
            listOf(
                PendingReport(ContributionAction.NOTE_SAVE, "e-1"),
                PendingReport(ContributionAction.ROOM_CELL_ADD, "e-2"),
                PendingReport(ContributionAction.COURSE_IMPORT, "e-4"),
            ),
            store.pending(),
        )
    }

    @Test
    fun `队列超过上限时裁掉最旧的`() {
        val store = ContributionStore(context)
        repeat(ContributionStore.MAX_QUEUE + 5) { index ->
            store.enqueue(PendingReport(ContributionAction.NOTE_SAVE, "e-$index"))
        }
        val queue = store.pending()
        assertEquals(ContributionStore.MAX_QUEUE, queue.size)
        // 丢掉的是最早的 5 条，队头应当是 e-5
        assertEquals("e-5", queue.first().eid)
        assertEquals("e-${ContributionStore.MAX_QUEUE + 4}", queue.last().eid)
    }

    @Test
    fun `榜单缓存能存能读回，读回标记为离线副本`() {
        val store = ContributionStore(context)
        val board = ContributionBoard(
            category = ContributionCategory.CAMPUS,
            period = ContributionPeriod.WEEK,
            entries = listOf(
                ContributionEntry(1, "鲤鱼王", 120, mine = false),
                ContributionEntry(2, "摸鱼佬", 80, mine = true),
            ),
            me = ContributionMe(2, 80, "摸鱼佬"),
            fetchedAt = dayStart,
        )
        store.cacheBoard(board)

        val back = ContributionStore(context).cachedBoard()
        assertNotNull(back)
        back!!
        assertEquals(ContributionCategory.CAMPUS, back.category)
        assertEquals(ContributionPeriod.WEEK, back.period)
        assertEquals(board.entries, back.entries)
        assertEquals(board.me, back.me)
        assertEquals(dayStart, back.fetchedAt)
        assertTrue(back.fromCache)
    }

    @Test
    fun `缓存坏 JSON 读出 null 而不是抛异常`() {
        prefs.edit().putString("board_cache", "{ 这不是 JSON").commit()
        assertNull(ContributionStore(context).cachedBoard())
    }

    @Test
    fun `缓存里的非法行被丢弃，负数分钳到 0`() {
        prefs.edit()
            .putString(
                "board_cache",
                """
                {"category":"campus","period":"week","fetchedAt":1,"entries":[
                  {"rank":0,"nickname":"无排名","points":10},
                  {"rank":1,"nickname":"   ","points":10},
                  {"rank":2,"nickname":"正常","points":-30}
                ],"me":{"rank":0,"nickname":"我","points":5}}
                """.trimIndent(),
            )
            .commit()

        val back = ContributionStore(context).cachedBoard()!!
        assertEquals(listOf(ContributionEntry(2, "正常", 0)), back.entries)
        assertNull(back.me)
    }

    @Test
    fun `缓存里的未知分类退回总榜，未知周期退回本周`() {
        prefs.edit()
            .putString("board_cache", """{"category":"bogus","period":null,"fetchedAt":0}""")
            .commit()

        val back = ContributionStore(context).cachedBoard()!!
        assertEquals(ContributionCategory.ALL, back.category)
        assertEquals(ContributionPeriod.WEEK, back.period)
        assertTrue(back.entries.isEmpty())
    }

    @Test
    fun `缓存里没有 entries 字段也不会崩`() {
        // 早期版本写下的只有分类与时间
        prefs.edit()
            .putString("board_cache", """{"category":"notes","fetchedAt":5}""")
            .commit()

        val back = ContributionStore(context).cachedBoard()!!
        assertEquals(ContributionCategory.NOTES, back.category)
        assertTrue(back.entries.isEmpty())
    }

    @Test
    fun `dayOf 的字符串格式稳定`() {
        val raw = ContributionStore.dayOf(dayStart).toString()
        assertEquals(
            LocalDate.of(2026, 10, 24).format(DateTimeFormatter.ISO_LOCAL_DATE),
            raw,
        )
        // 写进 prefs 的是 ISO 日期，跨版本可读
        val store = ContributionStore(context)
        assertTrue(store.takeDailySlot(ContributionAction.COURSE_IMPORT, dayStart))
        assertEquals("2026-10-24|1", prefs.getString("count_course_import", null))
        assertNotEquals(0, prefs.all.size)
    }
}
