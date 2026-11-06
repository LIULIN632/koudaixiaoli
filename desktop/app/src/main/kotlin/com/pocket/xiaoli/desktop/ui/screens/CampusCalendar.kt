package com.pocket.xiaoli.desktop.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.data.CalendarEvent
import com.pocket.xiaoli.desktop.data.CampusConfig
import com.pocket.xiaoli.desktop.data.CampusServiceKeys
import com.pocket.xiaoli.desktop.data.KnowledgeDoc
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.logic.Knowledge
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.SectionHeader
import com.pocket.xiaoli.desktop.ui.components.SettingRow
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.MarkdownText
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.ProgressBar
import com.pocket.xiaoli.desktop.ui.components.ScreenTitle
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.Segmented
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.core.calendar.TermWeeksCore
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.util.Platform
import java.time.LocalDate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import com.pocket.xiaoli.core.calendar.monthGrid
import com.pocket.xiaoli.desktop.ui.theme.onAccentColor
import com.pocket.xiaoli.core.calendar.eventCoversDay
import com.pocket.xiaoli.core.calendar.holidayOn
import com.pocket.xiaoli.desktop.data.HolidayStore
import com.pocket.xiaoli.desktop.logic.courseEndMinute
import com.pocket.xiaoli.desktop.logic.courseStartMinute
import com.pocket.xiaoli.desktop.logic.coursesOnDay
import com.pocket.xiaoli.desktop.logic.minuteText
import com.pocket.xiaoli.desktop.logic.termWeekOf

// ═══════════════════════ 校历 ═══════════════════════

@Composable
internal fun CalendarSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val calendar = Assets.calendar
    if (calendar == null || calendar.events.isEmpty()) {
        PocketCard(modifier.fillMaxWidth()) {
            EmptyState(PocketIcons.CalendarDays, "没有校历数据", "assets/calendar/ytlg.json 缺失或格式不对")
        }
        return
    }
    val today = LocalDate.now()
    val week = Store.currentWeek(today)
    val upcoming = calendar.events
        .mapNotNull { event -> runCatching { LocalDate.parse(event.start) }.getOrNull()?.let { it to event } }
        .sortedBy { it.first }
    val future = upcoming.filter { it.first >= today }
    val past = upcoming.filter { it.first < today }.reversed()

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 本月月历：格子几何来自共享层 MonthGridCore（与手机同一份规则）
            MonthCard(today = today, events = calendar.events, focusEpochDay = state.calendarFocusEpochDay)

        // 当前阶段：与手机端同一个判定顺序（假期 > 考试周 > 学期中 > 未开学），
        // 规则在 core/calendar/TermPhaseCore，桌面这里只喂校历事件表。
        val phaseToday = java.time.LocalDate.now()
        val sharedPhase = com.pocket.xiaoli.core.calendar.TermPhaseCore.phaseOf(
            todayEpochDay = phaseToday.toEpochDay(),
            terms = calendar.terms.mapNotNull { t ->
                val start = runCatching { LocalDate.parse(t.start) }.getOrNull() ?: return@mapNotNull null
                com.pocket.xiaoli.core.calendar.TermPhaseCore.Window(
                    kind = com.pocket.xiaoli.core.calendar.TermPhaseCore.Kind.OTHER,
                    startEpochDay = start.toEpochDay(),
                    endEpochDay = com.pocket.xiaoli.core.calendar.WeekCalendarCore.teachingEndEpochDay(
                        startEpochDay = start.toEpochDay(),
                        teachingWeeks = t.teachingWeeks,
                    ),
                )
            },
            events = calendar.events.mapNotNull { e ->
                val start = runCatching { LocalDate.parse(e.start) }.getOrNull() ?: return@mapNotNull null
                val end = runCatching { LocalDate.parse(e.end) }.getOrNull() ?: return@mapNotNull null
                com.pocket.xiaoli.core.calendar.TermPhaseCore.Window(
                    kind = when (e.kind.lowercase()) {
                        "vacation" -> com.pocket.xiaoli.core.calendar.TermPhaseCore.Kind.VACATION
                        "exam" -> com.pocket.xiaoli.core.calendar.TermPhaseCore.Kind.EXAM
                        else -> com.pocket.xiaoli.core.calendar.TermPhaseCore.Kind.OTHER
                    },
                    startEpochDay = start.toEpochDay(),
                    endEpochDay = end.toEpochDay(),
                    name = e.short.ifBlank { e.title },
                )
            },
            monthOf = { day -> LocalDate.ofEpochDay(day).monthValue },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前阶段", style = PocketTypography.caption, color = colors.textTertiary)
            Spacer(Modifier.width(8.dp))
            TagChip(sharedPhase.label, color = accentColor(), background = accentSoftColor())
        }

        // 学期卡：每个学期各自算「第几周」，不能都套当前学期的周次
        calendar.terms.forEach { span ->
            val start = runCatching { LocalDate.parse(span.start) }.getOrNull()
            val end = runCatching { LocalDate.parse(span.end) }.getOrNull()
            // 周次与阶段用共享口径（core/calendar）：手机端一直按「教学 18 + 考试 2 = 20 周」算，
            // 桌面原来用 totalWeeks(26) 做分母，第 9 周看着才过三分之一。
            val termWeek = start?.let {
                com.pocket.xiaoli.core.calendar.WeekCalendarCore.weekOf(it.toEpochDay(), today.toEpochDay())
            }
            val phase = com.pocket.xiaoli.core.calendar.TermWeeksCore.phaseOf(termWeek, span.teachingWeeks)
            PocketCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(PocketIcons.GraduationCap, accentColor(), accentSoftColor())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(span.name, style = PocketTypography.cardTitle, color = colors.textPrimary)
                        Text(
                            "${span.start} → ${span.end} · 教学 ${span.teachingWeeks} 周 / 共 ${span.totalWeeks} 周",
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    }
                    when (phase) {
                        TermWeeksCore.Phase.TEACHING -> TagChip("第 $termWeek 周", color = accentColor(), background = accentSoftColor())
                        TermWeeksCore.Phase.EXAM -> TagChip("考试周", color = accentColor(), background = accentSoftColor())
                        TermWeeksCore.Phase.BEFORE -> TagChip("未开始", color = colors.textTertiary, background = colors.surfaceAlt)
                        TermWeeksCore.Phase.AFTER -> TagChip("已结束", color = colors.textTertiary, background = colors.surfaceAlt)
                    }
                }
                if (span.teachingWeeks > 0) {
                    Spacer(Modifier.height(10.dp))
                    val passed = TermWeeksCore.progressFraction(termWeek, span.teachingWeeks)
                    ProgressBar(progress = passed, gold = phase != TermWeeksCore.Phase.BEFORE)
                }
            }
        }

        if (future.isNotEmpty()) {
            Text("接下来", style = PocketTypography.cardTitle, color = colors.textPrimary)
            PocketCard(Modifier.fillMaxWidth()) {
                future.forEachIndexed { index, (date, event) ->
                    EventRow(date, event, today)
                    if (index != future.lastIndex) {
                        Spacer(Modifier.height(6.dp))
                        DividerLine()
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        if (past.isNotEmpty()) {
            var showPast by remember { mutableStateOf(false) }
            Row(
                Modifier.clip(RoundedCornerShape(Radii.chip)).clickable { showPast = !showPast }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    if (showPast) PocketIcons.ChevronDown else PocketIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("已过去 ${past.size} 项", style = PocketTypography.caption, color = colors.textTertiary)
            }
            if (showPast) {
                PocketCard(Modifier.fillMaxWidth()) {
                    past.forEachIndexed { index, (date, event) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${date.monthValue}/${date.dayOfMonth}", style = PocketTypography.caption, color = colors.textTertiary, modifier = Modifier.width(56.dp))
                            Text(event.title, style = PocketTypography.body, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index != past.lastIndex) DividerLine()
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(date: LocalDate, event: CalendarEvent, today: LocalDate) {
    val colors = pocketColors()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, date).toInt()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(88.dp)) {
            Text(
                "${date.monthValue} 月 ${date.dayOfMonth} 日",
                style = PocketTypography.caption.copy(fontWeight = FontWeight.Bold),
                color = if (days <= 7) accentColor() else colors.textSecondary,
            )
            Text(Format.weekday(date.dayOfWeek.value), style = PocketTypography.label, color = colors.textTertiary)
        }
        Column(Modifier.weight(1f)) {
            Text(event.title, style = PocketTypography.body, color = colors.textPrimary)
            val range = if (event.end.isNotBlank() && event.end != event.start) "至 ${event.end}" else ""
            if (range.isNotBlank() || event.short.isNotBlank()) {
                Text(
                    listOf(event.short, range).filter { it.isNotBlank() }.joinToString(" · "),
                    style = PocketTypography.label,
                    color = colors.textTertiary,
                )
            }
        }
        TagChip(
            Format.relativeDays(date, today),
            color = if (days <= 3) accentColor() else colors.textSecondary,
            background = if (days <= 3) accentSoftColor() else colors.surfaceAlt,
        )
    }
}

/**
 * 月历卡。格子几何用共享层 `monthGrid`，事件覆盖判定用 `eventCoversDay`。
 * 可翻月；点某天 → 下方列出当天安排。今天实心，选中日淡色，有事件的日子点小圆点。
 */
@Composable
private fun MonthCard(today: LocalDate, events: List<CalendarEvent>, focusEpochDay: Long? = null) {
    val colors = pocketColors()
    val holidaysVersion = HolidayStore.version
    val holidays = remember(holidaysVersion) { HolidayStore.days }
    // 验收用开发开关：-Dxiaoli.campus.monthOffset=3 直接翻到三个月后，省得靠点击
    val monthOffset = remember { System.getProperty("xiaoli.campus.monthOffset")?.toIntOrNull() ?: 0 }
    val focus = focusEpochDay?.let { LocalDate.ofEpochDay(it) }
    var month by remember(focusEpochDay) {
        mutableStateOf((focus ?: today.withDayOfMonth(1)).withDayOfMonth(1).plusMonths(monthOffset.toLong()))
    }
    var selected by remember(focusEpochDay) { mutableStateOf(focus ?: month) }
    val cells = monthGrid(
        firstEpochDayOfMonth = month.toEpochDay(),
        daysInMonth = month.lengthOfMonth(),
        weekdayOfFirst = month.dayOfWeek.value - 1,
    )

    fun eventsOnDay(date: LocalDate): List<CalendarEvent> = events.filter { event ->
        val start = runCatching { LocalDate.parse(event.start) }.getOrNull() ?: return@filter false
        val end = runCatching { LocalDate.parse(event.end) }.getOrNull() ?: start
        eventCoversDay(start.toEpochDay(), end.toEpochDay(), date.toEpochDay())
    }

    val eventDays = events
        .mapNotNull { event -> runCatching { LocalDate.parse(event.start) }.getOrNull()?.let { it to event } }
        .filter { (date, _) -> date.year == month.year && date.monthValue == month.monthValue }
        .map { it.first.dayOfMonth }
        .toSet()
    val dayEvents = eventsOnDay(selected)

    PocketCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${month.year} 年 ${month.monthValue} 月", style = PocketTypography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            if (eventDays.isNotEmpty()) {
                Text("${eventDays.size} 天有安排", style = PocketTypography.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            GhostIconButton(PocketIcons.ChevronLeft, contentDescription = "上个月", size = 22.dp) {
                month = month.minusMonths(1)
                selected = month
            }
            GhostIconButton(PocketIcons.ChevronRight, contentDescription = "下个月", size = 22.dp) {
                month = month.plusMonths(1)
                selected = month
            }
            if (month != today.withDayOfMonth(1)) {
                Spacer(Modifier.width(4.dp))
                TagChip("回到本月", onClick = {
                    month = today.withDayOfMonth(1)
                    selected = today
                })
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = PocketTypography.label,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { cell ->
                    val date = LocalDate.ofEpochDay(cell.epochDay)
                    val isToday = date == today
                    val isSelected = date == selected
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selected = date }
                                .padding(vertical = 2.dp, horizontal = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isToday -> accentColor()
                                            isSelected -> accentSoftColor()
                                            else -> Color.Transparent
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = PocketTypography.caption,
                                    color = when {
                                        isToday -> onAccentColor()
                                        isSelected -> accentColor()
                                        cell.inMonth -> colors.textPrimary
                                        else -> colors.textTertiary.copy(alpha = 0.45f)
                                    },
                                )
                            }
                            val holiday = holidayOn(holidays, cell.epochDay)
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !cell.inMonth -> Color.Transparent
                                            holiday != null && holiday.isOff -> colors.warning
                                            holiday != null -> colors.textSecondary.copy(alpha = 0.55f)
                                            date.dayOfMonth in eventDays -> accentColor()
                                            else -> Color.Transparent
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DividerLine()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.monthDay(selected), style = PocketTypography.body, color = colors.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text(Format.weekday(selected.dayOfWeek.value), style = PocketTypography.caption, color = colors.textSecondary)
            val holidayToday = holidayOn(holidays, selected.toEpochDay())
            if (holidayToday != null) {
                Spacer(Modifier.width(6.dp))
                TagChip(
                    holidayToday.name + (if (holidayToday.isOff) " · 放假" else " · 调休上班"),
                    color = if (holidayToday.isOff) colors.warning else colors.textSecondary,
                    background = if (holidayToday.isOff) colors.warning.copy(alpha = 0.12f) else colors.surfaceAlt,
                )
            }
            Spacer(Modifier.weight(1f))
            val weekForLabel = Store.data.activeTerm?.let { t -> termWeekOf(t, selected) } ?: 0
            Text(
                (if (dayEvents.isEmpty()) "无校历事件" else "校历 ${dayEvents.size} 项") +
                    " · " + (if (weekForLabel > 0) "第 $weekForLabel 周" else "不在学期内"),
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        }
        if (dayEvents.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            dayEvents.forEach { event ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accentColor()),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(event.title, style = PocketTypography.caption, color = colors.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${event.start.substring(5)} · ${event.kind}",
                        style = PocketTypography.label,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        // 当天课程：周次口径与课表页完全一致（week <= 0 视为不在学期内，自然没有课）
        val data = Store.data
        val term = data.activeTerm
        val week = term?.let { termWeekOf(it, selected) } ?: 0
        val dayCourses = if (week > 0) {
            coursesOnDay(data.activeCourses, data.slots, selected, week)
        } else {
            emptyList()
        }
        if (dayCourses.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当天课程", style = PocketTypography.caption, color = colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Text("${dayCourses.size} 节", style = PocketTypography.label, color = colors.textTertiary)
            }
            dayCourses.forEach { course ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        minuteText(courseStartMinute(course, data.slots)) +
                            " - " + minuteText(courseEndMinute(course, data.slots)),
                        style = PocketTypography.label,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        course.shortName.ifBlank { course.name },
                        style = PocketTypography.caption,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.weight(1f))
                    if (course.room.isNotBlank()) {
                        Text(course.room, style = PocketTypography.label, color = colors.textTertiary)
                    }
                }
            }
        }
    }
}
