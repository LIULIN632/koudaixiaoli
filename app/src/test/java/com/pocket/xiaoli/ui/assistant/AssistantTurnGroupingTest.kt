package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.data.agent.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只构造界面上会出现的四种条目，避免每个用例都写一长串命名参数。 */
private fun user(text: String) = AssistantUiMessage(ChatRole.USER, text)

private fun reply(text: String, streaming: Boolean = false) =
    AssistantUiMessage(ChatRole.ASSISTANT, text, streaming = streaming)

private fun tool(name: String, preview: String = "…", failed: Boolean = false) =
    AssistantUiMessage(ChatRole.TOOL, preview, toolName = name, toolFailed = failed)

private fun error(text: String) =
    AssistantUiMessage(ChatRole.ASSISTANT, text, isError = true, needsRetry = true)

private fun reasoning(text: String, answer: String) =
    AssistantUiMessage(ChatRole.ASSISTANT, answer, reasoning = text)

/** 一段带计时的思考链（[startedAt] / [endedAt] 是毫秒时间戳，0 表示未结束）。 */
private fun reasoningTimed(
    text: String,
    answer: String,
    startedAt: Long,
    endedAt: Long = 0L,
) = AssistantUiMessage(
    role = ChatRole.ASSISTANT,
    content = answer,
    reasoning = text,
    reasoningStartedAt = startedAt,
    reasoningEndedAt = endedAt,
)

/**
 * 「一轮一条目」的合并规则。
 *
 * 这些断言锁住的是改造的核心不变量：**多步任务必须只出一个条目**。
 * 一旦有人把 `toTurns` 的边界条件改坏（比如把 TOOL 当作新一轮），
 * 界面会退回"刷屏 + 答案被夹在中间"，而那在模拟器里未必一眼看得出来。
 */
class AssistantTurnGroupingTest {

    @Test
    fun `一条用户消息自己就是一轮`() {
        val turns = listOf(user("今天有什么课")).toTurns()

        assertEquals(1, turns.size)
        assertEquals("今天有什么课", turns[0].user?.content)
        assertEquals("", turns[0].answer)
        assertTrue(turns[0].tools.isEmpty())
        assertTrue(turns[0].error == null)
    }

    @Test
    fun `先说话再调工具再回答 合成一轮且工具折进脚注`() {
        val turns = listOf(
            user("今天有什么课，A101 在哪"),
            reply("我先查一下。"),
            tool("今日课程", "今天的课：· 1-2节 高等数学"),
            tool("教室位置", "A101 在 1 号楼 1 层"),
            reply("你今天 1-2 节有高数，在 A101。"),
        ).toTurns()

        // JUnit 4 的 assertEquals 是 (message, expected, actual) —— 消息在**第一个**参数
        // 用户发言占 turns[0]，后面那一长串全部归到 turns[1]
        assertEquals("多步任务不该产生多个条目", 2, turns.size)
        assertTrue(turns[1].user == null)
        assertEquals(2, turns[1].tools.size)
        // 两段助手文本按顺序拼成一整段，正文在脚注上方
        assertEquals("我先查一下。你今天 1-2 节有高数，在 A101。", turns[1].answer)
    }

    @Test
    fun `用户发言后面才是新一轮 不会把上一轮的工具带走`() {
        val turns = listOf(
            user("第一个问题"),
            tool("今日课程"),
            reply("今天没课。"),
            user("第二个问题"),
            tool("成绩"),
            reply("绩点 3.5。"),
        ).toTurns()

        assertEquals("两条用户消息 + 两轮回答 = 4 个条目", 4, turns.size)
        assertTrue(turns[0].user != null)
        assertEquals(1, turns[1].tools.size)
        assertEquals("今天没课。", turns[1].answer)
        assertTrue(turns[2].user != null)
        assertEquals(1, turns[3].tools.size)
        assertEquals("绩点 3.5。", turns[3].answer)
    }

    @Test
    fun `流式中的最后一段带 streaming 标记`() {
        val turns = listOf(user("你好"), reply("你", streaming = true)).toTurns()

        assertTrue(turns[1].answerStreaming)
        assertEquals("你", turns[1].answer)
    }

    @Test
    fun `错误卡片不混进正文 而是单独挂在这一轮上`() {
        val turns = listOf(user("问点东西"), error("模型请求失败（HTTP 401）：API Key 可能不对")).toTurns()

        assertEquals("错误文案不该被拼进回答正文（否则会出现两遍）", "", turns[1].answer)
        assertEquals("模型请求失败（HTTP 401）：API Key 可能不对", turns[1].error?.content)
        assertTrue(turns[1].error?.needsRetry == true)
    }

    @Test
    fun `模型没说话只调了工具时 仍然是一轮`() {
        val turns = listOf(user("A101 在哪"), tool("教室位置", "A101 在 1 号楼")).toTurns()

        assertEquals(2, turns.size)
        assertEquals("没有正文时不该凭空造一个空回答气泡", "", turns[1].answer)
        assertEquals(1, turns[1].tools.size)
    }

    @Test
    fun `空列表不炸`() {
        assertTrue(emptyList<AssistantUiMessage>().toTurns().isEmpty())
    }

    @Test
    fun `工具失败标记原样带到界面`() {
        val turns = listOf(user("查个东西"), tool("成绩", "工具 get_grades 执行失败：boom", failed = true)).toTurns()

        assertTrue("失败要能在收起态就看见，不能被折叠藏起来", turns[1].tools.single().toolFailed)
    }

    // ── 思考链 ──

    @Test
    fun `思考链汇总到所在那一轮 且与正文分开`() {
        val turns = listOf(user("今天有什么课"), reasoning("用户想知道今天的课，我需要调工具", "先查一下。")).toTurns()

        assertEquals("用户想知道今天的课，我需要调工具", turns[1].reasoning)
        assertEquals("先查一下。", turns[1].answer)
        // 思考链绝不能混进正文：混进去就会被当成回答显示、也可能被落库
        assertTrue(!turns[1].answer.contains("用户想知道"))
    }

    @Test
    fun `多段回答的思考链按顺序拼接`() {
        val turns = listOf(
            user("查课表和教室"),
            reasoning("先查课表。", "让我查一下。"),
            tool("今日课程"),
            reasoning("再查教室。", "A101 在 1 号楼。"),
        ).toTurns()

        assertEquals(2, turns.size)
        assertEquals("先查课表。再查教室。", turns[1].reasoning)
    }

    @Test
    fun `没有思考链时为空字符串而不是 null 崩溃`() {
        val turns = listOf(user("你好"), reply("你好呀")).toTurns()

        assertEquals("", turns[1].reasoning)
        assertEquals(0, turns[1].reasoningSeconds)
    }

    // ── 思考耗时（抄 DeepSeek 的「Thought for N seconds」） ──

    @Test
    fun `思考耗时取开始到正文到达之间的秒数`() {
        val turns = listOf(
            user("今天有什么课"),
            reasoningTimed("想一下", "今天有课。", startedAt = 1_000L, endedAt = 13_500L),
        ).toTurns()

        assertEquals(12, turns[1].reasoningSeconds)
        assertTrue("结束时间已到，不该再显示思考中", !turns[1].reasoningStreaming)
    }

    @Test
    fun `还在思考时没有秒数且标记为思考中`() {
        val turns = listOf(
            user("今天有什么课"),
            reasoningTimed("正在想", "", startedAt = 1_000L, endedAt = 0L),
        ).toTurns()

        assertEquals(0, turns[1].reasoningSeconds)
        assertTrue("正文还没到，应显示思考中", turns[1].reasoningStreaming)
    }

    @Test
    fun `多段回答的耗时不重复计 区间取先开始与后结束`() {
        // 一轮里"想 → 说 → 调工具 → 再想 → 再说"：只有第一段的开始与最后一段的结束
        // 才构成有意义的区间；逐段相加会把工具执行的时间也算成思考时间
        val turns = listOf(
            user("查课表和教室"),
            reasoningTimed("先查课表", "让我查一下。", startedAt = 1_000L, endedAt = 5_000L),
            tool("今日课程"),
            reasoningTimed("再查教室", "A101 在 1 号楼。", startedAt = 9_000L, endedAt = 15_000L),
        ).toTurns()

        assertEquals(14, turns[1].reasoningSeconds)
    }

    @Test
    fun `不足一秒的思考也至少显示一秒`() {
        val turns = listOf(
            user("你好"),
            reasoningTimed("想", "你好呀", startedAt = 1_000L, endedAt = 1_400L),
        ).toTurns()

        // 显示"思考了 0 秒"很怪；coerceAtLeast(1) 保证不出现 0
        assertEquals(1, turns[1].reasoningSeconds)
    }

    @Test
    fun `没有计时信息时不显示秒数 而是退回思考过程`() {
        // 冷启动从库里恢复的历史没有计时（思考链本来就不落库）
        val turns = listOf(user("你好"), reasoning("想了一下", "你好呀")).toTurns()

        assertEquals(0, turns[1].reasoningSeconds)
        assertTrue(!turns[1].reasoningStreaming)
    }
}
