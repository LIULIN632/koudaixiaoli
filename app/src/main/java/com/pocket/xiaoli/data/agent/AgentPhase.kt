package com.pocket.xiaoli.data.agent

/**
 * Agent 此刻在做什么。
 *
 * ## 为什么它住在 `data/agent` 而不是 `ui/theme`
 *
 * 这四个取值描述的是 **Agent 的生命周期**，与"界面长什么样"无关 ——
 * 头像光环只是它的一个**消费者**。最初它定义在 `ui/theme/AiAvatar.kt` 里，
 * 于是 `di/AppContainer`（DI 容器）不得不 `import com.pocket.xiaoli.ui.theme.*`
 * 来持有它 —— **依赖方向是倒的**：容器属于 data/di 侧，却依赖了 UI 类型。
 *
 * 搬到这里之后方向恢复为合法的 `ui → data`：UI 层消费它，data 层拥有它。
 *
 * ## 它是一条跨功能的显式通道
 *
 * 写入方是鲤鱼（`AssistantChatViewModel`），读取方是「我的」页的鲤鱼光环
 *（`ProfileScreen` / `ProfileIdentity`）。也就是说**鲤鱼页在驱动「我的」页的动效**。
 * 这层耦合是产品有意为之（"用户切到我的页也能看出它还在跑"），
 * 但它是隐式的、跨 tab 的 —— 改动任一侧前先看一眼另一侧。
 */
enum class AgentPhase {
    /** 静息：没有进行中的对话。 */
    IDLE,

    /** 思考中：模型正在流式输出（尚未调用工具）。 */
    THINKING,

    /** 查工具中：正在调用校园数据工具。 */
    TOOL,

    /**
     * 出错：上一轮对话失败。
     *
     * ⚠️ **刻意不自动回到 [IDLE]** —— 停在错误态，用户切到「我的」页还能看见"需要处理"。
     * 下一轮发送才会离开这个状态。这是有意的产品行为，不是漏了复位。
     */
    ERROR,
}

/**
 * Agent 阶段的**唯一策略来源**。
 *
 * 抽出来的理由与 `AssistantContextBuilder` 同源：把"什么时候切到哪个阶段"从
 * ViewModel 的业务流程里剥离，好让这条策略**能被单测直接覆盖** ——
 * 它原本散在 `AssistantChatViewModel` 的三处（发送开始 / 工具启动 / 整轮收尾），
 * 想验证"取消时会不会停在 ERROR"必须起整个 ViewModel + 假 LLM。
 *
 * ⚠️ 这里**只收编"由什么决定阶段"这件事**，不收编"阶段影响了什么渲染"
 *（那是 `AiAvatar` 的事）。同理，也**不提供** `phaseOf(event): AgentPhase` 这种
 * "每个事件都映射出一个阶段"的函数 —— 现行为里绝大多数事件（正文增量、工具完成）
 * **本就不改变阶段**，硬给它们编一个映射会凭空造出当前不存在的写入。
 */
internal object AgentPhasePolicy {

    /** 一轮对话开始（用户按下发送）。 */
    fun onTurnStart(): AgentPhase = AgentPhase.THINKING

    /**
     * 事件驱动的阶段变化；**返回 `null` 表示该事件不改变阶段**。
     *
     * 目前只有"模型决定调工具"会改变阶段：正文与思考链增量不改变它
     *（都还在 THINKING 的语义里），工具完成也不改变（下一帧就要么继续调、
     * 要么收尾）。
     */
    fun onEvent(event: AgentEvent): AgentPhase? = when (event) {
        is AgentEvent.ToolStarted -> AgentPhase.TOOL
        is AgentEvent.TextDelta,
        is AgentEvent.ReasoningDelta,
        is AgentEvent.ToolFinished,
        is AgentEvent.Answer,
        is AgentEvent.Error,
        -> null
    }

    /**
     * 一轮对话收尾（**正常结束与取消都要走这里**）。
     *
     * @param errored 本轮是否以失败告终。⚠️ 判据必须是**调用方自己的局部标志**，
     *   不能读 `AssistantUiState.error` —— 那是给「我的」页光环看的展示字段，
     *   会被下一轮发送清空，拿它当控制流会让收尾依赖一个展示字段。
     */
    fun onTurnEnd(errored: Boolean): AgentPhase =
        if (errored) AgentPhase.ERROR else AgentPhase.IDLE
}
