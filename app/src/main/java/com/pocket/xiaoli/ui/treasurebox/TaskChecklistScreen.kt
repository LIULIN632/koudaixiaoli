package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.*
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskChecklistScreen(
    repository: CourseRepository,
    schemeId: Long,
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scope = rememberCoroutineScope()

    // WithLifecycle 版本：参数里内联构造的 Room 冷 Flow 每次重组都会重新注册观察者
    val tasks by repository.getTasksByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    val completedCount = tasks.count { it.completed }
    val totalCount = tasks.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "大学任务清单",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加任务", tint = colors.accentMain)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase)
        ) {
            // ⚠️ 2026-10-22：进度卡与「全部任务」标题原来固定在滚动之外、列表是内嵌
            // LazyColumn，往下滑时那张渐变卡一直挂在顶上，第一条任务从它下沿滑出来
            // 被切掉一截 —— 用户报的「卡片悬浮遮挡内容」就是它。现在全部进同一条列表。
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = skin.spaceLg,
                    end = skin.spaceLg,
                    top = skin.spaceSm,
                    bottom = skin.spaceXxl,
                ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceXs)
            ) {
                if (tasks.isEmpty()) {
                    item(key = "empty") {
                        Spacer(Modifier.height(skin.spaceLg))
                        ServiceEmptyCard(
                            icon = Icons.Outlined.Checklist,
                            title = "还没有任务",
                            message = "把想完成的事写下来：考证、比赛、社团、攒钱……完成一项就划掉一项。",
                            accent = ServiceAccent.LIFE,
                            actionLabel = "添加第一个任务",
                            onAction = { showAddDialog = true },
                        )
                    }
                } else {
                    // ── 进度主视觉 ──
                    item(key = "hero") {
                        ServiceHeroCard(accent = ServiceAccent.LIFE) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "任务进度",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                    )
                                    Spacer(Modifier.height(skin.spaceXs))
                                    ServiceHeroCount("$completedCount / $totalCount", "项已完成")
                                }
                                ServiceHeroBadge(Icons.Outlined.Checklist)
                            }

                            Spacer(Modifier.height(skin.spaceLg))

                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(skin.radiusMicro)),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.22f),
                            )

                            Spacer(Modifier.height(skin.spaceSm))

                            Text(
                                if (progress >= 1f) "全部完成，这一页可以翻过去了"
                                else if (progress >= 0.5f) "已经过半，剩下的也没那么难"
                                else if (progress > 0f) "好的开始，按自己的节奏来"
                                else "先挑一件最容易的开始",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }

                    item(key = "label") {
                        ServiceSectionLabel(
                            title = "全部任务",
                            accent = ServiceAccent.LIFE,
                            modifier = Modifier.padding(top = skin.spaceMd, bottom = skin.spaceXs),
                            trailing = {
                                Text(
                                    "已完成 $completedCount",
                                    fontSize = 12.sp,
                                    color = colors.textTertiary,
                                )
                            },
                        )
                    }

                    val sortedTasks = taskDisplayOrder(tasks)
                    items(sortedTasks, key = { it.id }) { task ->
                        TaskItem(
                            task = task,
                            onToggle = {
                                scope.launch {
                                    repository.updateTask(task.copy(completed = !task.completed))
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteTask(task.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val sortOrder = (tasks.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    repository.insertTask(TaskEntity(name = name.trim(), sortOrder = sortOrder, schemeId = schemeId))
                }
                showAddDialog = false
            }
        )
    }
}

/**
 * 列表顺序：**没完成的排前面**，同组内按用户自己排的 `sortOrder`。
 *
 * 2026-10-29 之前是已完成在前 —— 于是主视觉卡刚说完"先挑一件最容易的开始"，
 * 第一屏全是划掉的旧账。这一页的用途是"还剩什么"，不是"我做过多少"。
 * 抽成纯函数是因为这条顺序本身就是**判据**，不该只能靠肉眼看图来守。
 */
internal fun taskDisplayOrder(tasks: List<TaskEntity>): List<TaskEntity> =
    tasks.sortedWith(compareBy({ it.completed }, { it.sortOrder }))

@Composable
internal fun TaskItem(
    task: TaskEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        // 完成项用 surfaceAlt 而不是 50% 透明的白卡：半透明卡压在页底上
        // 会变成一块"脏白"，且与未完成项的区别全靠透明度，不清晰。
        containerColor = if (task.completed) colors.surfaceAlt else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = skin.spaceSm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accentMain,
                    checkmarkColor = onAccentColor(),
                    uncheckedColor = colors.textTertiary
                )
            )
            Text(
                text = task.name,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                // 完成项不再叠透明度：`textTertiary` 本来就是弱化色，再乘 0.6 在深色主题下
                // 几乎读不出来；划掉线已经把"这条做完了"说清楚了。
                color = if (task.completed) colors.textTertiary else colors.textPrimary,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp).invisibleTouchTarget()
            ) {
                Icon(
                    Icons.Outlined.Close,
                    "删除",
                    // 0.4 透明度等于"看不见但点得到"：一个看不见的删除键比没有更危险。
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除任务") },
            text = { Text("确定要删除「${task.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加任务", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 30) name = it },
                label = { Text("任务名称") },
                placeholder = { Text("如：通过普通话考试") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentMain,
                    cursorColor = colors.accentMain
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
