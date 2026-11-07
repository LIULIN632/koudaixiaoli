package com.pocket.xiaoli.ui.timetable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.ui.components.FieldActionChip
import com.pocket.xiaoli.ui.components.rememberStaggeredMenu
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.theme.CourseColors
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.util.CourseShortName
import com.pocket.xiaoli.util.MinuteOfDay

private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 「`1,2,3,…`」这种周次串压成首尾两档；解析不出来时按整个学期算。
 *
 * 原本这段逻辑在 composable 里用 `remember(weeks)` 现算，而 `weeks` 从来没被写过
 * （只有 `startWeek` / `endWeek` 会改），等于每次打开弹窗算一遍同一个值。
 */
private fun parseWeekRange(raw: String): Pair<Int, Int> {
    val nums = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    return if (nums.isNotEmpty()) nums.first() to nums.last() else 1 to 16
}

/**
 * 课程编辑器的表单状态。
 *
 * ## 为什么要提出来，而不是把 `remember` 写在对话框里
 *
 * 与 [com.pocket.xiaoli.ui.home.AddTodoFormState] 同一个理由：「保存」在 `AlertDialog`
 * 的 `confirmButton` 槽、字段在 `text` 槽，**两棵树**，状态必须提到共同祖先。
 * 提出来之后正文 [CourseEditorForm] 能**脱离对话框单独渲染** ——
 * `AlertDialog` 是独立窗口，`captureRoboImage` 抓不到它，不拆就没有基线可验。
 */
@Stable
internal class CourseEditorFormState(
    val original: CourseEntity,
    val currentWeek: Int,
    availableSlots: List<Int>,
) {
    var name by mutableStateOf(original.name)
    var shortName by mutableStateOf(original.shortName)
    var credits by mutableStateOf(original.credits)
    var teacher by mutableStateOf(original.teacher)
    var room by mutableStateOf(original.room)
    var dayOfWeek by mutableStateOf(original.dayOfWeek)
    var startSlot by mutableStateOf(original.startSlot)
    var endSlot by mutableStateOf(original.endSlot)
    var weekType by mutableStateOf(original.weekType)
    var kind by mutableStateOf(original.kind)
    var note by mutableStateOf(original.note)
    var useMinuteTime by mutableStateOf(original.startMinute >= 0 || original.endMinute >= 0)
    var startMinuteText by mutableStateOf(MinuteOfDay.format(original.startMinute))
    var endMinuteText by mutableStateOf(MinuteOfDay.format(original.endMinute))

    val isEditing: Boolean get() = original.id != 0L

    /** 节次下拉不写死 1..12：跟随「时间段管理」配的节次，但课程自己的节次永远在列表里。 */
    val slotOptions: List<Int> =
        (availableSlots.ifEmpty { (1..12).toList() } + original.startSlot + original.endSlot)
            .distinct().sorted()

    private val initialRange = parseWeekRange(original.weeks)
    var startWeek by mutableIntStateOf(initialRange.first)
    var endWeek by mutableIntStateOf(initialRange.second)

    /** 点过调色板才算"钉住"：没点过就继续按课名自动配色。 */
    var colorTouched by mutableStateOf(false)
    var colorIndex by mutableStateOf(original.effectiveColorIndex)

    val showWeekRange: Boolean get() = weekType != "thisWeek"

    val hasChanges: Boolean
        get() = name != original.name ||
            shortName != original.shortName ||
            credits != original.credits ||
            teacher != original.teacher ||
            room != original.room ||
            dayOfWeek != original.dayOfWeek ||
            startSlot != original.startSlot ||
            endSlot != original.endSlot ||
            weekType != original.weekType ||
            kind != original.kind ||
            note != original.note ||
            useMinuteTime != (original.startMinute >= 0 || original.endMinute >= 0) ||
            startMinuteText != MinuteOfDay.format(original.startMinute) ||
            endMinuteText != MinuteOfDay.format(original.endMinute) ||
            (colorTouched && colorIndex != original.effectiveColorIndex) ||
            startWeek != initialRange.first ||
            endWeek != initialRange.second

    /** 「仅本周」不是一种周次类型，而是"只排当前这一周"：存成 all + 单周 weeks。 */
    fun buildCourse(): CourseEntity {
        val generatedWeeks = when (weekType) {
            "thisWeek" -> currentWeek.toString()
            else -> (startWeek..endWeek).joinToString(",") { it.toString() }
        }
        return original.copy(
            name = name,
            credits = credits,
            teacher = teacher,
            room = room,
            dayOfWeek = dayOfWeek,
            startSlot = startSlot,
            endSlot = endSlot,
            weekType = if (weekType == "thisWeek") "all" else weekType,
            weeks = generatedWeeks,
            colorIndex = colorIndex,
            shortName = shortName.trim(),
            kind = kind,
            note = note.trim(),
            startMinute = if (useMinuteTime) MinuteOfDay.parse(startMinuteText) ?: MinuteOfDay.NONE else MinuteOfDay.NONE,
            endMinute = if (useMinuteTime) MinuteOfDay.parse(endMinuteText) ?: MinuteOfDay.NONE else MinuteOfDay.NONE,
            // 点过 → 钉住；本来已钉住的继续钉住（改别的字段不该把颜色放回自动）
            colorPinned = colorTouched || original.colorPinned,
        )
    }
}

/**
 * 课程编辑器。
 *
 * 字段分五组（基本信息 / 上课时间 / 周次 / 地点 / 颜色），一组里再按"必填在前、可选在后"排。
 * 原本 18 个控件是一根没有分隔的滚动列，且**备注这个多行框排在第 3 位**，
 * 把真正决定这门课长在课表哪一格的星期/节次/周次一路顶到折叠线以下。
 */
@Composable
fun CourseEditorDialog(
    course: CourseEntity,
    currentWeek: Int,
    availableSlots: List<Int> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (CourseEntity) -> Unit,
    onDelete: (CourseEntity) -> Unit,
    onLocate: (String) -> Unit = {},
    onOpenRoomLayout: (String) -> Unit = {},
    onOpenCourseNavigation: (String) -> Unit = {},
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val state = remember(course, currentWeek, availableSlots) {
        CourseEditorFormState(course, currentWeek, availableSlots)
    }
    var showExitConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (state.hasChanges) showExitConfirm = true else onDismiss() },
        shape = RoundedCornerShape(skin.radiusCard),
        title = { Text(if (state.isEditing) "编辑课程" else "添加课程", fontWeight = FontWeight.ExtraBold) },
        text = {
            CourseEditorForm(
                state = state,
                onOpenRoomLayout = onOpenRoomLayout,
                onLocate = onLocate,
                onOpenCourseNavigation = onOpenCourseNavigation,
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(state.buildCourse()) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentMain),
                shape = RoundedCornerShape(skin.radiusChip),
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (state.isEditing) {
                    TextButton(onClick = { onDelete(course) }) {
                        Text("删除", color = colors.danger)
                    }
                }
                TextButton(onClick = {
                    if (state.hasChanges) showExitConfirm = true else onDismiss()
                }) { Text("取消") }
            }
        },
    )

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("放弃更改？", fontWeight = FontWeight.Bold) },
            text = { Text("你有未保存的修改，退出后将丢失所有更改。") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.danger),
                ) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("继续编辑") }
            },
        )
    }
}

/**
 * 课程编辑器的**正文**（五组字段）。
 *
 * ⚠️ `internal` 而不是 private：`AlertDialog` 渲染在独立窗口，快照抓不到那个窗口，
 * 抽出来才能给它录基准图（与 [com.pocket.xiaoli.ui.home.AddTodoForm] 同一做法，别改回 private）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseEditorForm(
    state: CourseEditorFormState,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    onOpenRoomLayout: (String) -> Unit = {},
    onLocate: (String) -> Unit = {},
    onOpenCourseNavigation: (String) -> Unit = {},
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        // ── 基本信息 ──
        SectionCaption("基本信息")
        EditorField(label = "课程名称", value = state.name, onValueChange = { state.name = it })
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            EditorChoice(
                label = "课程",
                selected = state.kind == CourseEntity.KIND_COURSE,
                onClick = { state.kind = CourseEntity.KIND_COURSE },
            )
            EditorChoice(
                label = "日程",
                selected = state.kind == CourseEntity.KIND_AGENDA,
                onClick = { state.kind = CourseEntity.KIND_AGENDA },
            )
        }
        EditorField(
            label = "课表简称（可选）",
            value = state.shortName,
            onValueChange = { state.shortName = it },
            placeholder = CourseShortName.suggest(state.name)
                ?.let { "留空显示「$it」" } ?: "留空显示全名",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            EditorField(
                label = "学分",
                value = state.credits,
                onValueChange = { state.credits = it },
                modifier = Modifier.weight(1f),
            )
            EditorField(
                label = "教师",
                value = state.teacher,
                onValueChange = { state.teacher = it },
                modifier = Modifier.weight(1f),
            )
        }
        EditorField(
            label = if (state.kind == CourseEntity.KIND_AGENDA) "日程备注" else "备注（可选）",
            value = state.note,
            onValueChange = { state.note = it },
            maxLines = 3,
        )

        // ── 上课时间 ──
        SectionCaption("上课时间")
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            EditorDropdown(
                label = "星期",
                display = WEEKDAYS[(state.dayOfWeek - 1).coerceIn(0, 6)],
                options = WEEKDAYS,
                onSelect = { state.dayOfWeek = it + 1 },
                modifier = Modifier.weight(1f),
            )
            EditorDropdown(
                label = "开始节次",
                display = state.startSlot.toString(),
                options = state.slotOptions.map { "第${it}节" },
                onSelect = { i ->
                    state.startSlot = state.slotOptions[i]
                    // 开始挪到结束后面时把结束一起推下去，而不是存出一条 end < start 的课
                    if (state.endSlot < state.startSlot) state.endSlot = state.startSlot
                },
                modifier = Modifier.weight(1f),
            )
            val endOptions = state.slotOptions.filter { it >= state.startSlot }
            EditorDropdown(
                label = "结束节次",
                display = state.endSlot.toString(),
                options = endOptions.map { "第${it}节" },
                onSelect = { state.endSlot = endOptions[it] },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            EditorChoice(
                label = "按节次",
                selected = !state.useMinuteTime,
                onClick = { state.useMinuteTime = false },
            )
            EditorChoice(
                label = "按时间",
                selected = state.useMinuteTime,
                onClick = { state.useMinuteTime = true },
            )
        }
        if (state.useMinuteTime) {
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                EditorField(
                    label = "开始 HH:mm",
                    value = state.startMinuteText,
                    onValueChange = { state.startMinuteText = it },
                    placeholder = "10:00",
                    modifier = Modifier.weight(1f),
                )
                EditorField(
                    label = "结束 HH:mm",
                    value = state.endMinuteText,
                    onValueChange = { state.endMinuteText = it },
                    placeholder = "11:40",
                    modifier = Modifier.weight(1f),
                )
            }
            EditorFootnote("按时间填就按分钟排课，不再受节次表限制；留空回退到上面的节次。")
        }

        // ── 周次 ──
        SectionCaption("周次")
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            val weekTypes = listOf("all" to "全周", "odd" to "单周", "even" to "双周", "thisWeek" to "仅本周")
            weekTypes.forEach { (key, label) ->
                EditorChoice(label = label, selected = state.weekType == key, onClick = { state.weekType = key })
            }
        }
        if (state.showWeekRange) {
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                EditorDropdown(
                    label = "开始周",
                    display = "第${state.startWeek}周",
                    options = (1..25).map { "第${it}周" },
                    onSelect = { i ->
                        state.startWeek = i + 1
                        if (state.endWeek < state.startWeek) state.endWeek = state.startWeek
                    },
                    modifier = Modifier.weight(1f),
                )
                val endWeeks = state.startWeek..30
                EditorDropdown(
                    label = "结束周",
                    display = "第${state.endWeek}周",
                    options = endWeeks.map { "第${it}周" },
                    onSelect = { state.endWeek = state.startWeek + it },
                    modifier = Modifier.weight(1f),
                )
            }
            EditorFootnote("共 ${state.endWeek - state.startWeek + 1} 周：第 ${state.startWeek}–${state.endWeek} 周")
        } else {
            EditorFootnote("只在第 ${state.currentWeek} 周出现，其余周不排这门课。")
        }

        // ── 地点 ──
        SectionCaption("地点")
        EditorField(
            label = "教室 / 地点",
            value = state.room,
            onValueChange = { state.room = it },
            placeholder = "例如：教三楼 305",
        )
        if (state.room.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                FieldActionChip("分布图", onClick = { onOpenRoomLayout(state.room.trim()) })
                FieldActionChip("地图", onClick = { onLocate(state.room.trim()) })
                FieldActionChip("导航", onClick = { onOpenCourseNavigation(state.room.trim()) })
            }
        }

        // ── 颜色 ──
        SectionCaption("颜色")
        // 15 个色块排成一行会在弹窗宽度外被截掉（原来就是这样，第 9 个之后点不到）—— 让它换行。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            CourseColors.forEachIndexed { i, c ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .invisibleTouchTarget()
                        .clip(CircleShape)
                        .background(c)
                        .clickable {
                            state.colorIndex = i
                            state.colorTouched = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (i == state.colorIndex) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
        EditorFootnote("不选就按课名自动配色（同一门课每天都同色）；点一下就固定成你选的颜色。")
    }
}

/** 弹窗内的输入框：与创建任务弹窗、考试页粘贴框同一档（`radiusChip` + 页底色填充）。 */
@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    maxLines: Int = 1,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceBase,
            unfocusedContainerColor = colors.surfaceBase,
        ),
    )
}

/** 弹窗内的单选：原来用 M3 `FilterChip`，四个并排时每个都被压成两行文字，读不出是一组互斥选项。 */
@Composable
private fun EditorChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(if (selected) colors.accentMain else colors.surfaceBase)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else colors.borderDivider,
                shape = RoundedCornerShape(skin.radiusChip),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceMd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) onAccentColor() else colors.textSecondary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorDropdown(
    label: String,
    display: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var expanded by remember { mutableStateOf(false) }
    // 三个下拉并排、选项 7~12 个：逐项错开出场，收回时最后一项先走
    val reveal = rememberStaggeredMenu(expanded, options.size) { expanded = false }
    ExposedDropdownMenuBox(expanded = reveal.menuExpanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            // 三个下拉并排时一格只有 ~80dp，去掉这行「周三」会折成「周 / 三」。
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = RoundedCornerShape(skin.radiusChip),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceBase,
                unfocusedContainerColor = colors.surfaceBase,
            ),
        )
        ExposedDropdownMenu(expanded = reveal.menuExpanded, onDismissRequest = reveal.close) {
            options.forEachIndexed { i, o ->
                Box(reveal.itemModifier(i)) {
                    DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); expanded = false })
                }
            }
        }
    }
}

/** 组内的一句补充说明。只在"不读会做错"时出现，所以刻意压到一行。 */
@Composable
private fun EditorFootnote(text: String) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = text,
        fontSize = 11.sp,
        color = colors.textTertiary,
        lineHeight = 15.sp,
    )
}
