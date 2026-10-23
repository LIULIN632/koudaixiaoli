package com.pocket.xiaoli.data.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * SSE 行的解析结果。
 *
 * 抽成独立类型而不是直接改外部状态，是为了让解析逻辑**可以脱离网络被单测覆盖** ——
 * 流式解析是最容易出错、又最难靠人眼验证的一环。
 */
internal sealed interface SseLineResult {
    /** 忽略（空行 / 注释 / 非 data 行 / 畸形 JSON）。 */
    data object Ignore : SseLineResult

    /** 流结束标记 `[DONE]`。 */
    data object Done : SseLineResult

    /** 增量内容：正文片段、思考链片段与/或工具调用分片。 */
    data class Delta(
        val text: String = "",
        /**
         * 思考链增量（`reasoning_content`）。
         *
         * ⚠️ 这是 **DeepSeek 系与少数兼容实现的私有字段**，不是 OpenAI 标准 ——
         * 别的服务商可能叫 `reasoning`、`thinking`，或者干脆不回。
         * 所以整条链路（解析 → 事件 → 界面）都必须按"有就显示、没有就不显示"来写，
         * **不能**把它当成稳定能力（比如不能拿它判断模型"有没有思考"）。
         */
        val reasoning: String = "",
        val toolCallFragments: List<JsonObject> = emptyList(),
        /** 为 true 表示服务端报告回答被长度截断。 */
        val truncated: Boolean = false,
    ) : SseLineResult
}

/**
 * OpenAI 兼容流式响应的行解析器。**纯逻辑、无 IO、无协程**，因此可单测。
 *
 * 设计要点：
 * - 畸形帧一律 [SseLineResult.Ignore]，绝不抛异常。真实服务商偶尔会插入
 *   心跳或非标准帧，为此中断整个回答是不可接受的。
 * - 不在这里拼装 tool_calls 分片，只把"本行出现的分片对象"交出去；
 *   归并交给 [ToolCallAccumulator]（同样可单测）。
 */
internal object SseLineParser {

    private const val DATA_PREFIX = "data:"
    private const val DONE_MARKER = "[DONE]"

    fun parse(line: String): SseLineResult {
        // SSE 规范：空行是事件分隔符；以 ':' 开头是注释（心跳常用）
        if (line.isEmpty() || line.startsWith(":")) return SseLineResult.Ignore
        if (!line.startsWith(DATA_PREFIX)) return SseLineResult.Ignore

        val payload = line.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty()) return SseLineResult.Ignore
        if (payload == DONE_MARKER) return SseLineResult.Done

        val chunk = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
            ?: return SseLineResult.Ignore

        val choice = chunk.getAsJsonArray("choices")?.firstOrNullObject()
            ?: return SseLineResult.Ignore

        val delta = choice.getAsJsonObject("delta")
        // delta 可能缺失（部分服务商最后一个 chunk 只带 finish_reason），此时仍要
        // 把截断信息带出去，所以不能在这里直接返回 Ignore
        val text = delta?.stringOrNull("content").orEmpty()
        // 思考链：字段名各服务商不一致，按常见顺序取第一个非空的。
        // 取不到就是空串，整条链路自然退化成"不显示思考栏"。
        val reasoning = REASONING_KEYS.firstNotNullOfOrNull { key ->
            delta?.stringOrNull(key)?.takeIf { it.isNotEmpty() }
        }.orEmpty()
        val fragments = delta?.getAsJsonArray("tool_calls")
            ?.mapNotNull { it as? JsonObject }
            .orEmpty()

        return SseLineResult.Delta(
            text = text,
            reasoning = reasoning,
            toolCallFragments = fragments,
            truncated = choice.stringOrNull("finish_reason") == "length",
        )
    }

    /**
     * 思考链字段的候选名，按常见程度排序。
     *
     * 之所以要容错多个名字：这不是标准字段。DeepSeek 用 `reasoning_content`，
     * 另一些兼容实现用 `reasoning` 或 `thinking`。多认几个的代价只是三次 map 查找，
     * 收益是换服务商时不用改代码。
     */
    private val REASONING_KEYS = listOf("reasoning_content", "reasoning", "thinking")
}

/**
 * 按 index 归并 tool_call 分片。
 *
 * OpenAI 流式下，一次工具调用的 `name` 与 `arguments` 会拆成多个 chunk 下发，
 * 且**只有首个 chunk 带 id/name，后续只有 arguments 片段**。
 * 因此必须按 index 归并，不能每帧当成一个新调用。
 *
 * 单独成类以便单测覆盖"字段分散在多个分片"这条最容易写错的路径。
 */
internal class ToolCallAccumulator {

    private val byIndex = LinkedHashMap<Int, Partial>()

    private class Partial(
        var id: String = "",
        var name: String = "",
        val args: StringBuilder = StringBuilder(),
    )

    /** 收入一帧分片。[fallbackIndex] 在服务商未给 index 时兜底（按到达顺序）。 */
    fun accept(fragment: JsonObject, fallbackIndex: Int = byIndex.size) {
        val index = fragment.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: fallbackIndex
        val partial = byIndex.getOrPut(index) { Partial() }
        fragment.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { partial.id = it }
        fragment.getAsJsonObject("function")?.let { fn ->
            fn.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { partial.name = it }
            fn.stringOrNull("arguments")?.let { partial.args.append(it) }
        }
    }

    fun acceptAll(fragments: List<JsonObject>) {
        fragments.forEach { accept(it) }
    }

    /** 归并结果。没有函数名的分片会被丢弃（协议上不可能单独调用一个无名工具）。 */
    fun build(): List<ToolCall> = byIndex.entries
        .filter { it.value.name.isNotBlank() }
        .map { (index, p) ->
            ToolCall(
                // 极少数服务商不给 id：用 index 造一个稳定 id，保证请求与回应能配对
                id = p.id.ifBlank { "call_$index" },
                name = p.name,
                argumentsJson = p.args.toString().ifBlank { "{}" },
            )
        }

    val isEmpty: Boolean get() = byIndex.isEmpty()
}
