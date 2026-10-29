package com.pocket.xiaoli.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.model.NoteItem
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.util.relativeSinceLabel
import java.util.UUID
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val notes by container.notes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<NoteItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun openNew() {
        editing = NoteItem(id = UUID.randomUUID().toString())
        showEditor = true
    }

    Scaffold(
        topBar = {
            ServicePageTopBar(
                title = "学习笔记",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { openNew() }) {
                        Icon(Icons.Default.Add, "新建笔记", tint = colors.accentMain)
                    }
                }
            )
        },
    ) { padding ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(colors.surfaceBase),
                contentAlignment = Alignment.Center,
            ) {
                ServiceEmptyCard(
                    icon = Icons.Outlined.EditNote,
                    title = "还没有笔记",
                    message = "课堂要点先记在这里。笔记只存在这台手机上，不上传。",
                    accent = ServiceAccent.STUDY,
                    modifier = Modifier.padding(horizontal = skin.spaceLg),
                    actionLabel = "新建笔记",
                    onAction = { openNew() },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(colors.surfaceBase),
                contentPadding = PaddingValues(
                    start = skin.spaceLg,
                    end = skin.spaceLg,
                    top = skin.spaceMd,
                    bottom = skin.spaceXxl,
                ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = {
                            editing = note
                            showEditor = true
                        },
                        onDelete = { container.deleteNote(note.id) },
                        // 删除后滑出并让位（依赖上面的 key）
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showEditor) {
        val note = editing ?: return
        NoteEditorDialog(
            initial = note,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                container.saveNote(updated)
                showEditor = false
            },
        )
    }
}

@Composable
internal fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(
        modifier = modifier.fillMaxWidth(),
        // 整卡可点打开编辑器：AppCard 自带 clip + MIUIX 按压回弹，
        // 不再需要外面手写一遍 clickable + clip。
        onClick = onClick,
    ) {
        Row(modifier = Modifier.padding(skin.cardPadding), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        note.title.ifBlank { "无标题笔记" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (note.content.isNotBlank()) {
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        note.content,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(skin.spaceSmall))
                // 相对时间走全项目那一份实现（`util/RelativeTime.kt`）：
                // "3 分钟前"比"10-29 15:40"更快回答"这条还新不新"，超过 30 天它自己退回日期。
                Text(
                    relativeSinceLabel(note.updatedAt, ""),
                    fontSize = 10.sp,
                    color = colors.textTertiary,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                Icon(Icons.Outlined.Delete, "删除笔记", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NoteEditorDialog(
    initial: NoteItem,
    onDismiss: () -> Unit,
    onSave: (NoteItem) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    var title by remember { mutableStateOf(initial.title) }
    var content by remember { mutableStateOf(initial.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.title.isBlank() && initial.content.isBlank()) "新建笔记" else "编辑笔记",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            content = content.trim(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                },
                enabled = title.isNotBlank() || content.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
