package com.pocket.xiaoli.ui.focus

// ═══════════════════════════════════════════════════════════════════════
// 统计区：概览数字 + 打卡热力图 + 本周柱状图 + 当日分段图
//
// 专注页与「我的」页**共用这一个组件**：口径只有一份，两个页面的数字不会打架。
//
// 四块都是**纯摆放**：算数全在 `focus/FocusStats.kt` 那几个纯函数里
//（窗口边界、番茄数口径、时长占比、热力图分档），这里只负责把数字画出来。
// 所以这一层不需要单测，要测的是那边的纯函数；这里靠基准图看观感。
//
// ⚠️ 子组件一律 `internal`：整页渲不出来（要 `AppContainer` 建 ViewModel），
// 基准图只能录这些子组件的摆放影子 —— 见 `memory/02-feature-map.md` 里
// 首页 `HomeAddTodoFab` 那次教训。
//
// ⚠️ 不引第三方图表库：项目没有图表依赖，两个图也不需要（柱状图 = 几个 Box，
// 分段图 = 一条按权重切分的横条）。
// ═══════════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.focus.FocusDayStat
import com.pocket.xiaoli.focus.FocusHeatCell
import com.pocket.xiaoli.focus.FocusSegment
import com.pocket.xiaoli.focus.FocusStatsView
import com.pocket.xiaoli.focus.humanDuration
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import java.time.LocalDate

/**
 * 统计区。自上而下：概览数字、打卡热力图、本周柱状图、当日分段图。
 *
 * 一周与今天都还没有任何记录时**只留一句话，不画空框** —— 一个全是 0 的图表占着屏幕，
 * 用户第一反应是"是不是坏了"（对齐推广位「空态 → 整块不出现」的取向）。
 */
@Composable
internal fun FocusStatsSection(
    stats: FocusStatsView,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    if (!stats.hasData) {
        FocusStatsEmpty(modifier = modifier)
        return
    }
    val weekFocusMs = stats.weekStats.sumOf { it.focusMs }
    val weekPomodoros = stats.weekStats.sumOf { it.completedPomodoros }
    Column(modifier = modifier.fillMaxWidth()) {
        FocusSummaryCard(
            streakDays = stats.streakDays,
            weekMs = stats.weekMs,
            monthMs = stats.monthMs,
        )
        Spacer(Modifier.height(skin.spaceMd))
        FocusHeatmap(cells = stats.heatmap)
        // 一周全是 0、但今天有过段（比如只跑了几十秒就放弃）时，柱状图没有信息可画。
        if (weekFocusMs > 0L || weekPomodoros > 0) {
            Spacer(Modifier.height(skin.spaceMd))
            FocusWeekChart(stats = stats.weekStats)
        }
        if (stats.todaySegments.isNotEmpty()) {
            Spacer(Modifier.height(skin.spaceMd))
            FocusTodaySegments(segments = stats.todaySegments)
        }
    }
}

/** 空态：一句话，不画框。 */
@Composable
internal fun FocusStatsEmpty(modifier: Modifier = Modifier) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        text = "跑完第一段专注，这里会出现你的专注统计。",
        style = PocketTypography.caption,
        color = colors.textTertiary,
        modifier = modifier,
    )
}

/**
 * 概览：连续打卡天数 + 近 7 天 / 近 30 天合计。
 *
 * 用的是**滚动窗口**（含今天往前 7 / 30 天），不是"日历周 / 日历月"：柱状图那边已经是
 * "最近 7 天"的口径，两处若各说各的，用户会看到两个都叫"本周"却不一样的数。
 */
@Composable
internal fun FocusSummaryCard(
    streakDays: Int,
    weekMs: Long,
    monthMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
        ) {
            // 加了标题才跟下面几张卡一致（「打卡热力图」「最近 7 天」「今天怎么攒的」都有），
            // 不然在「我的」页上它是一张没头没尾的卡，不知道这仨数讲的是什么。
            Text(
                text = "专注统计",
                style = PocketTypography.cardTitle,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(skin.spaceMd))
            // 连续打卡是这个卡片里唯一"有情绪"的数（今天有没有接上），给它强调色；
            // 另外两项是累计时长，用正文色就够。
            summaryRow("连续打卡", if (streakDays > 0) "$streakDays 天" else "还没有", colors.accentMain)
            Spacer(Modifier.height(skin.spaceSm))
            summaryRow("近 7 天", durationText(weekMs), colors.textPrimary)
            Spacer(Modifier.height(skin.spaceSm))
            summaryRow("近 30 天", durationText(monthMs), colors.textPrimary)
        }
    }
}

/** 概览里的一行：左标签右数值。[valueColor] 用来把"连续打卡"那一行拎出来。 */
@Composable
private fun summaryRow(label: String, value: String, valueColor: Color) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = PocketTypography.caption, color = colors.textSecondary)
        Text(value, style = PocketTypography.cardTitle, color = valueColor)
    }
}

/**
 * 打卡热力图：一周一列、周一到周日一行，共 12 列（≈ 一个季度）。
 *
 * ## 为什么不按"最近 12 周里的最大值"归一化
 *
 * 那样同一格的颜色会随之后的数据变化（今天多跑一小时，历史全变浅），
 * "这周比上周如何"就没法看了。这里按**绝对分钟**分档（见 `FocusStats.heatLevel`），
 * 颜色只由那天自己的时长决定。
 *
 * 未来那几格（本周还没到的日子）画成透明，与"过去但没专注"（淡底色）区分开：
 * 两者混在一起，用户看到的是假的空白。
 */
@Composable
internal fun FocusHeatmap(cells: List<FocusHeatCell>, modifier: Modifier = Modifier) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val weeks = cells.chunked(7)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("打卡热力图", style = PocketTypography.cardTitle, color = colors.textPrimary)
                Text(
                    text = "最近 ${weeks.size} 周",
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(skin.spaceMd))

            // 格子尺寸按**可用宽度现算**（而不是写死 16dp）：写死的话在 393dp 的常见机型上
            // 整个网格只占卡片的三分之二，右边空出一大片，看着像没画完。
            // 上下限是为了平板（太大会变成色块）与 320dp 窄屏（太小看不清）。
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = weeks.size
                val gridWidth = maxWidth - HeatLabelWidth - skin.spaceSm
                val cell = if (columns <= 0) {
                    HeatCellSize
                } else {
                    val gapTotal = HeatGap * (columns - 1)
                    ((gridWidth - gapTotal) / columns).coerceIn(MinHeatCell, MaxHeatCell)
                }
                Row(verticalAlignment = Alignment.Top) {
                    // 星期标签只写"一/三/五/日"四行：7 行全写会挤掉格子的宽度
                    Column(verticalArrangement = Arrangement.spacedBy(HeatGap)) {
                        WEEKDAY_LABELS.forEachIndexed { index, label ->
                            Box(
                                modifier = Modifier
                                    .width(HeatLabelWidth)
                                    .height(cell),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (index % 2 == 0) {
                                    Text(
                                        text = label,
                                        style = PocketTypography.label,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(skin.spaceSm))
                    // "今天"就是最后一格有日期的（未来那几格 date 为 null）。
                    // 用数据自己判断而不是调 `LocalDate.now()`：基准图里的日期是固定的，
                    // 用真实时钟的话这条线在图上永远不出现，等于没验。
                    val today = cells.lastOrNull { it.date != null }?.date
                    Row(horizontalArrangement = Arrangement.spacedBy(HeatGap)) {
                        weeks.forEach { week ->
                            Column(verticalArrangement = Arrangement.spacedBy(HeatGap)) {
                                week.forEach { day ->
                                    HeatBox(
                                        level = day.level,
                                        present = day.date != null,
                                        size = cell,
                                        today = day.date != null && day.date == today,
                                    )
                                }
                                // 最后一列可能不满 7 格（不该发生：heatmap 总是补齐整周），兜一手
                                repeat(7 - week.size) {
                                    Box(Modifier.size(cell))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(skin.spaceSm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("少", style = PocketTypography.label, color = colors.textTertiary)
                Spacer(Modifier.width(skin.spaceXs))
                (1..4).forEach { level ->
                    HeatBox(level = level, present = true, size = LegendCellSize)
                    Spacer(Modifier.width(skin.spaceMicro))
                }
                Text("多", style = PocketTypography.label, color = colors.textTertiary)
                Spacer(Modifier.weight(1f))
                Text("每格一天", style = PocketTypography.label, color = colors.textTertiary)
            }
        }
    }
}

/**
 * 热力图的一格（图例也用同一个）。
 *
 * [today] 给今天那格描一圈：84 格里不标一下，用户得从右下角往回数才知道自己在哪
 *（描边用中性色而不是强调色 —— 今天正好跑满一档时，强调色的圈会跟格子同色，等于没画）。
 */
@Composable
private fun HeatBox(level: Int, present: Boolean, size: Dp = HeatCellSize, today: Boolean = false) {
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(HeatCorner))
            .background(heatColor(level, present, colors.accentMain, colors.borderDivider))
            .then(
                if (today) {
                    Modifier.border(1.dp, colors.textTertiary, RoundedCornerShape(HeatCorner))
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * 最近 7 天的番茄数柱状图。
 *
 * 纵轴不画刻度：7 根柱子旁边各写一个数，读数比"数格子"快。
 * 横轴标签用**固定表**（一/二/三…）而不是 locale 取星期名 —— 基准图不该随系统语言变。
 */
@Composable
internal fun FocusWeekChart(
    stats: List<FocusDayStat>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val maxCount = stats.maxOfOrNull { it.completedPomodoros } ?: 0
    val totalPomodoros = stats.sumOf { it.completedPomodoros }
    val totalFocusMs = stats.sumOf { it.focusMs }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("最近 7 天", style = PocketTypography.cardTitle, color = colors.textPrimary)
                Text(
                    text = "$totalPomodoros 个 · ${durationText(totalFocusMs)}",
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(skin.spaceMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                // 柱子之间留缝，靠 bottom 对齐让 7 根柱子的**基线**在同一条线上
                horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                stats.forEachIndexed { index, stat ->
                    // 窗口是"最近 7 天"（升序），所以最后一根就是今天：
                    // 它用满色，其余淡一档 —— 不然 7 根柱子一模一样，得先数标签才知道哪根是今天。
                    val isToday = index == stats.lastIndex
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            // 0 的那天不写"0"：一屏 7 个"0"只是噪音，空着反而看得清哪几天有
                            text = if (stat.completedPomodoros > 0) {
                                stat.completedPomodoros.toString()
                            } else {
                                ""
                            },
                            style = PocketTypography.label,
                            color = if (isToday) colors.textPrimary else colors.textSecondary,
                        )
                        Spacer(Modifier.height(skin.spaceXs))
                        FocusBar(
                            fraction = if (maxCount <= 0) {
                                0f
                            } else {
                                stat.completedPomodoros.toFloat() / maxCount
                            },
                            color = if (isToday) {
                                colors.accentMain
                            } else {
                                colors.accentMain.copy(alpha = PastBarAlpha)
                            },
                        )
                        Spacer(Modifier.height(skin.spaceXs))
                        Text(
                            text = dayLabel(stat.date),
                            style = PocketTypography.label,
                            color = if (isToday) colors.textSecondary else colors.textTertiary,
                            fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 当日分段图：今天每一段按**时长占比**画成一条横条（专注段用强调色，休息段用分隔色）。
 *
 * 为什么不是"在一天里的位置"（横轴 00:00→24:00）：一段 25 分钟的专注只占全天的 1.7%，
 * 画在 24 小时横轴上就是几个像素，竖屏手机直接被缩没。横条总长代表今天的总时长，
 * 每段按自己的时长得宽度 —— 专注与休息的配比一眼可见（详见 `FocusStats.segmentsOfDay`）。
 */
@Composable
internal fun FocusTodaySegments(
    segments: List<FocusSegment>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val focusMs = segments.filter { !it.phase.isBreak }.sumOf { it.actualMs }
    val breakMs = segments.filter { it.phase.isBreak }.sumOf { it.actualMs }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(skin.spaceLg),
        ) {
            Text("今天怎么攒的", style = PocketTypography.cardTitle, color = colors.textPrimary)
            Spacer(Modifier.height(skin.spaceMd))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StripHeight)
                    .clip(RoundedCornerShape(StripHeight / 2)),
            ) {
                segments.forEach { segment ->
                    Box(
                        modifier = Modifier
                            .weight(segment.fraction)
                            .fillMaxHeight()
                            .background(segmentColor(segment, colors.accentMain, colors.borderDivider)),
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceSm))
            Text(
                text = "专注 ${durationText(focusMs)} · 休息 ${durationText(breakMs)}",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
        }
    }
}

/** 一根柱子。0 也画一小截底线，好让横轴看得出来（而不是"这一根什么都没有"）。
 *
 * 圆角**只给上面两个角**：以前四角都是 `BarWidth / 2`，番茄数为 1 的那天（`BarMaxHeight / 3`）渲出来是个圆点，
 * 不像柱子（录图复核时发现的）。底面留直角，柱子才像"立"在那条基线上。
 */
@Composable
private fun FocusBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalPocketXiaoliColors.current
    val ratio = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .width(BarWidth)
            .height(if (ratio <= 0f) BarMinHeight else BarMaxHeight * ratio)
            .clip(RoundedCornerShape(topStart = BarCorner, topEnd = BarCorner))
            .background(if (ratio <= 0f) colors.borderDivider else color),
    )
}

/** 一段的颜色：专注跑满 = 强调色；专注没跑满 = 淡一点的强调色；休息 = 分隔色。 */
private fun segmentColor(segment: FocusSegment, accent: Color, divider: Color): Color = when {
    segment.phase.isBreak -> divider
    segment.completed -> accent
    else -> accent.copy(alpha = 0.45f)
}

/**
 * 热力图一格的颜色。四档都是**强调色压透明度**（不是另调四个颜色），
 * 这样换皮肤（含深色）时整条色阶跟着强调色走，不会出现某档在深色下看不清。
 */
private fun heatColor(level: Int, present: Boolean, accent: Color, track: Color): Color = when {
    !present -> Color.Transparent
    level <= 0 -> track
    // 1 档给到 0.30 而不是 0.22：深色皮肤下 0 档（`borderDivider` 的深灰）与 1 档
    // 相差太小时，"今天没练"和"练了一会儿"就分不出来了（录图复核时发现）。
    level == 1 -> accent.copy(alpha = 0.30f)
    level == 2 -> accent.copy(alpha = 0.50f)
    level == 3 -> accent.copy(alpha = 0.72f)
    else -> accent
}

/** 时长文案。0 说"0 分钟"而不是 `humanDuration(0)` 的"0 秒"（空态里"0 秒"像刚跑过一段）。 */
private fun durationText(ms: Long): String = if (ms <= 0L) "0 分钟" else humanDuration(ms)

/** 横轴标签：一/二/三…日。`dayOfWeek.value` 是 1(周一)..7(周日)。 */
private fun dayLabel(date: LocalDate): String =
    WEEKDAY_LABELS[(date.dayOfWeek.value - 1).coerceIn(0, WEEKDAY_LABELS.lastIndex)]

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 柱子的最大高度（番茄数最多的那天）；一根柱子的宽度与分段图横条的厚度。 */
private val BarMaxHeight = 56.dp
private val BarMinHeight = 3.dp
private val BarWidth = 14.dp
private val BarCorner = 4.dp
private val StripHeight = 14.dp

/** 柱状图里"不是今天"那几根的颜色透明度：比今天淡一档，但仍看得出是同一族数据。 */
private const val PastBarAlpha = 0.55f

/**
 * 热力图的格子尺寸。宽度够时按可用宽度撑满（见 [FocusHeatmap] 里的 `BoxWithConstraints`），
 * 这两个是上下限：上限防平板变成色块，下限防 320dp 窄屏挤成看不清的小点。
 */
private val MinHeatCell = 12.dp
private val MaxHeatCell = 26.dp
private val HeatCellSize = 16.dp
private val HeatGap = 3.dp
private val HeatLabelWidth = 14.dp
private val HeatCorner = 3.dp
private val LegendCellSize = 10.dp
