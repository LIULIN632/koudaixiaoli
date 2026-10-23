package com.pocket.xiaoli.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.focus.FocusDayStat
import com.pocket.xiaoli.focus.FocusEngine
import com.pocket.xiaoli.focus.FocusPhase
import com.pocket.xiaoli.focus.clockText
import com.pocket.xiaoli.focus.humanDuration
import com.pocket.xiaoli.focus.label
import com.pocket.xiaoli.focus.pomodoroCountText
import com.pocket.xiaoli.focus.runningLabel
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.accentColor
import com.pocket.xiaoli.ui.theme.onAccentColor

/**
 * 专注页（番茄钟）。
 *
 * ## 页面是镜子，不是账本
 *
 * 所有状态从 [FocusViewModel] 来，而 ViewModel 又从 `FocusController` 的快照来。
 * 这个页面**不做任何计时**：它只是每 250ms 读一次单调钟，然后把
 * 「截止点 − 现在」画出来。所以从通知点回来、从后台切回来、甚至换一台设备
 * （同一份 prefs 恢复），看到的剩余时间永远是对的。
 *
 * ## 底栏为什么消失
 *
 * `focus` 是**非 tab 路由**（`AppNavigation` 里 `isTabRoute` 为假），
 * 与 `campus_calendar`、`exam_schedule` 同策略：专注页是一个"进入任务"的页面，
 * 留着底栏会引诱用户中途切走。
 *
 * ## 子组件为什么都是 internal
 *
 * 整页渲不出来（要 `AppContainer`），golden 基准图只能直接渲子组件，
 * 而 `private` 组件在测试包里看不见。这是首页踩过的坑
 *（见 `memory/02-feature-map.md` 的「整页渲不出来」那条）——
 * 别为了"看着更封装"把它们改回 private。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val context = LocalContext.current
    val viewModel: FocusViewModel = viewModel(factory = FocusViewModel.Factory(container))

    val runtime by viewModel.runtime.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val remainingMs by viewModel.remainingMs.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // 进页面/从后台回来时兜一次底（见 FocusViewModel.onResume 的注释）。
    LaunchedEffect(Unit) { viewModel.onResume() }

    // 通知权限：番茄钟"锁屏还能到点提醒"全靠通知，没它就是残的。
    // **只在用户真的点「开始」那一刻问**，进页面就问会让只想看看的人也被弹一次
    // （与「我的」页提醒开关同一套范式）。用户拒绝也不拦着他开始计时。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    val startTimer: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.start()
    }

    val active = runtime != null
    val paused = runtime?.isPaused == true
    val phase = runtime?.phase ?: FocusPhase.FOCUS
    val totalRounds = config.roundsBeforeLongBreak
    val roundInCycle = runtime?.let { FocusEngine.roundInCycle(it, config) } ?: 1
    // 圈点填几个：专注段里当前这个还没跑完（减一），休息段里当前这个已经跑完了。
    val completedInCycle = if (active) {
        if (phase.isBreak) roundInCycle else roundInCycle - 1
    } else {
        0
    }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("专注计时", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(skin.spaceXl))

            FocusPhaseHeader(
                phase = phase,
                completedInCycle = completedInCycle,
                totalRounds = totalRounds,
                idle = !active,
            )

            Spacer(Modifier.height(skin.spaceXl))

            FocusRing(
                remainingMs = if (active) remainingMs else FocusEngine.plannedMsFor(FocusPhase.FOCUS, config),
                // 环画的是**剩余**：起点满圈，一路缩到 0（番茄钟的通用观感）。
                remainingFraction = if (active) 1f - progress else 1f,
                statusText = when {
                    !active -> "准备开始"
                    paused -> "已暂停"
                    else -> phase.runningLabel
                },
            )

            Spacer(Modifier.height(skin.spaceXl))

            FocusActions(
                active = active,
                paused = paused,
                onStart = startTimer,
                onTogglePause = viewModel::togglePause,
                onSkip = viewModel::skip,
                onAbandon = viewModel::abandon,
            )

            Spacer(Modifier.height(skin.spaceXl))

            FocusTodayCard(stat = today)

            if (runtime?.linkedTaskTitle != null) {
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    "关联待办：${runtime?.linkedTaskTitle}",
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(skin.spaceLg))

            // 统计区：本周柱状图 + 当日分段图。没有记录时它自己只留一句话（不画空框）。
            FocusStatsSection(stats = stats)

            Spacer(Modifier.height(skin.spaceLg))

            Text(
                "锁屏后仍会计时；若发现不计时，请在系统设置里允许本应用后台运行、" +
                    "并允许「闹钟和提醒」。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )

            Spacer(Modifier.height(skin.spaceXxl))
        }
    }
}

/** 阶段头：阶段名 + 本组进度圈点 + 一句"第几个"。 */
@Composable
internal fun FocusPhaseHeader(
    phase: FocusPhase,
    completedInCycle: Int,
    totalRounds: Int,
    idle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = phase.label,
            style = PocketTypography.sectionTitle,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(skin.spaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall)) {
            repeat(totalRounds) { index ->
                val done = index < completedInCycle
                Box(
                    Modifier
                        .size(if (done) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) accentColor() else accentColor().copy(alpha = 0.22f)
                        )
                )
            }
        }
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            text = if (idle) {
                "共 $totalRounds 个专注段为一组"
            } else if (phase.isBreak) {
                "已完成 $completedInCycle / $totalRounds 个专注"
            } else {
                "第 ${completedInCycle + 1} / $totalRounds 个专注"
            },
            style = PocketTypography.caption,
            color = colors.textSecondary,
        )
    }
}

private val RingSize = 208.dp
private val RingStroke = 14.dp
private val TimerFontSize = 46.sp

/**
 * 大圆环 + 环内倒计时。
 *
 * 自绘（`Canvas`），不引任何图表库 —— 与 `ui/theme/MascotAvatar.kt` 的自绘惯例一致，
 * 本项目不收第三方 UI 依赖。
 *
 * @param remainingFraction 环上还剩几分之几（1f = 满圈）。传剩余而不是进度：
 *   满圈起、缩到空，是番茄钟的通用观感。
 */
@Composable
internal fun FocusRing(
    remainingMs: Long,
    remainingFraction: Float,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val accent = accentColor()
    Box(modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // 圈内淡彩：让 208dp 的大白圈里有"内容正在发生"的感觉，而不是一个空心框。
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.42f,
                ),
                radius = size.minDimension * 0.42f,
            )
            // 底圈：整圈，用主色的淡影 —— 分割线灰在浅色底上几乎看不见，轨道就"消失"了
            drawArc(
                color = accent.copy(alpha = 0.14f),
                startAngle = StartAngle,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * remainingFraction.coerceIn(0f, 1f)
            if (sweep > 0.5f) {
                // 主弧外面先垫一层更宽的同色淡弧，当作发光，避免单色粗描边看起来发虚。
                drawArc(
                    color = accent.copy(alpha = 0.12f),
                    startAngle = StartAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = StartAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clockText(remainingMs),
                style = PocketTypography.numeric.copy(fontSize = TimerFontSize, lineHeight = TimerFontSize * 1.14f),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(text = statusText, style = PocketTypography.caption, color = colors.textSecondary)
        }
    }
}

/** 主操作（开始 / 暂停 / 继续）与次操作（跳过 / 放弃）。 */
@Composable
internal fun FocusActions(
    active: Boolean,
    paused: Boolean,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { if (active) onTogglePause() else onStart() },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(skin.radiusCard),
            colors = ButtonDefaults.buttonColors(
                containerColor = accentColor(),
                contentColor = onAccentColor(),
            ),
        ) {
            Text(
                text = if (!active) "开始专注" else if (paused) "继续" else "暂停",
                style = PocketTypography.cardTitle,
            )
        }
        if (active) {
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("跳过本段", style = PocketTypography.body, color = colors.textSecondary)
                }
                TextButton(
                    onClick = onAbandon,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    // 非强调色 + 文案写明：这是"我主动放弃"，不是失败。
                    // 刻意**不做二次确认** —— 放弃是预期中的正常操作，
                    // 历史段也会如实记 completed = false（判据见设计稿 §4）。
                    Text("放弃本段", style = PocketTypography.body, color = colors.danger)
                }
            }
        }
    }
}

/** 今日积累。数据来自已落库的段，不是内存里的乐观累加。 */
@Composable
internal fun FocusTodayCard(
    stat: FocusDayStat,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderDivider),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("今天", style = PocketTypography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    text = pomodoroCountText(stat.completedPomodoros),
                    style = PocketTypography.cardTitle,
                    color = colors.accentMain,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("专注时长", style = PocketTypography.caption, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    // 0 的时候说"0 分钟"而不是 humanDuration(0) 的"0 秒"：
                    // 空态里"0 秒"看着像是刚跑过一段。
                    text = if (stat.focusMs <= 0L) "0 分钟" else humanDuration(stat.focusMs),
                    style = PocketTypography.cardTitle,
                    color = colors.accentMain,
                )
            }
        }
    }
}

/** 从 12 点方向开始画（屏幕坐标里 -90° 是正上方）。 */
private const val StartAngle = -90f
