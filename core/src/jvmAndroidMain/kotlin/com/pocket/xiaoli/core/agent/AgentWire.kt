package com.pocket.xiaoli.core.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * OpenAI 兼容协议的**报文体**：消息模型 + 请求体拼装 + 工具清单。
 *
 * ## 为什么放在共享层
 *
 * 两端原来各写一份：Android 有 `ChatMessage` + 自己序列化，桌面端更省事——把整条
 * 消息**先 toString 成 JSON 字符串再塞进 `content`**，于是 `tool` 行没有顶层的
 * `tool_call_id`、`assistant` 行的 `tool_calls` 也没出现在顶层。这个错误只会在
 * 「模型真的调工具」时暴露，而两端都没有能用真 Key 跑的自动化测试。
 *
 * 所以这里把「消息长什么样」定成一处事实：**序列化只有一份，两端只负责把自己的
 * 业务对象映射成 [AgentMessage]**。
 *
 * ## 边界
 *
 * 只做 JSON 拼装，**不碰网络、不碰状态机**：什么时候发、发几轮、工具怎么执行仍在各自
 * 端的 Agent 循环里。也不做容错猜测——`argumentsJson` 原样透传（模型给什么就是什么，
 * 解析失败由调用方决定怎么兜底）。
 */
enum class AgentRole(val wire: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
}

/** 一次工具调用。[argumentsJson] 是模型给的 JSON **字符串**（协议要求字符串，不是对象）。 */
data class AgentToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

/**
 * 一条发给模型的消息。
 *
 * 扁平结构（不为 assistant/tool 各建子类）的理由与 Android 落库一致：
 * assistant 可以同时有正文和 tool_calls，拆子类反而要处理「两个都有」的第三种情况。
 */
data class AgentMessage(
    val role: AgentRole,
    val content: String = "",
    /** 仅 [AgentRole.ASSISTANT]：本轮要求调用的工具。 */
    val toolCalls: List<AgentToolCall> = emptyList(),
    /** 仅 [AgentRole.TOOL]：回应的是哪个请求，对应 [AgentToolCall.id]。 */
    val toolCallId: String? = null,
)

/** 一条工具定义。[parameters] 是 JSON Schema 对象。 */
data class AgentToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

object AgentWire {

    /** 把工具定义拼成 `tools` 数组（字符串形态，直接塞进请求体）。 */
    fun toolsJson(specs: List<AgentToolSpec>): String {
        val array = JsonArray()
        specs.forEach { spec ->
            array.add(
                JsonObject().apply {
                    addProperty("type", "function")
                    add(
                        "function",
                        JsonObject().apply {
                            addProperty("name", spec.name)
                            addProperty("description", spec.description)
                            add("parameters", spec.parameters)
                        },
                    )
                },
            )
        }
        return array.toString()
    }

    /**
     * 单条消息 → JSON。
     *
     * 三种形态按协议分开写，不做「有就加、没有就不加」的通用拼装 —— 那正是桌面端出错的写法：
     * `tool` 行缺 `tool_call_id` 时，服务端要么 400，要么把它当普通 user 消息读。
     */
    fun messageToJson(message: AgentMessage): JsonObject = JsonObject().apply {
        addProperty("role", message.role.wire)
        when (message.role) {
            AgentRole.TOOL -> {
                addProperty("tool_call_id", message.toolCallId.orEmpty())
                addProperty("content", message.content)
            }
            AgentRole.ASSISTANT -> {
                // 带 tool_calls 时 content 允许为空字符串（协议如此），但字段要在
                addProperty("content", message.content)
                if (message.toolCalls.isNotEmpty()) {
                    add("tool_calls", toolCallsJson(message.toolCalls))
                }
            }
            AgentRole.SYSTEM, AgentRole.USER -> addProperty("content", message.content)
        }
    }

    fun toolCallsJson(calls: List<AgentToolCall>): JsonArray {
        val array = JsonArray()
        calls.forEach { call ->
            array.add(
                JsonObject().apply {
                    addProperty("id", call.id)
                    addProperty("type", "function")
                    add(
                        "function",
                        JsonObject().apply {
                            addProperty("name", call.name)
                            // arguments 必须是**字符串**（里面是 JSON），不是对象
                            addProperty("arguments", call.argumentsJson)
                        },
                    )
                },
            )
        }
        return array
    }

    fun messagesJson(messages: List<AgentMessage>): JsonArray {
        val array = JsonArray()
        messages.forEach { array.add(messageToJson(it)) }
        return array
    }

    /** 没有 system 消息时补一条，有就原样返回（自动加系统提示的唯一入口）。 */
    fun withSystem(messages: List<AgentMessage>, systemPrompt: String): List<AgentMessage> =
        if (messages.any { it.role == AgentRole.SYSTEM }) messages
        else listOf(AgentMessage(role = AgentRole.SYSTEM, content = systemPrompt)) + messages

    /**
     * 拼一整个请求体。
     *
     * [toolsJson] 允许是 null / 空串 / 非法 JSON —— 后两者按「这次不给工具」处理，
     * 而不是把坏 JSON 塞进请求让服务端 400（工具清单来自注册表，注册表写错不该炸对话）。
     */
    fun buildRequestBody(
        model: String,
        temperature: Double,
        messages: List<AgentMessage>,
        toolsJson: String? = null,
        stream: Boolean = true,
    ): JsonObject {
        val root = JsonObject()
        root.addProperty("model", model)
        root.addProperty("stream", stream)
        root.addProperty("temperature", temperature)
        root.add("messages", messagesJson(messages))
        parsedTools(toolsJson)?.let { tools ->
            root.add("tools", tools)
            root.addProperty("tool_choice", "auto")
        }
        return root
    }

    /** 工具数组：空/非法/非数组一律视为「没有工具」。 */
    internal fun parsedTools(toolsJson: String?): JsonArray? {
        if (toolsJson.isNullOrBlank()) return null
        val array = runCatching { JsonParser.parseString(toolsJson).asJsonArray }.getOrNull() ?: return null
        return array.takeIf { it.size() > 0 }
    }
}
