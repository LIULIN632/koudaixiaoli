package com.pocket.xiaoli.ui.assistant

import com.pocket.xiaoli.ui.theme.MinimalSkin
import com.pocket.xiaoli.ui.theme.VividSkin
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

    @Test
    fun `列表第一轮上方不加间距`() {
        assertEquals(TurnGap.NONE, roundGapOf(index = 0))
    }

    @Test
    fun `两段问答之间一律拉开`() {
        assertEquals(TurnGap.BETWEEN, roundGapOf(index = 1))
        assertEquals(TurnGap.BETWEEN, roundGapOf(index = 9))
    }

    /**
     * 这条是**核心不变量**：一轮内部（提问与它的回答之间）必须比两段对话之间更紧。
     *
     * 改造前两者共用同一个值 —— 后果不是"不好看"，而是**一问一答读起来不像一组**。
     * 轮内走 [TurnGap.WITHIN]（`spaceSm`）、轮间走 [TurnGap.BETWEEN]（`spaceXl`），
     * 而各皮肤可以自由调刻度，所以这条要在**每套皮肤**上都成立。
     */
    @Test
    fun `轮内间距必须小于轮间距 两套皮肤都成立`() {
        listOf("Minimal" to MinimalSkin, "Vivid" to VividSkin).forEach { (name, skin) ->
            assertTrue(
                "$name：轮内 ${skin.spaceSm} 应比轮间 ${skin.spaceXl} 更紧 —— " +
                    "否则提问和回答会被推得一样远",
                skin.spaceSm < skin.spaceXl,
            )
        }
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
