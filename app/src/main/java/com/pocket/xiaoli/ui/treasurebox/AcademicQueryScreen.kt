package com.pocket.xiaoli.ui.treasurebox

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.assistant.copyToClipboard
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.campusui.CampusSearchBar
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginContent
import com.pocket.xiaoli.ui.zhengfang.ZhengfangSchoolList
import com.pocket.xiaoli.ui.components.infocard.color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 修课情况查询（试验阶段）：选学校 → 只登录 → 查看已修课程 / 学分统计 / 平均绩点。
 * 数据来自正方 v9 教务「学生学业情况」页面。选校/登录/验证码共用 ZhengfangLoginUi 组件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicQueryScreen(
    onBack: () -> Unit,
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer
) {
    val viewModel: AcademicQueryViewModel = viewModel(
        factory = AcademicQueryViewModel.Factory(client, schoolRegistry, appContainer)
    )
    val colors = LocalPocketXiaoliColors.current
    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    // ⚠️ 这里**不**自动登录（同「成绩查询」，2026-10 用户报"点进去自动开始"）：
    // 绑定账号已由 `ZhengfangLoginViewModel.init` 预填进表单，用户点一下再查。
    LaunchedEffect(Unit) { viewModel.prefillBoundCredentials() }

    Scaffold(
        // 页底与顶栏同色（surfaceBase），不再用 surfaceAlt 压出一层灰底 ——
        // 与服务页里其余二级页（成绩查询 / 成绩档案）保持同一套视觉。
        containerColor = colors.surfaceBase,
        topBar = {
            ServicePageTopBar(title = "修课情况", onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                selectedSchool == null -> ZhengfangSchoolList(state = viewModel)
                !loggedIn -> ZhengfangLoginContent(
                    state = viewModel,
                    hint = "登录正方教务系统（仅登录查询修课情况，不会导入课表）",
                    buttonText = "登录并查询修课情况"
                )
                else -> ResultContent(viewModel)
            }
        }
    }

    ZhengfangCaptchaHost(state = viewModel)
}

// ── 阶段 3：结果 ──

@Composable
private fun ResultContent(viewModel: AcademicQueryViewModel) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val types by viewModel.types.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val parseDiag by viewModel.parseDiag.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val diagSuspicious by viewModel.diagSuspicious.collectAsStateWithLifecycle()
    val totalProgress by viewModel.totalProgress.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ── 展开状态：两个"用户动过"的集合，默认值由 [academicExpandedIds] 按层级给 ──
    //
    // ⚠️ 用「逗号拼接的 id 串」而不是 Set：`rememberSaveable` 只存得进 Bundle 能装的东西，
    // 而 id 的字符集是 `[0-9A-Za-z_-]`（见 `isAcademicTypeId`），逗号不会与 id 冲突。
    // 实测数据量：23 个类型、其中 15 组有课程 —— 全展开滚起来没底，所以默认**展开到平台层**，
    // 模块与叶子要点一下才出来（口径与三条优先级全写在 `academicExpandedIds` 的注释里）。
    var openIds by rememberSaveable { mutableStateOf("") }
    var shutIds by rememberSaveable { mutableStateOf("") }
    val openSet = remember(openIds) { openIds.splitToIds() }
    val shutSet = remember(shutIds) { shutIds.splitToIds() }

    // ── 筛选与搜索 ──
    // 整页 23 个类型、上百门课，"翻"是没用的；真实用途是"我还差什么"。
    // 筛选状态同样用可保存的字符串（枚举名 / 搜索词）而不是复杂对象。
    var filterName by rememberSaveable { mutableStateOf(CourseFilter.ALL.name) }
    var query by rememberSaveable { mutableStateOf("") }
    val filter = CourseFilter.entries.firstOrNull { it.name == filterName } ?: CourseFilter.ALL
    val filtering = filter != CourseFilter.ALL || query.isNotBlank()
    val (visibleTypes, visibleCourses) = remember(types, courses, filter, query) {
        filterAcademicView(types, courses, filter, query)
    }
    // 展开态只算一次，下面三处（行、表头按钮、子树裁剪）读同一个结果。
    // ⚠️ 收起 = **整棵子树不渲染**，不只是藏掉它自己的课程表。
    // 以前这里没接 `visibleAcademicTypes`，于是点平台行只少了它自己那张表，
    // 下面的模块行原样留着 —— 用户看到的就是"展开收起按了没反应"。
    val tree = remember(visibleTypes, visibleCourses, openSet, shutSet, filtering) {
        academicTreeState(visibleTypes, visibleCourses, openSet, shutSet, filtering)
    }
    val badges = remember(types, courses) { filterBadgeCounts(types, courses) }
    // 学分构成条的段。**用全量 types 而不是 visibleTypes**：筛选/搜索改的是"看哪些课"，
    // 毕业要求怎么分配不会因为筛一下就变 —— 跟着筛选变化会让人以为培养方案变了。
    val composition = remember(types) { creditSegments(types) }
    // 树里平台层那一行的颜色从构成条借：同一块平台在上下两屏是同一个颜色
    val accents = remember(composition) { platformAccents(composition) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = skin.spaceLg)
    ) {
        // 操作行：复制诊断（常显）+ 重新登录
        // ⚠️ 复制诊断**必须常显**：它现在不只是"解析异常时上报"，还是把**明细接口原始记录**
        // 取回来的唯一入口（那几列的字段名只能从真实响应读出来）。藏在异常分支里就点不到了。
        // 2026-10 美化：从"一小行灰字 + 两个按钮"改成区块标题 —— 与下方「培养方案」同一套层级。
        AcademicActionsHeader(
            onCopyDiagnostics = {
                copyToClipboard(
                    context,
                    buildString {
                        // 版本号放最前面：远程排查时第一件事是确认"这份输出是哪个包产生的"
                        append("口袋小鲤 v").append(appVersionName(context)).append('\n')
                        append(parseDiag).append('\n')
                        append("类型 ").append(types.size).append(" 个\n")
                        if (probe.isNotBlank()) append(probe)
                    }
                )
            },
            onLogout = { viewModel.logout() },
        )

        if (error != null && !loaded) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                // 失败用描边而不是红底：整块红会把页面其余内容也压得紧张，
                // 而这里只需要"这一块出了问题"这一条信息。
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(skin.spaceLg)) {
                    Text("查询失败", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.danger)
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        error!!,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }

        if (isLoading && !loaded) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd)
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
                        color = colors.textSecondary
                    )
                }
            }
        }

        if (loaded) {
            val s = summary
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 只在**真的可疑**或"只剩一个类型"时才占地方：一两个重名是正常的，
                // 每次都弹红字就是狼来了（v0.1.15 实测误报过）。复制诊断按钮在上方常显，
                // 所以这里不再重复放按钮。
                if (parseDiag.isNotBlank() && (diagSuspicious || types.size <= 1)) {
                    item {
                        Text(
                            (if (diagSuspicious) "解析异常 · " else "解析自检 · ") + parseDiag,
                            fontSize = 11.sp,
                            color = if (diagSuspicious) colors.danger else colors.textTertiary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (s != null) {
                    item { SummaryCard(s, totalProgress) }
                    if (s.plannedTotal.isNotBlank() || s.unplannedPassed.isNotBlank()) {
                        item { CourseStatsCard(s) }
                    }
                }
                if (composition.size >= 2) {
                    item { CreditCompositionBar(composition) }
                }
                // 成绩分布同样吃**全量 courses**（理由与上面那条一样）：筛选改的是"看哪些行"，
                // 已经出的那些分不会因为筛一下就变。
                if (courses.isNotEmpty()) {
                    item { AcademicGradeCard(courses) }
                }
                if (types.isNotEmpty()) {
                    item {
                        AcademicFilterBar(
                            filter = filter,
                            query = query,
                            badges = badges,
                            onFilter = { filterName = it.name },
                            onQuery = { query = it },
                        )
                    }
                }
                if (types.isEmpty()) {
                    item {
                        // ⚠️ 空的要能分得清是"没选课"还是"没解析出来"：
                        // 2026-10 出过一次解析全空的事故，界面只显示"暂无课程明细"，
                        // 看上去像"我这个学期没课"，排查方向一开始就跑偏了。
                        ServiceEmptyCard(
                            icon = Icons.Filled.Warning,
                            title = "未解析到课程类型",
                            message = "教务页面结构可能已变更，请更新 App 后重试。",
                            accent = ServiceAccent.ACADEMIC,
                        )
                    }
                } else if (filtering && visibleTypes.isEmpty()) {
                    item {
                        EmptyFilterState(
                            onClear = {
                                filterName = CourseFilter.ALL.name
                                query = ""
                            }
                        )
                    }
                } else {
                    val withCourses = visibleTypes.filter { t -> visibleCourses.any { c -> c.typeId == t.id } }
                    item {
                        AcademicTreeHeader(
                            typeCount = visibleTypes.size,
                            courseGroupCount = withCourses.size,
                            // 筛选时命中项已经全展开，这个按钮没意义
                            showToggleAll = !filtering,
                            expandedCount = withCourses.count { tree.expandedIds.contains(it.id) },
                            onToggleAll = { expandAll ->
                                // 展开 = 把所有能展开的节点写进显式集合；
                                // 收回 = **清空两个集合**回到默认态（只剩主修与平台展开）。
                                // 直接写"全部收起"会让页面看着像空了，所以这一档收的是"到平台"。
                                openIds = if (expandAll) joinIds(tree.expandableIds) else ""
                                shutIds = ""
                            }
                        )
                    }
                    academicTreeSections(
                        types = tree.types,
                        courses = visibleCourses,
                        parents = tree.parents,
                        isExpanded = tree.expandedIds::contains,
                        onToggle = { id ->
                            val nowOpen = tree.expandedIds.contains(id)
                            openIds = joinIds(if (nowOpen) openSet - id else openSet + id)
                            shutIds = joinIds(if (nowOpen) shutSet + id else shutSet - id)
                        },
                        // 筛选时"祖先"会带着出现，它们名下没有命中课程 —— 别对它们喊"未取到明细"
                        hideEmptyHint = filtering,
                        platformAccents = accents,
                    )
                }
            }
        }
    }
}

/**
 * 结果页顶部的操作行：「已修课程」+ 复制诊断 + 重新登录。
 *
 * `internal`：整页 `ResultContent` 依赖 ViewModel、渲不出来；而这一行是本页**唯一常显的操作**，
 * 标题与两个按钮在 360dp 屏上会不会互相挤掉，只能靠整页基线确认。
 */
@Composable
internal fun AcademicActionsHeader(
    onCopyDiagnostics: () -> Unit,
    onLogout: () -> Unit,
) {
    val skin = LocalSkin.current
    ServiceSectionLabel(
        title = "已修课程",
        accent = ServiceAccent.ACADEMIC,
        modifier = Modifier.padding(top = skin.spaceSm, bottom = skin.spaceSm),
        trailing = {
            TextButton(onClick = onCopyDiagnostics) { Text("复制诊断", fontSize = 12.sp) }
            TextButton(onClick = onLogout) { Text("重新登录", fontSize = 13.sp) }
        },
    )
}

/** 当前 App 版本号（读法与「我的」页/更新检查一致 —— 本项目没开 BuildConfig 生成） */
private fun appVersionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
} catch (_: Exception) {
    "未知"
}

/**
 * 筛选 + 搜索条（抽成 `internal` 纯入参组件：整页依赖 ViewModel，单测渲不出来）。
 *
 * 四个口径各带一枚角标（未修/已通过 = 课程数，缺学分 = 类型数）——
 * 用户点之前就知道会筛出多少，"点了发现是空的"最劝退。
 */
@Composable
internal fun AcademicFilterBar(
    filter: CourseFilter,
    query: String,
    badges: Map<CourseFilter, Int>,
    onFilter: (CourseFilter) -> Unit,
    onQuery: (String) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            CourseFilter.entries.forEach { option ->
                val selected = option == filter
                val count = badges[option]
                Surface(
                    shape = RoundedCornerShape(50),
                    // 选中态用**实心主色**而不是 14% 淡底：淡底 + 同色字在小屏上
                    // 只是"稍微深一点的一枚胶囊"，筛没筛过一眼看不出来（用户此前报过"灰蒙蒙"）。
                    color = if (selected) accentColor() else colors.surfaceCard,
                    border = if (selected) null else BorderStroke(1.dp, colors.borderDivider),
                    modifier = Modifier
                        // ⚠️ chip 天然矮，触摸高度**写死下限**而不靠内边距估（本页折叠键同一个坑）
                        .heightIn(min = 44.dp)
                        .clickable { onFilter(option) },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = skin.spaceMd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (count != null && count > 0) "${option.label} $count" else option.label,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) onAccentColor() else colors.textSecondary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(skin.spaceSm))
        CampusSearchBar(
            value = query,
            onValueChange = onQuery,
            placeholder = "搜课程名或类型名（如：形势与政策）",
        )
    }
}

/** 筛选后一门课都没有：给一句实话 + 一个出口 */
@Composable
private fun EmptyFilterState(onClear: () -> Unit) {
    ServiceEmptyCard(
        icon = Icons.Filled.Search,
        title = "没有符合条件的课程",
        message = "换个筛选条件，或清空搜索词试试。",
        accent = ServiceAccent.ACADEMIC,
        actionLabel = "清除筛选",
        onAction = onClear,
    )
}

// ── 折叠 ──

/** 逗号拼接的 id 串 → id 集合（空串给空集，别给出一个 `[""]`） */
@Composable
internal fun AcademicTreeHeader(
    typeCount: Int,
    courseGroupCount: Int,
    expandedCount: Int,
    onToggleAll: (Boolean) -> Unit,
    showToggleAll: Boolean = true,
) {
    val skin = LocalSkin.current
    val allExpanded = courseGroupCount > 0 && expandedCount >= courseGroupCount
    val toggle: (@Composable () -> Unit)? = if (courseGroupCount > 0 && showToggleAll) {
        {
            TextButton(onClick = { onToggleAll(!allExpanded) }) {
                Text(if (allExpanded) "收回到平台" else "展开全部课程", fontSize = 12.sp)
            }
        }
    } else {
        null
    }
    ServiceSectionLabel(
        title = "培养方案 · $typeCount 个类型",
        accent = ServiceAccent.ACADEMIC,
        modifier = Modifier.padding(top = skin.spaceSm, bottom = skin.spaceMicro),
        trailing = toggle,
    )
}

// ── 汇总卡片 ──
