package com.pocket.xiaoli.data.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.pocket.xiaoli.util.NetworkErrorText
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** 对话角色。`TOOL` 承载工具执行结果，回灌给模型。 */
enum class ChatRole { SYSTEM, USER, ASSISTANT, TOOL }

/**
 * 一条消息。
 *
 * 刻意用扁平结构而不是 sealed class：
 * - 落库是一行一条，扁平结构与表结构一一对应，无需类型判别字段
 * - assistant 消息同时可能带 [content] 与 [toolCalls]（模型边说话边调工具），
 *   分成两个子类反而要处理"两个都有"的第三种情况
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String = "",
    /** 仅 ASSISTANT：本消息请求调用的工具。 */
    val toolCalls: List<ToolCall> = emptyList(),
    /** 仅 TOOL：回应的哪个请求，关联 [ToolCall.id]。 */
    val toolCallId: String? = null,
    /** 仅 TOOL：工具名，便于落库后还原与排错。 */
    val toolName: String? = null,
)

/** 模型请求的一次工具调用。[argumentsJson] 是模型给的 JSON 字符串，不保证合法，调用前必须容错解析。 */
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * assistant 行上的「失败轮」标记，存在 `tool_name` 字段里。
 *
 * 为什么借这个字段而不是加一列：加列要升 Room schema 版本 + 写迁移，而"这轮失败了"
 * 是**纯展示关心的事**（界面据此渲染错误卡片）。assistant 行上的 `tool_name`
 * 本来恒为空（它只对 tool 行有意义），拿来放一个不可能与真实工具名冲突的哨兵值，
 * 零成本地让失败态跟着历史一起落库。
 *
 * ⚠️ 它是**内部标记**，任何发给模型的请求体里都不能出现 —— 出口在
 * [withoutErrorMark]。
 */
const val ERROR_MARKER_TOOL_NAME = "__error__"

/** 摘掉上面的内部标记，得到可以真正发给模型的 [ChatMessage]。 */
internal fun ChatMessage.withoutErrorMark(): ChatMessage =
    if (role == ChatRole.ASSISTANT && toolName == ERROR_MARKER_TOOL_NAME) copy(toolName = null) else this

/** 流式过程中的增量事件。 */
sealed interface LlmStreamEvent {
    /** 正文增量，直接追加到界面即可。 */
    data class TextDelta(val text: String) : LlmStreamEvent

    /**
     * 思考链增量。
     *
     * 与 [TextDelta] 分开而不是拼进正文：它**不是回答的一部分**，既不该被当成答案
     * 存进历史，也不该在界面上混在正文里（那正是"思考链想看看、不想看别占地方"要解决的）。
     * 服务商不回这个字段时，整条链路自然收不到任何事件。
     */
    data class ReasoningDelta(val text: String) : LlmStreamEvent

    /**
     * 流结束，携带拼装完整的助手消息（含全部 tool_calls）。
     *
     * [truncated] 表示服务端报告这条回答被**长度**截断（`finish_reason == "length"`）。
     * 这里只报告事实、不拼任何提示文本：要不要续写由 [AssistantAgentLoop] 决定。
     * 客户端往正文里塞「被截断」字样会在落库后污染历史，模型下一轮还会模仿那句话。
     */
    data class Completed(
        val message: ChatMessage,
        val truncated: Boolean = false,
    ) : LlmStreamEvent

    /** 调用失败，[message] 已是可直接展示的中文提示。 */
    data class Failed(val message: String) : LlmStreamEvent
}

/** 请求配置。[toolsJson] 为 null 或空表示本次不提供工具。 */
data class LlmRequest(
    val messages: List<ChatMessage>,
    val toolsJson: String? = null,
    val temperature: Double = 0.3,
)

/**
 * OpenAI 兼容的流式聊天客户端。
 *
 * 只做协议层：拼请求、解析 SSE、拼装 tool_calls 增量。**不含任何 Agent 逻辑**
 * （何时调工具、调几轮、上下文怎么裁，都在 [AssistantAgentLoop]）。
 *
 * 用法上刻意不用 `eventSource` 之类的封装：SSE 的格式极简，
 * 手写解析能完全控制「畸形数据不崩」这条硬要求。
 *
 * 同样刻意**不用 Gson 反射反序列化响应**：全走 [JsonParser] 手动取值，
 * 这样 R8 混淆不会因为字段名被改写而破坏解析（只有请求体方向的
 * [JsonObject] 组装，那本来就不依赖反射）。
 */
class AssistantLlmClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 流式响应：首字节可能等很久（模型排队），但帧间隔通常很短。
            // callTimeout 设 0（不限总时长）避免长回答被腰斩，靠 readTimeout 兜住真死连接。
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * 发起一次流式请求。
     *
     * 返回的 Flow 是冷流：每次收集发起一次真实请求；取消收集会 abort 底层 call。
     * 异常不外抛 —— 一律转成 [LlmStreamEvent.Failed]，让上层决定是提示用户还是重试。
     */
    fun stream(config: AssistantLlmConfig, request: LlmRequest): Flow<LlmStreamEvent> = callbackFlow {
        if (!config.isConfigured) {
            trySend(LlmStreamEvent.Failed("还没有配置 AI 模型，请到「我的 → 助手设置」填好接口地址、模型名和 API Key。"))
            close()
            return@callbackFlow
        }

        val body = buildRequestBody(config, request).toString()
        val httpRequest = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    close()
                    return
                }
                trySend(LlmStreamEvent.Failed(describeNetworkError(e)))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val detail = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                        trySend(LlmStreamEvent.Failed(describeHttpError(resp.code, detail)))
                        close()
                        return
                    }
                    val source = resp.body?.source()
                    if (source == null) {
                        trySend(LlmStreamEvent.Failed("模型返回了空响应。"))
                        close()
                        return
                    }

                    // 增量拼装器：文本累积 + 按 index 累积各 tool_call 的分片
                    val textBuilder = StringBuilder()
                    val toolAcc = ToolCallAccumulator()
                    var truncated = false

                    try {
                        loop@ while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when (val parsed = SseLineParser.parse(line)) {
                                is SseLineResult.Ignore -> continue@loop
                                is SseLineResult.Done -> break@loop
                                is SseLineResult.Delta -> {
                                    toolAcc.acceptAll(parsed.toolCallFragments)
                                    // 思考链：只往外发，**不进 textBuilder** ——
                                    // 它不算回答正文，混进去会污染落库的历史与下一轮上下文
                                    if (parsed.reasoning.isNotEmpty()) {
                                        trySend(LlmStreamEvent.ReasoningDelta(parsed.reasoning))
                                    }
                                    if (parsed.text.isNotEmpty()) {
                                        textBuilder.append(parsed.text)
                                        trySend(LlmStreamEvent.TextDelta(parsed.text))
                                    }
                                    if (parsed.truncated) {
                                        // 只记事实，不往正文里拼提示（见 Completed.truncated）
                                        truncated = true
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!call.isCanceled()) {
                            // 已经收到部分内容时，不要把整轮判失败：把已有的给出去
                            val partial = textBuilder.toString()
                            if (partial.isNotBlank()) {
                                trySend(
                                    LlmStreamEvent.Completed(
                                        ChatMessage(ChatRole.ASSISTANT, content = partial, toolCalls = toolAcc.build())
                                    )
                                )                            } else {
                                trySend(LlmStreamEvent.Failed(describeNetworkError(e)))
                            }
                        }
                        close()
                        return
                    }

                    trySend(
                        LlmStreamEvent.Completed(
                            ChatMessage(
                                role = ChatRole.ASSISTANT,
                                content = textBuilder.toString(),
                                toolCalls = toolAcc.build(),
                            ),
                            truncated = truncated,
                        )
                    )
                    close()
                }
            }
        })

        awaitClose { runCatching { call.cancel() } }
    }

    /** 按 OpenAI 协议拼请求体。工具用 JSON 字符串原样挂上，避免二次解析。 */
    private fun buildRequestBody(config: AssistantLlmConfig, request: LlmRequest): JsonObject {
        val root = JsonObject()
        root.addProperty("model", config.model)
        root.addProperty("stream", true)
        root.addProperty("temperature", request.temperature)

        val messages = JsonArray()
        val hasSystem = request.messages.any { it.role == ChatRole.SYSTEM }
        if (!hasSystem) {
            messages.add(systemMessage(config.effectiveSystemPrompt()))
        }
        request.messages.forEach { messages.add(it.toJson()) }
        root.add("messages", messages)

        val tools = request.toolsJson
        if (!tools.isNullOrBlank()) {
            runCatching { JsonParser.parseString(tools).asJsonArray }.getOrNull()?.let {
                if (it.size() > 0) {
                    root.add("tools", it)
                    root.addProperty("tool_choice", "auto")
                }
            }
        }
        return root
    }

    private fun systemMessage(text: String): JsonObject = JsonObject().apply {
        addProperty("role", "system")
        addProperty("content", text)
    }

    private fun ChatMessage.toJson(): JsonObject = JsonObject().apply {
        addProperty("role", role.name.lowercase())
        when (role) {
            ChatRole.TOOL -> {
                addProperty("content", content)
                addProperty("tool_call_id", toolCallId.orEmpty())
            }
            else -> {
                // assistant 带 tool_calls 时，content 允许为空字符串
                addProperty("content", content)
                if (toolCalls.isNotEmpty()) {
                    val arr = JsonArray()
                    toolCalls.forEach { call ->
                        arr.add(JsonObject().apply {
                            addProperty("id", call.id)
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", call.name)
                                // arguments 必须是**字符串**（里面是 JSON），不是对象
                                addProperty("arguments", call.argumentsJson)
                            })
                        })
                    }
                    add("tool_calls", arr)
                }
            }
        }
    }

    private fun describeHttpError(code: Int, body: String): String {
        // 从错误体里捞服务商给的可读信息，捞不到再退回状态码
        val detail = runCatching {
            val obj = JsonParser.parseString(body).asJsonObject
            obj.getAsJsonObject("error")?.stringOrNull("message")
                ?: obj.stringOrNull("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }

        val hint = when (code) {
            401, 403 -> "API Key 可能不对或没有权限"
            404 -> "接口地址可能不对（应填到 /v1，不含 /chat/completions）"
            429 -> "请求太频繁或额度不足"
            in 500..599 -> "模型服务端异常，稍后再试"
            else -> null
        }
        return buildString {
            append("模型请求失败（HTTP ").append(code).append("）")
            hint?.let { append("：").append(it) }
            detail?.let { append("\n").append(it.take(300)) }
        }
    }

    private fun describeNetworkError(e: IOException): String =
        NetworkErrorText.networkFailure(label = "模型", e = e)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

// ── Gson 取值小工具（null / 类型不符一律返回 null，调用方不必层层判空）──

internal fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString

internal fun JsonArray.firstOrNullObject(): JsonObject? =
    if (size() > 0) get(0) as? JsonObject else null
