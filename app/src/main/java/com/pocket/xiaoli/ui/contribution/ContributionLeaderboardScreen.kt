package com.pocket.xiaoli.ui.contribution

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.contribution.ContributionAction
import com.pocket.xiaoli.data.contribution.ContributionBoard
import com.pocket.xiaoli.data.contribution.ContributionCategory
import com.pocket.xiaoli.data.contribution.ContributionEntry
import com.pocket.xiaoli.data.contribution.ContributionPeriod
import com.pocket.xiaoli.data.contribution.ContributionUiState
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.InlineError
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.components.campusui.CampusSegmentedTabs
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 「校园共建榜」整页。
 *
 * ## 为什么值得做
 *
 * 这个 App 的教室分布、课表模板、笔记都靠学生自己补。以前的「贡献」页只有
 * GitHub 链接 —— 会写代码的人才有参与感。榜单把**不会提 PR 的人**也接进来：
 * 导一份课表、补一间教室，都是可被看见的贡献。
 *
 * ## 分类的划分
 *
 * 「总榜 / 课程表 / 教室与地图 / 资料笔记 / 反馈纠错」不是按功能模块切的，
 * 而是按**一个人能贡献的东西**切的：有人整份导入课表，有人只顺手补一间教室，
 * 有人写笔记，有人报 bug。只看总分就变成"谁用得久谁第一"。
 *
 * ## 隐私
 *
 * 默认不参与。加入后只上传昵称与动作名；课表、教室号、笔记正文一个字节都不传。
 * 退出会立刻停止上报，并删除服务器上该 cid 的全部积分。
 */
@Composable
fun ContributionLeaderboardScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.contributionRepository
    val state by repository.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        repository.flush()
        repository.refresh()
    }

    ContributionLeaderboardContent(
        state = state,
        onBack = onBack,
        onSelectCategory = repository::selectCategory,
        onSelectPeriod = repository::selectPeriod,
        onJoin = repository::join,
        onRename = repository::rename,
        onLeave = repository::leave,
        onRefresh = repository::refresh,
    )
}

/**
 * 纯内容层：只吃 [ContributionUiState] + 回调，便于快照测试。
 *
 * ⚠️ 测试里传入的榜单必须是**样例数据**，不能为了让截图好看而把假数据塞进正式逻辑。
 */
@Composable
internal fun ContributionLeaderboardContent(
    state: ContributionUiState,
    onBack: () -> Unit,
    onSelectCategory: (ContributionCategory) -> Unit,
    onSelectPeriod: (ContributionPeriod) -> Unit,
    onJoin: (String) -> String?,
    onRename: (String) -> String?,
    onLeave: () -> Unit,
    onRefresh: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    var nicknameDialog by remember { mutableStateOf<NicknameMode?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }

    val board = state.board
    val error = state.error

    Scaffold(
        topBar = { ServicePageTopBar(title = "校园共建榜", onBack = onBack) },
        containerColor = colors.surfaceBase,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = skin.spaceLg,
                end = skin.spaceLg,
                top = skin.spaceSm,
                bottom = skin.spaceXxl,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            item(key = "hero") {
                Hero(state)
            }

            if (!state.configured) {
                item(key = "offline") {
                    ServiceEmptyCard(
                        icon = Icons.Outlined.CloudOff,
                        title = "榜单还没接上服务器",
                        message = "这个版本没有配置榜单地址。下面可以先看看哪些动作能上分。",
                        accent = ServiceAccent.QUERY,
                    )
                }
            } else if (!state.enabled) {
                item(key = "join") {
                    JoinCard(
                        nickname = state.nickname,
                        onJoin = { nicknameDialog = NicknameMode.JOIN },
                    )
                }
            } else {
                item(key = "me") {
                    MyRankCard(
                        state = state,
                        onRename = { nicknameDialog = NicknameMode.RENAME },
                        onLeave = { confirmLeave = true },
                    )
                }
            }

            item(key = "filters") {
                Column(verticalArrangement = Arrangement.spacedBy(skin.spaceXs)) {
                    CampusSegmentedTabs(
                        options = ContributionCategory.entries.map { it.label to it },
                        selected = state.category,
                        onSelect = onSelectCategory,
                    )
                    CampusSegmentedTabs(
                        options = ContributionPeriod.entries.map { it.label to it },
                        selected = state.period,
                        onSelect = onSelectPeriod,
                        scrollable = false,
                    )
                }
            }

            item(key = "section") {
                ServiceSectionLabel(
                    title = sectionTitle(state.category, state.period),
                    accent = ServiceAccent.ACADEMIC,
                )
            }

            if (error != null) {
                item(key = "error") {
                    InlineError(message = error, onRetry = onRefresh)
                }
            }

            when {
                state.loading && board == null -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = skin.spaceXl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                board == null -> item(key = "empty") {
                    ServiceEmptyCard(
                        icon = Icons.Outlined.Inbox,
                        title = "还没拉到榜单",
                        message = "检查一下网络，或者稍后再试。",
                        accent = ServiceAccent.QUERY,
                        actionLabel = "重试",
                        onAction = onRefresh,
                    )
                }

                board.entries.isEmpty() -> item(key = "empty") {
                    ServiceEmptyCard(
                        icon = Icons.Outlined.Inbox,
                        title = "这一栏还没有人上分",
                        message = "成为第一个：导一份课表、补一间教室，或者写一条笔记。",
                        accent = ServiceAccent.STUDY,
                    )
                }

                else -> {
                    item(key = "podium") {
                        Podium(board)
                        if (board.fromCache) {
                            Spacer(Modifier.height(skin.spaceXs))
                            Text(
                                "离线缓存 · 上次更新 ${shortTime(board.fetchedAt)}",
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    items(board.entries.drop(3), key = { it.rank }) { entry ->
                        RankRow(entry)
                    }
                }
            }

            item(key = "rules") {
                RulesCard()
            }

            item(key = "privacy") {
                Text(
                    "榜单只记录昵称和「做了什么动作」，不上传课表、教室号、笔记正文。" +
                        "退出会删除服务器上这个昵称的全部积分。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }

    nicknameDialog?.let { mode ->
        NicknameDialog(
            mode = mode,
            initial = if (mode == NicknameMode.RENAME) state.nickname else "",
            onDismiss = { nicknameDialog = null },
            onSubmit = { raw ->
                val error = if (mode == NicknameMode.JOIN) onJoin(raw) else onRename(raw)
                if (error == null) nicknameDialog = null
                error
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("退出共建榜？") },
            text = {
                Text(
                    "会立即停止上报，并删除服务器上这个昵称的全部积分。这张手机上不会留下 cid 或队列。",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    },
                ) {
                    Text("退出并删除", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("再想想") }
            },
        )
    }
}

// ── 主视觉 ──────────────────────────────────────────────────────────────

@Composable
private fun Hero(state: ContributionUiState) {
    val skin = LocalSkin.current
    val me = state.board?.me
    val board = state.board

    ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "把校园补全一点",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "课表、教室、笔记 —— 你补的一条，可能是别人少踩的一个坑。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            ServiceHeroBadge(icon = Icons.Filled.EmojiEvents)
        }

        Spacer(Modifier.height(skin.spaceLg))

        when {
            !state.configured -> Text(
                "这个版本没有连接榜单服务器",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
            )

            !state.enabled -> {
                Text("还没加入", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "加入前不产生任何数据；加入后只记昵称和动作名。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            me != null -> {
                ServiceHeroCount(number = me.points.toString(), unit = "分")
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ServiceHeroChip("第 ${me.rank} 名")
                    ServiceHeroChip(me.nickname)
                    board?.let { ServiceHeroChip(periodLabel(it.period)) }
                }
            }

            else -> {
                Text("还在榜外", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "这一期还没有你的记录。导一份课表、补一间教室，就会出现在榜上。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

// ── 加入 / 我的 ────────────────────────────────────────────────────────

@Composable
private fun JoinCard(nickname: String, onJoin: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPaddingLarge)) {
            Text(
                "加入共建榜",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "用一个昵称上榜。只上传昵称和「导入了课表 / 补了教室 / 写了笔记」这类动作，" +
                    "不上传任何课表内容、教室号或笔记正文。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(skin.spaceMd))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentMain,
                    contentColor = onAccentColor(),
                ),
            ) {
                Text(if (nickname.isBlank()) "用昵称加入" else "继续用「$nickname」加入")
            }
        }
    }
}

@Composable
private fun MyRankCard(
    state: ContributionUiState,
    onRename: () -> Unit,
    onLeave: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val me = state.board?.me

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.nickname.ifBlank { "我" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (me == null) {
                        "本期还没有记录"
                    } else {
                        "第 ${me.rank} 名 · ${me.points} 分"
                    },
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
            TextButton(onClick = onRename) { Text("改名", fontSize = 13.sp) }
            TextButton(onClick = onLeave) {
                Text("退出", fontSize = 13.sp, color = colors.danger)
            }
        }
    }
}

// ── 榜单 ────────────────────────────────────────────────────────────────

@Composable
private fun Podium(board: ContributionBoard) {
    val top = board.entries.take(3)
    if (top.isEmpty()) return
    val order = listOfNotNull(top.getOrNull(1), top.getOrNull(0), top.getOrNull(2))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        order.forEach { entry ->
            PodiumColumn(
                entry = entry,
                modifier = Modifier.weight(
                    if (entry.rank == 1) 1.15f else 1f,
                ),
            )
        }
    }
}

@Composable
private fun PodiumColumn(entry: ContributionEntry, modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = podiumTint(entry.rank)
    val blockHeight = when (entry.rank) {
        1 -> 58.dp
        2 -> 42.dp
        else -> 34.dp
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (entry.rank == 1) 46.dp else 40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                entry.nickname.take(1),
                fontSize = if (entry.rank == 1) 19.sp else 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.nickname,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${entry.points} 分",
            fontSize = 11.sp,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockHeight)
                .clip(RoundedCornerShape(topStart = skin.radiusCard, topEnd = skin.radiusCard))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${entry.rank}",
                fontSize = if (entry.rank == 1) 24.sp else 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = tint,
            )
        }
    }
}

@Composable
private fun RankRow(entry: ContributionEntry) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = if (entry.mine) colors.accentMain else colors.textTertiary

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (entry.mine) {
            BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.35f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entry.rank}",
                modifier = Modifier.width(28.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.accentMain.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.nickname.take(1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentMain,
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                entry.nickname,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = if (entry.mine) FontWeight.Bold else FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.mine) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.accentMain.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text("我", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accentMain)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(
                "${entry.points} 分",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textSecondary,
            )
        }
    }
}

// ── 上分说明 ────────────────────────────────────────────────────────────

@Composable
private fun RulesCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPaddingLarge)) {
            Text(
                "怎么上分",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceSm))
            ContributionAction.entries.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            actionLabel(action),
                            fontSize = 13.sp,
                            color = colors.textPrimary,
                        )
                        Text(
                            "每天最多 ${action.dailyCap} 次",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                        )
                    }
                    Text(
                        "+${action.points}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = serviceAccentColor(ServiceAccent.ACADEMIC),
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "分数在服务器上按动作计算，客户端说了不算；同分时先达到的人排前面。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = colors.textTertiary,
            )
        }
    }
}

// ── 昵称对话框 ──────────────────────────────────────────────────────────

private enum class NicknameMode { JOIN, RENAME }

@Composable
private fun NicknameDialog(
    mode: NicknameMode,
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    var text by remember(mode) { mutableStateOf(initial) }
    var error by remember(mode) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == NicknameMode.JOIN) "加入共建榜" else "改个昵称") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text("昵称") },
                    supportingText = {
                        Text(
                            error ?: "1–12 个字，榜上就显示这个；不要冒用官方身份",
                            fontSize = 11.sp,
                        )
                    },
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val message = onSubmit(text)
                    if (message == null) onDismiss() else error = message
                },
            ) {
                Text(if (mode == NicknameMode.JOIN) "加入" else "保存", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ── 小工具 ──────────────────────────────────────────────────────────────

@Composable
private fun podiumTint(rank: Int): Color {
    val colors = LocalPocketXiaoliColors.current
    return when (rank) {
        1 -> colors.warning
        2 -> serviceAccentColor(ServiceAccent.QUERY)
        else -> serviceAccentColor(ServiceAccent.PAYMENT)
    }
}

private fun sectionTitle(category: ContributionCategory, period: ContributionPeriod): String = when {
    category == ContributionCategory.ALL && period == ContributionPeriod.WEEK -> "本周总榜"
    category == ContributionCategory.ALL -> "总榜 · 全部"
    else -> "${category.label} · ${period.label}"
}

private fun periodLabel(period: ContributionPeriod): String =
    if (period == ContributionPeriod.WEEK) "本周" else "全部"

private fun actionLabel(action: ContributionAction): String = when (action) {
    ContributionAction.COURSE_IMPORT -> "导入一份课表"
    ContributionAction.ROOM_CELL_ADD -> "补一间教室"
    ContributionAction.ROOM_CELL_EDIT -> "修正教室信息"
    ContributionAction.NOTE_SAVE -> "保存一条笔记"
    ContributionAction.FEEDBACK_SEND -> "发一次反馈"
}

/** 列表里的时间只说明「缓存有多旧」，不参与排序，所以只要时分。 */
private fun shortTime(ms: Long): String {
    val local = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneOffset.ofHours(8))
    return "%02d:%02d".format(local.hour, local.minute)
}
