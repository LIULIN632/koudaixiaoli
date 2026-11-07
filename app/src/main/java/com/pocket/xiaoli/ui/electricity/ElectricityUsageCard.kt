package com.pocket.xiaoli.ui.electricity

import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.electricity.UsageStats
import com.pocket.xiaoli.data.electricity.trendCoordinates
import com.pocket.xiaoli.data.electricity.trendLineY
import com.pocket.xiaoli.data.electricity.usageFootnote
import com.pocket.xiaoli.data.electricity.usageNotice
import com.pocket.xiaoli.data.electricity.usageStatLines
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.infocard.InfoRow
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.serviceAccentColor

/**
 * 电费页的**用量趋势卡**（2026-11-06 从 `ElectricityContent.kt` 搬来）。
 *
 * 搬的理由很实在：那个文件加上这张卡就到 833 行，越过 `tools/lint_design.py` 的 800 行门槛
 *（那个数字只许降不许升，抹掉它不算改进）。同包拆分，调用点一行都不用改。
 *
 * 这一整段只吃 [UsageStats]，不碰网络也不碰存储 —— 算法与口径都在
 * `data/electricity/ElectricityUsage.kt`，这里只负责把它画出来、说出来。
 */

// ── 用量趋势（本机采样推的，不是上游给的）────────────────────────────────

/**
 * 用量统计卡。
 *
 * ## 为什么这张图上所有数字都要带口径
 *
 * 上游只有「实时余额」一个数，没有用电量、没有抄表、没有充值流水。
 * 于是"日均多少""还能用几天"**全是我自己从每日采样推的**（见
 * [com.pocket.xiaoli.data.electricity.usageStatsOf]）。推算就该写成推算：
 * 窗口几天、充值的日子算不算、为什么可能看不出来 —— 都要在卡上说出来，
 * 而不是用一个漂亮的折线让人以为这是后勤给的数据。
 */
@Composable
internal fun UsageCard(
    usage: UsageStats,
    threshold: Double,
    onClear: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val notice = usageNotice(usage)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(skin.cardPaddingLarge),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "用量趋势",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("清空", fontSize = 13.sp) }
            }

            if (notice != null) {
                // 数据不够 → 一句话，**不画任何东西**。
                // 一条贴底的假线读起来正好就是"钱快用光了"，那是拿图形骗人。
                Text(
                    text = notice,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary,
                )
            } else {
                UsageTrendCanvas(usage = usage, threshold = threshold, modifier = Modifier.fillMaxWidth().height(112.dp))
                usageStatLines(usage, threshold).forEach { (label, value) -> InfoRow(label, value) }
                // 口径脚注只在**真的推算了**的时候出现：
                // 还没攒够数据时来一句"按本机 1 天记录推算"，是在宣布一个没做过的推算。
                Text(
                    text = usageFootnote(usage),
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * 折线本体：面积 + 线 + 采样点 + 提醒线。
 *
 * 自绘 `Canvas`，不引图表库 —— 与 `ui/focus/FocusScreen.kt` 的番茄钟环同一条惯例
 *（本项目不收第三方 UI 依赖）。
 *
 * ⚠️ 图里**不画文字**：坐标数值用卡上的 `InfoRow` 给。
 * 一是 `drawText` 在 Robolectric 的 NATIVE 渲染下与真机字距不一致（基线会假红），
 * 二是系统字体放大时 Canvas 里的文字会被裁掉，而 Text 会自己换行。
 */
@Composable
private fun UsageTrendCanvas(
    usage: UsageStats,
    threshold: Double,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val accent = serviceAccentColor(ServiceAccent.ENERGY)
    val coords = trendCoordinates(usage, extraLine = threshold) ?: return
    val lineY = trendLineY(threshold, usage)
    val rechargeDays = usage.rechargeDays.toSet()

    Canvas(modifier) {
        val pad = ChartPadding.toPx()
        val plotW = size.width - pad * 2f
        val plotH = size.height - pad * 2f
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        val points = coords.map { Offset(pad + it.first * plotW, pad + it.second * plotH) }
        val baseline = pad + plotH

        // 面积：给"还剩多少"一个体积感。渐变到底部近乎透明，避免盖住提醒线。
        drawPath(
            path = Path().apply {
                moveTo(points.first().x, baseline)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, baseline)
                close()
            },
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.02f)),
            ),
        )

        // 提醒线（虚线）：这张图真正要回答的是"离没钱还有多远"。
        lineY?.let { fraction ->
            val y = pad + fraction * plotH
            drawLine(
                color = colors.warning.copy(alpha = 0.8f),
                start = Offset(pad, y),
                end = Offset(pad + plotW, y),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        drawPath(
            path = Path().apply {
                points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                }
            },
            color = accent,
            style = Stroke(width = 2.dp.toPx()),
        )

        points.forEachIndexed { index, point ->
            val recharged = usage.points[index].epochDay in rechargeDays
            // 充值那天画成空心环而不是实心点：它**不是**用量，混成实心点就会被读成"那天用了钱"。
            drawCircle(
                color = if (recharged) colors.warning else accent,
                radius = if (recharged) 4.5.dp.toPx() else 2.4.dp.toPx(),
                center = point,
                style = if (recharged) Stroke(1.6.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill,
            )
        }
    }
}

private val ChartPadding = 10.dp
