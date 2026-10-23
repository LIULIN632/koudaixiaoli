package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.data.agent.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息区两条**布局策略**的契约测试。
 *
 * 它们都是纯函数（不读 `LocalSkin`、不渲染），所以能在 JVM 上穷举验证 ——
 * 而它们决定的正是"一眼看过去舒不舒服"的两件事：
 * 轮与轮的节奏、Markdown 块与块的关系。
 *
 * 改造前这两件事**都没有策略**，是散在调用处的常量（轮距统一 `spaceMd`、
 * 块距统一 `6.dp`），既无法验证、也说不清"为什么是这个数"。
 */
class MessageLayoutPolicyTest {

    // ────────────────────────── 轮距 ──────────────────────────

    private fun userTurn() = ChatTurn(
        key = "u",
        user = AssistantUiMessage(ChatRole.USER, "问"),
        reasoning = "", reasoningSeconds = 0, reasoningStreaming = false,
        answer = "", answerStreaming = false, tools = emptyList(), error = null,
    )

    private fun answerTurn() = ChatTurn(
        key = "a",
        user = null,
        reasoning = "", reasoningSeconds = 0, reasoningStreaming = false,
        answer = "答", answerStreaming = false, tools = emptyList(), error = null,
    )

    @Test
    fun `列表第一项上方不加间距`() {
        assertEquals(TurnGap.NONE, turnGapOf(index = 0, turn = userTurn()))
        assertEquals(TurnGap.NONE, turnGapOf(index = 0, turn = answerTurn()))
    }

    @Test
    fun `用户发言是新对话的开头，上方拉开`() {
        assertEquals(TurnGap.BETWEEN, turnGapOf(index = 3, turn = userTurn()))
    }

    @Test
    fun `回答紧跟在提问之后，上方贴紧`() {
        assertEquals(TurnGap.WITHIN, turnGapOf(index = 3, turn = answerTurn()))
    }

    /**
     * 这条是**核心不变量**：提问与回答之间的间距必须**小于**两段对话之间的间距。
     *
     * 改造前两者共用同一个值 —— 后果不是"不好看"，而是**一问一答读起来不像一组**。
     * 谁将来"为了整齐"把它们调成同一个值，这里会红。
     */
    @Test
    fun `一问一答的间距必须小于两段对话之间的间距`() {
        val within = answerTurn().let { turnGapOf(1, it) }
        val between = userTurn().let { turnGapOf(1, it) }
        assertTrue(
            "WITHIN($within) 应当比 BETWEEN($between) 更紧 —— 否则提问和回答会被推得一样远",
            within != between,
        )
    }

    @Test
    fun `轮距档位没有死枚举`() {
        val produced = setOf(
            turnGapOf(0, userTurn()),
            turnGapOf(1, userTurn()),
            turnGapOf(1, answerTurn()),
        )
        assertEquals(TurnGap.entries.toSet(), produced)
    }

    // ────────────────────────── Markdown 块距 ──────────────────────────

    private fun paragraph() = MdBlock.Paragraph(MdRun(listOf(MdInline("文字"))))
    private fun listItem() = MdBlock.ListItem(ordered = false, index = 0, indent = 0, run = MdRun(listOf(MdInline("项"))))
    private fun code() = MdBlock.CodeBlock(language = "kotlin", code = "val x = 1")
    private fun quote() = MdBlock.Quote(blocks = listOf(paragraph()))
    private fun divider() = MdBlock.Divider

    @Test
    fun `同一列表内的相邻项贴紧`() {
        assertEquals(MarkdownGap.TIGHT, markdownGapOf(listItem(), listItem()))
    }

    @Test
    fun `代码块与相邻内容之间拉开`() {
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(paragraph(), code()))
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(code(), paragraph()))
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(code(), code()))
    }

    @Test
    fun `引用块与分隔线同样拉开`() {
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(paragraph(), quote()))
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(quote(), paragraph()))
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(divider(), paragraph()))
        assertEquals(MarkdownGap.LOOSE, markdownGapOf(paragraph(), divider()))
    }

    @Test
    fun `普通段落之间是常规间距`() {
        assertEquals(MarkdownGap.NORMAL, markdownGapOf(paragraph(), paragraph()))
        // 列表结束后回到正文：属于"跨组"，用常规而不是列表内的紧间距
        assertEquals(MarkdownGap.NORMAL, markdownGapOf(listItem(), paragraph()))
        assertEquals(MarkdownGap.NORMAL, markdownGapOf(paragraph(), listItem()))
    }

    /**
     * 这条把"分层"本身钉住：**三种档位必须都被真实产出**，
     * 且必须**互不相同** —— 否则"分了三档"只是三个名字。
     */
    @Test
    fun `块距三档都可达且互不相同`() {
        val produced = setOf(
            markdownGapOf(listItem(), listItem()),
            markdownGapOf(paragraph(), paragraph()),
            markdownGapOf(paragraph(), code()),
        )
        assertEquals(MarkdownGap.entries.toSet(), produced)
        assertTrue(
            "段间距与列表内间距不应是同一个档位",
            markdownGapOf(listItem(), listItem()) != markdownGapOf(paragraph(), paragraph()),
        )
        assertTrue(
            "块元素间距应当比段间距更松",
            markdownGapOf(paragraph(), code()) != markdownGapOf(paragraph(), paragraph()),
        )
    }
}
