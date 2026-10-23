package com.pocket.xiaoli.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.EmptyStatePlaceholder
import com.pocket.xiaoli.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class TimeSlotViewModel(
    private val repository: CourseRepository,
    private val container: AppContainer
) : ViewModel() {
    val allTimeSlots: StateFlow<List<TimeSlotEntity>> = repository.allTimeSlots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val classDuration: StateFlow<Int> = container.defaultClassDuration
    val breakDuration: StateFlow<Int> = container.defaultBreakDuration

    fun saveSettings(
        slots: List<TimeSlotEntity>,
        classDur: Int,
        breakDur: Int,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.setDefaultClassDuration(classDur)
                container.setDefaultBreakDuration(breakDur)
                repository.replaceTimeSlots(slots)
            }
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun deleteTimeSlot(slots: List<TimeSlotEntity>, index: Int): List<TimeSlotEntity> {
        val updated = slots.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            // Renumber
            return updated.mapIndexed { i, slot -> slot.copy(slot = i + 1) }
        }
        return updated
    }

    class Factory(
        private val repository: CourseRepository,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TimeSlotViewModel(repository, container) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSlotManagementScreen(
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val vm: TimeSlotViewModel = viewModel(
        factory = TimeSlotViewModel.Factory(app.repository, app.appContainer)
    )
    val savedSlots by vm.allTimeSlots.collectAsStateWithLifecycle()
    val savedClassDur by vm.classDuration.collectAsStateWithLifecycle()
    val savedBreakDur by vm.breakDuration.collectAsStateWithLifecycle()

    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    // Local editing state
    var localSlots by remember { mutableStateOf(savedSlots.sortedBy { it.slot }) }
    var localClassDur by remember { mutableStateOf(savedClassDur.toString()) }
    var localBreakDur by remember { mutableStateOf(savedBreakDur.toString()) }

    // Sync from saved state on first load
    LaunchedEffect(savedSlots, savedClassDur, savedBreakDur) {
        if (localSlots.isEmpty() && savedSlots.isNotEmpty()) {
            localSlots = savedSlots.sortedBy { it.slot }
        }
        if (localClassDur == "0" || localClassDur.isBlank()) {
            localClassDur = savedClassDur.toString()
        }
        if (localBreakDur == "0" || localBreakDur.isBlank()) {
            localBreakDur = savedBreakDur.toString()
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    val colors = LocalPocketXiaoliColors.current

    // Quick-generate state
    var morningHour by remember { mutableIntStateOf(8) }
    var morningMin by remember { mutableIntStateOf(0) }
    var morningCount by remember { mutableIntStateOf(4) }
    var afternoonHour by remember { mutableIntStateOf(14) }
    var afternoonMin by remember { mutableIntStateOf(0) }
    var afternoonCount by remember { mutableIntStateOf(4) }
    var eveningHour by remember { mutableIntStateOf(19) }
    var eveningMin by remember { mutableIntStateOf(0) }
    var eveningCount by remember { mutableIntStateOf(2) }

    fun generateSlots() {
        val classDur = localClassDur.toIntOrNull()?.coerceAtLeast(1) ?: 45
        val breakDur = localBreakDur.toIntOrNull()?.coerceAtLeast(0) ?: 10
        val slots = mutableListOf<TimeSlotEntity>()
        var slotNum = 1

        fun addPeriods(startH: Int, startM: Int, count: Int) {
            var cur = LocalTime.of(startH, startM)
            repeat(count) {
                val end = cur.plusMinutes(classDur.toLong())
                slots.add(TimeSlotEntity(slot = slotNum, startTime = cur.format(formatter), endTime = end.format(formatter)))
                cur = end.plusMinutes(breakDur.toLong())
                slotNum++
            }
        }

        addPeriods(morningHour, morningMin, morningCount)
        if (afternoonCount > 0) addPeriods(afternoonHour, afternoonMin, afternoonCount)
        if (eveningCount > 0) addPeriods(eveningHour, eveningMin, eveningCount)
        localSlots = slots
        Toast.makeText(context, "已生成 ${slots.size} 个时间段，点击保存生效", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("时间段管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, "添加节次", tint = colors.accentMain)
                    }
                    IconButton(onClick = {
                        val classDur = localClassDur.toIntOrNull() ?: 45
                        val breakDur = localBreakDur.toIntOrNull() ?: 10
                        if (classDur <= 0) {
                            Toast.makeText(context, "课程时长必须大于0", Toast.LENGTH_SHORT).show()
                            return@IconButton
                        }
                        vm.saveSettings(
                            slots = localSlots,
                            classDur = classDur,
                            breakDur = breakDur,
                            onSuccess = {
                                Toast.makeText(context, "时间段设置已保存", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }) {
                        Icon(Icons.Filled.Save, "保存", tint = colors.accentMain)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceAlt),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Quick Generate Card ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(skin.spaceLg),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(modifier = Modifier.padding(skin.spaceLg)) {
                        Text(
                            "快捷生成时间段",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(skin.spaceMd))

                        // ── 上午 ──
                        PeriodQuickRow(
                            label = "上午",
                            hour = morningHour,
                            minute = morningMin,
                            onHourChange = { morningHour = it },
                            onMinuteChange = { morningMin = it },
                            count = morningCount,
                            onCountChange = { morningCount = it }
                        )
                        Spacer(Modifier.height(skin.spaceSm))

                        // ── 下午 ──
                        PeriodQuickRow(
                            label = "下午",
                            hour = afternoonHour,
                            minute = afternoonMin,
                            onHourChange = { afternoonHour = it },
                            onMinuteChange = { afternoonMin = it },
                            count = afternoonCount,
                            onCountChange = { afternoonCount = it }
                        )
                        Spacer(Modifier.height(skin.spaceSm))

                        // ── 晚上 ──
                        PeriodQuickRow(
                            label = "晚上",
                            hour = eveningHour,
                            minute = eveningMin,
                            onHourChange = { eveningHour = it },
                            onMinuteChange = { eveningMin = it },
                            count = eveningCount,
                            onCountChange = { eveningCount = it }
                        )

                        Spacer(Modifier.height(skin.spaceMd))
                        HorizontalDivider(color = Divider)
                        Spacer(Modifier.height(skin.spaceMd))

                        // ── 时长设置 ──
                        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
                            OutlinedTextField(
                                value = localClassDur,
                                onValueChange = { v -> localClassDur = v.filter { it.isDigit() } },
                                label = { Text("每节课(分钟)") },
                                placeholder = { Text("45") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(skin.radiusMicro),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentMain,
                                    unfocusedBorderColor = colors.borderDivider
                                )
                            )
                            OutlinedTextField(
                                value = localBreakDur,
                                onValueChange = { v -> localBreakDur = v.filter { it.isDigit() } },
                                label = { Text("课间休息(分钟)") },
                                placeholder = { Text("10") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(skin.radiusMicro),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentMain,
                                    unfocusedBorderColor = colors.borderDivider
                                )
                            )
                        }

                        Spacer(Modifier.height(skin.spaceMd))

                        // ── Generate button ──
                        Button(
                            onClick = { generateSlots() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(skin.radiusChip),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
                        ) {
                            Text("生成时间段", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ── Section header ──
            item {
                Text(
                    "节次时间段列表",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = 8.dp)
                )
            }

            // ── Time slot list ──
            if (localSlots.isEmpty()) {
                item {
                    EmptyStatePlaceholder(
                        icon = Icons.Outlined.Schedule,
                        message = "暂无时间段\n点击右上角 + 添加"
                    )
                }
            } else {
                itemsIndexed(localSlots, key = { _, s -> "slot-${s.slot}" }) { index, slot ->
                    val bgColor = if (index % 2 == 0) colors.surfaceCard else colors.surfaceAlt
                    Surface(color = bgColor, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = skin.spaceLg, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Slot number
                            Text(
                                "第${slot.slot}节",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                modifier = Modifier.width(56.dp)
                            )

                            // Time range
                            Text(
                                "${slot.startTime} - ${slot.endTime}",
                                fontSize = 14.sp,
                                color = colors.accentMain,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )

                            // Delete button
                            IconButton(
                                onClick = {
                                    val updated = vm.deleteTimeSlot(localSlots, index)
                                    localSlots = updated
                                    Toast.makeText(context, "已删除，点击保存生效", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp).invisibleTouchTarget()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    "删除",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (index < localSlots.size - 1) {
                        HorizontalDivider(
                            color = Divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }

            // Quick-add hint
            if (localSlots.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(skin.spaceLg)
                            .clickable { showAddDialog = true },
                        shape = RoundedCornerShape(skin.radiusChip),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceHighlight.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(skin.spaceLg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, null, tint = colors.accentMain, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(skin.spaceSmall))
                            Text("添加新节次", fontSize = 14.sp, color = colors.accentMain, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // ── Add time slot dialog ──
        if (showAddDialog) {
            AddTimeSlotDialog(
                defaultClassDur = localClassDur.toIntOrNull() ?: 45,
                defaultBreakDur = localBreakDur.toIntOrNull() ?: 10,
                existingSlots = localSlots,
                onConfirm = { startTime, endTime ->
                    val newSlot = TimeSlotEntity(
                        slot = localSlots.size + 1,
                        startTime = startTime,
                        endTime = endTime
                    )
                    localSlots = localSlots + newSlot
                    showAddDialog = false
                    Toast.makeText(context, "已添加，点击保存生效", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTimeSlotDialog(
    defaultClassDur: Int,
    defaultBreakDur: Int,
    existingSlots: List<TimeSlotEntity>,
    onConfirm: (startTime: String, endTime: String) -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    // Calculate default start/end times from last slot
    val (defaultStart, defaultEnd) = remember(existingSlots, defaultClassDur, defaultBreakDur) {
        if (existingSlots.isNotEmpty()) {
            try {
                val lastEnd = LocalTime.parse(existingSlots.last().endTime, formatter)
                val start = lastEnd.plusMinutes(defaultBreakDur.toLong())
                val end = start.plusMinutes(defaultClassDur.toLong())
                start.format(formatter) to end.format(formatter)
            } catch (_: Exception) {
                "08:00" to String.format("%02d:%02d", 8 + defaultClassDur / 60, defaultClassDur % 60)
            }
        } else {
            "08:00" to String.format("%02d:%02d", 8 + defaultClassDur / 60, defaultClassDur % 60)
        }
    }

    var startHour by remember { mutableStateOf(try { LocalTime.parse(defaultStart, formatter).hour } catch (_: Exception) { 8 }) }
    var startMin by remember { mutableStateOf(try { LocalTime.parse(defaultStart, formatter).minute } catch (_: Exception) { 0 }) }
    var endHour by remember { mutableStateOf(try { LocalTime.parse(defaultEnd, formatter).hour } catch (_: Exception) { 8 }) }
    var endMin by remember { mutableStateOf(try { LocalTime.parse(defaultEnd, formatter).minute } catch (_: Exception) { 45 }) }

    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "添加第${existingSlots.size + 1}节",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(skin.spaceMd)
            ) {
                Text("开始时间", fontSize = 14.sp, color = colors.textSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPickerField(
                        value = startHour,
                        onValueChange = { startHour = it },
                        range = 0..23,
                        label = "时",
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontSize = 20.sp, color = colors.textPrimary)
                    NumberPickerField(
                        value = startMin,
                        onValueChange = { startMin = it },
                        range = 0..59,
                        label = "分",
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("结束时间", fontSize = 14.sp, color = colors.textSecondary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPickerField(
                        value = endHour,
                        onValueChange = { endHour = it },
                        range = 0..23,
                        label = "时",
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", fontSize = 20.sp, color = colors.textPrimary)
                    NumberPickerField(
                        value = endMin,
                        onValueChange = { endMin = it },
                        range = 0..59,
                        label = "分",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = String.format("%02d:%02d", startHour, startMin)
                    val end = String.format("%02d:%02d", endHour, endMin)
                    val startTotal = startHour * 60 + startMin
                    val endTotal = endHour * 60 + endMin
                    if (endTotal <= startTotal) {
                        Toast.makeText(context, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show()
                    } else {
                        onConfirm(start, end)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PeriodQuickRow(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    count: Int,
    onCountChange: (Int) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.width(32.dp)
        )
        Text("开始", fontSize = 11.sp, color = colors.textTertiary)
        TimeField(hour, onHourChange, "时")
        Text(":", fontSize = 16.sp, color = colors.textPrimary)
        TimeField(minute, onMinuteChange, "分")
        Spacer(Modifier.width(skin.spaceSm))
        Text("节数", fontSize = 11.sp, color = colors.textTertiary)
        OutlinedTextField(
            value = count.toString(),
            onValueChange = { v ->
                val n = v.filter { it.isDigit() }.toIntOrNull() ?: return@OutlinedTextField
                if (n in 0..12) onCountChange(n)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(56.dp),
            shape = RoundedCornerShape(skin.radiusMicro),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.accentMain,
                unfocusedBorderColor = colors.borderDivider
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
        )
    }
}

@Composable
private fun TimeField(value: Int, onValueChange: (Int) -> Unit, label: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = String.format("%02d", value),
        onValueChange = { v ->
            val n = v.filter { it.isDigit() }.toIntOrNull() ?: return@OutlinedTextField
            if (n in 0..59) onValueChange(n)
        },
        singleLine = true,
        label = { Text(label, fontSize = 10.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(56.dp),
        shape = RoundedCornerShape(skin.radiusMicro),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentMain,
            unfocusedBorderColor = colors.borderDivider
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    )
}

@Composable
private fun NumberPickerField(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = String.format("%02d", value),
        onValueChange = { text ->
            val num = text.filter { it.isDigit() }.toIntOrNull()
            if (num != null && num in range) onValueChange(num)
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(skin.radiusMicro),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentMain,
            unfocusedBorderColor = colors.borderDivider
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            textAlign = TextAlign.Center,
            fontSize = 16.sp
        )
    )
}
