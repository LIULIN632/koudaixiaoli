package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.loadWeather
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.clearCompletedTodos
import com.pocket.xiaoli.desktop.state.clearSampleCourses
import com.pocket.xiaoli.desktop.state.clearSampleTodos
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.data.Course
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.data.Todo
import com.pocket.xiaoli.desktop.logic.courseEndMinute
import com.pocket.xiaoli.desktop.logic.courseStartMinute
import com.pocket.xiaoli.desktop.logic.coursesOnDay
import com.pocket.xiaoli.desktop.logic.findUpcomingCourse
import com.pocket.xiaoli.desktop.logic.minuteText
import com.pocket.xiaoli.desktop.logic.weeksLabel
import com.pocket.xiaoli.desktop.logic.weeklyCourseCount
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.ConfirmDialog
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.MarkdownText
import com.pocket.xiaoli.desktop.ui.components.MascotBadge
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketMascot
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SectionHeader
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.rememberPressState
import com.pocket.xiaoli.core.weather.formatTemperature
import com.pocket.xiaoli.core.weather.windDirectionLabel
import com.pocket.xiaoli.core.weather.windScale
import com.pocket.xiaoli.desktop.ui.components.StatTile
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.LocalDarkMode
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.courseBgColor
import com.pocket.xiaoli.desktop.ui.theme.courseTextColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/**
 * 首页：问候 + 下一节课 + 今日安排 + 待办 + 校历提醒。
 *
 * ## 与移动端首页的差别（有意为之）
 *
 * 移动端首页刻意"只有下一节课和待办"（约束见 memory/02-feature-map.md），因为手机屏幕
 * 小、信息密度低。桌面窗口宽，所以这里把「今日安排」和「校历提醒」一并平铺 —— 但
 * **仍然不放广告位、不放说明气泡**。
 */
@Composable
fun HomeScreen(state: AppState) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(30_000)
        }
    }
    val today = LocalDate.now()
    val week = Store.currentWeek(today)
    val term = state.data.activeTerm
    val slots = state.data.slots
    val todayCourses = coursesOnDay(state.data.activeCourses, slots, today, week)
    val upcoming = findUpcomingCourse(state.data.activeCourses, slots, week, today, now)
    val openTodos = state.data.todos.filter { !it.done }
    val doneCount = state.data.todos.count { it.done }
    val groups = com.pocket.xiaoli.desktop.logic.groupTodos(state.data.todos, today)
    val weekCourses = weeklyCourseCount(state.data.activeCourses, week, term?.totalWeeks ?: 20)
    val settings = state.data.settings
    var pendingTodo by remember { mutableStateOf<Todo?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // ── 问候 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Format.greeting(now)}，${settings.studentName.ifBlank { "同学" }}",
                    style = PocketTypography.screenTitle,
                    color = colors.textPrimary,
                )
            }
            // 日期与周次左侧栏常驻，这里不再复述；原来那颗「浅色/深色」是个点了没反应的标签，
            // 现在直接让它切换主题。
            SecondaryButton(
                text = if (dark) "深色" else "浅色",
                icon = if (dark) PocketIcons.Moon else PocketIcons.Sun,
            ) { state.setSetting { it.copy(darkMode = !dark) } }
        }

        // ── 天气（解析与节流在共享核心，缓存只存本机）──
        WeatherStrip(state)

        // ── 下一节课 hero ──
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PocketCard(modifier = Modifier.weight(1.6f), padding = 22.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentColor())
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("下一节课", style = PocketTypography.caption, color = colors.textTertiary)
                            if (upcoming != null) {
                                Spacer(Modifier.width(8.dp))
                                TagChip(text = upcoming.dayLabel, color = accentColor(), background = accentSoftColor())
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (upcoming != null) {
                            Text(
                                upcoming.course.name,
                                style = PocketTypography.sectionTitle,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(PocketIcons.Clock, accentColor(), accentSoftColor(), 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${upcoming.startTime} - ${upcoming.endTime}",
                                    style = PocketTypography.body,
                                    color = colors.textSecondary,
                                )
                                if (upcoming.course.room.isNotBlank()) {
                                    Spacer(Modifier.width(14.dp))
                                    IconBadge(PocketIcons.Pin, colors.textSecondary, colors.surfaceAlt, 26.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(upcoming.course.room, style = PocketTypography.body, color = colors.textSecondary)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "第 ${upcoming.course.startSlot}-${upcoming.course.endSlot} 节" +
                                    (if (upcoming.course.teacher.isNotBlank()) " · ${upcoming.course.teacher}" else "") +
                                    " · " + weeksLabel(upcoming.course, term?.totalWeeks ?: 20),
                                style = PocketTypography.caption,
                                color = colors.textTertiary,
                            )
                        } else {
                            Text("接下来 7 天没有课", style = PocketTypography.sectionTitle, color = colors.textPrimary)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (term == null) "先在「我的 → 学期设置」里填学期开始日，课表才有周次。"
                                else "享受空档期，或者去课表里检查一下导入的数据。",
                                style = PocketTypography.body,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("查看课表", icon = PocketIcons.Timetable) {
                                state.tab = 1
                            }
                            SecondaryButton("问鲤鱼", icon = PocketIcons.Chat) {
                                state.tab = 2
                            }
                        }
                    }
                    Box(Modifier.width(170.dp), contentAlignment = Alignment.Center) {
                        PocketMascot(Modifier.size(150.dp))
                    }
                }
            }
        }

        // 数据来源提示：不让「演示数据」被当成真课表（用户反馈过这一点）
        val origin = state.data.origin
        val courseCount = state.data.activeCourses.size
        val isSample = origin == "seed"
        // 老数据文件没有 origin（""）：既不能当示例，也不能当完整导入，只在课很少时提醒
        val looksIncomplete = !isSample && courseCount in 1..4
        if (isSample || looksIncomplete) {
            PocketCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        if (isSample) PocketIcons.Info else PocketIcons.Info,
                        if (isSample) accentColor() else colors.warning,
                        if (isSample) accentSoftColor() else colors.warning.copy(alpha = 0.12f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isSample) "当前是内置示例数据" else "只导入了 $courseCount 门课，可能不完整",
                            style = PocketTypography.cardTitle,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (isSample) {
                                "课表与待办都是演示内容，不代表你的真实课表。从教务导入后才会显示你自己的课。"
                            } else {
                                "正方课表可能分页或需要选学年学期；可以重新导入一次，或在课表里手动补课。"
                            },
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    PrimaryButton(
                        if (isSample) "从教务导入" else "重新导入",
                        icon = PocketIcons.Refresh,
                    ) { state.openLogin() }
                }
                // 占位内容可以就地清掉（清前自动备份），不让假课表一直挂在首页
                val sampleCourses = state.data.courses.count { it.id in Store.sampleCourseIds() }
                val sampleTodos = state.data.todos.count { it.id in Store.sampleTodoIds() }
                if (sampleCourses > 0 || sampleTodos > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("清掉占位内容：", style = PocketTypography.caption, color = colors.textTertiary)
                        if (sampleCourses > 0) {
                            SecondaryButton("清空示例课程（$sampleCourses）", icon = PocketIcons.Trash) { state.clearSampleCourses() }
                        }
                        if (sampleTodos > 0) {
                            SecondaryButton("清空示例待办（$sampleTodos）", icon = PocketIcons.Trash) { state.clearSampleTodos() }
                        }
                    }
                }
            }
        }

        // ── 统计 ──
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCard("今日课程", "${todayCourses.size}", "门", Modifier.weight(1f), accentColor())
            StatCard("本周课程", "$weekCourses", "节", Modifier.weight(1f), colors.success)
            StatCard("待办", "${openTodos.size}", "项未完成", Modifier.weight(1f), colors.warning)
            StatCard("累计待办", "${state.data.todos.size}", "项", Modifier.weight(1f), colors.info)
        }

        // ── 今日安排 + 待办 ──
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
            PocketCard(Modifier.weight(1.35f)) {
                SectionHeader(
                    title = "今日安排",
                    // 空的时候下面就是「今天没有课」的空状态，标题别再重复一遍
                    subtitle = if (todayCourses.isEmpty()) null else "共 ${todayCourses.size} 门课 · ${Format.weekday(today.dayOfWeek.value)}",
                )
                Spacer(Modifier.height(12.dp))
                if (todayCourses.isEmpty()) {
                    EmptyState(
                        PocketIcons.CalendarDays,
                        "今天没有课",
                        action = { SecondaryButton("看本周课表", icon = PocketIcons.Timetable) { state.tab = 1 } },
                    )
                } else {
                    todayCourses.forEachIndexed { index, course ->
                        TodayCourseRow(course, slots, now)
                        if (index != todayCourses.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }

            PocketCard(Modifier.weight(1f)) {
                SectionHeader(
                    title = "待办",
                    // 下面每个分桶都带自己的条数，「还剩 N 项」是把它们再加一遍
                    subtitle = if (openTodos.isEmpty()) "都完成了" else null,
                    trailing = {
                        if (doneCount > 0) {
                            com.pocket.xiaoli.desktop.ui.components.GhostIconButton(PocketIcons.Trash, contentDescription = "清空已完成", size = 30.dp) {
                                state.clearCompletedTodos()
                            }
                        }
                        com.pocket.xiaoli.desktop.ui.components.GhostIconButton(PocketIcons.Plus, size = 30.dp) {
                            state.showTodoEditor = true
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                if (state.data.todos.isEmpty()) {
                    EmptyState(
                        PocketIcons.TodoList,
                        "还没有待办",
                        action = { SecondaryButton("新建待办", icon = PocketIcons.Plus) { state.showTodoEditor = true } },
                    )
                } else {
                    // 按截止日分桶展示：逾期在最前，已完成折成一行（能一键清）
                    var shown = 0
                    groups.filter { it.bucket != com.pocket.xiaoli.desktop.logic.TodoBucket.DONE }.forEach { group ->
                        if (shown < 6) {
                            Text(
                                "${group.label} · ${group.count}",
                                style = PocketTypography.label,
                                color = if (group.bucket == com.pocket.xiaoli.desktop.logic.TodoBucket.OVERDUE) colors.danger else colors.textTertiary,
                                modifier = Modifier.padding(top = if (shown == 0) 0.dp else 8.dp, bottom = 4.dp),
                            )
                            group.todos.take(6 - shown).forEach { todo ->
                                TodoRow(
                                    todo,
                                    onToggle = { state.toggleTodo(todo.id) },
                                    onOpen = { state.selectedTodo = todo },
                                    onDelete = { pendingTodo = todo },
                                )
                            }
                            shown += group.count.coerceAtMost(6 - shown)
                        }
                    }
                    val openTotal = state.data.todos.count { !it.done }
                    if (openTotal > shown) {
                        Spacer(Modifier.height(6.dp))
                        Text("还有 ${openTotal - shown} 项未显示…", style = PocketTypography.caption, color = colors.textTertiary)
                    }
                    if (doneCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("已完成 $doneCount 项", style = PocketTypography.caption, color = colors.textTertiary, modifier = Modifier.weight(1f))
                            SecondaryButton("清空已完成") { state.clearCompletedTodos() }
                        }
                    }
                }
            }
        }

        // ── 校历提醒 ──
        val nextEvent = Assets.calendar?.events
            ?.filter { it.start >= Format.ymd(today) }
            ?.minByOrNull { it.start }
        PocketCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(PocketIcons.CalendarDays, colors.info, colors.info.copy(alpha = 0.12f))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (nextEvent != null) nextEvent.title else "校历",
                        style = PocketTypography.cardTitle,
                        color = colors.textPrimary,
                    )
                    if (nextEvent != null) {
                        val date = runCatching { LocalDate.parse(nextEvent.start) }.getOrNull()
                        Text(
                            (date?.let { "${Format.monthDay(it)} · ${Format.weekday(it.dayOfWeek.value)}" } ?: nextEvent.start) +
                                " · " + (date?.let { Format.relativeDays(it, today) } ?: ""),
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    } else {
                        Text("没有可用的校历数据", style = PocketTypography.caption, color = colors.textTertiary)
                    }
                }
                SecondaryButton("看校历") { state.tab = 3 }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "数据只保存在本机（${Store.dataDir.absolutePath}）· 不上传任何服务器",
            style = PocketTypography.label,
            color = colors.textTertiary,
        )
    }

    // 删待办同样要二次确认
    pendingTodo?.let { todo ->
        ConfirmDialog(
            title = "删除待办",
            message = "「${todo.title}」将被删除，不能恢复。",
            onConfirm = {
                state.deleteTodo(todo.id)
                pendingTodo = null
                state.notify("待办已删除")
            },
            onDismiss = { pendingTodo = null },
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, hint: String, modifier: Modifier = Modifier, accent: Color) {
    val colors = pocketColors()
    // 原来四张都是纯白卡，整排像没上色；给每张一条自己的主题色脊，数字也用同色。
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.card))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDivider, RoundedCornerShape(Radii.card))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        StatTile(label = label, value = value, hint = hint, accent = accent)
    }
}

@Composable
private fun TodayCourseRow(course: Course, slots: List<com.pocket.xiaoli.desktop.data.TimeSlot>, now: LocalTime) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val start = courseStartMinute(course, slots)
    val end = courseEndMinute(course, slots)
    val nowMinute = now.hour * 60 + now.minute
    val ongoing = nowMinute in start until end
    val finished = nowMinute >= end
    val bg = courseBgColor(course.colorIndex, dark)
    val fg = courseTextColor(course.colorIndex, dark)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.chip))
            .background(if (ongoing) accentSoftColor() else colors.surfaceAlt.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (finished) bg.copy(alpha = 0.5f) else bg)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(76.dp)) {
            Text(minuteText(start), style = PocketTypography.caption.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary)
            Text(minuteText(end), style = PocketTypography.label, color = colors.textTertiary)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    course.name,
                    style = PocketTypography.body.copy(fontWeight = FontWeight.Medium),
                    color = if (finished) colors.textTertiary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ongoing) {
                    Spacer(Modifier.width(8.dp))
                    TagChip("进行中", color = Color.White, background = accentColor())
                }
            }
            Text(
                listOfNotNull(
                    course.room.takeIf { it.isNotBlank() },
                    course.teacher.takeIf { it.isNotBlank() },
                    fg.let { weeksLabel(course, 20) },
                ).joinToString(" · "),
                style = PocketTypography.label,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodoRow(todo: Todo, onToggle: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit) {
    val colors = pocketColors()
    val accent = accentColor()
    val rowPress = rememberPressState()
    val checkPress = rememberPressState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 勾选框原来是一块看不出能点的灰方，且只有 18dp；现在外圈 26dp 是热区，
        // 里面用描边表示"没勾"，悬停描强调色。
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .hoverable(checkPress.source)
                .clickable(interactionSource = checkPress.source, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (todo.done) colors.success.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.5.dp,
                        when {
                            todo.done -> colors.success
                            checkPress.pressed -> accent
                            checkPress.hovered -> accent.copy(alpha = 0.7f)
                            else -> colors.borderDivider
                        },
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (todo.done) {
                    androidx.compose.material3.Icon(
                        PocketIcons.Check,
                        contentDescription = "已完成",
                        tint = colors.success,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (rowPress.hovered) colors.surfaceAlt.copy(alpha = 0.6f) else Color.Transparent)
                .hoverable(rowPress.source)
                .clickable(interactionSource = rowPress.source, indication = null, onClick = onOpen)
                .padding(vertical = 2.dp),
        ) {
            Text(
                todo.title,
                style = PocketTypography.body,
                color = if (todo.done) colors.textTertiary else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(
                todo.due.takeIf { it.isNotBlank() }?.let { "截止 $it" },
                todo.courseName.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = PocketTypography.label, color = colors.textTertiary)
            }
        }
        if (todo.priority > 0) {
            TagChip(
                text = if (todo.priority == 2) "紧急" else "重要",
                color = if (todo.priority == 2) colors.danger else colors.warning,
                background = (if (todo.priority == 2) colors.danger else colors.warning).copy(alpha = 0.12f),
            )
            Spacer(Modifier.width(6.dp))
        }
        com.pocket.xiaoli.desktop.ui.components.GhostIconButton(PocketIcons.Close, size = 26.dp, onClick = onDelete)
    }
}

/**
 * 首页天气条。
 *
 * 坐标是**城市级（烟台）**，不是校区实测 —— 共享核心里写着「不要用猜测值填坐标」，
 * 所以这里如实标「烟台」，不假装是文一教楼前的温度。
 */
@Composable
private fun WeatherStrip(state: AppState) {
    val colors = pocketColors()
    val scope = rememberCoroutineScope()
    val enabled = state.data.settings.homeWeatherEnabled
    LaunchedEffect(enabled) { if (enabled) state.loadWeather() }
    if (!enabled) return

    val snapshot = state.weather
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (snapshot == null) {
            Text(
                if (state.weatherBusy) "正在取天气…" else "还没有天气数据（点右边刷新）",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        } else {
            val current = snapshot.current
            Icon(
                if (current.isDay) PocketIcons.Sun else PocketIcons.Moon,
                contentDescription = null,
                tint = accentColor(),
                modifier = Modifier.size(15.dp),
            )
            Text(
                buildString {
                    append("烟台 · ")
                    append(current.label)
                    append(' ')
                    append(formatTemperature(current.temperature))
                    append("℃（体感 ")
                    append(formatTemperature(current.apparentTemperature))
                    append("℃）· ")
                    append(windDirectionLabel(current.windDirection))
                    append("风 ")
                    append(windScale(current.windSpeed))
                    append(" 级")
                },
                style = PocketTypography.caption,
                color = colors.textSecondary,
            )
            snapshot.hourly.take(6).maxOfOrNull { it.precipitationProbability }?.takeIf { it > 0 }?.let {
                TagChip(
                    text = "未来 6 小时降水 $it%",
                    color = colors.info,
                    background = colors.info.copy(alpha = 0.12f),
                )
            }
        }
        state.weatherNote?.let {
            Text(it, style = PocketTypography.label, color = colors.warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        GhostIconButton(
            PocketIcons.Refresh,
            contentDescription = "刷新天气",
            size = 26.dp,
            tint = colors.textSecondary,
        ) { scope.launch { state.loadWeather(force = true) } }
    }
}
