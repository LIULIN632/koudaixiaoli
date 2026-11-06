package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.LoginPurpose
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.NewEntity
import com.pocket.xiaoli.desktop.data.TimeSlot
import com.pocket.xiaoli.desktop.logic.courseOccursInWeek
import com.pocket.xiaoli.desktop.logic.courseSpan
import com.pocket.xiaoli.desktop.logic.minuteText
import com.pocket.xiaoli.desktop.logic.findAgendaClashes
import com.pocket.xiaoli.desktop.logic.findConflicts
import com.pocket.xiaoli.desktop.logic.suggestValues
import com.pocket.xiaoli.desktop.logic.TimetableFilter
import com.pocket.xiaoli.desktop.logic.parseWeeks
import com.pocket.xiaoli.desktop.logic.slotStartMinute
import com.pocket.xiaoli.desktop.logic.weeksLabel
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketModal
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.rememberPressState
import com.pocket.xiaoli.desktop.ui.components.ScreenTitle
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.Segmented
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.CourseColors
import com.pocket.xiaoli.desktop.ui.theme.LocalDarkMode
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.courseBorderColor
import com.pocket.xiaoli.desktop.ui.theme.courseBgColor
import com.pocket.xiaoli.desktop.ui.theme.courseTextColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import java.time.LocalTime

private val CELL_HEIGHT = 58.dp

/**
 * 课表页：网格视图 + 列表视图 + 课程编辑。
 *
 * ## 合并块与跨周
 *
 * 网格按「天」分列，每列自上而下铺节次单元：某节课占 1-2 节时只画一个高 = span × 单元高
 * 的块，其余被覆盖的单元跳过。这样列与列之间自然对齐，也不需要绝对定位。
 *
 * 非本周的课**不消失**，而是画成半透明的「非本周」幽灵块 —— 单双周课表最怕"以为没课"。
 */
@Composable
fun TimetableScreen(state: AppState) {
    val colors = pocketColors()
    var gridMode by remember { mutableStateOf(true) }
    val term = state.data.activeTerm
    val totalWeeks = term?.totalWeeks ?: 20
    val week = state.displayedWeek()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(
            title = "课表",
            // 周次旁边就有切换器，侧栏也常驻第几周；这里只留"在看哪个学期"
            subtitle = term?.displayName ?: "未设置学期",
            actions = {
                GhostIconButton(PocketIcons.ChevronLeft, contentDescription = "上一周") {
                    state.weekOverride = (week - 1).coerceAtLeast(1)
                }
                TagChip(
                    text = if (state.weekOverride == 0) "本周" else "第 $week 周",
                    onClick = { state.weekOverride = 0 },
                )
                GhostIconButton(PocketIcons.ChevronRight, contentDescription = "下一周") {
                    state.weekOverride = (week + 1).coerceAtMost(totalWeeks)
                }
                Spacer(Modifier.width(10.dp))
                Segmented(
                    options = listOf("网格", "列表"),
                    selectedIndex = if (gridMode) 0 else 1,
                    onSelect = { gridMode = it == 0 },
                )
                Spacer(Modifier.width(10.dp))
                SecondaryButton("新课程", icon = PocketIcons.Plus) {
                    state.courseEditor = NewEntity.course(day = 1, slot = 1)
                }
            },
        )

        val courses = state.data.activeCourses
        val filter = state.timetableFilter
        val teachers = remember(courses) { suggestValues(courses, pick = { it.teacher }) }
        val rooms = remember(courses) { suggestValues(courses, pick = { it.room }) }
        if (teachers.size > 1 || rooms.size > 1) {
            // 原来教师与教室挤在同一条横向滚动里，中间夹一个孤零零的「教室」，
            // 而且各只给 6 个候选 —— 第 7 个老师就永远点不到。现在一组一行，候选全给。
            if (teachers.size > 1) {
                FilterRow(
                    label = "教师",
                    values = teachers,
                    selected = filter.teacher,
                    match = filter.matchCount(courses),
                ) { picked ->
                    state.timetableFilter = when {
                        picked == null || picked == filter.teacher -> TimetableFilter()
                        else -> filter.copy(teacher = picked, room = null)
                    }
                }
            }
            if (rooms.size > 1) {
                FilterRow(
                    label = "教室",
                    values = rooms,
                    selected = filter.room,
                    match = filter.matchCount(courses),
                ) { picked ->
                    state.timetableFilter = when {
                        picked == null || picked == filter.room -> TimetableFilter()
                        else -> filter.copy(teacher = null, room = picked)
                    }
                }
            }
        }

        if (gridMode) {
            GridView(state, week, totalWeeks, Modifier.weight(1f))
        } else {
            ListView(state, week, totalWeeks)
        }
    }
}

// ═══════════════════════ 筛选条 ═══════════════════════

/** 一组筛选一行：左边是维度名，右边给全部候选；选中后显示命中门数，再点一次或点 × 取消。 */
@Composable
private fun FilterRow(
    label: String,
    values: List<String>,
    selected: String?,
    match: Int,
    onSelect: (String?) -> Unit,
) {
    val colors = pocketColors()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PocketIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        Text(label, style = PocketTypography.label, color = colors.textTertiary)
        values.forEach { value ->
            val isSelected = selected == value
            TagChip(
                value,
                color = if (isSelected) Color.White else colors.textSecondary,
                background = if (isSelected) accentColor() else colors.surfaceAlt,
                onClick = { onSelect(value) },
            )
        }
        if (selected != null) {
            Text("匹配 $match 门", style = PocketTypography.label, color = accentColor())
            GhostIconButton(PocketIcons.Close, contentDescription = "清除${label}筛选", size = 24.dp) { onSelect(null) }
        }
    }
}

// ═══════════════════════ 网格 ═══════════════════════

@Composable
private fun GridView(state: AppState, week: Int, totalWeeks: Int, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val slots = state.data.slots
    if (slots.isEmpty()) {
        PocketCard(Modifier.fillMaxWidth()) {
            EmptyState(PocketIcons.Timer, "节次时间为空", "去「我的 → 节次时间」里恢复默认的 12 节课表")
        }
        return
    }
    val showWeekend = state.data.settings.showWeekend
    val days = if (showWeekend) (1..7).toList() else (1..5).toList()
    val courses = state.data.activeCourses
    val now = LocalTime.now()

    PocketCard(modifier.fillMaxWidth(), padding = 0.dp) {
        // 表头
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
            Spacer(Modifier.width(56.dp))
            days.forEach { day ->
                Text(
                    Format.weekday(day),
                    modifier = Modifier.weight(1f),
                    style = PocketTypography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DividerLine(Modifier.padding(horizontal = 12.dp))

        // 节次网格：12 节放不下整窗时纵向滚动（表头留在上面不动）
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)) {
            // 时间列
            Column(Modifier.width(56.dp)) {
                slots.forEach { slot ->
                    Column(
                        Modifier.height(CELL_HEIGHT),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("${slot.index}", style = PocketTypography.caption.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
                        Text(slot.start, style = PocketTypography.label, color = colors.textTertiary)
                        Text(slot.end, style = PocketTypography.label, color = colors.textTertiary)
                    }
                }
            }
            // 每天一列
            days.forEach { day ->
                Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                    var slotIndex = 1
                    while (slotIndex <= slots.size) {
                        val starting = courses.firstOrNull {
                            it.dayOfWeek == day && it.startSlot == slotIndex && (it.endSlot >= it.startSlot)
                        }
                        if (starting != null) {
                            val span = courseSpan(starting, slots)
                            val occurs = courseOccursInWeek(starting, week, totalWeeks)
                            CourseBlock(
                                course = starting,
                                span = span,
                                occurs = occurs,
                                dimmed = state.timetableFilter.active && !state.timetableFilter.matches(starting),
                                dark = dark,
                                onClick = { state.courseEditor = starting },
                            )
                            slotIndex += span
                        } else {
                            val covered = courses.any {
                                it.dayOfWeek == day && slotIndex > it.startSlot && slotIndex <= it.endSlot
                            }
                            if (!covered) {
                                EmptyCell(
                                    onClick = {
                                        state.courseEditor = NewEntity.course(day = day, slot = slotIndex)
                                    }
                                )
                            }
                            slotIndex += 1
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyCell(onClick: () -> Unit) {
    val colors = pocketColors()
    val press = rememberPressState()
    // 原来是一格灰（整张课表看起来灰蒙蒙），但它其实可点 —— 点空白格就是新建这门课。
    // 改成常态只留发丝边，悬停才亮出强调色 + 加号。
    Box(
        Modifier
            .fillMaxWidth()
            .height(CELL_HEIGHT - 4.dp)
            .padding(vertical = 2.dp)
            .scale(press.scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (press.hovered) accentSoftColor() else Color.Transparent)
            .border(
                1.dp,
                if (press.hovered) accentColor().copy(alpha = 0.5f) else colors.borderDivider.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .hoverable(press.source)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (press.hovered) {
            Icon(PocketIcons.Plus, contentDescription = null, tint = accentColor(), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CourseBlock(course: Course, span: Int, occurs: Boolean, dimmed: Boolean = false, dark: Boolean, onClick: () -> Unit) {
    val colors = pocketColors()
    val bg = courseBgColor(course.colorIndex, dark)
    val fg = courseTextColor(course.colorIndex, dark)
    val label = if (course.name.isBlank()) "未命名课程" else course.name
    val short = course.shortName.ifBlank { label }
    val press = rememberPressState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(CELL_HEIGHT * span - 4.dp)
            .padding(vertical = 2.dp)
            .scale(press.scale)
            .clip(RoundedCornerShape(10.dp))
            // 非本周仍保留自己的课程色（只是变淡），不要退成一块灰 —— 这块的作用正是"别以为没课"，
            // 灰到底 + 0.55 透明度会让字完全看不见。
            .background(if (occurs) bg else bg.copy(alpha = 0.42f))
            .border(
                width = if (press.pressed) 1.6.dp else 1.dp,
                color = if (occurs) bg else courseBorderColor(course.colorIndex, dark).copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
            )
            .alpha(if (occurs) (if (dimmed) 0.55f else 1f) else 0.8f)
            .hoverable(press.source)
            .clickable(interactionSource = press.source, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(
            short,
            style = PocketTypography.label.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            color = if (occurs) fg else fg.copy(alpha = 0.9f),
            maxLines = if (span >= 2) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (course.room.isNotBlank() && span >= 2) {
            Spacer(Modifier.height(2.dp))
            Text(
                course.room,
                style = PocketTypography.label.copy(fontSize = 10.sp),
                color = if (occurs) fg.copy(alpha = 0.8f) else colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!occurs) {
            Spacer(Modifier.height(2.dp))
            Text("非本周", style = PocketTypography.label.copy(fontSize = 9.sp), color = colors.textTertiary)
        }
    }
}

// ═══════════════════════ 列表 ═══════════════════════

@Composable
private fun ListView(state: AppState, week: Int, totalWeeks: Int) {
    val colors = pocketColors()
    val slots = state.data.slots
    val courses = state.data.activeCourses
    if (courses.isEmpty()) {
        PocketCard(Modifier.fillMaxWidth()) {
            EmptyState(
                PocketIcons.Timetable,
                "课表还是空的",
                "登录教务把本学期课表拉进来，或者自己新建一节；也能从「我的 → 导入 / 导出 JSON」搬手机端的课表",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton("从教务导入", icon = PocketIcons.GraduationCap) {
                            state.openLogin(LoginPurpose.IMPORT)
                        }
                        SecondaryButton("新建课程", icon = PocketIcons.Plus) {
                            state.courseEditor = NewEntity.course(1, 1)
                        }
                    }
                },
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (1..7).forEach { day ->
            val dayCourses = courses.filter { it.dayOfWeek == day }.sortedBy { it.startSlot }
            if (dayCourses.isEmpty()) return@forEach
            PocketCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Format.weekday(day),
                        style = PocketTypography.cardTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${dayCourses.size} 门", style = PocketTypography.caption, color = colors.textTertiary)
                }
                Spacer(Modifier.height(10.dp))
                dayCourses.forEach { course ->
                    val occurs = courseOccursInWeek(course, week, totalWeeks)
                    ListRow(
                        course,
                        slots,
                        occurs,
                        dimmed = state.timetableFilter.active && !state.timetableFilter.matches(course),
                    ) { state.courseEditor = course }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ListRow(course: Course, slots: List<TimeSlot>, occurs: Boolean, dimmed: Boolean = false, onClick: () -> Unit) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val bg = courseBgColor(course.colorIndex, dark)
    val fg = courseTextColor(course.colorIndex, dark)
    val start = if (course.startSlot >= 1) slotStartMinute(course.startSlot, slots) else 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.chip))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .alpha(if (occurs && !dimmed) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(bg)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(66.dp)) {
            Text(minuteText(start), style = PocketTypography.caption.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Text("第 ${course.startSlot}-${course.endSlot} 节", style = PocketTypography.label, color = colors.textTertiary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                course.name.ifBlank { "未命名课程" },
                style = PocketTypography.body.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    course.room.takeIf { it.isNotBlank() },
                    course.teacher.takeIf { it.isNotBlank() },
                    weeksLabel(course, 20),
                ).joinToString(" · "),
                style = PocketTypography.label,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!occurs) {
            TagChip("非本周")
        } else {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(bg))
        }
    }
}

// ═══════════════════════ 课程编辑 ═══════════════════════

@Composable
fun CourseEditorDialog(state: AppState) {
    val source = state.courseEditor ?: return
    val colors = pocketColors()
    var name by remember(source.id) { mutableStateOf(source.name) }
    var shortName by remember(source.id) { mutableStateOf(source.shortName) }
    var teacher by remember(source.id) { mutableStateOf(source.teacher) }
    var room by remember(source.id) { mutableStateOf(source.room) }
    var day by remember(source.id) { mutableStateOf(source.dayOfWeek.coerceIn(1, 7)) }
    var start by remember(source.id) { mutableStateOf(source.startSlot.coerceAtLeast(1)) }
    var end by remember(source.id) { mutableStateOf(source.endSlot.coerceAtLeast(source.startSlot)) }
    var weekType by remember(source.id) { mutableStateOf(source.weekType.ifBlank { "all" }) }
    var weeks by remember(source.id) { mutableStateOf(source.weeks) }
    var colorIndex by remember(source.id) { mutableStateOf(source.colorIndex) }
    var note by remember(source.id) { mutableStateOf(source.note) }
    var error by remember(source.id) { mutableStateOf<String?>(null) }
    var conflictAcknowledged by remember(source.id) { mutableStateOf(false) }
    val totalWeeks = state.data.activeTerm?.totalWeeks ?: 20
    val isNew = state.data.courses.none { it.id == source.id }
    // 冲突判据与课表画格子用的是同一套（共享的 courseOccursInWeek），不会出现"看着撞了却说不撞"
    val candidate = source.copy(
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weekType = weekType,
        weeks = weeks.trim(),
        termId = source.termId.ifBlank { state.data.activeTermId },
    )
    val conflicts = remember(candidate, state.data.courses, totalWeeks) {
        findConflicts(candidate, state.data.activeCourses, totalWeeks) + findAgendaClashes(candidate, state.data.activeCourses)
    }
    val teacherSuggestions = remember(state.data.courses) { suggestValues(state.data.courses, pick = { c -> c.teacher }) }
    val roomSuggestions = remember(state.data.courses) { suggestValues(state.data.courses, pick = { c -> c.room }) }

    PocketModal(
        title = if (isNew) "新建课程" else "编辑课程",
        subtitle = "时间为空或写错时，可用「我的 → 节次时间」恢复默认",
        onDismiss = { state.courseEditor = null },
        width = 520.dp,
        footer = {
            if (!isNew) {
                SecondaryButton("删除", icon = PocketIcons.Trash) {
                    state.deleteCourse(source.id)
                    state.courseEditor = null
                }
                Spacer(Modifier.width(10.dp))
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("取消") { state.courseEditor = null }
            Spacer(Modifier.width(10.dp))
            PrimaryButton(if (conflicts.isNotEmpty() && conflictAcknowledged) "仍然保存" else "保存") {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "课程名不能为空"
                } else if (start > end) {
                    error = "起始节不能晚于结束节"
                } else if (start > 12 || end > 12) {
                    error = "节次超出范围（最多 12 节）"
                } else if (!weeks.trim().let { it.isEmpty() || parseWeeks(it).isNotEmpty() }) {
                    error = "周次写法看不懂，例如 1-16,18"
                } else if (conflicts.isNotEmpty() && !conflictAcknowledged) {
                    conflictAcknowledged = true
                } else {
                    state.saveCourse(
                        source.copy(
                            name = trimmed,
                            shortName = shortName.trim(),
                            teacher = teacher.trim(),
                            room = room.trim(),
                            dayOfWeek = day,
                            startSlot = start,
                            endSlot = end,
                            weekType = weekType,
                            weeks = weeks.trim(),
                            colorIndex = colorIndex,
                            note = note.trim(),
                            colorPinned = colorIndex != source.colorIndex || source.colorPinned,
                        )
                    )
                    state.courseEditor = null
                }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PocketTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "课程名（如：高等数学）",
                )
                PocketTextField(
                    value = shortName,
                    onValueChange = { shortName = it },
                    modifier = Modifier.width(150.dp),
                    placeholder = "简称（可选）",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PocketTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "任课老师",
                )
                PocketTextField(
                    value = room,
                    onValueChange = { room = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "上课地点",
                )
            }
            if (teacherSuggestions.isNotEmpty() || roomSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (teacherSuggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("老师", style = PocketTypography.label, color = colors.textTertiary)
                            teacherSuggestions.take(6).forEach { value ->
                                TagChip(value, onClick = { teacher = value })
                            }
                        }
                    }
                    if (roomSuggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("教室", style = PocketTypography.label, color = colors.textTertiary)
                            roomSuggestions.take(6).forEach { value ->
                                TagChip(value, onClick = { room = value })
                            }
                        }
                    }
                }
            }
            if (conflicts.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(colors.warning.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (conflictAcknowledged) "时间有重叠，再点一次「仍然保存」就写进去" else "这个时间和已有课程撞上：",
                        style = PocketTypography.caption,
                        color = colors.warning,
                    )
                    conflicts.take(3).forEach { conflict ->
                        Text(conflict.summary, style = PocketTypography.label, color = colors.textSecondary)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("星期", style = PocketTypography.caption, color = colors.textSecondary)
                (1..7).forEach { d ->
                    TagChip(
                        text = Format.weekday(d).removePrefix("周"),
                        color = if (day == d) Color.White else colors.textSecondary,
                        background = if (day == d) accentColor() else colors.surfaceAlt,
                        onClick = { day = d },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("节次", style = PocketTypography.caption, color = colors.textSecondary)
                SlotStepper(value = start, onChange = {
                    start = it
                    if (end < it) end = it
                })
                Text("到", style = PocketTypography.caption, color = colors.textTertiary)
                SlotStepper(value = end, onChange = {
                    end = it
                    if (end < start) start = end
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Segmented(
                    options = listOf("每周", "单周", "双周"),
                    selectedIndex = when (weekType) {
                        "odd" -> 1
                        "even" -> 2
                        else -> 0
                    },
                    onSelect = { weekType = listOf("all", "odd", "even")[it] },
                )
                PocketTextField(
                    value = weeks,
                    onValueChange = { weeks = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "周次，留空 = 全学期（如 1-16,18）",
                )
            }
            Column {
                Text("颜色", style = PocketTypography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CourseColors.forEachIndexed { index, color ->
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(color)
                                .border(
                                    width = if (colorIndex == index) 2.5.dp else 1.dp,
                                    color = if (colorIndex == index) colors.textPrimary else colors.borderDivider,
                                    shape = RoundedCornerShape(11.dp),
                                )
                                .clickable { colorIndex = index }
                        )
                    }
                }
            }
            PocketTextField(
                value = note,
                onValueChange = { note = it },
                singleLine = false,
                minHeight = 52.dp,
                placeholder = "备注（可选）",
            )
            if (error != null) Text(error!!, style = PocketTypography.caption, color = colors.danger)
        }
    }
}

@Composable
private fun SlotStepper(value: Int, onChange: (Int) -> Unit) {
    val colors = pocketColors()
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.chip))
            .background(colors.surfaceAlt)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GhostIconButton(PocketIcons.ChevronLeft, size = 26.dp) { onChange((value - 1).coerceAtLeast(1)) }
        Text(
            "$value",
            Modifier.width(24.dp),
            style = PocketTypography.body.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        GhostIconButton(PocketIcons.ChevronRight, size = 26.dp) { onChange((value + 1).coerceAtMost(12)) }
    }
}
