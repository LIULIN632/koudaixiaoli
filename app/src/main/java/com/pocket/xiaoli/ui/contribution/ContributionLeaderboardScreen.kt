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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.data.contribution.ContributionAction
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
 * 有人写笔记，有人报 bug。
 *
 * ## 2026-10-24：积分制下线，榜单按昵称列人
 *
 * 用户要求“积分制先去掉，按名字排序”。所以这一页**不再有分数、名次和领奖台**：
 * - 排序口径是昵称（中文按拼音，见 `ContributionOrder`），不是谁做得多；
 * - 分类 / 周期仍然保留 —— 它们回答的是“这一周、这一类里有哪些人参与过”；
 * - 服务端仍把动作记进历史账本，只是不再汇总成分数返回（见 `ContributionModels` 的 KDoc）。
 *
 * 主视觉的大数字因此改成**在榜人数**；没有分数可秀时，"有多少人一起补校园"
 * 是这个页面唯一还成立的数量。
 *
 * ## 隐私
 *
 * 默认不参与。加入后只上传昵称与动作名；课表、教室号、笔记正文一个字节都不传。
 * 退出会立刻停止上报，并删除服务器上该 cid 的全部榜单记录。
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
                        message = "这个版本没有配置榜单地址。下面可以先看看哪些动作会被记录。",
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
                        title = "这一栏还没有人参与",
                        message = "成为第一个：导一份课表、补一间教室，或者写一条笔记。",
                        accent = ServiceAccent.STUDY,
                    )
                }

                else -> {
                    // 没有领奖台：前三名是"分数最高"的产物，按昵称排之后它只会
                    // 把任意三个人抬高。所有人用同一种行，顺序就是名字顺序。
                    if (board.fromCache) {
                        item(key = "cache") {
                            Text(
                                "离线缓存 · 上次更新 ${shortTime(board.fetchedAt)}",
                                fontSize = 11.sp,
                                color = colors.textTertiary,
                            )
                        }
                    }
                    items(board.entries, key = { it.rank }) { entry ->
                        NameRow(entry)
                    }
                }
            }

            item(key = "rules") {
                RulesCard()
            }

            item(key = "privacy") {
                Text(
                    "榜单只记录昵称和「做了什么动作」，不上传课表、教室号、笔记正文。" +
                        "退出会删除服务器上这个昵称的全部榜单记录。",
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
                    "会立即停止上报，并删除服务器上这个昵称的全部榜单记录。这张手机上不会留下 cid 或队列。",
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
                // 没有分数可秀：大数字改成"在榜人数"，它是这一页唯一还成立的数量。
                if (board != null) {
                    // 人数用服务端的完整计数（列表本身被 limit 截断过）。
                    val total = board.total.coerceAtLeast(board.entries.size)
                    ServiceHeroCount(number = total.toString(), unit = "人在榜")
                } else {
                    Text(
                        "已加入",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(skin.spaceSm))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        "已加入 · 按昵称排序"
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

/** 榜单里的一行：头像首字 + 昵称 +（自己）「我」。不显示序号与分数。 */
@Composable
private fun NameRow(entry: ContributionEntry) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

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
            }
        }
    }
}

// ── 参与说明 ────────────────────────────────────────────────────────────

@Composable
private fun RulesCard() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(skin.cardPaddingLarge)) {
            Text(
                "怎么参与",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceSm))
            // 只列"哪些动作会被记一次"。积分、每日次数都已经不参与展示：
            // 还写"+10 / 每天最多 1 次"会让用户以为有名次可追。
            ContributionAction.entries.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "·",
                        fontSize = 13.sp,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        actionLabel(action),
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
            Text(
                "榜单只记「做过这类事」，不记次数也不记分数；名字按昵称排序。",
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

/** 榜单标题不再出现「总榜 / 名次」：它现在回答的是"哪些人参与过"。 */
private fun sectionTitle(category: ContributionCategory, period: ContributionPeriod): String = when {
    category == ContributionCategory.ALL && period == ContributionPeriod.WEEK -> "本周参与"
    category == ContributionCategory.ALL -> "全部参与"
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
