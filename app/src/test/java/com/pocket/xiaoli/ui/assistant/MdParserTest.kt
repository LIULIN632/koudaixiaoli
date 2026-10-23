package com.pocket.xiaoli.ui.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 解析器的单测。
 *
 * 重点不在"能不能解析标准语法"（聊天回复用不到那么全），而在**流式容错**：
 * 解析器每次拿到的都是半截文本，`**加粗`、只写了一半的代码围栏、末尾孤零零的
 * `[标题](` 都会真实出现。这些情况一旦处理错，用户会看到**文字凭空消失**或者
 * 整段重新排版（闪烁），而且很难在模拟器里稳定复现。
 */
class MdParserTest {

    // ── 纯文本与段落 ──

    @Test
    fun `普通文本是一个段落`() {
        val blocks = MdParser.parse("今天没有课。")
        assertEquals(1, blocks.size)
        val p = blocks[0] as MdBlock.Paragraph
        assertEquals("今天没有课。", p.run.spans.joinToString("") { it.text })
    }

    @Test
    fun `连续非空行合并成一个段落 空行分段`() {
        val blocks = MdParser.parse("第一行\n第二行\n\n另一段")
        assertEquals(2, blocks.size)
        assertEquals("第一行 第二行", (blocks[0] as MdBlock.Paragraph).run.spans.joinToString("") { it.text })
        assertEquals("另一段", (blocks[1] as MdBlock.Paragraph).run.spans.joinToString("") { it.text })
    }

    @Test
    fun `空文本没有块`() {
        assertTrue(MdParser.parse("").isEmpty())
        assertTrue(MdParser.parse("   \n  ").isEmpty())
    }

    // ── 行内样式 ──

    @Test
    fun `加粗与斜体`() {
        val run = MdParser.parseInline("这是**重点**和*斜体*")

        assertEquals("这是重点和斜体", run.spans.joinToString("") { it.text })
        assertTrue("重点" in run.spans.filter { it.bold }.joinToString("") { it.text })
        assertTrue("斜体" in run.spans.filter { it.italic }.joinToString("") { it.text })
        // 星号本身绝不能留在显示文本里
        assertTrue(run.spans.none { it.text.contains('*') })
    }

    @Test
    fun `双星号里嵌套单星号 两层样式都生效`() {
        val run = MdParser.parseInline("**粗*斜*粗**")

        assertEquals("粗斜粗", run.spans.joinToString("") { it.text })
        assertTrue("斜" in run.spans.filter { it.bold && it.italic }.joinToString("") { it.text })
    }

    @Test
    fun `行内代码不解析其中的星号`() {
        val run = MdParser.parseInline("用 `a**b` 表示")

        val code = run.spans.single { it.code }
        assertEquals("a**b", code.text)
        assertTrue("代码里的星号不该被当成加粗", run.spans.none { it.bold })
    }

    @Test
    fun `删除线`() {
        val run = MdParser.parseInline("~~删掉~~")

        assertEquals("删掉", run.spans.joinToString("") { it.text })
        assertTrue(run.spans.any { it.strike })
    }

    @Test
    fun `链接解析出文字与地址 并登记到 links`() {
        val run = MdParser.parseInline("见[课表](https://example.com/a)")

        assertEquals("见课表", run.spans.joinToString("") { it.text })
        val linkSpan = run.spans.single { it.link != null }
        assertEquals("https://example.com/a", linkSpan.link)
        assertEquals(listOf("课表" to "https://example.com/a"), run.links)
    }

    @Test
    fun `裸链接自动识别 且结尾标点不算进地址`() {
        val run = MdParser.parseInline("详情 https://example.com/x。")

        val link = run.spans.single { it.link != null }
        assertEquals("https://example.com/x", link.link)
        assertTrue("句号必须留在链接之外", run.spans.any { it.link == null && it.text.contains("。") })
    }

    @Test
    fun `反斜杠转义让星号原样显示`() {
        val run = MdParser.parseInline("\\*不是斜体\\*")

        assertEquals("*不是斜体*", run.spans.joinToString("") { it.text })
        assertTrue(run.spans.none { it.italic })
    }

    // ── 流式容错（本文件的核心） ──

    @Test
    fun `未闭合的加粗按普通字符显示 不吞字`() {
        // 这是流式最常见的中间态：右半个星号还没到
        val run = MdParser.parseInline("这是**加粗")

        assertEquals("这是**加粗", run.spans.joinToString("") { it.text })
        assertTrue("不能产生空的加粗片段", run.spans.none { it.bold })
    }

    @Test
    fun `未闭合的行内代码同样不吞字`() {
        val run = MdParser.parseInline("看 `代码")

        assertEquals("看 `代码", run.spans.joinToString("") { it.text })
        assertTrue(run.spans.none { it.code })
    }

    @Test
    fun `写了一半的链接按普通字符显示`() {
        val run = MdParser.parseInline("见[标题](")

        assertEquals("见[标题](", run.spans.joinToString("") { it.text })
        assertTrue(run.links.isEmpty())
    }

    @Test
    fun `未闭合的代码围栏照常进入代码块模式`() {
        val blocks = MdParser.parse("```kotlin\nval a = 1")

        assertEquals(1, blocks.size)
        val code = blocks[0] as MdBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val a = 1", code.code)
    }

    @Test
    fun `闭合围栏之后的文本回到正常解析`() {
        val blocks = MdParser.parse("```\ncode\n```\n之后的正文")

        assertEquals(2, blocks.size)
        assertEquals("code", (blocks[0] as MdBlock.CodeBlock).code)
        assertEquals("之后的正文", (blocks[1] as MdBlock.Paragraph).run.spans.joinToString("") { it.text })
    }

    @Test
    fun `逐字增长的过程中 已经显示出来的文字不会消失`() {
        val full = "**重点** 和 `代码` 结束"
        // 真正要守住的性质：**渲染结果里去掉样式标记后，必须始终是"已到达内容"的前缀**。
        // 一旦解析器把未闭合的标记"吞掉"（比如把 `**重点` 解析成一个空的加粗片段），
        // 这个前缀关系就会断掉，用户会看到文字凭空消失。
        // 用"前缀"而不是"长度不减"：标记符在闭合到达时会消失（`**` 变成加粗），
        // 那是**应该**发生的，长度本来就会减少。
        for (i in 1..full.length) {
            val arrived = full.substring(0, i)
            val expected = arrived.replace("**", "").replace("`", "")
            val got = MdParser.parseInline(arrived).spans.joinToString("") { it.text }
            // 把"还没闭合、因而暂时以字面显示的标记符"也剥掉再比前缀：
            // 标记符的出现在这里不代表内容出错，它下一帧就会被样式吃掉
            val gotVisible = got.replace("*", "").replace("`", "")
            assertTrue(
                "第 $i 个字符时，可见文字 [$gotVisible] 不再是已到达内容 [$expected] 的前缀",
                expected.startsWith(gotVisible),
            )
        }
    }

    // ── 块级元素 ──

    @Test
    fun `标题按级别解析`() {
        val blocks = MdParser.parse("# 一级\n## 二级")

        assertEquals(2, blocks.size)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals("一级", blocks[0].let { (it as MdBlock.Heading).run.spans.joinToString("") { s -> s.text } })
        assertEquals(2, (blocks[1] as MdBlock.Heading).level)
    }

    @Test
    fun `井号后面没有空格就不是标题`() {
        val blocks = MdParser.parse("#1 号楼在食堂旁边")

        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun `无序列表与有序列表`() {
        val blocks = MdParser.parse("- 苹果\n- 香蕉\n1. 第一\n2. 第二")

        assertEquals(4, blocks.size)
        val first = blocks[0] as MdBlock.ListItem
        assertTrue(!first.ordered)
        assertEquals("苹果", first.run.spans.joinToString("") { it.text })
        val third = blocks[2] as MdBlock.ListItem
        assertTrue(third.ordered)
        assertEquals(1, third.index)
        assertEquals(2, (blocks[3] as MdBlock.ListItem).index)
    }

    @Test
    fun `星号开头且后面有空格是列表项 不是分隔线`() {
        val blocks = MdParser.parse("* 一项")

        assertTrue(blocks[0] is MdBlock.ListItem)
    }

    @Test
    fun `分隔线`() {
        val blocks = MdParser.parse("前\n\n---\n\n后")

        assertEquals(3, blocks.size)
        assertEquals(MdBlock.Divider, blocks[1])
    }

    @Test
    fun `引用块内容递归解析`() {
        val blocks = MdParser.parse("> 提示\n> - 子项")

        val quote = blocks.single() as MdBlock.Quote
        assertEquals(2, quote.blocks.size)
        assertTrue(quote.blocks[0] is MdBlock.Paragraph)
        assertTrue(quote.blocks[1] is MdBlock.ListItem)
    }

    @Test
    fun `缩进的列表项记录缩进层级`() {
        val blocks = MdParser.parse("- 一级\n  - 二级")

        assertEquals(0, (blocks[0] as MdBlock.ListItem).indent)
        assertEquals(1, (blocks[1] as MdBlock.ListItem).indent)
    }

    @Test
    fun `一段完整的课表回答`() {
        val md = """
            ## 明天的课
            - **1-2 节** 高等数学 @A101
            - 3-4 节 大学英语

            代码示例：
            ```
            val x = 1
            ```
        """.trimIndent()

        val blocks = MdParser.parse(md)

        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(2, blocks.count { it is MdBlock.ListItem })
        assertTrue(blocks.any { it is MdBlock.CodeBlock })
        // 列表项里的加粗生效，且星号不残留
        val items = blocks.filterIsInstance<MdBlock.ListItem>()
        assertTrue(items.any { item -> item.run.spans.any { it.bold } })
        assertTrue(items.none { item -> item.run.spans.any { it.text.contains("**") } })
    }
}
