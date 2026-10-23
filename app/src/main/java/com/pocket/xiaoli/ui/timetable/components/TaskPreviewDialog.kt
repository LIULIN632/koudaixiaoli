package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskPreviewDialog(
    tasks: List<TaskEntity>,
    repository: CourseRepository,
    schemeId: Long,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }

    val sorted = remember(tasks) {
        tasks.sortedWith(compareBy({ it.completed }, { it.sortOrder }))
    }
    val pending = sorted.filter { !it.completed }
    val done = sorted.filter { it.completed }
    val total = tasks.size
    val completedCount = done.size
    val progress = if (total > 0) completedCount.toFloat() / total else 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("待办任务", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    if (total > 0) {
                        Text(
                            "$completedCount/$total 已完成",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                IconButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, "添加", tint = colors.accentMain)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (total > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = colors.accentMain,
                        trackColor = colors.surfaceAlt,
                    )
                    Spacer(Modifier.height(skin.spaceSm))
                }

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                null,
                                tint = colors.textTertiary.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(skin.spaceSm))
                            Text("暂无任务", color = colors.textTertiary)
                            Text("点击 + 添加", fontSize = 12.sp, color = colors.textTertiary.copy(alpha = 0.6f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(skin.spaceXs)
                    ) {
                        if (pending.isNotEmpty()) {
                            item {
                                Text(
                                    "待完成 (${pending.size})",
                                    fontSize = 11.sp,
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = 2.dp)
                                )
                            }
                            items(pending, key = { it.id }) { task ->
                                TaskPreviewItem(
                                    task = task,
                                    onToggle = {
                                        scope.launch {
                                            repository.updateTask(task.copy(completed = !task.completed))
                                        }
                                    },
                                    onDelete = {
                                        scope.launch { repository.deleteTask(task.id) }
                                    }
                                )
                            }
                        }
                        if (done.isNotEmpty()) {
                            item {
                                Text(
                                    "已完成 (${done.size})",
                                    fontSize = 11.sp,
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = 2.dp)
                                )
                            }
                            items(done, key = { it.id }) { task ->
                                TaskPreviewItem(
                                    task = task,
                                    onToggle = {
                                        scope.launch {
                                            repository.updateTask(task.copy(completed = !task.completed))
                                        }
                                    },
                                    onDelete = {
                                        scope.launch { repository.deleteTask(task.id) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onViewAll) {
                    Text("查看全部", color = colors.accentMain, fontSize = 13.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = colors.textSecondary)
                }
            }
        },
        dismissButton = {}
    )

    if (showAdd) {
        val existingNames = tasks.map { it.name }.distinct()
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            shape = RoundedCornerShape(skin.radiusChip),
            title = { Text("添加任务", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 30) newName = it },
                        label = { Text("任务名称") },
                        placeholder = { Text("如：通过普通话考试") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (existingNames.isNotEmpty()) {
                        Text("已有任务", fontSize = 11.sp, color = colors.textTertiary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall),
                            verticalArrangement = Arrangement.spacedBy(skin.spaceXs)
                        ) {
                            existingNames.forEach { name ->
                                SuggestionChip(
                                    onClick = { newName = name },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch {
                                val sortOrder = (tasks.maxOfOrNull { it.sortOrder } ?: -1) + 1
                                repository.insertTask(
                                    TaskEntity(name = newName.trim(), sortOrder = sortOrder, schemeId = schemeId)
                                )
                            }
                            showAdd = false
                        }
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun TaskPreviewItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var showDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusMicro))
            .clickable { onToggle() }
            .padding(horizontal = skin.spaceSmall, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (task.completed) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (task.completed) SuccessGreen else colors.textTertiary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = task.name,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (task.completed) colors.textTertiary.copy(alpha = 0.5f) else colors.textPrimary,
            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // ⚠️ 这一处是"两个失败叠加"：热区 28dp **且** 图标画在 30% 透明度上
        //（12dp 的 × 在 30% 下压白底只有约 1.75:1，几乎看不见）。
        // 热区交给 invisibleTouchTarget()，图标回到正常不透明度与可辨尺寸。
        IconButton(
            onClick = { showDelete = true },
            modifier = Modifier.size(28.dp).invisibleTouchTarget(),
        ) {
            Icon(
                Icons.Outlined.Close, "删除",
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除任务") },
            text = { Text("确定删除「${task.name}」？") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}
