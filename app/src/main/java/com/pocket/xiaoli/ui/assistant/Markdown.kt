package com.pocket.xiaoli.ui.assistant

/**
 * 行内样式：加粗 / 斜体 / 行内代码 / 删除线。
 */
data class MdInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    /** 非空表示这段是链接（或裸 URL），值是目标地址。 */
    val link: String? = null,
    /** 该段是否由本解析器加上的视觉标记（列表圆点等），渲染时不需要再标注。 */
    val marker: Boolean = false,
)

/** 行内内容的解析结果：若干带样式片段 + 其中的链接（用于点击跳转）。 */
data class MdRun(
    val spans: List<MdInline>,
    val links: List<Pair<String, String>> = emptyList(),
) {
    companion object {
        fun plain(text: String) = MdRun(listOf(MdInline(text)))
    }
}

/**
 * 块级元素。
 *
 * 刻意做成**封闭的小集合**而不是去追 CommonMark：这是聊天回复的渲染器，
 * 不是 Markdown 引擎。系统提示词已经要求模型"不要输出表格"，所以表格不实现。
 */
sealed interface MdBlock {
    data class Paragraph(val run: MdRun) : MdBlock

    /** [level] 1..6。 */
    data class Heading(val level: Int, val run: MdRun) : MdBlock

    /** 列表项。[ordered] 为真时 [index] 是序号（从 [MdParser] 给定的起始值递增）。 */
    data class ListItem(val ordered: Boolean, val index: Int, val indent: Int, val run: MdRun) : MdBlock

    /** 围栏代码块。[language] 是 ``` 后面那串（可能为空），目前不用于高亮，只留给将来。 */
    data class CodeBlock(val language: String, val code: String) : MdBlock

    /** 引用块，内容已经**递归**解析过（引用里可以有列表、代码块）。 */
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** 分隔线 `---` / `***` / `___`。 */
    data object Divider : MdBlock
}

/**
 * 相邻两个块之间应当留多大的间距。
 *
 * ## 为什么不能所有块用一个间距
 *
 * 改造前 `MarkdownText` 是 `Column(verticalArrangement = spacedBy(6.dp))` ——
 * **列表项之间、段落之间、代码块之前，一律 6dp**。后果是两头都错：
 *
 * - 列表项之间 6dp ⇒ 列表被读成"一串独立段落"，看不出它们是一组；
 * - 代码块与正文之间只有 6dp ⇒ 代码块像正文的一部分，不像"引进来的一段"。
 *
 * 段间距本身没有绝对正确答案，但**相对关系**有：同组要紧、跨块要松。
 */
enum class MarkdownGap {
    /** 同一列表内的相邻项 —— 贴紧。 */
    TIGHT,

    /** 常规段落之间。 */
    NORMAL,

    /** 代码块 / 引用块 / 分隔线 与相邻内容之间 —— 拉开，让"这是一整块"立得住。 */
    LOOSE,
}

/**
 * 相邻两块的间距档位。**纯函数，可单测**。
 *
 * 判据只看两块的**类型组合**，与文本内容无关 —— 所以它能在不渲染的情况下穷举验证。
 */
internal fun markdownGapOf(prev: MdBlock, next: MdBlock): MarkdownGap = when {
    // 同一列表内的相邻项（无序的 `·` 或有序的 `1.`）
    prev is MdBlock.ListItem && next is MdBlock.ListItem -> MarkdownGap.TIGHT
    // 独立块与相邻内容之间
    prev is MdBlock.CodeBlock || next is MdBlock.CodeBlock -> MarkdownGap.LOOSE
    prev is MdBlock.Quote || next is MdBlock.Quote -> MarkdownGap.LOOSE
    prev is MdBlock.Divider || next is MdBlock.Divider -> MarkdownGap.LOOSE
    else -> MarkdownGap.NORMAL
}

/**
 * 助手回答的 Markdown 解析器。**纯逻辑、无 Compose 依赖**，因此可以单测。
 *
 * ## 为什么自己写
 *
 * 项目约束是"禁止第三方依赖"，而 Compose 的 `AnnotatedString` 本身只接受**样式区间**，
 * 不认 Markdown。所以"把 Markdown 变成带样式的区间"这件事必须自己做。
 * 需要覆盖的语法其实很少：模型输出的是**聊天气泡里的短文本**，
 * 加粗、列表、行内代码、代码块占了绝大多数场景。
 *
 * ## 与"流式到达"的关系（这是与通用解析器最大的不同）
 *
 * 解析器每次拿到的都是**半截文本**：`**加粗` 的右半个星号、只写了一半的代码围栏、
 * 末尾孤零零的 `[标题](` 都会真实出现。所以有一条硬规则：
 *
 * > **凡是不成对 / 不完整的标记，一律按普通字符原样显示，绝不报错、绝不吞字符。**
 *
 * 反例是把 `**bold` 解析成"一个空的加粗片段"，那会让用户看到文字**凭空消失**，
 * 而且下一帧右半个星号到达时又要重排一次 —— 观感上是闪烁。原样显示则只在
 * 右半个星号到达的瞬间变一次样式，且不会丢字。
 *
 * 唯一的例外是未闭合的代码围栏：它必须**照常进入代码块模式**，否则围栏之后的内容
 * 会以普通正文渲染，等闭合围栏到达时整段重新排版（一次巨大的跳动）。
 */
object MdParser {

    /** 解析一段（可能不完整的）Markdown 文本。 */
    fun parse(text: String): List<MdBlock> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<MdBlock>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        // 正在累积的段落行（惰性建段落：连续非空行合并成一段）
        val paragraph = mutableListOf<String>()
        // 正在累积的引用行（去掉 '>' 之后的内容）
        val quote = mutableListOf<String>()
        // 围栏代码块状态
        var fence: String? = null
        val fenceLang = StringBuilder()
        val code = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            out += MdBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
            paragraph.clear()
        }

        fun flushQuote() {
            if (quote.isEmpty()) return
            out += MdBlock.Quote(parse(quote.joinToString("\n")))
            quote.clear()
        }

        fun flushAll() {
            flushParagraph()
            flushQuote()
        }

        for (raw in lines) {
            // ── 围栏代码块内部：只有"闭合围栏"有意义，其余一律原样收 ──
            val activeFence = fence
            if (activeFence != null) {
                if (isFenceLine(raw) && raw.trimStart().startsWith(activeFence)) {
                    out += MdBlock.CodeBlock(fenceLang.toString().trim(), code.toString().trimEnd('\n'))
                    fence = null
                    fenceLang.clear()
                    code.clear()
                } else {
                    code.append(raw).append('\n')
                }
                continue
            }

            // ── 遇到新的围栏：开始代码块（未闭合也进，见类注释） ──
            if (isFenceLine(raw)) {
                flushAll()
                fence = raw.trimStart().take(3)
                fenceLang.clear()
                fenceLang.append(raw.trimStart().removePrefix(fence).trim())
                continue
            }

            val trimmed = raw.trim()

            // ── 空行：结束当前累积 ──
            if (trimmed.isEmpty()) {
                flushAll()
                continue
            }

            // ── 引用块 ──
            if (trimmed.startsWith(">")) {
                flushParagraph()
                quote += trimmed.removePrefix(">").removePrefix(" ")
                continue
            }
            flushQuote()

            // ── 分隔线（必须在列表判断之前：`***` 不是列表项） ──
            if (isDivider(trimmed)) {
                flushParagraph()
                out += MdBlock.Divider
                continue
            }

            // ── 标题 ──
            val headingLevel = headingLevel(trimmed)
            if (headingLevel > 0) {
                flushParagraph()
                out += MdBlock.Heading(headingLevel, parseInline(trimmed.drop(headingLevel).trim()))
                continue
            }

            // ── 列表项 ──
            val item = parseListItem(raw)
            if (item != null) {
                flushParagraph()
                out += item
                continue
            }

            // ── 普通段落行 ──
            paragraph += trimmed
        }

        // 收尾：未闭合的围栏按"已开始的代码块"落地（绝不能把它吞掉）
        val danglingFence = fence
        if (danglingFence != null) {
            out += MdBlock.CodeBlock(fenceLang.toString().trim(), code.toString().trimEnd('\n'))
        }
        flushAll()

        return out
    }

    /** 解析行内样式，返回带样式片段与其中的链接。 */
    fun parseInline(text: String): MdRun {
        val spans = mutableListOf<MdInline>()
        val links = mutableListOf<Pair<String, String>>()

        fun emit(span: MdInline) {
            // 相邻同样式段合并：减少 AnnotatedString 的区间数量（长回答上这是实打实的开销）
            val last = spans.lastOrNull()
            if (last != null && last.marker == span.marker && last.bold == span.bold &&
                last.italic == span.italic && last.code == span.code &&
                last.strike == span.strike && last.link == span.link
            ) {
                spans[spans.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                spans += span
            }
        }

        var i = 0
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isNotEmpty()) {
                emit(MdInline(plain.toString()))
                plain.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]
            when {
                // ── 反斜杠转义：\* 显示为 * ──
                c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                    plain.append(text[i + 1]); i += 2
                }

                // ── 行内代码：必须找到同长度的闭合反引号，否则原样显示 ──
                c == '`' -> {
                    val ticks = countRun(text, i, '`')
                    val close = findRun(text, i + ticks, '`', ticks)
                    if (close < 0) {
                        repeat(ticks) { plain.append('`') }
                        i += ticks
                    } else {
                        flushPlain()
                        emit(MdInline(text.substring(i + ticks, close).trim(), code = true))
                        i = close + ticks
                    }
                }

                // ── 删除线 ~~x~~ ──
                c == '~' && i + 1 < text.length && text[i + 1] == '~' -> {
                    val close = text.indexOf("~~", i + 2)
                    if (close < 0) {
                        plain.append("~~"); i += 2
                    } else {
                        flushPlain()
                        parseInline(text.substring(i + 2, close)).spans.forEach { emit(it.copy(strike = true)) }
                        i = close + 2
                    }
                }

                // ── 强调：* / ** / _ / __ ──
                c == '*' || c == '_' -> {
                    val len = if (i + 1 < text.length && text[i + 1] == c) 2 else 1
                    val close = findEmphasisClose(text, i + len, c, len)
                    if (close < 0) {
                        // 不完整：原样显示（流式下这是常态，见类注释）
                        repeat(len) { plain.append(c) }
                        i += len
                    } else {
                        flushPlain()
                        val inner = text.substring(i + len, close)
                        val nested = parseInline(inner)
                        links += nested.links
                        nested.spans.forEach { s ->
                            // 外层是双星号时，即使内层已经是斜体也要再套一层加粗
                            emit(if (len == 2) s.copy(bold = true) else s.copy(italic = true))
                        }
                        i = close + len
                    }
                }

                // ── 链接 [文字](地址) ──
                c == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    val openParen = if (closeBracket >= 0) text.indexOf('(', closeBracket + 1) else -1
                    val closeParen = if (openParen >= 0 && openParen == closeBracket + 1) text.indexOf(')', openParen + 1) else -1
                    if (closeBracket < 0 || closeParen < 0) {
                        // 不完整（流式下常见：`[标题](` 刚到一半）→ 原样显示
                        plain.append(c); i++
                    } else {
                        flushPlain()
                        val label = text.substring(i + 1, closeBracket)
                        val url = text.substring(openParen + 1, closeParen).trim()
                        links += label to url
                        parseInline(label).spans.forEach { emit(it.copy(link = url)) }
                        i = closeParen + 1
                    }
                }

                // ── 裸 URL：只有 http(s) 才认，避免把普通点号文本误判成链接 ──
                c == 'h' && (matchesAt(text, i, "https://") || matchesAt(text, i, "http://")) -> {
                    var end = i
                    while (end < text.length && !text[end].isWhitespace()) end++
                    // 结尾常见的中文标点不该算进地址
                    while (end > i && text[end - 1] in TRAILING_PUNCT) end--
                    val url = text.substring(i, end)
                    flushPlain()
                    links += url to url
                    emit(MdInline(url, link = url))
                    i = end
                }

                else -> {
                    plain.append(c); i++
                }
            }
        }
        flushPlain()
        return MdRun(spans, links)
    }

    // ── 行级判定小工具 ──

    private fun isFenceLine(line: String): Boolean {
        val t = line.trimStart()
        if (!(t.startsWith("```") || t.startsWith("~~~"))) return false
        // 围栏里不能有别的反引号（否则那是行内代码）
        val ch = t[0]
        val run = countRun(t, 0, ch)
        return run >= 3 && !t.drop(run).contains(ch)
    }

    private fun isDivider(t: String): Boolean {
        if (t.length < 3) return false
        val ch = t[0]
        if (ch != '-' && ch != '*' && ch != '_') return false
        return t.all { it == ch || it == ' ' } && t.count { it == ch } >= 3
    }

    private fun headingLevel(t: String): Int {
        var n = 0
        while (n < t.length && n < 6 && t[n] == '#') n++
        if (n == 0) return 0
        // `#标题` 不算标题（CommonMark 要求 # 后有空格），避免把 `#1` 这种当标题
        if (n < t.length && t[n] != ' ') return 0
        return n
    }

    private fun parseListItem(raw: String): MdBlock.ListItem? {
        val indent = (raw.length - raw.trimStart().length) / 2
        val t = raw.trimStart()
        // 无序：- * + 后面必须跟空格
        if (t.length >= 2 && (t[0] == '-' || t[0] == '*' || t[0] == '+') && t[1] == ' ') {
            return MdBlock.ListItem(ordered = false, index = 0, indent = indent, run = parseInline(t.drop(2).trim()))
        }
        // 有序：数字 + . 或 ) + 空格
        var d = 0
        while (d < t.length && t[d].isDigit()) d++
        if (d in 1..9 && d + 1 < t.length && (t[d] == '.' || t[d] == ')') && t[d + 1] == ' ') {
            val num = t.substring(0, d).toIntOrNull() ?: return null
            return MdBlock.ListItem(ordered = true, index = num, indent = indent, run = parseInline(t.drop(d + 2).trim()))
        }
        return null
    }

    /**
     * 找强调的闭合标记。
     *
     * 两个细节很关键（都是"看起来能用但实际会错"的地方）：
     * - `**` 的闭合必须是 `**`，不能把 `*` 当闭合（否则 `**a*b**` 会算错）
     * - 闭合标记不能紧跟在空格后、其前面也不能是空格，避免把 `a * b * c` 这种
     *   数学写法误判成斜体（CommonMark 的左右侧定界规则简化版）
     */
    private fun findEmphasisClose(text: String, from: Int, marker: Char, len: Int): Int {
        var i = from
        while (i < text.length) {
            if (text[i] == marker) {
                val run = countRun(text, i, marker)
                if (run >= len) {
                    val before = if (i > 0) text[i - 1] else ' '
                    if (!before.isWhitespace()) return i
                    // 前面是空白：跳过整段连续标记，继续找
                    i += run
                    continue
                }
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    private fun countRun(text: String, from: Int, ch: Char): Int {
        var n = 0
        while (from + n < text.length && text[from + n] == ch) n++
        return n
    }

    private fun findRun(text: String, from: Int, ch: Char, len: Int): Int {
        var i = from
        while (i < text.length) {
            if (text[i] == ch && countRun(text, i, ch) >= len) return i
            i++
        }
        return -1
    }

    private fun matchesAt(text: String, at: Int, token: String): Boolean =
        at + token.length <= text.length && text.regionMatches(at, token, 0, token.length)

    private val ESCAPABLE = "\\`*_{}[]()#+-.!~>|".toSet()
    private val TRAILING_PUNCT = "。，、；：！？）】》”』.,;:!?)]}\"'".toSet()
}
