package com.pocket.xiaoli.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.ui.theme.LocalCourseColorMode
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.colorIndexFor
import com.pocket.xiaoli.ui.theme.courseBgColor
import com.pocket.xiaoli.ui.theme.courseTextColor
import com.pocket.xiaoli.util.MinuteOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 日程视图（课表页的第三种视图）。
 *
 * 与课表网格的区别只是**读法**：同一份已按周次/调休过滤的课程列表，这里按天分组、
 * 按开始时间排序，线性地读。课表网格回答"这周每天怎么排"，日程视图回答"这周依次发生什么"。
 *
 * 日程（[CourseEntity.isAgenda]）与普通课程同表，靠角标区分。
 */
@Composable
internal fun AgendaList(
    courses: List<CourseEntity>,
    timeSlots: List<TimeSlotEntity>,
    startOfWeek: LocalDate,
    modifier: Modifier = Modifier,
    /** 「今天」的判定基准。默认取系统日期；测试注入固定值，避免基准图随日期漂移。 */
    today: LocalDate = LocalDate.now(),
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val isDark = LocalDarkMode.current
    val slotMap = remember(timeSlots) { timeSlots.associateBy { it.slot } }
    val fmt = remember { DateTimeFormatter.ofPattern("M/d") }
    val byDay = remember(courses) { courses.groupBy { it.dayOfWeek } }
    val days = (1..7).filter { byDay[it].orEmpty().isNotEmpty() }

    if (days.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(colors.accentMain.copy(alpha = if (isDark) 0.22f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.EventBusy,
                        contentDescription = null,
                        tint = colors.accentMain,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(skin.spaceMd))
                Text(
                    "本周还没有课程或日程",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    "切到课表视图，或添加一门课",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = skin.spaceSmall, vertical = skin.spaceSm),
        verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
    ) {
        days.forEach { day ->
            val date = startOfWeek.plusDays((day - 1).toLong())
            item(key = "agenda-day-$day") {
                Row(
                    modifier = Modifier.padding(horizontal = skin.spaceXs, vertical = skin.spaceMicro),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(13.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (date == today) colors.accentMain else colors.borderDivider)
                    )
                    Spacer(Modifier.width(skin.spaceSmall))
                    Text(
                        WEEKDAY_LABELS[day - 1],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(skin.spaceXs))
                    Text(date.format(fmt), fontSize = 11.sp, color = colors.textTertiary)
                    if (date == today) {
                        Spacer(Modifier.width(skin.spaceSmall))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.accentMain.copy(alpha = if (isDark) 0.22f else 0.10f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("今天", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.accentMain)
                        }
                    }
                    Spacer(Modifier.width(skin.spaceSm))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.borderDivider)
                    )
                }
            }
            // 排序键：填了分钟级时间就按它，否则退回节次；同分再按节次兜底
            val dayCourses = byDay[day].orEmpty().sortedWith(
                compareBy(
                    { if (MinuteOfDay.isValid(it.startMinute)) it.startMinute else it.startSlot * 100 },
                    { it.startSlot },
                )
            )
            items(dayCourses, key = { "agenda-${it.id}" }) { course ->
                AgendaRow(course, slotMap, colors, skin)
            }
        }
    }
}

@Composable
private fun AgendaRow(
    course: CourseEntity,
    slotMap: Map<Int, TimeSlotEntity>,
    colors: com.pocket.xiaoli.ui.theme.PocketXiaoliColors,
    skin: com.pocket.xiaoli.ui.theme.SkinTokens,
) {
    val dark = LocalDarkMode.current
    val colorMode = LocalCourseColorMode.current
    val colorIndex = course.colorIndexFor(colorMode)
    val bg = courseBgColor(colorIndex, dark)
    val fg = courseTextColor(colorIndex, dark)

    val startTime = when {
        MinuteOfDay.isValid(course.startMinute) -> MinuteOfDay.format(course.startMinute)
        else -> slotMap[course.startSlot]?.startTime.orEmpty()
    }
    val endTime = when {
        MinuteOfDay.isValid(course.endMinute) -> MinuteOfDay.format(course.endMinute)
        else -> slotMap[course.endSlot]?.endTime.orEmpty()
    }
    val timeText = when {
        startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime-$endTime"
        startTime.isNotBlank() -> startTime
        else -> "第${course.startSlot}节"
    }

    val shape = RoundedCornerShape(skin.radiusCard)
    // 鲜活皮肤才做渐变与同色光晕；极简皮肤保持纯色卡，符合「无装饰、靠留白分层」的承诺。
    val decorated = skin.shadowBlur > 0.dp
    val cardBrush = if (decorated) {
        Brush.linearGradient(
            listOf(
                bg,
                lerp(bg, colors.surfaceCard, if (dark) 0.30f else 0.55f),
            )
        )
    } else null
    val cardShadow = if (decorated) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = fg.copy(alpha = if (dark) 0.26f else 0.14f),
                offset = DpOffset(0.dp, skin.shadowOffsetY),
            ),
        )
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(cardShadow)
            .clip(shape)
            .then(if (cardBrush != null) Modifier.background(cardBrush) else Modifier.background(bg))
            .padding(horizontal = skin.spaceMd, vertical = skin.spaceMd),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = 0.82f))
            )
            Spacer(Modifier.width(skin.spaceSm))
            Box(
                modifier = Modifier
                    .width(82.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surfaceCard.copy(alpha = if (dark) 0.20f else 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    timeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                )
            }
            Spacer(Modifier.width(skin.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        course.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (course.isAgenda) {
                        Spacer(Modifier.width(skin.spaceXs))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.surfaceCard.copy(alpha = if (dark) 0.85f else 0.82f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "日程",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentMain,
                            )
                        }
                    }
                }
                val detail = listOf(course.room, course.teacher)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        detail,
                        fontSize = 12.sp,
                        color = fg.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (course.note.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        course.note,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = fg.copy(alpha = 0.72f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
