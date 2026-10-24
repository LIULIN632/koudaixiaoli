package com.pocket.xiaoli.data.agent

import com.google.gson.JsonObject
import com.pocket.xiaoli.data.knowledge.AssistantSource
import com.pocket.xiaoli.data.knowledge.AssistantTool
import com.pocket.xiaoli.data.knowledge.ToolOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent 循环的单测。
 *
 * 循环里最容易坏、真机最难复现的四条路径：工具回应配对、熔断降级、长度续写、
 * 失败短句/原文分离。它们都依赖"模型返回什么"这种外部输入，
 * 所以这里用 [AssistantStreamSource] 的脚本实现把每条分支挨个走一遍。
 */
class AssistantAgentLoopTest {

    // ── 脚本化的「模型」 ──

    private class ScriptedSource(
        private val script: List<() -> Flow<LlmStreamEvent>>,
    ) : AssistantStreamSource {
        val requests = mutableListOf<LlmRequest>()
        private var index = 0

        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
            requests += request
            // 脚本用完后重复最后一步，模拟"模型一直这样答"，避免测试因越界崩掉
            val step = script.getOrNull(index) ?: script.last()
            index++
            return step()
        }
    }

    private fun reply(text: String, truncated: Boolean = false): Flow<LlmStreamEvent> = flow {
        if (text.isNotEmpty()) emit(LlmStreamEvent.TextDelta(text))
        emit(
            LlmStreamEvent.Completed(
                ChatMessage(role = ChatRole.ASSISTANT, content = text),
                truncated = truncated,
            )
        )
    }

    private fun toolCallRound(vararg calls: ToolCall): Flow<LlmStreamEvent> = flow {
        emit(LlmStreamEvent.Completed(ChatMessage(role = ChatRole.ASSISTANT, toolCalls = calls.toList())))
    }

    private fun failed(text: String): Flow<LlmStreamEvent> = flow {
        emit(LlmStreamEvent.Failed(text))
    }

    /** 记录执行过哪些工具，并按名字产出固定结果。 */
    private class ToolLog {
        val executed = mutableListOf<String>()

        fun registry(): AssistantToolRegistry = AssistantToolRegistry(
            listOf("get_today_courses", "get_next_course").map { name ->
                AssistantToolSpec(
                    tool = AssistantTool.TODAY_COURSES,
                    name = name,
                    description = name,
                    parameters = JsonObject().apply { addProperty("type", "object") },
                    handler = {
                        executed += name
                        ToolOutcome("$name 的结果")
                    },
                )
            }
        )
    }

    // ── 正常路径 ──

    @Test
    fun `模型不调工具时直接给答案`() = runBlocking {
        val source = ScriptedSource(listOf({ reply("你好，我是鲤鱼") }))
        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("你是谁", emptyList())
            .toList()

        val answers = events.filterIsInstance<AgentEvent.Answer>()
        assertEquals(1, answers.size)
        assertEquals("你好，我是鲤鱼", answers[0].text)
        assertNotNull("第一次请求必须带上工具定义", source.requests[0].toolsJson)
    }

    @Test
    fun `工具调用按不变量 A 逐条配对回灌`() = runBlocking {
        val log = ToolLog()
        val source = ScriptedSource(
            listOf(
                { toolCallRound(ToolCall("c1", "get_today_courses", "{}"), ToolCall("c2", "get_next_course", "{}")) },
                { reply("今天两节课，下一节是高数") },
            )
        )

        val changes = mutableListOf<List<ChatMessage>>()
        val events = AssistantAgentLoop(source, log.registry())
            .run("今天有课吗", emptyList(), onMessagesChanged = { changes += it })
            .toList()

        assertEquals(listOf("get_today_courses", "get_next_course"), log.executed)
        assertEquals("今天两节课，下一节是高数", events.filterIsInstance<AgentEvent.Answer>().single().text)

        val second = source.requests[1].messages
        val callIndex = second.indexOfFirst { it.toolCalls.size == 2 }
        assertTrue("第二次请求里必须能看到上一轮的 assistant(tool_calls)", callIndex >= 0)
        assertEquals(ChatRole.TOOL, second[callIndex + 1].role)
        assertEquals("c1", second[callIndex + 1].toolCallId)
        assertEquals(ChatRole.TOOL, second[callIndex + 2].role)
        assertEquals("c2", second[callIndex + 2].toolCallId)

        // 落库的消息同样必须成对（不变量 A 约束的是持久化后的历史）
        val persisted = changes.last()
        val requested = persisted.flatMap { it.toolCalls }.map { it.id }.toSet()
        val responded = persisted.filter { it.role == ChatRole.TOOL }.mapNotNull { it.toolCallId }.toSet()
        assertEquals(requested, responded)
    }

    @Test
    fun `工具返回的来源随 ToolFinished 透传给界面`() = runBlocking {
        val source = AssistantSource(
            marker = "K1",
            title = "图书馆开放时间",
            meta = "更新于 2025-09-20",
            snippet = "平时 07:00-22:00",
        )
        val registry = AssistantToolRegistry(
            listOf(
                AssistantToolSpec(
                    tool = AssistantTool.SEARCH_KNOWLEDGE,
                    name = "search_knowledge",
                    description = "知识库",
                    parameters = JsonObject().apply { addProperty("type", "object") },
                    handler = { ToolOutcome("命中一条", sources = listOf(source)) },
                )
            )
        )
        val scripted = ScriptedSource(
            listOf(
                { toolCallRound(ToolCall("c1", "search_knowledge", "{}")) },
                { reply("平时 07:00-22:00 开放[K1]。") },
            )
        )

        val finished = AssistantAgentLoop(scripted, registry)
            .run("图书馆几点开门", emptyList())
            .toList()
            .filterIsInstance<AgentEvent.ToolFinished>()
            .single()

        assertEquals(listOf(source), finished.sources)
    }

    // ── 熔断降级 ──

    @Test
    fun `工具预算用尽后摘掉工具再请求一次而不是直接认输`() = runBlocking {
        val rounds = (1..AssistantAgentLoop.MAX_TOOL_ROUNDS).map { i ->
            { toolCallRound(ToolCall("c$i", "get_today_courses", "{}")) }
        }
        val source = ScriptedSource(rounds + listOf({ reply("根据已查到的信息：今天有高数") }))

        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("今天有什么课", emptyList())
            .toList()

        val last = source.requests.last()
        assertNull("熔断轮不能再提供工具", last.toolsJson)
        assertTrue(
            "要明确告诉模型别再用工具了",
            last.messages.last().content.contains("工具调用次数已达上限"),
        )
        assertEquals(
            "最终回答应取自熔断轮，而不是兜底文案",
            "根据已查到的信息：今天有高数",
            events.filterIsInstance<AgentEvent.Answer>().single().text,
        )
        assertEquals(AssistantAgentLoop.MAX_TOOL_ROUNDS + 1, source.requests.size)
    }

    @Test
    fun `熔断轮也失败时退回已有文本且不再弹错误卡片`() = runBlocking {
        val rounds = (1..AssistantAgentLoop.MAX_TOOL_ROUNDS).map { i ->
            { toolCallRound(ToolCall("c$i", "get_today_courses", "{}")) }
        }
        val source = ScriptedSource(rounds + listOf({ failed("模型请求失败（HTTP 500）") }))

        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("今天有什么课", emptyList())
            .toList()

        assertTrue("熔断失败不应该再打扰用户", events.none { it is AgentEvent.Error })
        assertEquals(
            AssistantAgentLoop.FALLBACK_ANSWER,
            events.filterIsInstance<AgentEvent.Answer>().single().text,
        )
    }

    @Test
    fun `熔断轮模型仍请求工具时不执行也不落库`() = runBlocking {
        val log = ToolLog()
        val rounds = (1..AssistantAgentLoop.MAX_TOOL_ROUNDS).map { i ->
            { toolCallRound(ToolCall("c$i", "get_today_courses", "{}")) }
        }
        // 熔断轮模型越权：明明没给工具还非要调
        val source = ScriptedSource(
            rounds + listOf({ toolCallRound(ToolCall("rogue", "get_today_courses", "{}")) })
        )

        val changes = mutableListOf<List<ChatMessage>>()
        AssistantAgentLoop(source, log.registry())
            .run("今天有什么课", emptyList(), onMessagesChanged = { changes += it })
            .toList()

        assertEquals(
            "熔断轮的工具绝不能执行",
            AssistantAgentLoop.MAX_TOOL_ROUNDS,
            log.executed.size,
        )
        // 落库的最后一条 assistant 不能还带着未被回应的 tool_calls（否则下一轮 400）
        val lastTurn = changes.last()
        val lastAssistant = lastTurn.last { it.role == ChatRole.ASSISTANT }
        assertTrue("落库前必须摘掉越权请求的 tool_calls", lastAssistant.toolCalls.isEmpty())
    }

    // ── 长度续写 ──

    @Test
    fun `回答被截断时续写并把增量拼回同一条回答`() = runBlocking {
        val source = ScriptedSource(
            listOf(
                { reply("第一段", truncated = true) },
                { reply("第二段") },
            )
        )

        val changes = mutableListOf<List<ChatMessage>>()
        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("详细讲讲", emptyList(), onMessagesChanged = { changes += it })
            .toList()

        assertEquals("第一段第二段", events.filterIsInstance<AgentEvent.Answer>().single().text)
        // 续写请求必须摘工具：模型此刻该接着写，而不是再查一轮
        assertNull(source.requests[1].toolsJson)
        assertTrue(
            "续写指令要挂在请求末尾",
            source.requests[1].messages.last().content.contains("继续写完"),
        )
        assertEquals(
            "续写指令不能落库（不变量 C）",
            "第一段第二段",
            changes.last().last { it.role == ChatRole.ASSISTANT }.content,
        )
    }

    @Test
    fun `续写次数有上限不会无限循环`() = runBlocking {
        // 模型每次都声称自己被截断：续写最多 MAX_CONTINUATIONS 次就收手
        val source = ScriptedSource(listOf({ reply("片段", truncated = true) }))

        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("写长一点", emptyList())
            .toList()

        assertEquals(AssistantAgentLoop.MAX_CONTINUATIONS + 1, source.requests.size)
        assertEquals(
            "片段".repeat(AssistantAgentLoop.MAX_CONTINUATIONS + 1),
            events.filterIsInstance<AgentEvent.Answer>().single().text,
        )
    }

    @Test
    fun `带工具调用的截断回答不续写而是先执行工具`() = runBlocking {
        val log = ToolLog()
        val source = ScriptedSource(
            listOf(
                // 模型一边说要调工具、一边被截断：此刻续写会让请求里出现没有回应的 tool_calls
                {
                    flow {
                        emit(LlmStreamEvent.Completed(
                            ChatMessage(role = ChatRole.ASSISTANT, toolCalls = listOf(ToolCall("c1", "get_today_courses", "{}"))),
                            truncated = true,
                        ))
                    }
                },
                { reply("今天有课") },
            )
        )

        AssistantAgentLoop(source, log.registry())
            .run("今天有课吗", emptyList())
            .toList()

        assertEquals(listOf("get_today_courses"), log.executed)
        assertEquals("不该为带 tool_calls 的截断回答发续写请求", 2, source.requests.size)
    }

    // ── 失败路径 ──

    @Test
    fun `失败时短句与原文分开且原文可追溯`() = runBlocking {
        val detail = "模型请求失败（HTTP 401）：API Key 可能不对或没有权限\n原始返回体很长很长"
        val source = ScriptedSource(listOf({ failed(detail) }))

        val events = AssistantAgentLoop(source, ToolLog().registry())
            .run("你好", emptyList())
            .toList()

        val error = events.filterIsInstance<AgentEvent.Error>().single()
        assertEquals(detail, error.detail)
        assertTrue("卡片正文只能是一行", !error.message.contains("\n"))
        assertTrue(error.message.contains("HTTP 401"))
    }
}
