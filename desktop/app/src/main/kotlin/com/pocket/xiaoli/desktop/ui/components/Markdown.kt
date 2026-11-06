package com.pocket.xiaoli.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.desktop.ui.theme.PocketXiaoliColors
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

/**
 * 极简 Markdown 渲染（粗体 / 行内代码 / 链接 / 标题 / 列表 / 引用）。
 *
 * ## 为什么只做这一小撮语法
 *
 * 知识库正文与 AI 回答里实际出现的只有这几类记号（在 `knowledge.json` 里统计过）。
 * 上一份实现试图支持完整 CommonMark（表格、嵌套列表），代价是渲染器成一个真源之外的
 * 第二套排版引擎，而且与移动端的观感不一致。这里的原则是：**认不出的记号原样显示**，
 * 宁可露出 `**` 也不吞文字。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = PocketTypography.body,
    color: Color? = null,
    selectable: Boolean = false,
) {
    val colors = pocketColors()
    val textColor = color ?: colors.textPrimary
    val blocks = remember(markdown) { splitBlocks(markdown) }
    val content: @Composable () -> Unit = {
        Column(modifier = modifier) {
            blocks.forEachIndexed { index, block ->
                when (block) {
                    is MdBlock.Text -> {
                        if (block.content.isNotBlank()) {
                            Text(
                                text = rememberMarkdown(block.content, style, colors, colors.accentMain),
                                style = LocalTextStyle.current.merge(style).copy(color = textColor),
                            )
                        }
                    }

                    is MdBlock.Table -> {
                        if (index > 0) Spacer(Modifier.height(6.dp))
                        MarkdownTable(block.rows, style, textColor)
                        if (index < blocks.lastIndex) Spacer(Modifier.height(6.dp))
                    }

                    is MdBlock.Code -> {
                        if (index > 0) Spacer(Modifier.height(6.dp))
                        CodeBlock(block.code, style, textColor)
                        if (index < blocks.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
    if (selectable) SelectionContainer { content() } else content()
}

/** Markdown 里能被「结构化渲染」的块：普通文本（含标题/列表/引用）与表格。 */
private sealed interface MdBlock {
    data class Text(val content: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data class Code(val code: String) : MdBlock
}

/**
 * 把 Markdown 拆成文本块与表格块。
 *
 * 只认「整行以 | 开头或包含 |，且下一行是 `|---|---|` 分隔行」这种写法（GFM 的常见形态）；
 * 认不出的 `|` 还是留在正文里原样显示 —— 宁可露出竖线也不吞内容。
 */
private fun splitBlocks(markdown: String): List<MdBlock> {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val text = StringBuilder()
    var i = 0

    fun flushText() {
        if (text.isNotEmpty()) {
            blocks += MdBlock.Text(text.toString().trimEnd('\n'))
            text.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val next = lines.getOrNull(i + 1)

        // 围栏代码块：整块收进 MdBlock.Code，交给带背景的容器渲染（不在正文里逐行加底色）
        if (line.trimStart().startsWith("```")) {
            flushText()
            val code = StringBuilder()
            var j = i + 1
            while (j < lines.size && !lines[j].trimStart().startsWith("```")) {
                if (code.isNotEmpty()) code.append('\n')
                code.append(lines[j])
                j++
            }
            blocks += MdBlock.Code(code.toString())
            i = if (j < lines.size) j + 1 else j
            continue
        }
        val isTableStart = line.contains('|') && next != null && isTableSeparator(next)
        if (isTableStart) {
            flushText()
            val rows = mutableListOf<List<String>>()
            var j = i
            while (j < lines.size && lines[j].contains('|')) {
                if (!isTableSeparator(lines[j])) {
                    val cells = lines[j].trim().trim('|').split('|').map { it.trim() }
                    if (cells.any { it.isNotEmpty() }) rows += cells
                }
                j++
            }
            if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            i = j
            continue
        }
        text.append(line).append('\n')
        i++
    }
    flushText()
    return blocks.ifEmpty { listOf(MdBlock.Text(markdown)) }
}

private fun isTableSeparator(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-')) return false
    return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' || it == '\t' }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>, style: TextStyle, textColor: Color) {
    val colors = pocketColors()
    val columns = rows.maxOf { it.size }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.surfaceHighlight, shape),
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (rowIndex == 0) colors.surfaceAlt else colors.surfaceCard),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                for (column in 0 until columns) {
                    val cell = row.getOrElse(column) { "" }
                    Text(
                        text = rememberMarkdown(
                            if (rowIndex == 0) "**$cell**" else cell,
                            style,
                            colors,
                            colors.accentMain,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = LocalTextStyle.current.merge(style).copy(color = textColor),
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = colors.surfaceHighlight)
            }
        }
    }
}

@Composable
private fun rememberMarkdown(
    markdown: String,
    style: TextStyle,
    colors: PocketXiaoliColors,
    linkColor: Color,
): AnnotatedString = androidx.compose.runtime.remember(markdown, style, colors, linkColor) {
    buildMarkdown(markdown, style, colors, linkColor)
}

private fun buildMarkdown(
    markdown: String,
    style: TextStyle,
    colors: PocketXiaoliColors,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold, color = colors.textPrimary)
    val code = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = (style.fontSize.value - 1f).sp,
        color = colors.textSecondary,
        background = colors.surfaceAlt,
    )
    val link = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)

    val lines = markdown.replace("\r\n", "\n").split('\n')
    var inCode = false
    lines.forEachIndexed { lineIndex, rawLine ->
        var line = rawLine
        if (lineIndex > 0) append('\n')

        // 围栏代码块：``` 行本身不出现在正文里，块内按等宽字体原样显示（不解析行内记号）
        if (line.trimStart().startsWith("```")) {
            inCode = !inCode
            return@forEachIndexed
        }
        if (inCode) {
            withStyle(code) { append(line) }
            return@forEachIndexed
        }

        // 标题：# / ## / ###
        val headingMatch = Regex("^(#{1,4})\\s+(.*)$").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val size = when (level) {
                1 -> style.fontSize.value + 5f
                2 -> style.fontSize.value + 3f
                else -> style.fontSize.value + 1f
            }
            withStyle(bold.copy(fontSize = size.sp)) {
                appendInline(headingMatch.groupValues[2], bold, code, link)
            }
            return@forEachIndexed
        }

        // 引用块：> 文字
        if (line.startsWith("> ")) {
            withStyle(SpanStyle(color = colors.textSecondary)) {
                append("│ ")
                appendInline(line.removePrefix("> "), bold, code, link)
            }
            return@forEachIndexed
        }

        // 列表：- * • 以及 1. 2.
        val bullet = when {
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> "• "
            Regex("^\\d+\\.\\s").containsMatchIn(line) -> ""
            else -> null
        }
        if (bullet != null) {
            if (bullet.isNotEmpty()) {
                line = bullet + line.drop(2)
            }
            withStyle(SpanStyle(color = colors.textSecondary)) { append("  ") }
        }

        // 连续空格/制表符折叠
        line = line.replace("\t", "    ")
        appendInline(guardCjkLineBreaks(line), bold, code, link)
    }
}

private fun AnnotatedString.Builder.appendInline(
    text: String,
    bold: SpanStyle,
    code: SpanStyle,
    link: SpanStyle,
) {
    var i = 0
    while (i < text.length) {
        val rest = text.length - i
        if (rest >= 2 && text.startsWith("**", i)) {
            val end = text.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(bold) { append(text.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        }
        if (text[i] == '`') {
            val end = text.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(code) { append(text.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }
        if (text[i] == '[') {
            val labelEnd = text.indexOf("](", i + 1)
            if (labelEnd > i + 1) {
                val urlEnd = text.indexOf(')', labelEnd + 2)
                if (urlEnd > labelEnd + 2) {
                    val label = text.substring(i + 1, labelEnd)
                    withStyle(link) { append(label) }
                    i = urlEnd + 1
                    continue
                }
            }
        }
        // 简单斜体 *text*（不要与 ** 冲突：走到这里说明不是 ** 开头）
        if (text[i] == '*' && rest > 2) {
            val end = text.indexOf('*', i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        append(text[i])
        i++
    }
}

/** 把一段文本截到 [max] 字，超出补省略号。 */
fun String.ellipsize(max: Int): String =
    if (length <= max) this else take(max).trimEnd() + "…"

/** 中文排版里常用的字号放大系数（em → sp 的便捷写法）。 */
fun TextStyle.em(factor: Float): TextStyle = copy(fontSize = (fontSize.value * factor).sp, letterSpacing = (factor * 0.0f).em)

/**
 * 中文行首行尾避头尾（标点悬挂）。
 *
 * SkParagraph 在 Windows 下按 Unicode 默认断行，**不保证** CSS `line-break: strict` 级别：
 * 实测「）」可落到行首。这里用 WORD JOINER（U+2060，零宽、不占位）把「闭标点」粘到前一个字、
 * 把「开标点」粘到后一个字，断行就不会把它们拆到错误的一侧。
 *
 * 为什么在渲染层做而不是改原文：这是排版需求，不是内容需求；写进数据会让导出的 JSON、
 * 搜索关键字都带上不可见字符。
 */
private val CJK_CLOSING = "，。、；：？！）】》」』”’…％‰℃'" 
private val CJK_OPENING = "（【《「『“‘"
private const val WORD_JOINER = '\u2060'

private fun guardCjkLineBreaks(line: String): String {
    if (line.isEmpty()) return line
    val out = StringBuilder(line.length + 8)
    for ((index, ch) in line.withIndex()) {
        val prev = if (index > 0) line[index - 1] else ' '
        if (ch in CJK_CLOSING && !prev.isWhitespace()) out.append(WORD_JOINER)
        out.append(ch)
        if (ch in CJK_OPENING && index < line.lastIndex && !line[index + 1].isWhitespace()) {
            out.append(WORD_JOINER)
        }
    }
    return out.toString()
}
/** 代码块：整块一个底色容器 + 等宽字体，行间不加底色（逐行加底色会看起来像「很多小灰条」）。 */
@Composable
private fun CodeBlock(code: String, style: TextStyle, textColor: Color) {
    val colors = pocketColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = code.trimEnd(),
            style = LocalTextStyle.current.merge(style).copy(
                fontFamily = FontFamily.Monospace,
                fontSize = (style.fontSize.value - 0.5f).sp,
                color = textColor,
            ),
        )
    }
}