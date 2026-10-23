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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.accentColor
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import androidx.compose.foundation.lazy.itemsIndexed

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
        topBar = {
            TopAppBar(
                title = { Text("考试安排", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = skin.spaceLg, end = skin.spaceLg,
                top = skin.spaceMd, bottom = skin.spaceHuge,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            // ── 说明 + 粘贴区 ──
            item {
                NoticeInputCard(
                    text = state.noticeText,
                    onTextChange = vm::onNoticeChange,
                    onParse = vm::parse,
                )
            }

            if (state.message != null) {
                item {
                    MessageRow(state.message!!, onDismiss = vm::clearMessage)
                }
            }

            // 规则解析没认出条目时，才提供 AI 兜底。
            // **刻意不自动调用**：规则能处理的场合不该联网、不该花钱、不该让用户等。
            if (state.parsed && state.pending.isEmpty() && !state.aiTried) {
                item {
                    AiFallbackCard(
                        loading = state.aiLoading,
                        configured = vm.aiAvailable,
                        onTry = vm::parseWithAi,
                    )
                }
            }

            // ── 预览：解析结果 + 勾选 ──
            if (state.pending.isNotEmpty()) {
                item {
                    PreviewHeader(
                        count = state.pending.size,
                        selected = state.selectedCount,
                        onSelectAll = { vm.selectAll(true) },
                        onSelectNone = { vm.selectAll(false) },
                    )
                }
                itemsIndexed(state.pending) { index, p ->
                    PendingExamRow(p, onToggle = { vm.toggle(index) })
                }
                item {
                    Button(
                        onClick = vm::confirm,
                        enabled = state.selectedCount > 0,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(skin.radiusChip),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
                    ) {
                        Text("确认添加 ${state.selectedCount} 场考试到待办", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (state.parsed) {
                item {
                    EmptyHint("没有识别到考试安排", "检查一下是否包含了「日期」，或换一段通知文字再试")
                }
            }

            // ── 已有考试 ──
            if (state.existing.isNotEmpty()) {
                item {
                    Text(
                        "已有考试（${state.pendingCount} 场未完成）",
                        style = PocketTypography.cardTitle,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = skin.spaceSm),
                    )
                }
                items(state.existing, key = { it.id }) { exam ->
                    ExistingExamRow(
                        exam = exam,
                        onToggle = { vm.toggleCompleted(exam) },
                        onDelete = { vm.delete(exam) },
                        // 删除/完成后滑出并让位（依赖上面的 key）
                        modifier = Modifier.animateItem(),
                    )
                }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.EventNote, null, tint = accentColor(), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(skin.spaceSm))
                Text("粘贴考试通知", style = PocketTypography.cardTitle, color = colors.textPrimary)
            }
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                "把教务通知里的考试安排**整段复制**过来即可，表格、多行、中英文标点都能认。" +
                    "识别后会先列出来让你确认，不会直接写入。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceMd))

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                placeholder = {
                    Text(
                        "例如：\n高等数学A   2026-01-15   14:00-16:00   教三楼305   12",
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
private fun PreviewHeader(
    count: Int,
    selected: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "识别到 $count 场（已选 $selected）",
            style = PocketTypography.cardTitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSelectAll) { Text("全选", fontSize = 12.sp) }
        TextButton(onClick = onSelectNone) { Text("全不选", fontSize = 12.sp) }
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
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

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
                Text(
                    exam.courseName,
                    style = PocketTypography.body,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(exam.summary, style = PocketTypography.caption, color = colors.textTertiary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).invisibleTouchTarget()) {
                Icon(
                    Icons.Outlined.Delete, "删除",
                    tint = colors.textTertiary,
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

/**
 * AI 兜底入口。
 *
 * 只在规则解析没认出条目时出现 —— 让用户知道"还有一招"，
 * 同时**明确告知这一步要联网**，而不是偷偷发请求。
 *
 * 未配置 API Key 时按钮仍可点，点了会告诉用户**去哪配** ——
 * 直接隐藏按钮的话，用户不知道有这个能力，也不知道差什么。
 */
@Composable
private fun AiFallbackCard(
    loading: Boolean,
    configured: Boolean,
    onTry: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusChip),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceHighlight),
    ) {
        Column(modifier = Modifier.padding(skin.spaceMd)) {
            Text(
                text = "格式不规则？可以让 AI 试一次",
                style = PocketTypography.body,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (configured) {
                    "会把上面这段通知发给你的 AI 接口识别（需联网）。结果仍需你核对。"
                } else {
                    "需要先配置 AI 接口：「鲤鱼 → 右上角设置」填 Base URL 与 API Key。"
                },
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Button(
                onClick = onTry,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(40.dp).invisibleTouchTarget(),
                shape = RoundedCornerShape(skin.radiusChip),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor()),
            ) {
                Text(
                    if (loading) "识别中……" else if (configured) "让 AI 试试" else "去配置说明",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(title: String, subtitle: String) {    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = PocketTypography.body, color = colors.textSecondary)
        Spacer(Modifier.height(skin.spaceXs))
        Text(subtitle, style = PocketTypography.caption, color = colors.textTertiary)
    }
}
