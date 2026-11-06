package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.desktop.data.Exam
import com.pocket.xiaoli.desktop.logic.PendingExam
import com.pocket.xiaoli.desktop.logic.sortedExams
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.confirmExams
import com.pocket.xiaoli.desktop.state.deleteExam
import com.pocket.xiaoli.desktop.state.onExamNoticeChange
import com.pocket.xiaoli.desktop.state.parseExams
import com.pocket.xiaoli.desktop.state.selectAllExams
import com.pocket.xiaoli.desktop.state.toggleExam
import com.pocket.xiaoli.desktop.state.toggleExamEditor
import com.pocket.xiaoli.desktop.state.toggleExamPending
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PocketTextField
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import java.time.LocalDate

/**
 * 考试安排。
 *
 * ## 为什么这一页不是「登录教务直接查」
 *
 * 正方教务**没有**考试安排接口（成绩、课表有），考试信息只出现在教务通知的
 * 正文或附件里。所以这一页走的是手机端同一套链路：**粘贴通知 → 规则解析 → 核对 → 添加**，
 * 解析与倒计时算法都在共享核心（`core/exam/`），两端同一份口径。
 *
 * 必须先核对再添加：解析器认不出日期就当不了一条考试，但格式歪掉的通知仍可能
 * 认错列。**错误的考试提醒比没有提醒更糟** —— 用户会按错的时间走错考场。
 */
@Composable
internal fun ExamsSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val exams = sortedExams(state.data.exams, LocalDate.now())
    val selectedCount = state.examPending.count { it.selected }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("考试", style = PocketTypography.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            Text("教务无考试接口 · 粘贴通知解析", style = PocketTypography.caption, color = colors.textTertiary)
            Spacer(Modifier.weight(1f))
            if (state.examEditorOpen) {
                SecondaryButton("收起", icon = PocketIcons.Close) { state.toggleExamEditor() }
            } else {
                PrimaryButton("粘贴通知", icon = PocketIcons.Plus) { state.toggleExamEditor() }
            }
        }

        if (exams.isEmpty() && !state.examEditorOpen) {
            PocketCard(Modifier.fillMaxWidth()) {
                EmptyState(
                    PocketIcons.CalendarDays,
                    "还没有考试",
                    "把教务通知或群里那张表整段粘进来，我按行认出课程、日期、时间与考场",
                )
                PrimaryButton("粘贴通知", icon = PocketIcons.Plus, fillWidth = true) { state.toggleExamEditor() }
            }
        }

        if (state.examEditorOpen) {
            PocketCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                PocketTextField(
                    value = state.examNotice,
                    onValueChange = { state.onExamNoticeChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "高等数学A    2026-01-15    14:00-16:00    教三楼305    12",
                    singleLine = false,
                    minHeight = 116.dp,
                )
                state.examMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = PocketTypography.caption, color = colors.warning)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("解析", icon = PocketIcons.Search) { state.parseExams() }
                    if (state.examPending.isNotEmpty()) {
                        Text(
                            "认出 ${state.examPending.size} 条 · 扫了 ${state.examScanned} 行",
                            style = PocketTypography.caption,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }

        if (state.examPending.isNotEmpty()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.examPending.forEachIndexed { index, pending ->
                    ExamPickCard(pending) { state.toggleExamPending(index) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val allSelected = state.examPending.filter { !it.duplicate }.all { it.selected }
                SecondaryButton(if (allSelected) "全不选" else "全选") { state.selectAllExams(!allSelected) }
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    "添加 $selectedCount 场",
                    icon = PocketIcons.Plus,
                    enabled = selectedCount > 0,
                ) { state.confirmExams() }
            }
        }

        if (exams.isNotEmpty() && state.examPending.isEmpty()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                exams.forEach { exam ->
                    ExamCard(
                        exam = exam,
                        onToggle = { state.toggleExam(exam.id) },
                        onDelete = { state.deleteExam(exam.id) },
                    )
                }
            }
        }
    }
}

/** 一条待确认的考试：整卡可点，点一下切换勾选；重复项默认不勾。 */
@Composable
private fun ExamPickCard(pending: PendingExam, onClick: () -> Unit) {
    val colors = pocketColors()
    val accent = accentColor()
    val schedule = pending.schedule
    PocketCard(Modifier.fillMaxWidth().alpha(if (pending.selected) 1f else 0.6f), padding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (pending.selected) accent else colors.surfaceAlt)
                    .border(
                        1.dp,
                        if (pending.selected) accent else colors.borderDivider,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (pending.selected) {
                    Icon(PocketIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    schedule.courseName.ifBlank { "未命名科目" },
                    style = PocketTypography.cardTitle,
                    color = colors.textPrimary,
                )
                val meta = buildList {
                    schedule.date.takeIf { it.isNotBlank() }?.let { add(it) }
                    schedule.time.takeIf { it.isNotBlank() }?.let { add(it) }
                    schedule.location.takeIf { it.isNotBlank() }?.let { add(it) }
                    schedule.seat.takeIf { it.isNotBlank() }?.let { add("座位 $it") }
                }
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(meta.joinToString(" · "), style = PocketTypography.label, color = colors.textSecondary)
                }
            }
            if (pending.duplicate) {
                TagChip("已在列表", color = colors.textTertiary)
            }
        }
    }
}

/** 一场已录入的考试：倒计时按远近换色，考完可以标完成或直接删掉。 */
@Composable
private fun ExamCard(exam: Exam, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = pocketColors()
    val accent = accentColor()
    val daysLeft = ExamCountdown.daysLeft(exam.date, LocalDate.now())

    PocketCard(Modifier.fillMaxWidth().alpha(if (exam.completed) 0.5f else 1f), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exam.courseName.ifBlank { "未命名科目" }, style = PocketTypography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    exam.date.takeIf { it.isNotBlank() }?.let { add(it) }
                    exam.schedule.time.takeIf { it.isNotBlank() }?.let { add(it) }
                    exam.schedule.location.takeIf { it.isNotBlank() }?.let { add(it) }
                    exam.schedule.seat.takeIf { it.isNotBlank() }?.let { add("座位 ${exam.schedule.seat}") }
                }
                Text(
                    meta.joinToString(" · ").ifBlank { "时间地点见通知" },
                    style = PocketTypography.caption,
                    color = colors.textTertiary,
                )
            }
            CountdownChip(completed = exam.completed, daysLeft = daysLeft)
            GhostIconButton(
                PocketIcons.Check,
                contentDescription = if (exam.completed) "取消完成" else "标为已考完",
                tint = if (exam.completed) colors.success else colors.textSecondary,
                onClick = onToggle,
            )
            GhostIconButton(PocketIcons.Trash, contentDescription = "删除", tint = colors.danger, onClick = onDelete)
        }
    }
}

@Composable
private fun CountdownChip(completed: Boolean, daysLeft: Long?) {
    val colors = pocketColors()
    val accent = accentColor()
    when {
        completed -> TagChip("已完成", color = colors.success, background = colors.success.copy(alpha = 0.14f))
        daysLeft == null -> TagChip("时间待定", color = colors.textTertiary)
        daysLeft < 0 -> TagChip("已过去", color = colors.danger, background = colors.danger.copy(alpha = 0.12f))
        daysLeft <= ExamCountdown.SOON_DAYS -> TagChip(
            ExamCountdown.countdownText(daysLeft),
            color = Color.White,
            background = colors.warning,
        )
        else -> TagChip(
            ExamCountdown.countdownText(daysLeft),
            color = accent,
            background = accent.copy(alpha = 0.12f),
        )
    }
}
