package com.pocket.xiaoli.ui.courseselect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.network.CourseSelectItem
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.InlineError
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.campusui.CampusSearchBar
import com.pocket.xiaoli.ui.components.campusui.CampusSegmentedTabs
import com.pocket.xiaoli.ui.components.uniqueKeys
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.ui.zhengfang.ZhengfangCaptchaHost
import com.pocket.xiaoli.ui.zhengfang.ZhengfangLoginContent
import com.pocket.xiaoli.ui.zhengfang.ZhengfangSchoolList

/**
 * 选课（只读浏览）：**课程预选**与**自主选课**两个分区。
 *
 * ## 这一页做什么、不做什么
 * **做**：复用本机已绑定的教务账号登录 → 原生拉取两个功能页的课程列表 → 检索 / 筛选 /
 * 看余量与学分 → 需要提交时把用户送到教务网页。
 *
 * **不做**：代提交选课 —— 由用户在教务原页面上自己点提交。
 * 理由见 [CourseSelectViewModel] 的 KDoc（与仓库既有设计一致：不模拟选课提交接口）。
 *
 * ## 为什么两个功能页合成一页两分区
 * 教务那边是两个独立入口（`kcyx` · N253525 与 `xsxk` · N253512），但学生的动作是同一个：
 * "看看有什么课、还选不选得上"。分两页会让用户在两处登录态之间来回跳。
 * 两个分区共用同一套选校 / 登录 / 验证码组件 —— 那份逻辑在 `ZhengfangLoginViewModel` 里，只有一份。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseSelectScreen(
    onBack: () -> Unit,
    client: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
    onOpenWeb: (url: String, title: String) -> Unit,
) {
    val viewModel: CourseSelectViewModel = viewModel(
        factory = CourseSelectViewModel.Factory(client, schoolRegistry, appContainer)
    )
    val colors = LocalPocketXiaoliColors.current

    val selectedSchool by viewModel.selectedSchool.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()

    // ⚠️ 刻意**不**自动登录（与「成绩查询」「修课情况」同口径，2026-10 用户报过"点进去自动开始"）：
    // 绑定账号由基类 init 预填进表单，用户点一下再查。
    LaunchedEffect(Unit) { viewModel.prefillBoundCredentials() }

    // 提交动作在教务自己的页面上完成，所以这两个地址就是入口页本身
    val webUrl = remember(section, selectedSchool) {
        val base = selectedSchool?.baseUrl?.trimEnd('/').orEmpty()
        when (section) {
            CourseSelectSection.PRE_SELECT ->
                "$base/kcyx/kcyx_cxYxkcIndex.html?doType=details&gnmkdm=N253525&layout=default"
            CourseSelectSection.SELF_SELECT ->
                "$base/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default"
        }
    }

    Scaffold(
        topBar = {
            ServicePageTopBar(
                title = "选课",
                onBack = onBack,
                actions = {
                    if (loggedIn && webUrl.startsWith("http")) {
                        TextButton(onClick = { onOpenWeb(webUrl, "教务选课") }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("去选课", fontSize = 13.sp)
                        }
                    }
                }
            )
        },
        containerColor = colors.surfaceBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase),
        ) {
            when {
                selectedSchool == null -> ZhengfangSchoolList(state = viewModel)
                !loggedIn -> ZhengfangLoginContent(
                    state = viewModel,
                    hint = "登录正方教务系统（只查询课程列表，不会提交选课、不会导入课表）",
                    buttonText = "登录并查看可选课程",
                )
                else -> LoggedInContent(viewModel)
            }
        }
    }

    ZhengfangCaptchaHost(state = viewModel)
}

@Composable
private fun LoggedInContent(viewModel: CourseSelectViewModel) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    val section by viewModel.section.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val preItems by viewModel.preSelectItems.collectAsStateWithLifecycle()
    val selfItems by viewModel.selfSelectItems.collectAsStateWithLifecycle()
    val prePhase by viewModel.preSelectPhase.collectAsStateWithLifecycle()
    val selfPhase by viewModel.selfSelectPhase.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val loadProgress by viewModel.loadProgress.collectAsStateWithLifecycle()
    val possiblyTruncated by viewModel.possiblyTruncated.collectAsStateWithLifecycle()

    val isPreSelect = section == CourseSelectSection.PRE_SELECT
    val phase = if (isPreSelect) prePhase else selfPhase
    val source = if (isPreSelect) preItems else selfItems

    // 进入分区时按需拉取（每个分区只拉一次，来回切换不会重复请求）
    LaunchedEffect(section) { viewModel.ensureLoaded(section) }

    var onlyChosen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(skin.spaceMd))

        CampusSegmentedTabs(
            options = listOf(
                "课程预选" to CourseSelectSection.PRE_SELECT,
                "自主选课" to CourseSelectSection.SELF_SELECT,
            ),
            selected = section,
            onSelect = { viewModel.selectSection(it) },
            modifier = Modifier.padding(horizontal = skin.spaceLg),
            scrollable = false,
        )

        Spacer(Modifier.height(skin.spaceSm))
        CampusSearchBar(
            value = query,
            onValueChange = { viewModel.updateQuery(it) },
            placeholder = "搜索课程名 / 课程代码 / 教师",
            modifier = Modifier.padding(horizontal = skin.spaceLg),
        )
        Spacer(Modifier.height(skin.spaceSm))

        // 「只看已选」只在预选分区出现：自主选课页没有这个概念
        val chosenCount = if (isPreSelect) source.count { it.chosen } else 0
        if (isPreSelect && chosenCount > 0) {
            FilterChipText(
                text = if (onlyChosen) {
                    "只看已选（$chosenCount）"
                } else {
                    "全部 ${source.size} 门 · 已选 $chosenCount 门"
                },
                active = onlyChosen,
                onClick = { onlyChosen = !onlyChosen },
                modifier = Modifier.padding(horizontal = skin.spaceLg),
            )
            Spacer(Modifier.height(skin.spaceSm))
        }

        val filtered = remember(source, query, onlyChosen, isPreSelect) {
            val base = if (onlyChosen && isPreSelect) source.filter { it.chosen } else source
            CourseListFilter.apply(base, query)
        }
        // ⚠️ key 必须在进入 `itemsIndexed` 之前算好：`key` 回调不是 @Composable，
        // 那里既不能 remember 也不该做重活。撞车保护交给 uniqueKeys。
        val keys = remember(filtered) { uniqueKeys(filtered) { it.identityKey() } }

        // ⚠️ 2026-10-22：统计行、加载/失败/空态与课程卡现在同处这一条滚动。
        // 以前它们在外层 Column、列表是 `fillMaxSize()` 的内嵌 LazyColumn ——
        // 往下滑时顶部那行「共 N 门 · 合计 X 学分」一直挂着，第一张课程卡
        // 从它下沿"滑出来"被切掉一截。用户报的「卡片悬浮遮挡内容」即此。
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = skin.spaceLg,
                end = skin.spaceLg,
                bottom = skin.spaceHuge,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            if (filtered.isNotEmpty()) {
                item(key = "summary") {
                    CourseListHero(
                        courses = filtered,
                        chosenCount = chosenCount,
                        isPreSelect = isPreSelect,
                    )
                }
            }

            when {
                phase == SelectListPhase.LOADING -> item(key = "loading") { LoadingRow(loadProgress) }

                phase == SelectListPhase.FAILED && error != null -> item(key = "error") {
                    InlineError(message = error!!, onRetry = { viewModel.retryCurrent() })
                }

                phase == SelectListPhase.LOADED && filtered.isEmpty() -> item(key = "empty") {
                    ServiceEmptyCard(
                        icon = Icons.Outlined.Inbox,
                        title = if (query.isNotBlank()) "没有匹配的课程" else "这里还没有课程",
                        message = when {
                            query.isNotBlank() -> "换个关键词试试，或清空搜索框看全部。"
                            !isPreSelect -> "当前学期可能还没开放自主选课，或本模块暂未开课。"
                            else -> "当前学期可能还没开放预选。"
                        },
                        accent = ServiceAccent.ACADEMIC,
                    )
                }

                else -> {
                    if (possiblyTruncated) {
                        item(key = "truncated_hint") {
                            Text(
                                "只显示了前 ${filtered.size} 门（教务端总数更多）。" +
                                    "完整名单请点右上角「去选课」在教务页查看。",
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    itemsIndexed(filtered, key = { index, _ -> keys[index] ?: "#$index" }) { _, item ->
                        CourseSelectCard(item = item)
                    }
                }
            }
        }
    }
}

/**
 * 列表顶部的主视觉卡：门数 + 学分 + 已选。
 *
 * 改造前这里只有一行 12sp 灰字「共 N 门 · 合计 X 学分」—— 统计信息是全页权重最低的，
 * 而它恰恰是选课时最先要看的两个数（还要选几门、学分够不够）。
 * 现在与服务页其余二级页统一成 hero：语义色深底 + 白字，数目给到 42sp。
 * 数字跟着**筛选结果**走（搜索/只看已选后立刻变），与下面列表永远一致。
 *
 * `internal`：整页 `LoggedInContent` 依赖 ViewModel，在 Robolectric 里起不来，
 * 而这张卡是选课页唯一的"统计口径"窗口 —— 抽出来供快照测试直接渲染。
 */
@Composable
internal fun CourseListHero(
    courses: List<CourseSelectItem>,
    chosenCount: Int,
    isPreSelect: Boolean,
) {
    val skin = LocalSkin.current
    val credits = remember(courses) {
        CourseListFilter.formatCredits(CourseListFilter.totalCredits(courses))
    }
    ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "可选课程",
                    fontSize = 12.sp,
                    // 0.9 而不是 0.7：hero 底压到亮度 ≤ 0.16，70% 白字只有约 3:1。
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(skin.spaceXs))
                ServiceHeroCount(
                    number = courses.size.toString(),
                    unit = "门",
                    numberSize = 42.sp,
                )
            }
            ServiceHeroBadge(Icons.Filled.School)
        }
        Spacer(Modifier.height(skin.spaceLg))
        Row {
            ServiceHeroChip(if (credits.isNotBlank()) "合计 $credits 学分" else "学分 —")
            if (isPreSelect) {
                Spacer(Modifier.width(skin.spaceSm))
                ServiceHeroChip("已选 $chosenCount 门")
            }
        }
    }
}

/** 列表项身份键：行 ID 唯一且稳定；缺失时退到"课程代码+课名" */
private fun CourseSelectItem.identityKey(): String =
    kcyxbId.ifBlank { "$kch|$kcmc" }

@Composable
private fun LoadingRow(progress: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(progress.ifBlank { "加载中……" }, fontSize = 13.sp, color = colors.textSecondary)
        }
    }
}

/**
 * 一枚可点的筛选胶囊。
 *
 * ⚠️ `heightIn(min = 44.dp)`：胶囊天然矮（约 30dp），低于本项目 44dp 的无障碍底线，
 * 而**这件事在截图上完全看不出来**（`tools/lint_design.py` 的 touch 检查也是这么判的）。
 *
 * `internal`：与 [CourseSelectCard] 一同供 `CourseSelectScreenshotTest` 直接渲染。
 */
@Composable
internal fun FilterChipText(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            // 胶囊形（50%）而不是 skin.radiusChip：它与上方分段选择、下方课程卡是**三种**形状，
            // 圆角一致会让人误以为它们属于同一组控件。
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.accentMain else colors.surfaceCard)
            .then(
                if (active) Modifier
                else Modifier.border(1.dp, colors.borderDivider, RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceCompact),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) onAccentColor() else colors.textSecondary,
        )
    }
}

/**
 * 一门课的卡片。
 *
 * 信息取舍（字段来自教务两个页面的真实列）：
 * - **课程名 + 已选/停开徽标**放第一行 —— 扫一眼要能回答"这门课是不是我的了"；
 * - 课程代码 / 学分 / 学时一行，教师 / 类别 / 时间地点一行，全部弱化；
 * - **余量**单独一行带进度条：这是选课决策里唯一"会变"的信息。
 *   ⚠️ 拿不到人数时**不画进度条**，而不是画 0/0 —— 假条会让用户以为"这门课没人选"。
 * - 学分与学时**照抄教务的原字符串**，不做数值格式化（`4` 不该变成 `4.0`）。
 *
 * `internal`：这一页的三种课程状态（已选 / 会满 / 已停开）只有并排渲出来才能确认
 * 颜色与层级真的分得开 —— 整页依赖 ViewModel，所以卡本身对测试开放。
 */
@Composable
internal fun CourseSelectCard(item: CourseSelectItem) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val capacity = remember(item) { CourseListFilter.capacity(item) }
    // 左侧状态条：已选 = 主色、已停开 = 红（停开优先，它是更紧急的结论）。
    // 一列卡片滚过去时，颜色比右下角的小徽标更快被认出来 —— 与「修课情况」的课程卡同一手法。
    val marker = when {
        item.sftkzt == "1" -> colors.danger
        item.chosen -> colors.accentMain
        else -> null
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (marker != null) {
                Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(marker))
            }
            Column(
                modifier = Modifier.padding(horizontal = skin.spaceMd, vertical = skin.spaceMd),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.kcmc,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    if (item.chosen) Badge(text = "已选", tint = colors.accentMain)
                    if (item.sftkzt == "1") {
                        if (item.chosen) Spacer(Modifier.width(4.dp))
                        Badge(text = "已停开", tint = colors.danger)
                    }
                }

                Spacer(Modifier.height(skin.spaceXs))

                Text(
                    text = buildString {
                        if (item.kch.isNotBlank()) append("${item.kch}  ")
                        if (item.xf.isNotBlank()) append("${item.xf} 学分  ")
                        if (item.zxs.isNotBlank()) append("${item.zxs} 学时")
                        if (item.xsxxxx.isNotBlank()) append("（周 ${item.xsxxxx}）")
                    }.trim(),
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )

                val teacherLine = buildString {
                    if (item.jsxm.isNotBlank()) {
                        append(item.jsxm)
                        if (item.zcmc.isNotBlank()) append(" · ${item.zcmc}")
                    }
                }
                val extraLine = listOf(
                    teacherLine,
                    listOf(item.kclbmc, item.kcxzmc).filter { it.isNotBlank() }.joinToString(" · "),
                    item.sksj,
                    item.jxdd,
                ).filter { it.isNotBlank() }.joinToString("  ")
                if (extraLine.isNotBlank()) {
                    Spacer(Modifier.height(skin.spaceTiny))
                    Text(text = extraLine, fontSize = 11.sp, color = colors.textTertiary)
                }

                if (capacity != null) {
                    Spacer(Modifier.height(skin.spaceSm))
                    CapacityBar(capacity = capacity, sxrsmc = item.sxrsmc)
                }
            }
        }
    }
}

/** 余量：文字 + 进度条。满员用 danger，紧张用 warning，宽松用主色 */
@Composable
private fun CapacityBar(capacity: CourseListFilter.Capacity, sxrsmc: String) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = when {
        capacity.isFull -> colors.danger
        capacity.ratio >= 0.85f -> colors.warning
        else -> colors.accentMain
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (capacity.isFull) "已满" else "余 ${capacity.remaining}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                // 优先显示教务自己排版的文本（形如 "120/60"），那才是学生在那两个页面上看到的口径
                text = sxrsmc.ifBlank { "${capacity.used}/${capacity.limit}" },
                fontSize = 11.sp,
                color = colors.textTertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                // 轨道用 surfaceHighlight 而不是 surfaceAlt：卡片本身就是 surfaceCard，
                // surfaceAlt 压上去几乎看不见，等于"满员时只剩一条红"。
                .background(colors.surfaceHighlight)
                // 余量是这条卡上唯一"会变"的信息，读屏也该能读出来比例。
                .progressSemantics(capacity.ratio.coerceIn(0f, 1f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(capacity.ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(tint),
            )
        }
    }
}

/** 小徽标（已选 / 已停开） */
@Composable
private fun Badge(text: String, tint: Color) {
    val skin = LocalSkin.current
    val isDark = LocalDarkMode.current
    Box(
        modifier = Modifier
            // 语义色 14% 底 + 同色字（深色主题 26%）：比灰底 + 彩字更"成组"，
            // 也更容易在卡片上定位 —— 灰底在深色主题里还会和卡色糊在一起。
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (isDark) 0.26f else 0.14f))
            .padding(horizontal = skin.spaceSm, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}
