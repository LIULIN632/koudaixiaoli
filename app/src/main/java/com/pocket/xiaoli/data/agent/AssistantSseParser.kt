package com.pocket.xiaoli.data.agent

import com.pocket.xiaoli.core.agent.AccumulatedToolCall

// SSE 解析与 tool_call 归并已经搬进共享核心（`:core` 的 jvmAndroidMain），
// 桌面端与 Android 跑同一份。这里只留 Android 侧的模型适配。
// 调用点直接 import com.pocket.xiaoli.core.agent.{SseLineParser, SseLineResult, ToolCallAccumulator}。

/** 共享层的中立结果 → Android 落库/请求用的 [ToolCall]。 */
internal fun List<AccumulatedToolCall>.toToolCalls(): List<ToolCall> =
    map { ToolCall(id = it.id, name = it.name, argumentsJson = it.argumentsJson) }
