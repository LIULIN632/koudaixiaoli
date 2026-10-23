package com.pocket.xiaoli.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.calendar.CalendarEvent
import com.pocket.xiaoli.ui.calendar.CalendarFormat
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import java.time.LocalDate

/**
 * 当月事件列表里的一行。
 *
 * [isToday] 只影响左侧日期块的着色；**不排序** —— 列表按开始日期升序是 `CalendarUtils.eventsInMonth`
 * 定好的，UI 再排一次就会出现两套顺序（本项目已踩过「同一个病的两种写法」）。
 */
internal data class EventRow(
    val event: CalendarEvent,
    val isToday: Boolean,
)

/**
 * 当月事件列表（B 版式：紧凑时间轴 —— 一行一件事，左侧日期数字块，行高统一）。
 *
 * 用**交集**取事件而不是「开始日期在本月」（`eventsInMonth` 负责）：寒假 1/18–2/28
 * 在 2 月的列表里也必须出现，否则 2 月看起来像什么事都没有。
 */
@Composable
internal fun MonthEventList(
    yearMonthLabel: String,
    rows: List<EventRow>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Column(modifier.fillMaxWidth()) {
        Text(
            text = "$yearMonthLabel · 共 ${rows.size} 件事",
            fontSize = 12.sp,
            color = colors.textTertiary,
            modifier = Modifier.padding(start = skin.spaceXs, bottom = skin.spaceXs),
        )
        if (rows.isEmpty()) {
            // 空态**不能留一大片空白**：这个月真的没事是正常情况（2026 年 10 月就只有国庆节一件）。
            // 但也不该是一行悬在页面上的字 —— 给它一张卡，和下面的列表同一个"面"。
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(skin.radiusCard),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.borderDivider),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = "这个月没有安排",
                    fontSize = 13.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = skin.spaceXl),
                )
            }
            return
        }
        rows.forEach { row -> EventRowItem(row.event, row.isToday) }
    }
}

@Composable
private fun EventRowItem(event: CalendarEvent, isToday: Boolean) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = event.start.dayOfMonth.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (isToday) colors.accentMain else colors.textPrimary,
            )
            Text(
                text = CalendarFormat.weekday(event.start),
                fontSize = 10.sp,
                color = colors.textTertiary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 62.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .background(colors.surfaceCard)
                .border(
                    width = 1.dp,
                    color = colors.borderDivider,
                    shape = RoundedCornerShape(skin.radiusChip),
                )
                .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = CalendarFormat.range(event) + " · " + event.kind.label,
                fontSize = 11.sp,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * 选中某天之后长出来的那张卡。
 *
 * 「这天的课 →」入口放在**这一天自己的条目里**（用户定案 B）：
 * 先选中，再决定要不要跳课表 —— 而不是一点日期就把人甩到课表去。
 * 点一下日期就跳走会让人没法只是「看看这天有什么」。
 */
@Composable
internal fun SelectedDayCard(
    date: LocalDate,
    weekLabel: String?,
    eventTitles: List<String>,
    onOpenTimetable: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = skin.spaceXl, vertical = skin.spaceLg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CalendarFormat.monthDayWeekday(date),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                if (weekLabel != null) {
                    Spacer(Modifier.width(skin.spaceSm))
                    Text(
                        text = weekLabel,
                        fontSize = 11.sp,
                        color = colors.accentMain,
                        modifier = Modifier
                            .clip(RoundedCornerShape(skin.radiusChip))
                            .background(colors.accentMain.copy(alpha = 0.12f))
                            .padding(horizontal = skin.spaceSm, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
            if (eventTitles.isEmpty()) {
                Text(
                    text = "这天没有校历安排",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
            } else {
                eventTitles.forEach { title ->
                    Text(
                        text = "· $title",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onOpenTimetable != null) {
                Spacer(Modifier.height(skin.spaceSm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accentMain.copy(alpha = 0.08f))
                        .clickable(onClick = onOpenTimetable)
                        .padding(horizontal = skin.spaceMd, vertical = skin.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "看这天的课",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accentMain,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.accentMain,
                    )
                }
            }
        }
    }
}
