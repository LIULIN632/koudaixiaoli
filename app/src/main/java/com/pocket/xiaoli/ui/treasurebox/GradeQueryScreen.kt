package com.pocket.xiaoli.ui.treasurebox

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZfGradeItem
import com.pocket.xiaoli.network.ZfTerm
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginContent
import com.pocket.xiaoli.ui.zhengfang.ZhengfangSchoolList

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.InlineError
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.infocard.ScoreBand
import com.pocket.xiaoli.ui.components.infocard.color
import com.pocket.xiaoli.ui.components.infocard.label
import com.pocket.xiaoli.ui.components.infocard.scoreBandOf
import com.pocket.xiaoli.ui.components.uniqueKeys

/**
 * 「成绩查询」独立页：在线查教务成绩（只登录、不导课表），可把成绩存档到「我的课程」。
 * 与「我的课程」分开后，这里专注在线查询与存档，不再混本地列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeQueryScreen(
    onBack: () -> Unit,
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
) {
    val colors = LocalPocketXiaoliColors.current
    val viewModel: GradeQueryViewModel = viewModel(
        factory = GradeQueryViewModel.Factory(client, schoolRegistry, appContainer)
    )

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(title = "成绩查询", onBack = onBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GradeQueryContent(viewModel)
        }
    }
}
/**
 * 成绩查询内容：选学校 → 只登录 → 选学期 → 查看总评/平时/期末/期中，
 * 可导出成绩到「课程管理」。学校接口未下发分项时对应字段为空，页面显示 —。
 * 选校/登录/验证码共用 ZhengfangLoginUi 组件。
 */
@Composable
fun GradeQueryContent(
    viewModel: GradeQueryViewModel,
    modifier: Modifier = Modifier
) {
    val colors = LocalPocketXiaoliColors.current
    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    // ⚠️ 这里**不**自动登录（2026-10 用户报"查成绩点进去就自动开始"）。
    // 账号密码已由 `ZhengfangLoginViewModel.init` 预填，进页面看到的是"填好的表单 + 一个按钮"，
    // 用户点一下才开始 —— 见 `autoLoginIfBound` 的 KDoc（那里记了自动登录的三条代价）。
    // 保留自动登录的只有一条路：「我的课程」里用户**自己点**「一键同步」。
    LaunchedEffect(Unit) { viewModel.prefillBoundCredentials() }

    // ⚠️ 这里原先有一条「身份条」（图标 + 「在线查询」+ 一行说明），2026-10 已删：
    // 用户报「进去多一行描述」→ 去掉说明后仍报「还是有重复信息」——
    // 因为服务页卡片、TopAppBar、身份条**说的是同一件事的三个名字**，页面名在 TopAppBar 上已有归属。
    // 理由与"以后要加解释放哪里"写在 `memory/03-pitfalls.md`。不要加回来。
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
    ) {
        when {
            selectedSchool == null -> ZhengfangSchoolList(state = viewModel)
            !loggedIn -> ZhengfangLoginContent(
                state = viewModel,
                hint = "登录正方教务系统（仅登录查询成绩，不会导入课表）",
                buttonText = "登录并查询成绩"
            )
            else -> GradesContent(viewModel)
        }
    }

    ZhengfangCaptchaHost(state = viewModel)
}

// ── 阶段 3：成绩列表 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradesContent(viewModel: GradeQueryViewModel) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val terms by viewModel.terms.collectAsStateWithLifecycle()
    val selectedTerm by viewModel.selectedTerm.collectAsStateWithLifecycle()
    val grades by viewModel.grades.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val gradesLoaded by viewModel.gradesLoaded.collectAsStateWithLifecycle()
    val savedKeys by viewModel.savedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = skin.spaceLg)
    ) {
        // 学期选择：独占一行，避免和操作按钮挤在一起
        Spacer(Modifier.height(skin.spaceXs))
        TermDropdown(
            terms = terms,
            selected = selectedTerm,
            onSelect = { viewModel.selectTerm(it) },
            modifier = Modifier.fillMaxWidth()
        )

        val unsavedCount = grades.count {
            "${it.courseName}|${it.termLabel}|${it.totalScore}" !in savedKeys
        }

        // 操作行：状态提示 + 刷新 / 重新登录
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = skin.spaceSmall)
        ) {
            Text(
                when {
                    isLoading -> progress.ifBlank { "加载中……" }
                    grades.isEmpty() -> "选择学期后自动查询"
                    unsavedCount == 0 -> "本学期成绩已全部存档"
                    else -> "还有 $unsavedCount 门未存档"
                },
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.refresh() }, enabled = !isLoading && selectedTerm != null) {
                Text("刷新", fontSize = 12.sp)
            }
            TextButton(onClick = { viewModel.logout() }) {
                Text("重新登录", fontSize = 12.sp)
            }
        }

        // ⚠️ 2026-10-22：从「平均分」往下**全部**进了这一条列表。
        //
        // 以前是「固定头部 + 内嵌 LazyColumn(weight(1f))」：往下滑时那张蓝色平均分卡
        // 一直挂在顶上，课程明细的第一张卡从它下沿"滑出来"、被切掉半张 ——
        // 用户报的「整个卡片始终悬浮在内容上，有点遮挡内容」就是它。
        // 只有学期下拉与「刷新 / 重新登录」这一行留在外面（都是控件，高度很薄）。
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = skin.spaceXxl),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm)
        ) {
            // 主视觉：平均分 / 门数 / 绩点。原先这三个数被压在一张矮白卡里，
            // 与下面每门课的成绩卡是同一量级 —— 页面没有一个"先看这里"的锚点。
            if (grades.isNotEmpty()) {
                item(key = "hero") {
                    GradeSummaryHero(grades)
                }
            }

            if (unsavedCount > 0) {
                item(key = "save_all") {
                    Button(
                        onClick = {
                            viewModel.saveAllGrades()
                            Toast.makeText(context, "已存档当前学期成绩", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = skin.spaceSm),
                        shape = RoundedCornerShape(skin.radiusCard),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentMain,
                            contentColor = onAccentColor(),
                        ),
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(skin.spaceSm))
                        Text("全部存档（$unsavedCount 门）", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (error != null && grades.isEmpty()) {
                // 用 tokens.danger 而不是硬编码 0xFFE57373：那套深色主题下对比度不足，
                // 而且 8 套主题里 danger 本来就各不相同（这就是令牌存在的意义）。
                // 重试走 viewModel.retry() 而不是 refresh()：学期列表没拿到时
                // refresh 会直接 return（它要求已选中学期），按钮会变成按不动的
                item(key = "error") {
                    InlineError(
                        message = error!!,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.padding(vertical = skin.spaceSm),
                    )
                }
            }

            if (isLoading && grades.isEmpty()) {
                item(key = "loading") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = skin.spaceLg)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = accentColor()
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            progress.ifBlank { "加载中……" },
                            fontSize = 13.sp,
                            color = colors.textTertiary
                        )
                    }
                }
            }

            when {
                grades.isEmpty() && gradesLoaded -> {
                    item(key = "empty") {
                        ServiceEmptyCard(
                            icon = Icons.Outlined.Inbox,
                            title = "这个学期还没有成绩",
                            message = "可能是成绩还没录入，或者学期选错了。换个学期看看，或稍后再刷新。",
                            accent = ServiceAccent.ACADEMIC,
                            actionLabel = "重新查询",
                            onAction = { viewModel.refresh() },
                        )
                    }
                }
                grades.isNotEmpty() -> {
                    item(key = "label") {
                        ServiceSectionLabel(
                            title = "课程明细",
                            accent = ServiceAccent.ACADEMIC,
                            modifier = Modifier.padding(top = skin.spaceSm),
                            trailing = {
                                Text("${grades.size} 门", fontSize = 12.sp, color = colors.textTertiary)
                            },
                        )
                    }
                    // key 必须唯一：`课程名|学期` 会撞 —— 重修刷分、同名课程多次考试、
                    // 体育 1/2/3/4 都显示为「体育」都会让两行拿到同一个 key，
                    // 而 LazyColumn 遇到重复 key 是**直接崩**（不是警告）。
                    val keys = uniqueKeys(grades) { "${it.termLabel}|${it.courseName}|${it.totalScore}" }
                    itemsIndexed(grades, key = { index, _ -> keys[index] ?: index.toString() }) { _, item ->
                        val key = "${item.courseName}|${item.termLabel}|${item.totalScore}"
                        GradeCard(
                            item = item,
                            isSaved = key in savedKeys,
                            onSave = {
                                viewModel.saveGrade(item)
                                Toast.makeText(context, "已导出到课程管理", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermDropdown(
    terms: List<ZfTerm>,
    selected: ZfTerm?,
    onSelect: (ZfTerm) -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.label ?: "请选择学期",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            shape = RoundedCornerShape(skin.radiusCard),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceCard,
                unfocusedContainerColor = colors.surfaceCard,
                focusedBorderColor = accentColor(),
                unfocusedBorderColor = colors.borderDivider,
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            terms.forEach { term ->
                DropdownMenuItem(
                    text = { Text(term.label, fontSize = 13.sp) },
                    onClick = {
                        onSelect(term)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun GradeSummaryHero(grades: List<ZfGradeItem>) {
    val skin = LocalSkin.current
    val scores = grades.mapNotNull { it.totalScore.toFloatOrNull() }
    val gpas = grades.mapNotNull { it.gpa.toFloatOrNull() }
    ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "本学期平均分",
                    fontSize = 12.sp,
                    // 用 0.9 而不是常见的 0.7：主视觉卡底压到了亮度 ≤ 0.12，
                    // 70% 白字压上去约 3:1 —— 12sp 小字读不清。
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(skin.spaceXs))
                ServiceHeroCount(
                    scores.takeIf { it.isNotEmpty() }?.let { "%.1f".format(it.average()) } ?: "—",
                    "分",
                    numberSize = 42.sp,
                )
            }
            ServiceHeroBadge(Icons.Outlined.School)
        }
        Spacer(Modifier.height(skin.spaceLg))
        Row {
            ServiceHeroChip("共 ${grades.size} 门")
            Spacer(Modifier.width(skin.spaceSm))
            ServiceHeroChip(
                "平均绩点 " +
                    (gpas.takeIf { it.isNotEmpty() }?.let { "%.2f".format(it.average()) } ?: "—")
            )
        }
    }
}

@Composable
private fun GradeCard(item: ZfGradeItem, isSaved: Boolean, onSave: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val band = scoreBandOf(item.totalScore)
    val bandColor = band.color()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        // 成绩档位色做一条很淡的描边：每门课的卡片因此有颜色线索，
        // 而不用在卡面上再铺一层色底（那会削弱分数本身）。
        border = if (band == ScoreBand.UNKNOWN) {
            null
        } else {
            BorderStroke(1.dp, bandColor.copy(alpha = 0.22f))
        },
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.courseName.ifBlank { "未知课程" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (item.totalScore.isNotBlank()) {
                    // 用语义档位而非硬编码色：深色模式下会自动换成更亮的语义色，
                    // 而原先固定的"深绿"压在深色卡片上对比度不足 —— 那恰恰是要看清的数字。
                    Surface(color = bandColor.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                        Text(
                            band.label() ?: "—",
                            fontSize = 10.sp,
                            color = bandColor,
                            modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(skin.spaceSm))
                }
                Text(
                    item.totalScore.ifBlank { "—" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = bandColor
                )
                Spacer(Modifier.width(skin.spaceXs))
                // 保存到已修课程（成绩存档）
                IconButton(onClick = onSave, modifier = Modifier.size(34.dp).invisibleTouchTarget()) {
                    Icon(
                        if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = if (isSaved) "已保存" else "保存成绩",
                        tint = if (isSaved) colors.textTertiary else accentColor(),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceMd))
            // 分项行：平时 / 期末 / 期中。放进一块内嵌浅底，
            // 与「课程名 + 总评」拉开一档，扫读时不会串行。
            Surface(
                color = colors.surfaceAlt,
                shape = RoundedCornerShape(skin.radiusChip),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(vertical = skin.spaceSm)) {
                    GradeCell("平时", item.regular, Modifier.weight(1f))
                    GradeCell("期末", item.final, Modifier.weight(1f))
                    GradeCell("期中", item.midterm, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(skin.spaceSm))
            HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                buildString {
                    append("绩点 ").append(item.gpa.ifBlank { "—" })
                    append("  ·  学分 ").append(item.credits.ifBlank { "—" })
                    if (item.examType.isNotBlank()) append("  ·  ").append(item.examType)
                    append("  ·  ").append(item.termLabel)
                },
                fontSize = 12.sp,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun GradeCell(label: String, value: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = colors.textTertiary)
        Spacer(Modifier.height(skin.spaceMicro))
        Text(
            value.ifBlank { "—" },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
    }
}
