package com.pocket.xiaoli.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文裁剪的单测。
 *
 * 重点验证一条**会直接导致服务端 HTTP 400 的不变量**：
 * assistant(tool_calls) 与它对应的 role=tool 回应必须同进同出。
 * 这个 bug 只在上下文长到触发裁剪时才出现，真机很难复现，所以必须靠单测守住。
 */
class AssistantContextBuilderTest {

    // ── 未超预算：原样返回 ──

    @Test
    fun `预算内原样返回`() {
        val history = listOf(
            msg(ChatRole.USER, "今天有什么课"),
            msg(ChatRole.ASSISTANT, "今天有高数"),
        )
        val current = listOf(msg(ChatRole.USER, "那明天呢"))
        val result = AssistantContextBuilder.build(history, current)
        assertEquals(3, result.size)
        assertEquals("今天有什么课", result[0].content)
        assertEquals("那明天呢", result[2].content)
    }

    @Test
    fun `空历史只返回本轮`() {
        val current = listOf(msg(ChatRole.USER, "你好"))
        val result = AssistantContextBuilder.build(emptyList(), current)
        assertEquals(1, result.size)
        assertEquals(ChatRole.USER, result[0].role)
    }

    // ── 超预算：按轮裁剪 ──

    /** 造一轮带工具调用的对话：user → assistant(tool_calls) → tool 回应 → assistant(最终回答)。 */
    private fun toolTurn(index: Int, padding: Int = 0): List<ChatMessage> {
        val call = ToolCall(id = "call_$index", name = "get_today_courses", argumentsJson = "{}")
        val filler = "x".repeat(padding)
        return listOf(
            msg(ChatRole.USER, "第${index}轮问题$filler"),
            ChatMessage(ChatRole.ASSISTANT, content = "我查一下$filler", toolCalls = listOf(call)),
            ChatMessage(ChatRole.TOOL, content = "工具结果$index$filler", toolCallId = "call_$index", toolName = "get_today_courses"),
            msg(ChatRole.ASSISTANT, "第${index}轮回答$filler"),
        )
    }

    @Test
    fun `超预算时丢弃最老的轮`() {
        // 每轮约 6000 字符，两轮必然超 12000 预算，只能保留最新一轮
        val oldTurn = toolTurn(1, padding = 6_000)
        val newTurn = toolTurn(2, padding = 6_000)

        val result = AssistantContextBuilder.build(oldTurn, newTurn)

        // 最新一轮的 user 问题必须在
        assertTrue(
            "最新问题不能被裁掉",
            result.any { it.role == ChatRole.USER && it.content.startsWith("第2轮问题") }
        )
        // 最老一轮的 user 问题应当被丢掉
        assertTrue(
            "最老一轮应被裁掉",
            result.none { it.role == ChatRole.USER && it.content.startsWith("第1轮问题") }
        )
    }

    @Test
    fun `裁剪结果中 tool_calls 与 tool 回应必须成对`() {
        // 这条是全文最重要的断言：违反它服务端会返回 400
        val oldTurn = toolTurn(1, padding = 6_000)
        val newTurn = toolTurn(2, padding = 6_000)

        val result = AssistantContextBuilder.build(oldTurn, newTurn)

        // 收集所有被请求调用的 id
        val requestedIds = result
            .flatMap { it.toolCalls }
            .map { it.id }
            .toSet()
        // 收集所有回应的 id
        val respondedIds = result
            .filter { it.role == ChatRole.TOOL }
            .mapNotNull { it.toolCallId }
            .toSet()

        assertEquals(
            "每个 tool_call 都必须有对应回应，反之亦然（否则服务端 400）",
            requestedIds,
            respondedIds,
        )
    }

    @Test
    fun `裁剪结果的第一个 user 之前不能有孤立的 assistant 或 tool`() {
        val oldTurn = toolTurn(1, padding = 6_000)
        val newTurn = toolTurn(2, padding = 6_000)
        val result = AssistantContextBuilder.build(oldTurn, newTurn)

        assertTrue("结果不能为空", result.isNotEmpty())
        assertEquals(
            "序列必须以 user 开头，否则协议非法",
            ChatRole.USER,
            result.first().role,
        )
    }

    @Test
    fun `一轮内部的消息不会被拆散`() {
        // 构造：老轮很大要被丢，新轮完整保留
        val oldTurn = toolTurn(1, padding = 8_000)
        val newTurn = toolTurn(2, padding = 100)
        val result = AssistantContextBuilder.build(oldTurn, newTurn)

        // 新轮的 4 条消息应全部在位（user / assistant(tool_calls) / tool / assistant）
        assertEquals("最新一轮 4 条消息应完整保留", 4, result.size)
        assertEquals(ChatRole.USER, result[0].role)
        assertEquals(ChatRole.ASSISTANT, result[1].role)
        assertTrue("assistant 应带 tool_calls", result[1].toolCalls.isNotEmpty())
        assertEquals(ChatRole.TOOL, result[2].role)
        assertEquals(ChatRole.ASSISTANT, result[3].role)
    }

    @Test
    fun `即使只有一轮超预算也不会把问题丢掉`() {
        // 单轮就超过预算：必须仍然返回内容，否则模型看不到用户问什么
        val hugeTurn = toolTurn(1, padding = 20_000)
        val result = AssistantContextBuilder.build(emptyList(), hugeTurn)

        assertTrue("不能裁成空列表", result.isNotEmpty())
        assertEquals(ChatRole.USER, result.first().role)
        assertTrue(result.first().content.startsWith("第1轮问题"))
    }

    @Test
    fun `无 user 的历史不会崩且返回原样`() {
        // 防御性：历史数据结构异常时不应抛异常
        val weird = listOf(msg(ChatRole.ASSISTANT, "孤立回答"))
        val current = listOf(msg(ChatRole.USER, "问题"))
        val result = AssistantContextBuilder.build(weird, current)
        assertEquals(2, result.size)
        assertEquals(ChatRole.ASSISTANT, result[0].role)
    }

    private fun msg(role: ChatRole, content: String) = ChatMessage(role = role, content = content)
}
