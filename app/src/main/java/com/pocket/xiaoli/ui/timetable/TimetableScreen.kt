package com.pocket.xiaoli.ui.timetable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import com.pocket.xiaoli.ui.components.MiuiMenuMaxHeight
import com.pocket.xiaoli.ui.components.MiuiMenuPopup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.worstDueStage
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import androidx.lifecycle.viewmodel.compose.viewModel

import com.pocket.xiaoli.ui.timetable.components.PeriodGrid
import com.pocket.xiaoli.ui.map.RoomFloorMapPanel
import com.pocket.xiaoli.ui.timetable.components.WeekHeader
import com.pocket.xiaoli.ui.timetable.components.TaskPreviewDialog
import com.pocket.xiaoli.ui.profile.SemesterSettingsPage
import com.pocket.xiaoli.ui.components.SemesterCalibrateDialog
import com.pocket.xiaoli.ui.components.TimetableEmptyState
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.util.HolidayCalendar
import com.pocket.xiaoli.util.HolidayMark
import com.pocket.xiaoli.util.HolidaySchedule
import com.pocket.xiaoli.util.parseSemesterStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TimetableScreen(
    onSubPageChange: (Boolean) -> Unit = {},
    onOpenImport: () -> Unit = {},
    onOpenCampusMap: (String) -> Unit = {},
    onOpenRoomLayout: (String) -> Unit = {},
    onOpenCourseNavigation: (String) -> Unit = {},
) {
    val skin = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val viewModel: TimetableViewModel = viewModel(
        factory = TimetableViewModel.Factory(app.repository, container)
    )
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val timeSlots by viewModel.allTimeSlots.collectAsStateWithLifecycle()
    val currentWeek by viewModel.currentWeek.collectAsStateWithLifecycle()
    val semesterTotalWeeks by viewModel.semesterTotalWeeks.collectAsStateWithLifecycle()
    // 学期是一级维度：顶栏给一个就地切换入口，不必每次都进「设置 → 学期设置」
    val allTerms by viewModel.allTerms.collectAsStateWithLifecycle()
    val activeTermId by viewModel.activeTermId.collectAsStateWithLifecycle()
    val activeTerm = allTerms.firstOrNull { it.id == activeTermId }
    val editingCourse by viewModel.editingCourse.collectAsStateWithLifecycle()
    val showEditor by viewModel.showEditor.collectAsStateWithLifecycle()
    var showDeleteRangeDialog by remember { mutableStateOf(false) }
    var pendingDeleteCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // 课表 / 地图双模式。地图模式只是楼层平面图，**不与课表共用时间语义**
    var viewMode by remember { mutableStateOf(TimetableMode.WEEK) }
    // 地图模式的网格尺寸提升到父级，切回课表再切回来不会丢。
    var mapGridColumns by rememberSaveable { mutableIntStateOf(4) }
    var mapGridRows by rememberSaveable { mutableIntStateOf(4) }
    val roomCells by container.roomCells.collectAsStateWithLifecycle()
    val mapCampus by container.selectedCampus.collectAsStateWithLifecycle()

    val nonCurrentWeekCourses by viewModel.nonCurrentWeekCourses.collectAsStateWithLifecycle()
    val filteredCourses by viewModel.filteredCourses.collectAsStateWithLifecycle()

    // Appearance settings
    val showTeacher by container.showTeacher.collectAsStateWithLifecycle()
    val showRoom by container.showRoom.collectAsStateWithLifecycle()
    val showCampus by container.showCampus.collectAsStateWithLifecycle()
    val showSlotTime by container.showSlotTime.collectAsStateWithLifecycle()
    val textCentered by container.textCentered.collectAsStateWithLifecycle()
    val cornerRadius by container.cornerRadius.collectAsStateWithLifecycle()
    val gridHeight by container.gridHeight.collectAsStateWithLifecycle()
    val gridOpacity by container.gridOpacity.collectAsStateWithLifecycle()
    val gridTextSize by container.gridTextSize.collectAsStateWithLifecycle()
    val showOddEven by container.showOddEven.collectAsStateWithLifecycle()
    val borderStyle by container.borderStyle.collectAsStateWithLifecycle()
    val showNonCurrentWeek by container.showNonCurrentWeek.collectAsStateWithLifecycle()
    val vibrationMode by container.vibrationMode.collectAsStateWithLifecycle()
    val gridBgColor by container.gridBgColor.collectAsStateWithLifecycle()
    val otherWeekAlpha by container.otherWeekAlpha.collectAsStateWithLifecycle()
    val verticalLayout by container.verticalLayout.collectAsStateWithLifecycle()
    val wallpaperUri by container.wallpaperUri.collectAsStateWithLifecycle()

    // Wallpaper bitmap loading（降采样解码防 OOM）
    var wallpaperBitmap by remember(wallpaperUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(wallpaperUri) {
        if (wallpaperUri.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val bitmap = com.pocket.xiaoli.util.decodeScaledWallpaper(context, android.net.Uri.parse(wallpaperUri))
                if (bitmap != null) wallpaperBitmap = bitmap.asImageBitmap()
            }
        } else {
            wallpaperBitmap = null
        }
    }

    val today = LocalDate.now()
    val todayDay = today.dayOfWeek.value
    val semesterStart = container.semesterStart.collectAsStateWithLifecycle().value
    val isCurrentWeek by remember { derivedStateOf { currentWeek == container.autoCurrentWeek() } }

    val startOfWeek by remember(semesterStart, currentWeek, today, todayDay) {
        derivedStateOf {
            parseSemesterStart(semesterStart)
                ?.plusWeeks((currentWeek - 1).toLong())
                ?: today.plusDays((-(todayDay - 1) + (currentWeek - 1) * 7).toLong())
        }
    }
    val endOfWeek by remember { derivedStateOf { startOfWeek.plusDays(6) } }
    // 节假日 / 调休角标（数据源 holiday-cn，AppContainer 启动预热）
    val holidays = container.holidays.collectAsStateWithLifecycle().value
    val holidayMarks by remember(holidays, startOfWeek) {
        derivedStateOf { HolidayCalendar.weekMarks(holidays, startOfWeek) }
    }
    val fmt = DateTimeFormatter.ofPattern("M/d")

    // 调休补课：用户配置 origin -> target，本周按 target 的星期渲染；放假列整列不显示课程
    val specialWorkDays by container.specialWorkDays.collectAsStateWithLifecycle()
    val specialWorkDayMap = remember(specialWorkDays) {
        specialWorkDays.associate { it.originDate to it.targetDate }
    }
    val weekSchedule by remember(holidays, specialWorkDayMap, startOfWeek) {
        derivedStateOf { HolidaySchedule.weekSchedule(holidays, specialWorkDayMap, startOfWeek) }
    }
    val columnMapping by remember(weekSchedule) {
        derivedStateOf { HolidaySchedule.columnMapping(weekSchedule) }
    }
    val hasDayRemap = remember(columnMapping) { columnMapping.any { (day, column) -> column != day } }
    val displayCourses = remember(filteredCourses, columnMapping, hasDayRemap) {
        if (hasDayRemap) HolidaySchedule.remapCoursesForWeek(filteredCourses, columnMapping) else filteredCourses
    }
    val displayNonCurrentCourses = remember(nonCurrentWeekCourses, columnMapping, hasDayRemap) {
        if (hasDayRemap) HolidaySchedule.remapCoursesForWeek(nonCurrentWeekCourses, columnMapping) else nonCurrentWeekCourses
    }
    // 周头角标：调休补课日显示「补周三」
    val displayHolidayMarks = remember(holidayMarks, weekSchedule) {
        if (weekSchedule.none { it.isAdjusted }) holidayMarks
        else {
            val merged = holidayMarks.toMutableMap()
            weekSchedule.filter { it.isAdjusted }.forEach { day ->
                merged[day.date] = HolidayMark("补" + HolidaySchedule.weekdayLabel(day.effectiveDayOfWeek), false)
            }
            merged
        }
    }

    // Task state（作业列表已移出本页：与首页重复，入口统一由首页承载）
    val schemeId by container.activeSchemeId.collectAsStateWithLifecycle()
    // WithLifecycle 版本：`getTasksByScheme` 是 Room 冷 Flow，写在参数里会每次重组
    // 新建实例并重新注册 InvalidationTracker 观察者（旧的未必及时释放）。
    val allTasks by app.repository.getTasksByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // Active homework course names (non-completed) — for asterisk marks on cards
    //
    // ⚠️ 位置很重要：必须写在 `if (showSemesterSettings) { ...; return }` **之前**。
    // 它原本在 return 之后，于是"打开学期设置页时这次组合少一个 collect、关掉时多一个"——
    // Composable 内的条件调用会让 Compose 的槽位表错位。提前到所有 early return 之前即可。
    val activeHomework by app.repository.getActiveHomework(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 课程名 → 这门课**最紧**那条作业的截止档位。
    // ⚠️ 用 Map 而不是 Set：**键的存在就是"有作业"**，值可以为 null（没写日期，或写了
    // 「下周交」这种解析不出的话）—— 那种仍然要给高亮边框，只是不升到危险色。
    val homeworkStageByCourse = remember(activeHomework) {
        activeHomework.groupBy { it.courseName }
            .mapValues { (_, items) -> worstDueStage(items.map { it.dueDate }) }
    }

    // ⚠️ 同理提到 early return 之前：`LaunchedEffect` 属于"按槽位记账"的调用，
    // 打开/关闭学期设置页时它会插入或消失，导致同一 Composable 的调用数量前后不一致。
    LaunchedEffect(Unit) {
        // 时间段表读空时补默认节次表（写回数据库，让小组件/提醒/导出也拿得到节次时间）。
        // ⚠️ 判空必须读 Flow 的**第一次真实发射**，不能读 `viewModel.allTimeSlots.value`：
        // 那个 StateFlow 的初值本来就是空表，数据库还没回话时它也是"空" —— 于是每次进课表页
        // 都会把 12 行默认时间 REPLACE 回表里，用户在「节次管理」改过的时间被悄悄覆盖。
        if (app.repository.allTimeSlots.first().isEmpty()) {
            app.repository.initTimeSlots()
        }
        viewModel.goToToday()
    }
    val scope = rememberCoroutineScope()
    var showTaskPreview by remember { mutableStateOf(false) }
    var showSemesterSettings by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    val calibrationPending by container.calibrationPending.collectAsStateWithLifecycle()

    // ⚠️ 上报给底栏的必须是「**有没有子页开着**」这个完整布尔，不能直接写某个子页开关。
    //
    // 这里曾经是 `LaunchedEffect(showSemesterSettings) { onSubPageChange(showSemesterSettings) }` ——
    // 只有一个子页时它恰好等价，但它是错的：这个表达式描述的是"学期设置**开着吗**"，
    // 而不是"我在子页里吗"。等这里出现第二个子页（比如课程详情、批量编辑），
    // 漏进表达式就会变成"子页开着却显示底栏"，而错误只在真机上看得出来。
    //
    // 写法对齐 `TreasureBoxScreen.subPageVisible`：先算出完整语义，再上报一次。
    val subPageVisible = showSemesterSettings
    LaunchedEffect(subPageVisible) { onSubPageChange(subPageVisible) }
    BackHandler(enabled = showSemesterSettings) { showSemesterSettings = false }

    // 校历页「这天的课」→ 跳到那一周。
    //
    // ⚠️ 这段必须排在上面 `LaunchedEffect(Unit) { … viewModel.goToToday() }` **之后**：
    // 两个 effect 都挂 `Unit`，启动顺序就是代码里的声明顺序，而 `goToToday()` 会把周次
    // 重置回今天那一周 —— 排在它前面就会被它盖掉，跳转看起来"没反应"。
    // 这条顺序是被 `TimetableWeekJumpTest` 钉住的，改位置会让它红。
    val pendingWeekJump by container.pendingWeekJump.collectAsStateWithLifecycle()
    LaunchedEffect(pendingWeekJump) {
        val week = pendingWeekJump ?: return@LaunchedEffect
        viewModel.goToWeek(week)
        container.consumeWeekJump()
    }

    // 导入课表后若学期开学日期缺失 → 弹出周次校准引导
    if (calibrationPending) {
        SemesterCalibrateDialog(
            container = container,
            onDismiss = { container.clearSemesterCalibration() }
        )
    }

    // 周次快速跳转选择器（点击顶部「第N周」弹出）
    if (showWeekPicker) {
        WeekPickerSheet(
            currentWeek = currentWeek,
            totalWeeks = semesterTotalWeeks.coerceAtLeast(1),
            onSelect = { week ->
                showWeekPicker = false
                viewModel.goToWeek(week)
            },
            onSemesterSettings = {
                showWeekPicker = false
                showSemesterSettings = true
            },
            onDismiss = { showWeekPicker = false }
        )
    }

    // 学期设置是整页 `return`，课表会整体离开组合。滚动状态提到分流之前，
    // 返回时才停在原来那一屏（课程很多时尤其明显）。
    val gridScrollState = rememberScrollState()
    val agendaListState = rememberLazyListState()

    // Full-screen semester settings overlay
    if (showSemesterSettings) {
        SemesterSettingsPage(
            container = container,
            onBack = { showSemesterSettings = false }
        )
        return
    }

    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current

    val hasWallpaper = wallpaperBitmap != null

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = if (hasWallpaper) Color.Transparent else colors.surfaceBase,
            // 本页自己接管状态栏（见 AppNavigation 的 SELF_MANAGED_TOP_ROUTES）：
            // 顶栏把渐变画到状态栏底下，所以这里不能再让 Scaffold 按窗口 inset 顶一次，
            // 否则状态栏那一段会变成无内容的纯底色，渐变仍旧从栏下沿开始。
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (hasWallpaper && wallpaperBitmap != null) {
                Image(bitmap = wallpaperBitmap!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(
                    if (isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.65f)
                ))
            }
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // ── Top bar: compact ──
            val colorfulTopBar = currentIconPresentation().colorful
            // 纵向淡出而不是横向：状态栏那一段带一点强调色，到栏底回到 `surfaceCard`，
            // 与紧挨着的周头（同色）无缝相接；横向渐变会在两者交界处留下一条斜的色差，
            // 铺到状态栏底下之后那条色差会横穿整条系统栏，更显眼。
            val topBarBrush = if (colorfulTopBar) {
                Brush.verticalGradient(
                    listOf(
                        colors.accentMain.copy(alpha = if (isDark) 0.20f else 0.12f),
                        colors.surfaceCard,
                    )
                )
            } else null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (topBarBrush != null) Modifier.background(topBarBrush)
                        else Modifier.background(colors.surfaceCard)
                    )
                    // 背景先铺满整条（含状态栏），内容再让位 —— 这样顶栏的彩色渐变
                    // 从屏幕最上沿开始，与状态栏之间没有那条纯色带。
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val weekInteractionSource = remember { MutableInteractionSource() }
                val isWeekPressed by weekInteractionSource.collectIsPressedAsState()
                val weekScale by animateFloatAsState(
                    targetValue = if (isWeekPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f)
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .clickable(
                            interactionSource = weekInteractionSource,
                            indication = null
                        ) { showWeekPicker = true }
                        .scale(weekScale)
                        .padding(vertical = 1.dp, horizontal = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.accentMain)
                        )
                        Spacer(Modifier.width(7.dp))
                        // 「第 N 周」三段字号差很大。写成**一个** Text、三段 `SpanStyle`：
                        // 基线是文字排版自己保证的，不用再拿 `Alignment.Bottom` 配一个
                        // 手调的 `padding(bottom = 3.dp)` 去补 —— 那个偏移只在 22sp/11sp
                        // 这一组字号下成立，系统字体放大就错位。
                        // 左边那根 3×20dp 色条仍由外层 Row 垂直居中，互不干扰。
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontSize = 11.sp, color = colors.textTertiary)) {
                                    append("第")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.textPrimary,
                                    )
                                ) {
                                    append(currentWeek.toString())
                                }
                                withStyle(SpanStyle(fontSize = 11.sp, color = colors.textTertiary)) {
                                    append("周")
                                }
                            },
                            lineHeight = 24.sp,
                        )
                        // 学期切换：只有存在多个学期时才出现，避免单学期用户看到多余控件
                        if (allTerms.size > 1 && activeTerm != null) {
                            var showTermMenu by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    activeTerm.displayName,
                                    fontSize = 10.sp,
                                    color = colors.accentMain,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(colors.accentMain.copy(alpha = 0.10f))
                                        .clickable { showTermMenu = true }
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                                MiuiMenuPopup(
                                    expanded = showTermMenu,
                                    onDismissRequest = { showTermMenu = false },
                                    options = allTerms.map { it.displayName },
                                    selectedIndex = allTerms.indexOfFirst { it.id == activeTermId },
                                    maxHeight = MiuiMenuMaxHeight,
                                    onSelect = { index ->
                                        showTermMenu = false
                                        val term = allTerms.getOrNull(index) ?: return@MiuiMenuPopup
                                        if (term.id != activeTermId) viewModel.switchTerm(term)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        "${startOfWeek.format(fmt)} - ${endOfWeek.format(fmt)} · 共${semesterTotalWeeks.coerceAtLeast(1)}周",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⚠️ 这三个控件原先分别只有 28 / 26 / 28dp（M3 要求 48、本项目 linter 底线 44）。
                    // 课表是最高频的一页，而"上一周/下一周"是它唯一的周次导航 ——
                    // 28dp 的热区连拇指的一半都盖不住，还会误触隔壁那个。
                    //
                    // 用 `invisibleTouchTarget()` 抬高**热区**而不是可见尺寸：
                    // 视觉保持原样，占位从 28→44dp。见 `ui/theme/TouchTarget.kt` 的取舍说明。
                    IconButton(
                        onClick = { viewModel.prevWeek() },
                        modifier = Modifier.size(28.dp).invisibleTouchTarget(),
                    ) {
                        Icon(Icons.Default.ChevronLeft, "上一周", tint = colors.accentMain, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = { viewModel.goToToday() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp).invisibleTouchTarget(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentWeek) colors.surfaceHighlight else colors.accentMain,
                            // 不要写 Color.White：强调色用户可选，浅色档配白字约 1.9:1
                            contentColor = if (isCurrentWeek) colors.accentMain else onAccentColor()
                        )
                    ) { Text("今天", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    IconButton(
                        onClick = { viewModel.nextWeek() },
                        modifier = Modifier.size(28.dp).invisibleTouchTarget(),
                    ) {
                        Icon(Icons.Default.ChevronRight, "下一周", tint = colors.accentMain, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Week date header（课表 / 日程共用；地图模式无星期/节次轴）──
            if (viewMode == TimetableMode.MAP) {
                // 地图模式的头部：必须保留切换入口，否则切不回课表
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceCard)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                        ModeToggleButton(mode = viewMode) { viewMode = it }
                    }
                    Icon(Icons.Outlined.Map, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(skin.spaceSmall))
                    Text("楼层平面图 · 无星期 / 节次轴", fontSize = 12.sp, color = colors.textTertiary)
                }
            } else {
                WeekHeader(
                    currentWeek = currentWeek,
                    isCurrentWeek = isCurrentWeek,
                    semesterStart = semesterStart,
                    holidayMarks = displayHolidayMarks,
                    // 放进节次栏上方的 32dp 槽位，既对齐又利用空间
                    leadingContent = { ModeToggleButton(mode = viewMode) { viewMode = it } },
                    today = today,
                )

                // Subtle separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderDivider)
                )
            }

            // ── Course grid area ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (viewMode == TimetableMode.MAP) {
                    RoomFloorMapPanel(
                        allCells = roomCells,
                        campusId = mapCampus.id,
                        modifier = Modifier.fillMaxSize(),
                        columns = mapGridColumns,
                        rows = mapGridRows,
                        cellHeight = gridHeight.dp,
                        gap = 1.dp,
                        cornerRadius = cornerRadius,
                        borderStyle = borderStyle,
                        textSize = gridTextSize,
                        opacity = gridOpacity,
                        onColumnsChange = { mapGridColumns = it },
                        onRowsChange = { mapGridRows = it },
                        onOpenFullScreen = { onOpenRoomLayout("") },
                        onAddRoom = { container.addRoomCells(listOf(it)) },
                        onUpdateRoom = { container.updateRoomCell(it) },
                        onMoveRoom = { cell, row, col -> container.moveRoomCell(cell.id, row, col) },
                    )
                } else if (viewMode == TimetableMode.AGENDA) {
                    // 日程与课表网格共用同一份"已按周次 / 调休过滤"的列表，只是换一种读法
                    AgendaList(
                        courses = displayCourses,
                        timeSlots = timeSlots,
                        startOfWeek = startOfWeek,
                        state = agendaListState,
                        today = today,
                    )
                } else if (courses.isEmpty()) {
                    TimetableEmptyState(
                        onImport = onOpenImport,
                        onAddCourse = { viewModel.openAddEditor() }
                    )
                } else {
                    PeriodGrid(
                        timeSlots = timeSlots,
                        courses = displayCourses,
                        currentWeek = currentWeek,
                        isCurrentWeek = isCurrentWeek,
                        showTeacher = showTeacher,
                        showRoom = showRoom,
                        showCampus = showCampus,
                        showSlotTime = showSlotTime,
                        textCentered = textCentered,
                        gridHeightProvider = { container.gridHeight.value },
                        cornerRadius = cornerRadius,
                        gridOpacityProvider = { container.gridOpacity.value },
                        gridTextSize = gridTextSize,
                        showOddEven = showOddEven,
                        borderStyle = borderStyle,
                        nonCurrentCourses = displayNonCurrentCourses,
                        showNonCurrentWeek = showNonCurrentWeek,
                        vibrationMode = vibrationMode,
                        gridBgColor = gridBgColor,
                        otherWeekAlpha = otherWeekAlpha,
                        verticalLayout = verticalLayout,
                        homeworkStageByCourse = homeworkStageByCourse,
                        // 底栏是浮层，这里**不再**在外面给网格让高度 ——
                        // 那样会在网格与底栏之间空出一条死白，底栏悬在空白上既没有东西可模糊，
                        // 看起来也就是"tab 后面多了一块白板"。改成把留白交给网格内部的滚动内容。
                        bottomContentPadding = LocalBottomBarInset.current,
                        onCourseClick = { viewModel.openEditEditor(it) },
                        // 点格子里的教室 → 教室分布图（`room_layout?q=教室名`，按名查楼层与位置）。
                        // 与「全屏地图」里那条 `onOpenRoomLayout("")` 是同一个出口，只是带上教室名。
                        onRoomClick = onOpenRoomLayout,
                        onEmptyCellClick = { day, slot -> viewModel.openAddEditor(day, slot) },
                        onCourseMoved = { ids, dayDelta, slotDelta ->
                            if (!hasDayRemap) {
                                viewModel.moveCoursesByDelta(ids, dayDelta, slotDelta)
                            } else {
                                // 调休周：显示列位移 -> 课程原始星期位移；落到放假列的拖动直接忽略
                                courses.filter { it.id in ids }
                                    .groupBy { it.dayOfWeek }
                                    .forEach { (originalDay, group) ->
                                        val mappedDelta = HolidaySchedule.translateDayDelta(originalDay, dayDelta, weekSchedule)
                                        if (mappedDelta != null) {
                                            viewModel.moveCoursesByDelta(group.map { it.id }, mappedDelta, slotDelta)
                                        }
                                    }
                            }
                        },
                        scrollState = gridScrollState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

            }
            }
        }
        } // end Scaffold content lambda

    } // end outer Box

    if (showEditor && editingCourse != null) {
        CourseEditorDialog(
            course = editingCourse!!,
            currentWeek = currentWeek,
            availableSlots = timeSlots.map { it.slot },
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveCourse(it); viewModel.closeEditor() },
            onDelete = {
                pendingDeleteCourse = it
                viewModel.closeEditor()
                showDeleteRangeDialog = true
            },
            onLocate = onOpenCampusMap,
            onOpenRoomLayout = onOpenRoomLayout,
            onOpenCourseNavigation = onOpenCourseNavigation,
        )
    }

    if (showDeleteRangeDialog && pendingDeleteCourse != null) {
        DeleteRangeDialog(
            onDismiss = {
                showDeleteRangeDialog = false
                pendingDeleteCourse = null
            },
            onConfirm = { range ->
                pendingDeleteCourse?.let { viewModel.deleteCourseRange(it, range) }
                showDeleteRangeDialog = false
                pendingDeleteCourse = null
            }
        )
    }

    if (showTaskPreview) {
        TaskPreviewDialog(
            tasks = allTasks,
            repository = app.repository,
            schemeId = schemeId,
            onDismiss = { showTaskPreview = false },
            onViewAll = { showTaskPreview = false }
        )
    }


}

// ═══════════════════════════════════════════
//  Delete Range Dialog
// ═══════════════════════════════════════════

@Composable
fun DeleteRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: (DeleteRange) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val options = listOf(
        DeleteRange.ALL_BY_NAME to "删除全部该课程",
        DeleteRange.SAME_TIME_SLOT to "删除该时间段所有课",
        DeleteRange.THIS_INSTANCE to "仅删除本次课"
    )
    var selected by remember { mutableStateOf(DeleteRange.ALL_BY_NAME) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(skin.radiusCard),
        title = {
            Text(
                "请选择删除范围",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(skin.spaceMicro)) {
                options.forEach { (range, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(skin.radiusChip))
                            .clickable { selected = range }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == range,
                            onClick = { selected = range },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.accentMain)
                        )
                        Spacer(Modifier.width(skin.spaceSm))
                        Text(
                            label,
                            fontSize = 14.sp,
                            color = if (selected == range) colors.textPrimary else colors.textSecondary,
                            fontWeight = if (selected == range) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(skin.radiusChip)
            ) {
                Text("确认删除", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = colors.textSecondary)
            }
        }
    )
}

// ── 周次快速跳转选择器（点击顶部「第N周」弹出，对标时光课表）──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerSheet(
    currentWeek: Int,
    totalWeeks: Int,
    onSelect: (Int) -> Unit,
    onSemesterSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "跳转到周次",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Text(
            "当前第 $currentWeek 周 · 共 $totalWeeks 周",
            fontSize = 12.sp,
            color = colors.textTertiary,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 330.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSmall)
        ) {
            // key = 周次本身：学期总周数变化时（用户在设置里改）能保持已有项的滚动位置与状态
            items(count = totalWeeks, key = { index -> index + 1 }) { index ->
                val week = index + 1
                val isCurrent = week == currentWeek
                Box(
                    modifier = Modifier
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(skin.radiusMicro))
                        .background(if (isCurrent) colors.accentMain else colors.surfaceHighlight)
                        .clickable { onSelect(week) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$week",
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isCurrent) colors.surfaceCard else colors.textPrimary
                    )
                }
            }
        }
        HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
        TextButton(
            onClick = onSemesterSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.textSecondary
            )
            Spacer(Modifier.width(skin.spaceSmall))
            Text("学期设置（开学日期 / 周数）", fontSize = 13.sp, color = colors.textSecondary)
        }
        Spacer(Modifier.height(skin.spaceMd))
    }
}



/** 课表页的三种视图：周网格 / 日程列表 / 楼层平面图 */
internal enum class TimetableMode { WEEK, AGENDA, MAP }

/**
 * 视图切换（顶栏小图标）—— 单按钮三态循环：课表 → 日程 → 地图 → 课表。
 *
 * 2026-10 起第一跳是「日程」：日程是高频读法，地图是低频配置页；
 * 按钮图标显示的是**下一站**，避免"当前是什么"与"点了会去哪"混淆。
 */
@Composable
private fun ModeToggleButton(mode: TimetableMode, onChange: (TimetableMode) -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val next = when (mode) {
        TimetableMode.WEEK -> TimetableMode.AGENDA
        TimetableMode.AGENDA -> TimetableMode.MAP
        TimetableMode.MAP -> TimetableMode.WEEK
    }
    val (icon, description) = when (next) {
        TimetableMode.AGENDA -> Icons.AutoMirrored.Filled.Assignment to "切换到日程"
        TimetableMode.MAP -> Icons.Outlined.Map to "切换到地图"
        TimetableMode.WEEK -> Icons.Outlined.CalendarMonth to "切换回课表"
    }
    IconButton(
        onClick = { onChange(next) },
        modifier = Modifier.size(28.dp).invisibleTouchTarget()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.accentMain,
            modifier = Modifier.size(17.dp)
        )
    }
}
