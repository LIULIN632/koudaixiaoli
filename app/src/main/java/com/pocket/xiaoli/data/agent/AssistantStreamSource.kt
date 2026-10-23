package com.pocket.xiaoli.data.agent

import kotlinx.coroutines.flow.Flow

/**
 * 「把一次请求发出去、换回一串流式事件」的唯一抽象。
 *
 * 抽它的理由只有一个：**让 [AssistantAgentLoop] 能脱网单测**。
 * 循环里有四条硬不变量（工具回应配对、熔断、续写、错误不吞），
 * 而这些路径用真网络几乎无法稳定复现；把「怎么发」抽走之后，
 * 测试只要按脚本吐事件就能把每条分支走一遍。
 *
 * 生产实现见 `AppContainer.newAssistantAgentLoop`：`AssistantStreamSource { req -> client.stream(config, req) }`。
 * 之所以用 fun interface 而不是让循环直接收 `AssistantLlmClient`：
 * 循环不需要知道 config 怎么读、OkHttp 怎么建，这两件事都该留在装配层。
 */
fun interface AssistantStreamSource {
    fun stream(request: LlmRequest): Flow<LlmStreamEvent>
}
