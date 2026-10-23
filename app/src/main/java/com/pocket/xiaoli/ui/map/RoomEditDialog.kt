package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.data.map.RoomDirectorySearch
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import java.util.UUID
import com.pocket.xiaoli.ui.theme.LocalSkin

@Composable
internal fun TextInputDialog(
    title: String,
    label: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                placeholder = { Text(placeholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun RoomEditDialog(
    initial: RoomCell,
    onConfirm: (RoomCell) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val scrollState = rememberScrollState()
    val isAdd = initial.id.isBlank()
    val roomFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isAdd) {
        if (isAdd) roomFocusRequester.requestFocus()
    }
    var advanced by remember {
        mutableStateOf(initial.building.isBlank() || initial.floor.isBlank())
    }
    var building by remember { mutableStateOf(initial.building) }
    var floor by remember { mutableStateOf(initial.floor) }
    var room by remember { mutableStateOf(initial.room) }
    var name by remember { mutableStateOf(initial.name) }
    var note by remember { mutableStateOf(initial.note) }
    var type by remember { mutableStateOf(initial.roomType) }
    var capacity by remember { mutableStateOf(if (initial.capacity > 0) initial.capacity.toString() else "") }
    var rowSpan by remember { mutableStateOf(initial.rowSpan.coerceIn(1, RoomDirectorySearch.MAX_SPAN).toString()) }
    var colSpan by remember { mutableStateOf(initial.colSpan.coerceIn(1, RoomDirectorySearch.MAX_SPAN).toString()) }
    var rowCoord by remember { mutableStateOf(if (initial.row >= 0) initial.row.toString() else "") }
    var colCoord by remember { mutableStateOf(if (initial.col >= 0) initial.col.toString() else "") }

    val locationText = listOf(building, floor)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "未设置位置" }
    val rowLabel = rowCoord.toIntOrNull()?.plus(1)?.toString() ?: "?"
    val colLabel = colCoord.toIntOrNull()?.plus(1)?.toString() ?: "?"
    val coordText = if (rowCoord.isNotBlank() && colCoord.isNotBlank()) {
        "第 " + rowLabel + " 行 · 第 " + colLabel + " 列"
    } else {
        "自动排布"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceMicro)) {
                Text(if (isAdd) "添加教室" else "编辑教室", fontWeight = FontWeight.Bold)
                Text(locationText + " · " + coordText, fontSize = 11.sp, color = colors.textTertiary)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
            ) {
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("房间号 *") },
                    placeholder = { Text("如 S552") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(roomFocusRequester)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("用途（选填）") },
                    placeholder = { Text("如 多媒体教室") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("类型", fontSize = 12.sp, color = colors.textTertiary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall)
                ) {
                    RoomCellType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text("容量（人，选填）") },
                    placeholder = { Text("如 60") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    if (rowCoord.isNotBlank() && colCoord.isNotBlank()) "将添加到当前格子：" + coordText
                    else "未指定坐标，保存后自动排布到空位",
                    fontSize = 10.sp,
                    color = colors.textTertiary
                )

                TextButton(onClick = { advanced = !advanced }) {
                    Text(
                        if (advanced) "收起更多设置" else "更多设置（楼栋 / 坐标 / 备注）",
                        fontSize = 12.sp
                    )
                }

                if (advanced) {
                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        OutlinedTextField(
                            value = building,
                            onValueChange = { building = it },
                            label = { Text("楼栋 *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = floor,
                            onValueChange = { floor = it },
                            label = { Text("楼层 *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("网格占用", fontSize = 12.sp, color = colors.textTertiary)
                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        OutlinedTextField(
                            value = rowSpan,
                            onValueChange = { rowSpan = it.filter { ch -> ch.isDigit() }.take(1) },
                            label = { Text("跨行") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = colSpan,
                            onValueChange = { colSpan = it.filter { ch -> ch.isDigit() }.take(1) },
                            label = { Text("跨列") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        OutlinedTextField(
                            value = rowCoord,
                            onValueChange = { rowCoord = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("行坐标") },
                            placeholder = { Text("自动") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = colCoord,
                            onValueChange = { colCoord = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("列坐标") },
                            placeholder = { Text("自动") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "行 / 列坐标留空 = 自动排布；两者需同时填写",
                        fontSize = 10.sp,
                        color = colors.textTertiary
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注（选填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rs = rowSpan.toIntOrNull()?.coerceIn(1, RoomDirectorySearch.MAX_SPAN) ?: 1
                    val cs = colSpan.toIntOrNull()?.coerceIn(1, RoomDirectorySearch.MAX_SPAN) ?: 1
                    val r = rowCoord.toIntOrNull() ?: -1
                    val c = colCoord.toIntOrNull() ?: -1
                    val hasCoord = r >= 0 && c >= 0
                    onConfirm(
                        initial.copy(
                            id = initial.id.ifBlank { UUID.randomUUID().toString() },
                            building = building.trim(),
                            floor = floor.trim(),
                            room = room.trim(),
                            name = name.trim(),
                            note = note.trim(),
                            type = type.key,
                            capacity = capacity.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                            rowSpan = rs,
                            colSpan = cs,
                            row = if (hasCoord) r else -1,
                            col = if (hasCoord) c else -1
                        ).normalized()
                    )
                },
                enabled = building.isNotBlank() && floor.isNotBlank() && room.isNotBlank()
            ) { Text(if (isAdd) "添加" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
