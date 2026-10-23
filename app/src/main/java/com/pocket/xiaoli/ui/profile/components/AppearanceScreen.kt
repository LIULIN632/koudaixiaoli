package com.pocket.xiaoli.ui.profile.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Card density presets ──
private data class CardDensity(val label: String, val height: Int, val textSize: Int, val radius: Int)
private val densityPresets = listOf(
    CardDensity("紧凑", 52, 10, 2),
    CardDensity("标准", 64, 12, 4),
    CardDensity("舒适", 80, 14, 8)
)

// ── Preview week grid sample data ──
private data class PreviewSample(
    val course: CourseEntity,
    val hasHomework: Boolean = false,
    val isNonCurrent: Boolean = false
)

private val sampleCourses = listOf(
    PreviewSample(CourseEntity(name = "高等数学", teacher = "张教授", room = "明伦校区 综合教学楼 101", dayOfWeek = 1, startSlot = 1, endSlot = 2, colorIndex = 0)),
    PreviewSample(CourseEntity(name = "大学英语", teacher = "李老师", room = "外语楼 302", dayOfWeek = 2, startSlot = 1, endSlot = 1, colorIndex = 2), hasHomework = true),
    PreviewSample(CourseEntity(name = "体育(单周)", teacher = "王教练", room = "田径场", dayOfWeek = 3, startSlot = 1, endSlot = 1, colorIndex = 4, weekType = "odd")),
    PreviewSample(CourseEntity(name = "数据结构", teacher = "赵老师", room = "信息楼 405", dayOfWeek = 4, startSlot = 2, endSlot = 2, colorIndex = 6)),
    PreviewSample(CourseEntity(name = "编译原理", teacher = "孙老师", room = "信息楼 201", dayOfWeek = 1, startSlot = 3, endSlot = 3, colorIndex = 3), isNonCurrent = true)
)

private val previewSlotLabels = listOf("08:00", "08:55", "10:00", "10:55")
private val weekDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    container: AppContainer,
    onBack: () -> Unit,
    vm: AppearanceViewModel = viewModel(factory = AppearanceViewModel.Factory(container))
) {
    val skin = LocalSkin.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    val courseColorMode by container.courseColorMode.collectAsStateWithLifecycle()
    val customAccentColor by container.customAccentColor.collectAsStateWithLifecycle()
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current

    var previewTeacher by remember { mutableStateOf(state.showTeacher) }
    var previewRoom by remember { mutableStateOf(state.showRoom) }
    var previewCampus by remember { mutableStateOf(state.showCampus) }
    var previewSlotTime by remember { mutableStateOf(state.showSlotTime) }
    var previewBorderStyle by remember { mutableIntStateOf(state.borderStyle) }
    var previewCentered by remember { mutableStateOf(state.textCentered) }
    var previewHeight by remember { mutableIntStateOf(state.gridHeight) }
    var previewRadius by remember { mutableIntStateOf(state.cornerRadius) }
    var previewOpacity by remember { mutableStateOf(state.gridOpacity) }
    var previewTextSize by remember { mutableIntStateOf(state.gridTextSize) }
    var previewNonCurrentWeek by remember { mutableStateOf(state.showNonCurrentWeek) }
    var previewOddEven by remember { mutableStateOf(state.showOddEven) }
    var previewBgColor by remember { mutableIntStateOf(state.gridBgColor) }
    var previewOtherWeekAlpha by remember { mutableStateOf(state.otherWeekAlpha) }
    var previewWallpaper by remember { mutableStateOf(state.wallpaperUri) }

    // New local-only preview settings
    var previewCardDensity by remember { mutableIntStateOf(1) }
    var previewVerticalLayout by remember { mutableStateOf(state.verticalLayout) }

    // Skip auto-sync once the user starts editing, so in-flight writes can't revert local changes
    var userTouched by remember { mutableStateOf(false) }

    // Sync from saved state
    LaunchedEffect(state) {
        if (userTouched) return@LaunchedEffect
        previewTeacher = state.showTeacher
        previewRoom = state.showRoom
        previewCampus = state.showCampus
        previewSlotTime = state.showSlotTime
        previewBorderStyle = state.borderStyle
        previewCentered = state.textCentered
        previewHeight = state.gridHeight
        previewRadius = state.cornerRadius
        previewOpacity = state.gridOpacity
        previewTextSize = state.gridTextSize
        previewNonCurrentWeek = state.showNonCurrentWeek
        previewOddEven = state.showOddEven
        previewBgColor = state.gridBgColor
        previewOtherWeekAlpha = state.otherWeekAlpha
        previewWallpaper = state.wallpaperUri
        previewVerticalLayout = state.verticalLayout
        // Guess density from saved values; -1 = custom, matched no preset
        previewCardDensity = densityPresets.indexOfFirst {
            it.height == state.gridHeight && it.textSize == state.gridTextSize && it.radius == state.cornerRadius
        }
    }

    // ── Preview week grid constants ──
    val previewRows = 4
    val rowH = previewHeight.coerceAtLeast(48).dp
    val sidebarW = if (previewSlotTime) 30.dp else 20.dp
    val todayDay = java.time.LocalDate.now().dayOfWeek.value

    fun markTouched() { userTouched = true }

    fun commitAll() {
        if (previewTeacher != state.showTeacher) vm.onIntent(AppearanceIntent.SetTeacher(previewTeacher))
        if (previewRoom != state.showRoom) vm.onIntent(AppearanceIntent.SetRoom(previewRoom))
        if (previewCampus != state.showCampus) vm.onIntent(AppearanceIntent.SetCampus(previewCampus))
        if (previewSlotTime != state.showSlotTime) vm.onIntent(AppearanceIntent.SetSlotTime(previewSlotTime))
        if (previewBorderStyle != state.borderStyle) vm.onIntent(AppearanceIntent.SetBorderStyle(previewBorderStyle))
        if (previewCentered != state.textCentered) vm.onIntent(AppearanceIntent.SetTextCentered(previewCentered))
        if (previewNonCurrentWeek != state.showNonCurrentWeek) vm.onIntent(AppearanceIntent.SetNonCurrentWeek(previewNonCurrentWeek))
        if (previewOddEven != state.showOddEven) vm.onIntent(AppearanceIntent.SetOddEven(previewOddEven))
        if (previewHeight != state.gridHeight) vm.onIntent(AppearanceIntent.SetGridHeight(previewHeight))
        if (previewRadius != state.cornerRadius) vm.onIntent(AppearanceIntent.SetCornerRadius(previewRadius))
        if (previewOpacity != state.gridOpacity) vm.onIntent(AppearanceIntent.SetOpacity(previewOpacity))
        if (previewTextSize != state.gridTextSize) vm.onIntent(AppearanceIntent.SetTextSize(previewTextSize))
        if (previewBgColor != state.gridBgColor) vm.onIntent(AppearanceIntent.SetBgColor(previewBgColor))
        if (previewOtherWeekAlpha != state.otherWeekAlpha) vm.onIntent(AppearanceIntent.SetOtherWeekAlpha(previewOtherWeekAlpha))
        if (previewWallpaper != state.wallpaperUri) vm.onIntent(AppearanceIntent.SetWallpaper(previewWallpaper))
        if (previewVerticalLayout != state.verticalLayout) vm.onIntent(AppearanceIntent.SetVerticalLayout(previewVerticalLayout))
    }

    // 系统返回键也要提交预览修改，否则用户拖动的滑块/开关会静默丢失
    // （本组件的 BackHandler 在 ProfileScreen 之后注册，Compose 优先级更高，会先触发）
    BackHandler(enabled = true) { commitAll(); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化配置", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { commitAll(); onBack() }) {
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
            // ═══════════════════════════════════════
            //  Fixed sticky preview
            // ═══════════════════════════════════════
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(skin.spaceMd),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(skin.spaceMd)) {
                    Text("实时预览", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary)
                    Spacer(Modifier.height(skin.spaceSm))
                    val gridBg = if (previewBgColor >= 0 && previewBgColor < GridBgPalette.size) {
                        val (lightBg, darkBg) = GridBgPalette[previewBgColor]
                        if (isDark) darkBg else lightBg
                    } else Color.Unspecified
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (gridBg != Color.Unspecified) Modifier.background(gridBg, RoundedCornerShape(skin.radiusMicro)) else Modifier)
                            .padding(skin.spaceXs)
                    ) {
                        // Weekday header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(Modifier.width(sidebarW))
                            repeat(7) { dayIdx ->
                                val isToday = dayIdx + 1 == todayDay
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        weekDayLabels[dayIdx],
                                        fontSize = 10.sp,
                                        fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                        color = if (isToday) colors.accentMain else colors.textTertiary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(skin.spaceXs))

                        // Week grid with absolutely-positioned course cards
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewRows * rowH)
                                .clip(RoundedCornerShape(skin.radiusMicro))
                        ) {
                            val cellW = (maxWidth - sidebarW) / 7f
                            // Row separators
                            for (r in 0..previewRows) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = rowH * r.toFloat() - 0.5.dp)
                                        .height(0.5.dp)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
                                )
                            }
                            // Slot sidebar
                            Column(modifier = Modifier.width(sidebarW)) {
                                repeat(previewRows) { i ->
                                    Box(
                                        modifier = Modifier.height(rowH),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${i + 1}", fontSize = 9.sp, color = colors.textTertiary)
                                            if (previewSlotTime) {
                                                Text(previewSlotLabels[i], fontSize = 8.sp, color = colors.textTertiary.copy(alpha = 0.7f), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                            // Course cards (ghost layer first, then normal)
                            sampleCourses.sortedBy { it.isNonCurrent }.forEach { s ->
                                val span = (s.course.endSlot - s.course.startSlot + 1).coerceAtLeast(1)
                                PreviewGridCourseCard(
                                    course = s.course,
                                    hasHomework = s.hasHomework,
                                    isNonCurrent = s.isNonCurrent,
                                    x = sidebarW + (s.course.dayOfWeek - 1) * cellW,
                                    y = (s.course.startSlot - 1) * rowH,
                                    width = cellW,
                                    height = span * rowH,
                                    opacity = previewOpacity,
                                    radius = previewRadius,
                                    borderStyle = previewBorderStyle,
                                    centered = previewCentered,
                                    textSize = previewTextSize,
                                    showTeacher = previewTeacher,
                                    showRoom = previewRoom,
                                    showCampus = previewCampus,
                                    verticalLayout = previewVerticalLayout,
                                    showOddEven = previewOddEven,
                                    otherWeekAlpha = previewOtherWeekAlpha
                                )
                            }
                        }
                    }
                }
            }

            // ═══════════════════════════════════════
            //  Scrollable settings below preview
            // ═══════════════════════════════════════
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = skin.spaceLg)
            ) {
                // ── Custom accent color ──
                Text("自定义主题色", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = skin.spaceLg, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = customAccentColor == CustomAccent.DEFAULT,
                            onClick = { container.clearCustomAccentColor() },
                            label = { Text("默认", fontSize = 12.sp) },
                        )
                        CustomAccent.presets.forEach { argb ->
                            val selected = customAccentColor == argb
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) colors.accentMain else colors.borderDivider,
                                        shape = CircleShape,
                                    )
                                    .clickable { container.setCustomAccentColor(argb) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))


                // ── Course color mode ──
                Text("课程着色", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                    ) {
                        CourseColorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = courseColorMode == mode,
                                onClick = { container.setCourseColorMode(mode) },
                                label = { Text(mode.label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentMain,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))


                // ── Card density preset ──
                Text("课程卡片样式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                    ) {
                        densityPresets.forEachIndexed { idx, preset ->
                            FilterChip(
                                selected = previewCardDensity == idx,
                                onClick = {
                                    markTouched()
                                    previewCardDensity = idx
                                    previewHeight = preset.height
                                    previewTextSize = preset.textSize
                                    previewRadius = preset.radius
                                },
                                label = { Text(preset.label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentMain,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))

                // ── Content layout toggle ──
                Text("课程信息布局", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                    ) {
                        listOf("竖向标签" to true, "横向紧凑" to false).forEach { (label, mode) ->
                            FilterChip(
                                selected = previewVerticalLayout == mode,
                                onClick = { markTouched(); previewVerticalLayout = mode },
                                label = { Text(label, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentMain,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceLg))

                // ── Display settings ──
                Text("显示设置", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Column {
                        AppSettingSwitchRow("显示教师", "在课程卡片上显示教师姓名", previewTeacher) { markTouched(); previewTeacher = it }
                        SettingDiv()
                        AppSettingSwitchRow("显示教室", "在课程卡片上显示教学楼和教室", previewRoom) { markTouched(); previewRoom = it }
                        SettingDiv()
                        AppSettingSwitchRow("显示校区", "导入课程时附带的校区信息", previewCampus) { markTouched(); previewCampus = it }
                        SettingDiv()
                        AppSettingSwitchRow("显示节次时间", "左侧节次栏显示具体时间", previewSlotTime) { markTouched(); previewSlotTime = it }
                        SettingDiv()
                        AppSettingSwitchRow("非本周课程", "非本周课程半透明显示", previewNonCurrentWeek) { markTouched(); previewNonCurrentWeek = it }
                        SettingDiv()
                        AppSettingSwitchRow("文字居中", "课程文字居中对齐", previewCentered) { markTouched(); previewCentered = it }
                        SettingDiv()
                        AppSettingSwitchRow("单双周标识", "在课程卡片上显示单双周", previewOddEven) { markTouched(); previewOddEven = it }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))

                // ── Border style ──
                Text("边框样式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                        listOf("无" to 0, "实线" to 1, "虚线" to 2).forEach { (label, style) ->
                            FilterChip(selected = previewBorderStyle == style, onClick = { markTouched(); previewBorderStyle = style }, label = { Text(label, fontSize = 12.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accentMain, selectedLabelColor = Color.White))
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))

                // ── Grid background color ──
                Text("课程表背景", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Column(modifier = Modifier.padding(skin.spaceMd)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = skin.spaceSm)) {
                            FilterChip(
                                selected = previewBgColor == -1,
                                onClick = { markTouched(); previewBgColor = -1 },
                                label = { Text("默认", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.accentMain, selectedLabelColor = Color.White)
                            )
                        }
                        GridBgPalette.chunked(5).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceTiny),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { pair ->
                                    val idx = GridBgPalette.indexOf(pair)
                                    val (lightColor, darkColor) = pair
                                    val displayColor = if (isDark) darkColor else lightColor
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .then(
                                                if (previewBgColor == idx)
                                                    Modifier.drawBehind {
                                                        drawCircle(color = colors.accentMain, radius = size.minDimension / 2, style = Stroke(width = 3.dp.toPx()))
                                                    }
                                                else Modifier
                                            )
                                            .clickable { markTouched(); previewBgColor = idx },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (previewBgColor == idx) {
                                            Text("✓", color = if (isDark) Color.White else Color(0xFF424242), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceMd))

                // ── Wallpaper ──
                Text("页面壁纸", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    val context = LocalContext.current
                    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                        if (uri != null) {
                            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            previewWallpaper = uri.toString()
                        }
                    }
                    var wallpaperBitmap by remember(previewWallpaper) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(previewWallpaper) {
                        if (previewWallpaper.isNotEmpty()) {
                            withContext(Dispatchers.IO) {
                                val bitmap = com.pocket.xiaoli.util.decodeScaledWallpaper(context, android.net.Uri.parse(previewWallpaper))
                                if (bitmap != null) wallpaperBitmap = bitmap.asImageBitmap()
                            }
                        } else {
                            wallpaperBitmap = null
                        }
                    }

                    Column(modifier = Modifier.padding(skin.spaceMd), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (previewWallpaper.isNotEmpty() && wallpaperBitmap != null) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(skin.radiusMicro))) {
                                Image(bitmap = wallpaperBitmap!!, contentDescription = "壁纸预览", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                            Spacer(Modifier.height(skin.spaceSm))
                            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                                OutlinedButton(onClick = { markTouched(); imagePicker.launch("image/*") }, shape = RoundedCornerShape(skin.radiusMicro)) { Text("更换", fontSize = 12.sp) }
                                OutlinedButton(onClick = { markTouched(); previewWallpaper = ""; wallpaperBitmap = null }, shape = RoundedCornerShape(skin.radiusMicro)) { Text("移除", fontSize = 12.sp, color = colors.danger) }
                            }
                        } else {
                            Icon(Icons.Outlined.Image, null, tint = colors.textTertiary.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(skin.spaceSmall))
                            Text("从相册选择图片作为课表壁纸", fontSize = 11.sp, color = colors.textTertiary)
                            Spacer(Modifier.height(skin.spaceSm))
                            OutlinedButton(onClick = { markTouched(); imagePicker.launch("image/*") }, shape = RoundedCornerShape(skin.radiusMicro)) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(skin.spaceXs))
                                Text("选择图片", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(skin.spaceLg))

                // ── Advanced sliders ──
                Text("参数微调", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.textSecondary, modifier = Modifier.padding(start = skin.spaceXs, bottom = 4.dp))
                Surface(shape = RoundedCornerShape(skin.radiusChip), color = colors.surfaceCard) {
                    Column(modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = 12.dp)) {
                        AppSettingSliderRow("格子高度", "${previewHeight}dp", previewHeight.toFloat(), 48f..96f) {
                            markTouched()
                            previewHeight = it.toInt()
                            previewCardDensity = -1 // custom
                        }
                        AppSettingSliderRow("卡片圆角", "${previewRadius}dp", previewRadius.toFloat(), 0f..16f) {
                            markTouched()
                            previewRadius = it.toInt()
                            previewCardDensity = -1
                        }
                        AppSettingSliderRow("不透明度", "${(previewOpacity * 100).toInt()}%", previewOpacity, 0.5f..1.0f) { markTouched(); previewOpacity = it }
                        AppSettingSliderRow("文字大小", "${previewTextSize}sp", previewTextSize.toFloat(), 10f..16f) {
                            markTouched()
                            previewTextSize = it.toInt()
                            previewCardDensity = -1
                        }
                        AppSettingSliderRow("非本周透明度", "${(previewOtherWeekAlpha * 100).toInt()}%", previewOtherWeekAlpha, 0.05f..0.5f) { markTouched(); previewOtherWeekAlpha = it }
                    }
                }

                Spacer(Modifier.height(skin.spaceLg))

                OutlinedButton(
                    onClick = {
                        markTouched()
                        vm.onIntent(AppearanceIntent.ResetDefaults)
                        previewTeacher = true; previewRoom = true; previewCampus = false; previewSlotTime = false
                        previewBorderStyle = 0; previewCentered = false; previewHeight = 64; previewRadius = 4
                        previewOpacity = 1f; previewTextSize = 12; previewNonCurrentWeek = true
                        previewOddEven = true; previewBgColor = -1; previewOtherWeekAlpha = 0.50f; previewWallpaper = ""
                        previewCardDensity = 1; previewVerticalLayout = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = SolidColor(colors.danger.copy(alpha = 0.5f)))
                ) { Text("恢复默认设置", fontSize = 14.sp) }

                Spacer(Modifier.height(skin.spaceHuge))
            }
        }
    }
}

// ═══ Preview week-grid course card ═══
@Composable
private fun PreviewGridCourseCard(
    course: CourseEntity,
    hasHomework: Boolean,
    isNonCurrent: Boolean,
    x: Dp,
    y: Dp,
    width: Dp,
    height: Dp,
    opacity: Float,
    radius: Int,
    borderStyle: Int,
    centered: Boolean,
    textSize: Int,
    showTeacher: Boolean,
    showRoom: Boolean,
    showCampus: Boolean,
    verticalLayout: Boolean,
    showOddEven: Boolean,
    otherWeekAlpha: Float
) {
    val isDark = LocalDarkMode.current
    val colorIndex = course.colorIndexFor(LocalCourseColorMode.current)
    val textColor = courseTextColor(colorIndex, isDark)
    val bgColor = courseBgColor(colorIndex, isDark)
    val cardAlpha = if (isNonCurrent) otherWeekAlpha else opacity

    Box(
        modifier = Modifier
            .offset(x = x + 1.dp, y = y + 1.dp)
            .width(width - 2.dp)
            .height(height - 2.dp)
            .graphicsLayer { alpha = cardAlpha }
            .clip(RoundedCornerShape(radius.dp))
            .background(bgColor)
            .then(
                when {
                    isNonCurrent -> Modifier.drawBehind {
                        drawRoundRect(
                            color = courseBorderColor(colorIndex, isDark),
                            cornerRadius = CornerRadius(radius.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f))
                        )
                    }
                    borderStyle == 1 -> Modifier.drawBehind {
                        drawRoundRect(
                            color = courseBorderColor(colorIndex, isDark),
                            cornerRadius = CornerRadius(radius.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    borderStyle == 2 -> Modifier.drawBehind {
                        drawRoundRect(
                            color = courseBorderColor(colorIndex, isDark),
                            cornerRadius = CornerRadius(radius.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f))
                        )
                    }
                    else -> Modifier
                }
            ),
        contentAlignment = if (centered) Alignment.Center else Alignment.TopStart
    ) {
        PreviewGridCardContent(
            course = course,
            textColor = textColor,
            isNonCurrent = isNonCurrent,
            showTeacher = showTeacher,
            showRoom = showRoom,
            showCampus = showCampus,
            showOddEven = showOddEven,
            centered = centered,
            textSize = textSize,
            hasHomework = hasHomework,
            verticalLayout = verticalLayout,
            otherWeekAlpha = otherWeekAlpha
        )
    }
}

@Composable
private fun PreviewGridCardContent(
    course: CourseEntity,
    textColor: Color,
    isNonCurrent: Boolean,
    showTeacher: Boolean,
    showRoom: Boolean,
    showCampus: Boolean,
    showOddEven: Boolean,
    centered: Boolean,
    textSize: Int,
    hasHomework: Boolean,
    verticalLayout: Boolean,
    otherWeekAlpha: Float
) {
    val skin = LocalSkin.current
    val nameAlpha = if (isNonCurrent) (otherWeekAlpha * 2.78f).coerceIn(0.3f, 1f) else 1f
    val infoAlpha = if (isNonCurrent) (otherWeekAlpha * 1.67f).coerceIn(0.2f, 0.8f) else 0.6f

    val displayRoom = remember(course.room, showCampus) {
        val room = course.room
        if (showCampus) room
        else {
            val campusIdx = room.indexOf("校区")
            if (campusIdx > 0) room.substring(campusIdx + 2).trimStart(' ', '·', '-') else room
        }
    }
    val weekLabel = when (course.weekType) {
        "odd" -> "单周"
        "even" -> "双周"
        else -> ""
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = skin.spaceTiny, vertical = 2.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = course.name,
                color = textColor.copy(alpha = nameAlpha),
                fontSize = textSize.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (textSize + 2).sp,
                maxLines = if (verticalLayout) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            if (hasHomework) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "有作业",
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        if (isNonCurrent) {
            Text(
                text = "非本周",
                color = textColor.copy(alpha = infoAlpha),
                fontSize = (textSize - 3).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (textSize - 1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showTeacher && course.teacher.isNotEmpty()) {
            Text(
                text = course.teacher,
                color = textColor.copy(alpha = infoAlpha),
                fontSize = (textSize - 3).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (textSize - 1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showRoom && displayRoom.isNotEmpty()) {
            Text(
                text = displayRoom,
                color = textColor.copy(alpha = infoAlpha),
                fontSize = (textSize - 3).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (textSize - 1).sp,
                maxLines = if (verticalLayout) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showOddEven && weekLabel.isNotEmpty()) {
            Text(
                text = weekLabel,
                color = textColor.copy(alpha = infoAlpha),
                fontSize = (textSize - 3).sp,
                fontWeight = FontWeight.Normal,
                lineHeight = (textSize - 1).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppSettingSwitchRow(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = skin.spaceLg, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = colors.textTertiary)
        }
        Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accentMain, uncheckedThumbColor = colors.surfaceCard, uncheckedTrackColor = colors.borderDivider))
    }
}

@Composable
private fun AppSettingSliderRow(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceSmall), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.textPrimary, modifier = Modifier.width(72.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = 0, colors = SliderDefaults.colors(thumbColor = colors.accentMain, activeTrackColor = colors.accentMain, inactiveTrackColor = colors.borderDivider), modifier = Modifier.weight(1f))
        Text(valueLabel, fontSize = 12.sp, color = colors.textTertiary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun SettingDiv() {
    val colors = LocalPocketXiaoliColors.current
    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 56.dp))
}
