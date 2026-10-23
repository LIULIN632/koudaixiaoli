package com.pocket.xiaoli.ui.profile

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.entity.TermEntity
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.timetable.TimetableViewModel
import com.pocket.xiaoli.util.currentWeekFrom
import com.pocket.xiaoli.util.parseSemesterStart

/**
 * 学期设置页（学期列表 + 当前学期的开学日期 / 总周数 / 自动算出的当前周）。
 *
 * ## 为什么有「学期列表」
 *
 * 原来学期只是两枚全局 SharedPreferences（`semester_start` / `semester_weeks`），换学期 = 覆盖，
 * 历史课表永远回不去。现在学期是一级实体（[TermEntity]），课表方案挂在学期下
 * （[com.pocket.xiaoli.data.entity.SchemeEntity.termId]）—— 这正是岱宗盒子 `termCode` 的做法，
 * 只是从「缓存 key」换成了 Room 行。
 *
 * `SettingsMainScreen` 那份 670 行的旧实现已删除；这一页仍是「学期设置」那一条的目标。
 * `SettingsGroup` / `SettingsMenuItem` 等行组件不在这里，它们住在 `ProfileSettingsScreen.kt`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SemesterSettingsPage(
    container: AppContainer,
    onBack: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val vm: TimetableViewModel =
        viewModel(factory = TimetableViewModel.Factory(container.repository, container))

    val terms by vm.allTerms.collectAsStateWithLifecycle()
    val activeTermId by vm.activeTermId.collectAsStateWithLifecycle()
    val activeTerm: TermEntity? = terms.firstOrNull { it.id == activeTermId }

    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TermEntity?>(null) }

    // 编辑区跟随当前学期：切换学期（id 变化）就重置草稿
    var editName by remember(activeTerm?.id) { mutableStateOf(activeTerm?.name.orEmpty()) }
    var editWeeks by remember(activeTerm?.id) { mutableIntStateOf(activeTerm?.totalWeeks ?: 20) }
    val parsedStart = remember(activeTerm?.id) { parseSemesterStart(activeTerm?.startDate.orEmpty()) }
    val now = java.time.LocalDate.now()
    var year by remember(activeTerm?.id) { mutableIntStateOf(parsedStart?.year ?: now.year) }
    var month by remember(activeTerm?.id) { mutableIntStateOf(parsedStart?.monthValue ?: 2) }
    var day by remember(activeTerm?.id) { mutableIntStateOf(parsedStart?.dayOfMonth ?: 1) }

    val editingStart = "$year-${String.format("%02d", month)}-${String.format("%02d", day)}"
    val autoWeek = remember(year, month, day, editWeeks) {
        try {
            currentWeekFrom(java.time.LocalDate.of(year, month, day), editWeeks)
        } catch (_: Exception) { container.autoCurrentWeek() }
    }

    fun saveCurrent() {
        val target = activeTerm ?: return
        vm.updateTerm(
            target.copy(
                name = editName.trim(),
                startDate = editingStart,
                totalWeeks = editWeeks,
            )
        )
        Toast.makeText(context, "学期设置已保存", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学期设置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { saveCurrent() }) {
                        Text("保存", color = colors.accentMain, fontWeight = FontWeight.Bold)
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
                .verticalScroll(rememberScrollState())
                .padding(skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceLg)
        ) {
            // ── 学期列表：点一行即切换 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
            ) {
                Column(modifier = Modifier.padding(vertical = skin.spaceSm)) {
                    Text(
                        "学期",
                        fontSize = 13.sp,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceSm)
                    )
                    terms.forEach { term ->
                        val isActive = term.id == activeTermId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { if (!isActive) vm.switchTerm(term) }
                                .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    term.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) colors.accentMain else colors.textPrimary
                                )
                                Text(
                                    termSubtitle(term),
                                    fontSize = 12.sp,
                                    color = colors.textTertiary
                                )
                            }
                            if (isActive) {
                                Text("当前", fontSize = 12.sp, color = colors.accentMain)
                            }
                            if (term.id != 0L) {
                                IconButton(onClick = { pendingDelete = term }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "删除学期",
                                        tint = colors.textTertiary
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { newName = ""; showCreate = true }
                            .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.accentMain)
                        Spacer(Modifier.height(skin.spaceSm))
                        Text("新建学期", fontSize = 14.sp, color = colors.accentMain)
                    }
                }
            }

            if (activeTerm != null) {
                // ── 自动算出的当前周 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(skin.spaceLg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "当前周次 · 由开学日期自动计算",
                            fontSize = 13.sp,
                            color = colors.textTertiary
                        )
                        Text(
                            "第 $autoWeek 周",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentMain
                        )
                    }
                }

                // ── 学期名 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(modifier = Modifier.padding(skin.spaceLg)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("学期名称") },
                            placeholder = { Text("如 2025-2026-1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── 学期开始日期：三只滚轮 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(skin.spaceLg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "学期开始日期",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(skin.spaceMd))
                        val daysInMonth = remember(year, month) {
                            java.time.YearMonth.of(year, month).lengthOfMonth()
                        }
                        LaunchedEffect(daysInMonth) {
                            if (day > daysInMonth) day = daysInMonth
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            WheelPicker(
                                value = year,
                                onValueChange = { year = it },
                                range = (now.year - 3)..(now.year + 3),
                                formatLabel = { "${it}年" },
                                modifier = Modifier.weight(1f)
                            )
                            WheelPicker(
                                value = month,
                                onValueChange = { month = it },
                                range = 1..12,
                                formatLabel = { "${it}月" },
                                modifier = Modifier.weight(1f)
                            )
                            WheelPicker(
                                value = day,
                                onValueChange = { day = it },
                                range = 1..daysInMonth,
                                formatLabel = { "${it}日" },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── 学期总周数 ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
                ) {
                    Column(
                        modifier = Modifier.padding(skin.spaceLg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "学期总周数",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(skin.spaceMd))
                        WheelPicker(
                            value = editWeeks,
                            onValueChange = { editWeeks = it },
                            range = 1..30,
                            formatLabel = { "${it}周" },
                            modifier = Modifier.fillMaxWidth(0.5f)
                        )
                    }
                }
            }
        }
    }

    // 新建学期：先建空学期（默认 20 周），再在列表中切过去补细节
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建学期") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("学期名称") },
                    placeholder = { Text("如 2025-2026-1") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createTerm(newName.trim().ifBlank { "新学期" }, "", 20)
                    showCreate = false
                    newName = ""
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("取消") }
            }
        )
    }

    // 删除学期二次确认（销毁性操作必须确认，见 memory/01-constraints.md §2.8）
    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除学期") },
            text = { Text("「${deleting.displayName}」及其下所有课表方案、课程、作业都会被删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTerm(deleting)
                    pendingDelete = null
                }) { Text("删除", color = colors.accentMain) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/** 学期行的副标题：开学日期 + 总周数。 */
private fun termSubtitle(term: TermEntity): String {
    val date = term.startDate.ifBlank { "未设置开学日期" }
    return "$date · ${term.totalWeeks} 周"
}

// ═══════════════════════════════════════════
//  Wheel Picker — scroll wheel selector
// ═══════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    formatLabel: (Int) -> String = { it.toString() },
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val isDark = LocalDarkMode.current
    val colors = LocalPocketXiaoliColors.current
    val items = range.toList()
    val itemHeight = 44.dp
    val visibleCount = 3
    val centerFraction = visibleCount / 2f  // 1.5 — visual center of 3-item viewport

    val startIdx = items.indexOf(value).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIdx)
    val density = LocalDensity.current
    val itemHeightPx: Float = with(density) { itemHeight.toPx() }

    // Derive selected value from scroll position
    val selected by remember {
        derivedStateOf {
            val centerOffset = listState.firstVisibleItemScrollOffset + (centerFraction * itemHeightPx).toInt()
            val idx = listState.firstVisibleItemIndex + (centerOffset / itemHeightPx.toInt())
            items.getOrElse(idx - 1) { items.last() }
        }
    }

    LaunchedEffect(selected) {
        if (selected != value) onValueChange(selected)
    }

    // Sync scroll position when external value changes (e.g. date edit recalculates week)
    LaunchedEffect(value) {
        val target = items.indexOf(value)
        if (target >= 0 && target != listState.firstVisibleItemIndex) {
            listState.scrollToItem(target)
        }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleCount),
        contentAlignment = Alignment.Center
    ) {
        // Center highlight bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    if (isDark) Color.White.copy(alpha = 0.05f)
                    else colors.accentMain.copy(alpha = 0.08f),
                    RoundedCornerShape(skin.radiusMicro)
                )
        )

        // Fade edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Top gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.surfaceCard,
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = itemHeightPx * 1.5f
                        )
                    )
                    // Bottom gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.surfaceCard
                            ),
                            startY = size.height - itemHeightPx * 1.5f,
                            endY = size.height
                        )
                    )
                }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            flingBehavior = rememberSnapFlingBehavior(listState)
        ) {
            // Top spacer for centering
            item { Spacer(modifier = Modifier.height(itemHeight)) }
            // key = 条目本身：这是滚轮选择器，用下标当 key 会在条目增删时串位
            items(items.size, key = { i -> items[i] }) { i ->
                val item = items[i]
                val isSelected = item == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatLabel(item),
                        fontSize = if (isSelected) 17.sp else 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> colors.accentMain
                            else -> colors.textTertiary
                        }
                    )
                }
            }
            // Bottom spacer for centering
            item { Spacer(modifier = Modifier.height(itemHeight)) }
        }
    }
}
