package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.data.ChatMessage
import com.pocket.xiaoli.desktop.data.ChatSession
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.MarkdownText
import com.pocket.xiaoli.desktop.ui.components.rememberPressState
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.LocalDarkMode
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.onAccentFor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format

/**
 * 鲤鱼（AI 助手）页：会话列表 + 流式对话 + 工具轨迹。
 *
 * ⚠️ 与移动端一致的判据：**未配置 Key 时不能假装能答** —— 直接弹配置框，且输入区提示要配。
 */
@Composable
fun AssistantScreen(state: AppState) {
    val colors = pocketColors()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ConversationList(state, Modifier.width(224.dp))
        ChatArea(state, Modifier.weight(1f))
    }
}

@Composable
private fun ConversationList(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    PocketCard(modifier.fillMaxHeight(), padding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("对话", style = PocketTypography.cardTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            GhostIconButton(PocketIcons.Plus, contentDescription = "新对话", size = 28.dp) { state.newChat() }
        }
        Spacer(Modifier.height(6.dp))
        val chats = state.chats
        if (chats.isEmpty()) {
            // 原来只有一行小字挂在两米高的空白里；给一个真正的入口
            EmptyState(
                icon = PocketIcons.Chat,
                title = "还没有对话",
                action = { PrimaryButton("新建对话", icon = PocketIcons.Plus, fillWidth = true) { state.newChat() } },
            )
        }
        Column(Modifier.fillMaxWidth()) {
            chats.forEach { chat ->
                val selected = chat.id == state.activeChatId
                val rowPress = rememberPressState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .scale(rowPress.scale)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(
                            when {
                                selected -> accentSoftColor()
                                rowPress.hovered -> colors.surfaceAlt
                                else -> Color.Transparent
                            },
                        )
                        .border(
                            1.dp,
                            if (selected) accentColor().copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(Radii.chip),
                        )
                        .hoverable(rowPress.source)
                        .clickable(interactionSource = rowPress.source, indication = null) { state.selectChat(chat.id) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            chat.title.ifBlank { "新对话" },
                            style = PocketTypography.caption.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                            color = if (selected) colors.textPrimary else colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (chat.messages.isEmpty()) "空对话" else "${chat.messages.size} 条 · ${Format.conversationDay(chat.createdAt)}",
                            style = PocketTypography.label,
                            color = colors.textTertiary,
                            maxLines = 1,
                        )
                    }
                    GhostIconButton(PocketIcons.Close, contentDescription = "删除对话", size = 22.dp) { state.deleteChat(chat.id) }
                }
            }
        }
    }
}

@Composable
private fun ChatArea(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val chat = state.activeChat
    val messages = chat?.messages.orEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length, state.streaming) {
        if (messages.isNotEmpty()) {
            runCatching { listState.scrollToItem(messages.size - 1) }
        }
    }

    PocketCard(modifier.fillMaxHeight(), padding = 0.dp) {
        // 标题栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(accentSoftColor()),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(PocketIcons.Fish, contentDescription = null, tint = accentColor(), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(chat?.title?.ifBlank { "鲤鱼" } ?: "鲤鱼", style = PocketTypography.cardTitle, color = colors.textPrimary, maxLines = 1)
                // 没配 Key 时下面已经有一条橙色横幅带「去配置」，标题别再重复一句同样的话
                if (state.llmConfigured) {
                    Text(
                        "${state.llm.model} · 本地课表/待办可查",
                        style = PocketTypography.label,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.streaming) {
                SecondaryButton("停止") { state.stopStreaming() }
                Spacer(Modifier.width(8.dp))
            } else if (messages.any { it.role == "user" }) {
                SecondaryButton("重试", icon = PocketIcons.Refresh) { state.retryLast() }
                Spacer(Modifier.width(8.dp))
            }
            GhostIconButton(PocketIcons.Settings, contentDescription = "AI 设置") { state.showApiDialog = true }
        }
        Box(Modifier.height(1.dp).fillMaxWidth().background(colors.borderDivider))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                SuggestionPanel(state)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message, streaming = state.streaming && message == messages.last())
                    }
                }
            }
        }

        // 输入区
        Box(Modifier.height(1.dp).fillMaxWidth().background(colors.borderDivider))
        Column(Modifier.padding(12.dp)) {
            if (!state.llmConfigured) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(colors.warning.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "配置一个兼容 OpenAI 的 API（推荐 DeepSeek）后，鲤鱼就能回答",
                        style = PocketTypography.caption,
                        color = colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton("去配置") { state.showApiDialog = true }
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PocketTextField(
                    value = state.draft,
                    onValueChange = { state.draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "问鲤鱼点什么…（Enter 发送，Shift+Enter 换行）",
                    singleLine = false,
                    minHeight = 58.dp,
                    textStyle = PocketTypography.body,
                    onPreviewKey = { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                            if (!state.streaming) state.send()
                            true
                        } else {
                            false
                        }
                    },
                )
                PrimaryButton(
                    text = if (state.streaming) "生成中" else "发送",
                    icon = PocketIcons.Send,
                    enabled = !state.streaming && state.draft.isNotBlank(),
                ) { state.send() }
            }
        }
    }
}

@Composable
private fun SuggestionPanel(state: AppState) {
    val colors = pocketColors()
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        com.pocket.xiaoli.desktop.ui.components.PocketMascot(Modifier.size(120.dp))
        Spacer(Modifier.height(10.dp))
        Text("我是鲤鱼，你的校园助手", style = PocketTypography.sectionTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            // 没配 Key 时代码里其实还有本机知识库这条路，别再重复"去配置"（下面横幅已经在说了），
            // 讲一句只有这里才说的信息。
            if (state.llmConfigured) "课表、待办、校历、校园知识库都能查；也可以直接聊天"
            else "没配 Key 也能问校园常识 —— 这类问题我直接查本机知识库",
            style = PocketTypography.caption,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(20.dp))
        val suggestions = listOf(
            "今天有什么课",
            "下一节课在哪",
            "帮我看看还有哪些待办",
            "最近校历有什么安排",
            "食堂几点开门",
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            suggestions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { text ->
                        TagChip(text = text, onClick = { state.draft = text })
                    }
                }
            }
        }
        // 「AI 会看到什么」只有在真的接了 AI 时才是问题；没配 Key 时这句挂在空处反而像废话
        if (state.llmConfigured) {
            Spacer(Modifier.height(18.dp))
            Text(
                "提示：这些数据都在本机，AI 只在你点发送时才看到相关内容",
                style = PocketTypography.label,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, streaming: Boolean) {
    val colors = pocketColors()
    val dark = LocalDarkMode.current
    val isUser = message.role == "user"
    var showReasoning by remember(message.id) { mutableStateOf(false) }

    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(accentColor())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(message.text, style = PocketTypography.body, color = onAccentFor(accentColor()))
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        // 工具轨迹
        if (message.tools.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                message.tools.forEach { trace ->
                    TagChip(
                        text = (trace.label.ifBlank { trace.name }) + if (trace.done) "" else " …",
                        color = if (trace.done) colors.textSecondary else accentColor(),
                        background = if (trace.done) colors.surfaceAlt else accentSoftColor(),
                    )
                }
            }
        }
        // 思考过程（可折叠）
        if (message.reasoning.isNotBlank()) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radii.chip))
                    .clickable { showReasoning = !showReasoning }
                    .padding(vertical = 3.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(
                    if (showReasoning) PocketIcons.ChevronDown else PocketIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (streaming && message.text.isEmpty()) "鲤鱼正在想…" else "思考过程",
                    style = PocketTypography.label,
                    color = colors.textTertiary,
                )
            }
            if (showReasoning) {
                Text(
                    message.reasoning,
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                    modifier = Modifier
                        .padding(start = 6.dp, top = 4.dp, bottom = 6.dp)
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(colors.surfaceAlt.copy(alpha = 0.5f))
                        .padding(10.dp),
                )
            }
        }
        if (message.text.isNotBlank()) {
            MarkdownText(
                markdown = message.text,
                color = if (message.failed) colors.danger else colors.textPrimary,
                selectable = true,
            )
        } else if (streaming) {
            Text("…", style = PocketTypography.body, color = colors.textTertiary)
        }
    }
}

