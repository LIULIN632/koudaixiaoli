package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.data.CalendarEvent
import com.pocket.xiaoli.desktop.data.CampusConfig
import com.pocket.xiaoli.desktop.data.CampusServiceKeys
import com.pocket.xiaoli.desktop.data.KnowledgeDoc
import com.pocket.xiaoli.desktop.data.Store
import com.pocket.xiaoli.desktop.logic.Knowledge
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.SectionHeader
import com.pocket.xiaoli.desktop.ui.components.SettingRow
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.MarkdownText
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.ProgressBar
import com.pocket.xiaoli.desktop.ui.components.ScreenTitle
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.Segmented
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.core.calendar.TermWeeksCore
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.util.Platform
import java.time.LocalDate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import com.pocket.xiaoli.core.calendar.monthGrid
import com.pocket.xiaoli.desktop.ui.theme.onAccentColor
import com.pocket.xiaoli.core.calendar.eventCoversDay
import com.pocket.xiaoli.desktop.logic.courseEndMinute
import com.pocket.xiaoli.desktop.logic.courseStartMinute
import com.pocket.xiaoli.desktop.logic.coursesOnDay
import com.pocket.xiaoli.desktop.logic.minuteText
import com.pocket.xiaoli.desktop.logic.termWeekOf

/**
 * 校园页：服务入口 / 成绩 / 考试 / 校历 / 知识库 / 快问快答。
 *
 * 这几块在移动端是各自独立的入口，桌面端合到一页用分段切换 —— 数据源完全一样
 * （成绩走教务会话、校历与知识库读 `assets/`、考试走通知解析），只是换了个更好的排版。
 */
@Composable
fun CampusScreen(state: AppState) {
    val colors = pocketColors()
    val section = state.campusSection
    val calendar = Assets.calendar
    val campus = CampusConfig.byId(state.data.settings.campusId)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(
            title = "服务",
            // 侧栏已经写了学校；这里只说"当前是哪个校区"，因为服务链接按校区分
            subtitle = campus.name.ifBlank { "烟台理工学院" },
            actions = {
                SecondaryButton("问鲤鱼", icon = PocketIcons.Chat) { state.tab = 2 }
            },
        )
        Segmented(
            options = listOf("服务入口", "成绩", "考试", "校历", "知识库", "快问快答", "笔记"),
            selectedIndex = section,
            onSelect = { state.campusSection = it },
        )
        when (section) {
            0 -> ServicesSection(state, Modifier.weight(1f))
            1 -> GradesSection(state, Modifier.weight(1f))
            2 -> ExamsSection(state, Modifier.weight(1f))
            3 -> CalendarSection(state, Modifier.weight(1f))
            4 -> KnowledgeSection(state, Modifier.weight(1f))
            6 -> NotesSection(state, Modifier.weight(1f))
            else -> QaSection(state, Modifier.weight(1f))
        }
    }
}

// ═══════════════════════ 服务入口 ═══════════════════════

/** 一个服务入口：标题 + 副标题 + 网址 + 图标。 */
private data class ServiceLink(val title: String, val subtitle: String, val url: String, val icon: ImageVector)

/**
 * 服务入口。
 *
 * 移动端这些入口大多有原生页（教务抓取、缴费、洗衣 App 拉起）；桌面端**不抓教务**，
 * 统一改成「点开浏览器」，所以副标题里写清楚跳去哪儿 —— 免得点开发现要登录就以为坏了。
 */
@Composable
private fun ServicesSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val campus = CampusConfig.byId(state.data.settings.campusId)
    val urls = campus.serviceUrls

    fun group(name: String, vararg rows: Triple<String, String, String>): Pair<String, List<ServiceLink>> =
        name to rows.mapNotNull { (title, subtitle, key) ->
            val url = urls[key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ServiceLink(title, subtitle, url, PocketIcons.LinkExternal)
        }

    val groups = listOf(
        group(
            "学习",
            Triple("教务系统", "正方教务 · 成绩 / 课表 / 学籍", CampusServiceKeys.CLASSROOM),
            Triple("选课中心", "自主选课（预选在移动端）", CampusServiceKeys.COURSE_SELECT),
            Triple("考试安排", "考试时间与考场", CampusServiceKeys.EXAM),
            Triple("教学评价", "期末评教", CampusServiceKeys.EVALUATION),
            Triple("图书馆", "馆藏检索 / 借阅 / 续借", CampusServiceKeys.LIBRARY),
            Triple("第二课堂", "到梦空间（以 App 为主）", CampusServiceKeys.SECOND_CLASS),
        ),
        group(
            "生活",
            Triple("智慧校园", "统一认证 · 一网通办", CampusServiceKeys.SMART_CAMPUS),
            Triple("一卡通", "余额 / 消费流水", CampusServiceKeys.CAMPUS_CARD),
            Triple("宿舍电费", "缴费（智慧校园内）", CampusServiceKeys.ELECTRICITY),
            Triple("校园网", "网费 / 认证", CampusServiceKeys.NETWORK),
            Triple("洗澡", "易校园 / 趣智校园", CampusServiceKeys.SHOWER),
            Triple("热水", "热水供应", CampusServiceKeys.HOT_WATER),
            Triple("快递", "驿站查询", CampusServiceKeys.EXPRESS),
            Triple("报修", "后勤报修", CampusServiceKeys.REPAIR),
        ),
        "其他" to listOfNotNull(
            campus.portalUrl?.let { ServiceLink("学校官网", "通知公告、招标采购", it, PocketIcons.Book) },
            urls[CampusServiceKeys.NOTICE]?.let { ServiceLink("通知公告", "www.yitsd.edu.cn", it, PocketIcons.Bell) },
            urls[CampusServiceKeys.JOB]?.let { ServiceLink("校园招聘", "就业信息网", it, PocketIcons.GraduationCap) },
        ),
    )

    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (campus.underConstruction) {
            PocketCard(Modifier.fillMaxWidth()) {
                Text(
                    "${campus.name}还在建设，暂无服务入口。",
                    style = PocketTypography.body,
                    color = colors.textSecondary,
                )
            }
        }
        groups.forEach { (title, links) ->
            if (links.isEmpty()) return@forEach
            // 原来给「学习」硬写了一句"教务 / 图书 / 第二课堂"，正是下面每一行的名字，删掉
            SectionHeader(title)
            PocketCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                links.forEachIndexed { index, link ->
                    SettingRow(
                        title = link.title,
                        subtitle = link.subtitle,
                        icon = link.icon,
                        iconTint = accentColor(),
                        trailing = {
                            androidx.compose.material3.Icon(
                                PocketIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            if (Platform.openUrl(link.url)) {
                                state.notify("已在浏览器打开：${link.title}")
                            } else {
                                state.notify("打不开：${link.url}")
                            }
                        },
                    )
                    if (index != links.lastIndex) DividerLine(Modifier.padding(horizontal = 10.dp))
                }
            }
        }
        Text(
            "这些入口都需要登录态：桌面版只负责把你送到网页，账号密码在校方系统里输入，本机不保存。",
            style = PocketTypography.label,
            color = colors.textTertiary,
        )
    }
}

// ═══════════════════════ 知识库 ═══════════════════════

@Composable
private fun KnowledgeSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val query = state.knowledgeQuery
    var category by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    // 全局搜索跳过来时展开指定文档；消费完清掉，避免下次进来自动展开
    LaunchedEffect(state.knowledgeFocusId) {
        state.knowledgeFocusId?.let { id ->
            expanded = id
            state.knowledgeFocusId = null
        }
    }

    val all = Knowledge.all
    val categories = Knowledge.categories
    val hits = remember(query) {
        if (query.isBlank()) emptyList() else Knowledge.search(query, topK = 20).distinctBy { it.doc.id }
    }
    val docs: List<KnowledgeDoc> = when {
        query.isNotBlank() -> hits.map { it.doc }
        category.isNotBlank() -> all.filter { it.category == category }
        else -> all
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PocketTextField(
                value = query,
                onValueChange = { state.knowledgeQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = "搜知识库：食堂 / 图书馆 / 宿舍 / 校历…",
                leadingIcon = PocketIcons.Search,
            )
            if (query.isNotBlank() || category.isNotBlank()) {
                SecondaryButton("清除") {
                    state.knowledgeQuery = ""
                    category = ""
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TagChip("全部", color = if (category.isEmpty()) Color.White else colors.textSecondary, background = if (category.isEmpty()) accentColor() else colors.surfaceAlt, onClick = { category = "" })
            categories.take(8).forEach { cat ->
                TagChip(
                    cat,
                    color = if (category == cat) Color.White else colors.textSecondary,
                    background = if (category == cat) accentColor() else colors.surfaceAlt,
                    onClick = { category = if (category == cat) "" else cat },
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${docs.size} 条", style = PocketTypography.label, color = colors.textTertiary)
        }

        if (docs.isEmpty()) {
            PocketCard(Modifier.fillMaxWidth()) {
                EmptyState(PocketIcons.Book, "没搜到", "换个关键词，或者直接问鲤鱼")
            }
            return@Column
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            docs.forEach { doc ->
                val isOpen = expanded == doc.id
                PocketCard(Modifier.fillMaxWidth(), onClick = { expanded = if (isOpen) null else doc.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(doc.title, style = PocketTypography.cardTitle, color = colors.textPrimary)
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (doc.category.isNotBlank()) TagChip(doc.category, color = accentColor(), background = accentSoftColor())
                                if (doc.isExpired()) TagChip("可能已过期", color = colors.warning, background = colors.warning.copy(alpha = 0.12f))
                                if (doc.tags.isNotEmpty()) {
                                    Text(doc.tags.take(3).joinToString(" · "), style = PocketTypography.label, color = colors.textTertiary)
                                }
                            }
                        }
                        androidx.compose.material3.Icon(
                            if (isOpen) PocketIcons.ChevronDown else PocketIcons.ChevronRight,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (isOpen) {
                        Spacer(Modifier.height(10.dp))
                        MarkdownText(doc.body, selectable = true)
                        if (doc.sourceLabel.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text("来源：${doc.sourceLabel}" + if (doc.effectiveTo.isNotBlank()) " · 有效至 ${doc.effectiveTo}" else "", style = PocketTypography.label, color = colors.textTertiary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("复制", icon = PocketIcons.Copy) {
                                Platform.copyToClipboard("${doc.title}\n\n${doc.body}")
                                copied = true
                            }
                            SecondaryButton("问鲤鱼", icon = PocketIcons.Chat) {
                                state.prefill("关于「${doc.title}」：${doc.body.take(220)}")
                            }
                        }
                        if (copied) {
                            Spacer(Modifier.height(4.dp))
                            Text("已复制到剪贴板", style = PocketTypography.label, color = colors.success)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════ 快问快答 ═══════════════════════

@Composable
private fun QaSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val courses = Assets.qaBank
    if (courses.isEmpty()) {
        PocketCard(modifier.fillMaxWidth()) {
            EmptyState(PocketIcons.Book, "没有快问快答数据", "assets/quickanswer/bank.json 缺失")
        }
        return
    }
    var courseIndex by remember { mutableStateOf(0) }
    var openQuestion by remember { mutableStateOf<String?>(null) }
    val course = courses.getOrNull(courseIndex) ?: courses.first()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            courses.forEachIndexed { index, item ->
                TagChip(
                    item.name.ifBlank { item.subject },
                    color = if (index == courseIndex) Color.White else colors.textSecondary,
                    background = if (index == courseIndex) accentColor() else colors.surfaceAlt,
                    onClick = {
                        courseIndex = index
                        openQuestion = null
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Text("${course.chapters.sumOf { it.questions.size }} 题", style = PocketTypography.label, color = colors.textTertiary)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            course.chapters.sortedBy { it.index }.forEach { chapter ->
                PocketCard(Modifier.fillMaxWidth()) {
                    Text(
                        (if (chapter.index > 0) "第 ${chapter.index} 章 · " else "") + chapter.title,
                        style = PocketTypography.cardTitle,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    chapter.questions.forEach { question ->
                        val open = openQuestion == question.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radii.chip))
                                .clickable { openQuestion = if (open) null else question.id }
                                .padding(vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (open) accentColor() else colors.borderDivider))
                                Spacer(Modifier.width(8.dp))
                                Text(question.question, style = PocketTypography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                                androidx.compose.material3.Icon(
                                    if (open) PocketIcons.ChevronDown else PocketIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            if (open) {
                                Spacer(Modifier.height(6.dp))
                                question.answerPoints.forEach { point ->
                                    Row(Modifier.padding(start = 14.dp, top = 2.dp)) {
                                        Text("·", style = PocketTypography.body, color = accentColor())
                                        Spacer(Modifier.width(6.dp))
                                        Text(point, style = PocketTypography.body, color = colors.textSecondary)
                                    }
                                }
                                if (question.hint.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "提示：${question.hint}",
                                        style = PocketTypography.caption,
                                        color = colors.textTertiary,
                                        modifier = Modifier.padding(start = 14.dp),
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                SecondaryButton("问鲤鱼", icon = PocketIcons.Chat) {
                                    state.prefill("${course.name} · ${question.question}")
                                }
                            }
                        }
                        DividerLine()
                    }
                }
            }
        }
    }
}

