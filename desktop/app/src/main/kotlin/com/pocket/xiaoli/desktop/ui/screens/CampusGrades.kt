package com.pocket.xiaoli.desktop.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.core.academic.ZfGradeItem
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.state.clearGradesCache
import com.pocket.xiaoli.desktop.state.exportGradesCsv
import com.pocket.xiaoli.desktop.state.cachedTermsSorted
import com.pocket.xiaoli.desktop.state.currentCachedTerm
import com.pocket.xiaoli.desktop.state.gradesAreCached
import com.pocket.xiaoli.desktop.state.LoginPurpose
import com.pocket.xiaoli.desktop.ui.components.EmptyState
import com.pocket.xiaoli.desktop.ui.components.PocketCard
import com.pocket.xiaoli.desktop.ui.components.PrimaryButton
import com.pocket.xiaoli.desktop.ui.components.SecondaryButton
import com.pocket.xiaoli.desktop.ui.components.StatTile
import com.pocket.xiaoli.desktop.ui.components.TagChip
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors

/**
 * 成绩查询：复用「从教务导入」的登录会话，应用内直接拉学期列表和成绩，不再跳浏览器。
 */
@Composable
internal fun GradesSection(state: AppState, modifier: Modifier = Modifier) {
    val colors = pocketColors()
    val session = state.academicSession
    val cached = state.cachedGradeTerms

    if (session == null && cached.isEmpty()) {
        PocketCard(modifier.fillMaxWidth()) {
            EmptyState(
                PocketIcons.Lock,
                "登录教务后查看成绩",
                "学号 + 密码 + 验证码，登录一次就能查询；密码不保存。查过的学期会留在本机，下次不必再登录。",
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("登录教务", icon = PocketIcons.GraduationCap) {
                state.openLogin(LoginPurpose.GRADES)
            }
        }
        return
    }

    // 没会话但有缓存：直接给上次的结果，别把用户挡在登录页外面
    if (state.gradesAreCached) {
        val entry = state.currentCachedTerm ?: cached.maxByOrNull { it.savedAt }!!
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("成绩", style = PocketTypography.sectionTitle, color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("缓存于 ${Format.dateTime(entry.savedAt)}", style = PocketTypography.caption, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                SecondaryButton("导出 CSV", icon = PocketIcons.Download) { state.notify(state.exportGradesCsv()) }
                Spacer(Modifier.width(8.dp))
                SecondaryButton("重新登录", icon = PocketIcons.Refresh) { state.openLogin(LoginPurpose.GRADES) }
            }
            PocketCard(Modifier.fillMaxWidth(), padding = 12.dp) {
                Text(
                    "这是上次登录时拉到的 ${entry.termLabel} 成绩，不是实时数据；要最新结果请重新登录。",
                    style = PocketTypography.caption,
                    color = colors.warning,
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton("清除本机成绩缓存", icon = PocketIcons.Trash) { state.clearGradesCache() }
            }
            // 查过多个学期时，缓存视图也能切
            if (cached.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.cachedTermsSorted.forEachIndexed { index, item ->
                        val selected = index == state.cachedGradeIndex
                        TagChip(
                            item.termLabel,
                            color = if (selected) Color.White else colors.textSecondary,
                            background = if (selected) accentColor() else colors.surfaceAlt,
                            onClick = { state.cachedGradeIndex = index },
                        )
                    }
                }
            }
            StatsRow(entry.items)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.items.forEach { grade -> GradeCard(grade) }
            }
        }
        return
    }

    LaunchedEffect(Unit) {
        if (state.gradeTerms.isEmpty() && state.grades.isEmpty() && !state.gradesLoading) {
            state.loadGrades(reloadTerms = true)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("成绩", style = PocketTypography.sectionTitle, color = colors.textPrimary)
            if (state.gradeTerms.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(state.gradeTerms.getOrNull(state.gradeTermIndex)?.label.orEmpty(), style = PocketTypography.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton("导出 CSV", icon = PocketIcons.Download) { state.notify(state.exportGradesCsv()) }
            SecondaryButton("刷新", icon = PocketIcons.Refresh) { state.refreshGrades() }
        }

        if (state.gradeTerms.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.gradeTerms.forEachIndexed { index, term ->
                    val selected = index == state.gradeTermIndex
                    TagChip(
                        term.label,
                        color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
                        background = if (selected) accentColor() else colors.surfaceAlt,
                        onClick = { state.selectGradeTerm(index) },
                    )
                }
            }
        }

        state.gradesError?.let { errorText ->
            PocketCard(Modifier.fillMaxWidth()) {
                Text(errorText, style = PocketTypography.body, color = colors.danger)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("重新登录") { state.openLogin(LoginPurpose.GRADES) }
            }
        }

        if (state.gradesLoading) {
            Text("正在查询成绩…", style = PocketTypography.caption, color = colors.textSecondary)
        }

        when {
            state.grades.isNotEmpty() -> {
                StatsRow(state.grades)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.grades.forEach { grade -> GradeCard(grade) }
                }
            }
            !state.gradesLoading && state.gradesError == null -> {
                PocketCard(Modifier.fillMaxWidth()) {
                    EmptyState(PocketIcons.Book, "这个学期没有成绩", "换一个学期看看，或点右上角刷新")
                }
            }
        }
    }
}

@Composable
private fun GradeCard(grade: ZfGradeItem) {
    val colors = pocketColors()
    val score = grade.totalScore.trim().toDoubleOrNull()
    val scoreColor = when {
        score == null -> colors.textPrimary
        score >= 90 -> colors.success
        score < 60 -> colors.danger
        else -> accentColor()
    }
    PocketCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(grade.courseName.ifBlank { "未命名课程" }, style = PocketTypography.cardTitle, color = colors.textPrimary)
                Spacer(Modifier.height(3.dp))
                val meta = buildList {
                    grade.credits.takeIf { it.isNotBlank() }?.let { add("$it 学分") }
                    grade.gpa.takeIf { it.isNotBlank() }?.let { add("绩点 $it") }
                    grade.examType.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = PocketTypography.caption, color = colors.textTertiary)
                }
            }
            Text(
                grade.totalScore.ifBlank { "—" },
                style = PocketTypography.sectionTitle,
                color = scoreColor,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        val parts = buildList {
            grade.regular.takeIf { it.isNotBlank() }?.let { add("平时 $it") }
            grade.final.takeIf { it.isNotBlank() }?.let { add("期末 $it") }
            grade.midterm.takeIf { it.isNotBlank() }?.let { add("期中 $it") }
        }
        if (parts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(parts.joinToString(" · "), style = PocketTypography.label, color = colors.textSecondary)
        }
    }
}

/** 课程数 / 平均分 / 学分合计（实时与缓存两处共用一份口径）。 */
@Composable
private fun StatsRow(items: List<ZfGradeItem>) {
    val colors = pocketColors()
    val stats = com.pocket.xiaoli.desktop.logic.gradeStats(items)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("课程", "${stats.count}", hint = "门", modifier = Modifier.weight(1f))
        StatTile(
            "平均分",
            if (stats.average == null) "—" else String.format("%.1f", stats.average),
            hint = "分",
            modifier = Modifier.weight(1f),
            accent = accentColor(),
        )
        StatTile(
            "学分合计",
            if (stats.credits == 0.0) "—" else String.format("%.1f", stats.credits),
            modifier = Modifier.weight(1f),
        )
        stats.gpa?.let {
            StatTile("平均绩点", String.format("%.2f", it), modifier = Modifier.weight(1f))
        }
    }
}
