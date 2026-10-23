package com.pocket.xiaoli.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.knowledge.LocalFaqKnowledgeSource
import com.pocket.xiaoli.data.service.CampusServiceRegistry
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.SectionCaption
import com.pocket.xiaoli.ui.components.uniqueKeys
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局搜索页。
 *
 * ## 2026-10-18：换成 MIUIX 观感（学岱宗 `home/GlobalSearchScreen.kt`）
 *
 * 改造前是 Material3 `TopAppBar` + `OutlinedTextField`（描边圆角胶囊）+ `FilterChip`
 * 快捷词 + 自绘卡片行 —— 四个零件来自四套不同的视觉语言，凑在一起就是"通用模板"味。
 *
 * 现在：
 * - 顶栏 `SmallTopAppBar`：52dp 紧凑栏，查询词非空时右侧出现「清空」（与设置页同一套壳）；
 * - 搜索框 `SearchBar` + `InputField`：MIUIX 的填充式圆角，进页自动聚焦；
 * - 快捷词、结果行都是 14~20dp 圆角 `surfaceAlt` 面（`RoundedCornerShape` + Material3
 *   `Surface`，不用 MIUIX `Surface` 是因为它走 squircle `RuntimeShader`，Robolectric 渲不了）；
 * - 结果按「功能 / 帮助」分组，行尾给一个右箭头——没有箭头的列表在这一页看起来像死文本。
 *
 * 配色全部经 `MiuixTheme.colorScheme`，也就是 `MiuixColorMapping.kt` 把我们 9 个令牌
 * 映进 MIUIX 的那一套；所以「外观 → 界面密度 / 深色浅色」在这一页依旧生效。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchScreen(
    campus: CampusInfo,
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val skin = LocalSkin.current
    var query by remember { mutableStateOf("") }
    var selectedBody by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val index = remember(campus) { buildIndex(campus) }
    val results = remember(index, query) {
        if (query.isBlank()) emptyList() else index.filter { match(it, query) }.take(30)
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focusRequester.requestFocus() }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            SmallTopAppBar(
                title = "搜索",
                navigationIcon = {
                    // 返回键用 Material3 的 IconButton：MIUIX 那个是 squircle 圆角，
                    // 走 `RuntimeShader`，截图基线渲不出来（见 memory/03-pitfalls.md §8）。
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 不再放顶栏「清空」：InputField 自己就带一个清空圆钮（见 160/161 基线），
                // 两个叉并排会让人不知道该按哪个。
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = skin.spaceLg),
        ) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        1.dp,
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                        RoundedCornerShape(26.dp),
                    )
            ) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = query,
                            onQueryChange = { query = it },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            label = "课表、成绩、空教室、服务、帮助",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                    insideMargin = DpSize(0.dp, 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                ) { }
            }

            if (query.isBlank()) {
                SearchHints(onPick = { query = it })
            } else if (results.isEmpty()) {
                NoResultHints(
                    query = query,
                    onAskAssistant = { onOpenRoute("assistant") },
                )
            } else {
                SearchResultList(
                    results = results,
                    onPick = { item ->
                        if (item.body != null) selectedBody = item.body else item.route?.let(onOpenRoute)
                    },
                )
            }
        }
    }

    selectedBody?.let { body ->
        AlertDialog(
            onDismissRequest = { selectedBody = null },
            title = { Text("帮助") },
            text = { Text(body, style = MiuixTheme.textStyles.body2) },
            confirmButton = { TextButton(onClick = { selectedBody = null }) { Text("知道了") } },
        )
    }
}

/** 查询词为空时的这一屏：常用词 + 一句"搜不到就问鲤鱼"。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHints(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        SectionCaption("常用")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("课表", "成绩", "空教室", "校园卡", "图书馆", "快递").forEach { keyword ->
                Chip(text = keyword, onClick = { onPick(keyword) })
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionCaption("没找到？")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "搜不到的东西可以直接问鲤鱼，它读的是本地校园知识库。",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 有查询词但一条都没命中：告诉用户还能往哪儿走，而不是丢一个空页面。 */
@Composable
private fun NoResultHints(
    query: String,
    onAskAssistant: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        SectionCaption("没有找到「$query」")
        Spacer(Modifier.height(10.dp))
        ResultRow(
            title = "问鲤鱼：$query",
            subtitle = "用这句话去问校园助手",
            onClick = onAskAssistant,
        )
    }
}

@Composable
private fun SearchResultList(
    results: List<GlobalSearchItem>,
    onPick: (GlobalSearchItem) -> Unit,
) {
    val features = results.filter { it.body == null }
    val faqs = results.filter { it.body != null }
    // 末尾让出导航条与键盘：最后一条结果能滚到它们上面，而不是被键盘压住
    val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime)
        .asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (features.isNotEmpty()) {
            item(key = "hdr-feature") { SectionCaption("功能与服务") }
            val keys = uniqueKeys(features) { it.title + it.subtitle }
            items(features, key = { item -> keys[features.indexOf(item)] ?: item.title }) { item ->
                ResultRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = { onPick(item) },
                )
            }
        }
        if (faqs.isNotEmpty()) {
            item(key = "hdr-faq") {
                Column(Modifier.padding(top = 10.dp)) { SectionCaption("帮助") }
            }
            val keys = uniqueKeys(faqs) { it.title + it.subtitle }
            items(faqs, key = { item -> keys[faqs.indexOf(item)] ?: item.title }) { item ->
                ResultRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    onClick = { onPick(item) },
                )
            }
        }
    }
}

/** 快捷词的图标：让六个胶囊不是六块一模一样的灰。 */
private fun keywordIcon(text: String): ImageVector? = when (text) {
    "课表" -> Icons.Outlined.CalendarMonth
    "成绩" -> Icons.Outlined.Grade
    "空教室" -> Icons.Outlined.MeetingRoom
    "校园卡" -> Icons.Outlined.CreditCard
    "图书馆" -> Icons.Outlined.MenuBook
    "快递" -> Icons.Outlined.LocalShipping
    else -> null
}

/** 快捷词胶囊：20dp 圆角 + 白底描边，与结果行同一套面。 */
@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MiuixTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MiuixTheme.colorScheme.outline),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "搜索 $text" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            keywordIcon(text)?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 一行结果：皮肤驱动的卡片（圆角 / 阴影跟「外观」走）+ 标题 / 说明 + 淡底圆箭头。
 *
 * 原来是 `surfaceVariant` 的平灰面 —— 和首页的白卡、弥散阴影不是一套语言。
 * 换成 [AppCard] 后，极简档自动无影、鲜活档自动弥散，同时拿到 MIUIX Sink 按压反馈；
 * 箭头收进 28dp 强调色淡底圆，和首页搜索框的放大镜、待办行尾按钮同一个动机。
 */
@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = title },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    // summary 压在卡底只有约 2.15:1，两行说明读起来发灰；次级文字色更实。
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    // 两行时说明常被切成「…和 C…」，三行能多给半句上下文。
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.accentMain.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.accentMain,
                )
            }
        }
    }
}

// ── 索引与匹配（这一块没动：搜索行为与改造前逐字节一致，只是外层观感换了） ──────────

private data class GlobalSearchItem(
    val title: String,
    val subtitle: String,
    val keywords: List<String>,
    val route: String? = null,
    val body: String? = null,
)

private fun buildIndex(campus: CampusInfo): List<GlobalSearchItem> {
    val features = listOf(
        GlobalSearchItem("课表", "周视图 · 课程 · 方案 · 导入", listOf("课表", "课程", "安排"), "timetable"),
        GlobalSearchItem("导入课表", "教务直连 / 网页 / 文件 / 分享码", listOf("导入", "教务", "课表"), "import_entry"),
        GlobalSearchItem("校园地图", "楼栋 · 楼层 · 教室定位", listOf("地图", "校园", "楼栋", "导航"), "campus_map"),
        GlobalSearchItem("教室分布图", "查看和录入教室", listOf("教室", "分布图", "房间"), "room_layout"),
        GlobalSearchItem("鲤鱼", "校园助手 · 本地知识库 · 只读工具", listOf("助手", "鲤鱼", "ai", "问答"), "assistant"),
        GlobalSearchItem("我的", "账号 · 外观 · 设置 · 数据", listOf("我的", "设置", "外观", "账号"), "profile"),
    )
    val services = CampusServiceRegistry.catalogFor(campus).map { spec ->
        GlobalSearchItem(
            title = spec.name,
            subtitle = (spec.provider ?: campus.shortName) + " · 服务入口",
            keywords = listOf(spec.id, spec.name, spec.provider.orEmpty()),
            route = "services",
        )
    }
    val faqs = LocalFaqKnowledgeSource.documents.map { doc ->
        GlobalSearchItem(
            title = doc.title,
            subtitle = doc.body.take(42) + if (doc.body.length > 42) "…" else "",
            keywords = doc.tags + doc.title,
            body = doc.body,
        )
    }
    return (features + services + faqs).distinctBy { it.title }
}

private fun match(item: GlobalSearchItem, query: String): Boolean {
    if (query.isBlank()) return false
    val q = query.trim().lowercase()
    return (listOf(item.title, item.subtitle) + item.keywords)
        .any { it.lowercase().contains(q) }
}
