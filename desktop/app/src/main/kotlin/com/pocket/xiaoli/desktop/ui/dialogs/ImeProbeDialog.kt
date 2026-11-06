package com.pocket.xiaoli.desktop.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

/**
 * 输入法自检（`--demo=ime` 或 我的 → 数据 → 输入法自检）。
 *
 * ## 为什么要有这个
 *
 * Compose Desktop 的中文输入法「组合态」（拼音候选框、上屏时机）没法用自动化测试覆盖：
 * 它取决于系统输入法的 IME 消息，跑不了单测。`memory/03-pitfalls.md` 里那条
 * 「只验证过粘贴中文，没验证候选框」说的就是这件事。
 *
 * 这个弹窗把**判断变成十秒钟的事**：在输入框里用拼音打几个字，看下面三行读数。
 *
 * - **拼音字母有没有跑进输入框**：真的 IME 会把 `nihao` 吃掉、只把「你好」交上来；
 *   如果下面第一行显示 `nihao`，说明输入法没生效或组合态没被拦截。
 * - **上屏是不是一次到位**：`变更次数` 在打完一个词后才 +1 才是对的；每个字母 +1 说明
 *   字符被逐字塞进来，IME 组合态没工作。
 * - **删除/回退**：Backspace 应该一次删掉一整个组合或一个字，而不是删到一半。
 */
@Composable
fun ImeProbeDialog(state: AppState) {
    if (!state.showImeProbe) return
    val colors = pocketColors()
    var text by remember { mutableStateOf("") }
    var changes by remember { mutableIntStateOf(0) }
    var lastDelta by remember { mutableStateOf("（还没输入）") }
    // 打开就聚焦输入框：省掉「先点一下」这一步，也让 --demo=ime 的自检脚本不必合成点击
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    PocketModal(
        title = "输入法自检",
        subtitle = "用拼音打几个字，看下面三行对不对；这一步只能人工看一眼",
        onDismiss = { state.showImeProbe = false },
        width = 620.dp,
        footer = {
            SecondaryButton("清空") {
                lastDelta = "（已清空）"
                text = ""
                changes = 0
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton("完成") { state.showImeProbe = false }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "1. 点下面的输入框，切到拼音输入法，打「nihao」再按空格或数字选词。" +
                    " 2. 全选删掉，再打一个词试试 Backspace。 3. 看读数：",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            PocketTextField(
                value = text,
                onValueChange = {
                    lastDelta = "上次写入：" + describeDelta(text, it)
                    text = it
                    changes++
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = "在这里用拼音打几个字",
            )

            Spacer(Modifier.height(2.dp))
            Readout("输入框内容", text.ifEmpty { "（空）" })
            Readout("变更次数", changes.toString())
            Readout("上一次变化", lastDelta)

            Spacer(Modifier.height(4.dp))
            Text(
                "判据：打完一个词只 +1 次、内容里没有英文拼音残渣、Backspace 不删半个字 = 正常。" +
                    " 若不正常，把这三行截图发我，再决定要不要走 Swing 文本框兜底。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        }
    }
}

/** 描述这次变化：是整体替换（IME 上屏）、追加，还是删除。 */
private fun describeDelta(old: String, new: String): String = when {
    new.length > old.length && new.startsWith(old) -> "追加 " + quote(new.removePrefix(old))
    new.length < old.length && old.startsWith(new) -> "删除 " + quote(old.removePrefix(new))
    else -> "整体替换 " + quote(new)
}

private fun quote(text: String): String = if (text.isEmpty()) "（空）" else "「" + text + "」"

@Composable
private fun Readout(label: String, value: String) {
    val colors = pocketColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = PocketTypography.label, color = colors.textTertiary, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = PocketTypography.body.copy(fontFamily = FontFamily.Monospace),
            color = colors.textPrimary,
        )
    }
}
