package com.pocket.xiaoli.data.agent

/**
 * 请求上下文的裁剪器。抽成独立单元是为了能单测那条**会导致服务端 400 的不变量**。
 *
 * ## 为什么必须按"轮"裁，而不是按单条消息裁
 *
 * OpenAI 兼容协议要求：assistant 消息带 N 个 `tool_calls` 时，紧随其后必须有
 * N 条 `role=tool` 的回应，且 `tool_call_id` 一一对应。若裁剪时把
 * `assistant(tool_calls)` 丢掉却留下了 `tool` 回应（或反过来），服务端会直接
 * 返回 400 —— 而且这个错误在开发期很难复现，只有上下文长到触发裁剪时才出现。
 *
 * 所以这里以「一条 user 消息 + 其后直到下一条 user 之前的全部消息」为一个不可分割的
 * 单元（一轮），从最老的轮开始整轮丢弃。这样配对天然完整。
 */
internal object AssistantContextBuilder {

    /** 上下文正文字符预算。中文按 1 字 ≈ 1 计；英文/JSON 折半估算，够用且不引 tokenizer。 */
    const val MAX_CONTEXT_CHARS = 12_000

    /**
     * 组装本次请求的消息序列。
     *
     * @param history 此前已持久化的消息
     * @param currentTurn 本轮新增的消息（至少含一条 user）
     */
    fun build(history: List<ChatMessage>, currentTurn: List<ChatMessage>): List<ChatMessage> {
        val all = history + currentTurn
        if (all.sumOf { it.cost() } <= MAX_CONTEXT_CHARS) return all

        // 切成一"轮"一组
        val turns = mutableListOf<MutableList<ChatMessage>>()
        all.forEach { msg ->
            if (msg.role == ChatRole.USER || turns.isEmpty()) {
                turns += mutableListOf(msg)
            } else {
                turns.last() += msg
            }
        }

        // 从最新一轮往回取，超预算就停
        val kept = ArrayDeque<List<ChatMessage>>()
        var budget = 0
        for (turn in turns.asReversed()) {
            val cost = turn.sumOf { it.cost() }
            if (kept.isNotEmpty() && budget + cost > MAX_CONTEXT_CHARS) break
            kept.addFirst(turn)
            budget += cost
        }
        val result = kept.flatten()

        // 兜底：整轮丢弃后若开头不是 user（理论上不会发生，但协议上
        // 以 assistant/tool 开头的序列同样非法），丢掉开头直到 user
        val firstUser = result.indexOfFirst { it.role == ChatRole.USER }
        return if (firstUser <= 0) result else result.subList(firstUser, result.size)
    }

    /** 估算一条消息的上下文开销。+4 为角色等协议开销。 */
    private fun ChatMessage.cost(): Int {
        val args = toolCalls.sumOf { it.name.length + it.argumentsJson.length }
        return content.length + args + 4
    }
}
