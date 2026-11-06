package com.pocket.xiaoli.ui.exam

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.accentColor
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.Color
import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServiceHeroCount
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.ServiceAccent
import java.time.LocalDate

/**
 * 考试安排。
 *
 * ## 为什么是这个流程
 *
 * 教务系统**不提供**考试安排接口，考试信息通过**文件通知**下发。
 * 所以这里的流程是：
 *
 * ```
 * 粘贴通知内容 → 解析 → 预览（可勾选/取消）→ 确认 → 写入待办
 * ```
 *
 * **"预览再确认"这一步不能省**：通知格式千差万别，规则解析无法保证全对。
 * 直接入库的话，一条解析错的考试会变成**错误的提醒** ——
 * 用户可能因此走错考场，比"没提醒"严重得多。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExamScheduleScreen(
    onBack: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val vm: ExamScheduleViewModel = viewModel(
        factory = ExamScheduleViewModel.Factory(app.repository, app.appContainer)
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.surfaceBase,
        // 与其它二级页同一套顶栏（原来是裸 TopAppBar + surfaceCard 底，全 App 只此一处）
        topBar = { ServicePageTopBar(title = "考试安排", onBack = onBack) },
    ) { padding ->
        ExamScheduleContent(
            state = state,
            aiConfigured = vm.aiAvailable,
            modifier = Modifier.fillMaxSize().padding(padding),
            onNoticeChange = vm::onNoticeChange,
            onParse = vm::parse,
            onClearMessage = vm::clearMessage,
            onTryAi = vm::parseWithAi,
            onSelectAll = vm::selectAll,
            onTogglePending = vm::toggle,
            onConfirm = vm::confirm,
            onToggleCompleted = vm::toggleCompleted,
            onDelete = vm::delete,
        )
    }
}

/**
 * 考试安排的正文（纯状态驱动）。
 *
 * 抽出来是为了**能录基线**：整页 [ExamScheduleScreen] 要 ViewModel 与 Application，
 * Robolectric 里起不来，于是这一页此前一张截图都没有 —— 观感只能靠真机肉眼，
 * 而"粘贴框在窄屏上会不会把解析按钮挤下去"这种事恰恰只有图能看出来。
 *
 * [today] 是给基线用的**基准时刻注入**：倒计时是相对时间，不钉住的话
 * 基准图会随日期一天天变红（与 `ConversationRow(now = …)` 同一条纪律）。
 */
@Composable
internal fun ExamScheduleContent(
    state: ExamUiState,
    aiConfigured: Boolean,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    onNoticeChange: (String) -> Unit = {},
    onParse: () -> Unit = {},
    onClearMessage: () -> Unit = {},
    onTryAi: () -> Unit = {},
    onSelectAll: (Boolean) -> Unit = {},
    onTogglePending: (Int) -> Unit = {},
    onConfirm: () -> Unit = {},
    onToggleCompleted: (ExamEntity) -> Unit = {},
    onDelete: (ExamEntity) -> Unit = {},
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 「最近一场」按剩余天数取，而不是按列表首项：
    // 库里按 date 排序，但通知里常见省略年份的「1月18日」，补出来的年份可能排在最前却不是最早。
    // 已过去的场次不登 hero（行内仍写「已过去 N 天」），否则最久那条会被当成"最近一场"。
    val nextExam = state.existing
        .filter { !it.completed }
        .mapNotNull { exam -> ExamCountdown.daysLeft(exam.date, today)?.let { exam to it } }
        .filter { it.second >= 0 }
        .minByOrNull { it.second }
    // 大数字用 `pendingCount`（所有未完成），**不是** nextExam 那条列表的长度：
    // 通知里常有「时间地点见通知」这类没日期的行，按日期筛会让 hero 说 2 场、列表数出 3 行。
    val soonCount = state.pendingCount
    val doneCount = state.existing.size - soonCount

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = skin.spaceLg, end = skin.spaceLg,
            top = skin.spaceMd, bottom = skin.spaceHuge,
        ),
        verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        // ── 主视觉：与服务页那张教务卡同一个语义色，点进去颜色是连续的 ──
        item(key = "hero") {
            ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("待考考试", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.height(skin.spaceXs))
                        ServiceHeroCount(soonCount.toString(), "场")
                    }
                    ServiceHeroBadge(Icons.AutoMirrored.Outlined.EventNote)
                }
                Spacer(Modifier.height(skin.spaceMd))
                Text(
                    text = when {
                        nextExam != null ->
                            "${nextExam.first.courseName} · ${ExamCountdown.countdownText(nextExam.second)}"
                        state.existing.isEmpty() -> "还没有考试 · 把教务通知粘到下面"
                        soonCount > 0 -> "最近一场没写日期 · 倒计时认不出来"
                        else -> "都考完了 · 有新通知可以继续粘"
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
                // 「共几场」不在这张卡上：它是下面那段列表的条数，放列表标题右侧才不重复；
                // 这里只放「已完成」——它解释的是上面那个数字为什么小于总数。
                if (doneCount > 0) {
                    Spacer(Modifier.height(skin.spaceLg))
                    ServiceHeroChip("已完成 $doneCount 场")
                }
            }
        }

        // ── 录入：标题走区块标题，卡内不再复述一遍 ──
        item(key = "input-label") {
            ServiceSectionLabel(
                title = "录入考试",
                accent = ServiceAccent.ACADEMIC,
                modifier = Modifier.padding(top = skin.spaceSm),
            )
        }
        item(key = "input") {
            NoticeInputCard(
                text = state.noticeText,
                onTextChange = onNoticeChange,
                onParse = onParse,
            )
        }

        if (state.message != null) {
            item(key = "message") {
                MessageRow(state.message!!, onDismiss = onClearMessage)
            }
        }

        // ── 预览：解析结果 + 勾选 ──
        if (state.pending.isNotEmpty()) {
            item(key = "preview-header") {
                ServiceSectionLabel(
                    title = "识别到 ${state.pending.size} 场（已选 ${state.selectedCount}）",
                    accent = ServiceAccent.ACADEMIC,
                    modifier = Modifier.padding(top = skin.spaceSm),
                    trailing = {
                        TextButton(onClick = { onSelectAll(true) }) { Text("全选", fontSize = 12.sp) }
                        TextButton(onClick = { onSelectAll(false) }) { Text("全不选", fontSize = 12.sp) }
                    },
                )
            }
            itemsIndexed(
                state.pending,
                // 一门课可能在两个时间考两次，日期 + 时间 + 课名才是这一条的身份。
                // 这一行本身无状态（勾选存在模型里），但 `onToggle` 捕获的是**下标**：
                // 没有 key 时列表一变（确认添加后整列缩短），按位置复用的那一行仍可能
                // 拿着旧下标去 toggle 另一条 —— 有 key 才能保证"行跟着数据走"。
                key = { _, p -> "${p.schedule.date} ${p.schedule.time} ${p.schedule.courseName}" },
            ) { index, p ->
                PendingExamRow(p, onToggle = { onTogglePending(index) })
            }
            item(key = "confirm") {
                Button(
                    onClick = onConfirm,
                    enabled = state.selectedCount > 0,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(skin.radiusChip),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                ) {
                    Text("确认添加 ${state.selectedCount} 场考试到待办", fontWeight = FontWeight.Bold)
                }
            }
        } else if (state.parsed) {
            // 「没认出来」与「让 AI 试」是同一件事的两半，合成一张卡：
            // 原先上下两张都在说"这段不行，换个办法"，读起来像报错叠报错。
            // AI **刻意不自动调用**：规则能处理的场合不该联网、不该花钱、不该让用户等。
            item(key = "parse-empty") {
                val offerAi = !state.aiTried
                ServiceEmptyCard(
                    icon = Icons.Outlined.SearchOff,
                    title = "没认出考试",
                    message = when {
                        !offerAi -> "AI 也没能认出来。通知里要有日期才算一场考试，换一段文字再试"
                        aiConfigured -> "通知里要有日期才算一场考试。格式不规则可以让 AI 再认一次，" +
                            "会把上面这段发给你的接口（需联网），结果仍需你核对"
                        else -> "通知里要有日期才算一场考试。也可以让 AI 认一次，" +
                            "需先在「鲤鱼 → 右上角设置」填 Base URL 与 API Key"
                    },
                    accent = ServiceAccent.ACADEMIC,
                    actionLabel = when {
                        !offerAi -> null
                        state.aiLoading -> "识别中……"
                        aiConfigured -> "让 AI 试试"
                        else -> "去配置说明"
                    },
                    onAction = if (offerAi) ({ if (!state.aiLoading) onTryAi() }) else null,
                )
            }
        }

        // ── 已有考试 ──
        if (state.existing.isNotEmpty()) {
            item(key = "existing-label") {
                ServiceSectionLabel(
                    title = "已有考试",
                    accent = ServiceAccent.ACADEMIC,
                    modifier = Modifier.padding(top = skin.spaceSm),
                    trailing = {
                        Text(
                            "共 ${state.existing.size} 场",
                            fontSize = 12.sp,
                            color = colors.textTertiary,
                        )
                    },
                )
            }
            items(state.existing, key = { it.id }) { exam ->
                ExistingExamRow(
                    exam = exam,
                    today = today,
                    onToggle = { onToggleCompleted(exam) },
                    onDelete = { onDelete(exam) },
                    // 删除/完成后滑出并让位（依赖上面的 key）
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun NoticeInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(modifier = Modifier.padding(skin.spaceLg)) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    Text(
                        "例如：高数A 2026-01-15 14:00-16:00 教三305",
                        style = PocketTypography.caption,
                        color = colors.textTertiary,
                    )
                },
                shape = RoundedCornerShape(skin.radiusChip),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceBase,
                    unfocusedContainerColor = colors.surfaceBase,
                ),
            )
            Spacer(Modifier.height(skin.spaceMd))

            Button(
                onClick = onParse,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
            ) {
                Text("识别考试", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PendingExamRow(p: PendingExam, onToggle: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val s = p.schedule

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(skin.spaceMd).clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (p.selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (p.selected) "已选" else "未选",
                tint = if (p.selected) accentColor() else colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.courseName,
                        style = PocketTypography.body,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (p.duplicate) {
                        Spacer(Modifier.width(skin.spaceSm))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colors.warning.copy(alpha = 0.16f),
                        ) {
                            Text(
                                "已在列表",
                                fontSize = 10.sp,
                                color = colors.warning,
                                modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = 1.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(s.summary, style = PocketTypography.caption, color = colors.textTertiary)
                if (s.seat.isNotBlank()) {
                    Text("座位 ${s.seat}", style = PocketTypography.caption, color = colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun ExistingExamRow(
    exam: ExamEntity,
    today: LocalDate,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 已完成的不再报倒计时：那行数字只会变成「已过去 N 天」，是噪音不是信息。
    val daysLeft = if (!exam.completed) ExamCountdown.daysLeft(exam.date, today) else null

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceCard,
    ) {
        Row(
            modifier = Modifier.padding(
                start = skin.spaceMd, top = skin.spaceSm,
                bottom = skin.spaceSm, end = skin.spaceXs,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (exam.completed) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                contentDescription = if (exam.completed) "已完成" else "未完成",
                tint = if (exam.completed) colors.success else colors.textTertiary,
                modifier = Modifier.size(20.dp).invisibleTouchTarget().clickable { onToggle() },
            )
            Spacer(Modifier.width(skin.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        exam.courseName,
                        style = PocketTypography.body,
                        fontWeight = FontWeight.Medium,
                        color = if (exam.completed) colors.textSecondary else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 与首页待办区同一条判据（`ExamCountdown.isSoon`）：SOON_DAYS 天内含今天/明天用警示色。
                    // 解析不出日期时不显示（而不是显示「未知」，那只是噪音）。
                    daysLeft?.let { d ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = ExamCountdown.countdownText(d),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ExamCountdown.isSoon(d)) colors.warning else colors.textTertiary,
                        )
                    }
                }
                Text(exam.summary, style = PocketTypography.caption, color = colors.textTertiary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                Icon(
                    Icons.Outlined.Delete, "删除",
                    // 危险色而不是灰：删除是唯一不可恢复的操作，灰色把它混进了装饰图标里。
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageRow(text: String, onDismiss: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceHighlight,
    ) {
        Row(
            modifier = Modifier.padding(skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Info, null, tint = colors.accentMain, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(skin.spaceSm))
            Text(
                text,
                style = PocketTypography.caption,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) }
        }
    }
}
