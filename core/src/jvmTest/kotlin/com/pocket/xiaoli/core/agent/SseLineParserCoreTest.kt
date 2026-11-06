package com.pocket.xiaoli.core.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 共享 SSE 解析的 JVM 单测。
 *
 * Android 侧原有的 `AssistantSseParserTest` 仍然保留（通过 typealias 测同一份代码），
 * 这里补一份跑在 `:core:jvmTest` 的用例，保证桌面端消费的也是这份实现。
 */
class SseLineParserCoreTest {

    @Test
    fun `空行 注释 非 data 行都被忽略`() {
        assertTrue(SseLineParser.parse("") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse(": heartbeat") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("event: message") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("data:") is SseLineResult.Ignore)
    }

    @Test
    fun `DONE 标记结束流`() {
        assertTrue(SseLineParser.parse("data: [DONE]") is SseLineResult.Done)
        assertTrue(SseLineParser.parse("data:  [DONE] ") is SseLineResult.Done)
    }

    @Test
    fun `正文与思考链各归各位`() {
        val result = SseLineParser.parse(
            chunk(content = "今天有三节课", reasoning = "先查课表"),
        ) as SseLineResult.Delta

        assertEquals("今天有三节课", result.text)
        assertEquals("先查课表", result.reasoning)
    }

    @Test
    fun `思考链兼容 reasoning 与 thinking 别名`() {
        for (key in listOf("reasoning_content", "reasoning", "thinking")) {
            val result = SseLineParser.parse(chunk(reasoning = "想一下", reasoningKey = key))
                as SseLineResult.Delta
            assertEquals("想一下", result.reasoning, "别名 $key 没被识别")
        }
    }

    @Test
    fun `畸形 JSON 不抛异常`() {
        assertTrue(SseLineParser.parse("data: {not json") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("""data: {"choices":[123]}""") is SseLineResult.Ignore)
    }

    @Test
    fun `只有 finish_reason 的帧仍然报告截断`() {
        val result = SseLineParser.parse(
            """data: {"choices":[{"finish_reason":"length"}]}""",
        ) as SseLineResult.Delta

        assertTrue(result.truncated)
        assertEquals("", result.text)
    }

    @Test
    fun `正常结束不算截断`() {
        val result = SseLineParser.parse(
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
        ) as SseLineResult.Delta
        assertFalse(result.truncated)
    }

    @Test
    fun `工具分片按 index 归并且参数拼接完整`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "call_1", toolName = "get_today_courses")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = "{\"a\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = ":1}")))

        val calls = acc.build()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("get_today_courses", calls[0].name)
        assertEquals("""{"a":1}""", calls[0].argumentsJson)
    }

    @Test
    fun `多个工具调用按各自 index 独立归并`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "c0", toolName = "get_grades", toolArgs = "{\"course\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 1, toolId = "c1", toolName = "find_classroom", toolArgs = "{\"room\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = ":\"高数\"}")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 1, toolArgs = ":\"A101\"}")))

        val calls = acc.build()
        assertEquals(listOf("get_grades", "find_classroom"), calls.map { it.name })
        assertEquals("""{"course":"高数"}""", calls[0].argumentsJson)
        assertEquals("""{"room":"A101"}""", calls[1].argumentsJson)
    }

    @Test
    fun `缺失 id 时生成稳定 id`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 3, toolName = "get_todos", toolArgs = "{}")))
        assertEquals("call_3", acc.build().single().id)
    }

    @Test
    fun `缺失参数时归并为空对象`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "c", toolName = "get_today_courses")))
        assertEquals("{}", acc.build().single().argumentsJson)
    }

    @Test
    fun `没有函数名的分片被丢弃`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = "{}")))
        assertTrue(acc.build().isEmpty())
    }

    private fun chunk(
        content: String? = null,
        reasoning: String? = null,
        reasoningKey: String = "reasoning_content",
        toolCallIndex: Int? = null,
        toolId: String? = null,
        toolName: String? = null,
        toolArgs: String? = null,
    ): String {
        val delta = JsonObject()
        content?.let { delta.addProperty("content", it) }
        reasoning?.let { delta.addProperty(reasoningKey, it) }

        if (toolCallIndex != null || toolName != null || toolArgs != null) {
            val call = JsonObject()
            toolCallIndex?.let { call.addProperty("index", it) }
            toolId?.let { call.addProperty("id", it) }
            if (toolName != null || toolArgs != null) {
                val fn = JsonObject()
                toolName?.let { fn.addProperty("name", it) }
                toolArgs?.let { fn.addProperty("arguments", it) }
                call.add("function", fn)
            }
            val arr = JsonArray().apply { add(call) }
            delta.add("tool_calls", arr)
        }

        val choice = JsonObject().apply { add("delta", delta) }
        val root = JsonObject().apply {
            addProperty("id", "chatcmpl-test")
            add("choices", JsonArray().apply { add(choice) })
        }
        return "data: $root"
    }

    private fun fragmentsOf(line: String): List<JsonObject> {
        val result = SseLineParser.parse(line)
        assertTrue(result is SseLineResult.Delta, "测试帧应当解析为 Delta：$line")
        return (result as SseLineResult.Delta).toolCallFragments
    }
}
