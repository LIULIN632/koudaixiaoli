package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import kotlinx.coroutines.delay

/**
 * 把一段（可能仍在流式增长的）Markdown 渲染成气泡内容。
 *
 * 两个刻意的性能取舍：
 *
 * 1. **解析结果用 `remember(text)` 缓存**。流式期间每次重组都会重跑解析，
 *    而长回答（几千字）的解析是实打实的开销。文本没变时直接复用上一次的块列表 ——
 *    这与"流式合并"是配套的：合并把重组次数压到每 50ms 一次，缓存再把
 *    "没变化的那些重组"的解析开销压到零。
 *
 * 2. **一行文本只包一个 `Text`**，而不是每个片段一个 `Text`。
 *    `AnnotatedString` 的意义就在于此：换行、缩进、选中、无障碍朗读都由一个
 *    text layout 负责，片段多了反而更慢且会破坏换行。
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    /**
     * 正文样式。默认取 [PocketTypography.body]。
     *
     * ⚠️ 改造前这里是 `fontSize: Int = 14, lineHeight: Int = 22` 两个字面默认值 ——
     * **数值恰好等于 body 档**，但只存在于这一行，改排版的人不会想到来这里找。
     * 现在取值只有一个来源。
     */
    style: TextStyle = PocketTypography.body,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val textColor = color ?: colors.textPrimary
    val blocks = remember(text) { MdParser.parse(text) }
    // 块级派生尺寸（标题要放大、代码要缩一号）仍然需要数值，所以从 TextStyle 里取出来
    val fontSize = style.fontSize.let { if (it.isSpecified) it.value else 14f }
    val lineHeight = style.lineHeight.let { if (it.isSpecified) it.value else 22f }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) {
                // 块间距按「前一块 + 这一块」的类型组合决定 —— 见 MarkdownGap 的说明。
                // 改造前是一律 6dp：列表被读成一串独立段落、代码块像正文的一部分。
                Spacer(Modifier.height(markdownGapOf(blocks[index - 1], block).toGapDp()))
            }
            MdBlockView(block, textColor, fontSize, lineHeight)
        }
    }
}

/**
 * 间距档位 → 具体数值。集中在一处，避免映射散开。
 *
 * ⚠️ 刻意**不接收 `SkinTokens` 参数**，而是在内部读 `LocalSkin.current`：
 * 传参意味着调用方可能把**另一套皮肤**的令牌传进来（`tools/check_skin_types.py`
 * 专门守这条约定）。`TurnGap.toDp()` 也是同样写法。
 */
@Composable
private fun MarkdownGap.toGapDp() = when (this) {
    MarkdownGap.TIGHT -> LocalSkin.current.spaceTiny
    MarkdownGap.NORMAL -> LocalSkin.current.spaceSm
    MarkdownGap.LOOSE -> LocalSkin.current.spaceCompact
}

@Composable
private fun MdBlockView(block: MdBlock, textColor: Color, fontSize: Float, lineHeight: Float) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    when (block) {
        is MdBlock.Paragraph -> Text(
            text = inline(block.run, textColor, colors.accentMain),
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            color = textColor,
        )

        is MdBlock.Heading -> {
            // 标题层级：一级比正文大 3sp 且加粗，逐级递减，最小不小于正文
            val size = (fontSize + (4 - block.level).coerceAtLeast(0)).coerceAtMost(fontSize + 4)
            Text(
                text = inline(block.run, textColor, colors.accentMain),
                fontSize = size.sp,
                lineHeight = (size + 7).sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.padding(top = skin.spaceMicro),
            )
        }

        is MdBlock.ListItem -> Row(
            modifier = Modifier
                .fillMaxWidth()
                // 二级缩进：让嵌套列表在视觉上真的退一级
                .padding(start = (block.indent.coerceAtMost(4) * 12).dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = if (block.ordered) "${block.index}." else "·",
                fontSize = fontSize.sp,
                lineHeight = lineHeight.sp,
                color = colors.textSecondary,
                modifier = Modifier.width(if (block.ordered) 22.dp else 14.dp),
            )
            Text(
                text = inline(block.run, textColor, colors.accentMain),
                fontSize = fontSize.sp,
                lineHeight = lineHeight.sp,
                color = textColor,
            )
        }

        is MdBlock.CodeBlock -> Surface(
            shape = RoundedCornerShape(skin.radiusChip),
            color = colors.surfaceAlt,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                // 头部：语言标签 + 复制。
                //
                // 为什么要语言标签：模型给的围栏几乎总带语言（```kotlin / ```json），
                // 而这是**免费的上下文** —— 解析器早就在 `CodeBlock.language` 里存下来了
                //（其 KDoc 写着"目前不用于高亮，只留给将来"，现在就是那个将来）。
                // 为什么要复制：整段回答的复制会把列表和解释一起带上，只想要代码时很难用。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = skin.spaceCompact, end = skin.spaceMicro, top = skin.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = block.language.ifBlank { "代码" },
                        style = PocketTypography.label,
                        // textSecondary 而非 textTertiary：后者压在 surfaceAlt 上实测只有
                        // **1.97:1**（见 ThemeContrastTest），标签等于没写。
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    CodeCopyButton(code = block.code)
                }
                Text(
                    text = block.code,
                    modifier = Modifier
                        // 上边距交给头部行 —— 这里再加一个 vertical 会让
                        // "kotlin" 与代码之间空出一大块（头部行本身有 44dp 的复制按钮撑着）
                        .padding(
                            start = skin.spaceCompact,
                            end = skin.spaceCompact,
                            bottom = skin.spaceSm,
                        )
                        // 横向滚动而不是折行：代码的缩进与对齐是语义的一部分，
                        // 折行会把 `if (…)` 的续行和下一行语句混在一起看。
                        .horizontalScroll(rememberScrollState()),
                    // ⚠️ 用 mono 档而不是"正文减 1.5sp"：后者算出 **12.5sp** 半像素值
                    //（`PocketTypography` 的 KDoc 点名要消灭的正是它）。
                    style = PocketTypography.mono,
                    color = colors.textPrimary,
                )
            }
        }

        is MdBlock.Quote -> Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            // 左侧竖线用 accent 的柔和色，比整块底色更轻，符合"马卡龙极简"
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(colors.accentMain.copy(alpha = 0.35f), RoundedCornerShape(1.dp))
            )
            Spacer(Modifier.width(skin.spaceSm))
            Column {
                // 引用内部的块间距同样走 MarkdownGap，避免"外面分层、里面回到老样子"
                block.blocks.forEachIndexed { index, inner ->
                    if (index > 0) {
                        Spacer(
                            Modifier.height(
                                markdownGapOf(block.blocks[index - 1], inner).toGapDp()
                            )
                        )
                    }
                    MdBlockView(inner, colors.textSecondary, fontSize, lineHeight)
                }
            }
        }

        MdBlock.Divider -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = skin.spaceXs)
                .height(1.dp)
                .background(colors.borderDivider)
        )
    }
}

/**
 * 把行内片段拼成一个 [AnnotatedString]。
 *
 * 唯一的坑是**顺序**：样式区间是按字符下标累积的，所以每处理完一个片段就要把游标
 * 往后推它自己的长度。这一点写错时表现很隐蔽 —— 短文本看起来正常，
 * 长了以后样式会整体错位。
 */
internal fun inline(run: MdRun, baseColor: Color, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        run.spans.forEach { span ->
            val start = length
            append(span.text)
            val end = length
            if (end == start) return@forEach

            val style = buildList {
                if (span.bold) add(SpanStyle(fontWeight = FontWeight.SemiBold))
                if (span.italic) add(SpanStyle(fontStyle = FontStyle.Italic))
                if (span.strike) add(SpanStyle(textDecoration = TextDecoration.LineThrough))
                if (span.code) add(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = baseColor.copy(alpha = 0.08f),
                    )
                )
                if (span.link != null) add(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                )
            }
            // addStyle 而不是 addStyle(style)：多个 SpanStyle 各自独立合并，顺序无所谓
            style.forEach { addStyle(it, start, end) }
        }
    }
}

/**
 * 代码块右上角的复制按钮：点一下变勾、一秒后复原。
 *
 * 触摸区取 [AssistantIconTouchSize]（≥44dp）而字形只有 14dp —— 与消息区其它图标
 * 同一条规矩：**触摸区可以比视觉大，但不能比 44dp 小**。
 */
@Composable
private fun CodeCopyButton(code: String) {
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    // 「已复制」是一个**瞬态反馈**：1 秒后自己回到复制图标，不需要用户再点一次
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_000)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .size(AssistantIconTouchSize)
            .clip(CircleShape)
            .clickable {
                copyToClipboard(context, code)
                copied = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (copied) "已复制代码" else "复制代码",
            tint = if (copied) colors.accentMain else colors.textTertiary,
            modifier = Modifier.size(14.dp),
        )
    }
}
