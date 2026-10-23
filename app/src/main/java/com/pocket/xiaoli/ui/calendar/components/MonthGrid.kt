package com.pocket.xiaoli.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.calendar.EventKind
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.util.DayMark
import com.pocket.xiaoli.util.MonthCell
import java.time.LocalDate

/**
 * 格子高度 = 44dp。
 *
 * 这个数是**触控下限**（`TouchTarget.MinTouchTarget`），不是美学选择。
 * B 版式格子里要放「日期 + 一行小字」，38dp 排得下，但会踩 `tools/lint_design.py` 的 touch 规则；
 * 日历格子本来就该有 44dp 的体量，用 `invisibleTouchTarget()` 把 38dp 的热区撑起来
 * 只是把布局问题藏起来 —— 而且相邻格子会互相抢热区（`TouchTarget.kt` 里专门讲过这条）。
 */
internal val CalendarCellHeight: Dp = 44.dp

private val WEEK_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 月历网格（B 版式：格内小字）。
 *
 * 只负责画 —— 哪天显示什么由 [markOf] 决定，而它来自 `CalendarUtils.dayMark`（纯函数，可单测）。
 * 组件自己不碰校历资产、不碰节假日列表：同一天的小字与「是不是调休」必须同源，
 * 而那件事在 `DayMark` 里已经绑好了。
 *
 * 行数由 [cells] 决定，**不写死 6 行**：一个正好从周一开始的 28 天月只占 4 行。
 */
@Composable
internal fun MonthGrid(
    cells: List<MonthCell>,
    today: LocalDate,
    selected: LocalDate?,
    markOf: (LocalDate) -> DayMark?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderDivider),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = skin.spaceSm)
        ) {
            Row(Modifier.fillMaxWidth()) {
                WEEK_HEADERS.forEach { header ->
                    Text(
                        text = header,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            isToday = cell.date == today,
                            isSelected = cell.date == selected,
                            mark = if (cell.inMonth) markOf(cell.date) else null,
                            onSelect = onSelect,
                        )
                    }
                    // 末行不足 7 格时补空位，否则最后几个格子会被拉宽
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    cell: MonthCell,
    isToday: Boolean,
    isSelected: Boolean,
    mark: DayMark?,
    onSelect: (LocalDate) -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val onAccent = onAccentColor()

    val discBackground = when {
        isSelected -> colors.accentMain
        isToday -> colors.surfaceHighlight
        else -> Color.Transparent
    }
    val dateColor = when {
        isSelected -> onAccent
        !cell.inMonth -> colors.textTertiary.copy(alpha = 0.45f)
        isToday -> colors.accentMain
        else -> colors.textPrimary
    }
    val labelColor = when {
        isSelected -> onAccent.copy(alpha = 0.85f)
        mark == null -> Color.Transparent
        mark.kind == EventKind.EXAM -> colors.warning
        mark.isAdjustedWorkday -> colors.textSecondary
        // 假期、放假的小字走中性色：十月初连着七八个「国庆节」，
        // 全用信息蓝会把选中日和考试提醒一起盖过去。
        else -> colors.textTertiary
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .height(CalendarCellHeight)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onSelect(cell.date) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        // 选中/今天只染日期数字本身（一个正圆），不要整格铺色 ——
        // 44dp 高的格子铺成方块时，视觉重心会从数字偏到格子。
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(discBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = dateColor,
            )
        }
        Text(
            text = mark?.label.orEmpty(),
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = labelColor,
        )
    }
}
