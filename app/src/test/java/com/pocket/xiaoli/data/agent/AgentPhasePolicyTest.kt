package com.pocket.xiaoli.data.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `AgentPhasePolicy` 的契约测试 —— 把"什么时候切到哪个阶段"从 ViewModel 里搬出来之后，
 * 这条策略终于可以**不起 ViewModel、不用假 LLM** 直接验证。
 *
 * ## 为什么值得单测
 *
 * 它原本散在 `AssistantChatViewModel` 的三处（发送开始 / 工具启动 / 整轮收尾），
 * 想验证"出错后光环会不会停在 ERROR"必须起整个 ViewModel + 假 LLM 客户端 + 假仓库。
 * 而这些行为**用户看得见**：光环停在错误态、正常结束回到静息，都在「我的」页上。
 *
 * ## 这个测试同时钉死两条**容易被"顺手修掉"的有意行为**
 *
 * 1. **出错后停在 [AgentPhase.ERROR]，不自动回 IDLE** —— 产品有意为之：
 *    用户切到「我的」页还能看见"需要处理"。谁把它改成无条件 IDLE，这条会红。
 * 2. **大多数事件不改变阶段** —— 正文/思考链增量、工具完成都属于此类。
 *    别为了"看起来对称"给每个事件都编一个阶段。
 */
class AgentPhasePolicyTest {

    @Test
    fun `一轮开始是思考中`() {
        assertEquals(AgentPhase.THINKING, AgentPhasePolicy.onTurnStart())
    }

    @Test
    fun `模型决定调工具时切到查工具态`() {
        val phase = AgentPhasePolicy.onEvent(
            AgentEvent.ToolStarted(name = "today_courses", displayName = "今日课表")
        )
        assertEquals(AgentPhase.TOOL, phase)
    }

    /**
     * 除 `ToolStarted` 之外的**每一个**事件都不得改变阶段。
     *
     * 用穷举而不是挑几个：漏掉一个就等于给某个事件偷偷加了写入 ——
     * 而"光环为什么会闪一下"是最难从界面反查回来的那类问题。
     */
    @Test
    fun `其余事件都不改变阶段`() {
        val notPhaseChanging = listOf(
            AgentEvent.TextDelta("正文"),
            AgentEvent.ReasoningDelta("思考"),
            AgentEvent.ToolFinished(
                name = "today_courses",
                displayName = "今日课表",
                preview = "今天 3 节课",
            ),
            AgentEvent.ToolFinished(
                name = "today_courses",
                displayName = "今日课表",
                preview = "查询失败",
                failed = true, // 工具失败同样不改变阶段：整轮还没结束
            ),
            AgentEvent.Answer("最终回答"),
            // ⚠️ Error 也不在这里切阶段：阶段由**收尾**（onTurnEnd）决定。
            // 若改成"收到 Error 就切"，取消路径与正常结束路径会给出不同结果。
            AgentEvent.Error(message = "网络错误", detail = "HTTP 500"),
        )
        notPhaseChanging.forEach { event ->
            assertNull("事件 $event 不该改变阶段", AgentPhasePolicy.onEvent(event))
        }
    }

    @Test
    fun `正常收尾回到静息`() {
        assertEquals(AgentPhase.IDLE, AgentPhasePolicy.onTurnEnd(errored = false))
    }

    /**
     * 出错收尾**停在错误态**，这是有意的产品行为，不是漏了复位。
     *
     * 下一轮发送才会离开这个状态（`onTurnStart` → THINKING）。
     */
    @Test
    fun `出错收尾停在错误态而不是静息`() {
        assertEquals(AgentPhase.ERROR, AgentPhasePolicy.onTurnEnd(errored = true))
    }

    /**
     * **没有死取值**：策略必须能产出 [AgentPhase] 的每一个取值。
     *
     * 这条与本项目的既有纪律同源（"每个声明的字段都必须有具名消费者"）：
     * 谁将来加了第 5 个阶段却忘了在任何一条路径上产出它，它会是一个永远不出现的死枚举 ——
     * 而那时 `AiAvatar` 的 `when` 还会为它写一套颜色，看起来像是"支持了"。
     */
    @Test
    fun `策略能产出每一个阶段取值，没有死枚举`() {
        val produced = buildSet {
            add(AgentPhasePolicy.onTurnStart())
            add(AgentPhasePolicy.onTurnEnd(errored = false))
            add(AgentPhasePolicy.onTurnEnd(errored = true))
            add(AgentPhasePolicy.onEvent(AgentEvent.ToolStarted("t", "工具"))!!)
        }
        assertEquals(
            "AgentPhase 有取值永远不会被策略产出（死枚举）：" +
                (AgentPhase.entries.toSet() - produced),
            AgentPhase.entries.toSet(),
            produced,
        )
    }
}
