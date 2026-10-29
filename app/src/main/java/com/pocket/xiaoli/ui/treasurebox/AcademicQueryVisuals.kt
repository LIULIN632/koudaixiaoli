package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
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
import com.pocket.xiaoli.ui.components.infocard.GradeDistribution
import com.pocket.xiaoli.ui.components.infocard.GradeDistributionChart
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

/**
 * 绩点列的**出场门槛**，量的是**卡片内边距之后**的那一档可用宽。
 *
 * 一门课的课名长到 20 字是常态（「毛泽东思想和中国特色社会主义理论体系概论」）。
 * 360dp 真机上页面左右各 16dp、卡片左右各 12dp，剩下 304dp 里四列数字加状态色条
 * 吃掉 193dp，课程名只剩 111dp ≈ 每行 8 个字 —— 两行装不下，长课名会被省略号裁掉，
 * 而这一行最主要的东西恰恰是课名。
 *
 * 门槛就按"课名至少留 120dp（两行 20 字）"倒推：11 + 182 + 120 = 313dp。
 *
 * ⚠️ 快照机（1080×2340 @ 440dpi ⇒ 393dp 宽）上，树里缩进 20dp 的那张表实测
 * `maxWidth = 312.4dp` —— **只差 0.6dp** 落在门槛外，所以 `143` 那张图里没有绩点列，
 * 而同一台机器上不缩进的 `363` 有（332.4dp）。这不是错列，是这条规则本来就会给的两种结果。
 *
 * 窄屏让掉的是**绩点**：它由成绩按学校换算表推得（90→4.0、85→3.7…），
 * 是这一页唯一"看着成绩就知道"的一列；学分、成绩、状态都不可替代。
 */
private val gradePointColumnMinWidth: Dp = 313.dp

@Composable
internal fun AcademicCourseTable(courses: List<AcademicCourseItem>) {
    if (courses.isEmpty()) return
    val skin = LocalSkin.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceMd)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // 表头与数据行读**同一个**开关：漏一处就是"表头有绩点、行里没有"的错列。
                val showGradePoint = maxWidth >= gradePointColumnMinWidth
                // ⚠️ 这层 Column 不能省：BoxWithConstraints 的作用域是 **BoxScope**，
                // 直接把表头行 / 分隔线 / 数据行摊在里面，它们会**全部叠在同一位置**
                //（第一版就是这么红的：整张表塌成两行高，四行课的文字互相压着）。
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = skin.spaceSm, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("课程", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LocalPocketXiaoliColors.current.textTertiary, modifier = Modifier.weight(1f))
                        TableLabel("学分", CourseColumns.credit)
                        TableLabel("成绩", CourseColumns.score)
                        if (showGradePoint) TableLabel("绩点", CourseColumns.gradePoint)
                        TableLabel("状态", CourseColumns.status)
                    }
                    HorizontalDivider(color = Divider, thickness = 0.5.dp)
                    courses.forEach { course ->
                        AcademicCourseRow(course, showGradePoint)
                    }
                }
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

/**
 * 这门课**对外显示的那个分数**。
 *
 * 教务一行里同时有「成绩」与「最大成绩」两列（补考/重修后前者是原始分、后者是取高分），
 * 表格行、脚注里的"原 X"、以及成绩分布图都读这一个口径 ——
 * 三处各写一遍 `maxGrade.ifBlank { score }`，早晚会有一处漏掉补考。
 */
internal fun AcademicCourseItem.headlineGrade(): String = maxGrade.ifBlank { score }

/**
 * 成绩分布：把这一页所有**已经出分**的课分档。
 *
 * 不做筛选是有意的 —— 在修 / 未修的课没有分数，[GradeDistribution.ofScores] 解析不出来
 * 就自然不计入，图尾那句"共 N 门有分数记录"说的就是这个 N。
 * 手动再挑"已修"反而会把"未过但有 51 分"那门课从图上抹掉。
 */
internal fun gradeDistributionOf(courses: List<AcademicCourseItem>): GradeDistribution =
    GradeDistribution.ofScores(courses.map { it.headlineGrade() })

/**
 * 成绩分布卡（学分构成条回答"还差多少"，这张回答"已修的这些分长什么样"）。
 *
 * 平均分 78 可能是"全都 78"，也可能是"一半 95 一半 60" —— 这一页此前只有前者，
 * 所以画的是**已有的** [GradeDistributionChart]（成绩档案页同一张），不新造图。
 */
@Composable
internal fun AcademicGradeCard(courses: List<AcademicCourseItem>) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val distribution = remember(courses) { gradeDistributionOf(courses) }
    if (distribution.isEmpty) return
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceMd)) {
            Text("成绩分布", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceSm))
            GradeDistributionChart(distribution)
        }
    }
}

@Composable
private fun AcademicCourseRow(item: AcademicCourseItem, showGradePoint: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tone = item.statusTone()
    val toneColor = when (tone) {
        CourseStatusTone.PASSED -> colors.success
        CourseStatusTone.IN_PROGRESS -> colors.info
        CourseStatusTone.FAILED -> colors.danger
        CourseStatusTone.UNTAKEN -> colors.textTertiary
    }
    val headline = item.headlineGrade()
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
        if (showGradePoint) {
            TableValue(item.gradePoint.ifBlank { "—" }, CourseColumns.gradePoint, 12.sp, colors.textSecondary)
        }
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

// ── 学分差额（树行与构成条共用一条规则）─────────────────────────────
//
// 「已获 / 要求」这一对数，页面上有三处要下结论：树里那行的条右端、构成条图例的右端、
// 以及（改造前）树里那枚「还差 X 学分」药丸。三处各写一遍 `required - earned`
// 迟早有一处判反 —— 收成一条规则，三处读它。

/** 差额的结论档位。 */
internal enum class CreditGapKind {
    /** 已获超过要求（超修） */
    OVER,

    /** 一分没有 */
    NONE,

    /** 还差一点 */
    GAP,

    /** 刚好修满 */
    FULL,

    /** 学校没给要求学分（`0.0`）—— 没有分母，谈不上进度 */
    UNLIMITED,
}

/** 一个档位 + 一句人话（`label` 就是它，五档都会说话）。 */
internal data class CreditGap(val kind: CreditGapKind, val label: String)

/**
 * 浮点比较容差：教务给的是 `49.5` 这种一位小数，0.05 足够挡住累加误差，
 * 又不会把"真的差 0.05 学分"糊掉。
 */
private const val CREDIT_EPS = 0.05f

/**
 * 纯函数：把「已获 / 要求」两个文本分数压成一句结论。
 *
 * 判断顺序是有讲究的，两条都出过真实数据：
 *
 * - **超修必须先判**。真实数据里「通识选修课程模块」是 `已获 10.0 / 要求 8.0`，
 *   先算 `required - earned` 得到负数 → 落进"没有缺口"分支 ⇒ 超修在界面上**完全隐身**，
 *   和"刚好修满"长得一模一样（改造前就是这样，渲真实 24 节点树才看出来）。
 * - **一分没有要单列**。「专业核心课程模块」`0.0 / 19.0`：0% 的条只剩轨道，
 *   在页面上和一条分隔线几乎分不开，光靠图形说不清"这块完全没开始"。
 * - **要求为 0 / 拿不到 = 没有分母**：不编百分比，只说"不限"。
 */
internal fun creditGap(earnedCredit: String, requiredCredit: String): CreditGap? {
    val earned = earnedCredit.trim().toFloatOrNull()
    val required = requiredCredit.trim().toFloatOrNull()
    // 要求学分**整串没有**（老部署的页面没这一项）与**学校给了 0.0** 是两件事：
    // 前者是没数据，什么都别说；后者是学校明说"这块不设要求"。
    if (required == null) return null
    if (required <= 0f) {
        return if (earned != null && earned > 0f) CreditGap(CreditGapKind.UNLIMITED, "不限") else null
    }
    val got = earned ?: 0f
    return when {
        got > required + CREDIT_EPS -> CreditGap(CreditGapKind.OVER, "超 ${creditText(got - required)}")
        got <= CREDIT_EPS -> CreditGap(CreditGapKind.NONE, "还没开始")
        required - got <= CREDIT_EPS -> CreditGap(CreditGapKind.FULL, "已满")
        else -> CreditGap(CreditGapKind.GAP, "差 ${creditText(required - got)}")
    }
}

/** 学分统一一位小数（教务原文就是 `53.5` 这种，别让浮点尾巴露出来）。 */
internal fun creditText(value: Float): String = "%.1f".format(value)

/**
 * 差额结论的语义色。
 *
 * ⚠️ "一分没有"**不给 danger**：培养方案里某块还没开始是正常进度，不是故障
 *（`ShortfallPill` 的 KDoc 为同一件事记过一条：这一页把"没修满"画成红色会被读成出错了）。
 * 五档里只有超修 / 修满是绿，缺口与没开始都是橙，"不限"是弱化灰。
 */
@Composable
internal fun creditGapColor(kind: CreditGapKind?): Color {
    val colors = LocalPocketXiaoliColors.current
    return when (kind) {
        CreditGapKind.OVER, CreditGapKind.FULL -> colors.success
        CreditGapKind.GAP, CreditGapKind.NONE -> colors.warning
        else -> colors.textTertiary
    }
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
 * - **只有算实心比例时**才把已获封顶到要求（超修那段会把后面的段挤没）。
 *   ⚠️ 曾经连 `earned` 一起封顶，于是「通识选修 10.0 / 要求 8.0」在图例里显示成
 *   `8.0 / 8.0 已满` —— **把学校给的数改小了**，超修这件事整页都没地方说。
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
            earned = type.earnedCredit.trim().toFloatOrNull() ?: 0f,
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
                        "${creditText(segment.earned)} / ${creditText(segment.required)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                        // 与课程表同一条规矩：数字列**定宽 + 右对齐**。
                        // 左对齐时「49.5 / 53.5」与「0.0 / 19.0」起点齐、终点不齐，
                        // 两行的"差多少"就各站各的位置，扫不下去。
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(skin.spaceSm))
                    // 与树里那一行读同一条规则（`creditGap`），两处不会各说各话
                    val gap = creditGap(creditText(segment.earned), creditText(segment.required))
                    Text(
                        gap?.label.orEmpty(),
                        fontSize = 11.sp,
                        color = creditGapColor(gap?.kind),
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(62.dp),
                    )
                }
            }
        }
    }
}
