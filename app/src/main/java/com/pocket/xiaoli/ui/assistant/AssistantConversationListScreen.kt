package com.pocket.xiaoli.ui.assistant

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.agent.AssistantConversation
import com.pocket.xiaoli.ui.components.EmptyState
import com.pocket.xiaoli.ui.components.TopBarCircleButton
import com.pocket.xiaoli.ui.glass.glassSource
import com.pocket.xiaoli.ui.glass.glassTopBarSurface
import com.pocket.xiaoli.ui.glass.rememberAppBackdrop
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 会话列表。
 *
 * 数据来自已有的 Room 层（[com.pocket.xiaoli.data.dao.AssistantChatDao]）：
 * 实体、DAO、仓储在上一次改造里就已经齐全并且**一直在用**（建会话、取最近一条、写标题），
 * 缺的只是这个页面。所以这里没有任何新的持久化代码，只是把 `observeConversations()`
 * 接上界面 —— 也顺带避开了"自己维护 JSON 索引文件"那类一致性问题。
 *
 * [currentId] 用来把"当前正在聊的那一条"标出来，否则用户会分不清点了会不会切走。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConversationListScreen(
    conversations: List<AssistantConversation>,
    currentId: Long?,
    streaming: Boolean,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<AssistantConversation?>(null) }

    // 顶栏磨砂的采样源。与鲤鱼页同一套做法：录制挂在 Scaffold 的**内容区**上，
    // 顶栏是内容区的**兄弟节点** —— 录到的只有列表内容，不含顶栏自己。
    // ⚠️ 采样源录到自己会成环：RenderThread 级 SIGSEGV（原生崩溃，catch 不住）。
    val pageGlass = rememberAppBackdrop()

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("历史对话", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TopBarCircleButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "返回",
                        backdrop = pageGlass,
                    )
                },
                actions = {
                    TopBarCircleButton(
                        onClick = onNew,
                        icon = Icons.Filled.Add,
                        label = "新对话",
                        backdrop = pageGlass,
                    )
                },
                // 磨砂顶栏：底色与底边发丝线交给它画。
                // TopAppBar 自己的底色必须透明，否则会盖住玻璃。
                modifier = Modifier.glassTopBarSurface(
                    backdrop = pageGlass,
                    surfaceColor = colors.surfaceBase,
                    dividerColor = colors.borderDivider,
                ),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        // 顶栏高度不再把内容整体顶下去，而是交给列表的 contentPadding 顶部消化 ——
        // 否则内容永远到不了顶栏背后，磨砂没有东西可采，等于白做。
        val topInset = padding.calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 只让出**底部**的系统导航区；顶部留给列表自己撑开（见 contentPadding）
                .padding(bottom = padding.calculateBottomPadding())
                // 把内容区录进采样源，供顶栏采样
                .glassSource(pageGlass)
                .padding(horizontal = skin.spaceLg),
        ) {
            if (conversations.isEmpty()) {
                EmptyState(
                    title = "还没有历史对话",
                    subtitle = "回到鲤鱼页问一句，这里就会留下记录",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
                    contentPadding = PaddingValues(
                        // 顶部让开顶栏：内容因此会从顶栏背后滚过去
                        top = topInset + skin.spaceMd,
                        bottom = skin.spaceMd,
                    ),
                ) {
                    items(conversations, key = { it.id }) { item ->
                        ConversationRow(
                            item = item,
                            isCurrent = item.id == currentId,
                            onClick = {
                                // 流式期间不给切：收尾（落库、清「正在查」占位）还在跑，
                                // 这时候换掉消息列表会把收尾写到别的会话里去
                                if (streaming) {
                                    Toast.makeText(context, "鲤鱼还在回答，等它说完再切", Toast.LENGTH_SHORT).show()
                                } else {
                                    onOpen(item.id)
                                }
                            },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    // 删除确认：会话删了就没了（消息是级联删的），不能点一下就执行
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条对话？") },
            text = { Text("「${target.title}」的全部消息会一起删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) { Text("删除", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 列表里的一行。
 *
 * ⚠️ 标 `internal` 而非 `private`：**"当前会话"这个状态有两种表达**（视觉那圈主色描边、
 * 语义那个 `selected`），而它们**只有一起成立才对** —— 只看源码很容易改掉一个忘了另一个。
 * `AssistantWelcomeAndListScreenshotTest` 用真实组件渲它、并断言选中语义。
 * 另外它也是 `formatTime` 三个分支（今天/昨天/日期）的视觉落点。**不要改回 private。**
 *
 * @param now 计算"今天/昨天"的基准时刻。**生产不要传**（默认就是此刻）；
 *   它是给快照测试的注入点 —— 见下面那段说明。
 */
@Composable
internal fun ConversationRow(
    item: AssistantConversation,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        // 用 Surface 自己的 onClick 而不是在外面套 clickable：
        // 外面套的话水波纹按矩形裁切，圆角卡片上会露出四个直角。
        onClick = onClick,
        // ⚠️ 必须显式给 `selected`：**"当前在聊哪一段"原本只靠一圈主色描边表达**，
        // 而那对读屏用户等于不存在 —— 他听到一串标题，分不出哪个是当前会话，
        // 而"点了会不会切走"正是这个列表最要紧的一件事（见文件头的 KDoc）。
        // Material3 的 `Surface(onClick=…, selected=…)` 会带上选中语义，
        // 读屏因此会念出"已选中"。这条不靠人眼能验收，所以有对应单测守着。
        selected = isCurrent,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        // 当前会话整张卡都染一层主色：只靠 1dp 描边时，白卡排成一片，
        // "我在哪一段"仍要凑近看才发现。染色（而非加深描边）不增加任何边界噪声。
        color = if (isCurrent) {
            lerp(colors.surfaceCard, colors.accentMain, 0.06f)
        } else {
            colors.surfaceCard
        },
        border = BorderStroke(
            1.dp,
            // 当前会话用主色描边标出来，比加一个"当前"徽标更安静。
            // 注意它是**视觉**手段；语义那一半由上面的 `selected` 承担，两者不能互相替代。
            if (isCurrent) colors.accentMain.copy(alpha = 0.6f) else colors.borderDivider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = skin.spaceSm, end = skin.spaceXs, top = skin.spaceSm, bottom = skin.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 每行一枚对话图标砖：替代原先只在当前行出现的竖条。
            // 竖条只能标"当前"，而图标砖在没有当前项时也能给列表一条统一的左轴 ——
            // 标题不再从卡片最左边贴边开始，整列看起来是"一排会话"而非"一堆字"。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(skin.radiusChip))
                    .background(
                        if (isCurrent) colors.accentMain.copy(alpha = 0.14f) else colors.surfaceAlt
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    tint = if (isCurrent) colors.accentMain else colors.textTertiary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) colors.accentMain else colors.textPrimary,
                    maxLines = 1,
                )
                // 预览：让人不用点进去就知道这段聊的是什么。
                // 标题只截了 14 字，很多问题在前 14 字里看不出区别
                if (item.preview.isNotBlank() && item.preview != item.title) {
                    Spacer(Modifier.height(skin.spaceMicro))
                    Text(
                        text = item.preview,
                        fontSize = 12.sp,
                        // 预览是"内容"，不是"元信息"：用次要色而不是三级色，
                        // 否则 12sp 的浅灰在浅底上几乎读不出来（列表就只剩标题可用）。
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(skin.spaceMicro))
                Text(
                    text = formatTime(item.updatedAt, now),
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    // ⚠️ 带上标题：列表里每一行的删除按钮**长得一模一样**，读屏若只念
                    // "删除这条对话"，用户无法知道即将删掉的是哪一段 —— 而删除是不可恢复的
                    //（消息级联删）。确认弹窗里虽然复述了标题，但那时已经点完了。
                    contentDescription = "删除对话：${item.title}",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 时间显示规则。
 *
 * 刻意不显示完整日期时间：会话列表里"今天 / 昨天 / 9月26日"比 `2026-09-26 18:47`
 * 更快被读懂，而精确到分钟在这个场景没有意义。
 *
 * 划天按**东八区**算（本项目只服务国内高校）：跟着设备时区走的话，
 * 用户把手机时区调成 UTC 就会看到"今天"标在昨天的时间上。
 *
 * ## ⚠️ `now` 这个参数不是可选的风格问题，是**确定性**问题
 *
 * 快照测试（`52-conversation-list`）要跑在任意一天、任意机器上，两次结果必须逐像素相同。
 * 而"今天/昨天/某月某日"全都相对 `now` 算 —— 如果 `ConversationRow` 内部直接取
 * `System.currentTimeMillis()`，**基准图会在第二天自己变红**，而且原因极难一眼看出
 *（看起来只是"某个日期变了"）。所以基准时刻必须能从外面注入。
 */
internal fun formatTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0L) return "刚刚"
    val dayMs = 24L * 60 * 60 * 1000
    val offset = 8L * 60 * 60 * 1000
    val startOfToday = now - (now + offset) % dayMs
    return when {
        millis >= startOfToday -> "今天"
        millis >= startOfToday - dayMs -> "昨天"
        millis >= startOfToday - 2 * dayMs -> "前天"
        else -> SimpleDateFormat("M月d日", Locale.CHINA).format(Date(millis))
    }
}
