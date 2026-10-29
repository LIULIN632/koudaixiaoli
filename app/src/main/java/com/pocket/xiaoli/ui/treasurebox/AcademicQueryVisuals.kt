package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.network.AcademicCourseItem
import com.pocket.xiaoli.network.AcademicTypeInfo
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.infocard.color
import com.pocket.xiaoli.ui.components.infocard.scoreBandOf
import com.pocket.xiaoli.ui.theme.Divider
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 修课情况页的**两张新图形**：课程表 + 学分构成条。
 *
 * 单独成文件是因为 `AcademicQueryScreen.kt` 已经 1260 行（早过了本项目 800 行的门槛），
 * 再往里加 300 行只会让"改一处要看全页"的成本继续涨。
 *
 * 两个都是纯入参组件（吃 `List<…>`，不碰 ViewModel / `AppContainer`），
 * 所以 `AcademicQueryScreenshotTest` 能直接渲它们。
 */

// ── 课程表 ───────────────────────────────────────────────────────────────
//
// 改造前一门课一张卡（左色条 + 20sp 大字成绩 + 两行小字），41 门课就是 41 张卡，
// 要滚四屏多才看得完，而且**数字没法横向比** —— 每行的学分/成绩/绩点都在不同列位置上。
//
// 表格解决的就是这两件事：一行一门（高度约为原来的四成），列固定右对齐，
// 扫一眼就能找出"哪门分低""哪门学分大"。
//
// ⚠️ 列宽只在这里定义一次（[CourseColumns]），表头与数据行都读它 ——
// 表头和数据错列是最难发现的排版 bug（数字看着都对，就是不对齐）。

private object CourseColumns {
    /**
     * 数字列一律**右对齐**，宽度里含列间距。
     *
     * ⚠️ 宽度不能只按内容量：第一版按"2.0 / 83 / 3.3"的实际字宽给（34/48/34），
     * 渲出来成绩与绩点粘成了「833.3」—— 表格的列感全靠**列间那点空隙**，
     * 数字本身右对齐、左边留白，读起来才是"一列一列"。
     */
    val credit: Dp = 40.dp
    val score: Dp = 54.dp
    val gradePoint: Dp = 42.dp
    val status: Dp = 46.dp
}

@Composable
internal fun AcademicCourseTable(courses: List<AcademicCourseItem>) {
    if (courses.isEmpty()) return
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceMd)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = skin.spaceSm, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("课程", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textTertiary, modifier = Modifier.weight(1f))
                TableLabel("学分", CourseColumns.credit)
                TableLabel("成绩", CourseColumns.score)
                TableLabel("绩点", CourseColumns.gradePoint)
                TableLabel("状态", CourseColumns.status)
            }
            HorizontalDivider(color = Divider, thickness = 0.5.dp)
            courses.forEach { course ->
                AcademicCourseRow(course)
            }
        }
    }
}

@Composable
private fun TableLabel(text: String, width: Dp) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalPocketXiaoliColors.current.textTertiary,
        textAlign = TextAlign.End,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun AcademicCourseRow(item: AcademicCourseItem) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tone = item.statusTone()
    val toneColor = when (tone) {
        CourseStatusTone.PASSED -> colors.success
        CourseStatusTone.IN_PROGRESS -> colors.info
        CourseStatusTone.FAILED -> colors.danger
        CourseStatusTone.UNTAKEN -> colors.textTertiary
    }
    val headline = item.maxGrade.ifBlank { item.score }
    // 补考/重修与"成绩≠最大成绩"是同一类信息（这门课不止一次），压成成绩下面的一行小字，
    // 而不是再开一列 —— 列数一多，课程名就先被挤没了，而那才是这行最主要的东西。
    val footnotes = buildList {
        if (item.maxGrade.isNotBlank() && item.score.isNotBlank() && item.score != item.maxGrade) {
            add("原 ${item.score}")
        }
        if (item.makeupScore.isNotBlank()) add("补 ${item.makeupScore}")
        if (item.retakeScore.isNotBlank()) add("重 ${item.retakeScore}")
    }.joinToString(" ")
    // 名称下的第二行只放**归属与时间**：性质（必修/选修）+ 实际学期；
    // 没修过的课没有实际学期，就显示学校的建议学期 —— 那才是它能提供的增量信息。
    val subLine = listOf(
        item.nature.takeIf { it.isNotBlank() },
        item.term.takeIf { it.isNotBlank() } ?: item.suggestedTerm.takeIf { it.isNotBlank() }?.let { "建议 $it" },
    ).filterNotNull().joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧状态色条：与改造前的卡片同一套语言（滚一列时颜色比小字更快被认出来）。
        // ⚠️ 父 Row 必须是 IntrinsicSize.Min，否则 `fillMaxHeight()` 在无限高约束下画成 0 高。
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(toneColor)
        )
        Spacer(Modifier.width(skin.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.courseName.ifBlank { "未知课程" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subLine.isNotBlank()) {
                Text(subLine, fontSize = 10.sp, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TableValue(item.credit.ifBlank { "—" }, CourseColumns.credit, 12.sp, colors.textSecondary)
        Column(
            modifier = Modifier.width(CourseColumns.score),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                headline.ifBlank { "—" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                // 分数按档上色（与「成绩查询」同一套分档），灰底黑字分不出 51 和 91
                color = if (headline.isBlank()) colors.textTertiary else scoreBandOf(headline).color(),
                maxLines = 1,
            )
            if (footnotes.isNotBlank()) {
                Text(footnotes, fontSize = 9.sp, color = colors.textTertiary, maxLines = 1)
            }
        }
        TableValue(item.gradePoint.ifBlank { "—" }, CourseColumns.gradePoint, 12.sp, colors.textSecondary)
        Box(modifier = Modifier.width(CourseColumns.status), contentAlignment = Alignment.CenterEnd) {
            if (item.status.isNotBlank()) {
                Text(
                    item.status,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = toneColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(toneColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TableValue(text: String, width: Dp, size: TextUnit, color: Color) {
    Text(
        text,
        fontSize = size,
        color = color,
        // ⚠️ 必须 End：默认 Start 会把数字顶到列的左边缘，于是"成绩"和"绩点"
        // 两列粘成「833.3」—— 渲第一版时就是这样，列感全靠右对齐留出的左白。
        textAlign = TextAlign.End,
        modifier = Modifier.width(width),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ── 学分构成条 ───────────────────────────────────────────────────────────
//
// 这一页原先只有"每个类型一条自己的进度条"，于是**看不出相对分量**：
// 「通识教育平台 49.5/53.5」和「专业核心 0/19」两条都缺，但缺 4 学分和缺 19 学分
// 在各自满宽的条上长得一模一样。这张条把各平台按**要求学分**排进同一根横杠里，
// 段宽 = 这块占毕业要求多少，段内实心 = 已获多少 —— 一眼能答"该先补哪块"。

/** 构成条里的一段（一个平台）。 */
internal data class CreditSegment(
    val name: String,
    val required: Float,
    val earned: Float,
    val accent: ServiceAccent,
) {
    /** 已获占比。分母为 0 时返回 null（不参与绘制，见 [creditSegments]）。 */
    val ratio: Float? get() = if (required > 0f) (earned / required).coerceIn(0f, 1f) else null
}

/** 段色循环用的类目色板 —— 与「服务」页同一套语义色，跟着主题色相走，不新造颜色。 */
private val segmentPalette = listOf(
    ServiceAccent.ACADEMIC,
    ServiceAccent.QUERY,
    ServiceAccent.LIFE,
    ServiceAccent.PAYMENT,
    ServiceAccent.STUDY,
    ServiceAccent.INFO,
)

/**
 * 纯函数：从培养方案里取出**参与构成的平台层**，算出每段该多宽、实心该多长。
 *
 * 三条规则都是"宁可不画，也不编"：
 *
 * - 只取 `depth == 1`（平台层）。叶子层的和会重复计入父层，加起来就不是 100% 了；
 * - **拿不到要求学分的节点直接跳过**（真实数据里「其他选修课程」是 `已获 4.0 / 要求 0.0`），
 *   按 0 宽画就是把它从毕业要求里抹掉，按平均宽画更是编数据；
 * - 已获**封顶到要求**：超修是好事，但让一段超过 100% 会把后面的段挤没。
 */
internal fun creditSegments(types: List<AcademicTypeInfo>): List<CreditSegment> {
    val platforms = types.filter { it.depth == 1 }
    if (platforms.isEmpty()) return emptyList()
    return platforms.mapIndexed { index, type ->
        val required = type.requiredCredit.trim().toFloatOrNull() ?: return@mapIndexed null
        if (required <= 0f) return@mapIndexed null
        CreditSegment(
            name = type.name,
            required = required,
            earned = (type.earnedCredit.trim().toFloatOrNull() ?: 0f).coerceIn(0f, required),
            accent = segmentPalette[index % segmentPalette.size],
        )
    }.filterNotNull()
}

@Composable
internal fun CreditCompositionBar(segments: List<CreditSegment>) {
    if (segments.size < 2) return
    // 段宽要有个总参照：所有平台的要求学分之和（不等于根节点的总数 ——
    // 根上还有没被平台覆盖的部分，这里只说"这几块之间怎么分"）
    val totalRequired = segments.sumOf { it.required.toDouble() }.toFloat()
    if (totalRequired <= 0f) return

    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val earnedTotal = segments.sumOf { it.earned.toDouble() }.toFloat()
    val barShape = RoundedCornerShape(4.dp)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd)) {
            Text(
                "学分构成",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                "段宽 = 要求学分占比 · 实心 = 已获 · 合计 ${"%.1f".format(earnedTotal)} / ${"%.1f".format(totalRequired)}",
                fontSize = 11.sp,
                color = colors.textTertiary,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(skin.spaceSm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(barShape)
                    .border(1.dp, colors.borderDivider, barShape),
            ) {
                segments.forEach { segment ->
                    val tint = serviceAccentColor(segment.accent)
                    Column(
                        modifier = Modifier
                            .weight(segment.required)
                            .fillMaxHeight()
                            // 缺口段用**类目色的淡调**（lerp 出的是不透明色），而不是统一的轨道色：
                            // 第一版所有段的缺口都是同一种浅灰，两根段拼在一起看着就是
                            // "一根条挖了个洞"，分不出这是两块不同的要求。
                            .background(lerp(colors.surfaceHighlight, tint, 0.22f)),
                    ) {
                        // 实心段用**类目色**，缺口留淡调 —— 两段都是实心色就分不出"到手"和"还差"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(segment.ratio ?: 0f)
                                .weight(1f)
                                .background(tint)
                        )
                    }
                }
            }
            Spacer(Modifier.height(skin.spaceMd))
            segments.forEach { segment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(serviceAccentColor(segment.accent))
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    Text(
                        segment.name,
                        fontSize = 12.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${"%.1f".format(segment.earned)} / ${"%.1f".format(segment.required)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    val gap = segment.required - segment.earned
                    Text(
                        if (gap > 0.05f) "差 ${"%.1f".format(gap)}" else "已满",
                        fontSize = 11.sp,
                        color = if (gap > 0.05f) colors.warning else colors.success,
                        modifier = Modifier.width(50.dp),
                    )
                }
            }
        }
    }
}
