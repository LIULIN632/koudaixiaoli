package com.pocket.xiaoli.ui.treasurebox

// 修课情况页的**汇总卡**：绩点 + 学分环 + 计划内/计划外统计。单独成文件的理由同 `AcademicCourseTree.kt`。

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.network.AcademicSummary
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.ui.components.infocard.color
import com.pocket.xiaoli.util.maskStudentId
import kotlin.math.roundToInt

/**
 * 学业汇总卡（绩点 + 学分环 + 一行脚注）。
 *
 * ## 版式（2026-10 第二次美化：从"白卡"升级成"主视觉卡"）
 *
 * 这一页此前是一张白卡 + 页底灰，翻进来像一张表单；而服务页里的其余二级页
 * （成绩查询 / 成绩档案）都已经是「语义色深底 hero + 白字」。这里改走同一套语言：
 * 语义色取 [ServiceAccent.ACADEMIC]（与「成绩查询」同源），所以"点的是学业卡，进来还是学业色"。
 *
 * 结构仍是**一个主角 + 一行脚注**：右边学分环是"修了多少"的图形答案，
 * 左边绩点是数字答案，学号 / 计划内通过 / 计划内未修压成底部胶囊 ——
 * 它们是"核实身份用"的信息，不该与绩点争字号。
 *
 * `internal` 而非 `private`：整页 `ResultContent` 依赖 ViewModel、渲不出来，
 * 而这条卡上有两个**只能靠语义树守**的约定（学号必须脱敏、学分为 0 时不显示「还差」），
 * 所以把它抽成纯入参组件供 `AcademicQueryPrivacyTest` / `AcademicQueryScreenshotTest` 直接渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryCard(summary: AcademicSummary, progress: CreditProgress?) {
    val skin = LocalSkin.current
    ServiceHeroCard(accent = ServiceAccent.ACADEMIC) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "平均学分绩点",
                    fontSize = 12.sp,
                    // 0.9 而不是 0.7：主视觉卡底压到亮度 ≤ 0.16，70% 白字只有约 3:1。
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    summary.gpa.ifBlank { "—" },
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 50.sp,
                )
                if (progress != null && progress.required > 0f) {
                    Spacer(Modifier.height(skin.spaceSm))
                    Text(
                        "已获 ${"%.1f".format(progress.earned)} / 要求 ${"%.1f".format(progress.required)} 学分",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            Spacer(Modifier.width(skin.spaceMd))
            if (progress != null) {
                CreditRing(progress = progress, modifier = Modifier.size(84.dp))
            } else {
                ServiceHeroBadge(Icons.Filled.School)
            }
        }
        val chips = buildList {
            // ⚠️ 必须脱敏：学号是全项目共用一条规则的敏感值（util/StudentIdMask.kt），
            // 这一页曾经整串印出来 —— 与「我的」页/选课页的显示口径不一致，
            // 也会在截图分享时把学号漏出去。
            if (summary.studentId.isNotBlank()) add("学号 ${maskStudentId(summary.studentId)}")
            if (summary.plannedPassed.isNotBlank()) add("计划内通过 ${summary.plannedPassed} 门")
            if (summary.plannedMissed.isNotBlank()) add("计划内未修 ${summary.plannedMissed} 门")
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceLg))
            // FlowRow 而不是一行三枚：小屏（360dp）+ 系统字号放大时，
            // 三枚胶囊加起来会超过卡片内宽 —— 换行比被裁掉或缩成小字好。
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
            ) {
                chips.forEach { ServiceHeroChip(it) }
            }
        }
    }
}

/** 学分完成度环形进度：中心显示百分比，外圈按已获/要求绘制（与总进度条同一套金属金）。 */
@Composable
private fun CreditRing(progress: CreditProgress, modifier: Modifier = Modifier) {
    // 这枚环代表**总进度**（培养方案总学分），所以用总进度金属金、并在弧上走一遍
    // `#FFD700 → #F9A825` 的扫掠渐变 —— 与那条金属条同一个"材质"，两处不会各长一样。
    // 渐变的两个端点都**不透明**（用户要求"去掉任何降低透明度的属性"）。
    val goldBrush = Brush.sweepGradient(
        listOf(MetallicGoldStart, MetallicGoldEnd, MetallicGoldStart)
    )
    val ratio = progress.ratio
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            // 轨道用白 20%：压在深色 hero 上仍看得见"没修完的那一段"，
            // 而不会与金色进度抢注意力。
            drawArc(
                color = Color.White.copy(alpha = 0.20f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (ratio != null) {
                drawArc(
                    brush = goldBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (ratio != null) "${(ratio * 100).roundToInt()}%" else "—",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text("学分", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
@Composable
internal fun CourseStatsCard(summary: AcademicSummary) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd)) {
            Text("培养方案完成情况", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceMd))
            if (summary.plannedTotal.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("计划内总数", summary.plannedTotal, Modifier.weight(1f))
                    // 数值按语义上色：通过=绿、未通过=红、在读=蓝 ——
                    // 三格数字同色时，用户得先读标题才知道哪格是"坏消息"。
                    StatCell("通过", summary.plannedPassed, Modifier.weight(1f), colors.success)
                    StatCell("未通过", summary.plannedFailed, Modifier.weight(1f), colors.danger)
                }
                Spacer(Modifier.height(skin.spaceMd))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("未修", summary.plannedMissed, Modifier.weight(1f))
                    StatCell("在读", summary.plannedIn, Modifier.weight(1f), colors.info)
                    Spacer(Modifier.weight(1f))
                }
            }
            if (summary.unplannedPassed.isNotBlank() || summary.unplannedFailed.isNotBlank()) {
                Spacer(Modifier.height(skin.spaceSm))
                HorizontalDivider(color = Divider, thickness = 0.5.dp)
                Spacer(Modifier.height(skin.spaceSm))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("计划外通过", summary.unplannedPassed, Modifier.weight(1f), colors.success)
                    StatCell("计划外未通过", summary.unplannedFailed, Modifier.weight(1f), colors.danger)
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.ifBlank { "—" },
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor ?: colors.textPrimary
        )
        Spacer(Modifier.height(skin.spaceMicro))
        Text(label, fontSize = 11.sp, color = colors.textTertiary)
    }
}
