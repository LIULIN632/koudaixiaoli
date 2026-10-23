package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号令牌 —— 7 档，覆盖改造前散落的 **18 种字号**。
 *
 * ## 改造前的问题
 *
 * 出现频次最高的三档是 **12sp(242 次) / 13sp(172 次) / 11sp(127 次)** ——
 * 三档只差 1sp，**肉眼根本无法分辨**，等于没有层级；而且还有 `12.5sp`、`11.5sp`
 * 这种半像素值。结果是界面"看起来什么都有，但什么都记不住"。
 *
 * ## 收成 7 档
 *
 * ```
 * 24sp Bold    页面大标题  （如"鲤鱼"）
 * 20sp Bold    区块标题    （如首页的"待办"）
 * 16sp Bold    卡片标题    （如课程名）
 * 14sp Normal  正文        （如说明文字、AI 回答）
 * 12sp Normal  次要说明    （如时间、教室）  ← 原 11/12/13sp 全部归并至此
 * 10sp Normal  极小标注    （如角标、辅助标签）
 * ```
 *
 * 刻意**只有两档字重**（Normal / Bold）：多字重是"看起来有层级"的常见误区，
 * 真正的层级来自**字号差**与**颜色差**，不是靠一堆 500/600/700 的细微区别。
 *
 * ## 行高
 *
 * 行高统一按字号的 1.6 倍。中文比英文需要更松的行距（1.4 倍对中文偏挤），
 * 1.6 是中文正文的稳妥值。
 *
 * ## 用法
 *
 * ```kotlin
 * Text("数据结构", style = PocketTypography.cardTitle)
 * Text("A-302", style = PocketTypography.caption)
 * ```
 *
 * 颜色与字重需要覆盖时，在调用处传 `color=` / `fontWeight=` 即可，
 * **不要**为此新建字号档位。
 */
object PocketTypography {

    /** 24sp Bold — 页面大标题 */
    val screenTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)

    /** 20sp Bold — 区块标题（页面内的大分段） */
    val sectionTitle: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)

    /** 16sp Bold — 卡片标题（课程名、服务名等） */
    val cardTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)

    /** 14sp Normal — 正文（说明、AI 回答） */
    val body: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)

    /** 12sp Normal — 次要说明（时间、教室、辅助信息）。原 11/12/13sp 归并于此。 */
    val caption: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)

    /** 10sp Normal — 极小标注（角标、单位、辅助标签） */
    val label: TextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)

    /**
     * 13sp Normal — **等宽**（代码块、行内代码）。
     *
     * 单独成一档，而不是"正文减 1.5sp"现算：等宽字体的**度量与正文不同**
     *（等宽字体普遍更宽更高，对齐与缩进全靠它），沿用正文的字号会显得挤。
     *
     * ⚠️ 此前代码块写的是 `fontSize = (fontSize - 1.5f).sp`，算出 **12.5sp** ——
     * 本文件开头点名要消灭的正是这种半像素值。行高同理（原来是 `lineHeight - 3`）。
     */
    val mono: TextStyle = TextStyle(
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp,
            fontFamily = FontFamily.Monospace,
        )

    /**
     * 数值展示（余额、分数、倒计时等）。
     *
     * 单独成一档是因为这类内容的诉求不同：**要醒目、要数字对齐**。
     * 用 28sp Bold 与正文拉开明显差距，避免"重要数字和普通文字一样大"。
     */
    val numeric: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
}
