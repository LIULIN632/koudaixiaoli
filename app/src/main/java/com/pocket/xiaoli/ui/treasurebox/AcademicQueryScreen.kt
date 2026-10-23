package com.pocket.xiaoli.ui.treasurebox

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.progressSemantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicSummary
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.assistant.copyToClipboard
import com.pocket.xiaoli.ui.components.campusui.CampusSearchBar
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginContent
import com.pocket.xiaoli.ui.zhengfang.ZhengfangSchoolList
import com.pocket.xiaoli.ui.components.infocard.ScoreBand
import com.pocket.xiaoli.ui.components.infocard.color
import com.pocket.xiaoli.ui.components.infocard.scoreBandOf
import com.pocket.xiaoli.util.maskStudentId
import kotlin.math.roundToInt
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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("修课情况", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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

    // ── 折叠状态（两段各一套，默认值不同）──
    //
    // ⚠️ 用「逗号拼接的 id 串」而不是 Set：`rememberSaveable` 只存得进 Bundle 能装的东西，
    // 而 id 的字符集是 `[0-9A-Za-z_-]`（见 `isAcademicTypeId`），逗号不会与 id 冲突。
    // 实测数据量：23 个类型、其中 15 组有课程 —— 全展开滚起来没底，所以：
    //   · 学分卡（概览）默认**全展开**，点有子节点的行收起整棵子树；
    //   · 课程明细（明细）默认**全收起**，表头带门数，点开才拉课程。
    var collapsedIds by rememberSaveable { mutableStateOf("") }
    var expandedIds by rememberSaveable { mutableStateOf("") }
    val collapsedSet = remember(collapsedIds) { collapsedIds.splitToIds() }
    val expandedSet = remember(expandedIds) { expandedIds.splitToIds() }

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
    val badges = remember(types, courses) { filterBadgeCounts(types, courses) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = skin.spaceLg)
    ) {
        // 操作行：复制诊断（常显）+ 重新登录
        // ⚠️ 复制诊断**必须常显**：它现在不只是"解析异常时上报"，还是把**明细接口原始记录**
        // 取回来的唯一入口（那几列的字段名只能从真实响应读出来）。藏在异常分支里就点不到了。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "已修课程 · 学分统计",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
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
                }
            ) { Text("复制诊断", fontSize = 12.sp) }
            TextButton(onClick = { viewModel.logout() }) {
                Text("重新登录", fontSize = 13.sp)
            }
        }

        if (error != null && !loaded) {
            Text(
                error!!,
                color = colors.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = skin.spaceSm)
            )
        }

        if (isLoading && !loaded) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = skin.spaceHuge),
                            contentAlignment = Alignment.Center
                        ) {
                            // ⚠️ 空的要能分得清是"没选课"还是"没解析出来"：
                            // 2026-10 出过一次解析全空的事故，界面只显示"暂无课程明细"，
                            // 看上去像"我这个学期没课"，排查方向一开始就跑偏了。
                            Text(
                                "未解析到课程类型\n教务页面结构可能已变更，请更新 App 后重试",
                                color = colors.textTertiary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
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
                            // 筛选时命中项已经自动展开，这个按钮没意义
                            showToggleAll = !filtering,
                            expandedCount = withCourses.count { expandedSet.contains(it.id) },
                            onToggleAll = { expandAll ->
                                // 「展开全部」= 父母展开 + 各类课程展开；
                                // 「收起全部」只收课程，**不动树的层级** —— 把 24 行折成一行的
                                // "全部收起"会让页面看起来像空了（本轮合并前就是两个按钮各管一半）。
                                collapsedIds = ""
                                expandedIds = if (expandAll) withCourses.joinToString(",") { it.id } else ""
                            }
                        )
                    }
                    academicTreeSections(
                        types = visibleTypes,
                        courses = visibleCourses,
                        // ⚠️ 一棵树、**一套展开状态**（本轮合并的核心）：
                        // 有子节点的默认展开（要能一眼看到 24 行），叶子默认收起（课程按需展开）。
                        // 曾经是两套状态（卡片收起子树 / 明细逐类型展开），收了这个那个还开着，容易看糊涂。
                        isExpanded = { id ->
                            val hasChildren = visibleTypes.any { it.parentId == id }
                            if (filtering || hasChildren) !collapsedSet.contains(id)
                            else expandedSet.contains(id)
                        },
                        onToggle = { id ->
                            val hasChildren = visibleTypes.any { it.parentId == id }
                            if (hasChildren) {
                                collapsedIds = if (collapsedSet.contains(id)) {
                                    collapsedSet.minus(id).joinToString(",")
                                } else {
                                    (collapsedSet + id).joinToString(",")
                                }
                            } else {
                                expandedIds = if (expandedSet.contains(id)) {
                                    expandedSet.minus(id).joinToString(",")
                                } else {
                                    (expandedSet + id).joinToString(",")
                                }
                            }
                        },
                        // 筛选时"祖先"会带着出现，它们名下没有命中课程 —— 别对它们喊"未取到明细"
                        hideEmptyHint = filtering
                    )
                }
            }
        }
    }
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
                    color = if (selected) accentColor().copy(alpha = 0.14f) else colors.surfaceCard,
                    border = BorderStroke(
                        1.dp,
                        if (selected) accentColor() else colors.borderDivider
                    ),
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
                            color = if (selected) accentColor() else colors.textSecondary,
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
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "没有符合条件的课程\n换个筛选条件，或清空搜索词试试",
            color = colors.textTertiary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) { Text("清除筛选", fontSize = 13.sp) }
    }
}

// ── 折叠 ──

/** 逗号拼接的 id 串 → id 集合（空串给空集，别给出一个 `[""]`） */
private fun String.splitToIds(): Set<String> =
    if (isBlank()) emptySet() else split(',').filter { it.isNotBlank() }.toSet()

/**
 * 可见的类型：**祖先被收起，整棵子树都不渲染**。
 *
 * `types` 是文档顺序（父在子前，见 `ZhengfangAcademicApi.parentIdOf`），
 * 所以这里只需按 `parentId` 链往上找有没有被收起的祖先。
 */
internal fun visibleAcademicTypes(
    types: List<AcademicTypeInfo>,
    collapsedIds: Set<String>,
): List<AcademicTypeInfo> {
    if (collapsedIds.isEmpty()) return types
    val byId = types.associateBy { it.id }
    return types.filter { type ->
        var parent = byId[type.parentId]
        var guard = 0
        while (parent != null && guard < 8) {
            if (parent.id in collapsedIds) return@filter false
            parent = byId[parent.parentId]
            guard++
        }
        true
    }
}

/**
 * 培养方案树：**一行一个节点**，学分 / 进度条 / 门次 / 该类型下的课程全在同一棵树里。
 *
 * ## 为什么合并（2026-10 用户问"课程类型和课程明细为啥是分开的"）
 *
 * 合并前是**两代功能并排**：先有「课程类型学分卡」（回答"我还差多少"），后有「课程明细」
 * （回答"哪几门没修"）。两块画的是**同一棵树**，各有各的展开状态与默认值 ——
 * 收了这个那个还开着，看着像两个不同的东西，还得滚两遍。现在：
 *
 * - **一套展开状态**（`isExpanded`），一个箭头，点哪儿都是"展开/收起这一节点"；
 * - 类型行同时携带学分与进度（原来只在卡片里）与课程数（原来只在明细表头里）；
 * - 筛选与搜索只需要作用一次。
 *
 * 抽成 `LazyListScope` 扩展是为了**单测能渲**：整页 `ResultContent` 依赖 ViewModel，
 * 在 Robolectric 里起不来，而"点一行能不能展开/收起"只能靠语义树断言。
 */
internal fun LazyListScope.academicTreeSections(
    types: List<AcademicTypeInfo>,
    courses: List<AcademicCourseItem>,
    isExpanded: (String) -> Boolean,
    onToggle: (String) -> Unit,
    hideEmptyHint: Boolean = false,
) {
    // ⚠️ **每个类型都要出行**，哪怕它一门课都没取到 —— 以前"没课就不出行"，
    // 于是明细接口一失败，用户看到的就是"这个类型不见了"，与"类型没解析出来"分不清。
    // ⚠️ 分组用 **id** 不能用名字：培养方案里真的存在同名节点
    //（通识必修与通识选修下各有一个「身心健康类课程」），按名字分会让两组课程串在一起。
    types.forEach { type ->
        val typeCourses = courses.filter { it.typeId == type.id }
        val expanded = isExpanded(type.id)
        // ⚠️ 「学校暂无该类型的课程明细」**只给叶子节点**：
        // 平台/模块**本来就不直接挂课**（课挂在叶子类型上），给它们喊这句是噪音 ——
        // 合并成一棵树之后这一条尤其刺眼（每个父节点下面都挂一句），渲图才看出来的。
        val hasChildren = types.any { it.parentId == type.id }
        item(key = "type_${type.id}") {
            AcademicTypeRow(
                type = type,
                courseCount = typeCourses.size,
                expandable = typeCourses.isNotEmpty() || hasChildren,
                expanded = expanded,
                onToggle = { onToggle(type.id) }
            )
        }
        if (typeCourses.isEmpty() && !hideEmptyHint && !hasChildren) {
            item(key = "empty_${type.id}") {
                // ⚠️ 措辞刻意是"学校暂无"而不是"未取到"：实测学校自己的页面上，
                // 「创新创业情况尚未数据信息」这类节点**根本没有明细链接**（`jdkcsx` 为空串的
                // 那几个平台/模块节点也一样）。写成"未取到"会让用户以为这是 App 的故障 ——
                // 用户 2026-10 报的「有的课程明细查不到」有一部分就是这么被误导出来的。
                Text(
                    "（学校暂无该类型的课程明细）",
                    fontSize = 12.sp,
                    color = LocalPocketXiaoliColors.current.textTertiary,
                    modifier = Modifier.padding(
                        start = (LocalSkin.current.spaceSm.value + type.depth * 10).dp,
                        bottom = LocalSkin.current.spaceXs
                    )
                )
            }
        } else if (expanded) {
            items(typeCourses, key = { "${type.id}_${it.courseId}_${it.courseName}" }) { course ->
                // 课程卡片与它所属的类型行**左对齐**（同缩进）：不然卡片像是"跳出去"了，
                // 一眼看不出这几门课属于上面那个类型 —— 渲图看出来的。
                Box(modifier = Modifier.padding(start = (type.depth * 10).coerceAtMost(20).dp)) {
                    AcademicCourseCard(course)
                }
            }
        }
    }
}

/** 树的小标题行：一句说明 + 「全部展开 / 收起全部课程」 */
@Composable
internal fun AcademicTreeHeader(
    typeCount: Int,
    courseGroupCount: Int,
    expandedCount: Int,
    onToggleAll: (Boolean) -> Unit,
    showToggleAll: Boolean = true,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val allExpanded = courseGroupCount > 0 && expandedCount >= courseGroupCount
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "培养方案 · $typeCount 个类型",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (courseGroupCount > 0 && showToggleAll) {
            TextButton(onClick = { onToggleAll(!allExpanded) }) {
                Text(if (allExpanded) "收起全部课程" else "展开全部课程", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(skin.spaceXs))
    }
}

// ── 汇总卡片 ──

/**
 * 学业汇总卡（绩点 + 学分环 + 一行脚注）。
 *
 * ## 版式（2026-10 美化时重排）
 *
 * **一个主角 + 一行脚注**：左边学分环（这一页最想让人一眼看到的"修了多少"），
 * 右边绩点（同样是大字，但排在环的右侧，两者并排而不是上下叠）。
 * 学号与「计划内通过 N 门」压成**最底下一行弱化小字** ——
 * 它们是"核实身份用"的信息，不该与绩点争字号（改造前它们占着右上角两行，
 * 与左下的大字形成两块同重的视觉，卡里还空出一大块）。
 *
 * `internal` 而非 `private`：整页 `ResultContent` 依赖 ViewModel、渲不出来，
 * 而这条卡上有两个**只能靠语义树守**的约定（学号必须脱敏、学分为 0 时不显示「还差」），
 * 所以把它抽成纯入参组件供 `AcademicQueryPrivacyTest` / `AcademicQueryScreenshotTest` 直接渲染。
 */
@Composable
internal fun SummaryCard(summary: AcademicSummary, progress: CreditProgress?) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) {
                    CreditRing(progress = progress, modifier = Modifier.size(78.dp))
                    Spacer(Modifier.width(skin.spaceLg))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("平均学分绩点", fontSize = 12.sp, color = colors.textTertiary)
                    Text(
                        summary.gpa.ifBlank { "—" },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor()
                    )
                    if (progress != null && progress.required > 0f) {
                        Spacer(Modifier.height(skin.spaceMicro))
                        Text(
                            "已获 ${"%.1f".format(progress.earned)} / 要求 ${"%.1f".format(progress.required)} 学分",
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
            val footnote = buildList {
                // ⚠️ 必须脱敏：学号是全项目共用一条规则的敏感值（util/StudentIdMask.kt），
                // 这一页曾经整串印出来 —— 与「我的」页/选课页的显示口径不一致，
                // 也会在截图分享时把学号漏出去。
                if (summary.studentId.isNotBlank()) add("学号 ${maskStudentId(summary.studentId)}")
                if (summary.plannedPassed.isNotBlank()) add("计划内通过 ${summary.plannedPassed} 门")
                if (summary.plannedMissed.isNotBlank()) add("计划内未修 ${summary.plannedMissed} 门")
            }.joinToString("  ·  ")
            if (footnote.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceMd))
                Text(footnote, fontSize = 11.sp, color = colors.textTertiary)
            }
        }
    }
}

/** 学分完成度环形进度：中心显示百分比，外圈按已获/要求绘制（与总进度条同一套金属金）。 */
@Composable
private fun CreditRing(progress: CreditProgress, modifier: Modifier = Modifier) {
    val colors = LocalPocketXiaoliColors.current
    // 这枚环代表**总进度**（培养方案总学分），所以用总进度金属金、并在弧上走一遍
    // `#FFD700 → #F9A825` 的扫掠渐变 —— 与那条金属条同一个"材质"，两处不会各长一样。
    // 渐变的两个端点都**不透明**（用户要求"去掉任何降低透明度的属性"）。
    val goldBrush = Brush.sweepGradient(
        listOf(MetallicGoldStart, MetallicGoldEnd, MetallicGoldStart)
    )
    val ratio = progress.ratio
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            drawArc(
                color = colors.surfaceHighlight,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (ratio != null) {
                drawArc(
                    brush = goldBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ratio != null) "${(ratio * 100).roundToInt()}%" else "—",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary
            )
            Text("学分", fontSize = 10.sp, color = colors.textTertiary)
        }
    }
}
@Composable
private fun CourseStatsCard(summary: AcademicSummary) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
    ) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = 12.dp)) {
            Text("培养方案完成情况", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            if (summary.plannedTotal.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("计划内总数", summary.plannedTotal, Modifier.weight(1f))
                    StatCell("通过", summary.plannedPassed, Modifier.weight(1f))
                    StatCell("未通过", summary.plannedFailed, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("未修", summary.plannedMissed, Modifier.weight(1f))
                    StatCell("在读", summary.plannedIn, Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
            if (summary.unplannedPassed.isNotBlank() || summary.unplannedFailed.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceSm))
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Spacer(Modifier.height(skin.spaceSm))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("计划外通过", summary.unplannedPassed, Modifier.weight(1f))
                    StatCell("计划外未通过", summary.unplannedFailed, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.ifBlank { "—" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(skin.spaceMicro))
        Text(label, fontSize = 11.sp, color = colors.textTertiary)
    }
}

// ── 学分汇总（各类型）──

@Composable
private fun CreditStatsHeader() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Text(
        "课程类型学分",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary,
        modifier = Modifier.padding(top = skin.spaceSmall)
    )
}

/**
 * 各类课程类型的学分条（已获/要求 + 进度 + 还差多少），**按培养方案的层级组织**。
 *
 * ⚠️ 「还差 X 学分」**只在真的缺学分时**才出现：`missedCredit` 的 0 / `0.0` / `无`
 * 在解析层就已经归一成空串（`ZhengfangAcademicApi.normalizeCredit`），
 * 这里再挡一次 0 —— 曾经的线上问题是「进度条已经满格，下面却写着还差 1 学分」，
 * 因为那时这一行读的是类型 id 而不是未获得学分。
 *
 * 折叠：有子节点的行可点，收起后**整棵子树都不渲染**（23 个节点的树全展开太长）。
 * 默认全展开 —— 这张卡的用途是"扫一眼看哪块没修满"，先收起反而要多点几下。
 *
 * `internal` 是为了让 `AcademicQueryPrivacyTest` 直接渲这张卡（整页依赖 ViewModel，渲不出来）。
 */
/**
 * 培养方案的层级怎么区分（4 级：主修 → 平台 → 模块 → 课程类型）。
 *
 * 只靠缩进是不够的 —— 2026-10 渲了真实 23 个节点看过：四级**字号字重完全一样**，
 * 屏幕上就是"一串往右挪了 12dp 的行"，分不出谁属于谁。所以这里给每一级三样东西：
 *
 * | 层级 | 字号 / 字重 | 颜色 | 左侧色条 |
 * |---|---|---|---|
 * | 0 主修（总计） | 16sp ExtraBold | 主色 | 无 |
 * | 1 平台 | 14.5sp Bold | textPrimary | 3dp 主色 |
 * | 2 模块 | 13.5sp SemiBold | textPrimary | 2dp textTertiary |
 * | 3 课程类型 | 13sp Normal | textPrimary | 无 |
 *
 * 为什么不给每级配不同颜色：这套配色要跟着 **8 套主题 × 明暗** 走，
 * 多加几种"层级色"必然有一半主题下对比度不达标（`ThemeContrastTest` 一直在守这条）。
 * 字号/字重/色条是**不依赖色相**的层级信号，换主题不会失效。
 */
private data class HierarchyStyle(
    val fontSize: TextUnit,
    val weight: FontWeight,
    val color: Color?,
    val barWidth: Dp,
    val barColor: Color?,
    val indentPerDepth: Dp,
)

@Composable
private fun hierarchyStyle(depth: Int): HierarchyStyle {
    val colors = LocalPocketXiaoliColors.current
    return when (depth) {
        0 -> HierarchyStyle(16.sp, FontWeight.ExtraBold, accentColor(), 0.dp, null, 0.dp)
        1 -> HierarchyStyle(14.5.sp, FontWeight.Bold, colors.textPrimary, 3.dp, accentColor(), 10.dp)
        2 -> HierarchyStyle(13.5.sp, FontWeight.SemiBold, colors.textPrimary, 2.dp, colors.textTertiary, 10.dp)
        else -> HierarchyStyle(13.sp, FontWeight.Normal, colors.textPrimary, 0.dp, null, 10.dp)
    }
}

/**
 * 树里的**一行**：名称 + 已获/要求 + 进度条 + 还差/门次 + 展开箭头。
 *
 * 这一行以前是两块东西（卡片里的一行只带学分、明细区的一行只带课程数与箭头），
 * 2026-10 合并成一行 —— 点它同时意味着"展开这一个节点"。
 *
 * ⚠️ `toggleable` 不补最小触摸尺寸（Material 组件才补），本项目底线 44dp，
 * 所以显式写死 `heightIn(min = 44.dp)`；否则手指点不准，而截图看不出来。
 */
@Composable
internal fun AcademicTypeRow(
    type: AcademicTypeInfo,
    courseCount: Int,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val style = hierarchyStyle(type.depth)
    val earned = type.earnedCredit.trim().toFloatOrNull()
    val required = type.requiredCredit.trim().toFloatOrNull()
    val ratio = if (required != null && required > 0f && earned != null) {
        (earned / required).coerceIn(0f, 1f)
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (type.depth * style.indentPerDepth.value).dp)
            .padding(top = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expandable) {
                        Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() })
                    } else {
                        Modifier
                    }
                )
                .heightIn(min = 44.dp)
        ) {
            // 左侧层级色条：平台 = 主色 3dp，模块 = textTertiary 2dp，其余不画
            if (style.barColor != null) {
                Box(
                    modifier = Modifier
                        .width(style.barWidth)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(style.barColor)
                )
                Spacer(Modifier.width(skin.spaceSmall))
            }
            Text(
                type.name,
                fontSize = style.fontSize,
                fontWeight = style.weight,
                color = style.color ?: colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            // 「已获」是这一行真正要读的数字，给它字重与正文色；「/ 要求」是参照系，压成弱化色。
            // （改造前是一整串同色灰字「已获 49.5 / 要求 53.5」，扫读时两个数一样重，看不出重点。）
            Text(
                text = type.earnedCredit.ifBlank { "—" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = " / ${type.requiredCredit.ifBlank { "—" }}",
                fontSize = 12.sp,
                color = colors.textTertiary,
            )
            if (expandable) {
                Spacer(Modifier.width(skin.spaceMicro))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 进度条**每一级都画**（只要拿得到「要求学分」当分母）。
        //
        // ⚠️ v0.1.18 曾限制成"只画到模块级"，理由是"13 个课程类型各来一条横杠会把层级压平"。
        // 但那次同时加的字号阶梯 + 左侧层级色条已经承担了层级，进度条不必再让位；
        // 而"这块修满没有"恰恰是这一页最该一眼看到的东西（用户直接问过"为啥有的类型没有进度条"）。
        // 叶子级用更细的条（3dp vs 5dp）：横杠多了也不至于变成一堵墙。
        //
        // ⚠️ 分母缺失（要求学分为空 / 0）时**仍然不画**：没有分母就谈不上进度，
        // 补一条 0% 或 100% 都是**编数据**（真实数据里「其他选修课程」正是这种：
        // `已获 4.0 / 要求 0.0`）。
        //
        // ⚠️ 但**分子为 0 一定要画**（0% 的条）：那是"这门模块一门都没过"，
        // 是这一页最该被看见的状态。曾经因为"学分 0 被当缺失"整条不画 ——
        // 用户报过「为零分的课程类型没有进度条」（见 normalizeCredit 的 KDoc）。
        // 0% 时画面上只剩轨道，所以轨道色要用**看得见**的那一档
        //（`surfaceHighlight` 在页面底色上几乎不可见，等于没画）。
        if (ratio != null) {
            Spacer(Modifier.height(skin.spaceSmall))
            // 三种填充三个含义（2026-10 用户要求"别只用一种颜色"）：
            //   · **总进度（根节点）→ 金属金**：一页里同时有"总进度"和"各类型进度"，
            //     同形状的条分不清哪条是总计；
            //   · **修满 → 绿**：这块过没过，颜色直接给结论；
            //   · **其余 → 主色**。
            val metallic = type.depth == 0
            ProgressTrack(
                progress = ratio,
                // 总进度条更厚（7dp）：金属质感靠"高光 + 阴影 + 渐变"三层，5dp 太薄撑不住
                //（渲图对比过 —— 5dp 上那 1dp 高光几乎看不见，金属感出不来）；
                // 各类型保持 3/5dp，横杠多了也不至于变成一堵墙。
                height = if (metallic) 7.dp else if (type.depth >= 3) 3.dp else 5.dp,
                brush = if (metallic) {
                    Brush.horizontalGradient(listOf(MetallicGoldStart, MetallicGoldEnd))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            if (ratio >= 1f) colors.success else accentColor(),
                            if (ratio >= 1f) colors.success else accentColor(),
                        )
                    )
                },
                track = if (ratio <= 0f) colors.borderDivider else colors.surfaceHighlight,
                // 金属质感只给总进度：它要"像一条实心金属"，各类型仍是平面填充（一页里
                // 十几条立体阴影会变成噪音）。
                highlight = metallic,
            )
        }
        // 「还差」按**数值**判 0，不按字符串：解析层现在会保留 `0.0`
        //（那是学校给的结论），字符串比较 `!= "0"` 会漏掉它 ⇒ 修满的模块显示「还差 0.0 学分」。
        //
        // 呈现上做成**药丸**而不是又一行灰字：这是整页唯一需要用户"去做点什么"的信息
        //（哪块还差多少），压成弱化色等于把它藏起来。
        val missedCredit = type.missedCredit.toFloatOrNull() ?: 0f
        val missedGates = type.missedCount.toIntOrNull() ?: 0
        if (missedCredit > 0f || missedGates > 0) {
            Spacer(Modifier.height(skin.spaceXs))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                if (missedCredit > 0f) {
                    ShortfallPill("还差 ${type.missedCredit} 学分")
                }
                if (missedGates > 0) {
                    ShortfallPill("还差 $missedGates 门")
                }
            }
        }
        // 门次 / 课程门数这类**上下文**仍然是弱化小字：它们回答"为什么够/不够"，
        // 而不是"我该做什么"，与上面的药丸分工明确。
        // ⚠️ `includeShortfall = false`：「还差 N 门」已经在上面的药丸里了，
        // 这里再说一遍就是同一句话出现两次（渲图抓到的）。
        val countLine = type.countSummary(includeShortfall = false)
        if (countLine.isNotBlank()) {
            Spacer(Modifier.height(skin.spaceXs))
            Text(countLine, fontSize = 11.sp, color = colors.textTertiary)
        }
    }
}

/**
 * 进度条（自绘，替代 `LinearProgressIndicator`）。
 *
 * ## 为什么要自绘
 *
 * `LinearProgressIndicator` 只吃**单色**，而总进度要按用户要求做成
 * **`#FFD700 → #F9A825` 的线性渐变 + 白色高光边缘 + 轻微阴影**的金属质感
 *（"金属金"没法用一个 `Color` 表达）。渐变、高光、阴影三样它都不支持。
 *
 * ## ⚠️ 不能把语义丢了
 *
 * `LinearProgressIndicator` 顺带提供了 `ProgressBarRangeInfo` 语义（读屏会念"进度 56%"），
 * 自绘时必须自己补 [progressSemantics] —— 否则**读屏用户完全不知道这条是什么**，
 * 而这件事在任何截图与像素比对里都看不出来。守门人：
 * `AcademicQueryPrivacyTest.已获得学分为 0 的模块也要有进度条`
 *（它断言 `ProgressBarRangeInfo(0f, 0f..1f)`）。
 *
 * [highlight] 为真时画三样金属细节，全部**不透明**（用户明确要求"去掉任何降低透明度的属性"）：
 * ① 顶部 1dp 的实心近白高光带；② 底部 1dp 的暗端压边；③ 轻微 `shadow`。
 */
@Composable
private fun ProgressTrack(
    progress: Float,
    height: Dp,
    brush: Brush,
    track: Color,
    highlight: Boolean = false,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (highlight) Modifier.shadow(1.5.dp, shape) else Modifier)
            .clip(shape)
            .background(track)
            .progressSemantics(progress)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(brush)
        ) {
            if (highlight) {
                // 高光/压边用**实心色**而不是半透明白：半透明会被底下的渐变透上来，
                // 金属感反而糊掉（用户也明确要求不要透明度）。
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MetallicGoldHighlight)
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MetallicGoldEnd)
                    )
                }
            }
        }
    }
}

/**
 * 「还差 X」药丸：主色淡底 + 主色字。
 *
 * ⚠️ 底色用 `accentMain` 的低透明度而**不是** `danger`：学分没修满是**正常进度状态**，
 * 不是错误（本项目在 `ErrorCard` 那处专门记过"danger 低透明度在深色下几乎不可见"的坑，
 * 而这里用主色既看得见、又不会把正常的学业进度说成故障）。
 *
 * 注：这里的半透明底**不是**用户要求去掉的那类 —— 他指的是金属金条
 *（"去掉任何降低透明度的属性"紧跟在高光/阴影那句后面）。药丸的淡底是**文字的背景**，
 * 换成不透明色就意味着要为每套主题各配一个淡色底，反而更难维护；
 * 而金色元素本身（条、环、色条）现在都是**全不透明**的。
 */
@Composable
private fun ShortfallPill(text: String) {
    val skin = LocalSkin.current
    val accent = accentColor()
    Surface(color = accent.copy(alpha = 0.13f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = accent,
            modifier = Modifier.padding(horizontal = skin.spaceSm, vertical = 2.dp),
        )
    }
}

// ── 课程列表 ──

/**
 * 明细区里一个类型的表头：名字 + 已获学分 + 门数 + 展开/收起箭头。
 *
 * 整行是点击区（有课程时），并且**显式写死 44dp 最小高度** ——
 * `toggleable` 不会像 Material 组件那样自动补触摸尺寸，字小的时候手指点不准，
 * 而这件事在任何截图上都看不出来。
 */
@Composable
private fun TypeHeader(
    type: AcademicTypeInfo,
    courseCount: Int,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 与上面「课程类型学分」卡用**同一套层级样式**（同一页两处层级长得不一样会更难认）
    val style = hierarchyStyle(type.depth)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (type.depth * style.indentPerDepth.value).dp)
            .then(
                if (expandable) {
                    Modifier.toggleable(value = expanded, role = Role.Button, onValueChange = { onToggle() })
                } else {
                    Modifier
                }
            )
            .heightIn(min = 44.dp)
            .padding(top = skin.spaceSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起课程" else "展开课程",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(skin.spaceMicro))
        }
        if (style.barColor != null) {
            Box(
                modifier = Modifier
                    .width(style.barWidth)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(style.barColor)
            )
            Spacer(Modifier.width(skin.spaceSmall))
        }
        Text(
            type.name,
            fontSize = style.fontSize,
            fontWeight = style.weight,
            color = style.color ?: colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        val suffix = buildList {
            if (type.earnedCredit.isNotBlank()) add("已获 ${type.earnedCredit} 学分")
            if (courseCount > 0) add("$courseCount 门")
        }.joinToString(" · ")
        if (suffix.isNotBlank()) {
            Text(suffix, fontSize = 12.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun AcademicCourseCard(item: AcademicCourseItem) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 颜色按**修读状态**分档（纯函数在 `StudyStats.statusTone()`）：
    // 绿=已修、金=在修、红=未过、灰=未修 —— 用户要求"正在修课和未修课用不同颜色隔开"。
    // ⚠️ 旧实现按 `status.contains("在读")` 判，而解析层给的词是「在修」⇒ 在修的课与未修一样是灰的。
    val toneColor = when (item.statusTone()) {
        CourseStatusTone.PASSED -> colors.success
        // 「在修」用 info 蓝而不是总进度金：金是**图形专用**令牌
        //（深色卡上没有"够金又够读"的值，见 ThemeContrastTest 里那两条 3:1 探针的说明），
        // 而这一档要当**文字**给状态药丸用。
        CourseStatusTone.IN_PROGRESS -> colors.info
        CourseStatusTone.FAILED -> colors.danger
        CourseStatusTone.UNTAKEN -> colors.textTertiary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard)
    ) {
        // ⚠️ `height(IntrinsicSize.Min)` 不能省：左侧色条用 `fillMaxHeight()`，
        // 而它需要**确定的高度约束** —— 在 LazyColumn 里直接给的是无限高，色条会画成 0 高
        //（看着就是"根本没画"，渲图对比才发现的）。IntrinsicSize.Min 让 Row 先量出内容高度。
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 左侧 3dp 状态色条：一列卡片滚过去时，颜色比小字更快被认出来
            //（与培养方案树里"平台/模块"的层级色条同一手法，见 `hierarchyStyle`）。
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    // 不透明（用户要求"去掉任何降低透明度的属性"）：状态色本来就靠色相区分，
                    // 掺了透明度在深色主题下会灰掉，反而分不清。
                    .background(toneColor)
            )
            Column(modifier = Modifier.padding(skin.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.courseName.ifBlank { "未知课程" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (item.status.isNotBlank()) {
                    Surface(color = toneColor.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text(
                            item.status,
                            fontSize = 10.sp,
                            color = toneColor,
                            modifier = Modifier.padding(horizontal = skin.spaceSmall, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(skin.spaceSm))
                }
                val headline = item.maxGrade.ifBlank { item.score }
                if (headline.isNotBlank()) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            headline,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreBandOf(headline).color()
                        )
                        // 挂科补考时教务同一行给两列：「成绩 51 / 最大成绩 55」。
                        // 只显示高的那个，用户会以为系统算错了；两个都显示才说得通。
                        if (item.maxGrade.isNotBlank() && item.score.isNotBlank() &&
                            item.score != item.maxGrade
                        ) {
                            Text("成绩 ${item.score}", fontSize = 10.sp, color = colors.textTertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(skin.spaceSm))
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            Spacer(Modifier.height(skin.spaceSm))
            // ⚠️ 拆成**两行**、每行最多三件事：
            // 改造前是一条八九段的灰字长龙（学分 · 学时 · 绩点 · 必修 · 本科 · 已修 · 学期），
            // 折成两行以后读起来像"数值 / 归属 / 时间"三段，扫读成本低一半（渲图对比过）。
            val numbers = buildList {
                if (item.credit.isNotBlank()) add("学分 ${item.credit}")
                if (item.hours.isNotBlank()) add("学时 ${item.hours}")
                if (item.gradePoint.isNotBlank()) add("绩点 ${item.gradePoint}")
            }.joinToString("  ·  ")
            val facts = buildList {
                if (item.nature.isNotBlank()) add(item.nature)
                if (item.category.isNotBlank()) add(item.category)
                // ⚠️ 状态**不写进这一行**：它已经以药丸形式显示在右上角（"已修"/"未修"），
                // 同一张卡里说两遍就是噪音（`2026-10` 美化时渲图看出来的）。
                if (item.term.isNotBlank()) add(item.term)
            }.joinToString("  ·  ")
            if (numbers.isNotBlank()) {
                Text(numbers, fontSize = 12.sp, color = colors.textSecondary)
            }
            if (facts.isNotBlank()) {
                if (numbers.isNotBlank()) Spacer(Modifier.height(skin.spaceMicro))
                Text(facts, fontSize = 11.sp, color = colors.textTertiary)
            }
            if (numbers.isBlank() && facts.isBlank()) {
                Text("暂无详细信息", fontSize = 12.sp, color = colors.textTertiary)
            }
            // 补考 / 重修：只有教务真给了成绩才显示（这一行在挂科场景下才是关键信息）
            val retakeLine = buildList {
                if (item.makeupScore.isNotBlank()) add("补考 ${item.makeupScore}")
                if (item.retakeScore.isNotBlank()) add("重修 ${item.retakeScore}")
            }.joinToString("  ·  ")
            if (retakeLine.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceXs))
                Text(retakeLine, fontSize = 12.sp, color = colors.textSecondary)
            }
            // 建议修读学年学期：与实际修读**不同**时才说（未修课程就只有这一个信息）。
            // ⚠️ 这两个是教务页面上**两对不同的列**，混起来就是把"建议"当"实际"，看着完全正常。
            if (item.suggestedTerm.isNotBlank() && item.suggestedTerm != item.term) {
                Spacer(Modifier.height(skin.spaceXs))
                Text("建议修读 ${item.suggestedTerm}", fontSize = 11.sp, color = colors.textTertiary)
            }
            } // Column
        } // Row（左侧状态色条 + 内容列）
    }
}

