package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.core.notes.NoteItem
import com.pocket.xiaoli.desktop.logic.noteExcerpt
import com.pocket.xiaoli.desktop.logic.noteTitle
import com.pocket.xiaoli.desktop.logic.sortedNotes
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.deleteNote
import com.pocket.xiaoli.desktop.state.saveNote
import com.pocket.xiaoli.desktop.ui.components.DangerButton
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SectionHeader
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.SettingRow
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 服务页「笔记」分区 —— 从 Android 端 `ui/notes/NoteListScreen` 搬过来的桌面版。
 *
 * 模型与排序/摘要规则都在共享核心 `core/notes`，两端读同一套；这里只重画成桌面的
 * 「列表 + 弹窗编辑」，不做侧栏常驻编辑器（桌面窗口宽，但笔记是"看一眼"的东西，
 * 不该长期占掉半屏）。
 */
@Composable
fun NotesSection(state: AppState, modifier: Modifier = Modifier) {
    var editing by remember { mutableStateOf<NoteItem?>(null) }
    val notes = sortedNotes(state.data.notes)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = "学习笔记",
            trailing = {
                PrimaryButton("新建笔记", icon = PocketIcons.Plus) {
                    editing = NoteItem(id = "note_" + System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                }
            },
        )
        if (notes.isEmpty()) {
            EmptyState(
                icon = PocketIcons.Book,
                title = "还没有笔记",
                hint = "上课记的东西放这里，只存这台电脑",
                action = {
                    SecondaryButton("写第一条", icon = PocketIcons.Plus) {
                        editing = NoteItem(id = "note_" + System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                    }
                },
            )
        } else {
            PocketCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                notes.forEachIndexed { index, note ->
                    SettingRow(
                        title = noteTitle(note),
                        subtitle = noteSubtitle(note),
                        icon = PocketIcons.Folder,
                        onClick = { editing = note },
                    )
                    if (index != notes.lastIndex) DividerLine(Modifier.padding(horizontal = 10.dp))
                }
            }
            Text(
                "共 ${notes.size} 条 · 全部在本机",
                style = PocketTypography.label,
                color = pocketColors().textTertiary,
            )
        }
    }

    val current = editing
    if (current != null) {
        NoteEditorDialog(
            note = current,
            onDismiss = { editing = null },
            onSave = { next ->
                state.saveNote(next)
                editing = null
                state.notify("笔记已保存")
            },
            onDelete = {
                state.deleteNote(current.id)
                editing = null
                state.notify("笔记已删除")
            },
        )
    }
}

/** 一行摘要 + 更新时间；正文没写就直说，不要留空白行让人以为笔记丢了。 */
private fun noteSubtitle(note: NoteItem): String {
    val excerpt = noteExcerpt(note)
    val time = formatNoteTime(note.updatedAt)
    return if (excerpt.isBlank()) "还没写内容 · $time" else "$excerpt · $time"
}

private fun formatNoteTime(at: Long): String =
    Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

@Composable
private fun NoteEditorDialog(
    note: NoteItem,
    onDismiss: () -> Unit,
    onSave: (NoteItem) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var content by remember(note.id) { mutableStateOf(note.content) }
    var confirmDelete by remember(note.id) { mutableStateOf(false) }

    PocketModal(
        title = if (note.title.isBlank() && note.content.isBlank()) "新建笔记" else "编辑笔记",
        subtitle = "只存这台电脑，不上传",
        onDismiss = onDismiss,
        width = 560.dp,
        footer = {
            if (confirmDelete) {
                Text("确定删掉这条笔记？", style = PocketTypography.caption, color = pocketColors().danger)
                Spacer(Modifier.width(8.dp))
                DangerButton("确认删除") { onDelete() }
            } else {
                SecondaryButton("删除", icon = PocketIcons.Trash) { confirmDelete = true }
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { onDismiss() }
            PrimaryButton("保存", icon = PocketIcons.Check) {
                onSave(note.copy(title = title.trim(), content = content, updatedAt = System.currentTimeMillis()))
            }
        },
    ) {
        PocketTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = "标题（可留空）",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        PocketTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = "正文",
            singleLine = false,
            minHeight = 240.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
