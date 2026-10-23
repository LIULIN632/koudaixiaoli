package com.pocket.xiaoli.data.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSE 流式解析的单测。
 *
 * 为什么这些测试重要：流式解析是整个 Agent 里**最难靠人眼验证**的一环 ——
 * 真机调试只能看到"结果对不对"，而字段分散在多个分片、服务商插入心跳帧、
 * 最后一个 chunk 只带 finish_reason 这些情况，只能靠构造帧来覆盖。
 */
class AssistantSseParserTest {

    // ── 基本行为 ──

    @Test
    fun `空行与注释行被忽略`() {
        assertTrue(SseLineParser.parse("") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse(": heartbeat") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("event: message") is SseLineResult.Ignore)
    }

    @Test
    fun `DONE 标记被识别为流结束`() {
        assertTrue(SseLineParser.parse("data: [DONE]") is SseLineResult.Done)
        // 前后空白不应影响判定（真实服务商偶尔带空格）
        assertTrue(SseLineParser.parse("data:  [DONE] ") is SseLineResult.Done)
    }

    @Test
    fun `data 为空的帧被忽略`() {
        assertTrue(SseLineParser.parse("data:") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("data:   ") is SseLineResult.Ignore)
    }

    @Test
    fun `正文增量被正确取出`() {
        val result = SseLineParser.parse(chunk(content = "今天"))
        assertTrue(result is SseLineResult.Delta)
        assertEquals("今天", (result as SseLineResult.Delta).text)
    }

    // ── 容错：畸形数据绝不能中断整个回答 ──

    @Test
    fun `非法 JSON 被忽略而不抛异常`() {
        assertTrue(SseLineParser.parse("data: {not json") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("data: <html>error</html>") is SseLineResult.Ignore)
    }

    @Test
    fun `缺少 choices 的帧被忽略`() {
        assertTrue(SseLineParser.parse("""data: {"id":"x","model":"m"}""") is SseLineResult.Ignore)
        assertTrue(SseLineParser.parse("""data: {"choices":[]}""") is SseLineResult.Ignore)
    }

    @Test
    fun `choices 里不是对象时被忽略`() {
        assertTrue(SseLineParser.parse("""data: {"choices":[123]}""") is SseLineResult.Ignore)
    }

    @Test
    fun `delta 缺失但带 finish_reason 时仍返回截断信息`() {
        // 部分服务商最后一个 chunk 只有 finish_reason，没有 delta。
        // 这种帧不能被当成"无内容"直接丢，否则截断提示会丢。
        val result = SseLineParser.parse(
            """data: {"choices":[{"finish_reason":"length"}]}"""
        )
        assertTrue(result is SseLineResult.Delta)
        assertTrue((result as SseLineResult.Delta).truncated)
        assertEquals("", result.text)
    }

    @Test
    fun `finish_reason 为 stop 时不算截断`() {
        val result = SseLineParser.parse(
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}"""
        )
        assertTrue(result is SseLineResult.Delta)
        assertFalse((result as SseLineResult.Delta).truncated)
    }

    // ── tool_calls 分片归并（最容易写错的一处）──

    @Test
    fun `工具调用分片按 index 归并`() {
        // 真实形态：首帧带 id + name + arguments 起始片段，
        // 后续帧只带 arguments 的续片，且 index 保持不变。
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "call_1", toolName = "get_today_courses", toolArgs = "")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = "{\"a\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = ":1}")))

        val calls = acc.build()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("get_today_courses", calls[0].name)
        // 关键：参数碎片必须被拼成完整 JSON
        assertEquals("""{"a":1}""", calls[0].argumentsJson)
    }

    @Test
    fun `多个工具调用按各自 index 独立归并`() {
        val acc = ToolCallAccumulator()
        // 交错到达：index 0 和 index 1 的片段穿插，不能串台
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "c0", toolName = "get_grades", toolArgs = "{\"course\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 1, toolId = "c1", toolName = "find_classroom", toolArgs = "{\"room\"")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = ":\"高数\"}")))
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 1, toolArgs = ":\"A101\"}")))

        val calls = acc.build()
        assertEquals(2, calls.size)
        assertEquals("get_grades", calls[0].name)
        assertEquals("""{"course":"高数"}""", calls[0].argumentsJson)
        assertEquals("find_classroom", calls[1].name)
        assertEquals("""{"room":"A101"}""", calls[1].argumentsJson)
    }

    @Test
    fun `缺失 id 时生成稳定 id 以保证请求回应能配对`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 3, toolName = "get_todos", toolArgs = "{}")))
        val calls = acc.build()
        assertEquals(1, calls.size)
        // 配对靠的是 id 一致，所以必须是可预期的确定值，不能是随机串
        assertEquals("call_3", calls[0].id)
    }

    @Test
    fun `缺失参数时分片归并为空对象`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolId = "c", toolName = "get_today_courses")))
        assertEquals("{}", acc.build()[0].argumentsJson)
    }

    @Test
    fun `没有函数名的分片被丢弃`() {
        // 协议上不可能调用一个无名工具；这种残帧不该产出 ToolCall，
        // 否则会向下游生成一个 tool_call_id 无法配对的请求
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolCallIndex = 0, toolArgs = "{}")))
        assertTrue(acc.build().isEmpty())
    }

    @Test
    fun `无 index 的分片按到达顺序兜底`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(fragmentsOf(chunk(toolId = "a", toolName = "get_grades", toolArgs = "{}")))
        acc.acceptAll(fragmentsOf(chunk(toolId = "b", toolName = "get_todos", toolArgs = "{}")))
        val calls = acc.build()
        assertEquals(2, calls.size)
        assertEquals("get_grades", calls[0].name)
        assertEquals("get_todos", calls[1].name)
    }

    @Test
    fun `空分片列表不产生调用`() {
        val acc = ToolCallAccumulator()
        acc.acceptAll(emptyList())
        assertTrue(acc.isEmpty)
        assertTrue(acc.build().isEmpty())
    }

    // ── 构造测试帧的工具 ──

    // ── 思考链（非标准字段，必须容错） ──

    @Test
    fun `reasoning_content 被取出且不混进正文`() {
        val result = SseLineParser.parse(chunk(reasoning = "用户想知道今天的课")) as SseLineResult.Delta

        assertEquals("用户想知道今天的课", result.reasoning)
        assertEquals("思考链绝不能混进正文，否则会被当成回答落库", "", result.text)
    }

    @Test
    fun `思考链与正文同时到达时各归各位`() {
        val result = SseLineParser.parse(chunk(content = "今天有三节课", reasoning = "先查课表")) as SseLineResult.Delta

        assertEquals("今天有三节课", result.text)
        assertEquals("先查课表", result.reasoning)
    }

    @Test
    fun `兼容 reasoning 与 thinking 两个别名`() {
        // 这不是标准字段：DeepSeek 用 reasoning_content，别的兼容实现用别的名字。
        // 换服务商时不该因此丢掉功能。
        assertEquals(
            "换个名字也要认",
            "想一下",
            (SseLineParser.parse(chunk(reasoning = "想一下", reasoningKey = "reasoning")) as SseLineResult.Delta).reasoning,
        )
        assertEquals(
            "thinking 也要认",
            "想一下",
            (SseLineParser.parse(chunk(reasoning = "想一下", reasoningKey = "thinking")) as SseLineResult.Delta).reasoning,
        )
    }

    @Test
    fun `服务商不回思考链时字段为空 而不是报错`() {
        val result = SseLineParser.parse(chunk(content = "直接回答")) as SseLineResult.Delta

        assertEquals("", result.reasoning)
    }

    @Test
    fun `思考链为空串时不产生事件（避免上游白推一次界面更新）`() {
        val result = SseLineParser.parse(chunk(content = "答", reasoning = "")) as SseLineResult.Delta

        assertEquals("", result.reasoning)
    }

    /** 造一个 `data: {...}` 行。字段按需填充，未填的省略。 */
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
            val arr = com.google.gson.JsonArray()
            arr.add(call)
            delta.add("tool_calls", arr)
        }

        val choice = JsonObject().apply { add("delta", delta) }
        val choices = com.google.gson.JsonArray().apply { add(choice) }
        val root = JsonObject().apply {
            addProperty("id", "chatcmpl-test")
            add("choices", choices)
        }
        return "data: $root"
    }

    /** 从一整行里取出 tool_calls 分片，模拟客户端从 [SseLineResult.Delta] 拿到的内容。 */
    private fun fragmentsOf(line: String): List<JsonObject> {
        val result = SseLineParser.parse(line)
        assertTrue("测试帧应当能被解析为 Delta：$line", result is SseLineResult.Delta)
        return (result as SseLineResult.Delta).toolCallFragments
    }

    @Test
    fun `解析器与累积器可以串起来跑完一整段真实流`() {
        // 端到端：模拟一次"先输出正文、再请求调用工具"的完整流
        val lines = listOf(
            chunk(content = "让我查一下"),
            chunk(content = "今天的课。"),
            chunk(toolCallIndex = 0, toolId = "call_abc", toolName = "get_today_courses", toolArgs = "{"),
            chunk(toolCallIndex = 0, toolArgs = "}"),
            """data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]",
        )

        val text = StringBuilder()
        val acc = ToolCallAccumulator()
        var done = false

        for (line in lines) {
            when (val parsed = SseLineParser.parse(line)) {
                is SseLineResult.Ignore -> Unit
                is SseLineResult.Done -> { done = true; break }
                is SseLineResult.Delta -> {
                    text.append(parsed.text)
                    acc.acceptAll(parsed.toolCallFragments)
                }
            }
        }

        assertTrue("应当遇到 DONE 标记", done)
        assertEquals("让我查一下今天的课。", text.toString())
        val calls = acc.build()
        assertEquals(1, calls.size)
        assertEquals("get_today_courses", calls[0].name)
        assertEquals("{}", calls[0].argumentsJson)
    }
}
