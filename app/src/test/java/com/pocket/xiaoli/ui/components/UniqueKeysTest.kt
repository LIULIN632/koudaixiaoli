package com.pocket.xiaoli.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 列表 key 去重的单测。
 *
 * ## 为什么这个必须测
 *
 * 重复 key 的后果是 **Compose 运行时直接抛异常**，不是警告、也不是样式错乱：
 *
 * ```
 * IllegalArgumentException: Key "高等数学|2025-2026-1" was already used.
 * ```
 *
 * 而它只在**真实数据重复**时才复现。开发时拿一份干净的测试数据，
 * 或者拿一个没有重修记录的成绩单，怎么看都是好的 —— 这正是一个
 * "测试数据挡住了 bug" 的典型场景。所以这里专门用会撞车的数据来断言。
 */
class UniqueKeysTest {

    private data class Row(val a: String, val b: String = "")

    @Test
    fun `没有重复时原样保留业务 key`() {
        val items = listOf(Row("高数"), Row("英语"), Row("物理"))

        val keys = uniqueKeys(items) { it.a }

        assertEquals(mapOf(0 to "高数", 1 to "英语", 2 to "物理"), keys)
    }

    @Test
    fun `重复的 key 会被补上位置后缀 从而保证唯一`() {
        // 真实场景：体育 1/2/3/4 都显示成「体育」，或者重修刷分导致同名课程出现两次
        val items = listOf(Row("体育"), Row("体育"), Row("体育"))

        val keys = uniqueKeys(items) { it.a }

        assertEquals(3, keys.size)
        assertEquals(3, keys.values.toSet().size) // 唯一 —— 这是避免崩溃的**唯一**条件
        assertEquals("体育", keys[0])             // 第一个保留原 key，便于排查
        assertEquals("体育#1", keys[1])
        assertEquals("体育#2", keys[2])
    }

    @Test
    fun `业务 key 为空时退化成位置 key 而不是空串`() {
        // 数据缺失（课程名为空）也必须能渲染，空串当 key 一样会互相撞
        val items = listOf(Row(""), Row(""))

        val keys = uniqueKeys(items) { it.a }

        assertEquals(2, keys.values.toSet().size)
        assertTrue(keys.values.none { it.isEmpty() })
    }

    @Test
    fun `稳定输入下结果稳定 保证条目跨重组能被复用`() {
        val items = listOf(Row("体育"), Row("高数"), Row("体育"))

        val first = uniqueKeys(items) { it.a }
        val second = uniqueKeys(items) { it.a }

        assertEquals(first, second)
    }

    @Test
    fun `先前不重复的项在插入同名项后 key 不变`() {
        // 关键性质：去重只影响**撞车的那几项**，不能因为列表头部插了一条
        // 就把所有下标平移 —— 那会导致每次重组全部重建条目（丢滚动位置、丢展开状态）
        val before = uniqueKeys(listOf(Row("高数"), Row("英语"))) { it.a }
        val after = uniqueKeys(listOf(Row("体育"), Row("高数"), Row("英语"))) { it.a }

        assertEquals("高数", before[0])
        assertEquals("高数", after[1])
        assertEquals("英语", before[1])
        assertEquals("英语", after[2])
    }

    @Test
    fun `空列表不炸`() {
        assertTrue(uniqueKeys(emptyList<Row>()) { it.a }.isEmpty())
    }

    @Test
    fun `多字段拼接的撞车也能识别`() {
        // 成绩列表用的是 `学期|课程|分数` 这类复合 key，字段单独不同但拼起来相同
        val items = listOf(
            Row("2025-2026-1", "体育"),
            Row("2025-2026-1", "体育"),
        )

        val keys = uniqueKeys(items) { "${it.a}|${it.b}" }

        assertEquals(2, keys.values.toSet().size)
    }
}
