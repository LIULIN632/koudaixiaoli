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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.ui.components.ConfirmDialog
import com.pocket.xiaoli.desktop.ui.components.DangerButton
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

/**
 * 待办详情（全局搜索点进来时用）。
 *
 * 桌面端原来只有「新建待办」弹窗，点搜索结果没有地方可落；这里补一个可看、可完成、
 * 可删除的详情，删除仍走二次确认。
 */
@Composable
fun TodoDetailDialog(state: AppState) {
    val todo = state.selectedTodo ?: return
    val colors = pocketColors()
    var confirmDelete by remember(todo.id) { mutableStateOf(false) }

    PocketModal(
        title = if (todo.done) "待办 · 已完成" else "待办",
        subtitle = todo.due.takeIf { it.isNotBlank() }?.let { "截止 $it" } ?: "没有截止日期",
        onDismiss = { state.selectedTodo = null },
        footer = {
            DangerButton("删除") { confirmDelete = true }
            Spacer(Modifier.weight(1f))
            SecondaryButton("关闭") { state.selectedTodo = null }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(if (todo.done) "标记未完成" else "标记完成") {
                state.toggleTodo(todo.id)
                state.selectedTodo = state.data.todos.firstOrNull { it.id == todo.id }
                state.notify(if (todo.done) "已标记为未完成" else "已标记为完成")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(todo.title, style = PocketTypography.sectionTitle, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TagChip(
                    if (todo.done) "已完成" else "未完成",
                    color = if (todo.done) colors.success else accentColor(),
                    background = if (todo.done) colors.success.copy(alpha = 0.14f) else accentSoftColor(),
                )
                when (todo.priority) {
                    2 -> TagChip("紧急", color = colors.danger, background = colors.danger.copy(alpha = 0.12f))
                    1 -> TagChip("重要", color = colors.warning, background = colors.warning.copy(alpha = 0.12f))
                }
            }
            if (todo.courseName.isNotBlank()) {
                InfoLine("关联课程", todo.courseName)
            }
            if (todo.due.isNotBlank()) {
                InfoLine("截止日期", todo.due)
            }
            if (todo.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(todo.note, style = PocketTypography.body, color = colors.textSecondary)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除待办",
            message = "「${todo.title}」将被删除，不能恢复。",
            onConfirm = {
                state.deleteTodo(todo.id)
                state.selectedTodo = null
                state.notify("待办已删除")
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = pocketColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = PocketTypography.caption, color = colors.textTertiary, modifier = Modifier.width(72.dp))
        Text(value, style = PocketTypography.body, color = colors.textPrimary)
    }
}
