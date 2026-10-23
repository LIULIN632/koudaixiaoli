package com.pocket.xiaoli.ui.courseselect

import com.pocket.xiaoli.network.CourseSelectItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PreSelectMerger] 的穷举测试。
 *
 * 这段逻辑决定"这门课我到底选上没有"，判错了用户会据此做错决定（以为没选上又去抢一次），
 * 所以边界都要钉住。它是纯函数，测试不需要 Android 环境。
 */
class PreSelectMergerTest {

    private fun item(
        kcmc: String,
        kch: String = "",
        kcyxbId: String = "",
        // 默认空串而不是 "0"：真实列表端点尚未预选时给的是 "0"，
        // 但测试夹具用空串更能暴露"字段没被赋值"，不会把默认值当成数据。
        xsyxbId: String = "",
        sftkzt: String = "",
        xf: String = "",
        sxrsmc: String = "",
        fromChoosed: Boolean = false,
    ) = CourseSelectItem(
        kch = kch,
        kcmc = kcmc,
        kcyxbId = kcyxbId,
        xsyxbId = xsyxbId,
        sftkzt = sftkzt,
        xf = xf,
        sxrsmc = sxrsmc,
        chosen = fromChoosed || (xsyxbId.isNotBlank() && xsyxbId != "0"),
    )

    // ────────────────────── 合并键 ──────────────────────

    @Test
    fun `合并键优先用行 ID`() {
        assertEquals(
            "行 ID 是行级唯一键，必须优先",
            "id:ROW1",
            PreSelectMerger.mergeKey(item("高等数学", kch = "B1", kcyxbId = "ROW1")),
        )
    }

    @Test
    fun `没有行 ID 时退到课程代码加课名 再退到课名`() {
        assertEquals("kc:B1|高等数学", PreSelectMerger.mergeKey(item("高等数学", kch = "B1")))
        assertEquals("nm:高等数学", PreSelectMerger.mergeKey(item("高等数学")))
    }

    // ────────────────────── 合并 ──────────────────────

    @Test
    fun `已选端点把对应行标记为已选并补齐真实 ID`() {
        val list = listOf(item("高等数学", kch = "B1", kcyxbId = "ROW1", sxrsmc = "120/60"))
        val choosed = listOf(item("高等数学", kch = "B1", kcyxbId = "ROW1", xsyxbId = "9F8E"))
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertTrue("左表里这门课应变成已选", merged.chosen)
        assertEquals("预选 ID 要用已选端点的真实值", "9F8E", merged.xsyxbId)
        assertEquals("左表的余量信息必须保留", "120/60", merged.sxrsmc)
    }

    @Test
    fun `未在已选端点出现的课保持未选`() {
        val list = listOf(item("高等数学", kcyxbId = "ROW1"), item("大学英语", kcyxbId = "ROW2"))
        val choosed = listOf(item("高等数学", kcyxbId = "ROW1", xsyxbId = "9F8E"))
        val merged = PreSelectMerger.merge(list, choosed)
        assertEquals(2, merged.size)
        assertTrue(merged[0].chosen)
        assertFalse("没选上的课不能被标成已选", merged[1].chosen)
    }

    @Test
    fun `同一门课的多个教学班不会互相覆盖`() {
        // 这是选 mergeKey 用行 ID 而不是课程代码的全部理由：
        // 两个教学班同 kch、不同 kcyxb_id，用 kch 当键就会只剩一条。
        val list = listOf(
            item("高等数学", kch = "B1", kcyxbId = "ROW1", sxrsmc = "60/60"),
            item("高等数学", kch = "B1", kcyxbId = "ROW2", sxrsmc = "60/12"),
        )
        val choosed = listOf(item("高等数学", kch = "B1", kcyxbId = "ROW2", xsyxbId = "AAA"))
        val merged = PreSelectMerger.merge(list, choosed)
        assertEquals("两个教学班必须都在", 2, merged.size)
        assertFalse("ROW1 没选上", merged[0].chosen)
        assertTrue("ROW2 选上了", merged[1].chosen)
        assertEquals("余量不能错位", "60/60", merged[0].sxrsmc)
        assertEquals("60/12", merged[1].sxrsmc)
    }

    @Test
    fun `两侧都有行 ID 时对不上就是不匹配 不许按课名兜`() {
        // ⚠️ 这条钉的是一个**真实的实现缺陷**：最初的实现无条件按课名兜底，
        // 于是同名的两个教学班（不同 kcyxb_id）会被判成同一行 ——
        // ROW1 没选，界面上却显示「已选」。
        // 行 ID 是行级唯一键，对不上就是两行，"没匹配上"是结论而不是信息不足。
        val list = listOf(item("高等数学", kch = "B1", kcyxbId = "ROW1"))
        val choosed = listOf(item("高等数学", kch = "B1", kcyxbId = "ROW2", xsyxbId = "ZZZ"))
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertFalse("ROW1 与 ROW2 是两行，不能因为课名相同就标成已选", merged.chosen)
        assertEquals("", merged.xsyxbId)
    }

    @Test
    fun `两侧都有行 ID 且相同才算匹配`() {
        val list = listOf(item("高等数学", kcyxbId = "ROW1"))
        val choosed = listOf(item("高等数学", kcyxbId = "ROW1", xsyxbId = "REAL_ID"))
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertTrue(merged.chosen)
        assertEquals("REAL_ID", merged.xsyxbId)
    }

    @Test
    fun `两侧都没有行 ID 时才允许按课名兜`() {
        // 有些部署的列表端点不返回 kcyxb_id，此时课名是唯一可用依据
        val list = listOf(item("高等数学", kch = "B1"))
        val choosed = listOf(item("高等数学", kch = "B1", xsyxbId = "REAL_ID"))
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertTrue("都没有行 ID 时课名是唯一依据，应认作同一门课", merged.chosen)
        assertEquals("REAL_ID", merged.xsyxbId)
    }

    @Test
    fun `右表同名候选多于一个时也不猜`() {
        // 右表有两个同名教学班、左表没有行 ID ⇒ 无法判断是哪一个，宁可不动
        val list = listOf(item("高等数学", kch = "B1"))
        val choosed = listOf(
            item("高等数学", kcyxbId = "ROW1", xsyxbId = "A"),
            item("高等数学", kcyxbId = "ROW2", xsyxbId = "B"),
        )
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertFalse("无法判断是哪一行时不能猜", merged.chosen)
    }

    @Test
    fun `已选端点带来停开状态`() {
        val list = listOf(item("高等数学", kcyxbId = "ROW1"))
        val choosed = listOf(item("高等数学", kcyxbId = "ROW1", xsyxbId = "A", sftkzt = "1"))
        val merged = PreSelectMerger.merge(list, choosed).single()
        assertEquals("停开状态只在已选端点里，必须带过来", "1", merged.sftkzt)
    }

    // ────────────────────── 退化输入 ──────────────────────

    @Test
    fun `左表为空时直接用右表`() {
        val choosed = listOf(item("高等数学", xsyxbId = "A", fromChoosed = true))
        val merged = PreSelectMerger.merge(emptyList(), choosed)
        assertEquals(1, merged.size)
        assertTrue(merged[0].chosen)
    }

    @Test
    fun `右表为空时左表原样返回`() {
        val list = listOf(item("高等数学", kcyxbId = "ROW1"))
        val merged = PreSelectMerger.merge(list, emptyList())
        assertEquals(1, merged.size)
        assertFalse("尚未预选是常态，不是异常", merged[0].chosen)
    }

    @Test
    fun `两边都为空时返回空`() {
        assertTrue(PreSelectMerger.merge(emptyList(), emptyList()).isEmpty())
    }
}
