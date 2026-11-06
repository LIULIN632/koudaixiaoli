package com.pocket.xiaoli.core.agent

import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 协议报文的形状测试。
 *
 * 这些断言守的是一个只能靠肉眼发现、真机才暴露的 bug：桌面端曾把整条消息
 * `toString()` 后塞进 `content`，于是 `tool` 行没有顶层 `tool_call_id`、
 * `assistant` 行的 `tool_calls` 也不在顶层。所以这里**直接断言 JSON 结构**，
 * 而不是断言「能序列化」。
 */
class AgentWireTest {

    private fun parse(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun `assistant 的 tool_calls 在顶层且 arguments 是字符串`() {
        val json = AgentWire.messageToJson(
            AgentMessage(
                role = AgentRole.ASSISTANT,
                content = "",
                toolCalls = listOf(AgentToolCall("call_1", "today_courses", """{"day":"2026-11-02"}""")),
            ),
        )
        assertEquals("assistant", json.get("role").asString)
        assertTrue(json.has("tool_calls"), "顶层必须有 tool_calls")
        val call = json.getAsJsonArray("tool_calls")[0].asJsonObject
        assertEquals("function", call.get("type").asString)
        assertEquals("call_1", call.get("id").asString)
        val fn = call.getAsJsonObject("function")
        assertEquals("today_courses", fn.get("name").asString)
        assertTrue(fn.get("arguments").asJsonPrimitive.isString, "arguments 必须是字符串，不是对象")
        assertEquals("""{"day":"2026-11-02"}""", fn.get("arguments").asString)
    }

    @Test
    fun `tool 消息带顶层 tool_call_id`() {
        val json = AgentMessage(role = AgentRole.TOOL, content = "3 门课", toolCallId = "call_1")
        val wire = AgentWire.messageToJson(json)
        assertEquals("tool", wire.get("role").asString)
        assertEquals("call_1", wire.get("tool_call_id").asString)
        assertEquals("3 门课", wire.get("content").asString)
        assertTrue(!wire.has("tool_calls"))
    }

    @Test
    fun `assistant 没有 tool_calls 时不出现这个字段`() {
        val wire = AgentWire.messageToJson(AgentMessage(AgentRole.ASSISTANT, "你好"))
        assertTrue(!wire.has("tool_calls"))
        assertEquals("你好", wire.get("content").asString)
    }

    @Test
    fun `withSystem 只在缺 system 时补`() {
        val user = AgentMessage(AgentRole.USER, "今天有课吗")
        val once = AgentWire.withSystem(listOf(user), "你是鲤鱼")
        assertEquals(2, once.size)
        assertEquals(AgentRole.SYSTEM, once[0].role)
        assertEquals("你是鲤鱼", once[0].content)

        val twice = AgentWire.withSystem(once, "换一句")
        assertEquals(2, twice.size, "已经有 system 就不该再加一条")
        assertEquals("你是鲤鱼", twice[0].content)
    }

    @Test
    fun `请求体字段齐全且工具数组按 schema 拼`() {
        val specs = listOf(
            AgentToolSpec(
                name = "today_courses",
                description = "查今天有什么课",
                parameters = parse("""{"type":"object","properties":{},"required":[]}"""),
            ),
        )
        val body = AgentWire.buildRequestBody(
            model = "deepseek-chat",
            temperature = 0.3,
            messages = listOf(AgentMessage(AgentRole.USER, "今天有课吗")),
            toolsJson = AgentWire.toolsJson(specs),
        )
        assertEquals("deepseek-chat", body.get("model").asString)
        assertTrue(body.get("stream").asBoolean)
        assertEquals(0.3, body.get("temperature").asDouble, 0.0001)
        assertEquals("auto", body.get("tool_choice").asString)
        val tool = body.getAsJsonArray("tools")[0].asJsonObject
        assertEquals("function", tool.get("type").asString)
        assertEquals("today_courses", tool.getAsJsonObject("function").get("name").asString)
        assertTrue(tool.getAsJsonObject("function").has("parameters"))
    }

    @Test
    fun `没有工具时不出现 tools 与 tool_choice`() {
        val body = AgentWire.buildRequestBody("m", 0.3, listOf(AgentMessage(AgentRole.USER, "hi")), toolsJson = null)
        assertTrue(!body.has("tools"))
        assertTrue(!body.has("tool_choice"))
    }

    @Test
    fun `坏的工具 JSON 当作没有工具，而不是塞进请求`() {
        assertNull(AgentWire.parsedTools("not json"))
        assertNull(AgentWire.parsedTools("[]"))
        assertNull(AgentWire.parsedTools(""))
        val body = AgentWire.buildRequestBody("m", 0.3, emptyList(), toolsJson = "{\"oops\":1}")
        assertTrue(!body.has("tools"))
    }

    @Test
    fun `多轮消息顺序与角色原样保留`() {
        val messages = listOf(
            AgentMessage(AgentRole.SYSTEM, "你是鲤鱼"),
            AgentMessage(AgentRole.USER, "今天有课吗"),
            AgentMessage(AgentRole.ASSISTANT, "", listOf(AgentToolCall("c1", "today_courses", "{}"))),
            AgentMessage(AgentRole.TOOL, "今天 2 门课", toolCallId = "c1"),
            AgentMessage(AgentRole.ASSISTANT, "今天有两门课"),
        )
        val array = AgentWire.messagesJson(messages)
        assertEquals(5, array.size())
        assertEquals(
            listOf("system", "user", "assistant", "tool", "assistant"),
            array.map { it.asJsonObject.get("role").asString },
        )
    }
}
