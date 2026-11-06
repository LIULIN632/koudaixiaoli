package com.pocket.xiaoli.desktop.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.core.agent.AgentMessage
import com.pocket.xiaoli.core.agent.AgentRole
import com.pocket.xiaoli.core.agent.AgentToolCall
import com.pocket.xiaoli.core.agent.AgentToolSpec
import com.pocket.xiaoli.core.agent.AgentWire
import com.pocket.xiaoli.core.agent.SseLineParser
import com.pocket.xiaoli.core.agent.SseLineResult
import com.pocket.xiaoli.core.agent.ToolCallAccumulator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.3,
) {
    /** 三项齐全才算"配好了"（与移动端 `isConfigured` 同判据）。 */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    val endpoint: String get() = baseUrl.trim().trimEnd('/') + "/chat/completions"
}

/** 一次对话里产生的事件；UI 按顺序消费。 */
sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class Reasoning(val text: String) : ChatEvent
    data class ToolStart(val name: String, val label: String) : ChatEvent
    data class ToolDone(val name: String, val summary: String) : ChatEvent
    data class Failed(val message: String) : ChatEvent
}

/** 工具定义（OpenAI 兼容的函数调用格式）。 */
data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    /** 界面上显示的中文动作名，例如「查校园知识库」 */
    val label: String,
    val execute: (JsonObject) -> String,
)

/**
 * DeepSeek / OpenAI 兼容的流式对话客户端。
 *
 * ## 为什么自己解析 SSE
 *
 * 移动端 `LlmHttpClient` 解析的就是 `data:` 行的 delta；这里保持同一套协议理解，
 * 于是"桌面端能跑通"同时意味着"和移动端发的是同一种请求"。
 * 关键点三条：`choices[0].delta.content` 是正文字段、`reasoning_content` 是思维链、
 * `tool_calls` 是**按 index 分片**到达的（id/name 只在第一片，arguments 要拼）。
 */
class LlmClient(
    private val config: LlmConfig,
    private val tools: List<ToolSpec> = emptyList(),
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** 既有上下文（role/content 对）→ 共享协议模型；role 认不出来的按 user 处理。 */
    private fun historyToMessages(history: List<Pair<String, String>>): List<AgentMessage> =
        history.map { (role, content) ->
            AgentMessage(
                role = when (role.lowercase()) {
                    "system" -> AgentRole.SYSTEM
                    "assistant" -> AgentRole.ASSISTANT
                    "tool" -> AgentRole.TOOL
                    else -> AgentRole.USER
                },
                content = content,
            )
        }

    /** 工具清单只拼一次（每个 chat 调用一份），形状由共享层决定。 */
    private val toolsJson: String? by lazy {
        if (tools.isEmpty()) null
        else AgentWire.toolsJson(tools.map { AgentToolSpec(it.name, it.description, it.parameters) })
    }

    /**
     * 请求体走 `:core` 的 AgentWire —— 与 Android 同一份序列化。
     *
     * 之前这里是手工拼 JSON，而且把 tool 消息**先 toString 再塞进 content**，
     * 结果 `tool` 行没有顶层 `tool_call_id`、`assistant` 行的 `tool_calls` 也不在顶层，
     * 真机上一旦模型真的调工具就会 400 或被当成普通文本。共享层把这形状钉死了。
     */
    private fun buildRequestBody(messages: List<AgentMessage>): String =
        AgentWire.buildRequestBody(
            model = config.model,
            temperature = config.temperature,
            messages = messages,
            toolsJson = toolsJson,
        ).toString()

    /**
     * 发一轮请求，最多四轮工具调用。
     *
     * @param history 形如 ("user", "…") / ("assistant", "…") 的既有上下文
     * @return 最终回答（含思维链）。工具调用过程中的状态通过 [onEvent] 通知界面。
     */
    fun chat(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        onEvent: (ChatEvent) -> Unit,
    ): Pair<String, String> {
        if (!config.isConfigured) {
            onEvent(ChatEvent.Failed("还没配置 API：请在「我的 → 鲤鱼 AI」里填接口地址、Key 和模型名。"))
            return "" to ""
        }
        val wire = historyToMessages(history).toMutableList()
        var finalContent = StringBuilder()
        var finalReasoning = StringBuilder()

        repeat(MAX_ROUNDS) { round ->
            if (round > 0) {
                // 后续轮次已经在消息里塞了 tool 结果，继续请求
            }
            val messages = AgentWire.withSystem(wire.toList(), systemPrompt)
            val request = Request.Builder()
                .url(config.endpoint)
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", "text/event-stream")
                .post(buildRequestBody(messages).toRequestBody(jsonMedia))
                .build()

            val content = StringBuilder()
            val reasoning = StringBuilder()
            val toolAcc = ToolCallAccumulator()
            var failure: String? = null

            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        failure = "接口返回 ${response.code}：${body.take(300)}"
                        return@use
                    }
                    val source = response.body?.source() ?: run {
                        failure = "接口没有返回内容"
                        return@use
                    }
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when (val parsed = SseLineParser.parse(line)) {
                            SseLineResult.Ignore -> continue
                            SseLineResult.Done -> break
                            is SseLineResult.Delta -> {
                                toolAcc.acceptAll(parsed.toolCallFragments)
                                if (parsed.reasoning.isNotEmpty()) {
                                    reasoning.append(parsed.reasoning)
                                    onEvent(ChatEvent.Reasoning(parsed.reasoning))
                                }
                                if (parsed.text.isNotEmpty()) {
                                    content.append(parsed.text)
                                    onEvent(ChatEvent.Delta(parsed.text))
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                failure = "网络请求失败：${t.message ?: t.javaClass.simpleName}"
            }

            if (failure != null) {
                onEvent(ChatEvent.Failed(failure!!))
                return content.toString() to reasoning.toString()
            }

            finalContent = content
            finalReasoning = reasoning

            val validCalls = toolAcc.build()
            if (validCalls.isEmpty()) {
                return content.toString() to reasoning.toString()
            }

            // 把这一轮 assistant 的 tool_calls 原样回传，再逐条塞 tool 结果。
            // 这里只需要造模型对象，序列化由 AgentWire 负责（顶层 tool_calls / tool_call_id）。
            wire.add(
                AgentMessage(
                    role = AgentRole.ASSISTANT,
                    content = content.toString(),
                    toolCalls = validCalls.map {
                        AgentToolCall(id = it.id, name = it.name, argumentsJson = it.argumentsJson)
                    },
                ),
            )

            validCalls.forEach { call ->
                val tool = tools.firstOrNull { it.name == call.name }
                onEvent(ChatEvent.ToolStart(call.name, tool?.label ?: call.name))
                val args = runCatching { JsonParser.parseString(call.argumentsJson).asJsonObject }
                    .getOrDefault(JsonObject())
                val output = runCatching { tool?.execute?.invoke(args) ?: "没有这个工具：${call.name}" }
                    .getOrElse { "工具执行失败：${it.message}" }
                onEvent(ChatEvent.ToolDone(call.name, summarizeToolResult(output)))
                wire.add(AgentMessage(role = AgentRole.TOOL, content = output, toolCallId = call.id))
            }
        }

        onEvent(ChatEvent.Failed("工具调用超过 $MAX_ROUNDS 轮，先停在这里。"))
        return finalContent.toString() to finalReasoning.toString()
    }

    companion object {
        private const val MAX_ROUNDS = 4

        private fun summarizeToolResult(output: String): String {
            val firstLine = output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            return if (firstLine.length > 40) firstLine.take(40) + "…" else firstLine
        }
    }
}
