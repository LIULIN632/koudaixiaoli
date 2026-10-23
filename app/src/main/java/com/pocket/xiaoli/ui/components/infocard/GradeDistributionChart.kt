package com.pocket.xiaoli.ui.components.infocard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 成绩分布条 —— 用**横向条**而不是柱状图。
 *
 * ## 为什么是横条
 *
 * 手机竖屏宽度有限、高度宝贵。竖向柱状图要预留轴标签与刻度空间，
 * 4 个档位在窄屏上会显得局促；横条把「档位名 + 条 + 数量」压成一行，
 * 信息密度更高、天然可读，也不需要画坐标轴。
 *
 * ## 为什么不用 Canvas
 *
 * `Box` + `fillMaxWidth(fraction)` 就能表达，且**不需要手动测量** ——
 * 自动适配横竖屏与系统字体缩放。手绘 Canvas 反而要自己算宽度，
 * 在字体放大时容易错位。
 *
 * ## 动效
 *
 * 条长用 `animateFloatAsState` 从 0 长到目标值，让"数据出现"有过渡，
 * 而不是啪一下填满。400ms 比页面转场慢一点，读起来像"在统计"。
 */
@Composable
fun GradeDistributionChart(
    distribution: GradeDistribution,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    if (distribution.isEmpty) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        distribution.buckets.forEach { bucket ->
            val fraction = bucket.count.toFloat() / distribution.maxCount
            val animated by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(400),
                label = "gradeBar_${bucket.label}",
            )
            val barColor = bucket.band.color()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bucket.label,
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    modifier = Modifier.width(46.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight)
                        .clip(RoundedCornerShape(50))
                        .background(colors.surfaceHighlight),
                ) {
                    if (bucket.count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animated.coerceIn(0f, 1f))
                                .height(barHeight)
                                .clip(RoundedCornerShape(50))
                                .background(barColor),
                        )
                    }
                }
                Spacer(Modifier.width(skin.spaceSm))
                Text(
                    text = bucket.count.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    // 数量为 0 时用弱色，避免右列贴一排"0"抢注意力
                    color = if (bucket.count > 0) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.width(24.dp),
                )
            }
        }

        // 说明统计口径：让用户知道"少于 N 门"与"有分数没录"的区别
        Text(
            text = "共 ${distribution.scored} 门有分数记录",
            fontSize = 11.sp,
            color = colors.textTertiary,
        )
    }
}
