package com.pocket.xiaoli.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 宿舍房号的构成与等价写法。
 *
 * 这些断言守的是**两件会静默出错的事**：
 * 1. 转盘拼出来的字符串（存进 `DormInfo.room` 的那个）必须能反解回原来的楼层/房号 ——
 *    拼错或解错，用户存进去的就是一间不存在的房；
 * 2. [DormRoomNumber.matchForms] 展开的等价写法必须**覆盖缴费系统地址里见过的所有形态** ——
 *    少一种，那类用户就会看到"没有匹配到该宿舍的缴费账户"。
 */
class DormRoomNumberTest {

    // ── compose / parse 往返 ──

    @Test
    fun `本校区房号是楼层加两位房号`() {
        assertEquals("412", DormRoomNumber.compose(side = null, floor = 4, room = 12))
        assertEquals("101", DormRoomNumber.compose(side = null, floor = 1, room = 1))
        assertEquals("1840", DormRoomNumber.compose(side = null, floor = 18, room = 40))
    }

    @Test
    fun `南校房号带侧翼前缀`() {
        assertEquals("n602", DormRoomNumber.compose(side = "n", floor = 6, room = 2))
        assertEquals("s1602", DormRoomNumber.compose(side = "s", floor = 16, room = 2))
    }

    @Test
    fun `拼出来的房号能原样反解`() {
        listOf(
            DormRoomNumber.compose(null, 4, 12),
            DormRoomNumber.compose("n", 6, 2),
            DormRoomNumber.compose("s", 16, 2),
            DormRoomNumber.compose("n", 18, 40),
        ).forEach { room ->
            val parsed = DormRoomNumber.parse(room)
            assertEquals("反解失败：$room", room, DormRoomNumber.compose(parsed?.side, parsed!!.floor, parsed.room))
        }
    }

    @Test
    fun `解析认大小写与中文方位`() {
        assertEquals(DormRoomNumber.Parsed("n", 6, 2), DormRoomNumber.parse("N602"))
        assertEquals(DormRoomNumber.Parsed("s", 6, 2), DormRoomNumber.parse("s602"))
        assertEquals(DormRoomNumber.Parsed("n", 6, 2), DormRoomNumber.parse("北602"))
        assertEquals(DormRoomNumber.Parsed("s", 16, 2), DormRoomNumber.parse("南1602"))
        assertEquals(DormRoomNumber.Parsed(null, 4, 12), DormRoomNumber.parse(" 412 "))
    }

    @Test
    fun `解析不出就返回 null，绝不瞎猜`() {
        // 老数据里可能存着用户在缴费页见过的写法；这类值必须原样保留，不能猜成"1 楼 14 间"
        assertNull(DormRoomNumber.parse("A114"))
        assertNull(DormRoomNumber.parse(""))
        assertNull(DormRoomNumber.parse("12"))       // 位数不够，拆不出房号
        assertNull(DormRoomNumber.parse("1999"))     // 楼层 19 超范围
        assertNull(DormRoomNumber.parse("4100"))     // 房号 00 超范围
    }

    // ── 电费匹配用的等价写法 ──

    @Test
    fun `南校房号展开成字母中文与纯数字三种写法`() {
        assertEquals(listOf("N602", "北602", "602"), DormRoomNumber.matchForms("n602"))
        assertEquals(listOf("S1602", "南1602", "1602"), DormRoomNumber.matchForms("s1602"))
    }

    @Test
    fun `本校区房号只有数字一种写法`() {
        assertEquals(listOf("412"), DormRoomNumber.matchForms("412"))
    }

    @Test
    fun `认不出的房号按原样匹配，不额外生造写法`() {
        assertEquals(listOf("A114"), DormRoomNumber.matchForms("A114"))
        assertEquals(emptyList<String>(), DormRoomNumber.matchForms("   "))
    }

    // ── 侧翼文案 ──

    @Test
    fun `侧翼字母与中文的对应关系`() {
        // ⚠️ n = 北、s = 南（英文 north/south）。这条断言存在的意义就是防止有人"顺手"按拼音改成 b/n
        assertEquals("北侧", DormRoomNumber.sideLabel("n"))
        assertEquals("南侧", DormRoomNumber.sideLabel("S"))
        assertEquals("—", DormRoomNumber.sideLabel(null))
        assertEquals(listOf("n", "s"), DormRoomNumber.SIDES.map { it.first })
    }
}
