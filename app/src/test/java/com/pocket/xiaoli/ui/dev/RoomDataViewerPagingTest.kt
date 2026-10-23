package com.pocket.xiaoli.ui.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 导出分页规则。
 *
 * 历史 bug：`exportJson` 只读第一页就当读完了，于是**任何超过 2000 行的表在完整备份里
 * 被静默截断** —— 不报错、不提示，恢复回来就是少数据。这条门禁要求
 * 「导入 → 导出 → 再导入，字段一致」，截断正是最难发现的那类不一致。
 *
 * 真正碰数据库的那层没法在 JVM 单测里跑（没引 `room-testing`），所以把**推进规则**
 * 抽成纯函数 [RoomDataViewer.nextOffset] 单独覆盖。
 */
class RoomDataViewerPagingTest {

    private val page = RoomDataViewer.EXPORT_PAGE_SIZE

    @Test
    fun `读回整页时继续翻页`() {
        assertEquals(0 + page, RoomDataViewer.nextOffset(0, page, page) ?: -1)
        assertEquals(page + page, RoomDataViewer.nextOffset(page, page, page) ?: -1)
    }

    @Test
    fun `读回不足一页就是表尾`() {
        assertNull(RoomDataViewer.nextOffset(0, page - 1, page))
        assertNull(RoomDataViewer.nextOffset(page, 1, page))
    }

    @Test
    fun `读回空页就是表尾`() {
        assertNull(RoomDataViewer.nextOffset(0, 0, page))
    }

    @Test
    fun `两千五百行会被完整读完，不会停在两千行`() {
        val total = 2_500
        var offset = 0
        var seen = 0
        var laps = 0
        while (true) {
            val read = minOf(page, total - offset)
            seen += read
            laps += 1
            offset = RoomDataViewer.nextOffset(offset, read, page) ?: break
            check(laps < 100) { "分页没有收敛：$laps 轮后 offset=$offset" }
        }
        assertEquals("导出的行数必须等于表里的行数", total, seen)
        assertEquals("2500 行应当恰好读两页", 2, laps)
    }
}
