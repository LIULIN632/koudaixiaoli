package com.pocket.xiaoli.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.util.HolidaySchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 调休补课设置：放假通知只公布「哪天补班」，不公布「补哪天的课」，由用户手动指定。
 * 配置后课表会在补班日按目标星期的课表渲染，放假列整列不显示课程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustedWorkdayScreen(container: AppContainer, onBack: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val mappings by container.specialWorkDays.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调休补课", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceAlt)
        ) {
            Text(
                "放假通知只公布「哪天补班」，不会公布「补哪天的课」。在这里指定后，课表会在补班日按目标星期的课表渲染；放假当天的课自动不显示。",
                fontSize = 12.sp,
                color = colors.textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = 12.dp)
            )

            if (mappings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有配置调休补课", color = colors.textTertiary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(mappings.sortedBy { it.originDate }, key = { it.originDate }) { item ->
                        AdjustedWorkdayRow(
                            originDate = item.originDate,
                            targetDate = item.targetDate,
                            onDelete = { pendingDelete = item.originDate }
                        )
                    }
                }
            }

            Button(
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(skin.spaceLg),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(skin.spaceSmall))
                Text("添加调休补课")
            }
        }
    }

    if (showAdd) {
        AddAdjustedWorkdayDialog(
            onDismiss = { showAdd = false },
            onConfirm = { origin, target ->
                if (origin == target) {
                    Toast.makeText(context, "补班日与目标日期不能相同", Toast.LENGTH_SHORT).show()
                } else {
                    container.saveSpecialWorkDay(origin, target)
                    showAdd = false
                    Toast.makeText(context, "已保存：$origin 按 $target 的课表", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    pendingDelete?.let { origin ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条调休补课？") },
            text = { Text("$origin 将恢复为按当天星期上课") },
            confirmButton = {
                TextButton(onClick = {
                    container.deleteSpecialWorkDay(origin)
                    pendingDelete = null
                }) {
                    Text("删除", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AdjustedWorkdayRow(
    originDate: String,
    targetDate: String,
    onDelete: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(color = colors.surfaceCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$originDate ${weekdayText(originDate)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Text(
                    "按 $targetDate ${weekdayText(targetDate)} 的课表上课",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "删除", tint = colors.textTertiary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAdjustedWorkdayDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var origin by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加调休补课") },
        text = {
            Column {
                Text("补班日", fontSize = 12.sp, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceSmall))
                DatePickerField(value = origin, placeholder = "选择补班日（如周六）") { origin = it }
                Spacer(Modifier.height(skin.spaceCompact))
                Text("按哪天的课表上课", fontSize = 12.sp, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceSmall))
                DatePickerField(value = target, placeholder = "选择目标日期（如周三）") { target = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = origin.isNotBlank() && target.isNotBlank(),
                onClick = { onConfirm(origin, target) }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    placeholder: String,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(value.ifBlank { placeholder }, fontSize = 13.sp)
    }

    if (open) {
        val initialMillis = runCatching { LocalDate.parse(value) }.getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

private fun weekdayText(date: String): String =
    runCatching { LocalDate.parse(date) }.getOrNull()
        ?.let { HolidaySchedule.weekdayLabel(it.dayOfWeek.value) }
        .orEmpty()
