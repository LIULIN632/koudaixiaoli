package com.pocket.xiaoli.ui.treasurebox

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.entity.SavedGradeEntity
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginContent
import com.pocket.xiaoli.ui.zhengfang.ZhengfangSchoolList
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.infocard.ScoreBand
import com.pocket.xiaoli.ui.components.infocard.color
import com.pocket.xiaoli.ui.components.infocard.scoreBandOf
import java.text.SimpleDateFormat
import java.util.Date
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.ui.components.infocard.GradeDistribution
import com.pocket.xiaoli.ui.components.infocard.GradeDistributionChart

/**
 * 「我的课程」独立页：本地已保存的课程成绩存档，离线可看；可从教务一键同步全部学期。
 * 与「成绩查询」分开后，这里只负责本地存档，不再混在线查询。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedGradesScreen(
    onBack: () -> Unit,
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
) {
    val colors = LocalPocketXiaoliColors.current
    val viewModel: SavedGradesViewModel = viewModel(
        factory = SavedGradesViewModel.Factory(client, schoolRegistry, appContainer)
    )
    val grades by viewModel.savedGrades.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(
                title = "我的课程",
                onBack = onBack,
                actions = {
                    if (grades.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) {
                            Text("清空", fontSize = 13.sp, color = colors.textSecondary)
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SavedGradesContent(viewModel = viewModel)
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空本地课程存档？") },
            text = { Text("将删除本机保存的 ${grades.size} 门课程成绩，教务系统里的成绩不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                }) { Text("清空", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}
/**
 * 「我的课程」内容：按学期分组展示已保存的课程成绩；
 * 可从教务一键拉取全部学期成绩保存，离线可查看。同步流程的选校/登录/验证码共用 ZhengfangLoginUi 组件。
 */
@Composable
fun SavedGradesContent(
    viewModel: SavedGradesViewModel,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val syncResult by viewModel.syncResult.collectAsStateWithLifecycle()

    // 点「从教务同步」后：有绑定账号则自动登录（验证码仍走弹窗）
    LaunchedEffect(syncing) { if (syncing) viewModel.autoLoginIfBound() }

    // 同步完成提示
    LaunchedEffect(syncResult) {
        if (syncResult != null) {
            Toast.makeText(context, syncResult, Toast.LENGTH_LONG).show()
            viewModel.consumeSyncResult()
        }
    }

    // ⚠️ 这里原先有一条「身份条」（图标 + 「本地存档」+ 一行说明），2026-10 已删 ——
    // 与服务页卡片、TopAppBar 说的是同一件事（三个名字）。理由见 `memory/03-pitfalls.md`。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
    ) {
        when {
            // 同步流程：先选学校
            syncing && selectedSchool == null -> ZhengfangSchoolList(
                state = viewModel,
                header = {
                    val c = LocalPocketXiaoliColors.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = skin.spaceSm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.backToSchoolList() }) {
                            Text("返回列表", fontSize = 13.sp)
                        }
                        Text(
                            "选择学校以同步成绩",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            )
            // 同步流程：登录 + 拉取进度
            syncing -> ZhengfangLoginContent(
                state = viewModel,
                hint = "登录正方教务系统，自动拉取全部学期成绩并保存（不会导入课表）",
                buttonText = "登录并同步全部成绩",
                topBackLabel = "返回列表",
                onTopBack = { viewModel.backToSchoolList() }
            )
            // 默认：本地成绩列表
            else -> SavedListContent(viewModel)
        }
    }

    ZhengfangCaptchaHost(state = viewModel)
}

// ── 本地成绩列表 ──

@Composable
private fun SavedListContent(
    viewModel: SavedGradesViewModel,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val grades by viewModel.savedGrades.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<SavedGradeEntity?>(null) }

    if (grades.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = skin.spaceLg),
        ) {
            Spacer(Modifier.height(skin.spaceLg))
            ServiceEmptyCard(
                icon = Icons.Outlined.School,
                title = "还没有保存的课程成绩",
                message = "登录教务系统后，自动拉取所有学期成绩保存在本机，之后离线也能查看。",
                accent = ServiceAccent.ACADEMIC,
                actionLabel = "从教务同步成绩",
                onAction = { viewModel.startSync() },
            )
        }
    } else {
        // 按学期分组展示（学期倒序，新在前）
        val grouped = grades.groupBy { it.termLabel.ifBlank { "未分学期" } }
        val terms = grouped.keys.sortedDescending()
        val distribution = remember(grades) { GradeDistribution.of(grades) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = skin.spaceLg,
                end = skin.spaceLg,
                top = skin.spaceSm,
                bottom = skin.spaceXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            item(key = "hero") {
                SavedStatsHero(grades)
            }
            item(key = "sync") {
                OutlinedButton(
                    onClick = { viewModel.startSync() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(skin.radiusCard),
                ) {
                    Icon(Icons.Outlined.Sync, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(skin.spaceSmall))
                    Text("从教务同步全部学期", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!distribution.isEmpty) {
                item(key = "distribution") {
                    DistributionCard(distribution)
                }
            }
            item(key = "section") {
                Spacer(Modifier.height(skin.spaceXs))
                ServiceSectionLabel(
                    title = "按学期查看",
                    accent = ServiceAccent.ACADEMIC,
                    trailing = {
                        Text(
                            "${grades.size} 门 · ${terms.size} 个学期",
                            fontSize = 12.sp,
                            color = colors.textTertiary,
                        )
                    },
                )
            }
            terms.forEach { term ->
                val list = grouped.getValue(term)
                item(key = "term_$term") {
                    TermHeader(term, list)
                }
                items(list, key = { it.id }) { grade ->
                    SavedGradeCard(grade, onDelete = { pendingDelete = grade })
                }
            }
        }
    }

    pendingDelete?.let { grade ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条课程存档？") },
            text = { Text("${grade.courseName} ${grade.termLabel}".trim()) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGrade(grade.id)
                    pendingDelete = null
                }) { Text("删除", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

// ── 学期分组标题 ──

@Composable
private fun TermHeader(term: String, grades: List<SavedGradeEntity>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val stats = SavedGradeStats.of(grades)
    ServiceSectionLabel(
        title = term,
        accent = ServiceAccent.ACADEMIC,
        modifier = Modifier.padding(top = skin.spaceMd, bottom = skin.spaceMicro),
        trailing = {
            Text(
                buildString {
                    append(grades.size).append(" 门")
                    if (stats.totalCredit > 0f) append("  ·  学分 ").append("%.1f".format(stats.totalCredit))
                    stats.averageScore?.let { append("  ·  均分 ").append("%.1f".format(it)) }
                },
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
        },
    )
}

// ── 汇总统计：主视觉卡 + 成绩分布卡 ──

@Composable
private fun SavedStatsHero(grades: List<SavedGradeEntity>) {
    val skin = LocalSkin.current
    val stats = remember(grades) { SavedGradeStats.of(grades) }
    ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "平均分",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(skin.spaceXs))
                ServiceHeroCount(
                    stats.averageScore?.let { "%.1f".format(it) } ?: "—",
                    "分",
                    numberSize = 42.sp,
                )
            }
            ServiceHeroBadge(Icons.Outlined.School)
        }
        Spacer(Modifier.height(skin.spaceLg))
        Row {
            ServiceHeroChip("共 ${stats.count} 门")
            Spacer(Modifier.width(skin.spaceSm))
            ServiceHeroChip(
                if (stats.totalCredit > 0f) "学分 ${"%.1f".format(stats.totalCredit)}" else "学分 —"
            )
            Spacer(Modifier.width(skin.spaceSm))
            // 有不及格时通过率不是 100% —— 这枚胶囊是"提醒"，所以不能只靠颜色，
            // 数字本身已经写清楚。
            ServiceHeroChip(
                "通过率 " + (stats.passRate?.let { "%.0f%%".format(it * 100) } ?: "—")
            )
        }
    }
}

@Composable
private fun DistributionCard(distribution: GradeDistribution) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "成绩分布",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text("按门数", fontSize = 11.sp, color = colors.textTertiary)
            }
            Spacer(Modifier.height(skin.spaceSm))
            GradeDistributionChart(distribution)
        }
    }
}

@Composable
private fun SavedGradeCard(grade: SavedGradeEntity, onDelete: () -> Unit) {
    // ⚠️ 这里**必须**用 LocalConfiguration.current.locales[0]，不能用 Locale.getDefault()。
    //
    // `Locale.getDefault()` 是**非 Compose 可观察**的：读它不会建立订阅关系，
    // 系统语言变了这一行也不会重算 —— 属于"看起来对、实际不刷新"的隐蔽问题。
    // Compose 的 lint 规则 NonObservableLocale 就是专门抓这个的（本项目首跑即命中）。
    //
    // 注：`yyyy-MM-dd HH:mm` 全是数字，格式本身其实与 locale 无关；
    // 但既然要传 locale，就该传一个**可观察**的，而不是绕过检查。
    val locale = LocalConfiguration.current.locales[0]
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val band = scoreBandOf(grade.totalScore)
    val bandColor = band.color()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (grade.totalScore.isBlank()) {
            null
        } else {
            BorderStroke(1.dp, bandColor.copy(alpha = 0.20f))
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = skin.spaceLg, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        grade.courseName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    if (grade.totalScore.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(bandColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                grade.totalScore,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = bandColor
                            )
                        }
                    }
                }
                Spacer(Modifier.height(skin.spaceSmall))
                Text(
                    buildString {
                        if (grade.credits.isNotBlank()) append("学分 ").append(grade.credits)
                        if (grade.gpa.isNotBlank()) append("  ·  绩点 ").append(grade.gpa)
                        if (grade.schoolName.isNotBlank()) append("  ·  ").append(grade.schoolName)
                    }.ifBlank { "暂无详细信息" },
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
                if (grade.savedAt > 0) {
                    Text(
                        "保存于 " + SimpleDateFormat("yyyy-MM-dd HH:mm", locale).format(Date(grade.savedAt)),
                        fontSize = 11.sp,
                        color = colors.textTertiary.copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    "删除",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
