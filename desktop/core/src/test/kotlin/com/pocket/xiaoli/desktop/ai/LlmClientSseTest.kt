package com.pocket.xiaoli.desktop.ai

import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * 桌面 LLM 客户端 × 共享 SSE 解析的端到端单测。
 *
 * 为什么值得单独写：SSE 分片归并（尤其 tool_call 的 name/arguments 分片）是最难靠界面
 * 验证的一环。这里用假服务端把「正文 + 思考链 + 工具调用 → 工具结果回填 → 第二轮回答」
 * 整条链路跑一遍，证明桌面消费的是 `:core` 里那一份解析，而不是又写了一套。
 */
class LlmClientSseTest {

    @Test
    fun `流式正文 思考链与工具调用走同一份解析`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"choices":[{"delta":{"reasoning_content":"先查课表"}}]}
                        data: {"choices":[{"delta":{"content":"稍等"}}]}
                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"today","arguments":"{\"day\":"}}]}}]}
                        data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"tomorrow\"}"}}]}}]}
                        data: [DONE]
                        """.trimIndent(),
                    ),
            )
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(
                        """
                        data: {"choices":[{"delta":{"content":"明天两节课"}}]}
                        data: [DONE]
                        """.trimIndent(),
                    ),
            )

            val events = mutableListOf<ChatEvent>()
            val receivedArgs = AtomicReference<JsonObject>()
            val tools = listOf(
                ToolSpec(
                    name = "today",
                    description = "查课表",
                    parameters = JsonObject(),
                    label = "查课表",
                    execute = { args ->
                        receivedArgs.set(args)
                        "两节课"
                    },
                ),
            )
            val client = LlmClient(
                LlmConfig(
                    baseUrl = server.url("/v1").toString().trimEnd('/'),
                    apiKey = "test-key",
                    model = "test-model",
                ),
                tools,
            )

            val (content, _) = client.chat(
                systemPrompt = "你是测试助手",
                history = listOf("user" to "明天有什么课"),
                onEvent = events::add,
            )

            // 返回值是「最后一轮」的正文；前面几轮的内容已经通过 ChatEvent.Delta 流式发出去
            assertEquals("明天两节课", content)
            assertTrue(events.any { it is ChatEvent.Reasoning && it.text == "先查课表" })
            assertTrue(events.any { it is ChatEvent.ToolStart && it.name == "today" })
            // 工具分片跨帧拼接后必须能被解析成合法 JSON 再执行
            assertEquals("tomorrow", receivedArgs.get().get("day").asString)

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("/v1/chat/completions", first.path)

            // 第一轮：工具清单与系统提示必须在**顶层**字段里
            val firstBody = com.google.gson.JsonParser.parseString(first.body.readUtf8()).asJsonObject
            assertEquals("test-model", firstBody.get("model").asString)
            val firstMessages = firstBody.getAsJsonArray("messages")
            assertEquals("system", firstMessages[0].asJsonObject.get("role").asString)
            assertEquals("你是测试助手", firstMessages[0].asJsonObject.get("content").asString)
            val toolsArray = firstBody.getAsJsonArray("tools")
            assertEquals(1, toolsArray.size())
            assertEquals("today", toolsArray[0].asJsonObject.getAsJsonObject("function").get("name").asString)
            assertEquals("auto", firstBody.get("tool_choice").asString)

            // 第二轮：assistant 的 tool_calls 与 tool 的 tool_call_id 都必须在**顶层**。
            // 这两条是 2026-11-02 修掉的真 bug：之前整条消息被 toString 塞进 content。
            val secondBody = com.google.gson.JsonParser.parseString(second.body.readUtf8()).asJsonObject
            val secondMessages = secondBody.getAsJsonArray("messages")
            val assistantWithCalls = secondMessages
                .map { it.asJsonObject }
                .first { it.get("role").asString == "assistant" && it.has("tool_calls") }
            val call = assistantWithCalls.getAsJsonArray("tool_calls")[0].asJsonObject
            assertEquals("call_1", call.get("id").asString)
            assertEquals("today", call.getAsJsonObject("function").get("name").asString)
            assertTrue(call.getAsJsonObject("function").get("arguments").asJsonPrimitive.isString)

            val toolMessage = secondMessages.map { it.asJsonObject }.first { it.get("role").asString == "tool" }
            assertEquals("call_1", toolMessage.get("tool_call_id").asString)
            assertEquals("两节课", toolMessage.get("content").asString)
        } finally {
            server.shutdown()
        }
    }
}
