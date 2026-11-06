package com.pocket.xiaoli.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.desktop.data.Assets
import com.pocket.xiaoli.desktop.logic.Knowledge
import com.pocket.xiaoli.desktop.logic.SearchHit
import com.pocket.xiaoli.desktop.logic.SearchKind
import com.pocket.xiaoli.desktop.logic.SearchPage
import com.pocket.xiaoli.desktop.logic.searchAll
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.ui.components.DividerLine
import com.pocket.xiaoli.desktop.ui.components.IconBadge
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

private val searchPages = listOf(
    SearchPage(0, "首页", "今天 · 下一节课 · 待办", listOf("home", "今天", "待办")),
    SearchPage(1, "课表", "周课表 · 课程", listOf("timetable", "课程", "周表")),
    SearchPage(2, "鲤鱼", "AI 助手 · 对话", listOf("ai", "助手", "聊天", "鲤鱼")),
    SearchPage(3, "服务", "成绩 · 考试 · 校历 · 知识库", listOf("campus", "成绩", "考试", "校历", "知识库", "快问快答")),
    SearchPage(4, "我的", "设置 · 学期 · 节次 · 数据", listOf("settings", "设置", "学期", "节次", "导出")),
)

/**
 * 全局搜索面板（Ctrl+K）。
 *
 * 交互口径：输入即搜、↑↓ 选择、Enter 打开、Esc 关闭；点空白处也关闭。
 * 结果排序规则在 `:core` 的 `searchAll`（可单测），这里只负责渲染与键盘。
 */
@Composable
fun SearchPalette(state: AppState) {
    if (!state.showSearch) return
    val colors = pocketColors()
    val query = state.searchQuery
    val data = state.data
    val results = remember(query, data) {
        searchAll(
            query = query,
            pages = searchPages,
            courses = data.activeCourses,
            todos = data.todos,
            events = Assets.calendar?.events.orEmpty(),
            knowledgeSearch = Knowledge::search,
        )
    }
    var selected by remember { mutableStateOf(0) }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(results.size) { selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0)) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (results.isNotEmpty()) listState.animateScrollToItem(selected)
    }

    fun activate(hit: SearchHit) = state.activateSearch(hit)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                state.showSearch = false
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 76.dp)
                .width(680.dp)
                .clip(RoundedCornerShape(Radii.dialog))
                .background(colors.surfaceCard)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PocketTextField(
                    value = query,
                    onValueChange = { state.searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "搜课程、待办、知识库、校历，或输入页面名…",
                    leadingIcon = PocketIcons.Search,
                    focusRequester = focusRequester,
                    onPreviewKey = { event ->
                        if (event.type != KeyEventType.KeyDown) return@PocketTextField false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (results.isNotEmpty()) selected = (selected + 1).coerceAtMost(results.lastIndex)
                                true
                            }
                            Key.DirectionUp -> {
                                if (results.isNotEmpty()) selected = (selected - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                results.getOrNull(selected)?.let { activate(it) }
                                true
                            }
                            Key.Escape -> {
                                state.showSearch = false
                                true
                            }
                            else -> false
                        }
                    },
                )
                TagChip("Esc", color = colors.textTertiary, background = colors.surfaceAlt, onClick = { state.showSearch = false })
            }

            Spacer(Modifier.height(12.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            when {
                query.isBlank() -> {
                    Text(
                        "试试「高数」「实验报告」「图书馆」「国庆」或「设置」",
                        style = PocketTypography.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                    Column(Modifier.fillMaxWidth()) {
                        searchPages.forEach { page ->
                            SearchRow(
                                icon = pocketIconOf(page.index),
                                title = page.title,
                                subtitle = "页面 · ${page.hint}",
                                label = "页面",
                                selected = false,
                                onClick = {
                                    state.activateSearch(
                                        SearchHit(SearchKind.PAGE, page.title, "页面 · ${page.hint}", 0, page = page.index),
                                    )
                                },
                            )
                        }
                    }
                }

                results.isEmpty() -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("没找到「$query」", style = PocketTypography.body, color = colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("换个关键词，或问问鲤鱼", style = PocketTypography.caption, color = colors.textTertiary)
                    }
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(results) { index, hit ->
                            SearchRow(
                                icon = iconOf(hit.kind),
                                title = hit.title,
                                subtitle = hit.subtitle,
                                label = labelOf(hit.kind),
                                selected = index == selected,
                                onClick = { activate(hit) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isBlank()) "全局搜索" else "${results.size} 条结果",
                    style = PocketTypography.label,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                Text("上下键选择 · Enter 打开 · Esc 关闭", style = PocketTypography.label, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun SearchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = pocketColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentSoftColor() else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            icon = icon,
            tint = if (selected) accentColor() else colors.textSecondary,
            background = if (selected) Color.White.copy(alpha = 0.55f) else colors.surfaceAlt,
            size = 32.dp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = PocketTypography.body,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = PocketTypography.label,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = PocketTypography.label.copy(fontSize = 11.sp), color = colors.textTertiary)
    }
}

private fun iconOf(kind: SearchKind): ImageVector = when (kind) {
    SearchKind.COURSE -> PocketIcons.Timetable
    SearchKind.TODO -> PocketIcons.TodoList
    SearchKind.KNOWLEDGE -> PocketIcons.Book
    SearchKind.CALENDAR -> PocketIcons.CalendarDays
    SearchKind.PAGE -> PocketIcons.Services
}

private fun labelOf(kind: SearchKind): String = when (kind) {
    SearchKind.COURSE -> "课程"
    SearchKind.TODO -> "待办"
    SearchKind.KNOWLEDGE -> "知识库"
    SearchKind.CALENDAR -> "校历"
    SearchKind.PAGE -> "页面"
}

private fun pocketIconOf(page: Int): ImageVector = when (page) {
    0 -> PocketIcons.Home
    1 -> PocketIcons.Timetable
    2 -> PocketIcons.Chat
    3 -> PocketIcons.Services
    else -> PocketIcons.Profile
}
