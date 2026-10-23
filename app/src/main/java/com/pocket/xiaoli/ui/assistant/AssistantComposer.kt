package com.pocket.xiaoli.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// ⚠️ getValue / setValue 是 `by` 委托的**运算符扩展**，源码里从不出现这两个名字 ——
// 纯按符号扫 import 会漏掉它们（拆分时实测就漏了，编译器报
// "Type 'MutableState<Boolean>' has no method 'setValue'"）。凡是用 `by remember` 的文件都要有这两行。
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.glass.reportTextInputFocus
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.onAccentColor

/**
 * 鲤鱼的**底部输入区**。
 *
 * ## 为什么单独一个文件
 *
 * 它原先住在 `AssistantScreen.kt` 里，而那个文件是本项目最大的单文件
 *（拆分前 1704 行 / 25 个顶层声明）。输入区与"整页的滚动跟随、顶栏、会话收尾"
 * 没有任何共享状态 —— 它只是一个"文本 + 发送/停止按钮"的受控组件，
 * 却和整页逻辑挤在一起，改一处要读一整个文件。
 *
 * 纯移动，**逻辑一行未改**。验证方式是既有的 `AssistantComposerScreenshotTest`
 *（`30..33` 四态快照）在移动前后必须逐像素一致。
 */

/**
 * 底部输入区：一个填充式大圆角框，发送 / 停止按钮**嵌在框内右下角**。
 *
 * ## 沿用下来的两条语义
 *
 * - 「生成中变停止」**同一位置、同一个按钮** —— 想打断时的落点永远是同一个地方，找入口成本为 0；
 * - 输入框自身**不画描边**，边界由外层 [Surface] 给 —— 两层各画一次会变成"框套框"。
 *
 * ## ⚠️ 2026-10 改版：修掉"太高太空"与"灰疙瘩按钮"
 *
 * 用户反馈"太难看"。先用 `AssistantComposerScreenshotTest` 渲了四个状态**看图**，
 * 才定位到两个**具体**问题（不是凭感觉调）：
 *
 * 1. **单行也是约 72dp 高的胶囊**。`OutlinedTextField` 自带 `minHeight = 56dp`，
 *    叠上外层 8dp padding 就撑到 72dp —— 里面只有一行字，上下大片死区，
 *    36dp 的发送键被衬得又小又"掉在右下角"。
 * 2. **禁用态是"灰疙瘩"**：`borderDivider` 灰底 + 灰图标，在白底胶囊里糊成一团，
 *    既不像按钮、也读不出图标。
 *
 * 改法：换成 `BasicTextField` + 自定义 `decorationBox`，**高度由我们说了算**
 *（单行 44dp、多行自然长高）；按钮改为**视觉 36dp / 触摸 44dp** 的两层结构。
 * 为什么分两层：直接画 44dp 的圆显得笨重，而直接画 36dp 又会让点击目标低于
 * Material 的 44dp 最小可点尺寸（本项目 lint 已有一条 `TouchTargetSizeCheck` 基线，不再添新的）。
 *
 * ⚠️ 底色是 `surfaceCard` + 一圈描边，**不是**本 KDoc 早先写的"`surfaceAlt` 填充、无描边"。
 * 换掉的原因当时记录过：浅色下 `surfaceAlt`(#F5F5F5) 与页底(#F8FAFC)几乎同色，
 * 输入框"没有边界"、整块发虚；白底 + 一条 `borderDivider` 才立得住。
 */
@Composable
internal fun InputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    var focused by remember { mutableStateOf(false) }
    val canSend = isStreaming || query.isNotBlank()
    val shape = RoundedCornerShape(24.dp)
    // 输入区是浮在消息列表上的：补一层同色柔光，让它的边界不只依赖一条描边。
    // 聚焦时阴影跟着变浓 —— 与上面那条描边一起表达"现在在输入"。
    val composerShadow = if (skin.shadowBlur > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 14.dp,
                spread = 0.dp,
                color = colors.accentMain.copy(
                    alpha = when {
                        focused -> if (isDark) 0.34f else 0.20f
                        else -> if (isDark) 0.24f else 0.11f
                    }
                ),
                offset = DpOffset(0.dp, 4.dp),
            ),
        )
    } else Modifier

    Surface(
        modifier = Modifier.fillMaxWidth().then(composerShadow),
        // 用 24dp 而不是 `RoundedCornerShape(50)`：后者是百分比圆角、恒定胶囊形，
        // 单行时很好看，但多行撑高之后会变成一个怪异的"药丸"。24dp 单行已足够接近胶囊。
        shape = shape,
        color = colors.surfaceCard,
        // 聚焦时描边转主色并加粗半档 —— 否则"正在输入"没有任何视觉反馈。
        border = BorderStroke(
            if (focused) 1.5.dp else 1.dp,
            if (focused) colors.accentMain else colors.borderDivider,
        ),
    ) {
        Row(
            // ⚠️ 按钮侧只留 4dp：按钮自己已经带了 44dp 的触摸区，
            // 外层再给 8dp 只会把胶囊白白撑高（旧版就是这么撑到 72dp 的）。
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            // 多行时按钮贴底（聊天产品的惯例）；单行时两者同为 44dp，等价于居中。
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = !isStreaming,
                maxLines = 5,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(colors.accentMain),
                modifier = Modifier
                    .weight(1f)
                    // 单行高度 = 20dp 行高 + 12×2 = **44dp**，即 Material 的最小可点高度；
                    // 多行时这段只是上下留白，不参与撑高。
                    .padding(vertical = 12.dp)
                    .onFocusChanged { focused = it.isFocused }
                    // 聚焦状态上报给导航层：键盘弹出时把底栏收起来。
                    // 走统一通道（见 ui/glass/BottomBarInset.kt），不再靠参数逐层穿透 ——
                    // 穿透的问题在于只有接了线的那一个输入框会收起底栏。
                    .reportTextInputFocus(),
                decorationBox = { inner ->
                    // 占位文字与真实文本**同一个容器**里居中对齐 ——
                    // 分成两层会在"输入第一个字"时文字位置跳一下。
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = if (isStreaming) "正在回答…" else "问点什么，例如：今天有什么课",
                                fontSize = 14.sp,
                                // textSecondary 而非 textTertiary：后者在白底上实测只有
                                // 约 2.2:1 的对比度（见 `ThemeContrastTest`），淡到像"没渲染出来"，
                                // 这也是"难看"的一部分。
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(skin.spaceXs))
            // 发送 / 停止同体：同一个位置、同一个按钮，内容随状态切换（找入口成本为 0）。
            //
            // 两层结构：外层 44dp 只负责**触摸**，内层 36dp 负责**视觉**。
            // 一层做不到两全 —— 画 44dp 的圆显得笨重，画 36dp 又低于 Material 的
            // 44dp 最小可点尺寸（本项目 lint 已有 `TouchTargetSizeCheck` 基线，不再添新的）。
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canSend) { if (isStreaming) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        // 可用时给按钮一层主色光晕：它在白色胶囊里是唯一的主操作，
                        // 不该和禁用态只差一个颜色。
                        .then(
                            if (canSend) {
                                Modifier.dropShadow(
                                    shape = CircleShape,
                                    shadow = Shadow(
                                        radius = 8.dp,
                                        spread = 0.dp,
                                        color = colors.accentMain.copy(alpha = if (isDark) 0.48f else 0.34f),
                                        offset = DpOffset(0.dp, 2.dp),
                                    ),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .background(
                            // 禁用态不再用 borderDivider 那块灰：它与白底只差一点，
                            // 读起来是"糊掉的一团"。改用 surfaceHighlight 的浅色底 +
                            // textTertiary 图标 —— 明确是"按钮在这儿、现在不可用"。
                            brush = if (canSend) {
                                Brush.linearGradient(
                                    listOf(
                                        lerp(colors.accentMain, Color.White, 0.18f),
                                        colors.accentMain,
                                    )
                                )
                            } else {
                                SolidColor(colors.surfaceHighlight)
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isStreaming) "停止" else "发送",
                        tint = if (canSend) onAccentColor() else colors.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

