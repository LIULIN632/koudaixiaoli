package com.pocket.xiaoli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.calendar.CalendarAsset
import com.pocket.xiaoli.data.calendar.CalendarPhase
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.calendar.components.CalendarHeaderCard
import com.pocket.xiaoli.ui.calendar.components.EventRow
import com.pocket.xiaoli.ui.calendar.components.MonthEventList
import com.pocket.xiaoli.ui.calendar.components.MonthGrid
import com.pocket.xiaoli.ui.calendar.components.SelectedDayCard
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.util.CalendarUtils
import com.pocket.xiaoli.util.CalibrationTarget
import com.pocket.xiaoli.util.DayMark
import com.pocket.xiaoli.util.parseSemesterStart
import java.time.LocalDate
import java.time.YearMonth

/**
 * 校历页（独立页，从服务页那一格进来，不带底栏）。
 *
 * 接线层：只负责从 `AppContainer` 取状态、把「按校历校准」写回去。
 * 画面本身在 [CampusCalendarContent] —— 那层是 `internal` 且**只吃普通参数**，
 * 这样截图测试不需要 `AppContainer`（也不需要 Robolectric 去起一个 Application）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusCalendarScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTimetableWeek: (week: Int) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    // 资产是随包的，读一次就够；`by lazy` 在仓库里，冷启动不会碰文件 IO。
    val asset = remember { container.calendarRepository.asset() }
    val holidays by container.holidays.collectAsStateWithLifecycle()
    val semesterStart by container.semesterStart.collectAsStateWithLifecycle()
    val semesterWeeks by container.semesterWeeks.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校历", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        },
    ) { padding ->
        CampusCalendarContent(
            asset = asset,
            holidays = holidays,
            today = today,
            semesterStartRaw = semesterStart,
            semesterWeeks = semesterWeeks,
            onCalibrate = { target ->
                container.setSemesterStart(target.semesterStart.toString())
                container.setSemesterWeeks(target.semesterWeeks)
            },
            onOpenTimetableWeek = onOpenTimetableWeek,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * 校历页的画面本体 —— 纯参数，无 `AppContainer`、无 `LocalDate.now()`。
 *
 * 截图测试与肉眼验收都打这一层：把 `today` 钉在 2026-10-16，
 * 画面就必须每次都一样（无限动画、相对时间、眨眼三者都不在这里）。
 */
@Composable
internal fun CampusCalendarContent(
    asset: CalendarAsset?,
    holidays: List<HolidayDay>,
    today: LocalDate,
    semesterStartRaw: String,
    semesterWeeks: Int,
    onCalibrate: (CalibrationTarget) -> Unit,
    onOpenTimetableWeek: (week: Int) -> Unit,
    modifier: Modifier = Modifier,
    initialMonth: YearMonth = YearMonth.from(today),
    initialSelected: LocalDate? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    var month by remember { mutableStateOf(initialMonth) }
    var selected by remember { mutableStateOf(initialSelected) }

    if (asset == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.surfaceAlt),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "本校暂未收录校历",
                fontSize = 14.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val phase = CalendarUtils.calendarPhase(asset, today)
    val lessonWeek = CalendarUtils.activeTerm(asset, today)?.let { CalendarUtils.legalWeekOf(it, today) }
    val isVacation = phase == CalendarPhase.WINTER_VACATION || phase == CalendarPhase.SUMMER_VACATION

    // 假期周显示「寒假」/「暑假」而不是第 21–26 周（用户定案 B）：
    // 校历把假期编进了周次表，但「第 23 周」对用户没有意义，而「寒假」有。
    val eyebrow = buildString {
        append(asset.academicYear)
        append(" 学年")
        when {
            isVacation -> append(" · ").append(phase.label)
            lessonWeek != null -> append(" · 第 ").append(lessonWeek).append(" 周")
            else -> Unit
        }
    }

    val target = CalendarUtils.calibrationTarget(asset, today)
    val needsCalibration = target != null &&
        CalendarUtils.needsCalibration(target, parseSemesterStart(semesterStartRaw), semesterWeeks)

    val cells = remember(month) { CalendarUtils.monthGrid(month) }
    val markOf: (LocalDate) -> DayMark? = { date ->
        CalendarUtils.dayMark(asset, holidays, date)
    }
    // 只跟这三个键走：节假日列表一变（在线源补进来），格子上的小字要跟着变。
    val rows = remember(asset, month, today, holidays) { rowsFor(asset, month, today) }

    val selectedDate = selected
    val selectedWeek = selectedDate?.let { date ->
        CalendarUtils.activeTerm(asset, date)?.let { CalendarUtils.legalWeekOf(it, date) }
    }
    val selectedPhase = selectedDate?.let { CalendarUtils.calendarPhase(asset, it) }
    // 只有落在「课表排得到的周次」里才给入口：寒假是第 21–26 周，跳过去是一片空课表，
    // 那比不给入口更让人困惑。
    val jumpableWeek = selectedWeek?.takeIf { it <= semesterWeeks }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceAlt)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = skin.spaceLg),
    ) {
        Spacer(Modifier.height(skin.spaceLg))
        CalendarHeaderCard(
            eyebrow = eyebrow,
            headline = "今天 ${CalendarFormat.monthDayWeekday(today)}",
            phase = phase,
            onCalibrate = if (needsCalibration) {
                { target?.let(onCalibrate) }
            } else {
                null
            },
        )

        Spacer(Modifier.height(skin.spaceLg))
        MonthNavigator(
            month = month,
            onPrev = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
            onToday = {
                month = YearMonth.from(today)
                selected = today
            },
        )

        Spacer(Modifier.height(skin.spaceSm))
        MonthGrid(
            cells = cells,
            today = today,
            selected = selected,
            markOf = markOf,
            onSelect = { date ->
                selected = date
                // 点到补位的邻月灰格时跟着翻月 —— 否则选中态在一个看不见的月份里。
                if (YearMonth.from(date) != month) month = YearMonth.from(date)
            },
        )

        if (selectedDate != null) {
            Spacer(Modifier.height(skin.spaceMd))
            SelectedDayCard(
                date = selectedDate,
                weekLabel = selectedWeek?.let { "第 $it 周" }
                    ?: selectedPhase?.takeIf {
                        it == CalendarPhase.WINTER_VACATION || it == CalendarPhase.SUMMER_VACATION
                    }?.label,
                eventTitles = CalendarUtils.eventsOn(asset, selectedDate).map { it.title },
                onOpenTimetable = jumpableWeek?.let { week -> { onOpenTimetableWeek(week) } },
            )
        }

        Spacer(Modifier.height(skin.spaceLg))
        MonthEventList(
            yearMonthLabel = "${month.year}年${month.monthValue}月",
            rows = rows,
        )
        Spacer(Modifier.height(skin.spaceXl))
    }
}

/** 当月事件 → 列表行。 */
private fun rowsFor(asset: CalendarAsset, month: YearMonth, today: LocalDate): List<EventRow> =
    CalendarUtils.eventsInMonth(asset, month).map { EventRow(event = it, isToday = it.covers(today)) }

@Composable
private fun MonthNavigator(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月", tint = colors.textSecondary)
            }
            Text(
                text = "${month.year}年${month.monthValue}月",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月", tint = colors.textSecondary)
            }
        }
        Text(
            text = "回到今天",
            fontSize = 12.sp,
            color = colors.accentMain,
            modifier = Modifier
                .clickable(onClick = onToday)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}
