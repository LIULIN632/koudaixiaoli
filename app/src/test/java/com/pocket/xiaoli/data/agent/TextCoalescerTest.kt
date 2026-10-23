package com.pocket.xiaoli.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式合并的两条不变量。
 *
 * 这三件事（时间窗、与工具事件的相对顺序、不丢已收到的文本）都很难靠人眼在模拟器里
 * 验证 —— 顺序错乱只在"模型先说半句再调工具"时出现，丢文本只在"刚好在节拍之间点停止"
 * 时出现。所以用纯单测把规则锁死。
 */
class TextCoalescerTest {

    /** 用 Int 当"文本块"、String 当"控制事件"，避免测试依赖 AgentEvent。 */
    private fun coalescer() = TextCoalescer<Int, String>(
        merge = { chunks -> chunks.sum() },
        mergeReasoning = { chunks -> chunks.sum() },
    )

    @Test
    fun `一次节拍把多个文本块合并成一个`() {
        val c = coalescer()
        c.onText(1); c.onText(2); c.onText(3)

        val flushed = c.canFlush()

        assertEquals(1, flushed.size)
        assertEquals(TextCoalescer.Flush.Text(6), flushed[0])
        assertFalse("取过一次就不该再有待发内容", c.hasPending)
    }

    @Test
    fun `没有待发内容时不产生空的界面更新`() {
        val c = coalescer()
        assertFalse(c.hasPending)
        assertTrue("空节拍必须是零输出，否则仍然是一次无意义的状态更新", c.canFlush().isEmpty())
    }

    @Test
    fun `工具事件必须排在待发文本之后 不能插队`() {
        val c = coalescer()
        c.onText(1)
        c.onEvent("tool_started")

        val flushed = c.canFlush()

        // 顺序是硬要求：反过来的话，界面上工具脚注会被画在那半句**前面**，版式就错了
        assertEquals(2, flushed.size)
        assertEquals(TextCoalescer.Flush.Text(1), flushed[0])
        assertEquals(TextCoalescer.Flush.Events(listOf("tool_started")), flushed[1])
    }

    @Test
    fun `只有事件没有文本时 直接给出事件`() {
        val c = coalescer()
        c.onEvent("tool_started")
        c.onEvent("tool_finished")

        val flushed = c.canFlush()

        assertEquals(1, flushed.size)
        assertEquals(TextCoalescer.Flush.Events(listOf("tool_started", "tool_finished")), flushed[0])
    }

    @Test
    fun `强制冲刷不丢已收到的文本`() {
        val c = coalescer()
        c.onText(7); c.onText(8)
        c.onEvent("cancelled")

        val flushed = c.forceFlush()

        // 用户的「停止」走的就是这条路：已经收到的字必须原样出现在输出里
        assertEquals(2, flushed.size)
        assertEquals(TextCoalescer.Flush.Text(15), flushed[0])
        assertFalse(c.hasPending)
    }

    @Test
    fun `多轮节拍之间文本不会被上一轮带走`() {
        val c = coalescer()
        c.onText(1)
        c.canFlush()
        c.onText(2)

        val flushed = c.canFlush()

        assertEquals(1, flushed.size)
        assertEquals(TextCoalescer.Flush.Text(2), flushed[0])
    }

    @Test
    fun `discard 丢掉缓冲 用于整轮作废`() {
        val c = coalescer()
        c.onText(1)
        c.onEvent("x")
        c.discard()

        assertFalse(c.hasPending)
        assertTrue(c.canFlush().isEmpty())
    }

    @Test
    fun `反复 canFlush 幂等 不会重复输出同一批文本`() {
        val c = coalescer()
        c.onText(1)

        assertEquals(1, c.canFlush().size)
        assertEquals(0, c.canFlush().size)
        assertEquals(0, c.canFlush().size)
    }

    // ── 思考链 ──

    @Test
    fun `思考链与正文在同一个块里 且思考链在前`() {
        val c = coalescer()
        c.onReasoning(100)
        c.onText(1)

        val flushed = c.canFlush()

        assertEquals(1, flushed.size)
        val block = flushed[0] as TextCoalescer.Flush.Text
        assertEquals(1, block.chunk)
        assertEquals(100, block.reasoning)
    }

    @Test
    fun `只有思考链没有正文时 正文块为空而思考链保留`() {
        val c = coalescer()
        c.onReasoning(5)

        val block = c.canFlush().single() as TextCoalescer.Flush.Text

        // 推理阶段常常先来很久：这一段必须能单独推出去，否则界面上"正在思考"永远不出现
        assertEquals(0, block.chunk)
        assertEquals(5, block.reasoning)
    }

    @Test
    fun `没有思考链时 reasoning 为 null 而不是空块`() {
        val c = coalescer()
        c.onText(1)

        val block = c.canFlush().single() as TextCoalescer.Flush.Text

        assertEquals(null, block.reasoning)
    }

    @Test
    fun `思考链也必须让位给控制事件的顺序`() {
        val c = coalescer()
        c.onReasoning(9)
        c.onEvent("tool_started")

        val flushed = c.canFlush()

        assertEquals(2, flushed.size)
        assertEquals(9, (flushed[0] as TextCoalescer.Flush.Text).reasoning)
        assertEquals(TextCoalescer.Flush.Events(listOf("tool_started")), flushed[1])
    }

    @Test
    fun `discard 也会清掉思考链`() {
        val c = coalescer()
        c.onReasoning(1)
        c.discard()

        assertFalse(c.hasPending)
    }
}
