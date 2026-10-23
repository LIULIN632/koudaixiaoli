package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.util.HolidayMark
import com.pocket.xiaoli.util.parseSemesterStart
import androidx.compose.ui.graphics.shadow.Shadow
import java.time.LocalDate

/**
 * 节假日标记配色：休 = 放假（红），班 = 调休上班（橙）。
 *
 * ⚠️ 这两个色**只在浅色底上够用**（红 4.45:1 / 橙 8.11:1 压 `surfaceCard`）。
 * 深色主题下把同一组色当文字用会偏暗，所以下面取色时按 `isDark` 走令牌的
 * `danger` / `warning` —— 它们本来就是"按明暗分档"定义好的（见 `DesignTokens.kt`）。
 * 保留这两个常量是为了**浅色态的观感不变**（令牌的 danger 是 `#B91C1C`，比这里的
 * `#E5484D` 深，直接换会改变浅色主题下的观感 —— 那是另一次视觉决定）。
 */
private val HolidayOffColor = Color(0xFFE5484D)
private val HolidayWorkColor = Color(0xFFF59E0B)

@Composable
fun WeekHeader(
    currentWeek: Int,
    isCurrentWeek: Boolean = false,
    semesterStart: String = "",
    holidayMarks: Map<String, HolidayMark> = emptyMap(),
    /** 左侧 32dp 固定插槽（与课表节次栏等宽）；模式切换等入口放这里，保证星期列与下方课表列对齐 */
    leadingContent: (@Composable () -> Unit)? = null,
    /** 「今天」的判定基准。默认取系统日期；测试注入固定值，避免基准图随日期漂移。 */
    today: LocalDate = LocalDate.now(),
) {
    val skin = LocalSkin.current
    val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val todayDay = today.dayOfWeek.value
    val colors = LocalPocketXiaoliColors.current
    // 节假日角标要按明暗取不同档的语义色（见下面 badgeColor 的说明）
    val darkMode = LocalDarkMode.current
    val todayForeground = onAccentColor()
    // 极简皮肤不画阴影：今日格退化成纯色块，渐变与光晕只属于鲜活皮肤
    val decorated = skin.shadowBlur > 0.dp

    val weekMonday = parseSemesterStart(semesterStart)
        ?.plusWeeks((currentWeek - 1).toLong())
        ?: today.plusDays((-(todayDay - 1) + (currentWeek - 1) * 7).toLong())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 固定 32dp：与课表节次栏等宽，星期列才能和下方课表列严格对齐
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            leadingContent?.invoke()
        }

        weekdays.forEachIndexed { index, dayLabel ->
            val dayNum = index + 1
            val isToday = isCurrentWeek && dayNum == todayDay
            val date = weekMonday.plusDays(index.toLong())
            val mark = holidayMarks[date.toString()]
            val cellShape = RoundedCornerShape(skin.radiusLarge)

            // 今日格：主题色渐变 + 同色光晕。极简皮肤保持纯色平铺，
            // 让「极简」仍然只靠形状与字重表达选中态。
            val cellShadow = if (isToday && decorated) {
                Modifier.dropShadow(
                    shape = cellShape,
                    shadow = Shadow(
                        radius = skin.shadowBlur,
                        spread = 0.dp,
                        color = colors.accentMain.copy(alpha = if (darkMode) 0.35f else 0.22f),
                        offset = DpOffset(0.dp, 3.dp),
                    ),
                )
            } else Modifier
            val cellBrush = if (isToday && decorated) {
                Brush.verticalGradient(
                    listOf(
                        colors.accentMain,
                        lerp(colors.accentMain, Color.Black, if (darkMode) 0.10f else 0.12f),
                    )
                )
            } else null

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp)
                    .then(cellShadow)
                    .clip(cellShape)
                    .then(
                        when {
                            cellBrush != null -> Modifier.background(cellBrush)
                            isToday -> Modifier.background(colors.accentMain)
                            else -> Modifier
                        }
                    )
                    .padding(vertical = if (isToday) 6.dp else 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally

            ) {
                Text(
                    text = dayLabel,
                    color = if (isToday) todayForeground.copy(alpha = 0.82f) else colors.textTertiary,
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = date.dayOfMonth.toString(),
                    color = if (isToday) todayForeground else colors.textPrimary,
                    fontSize = if (isToday) 17.sp else 15.sp,
                    fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                // 节假日（休）/ 调休上班（班）角标
                if (mark != null) {
                    Spacer(Modifier.height(2.dp))
                    val badgeColor = when {
                        isToday -> todayForeground
                        // 深色态改走令牌：它们是按明暗分档定义好的语义色
                        //（浅色用 700~800 号、深色用 500 号），见 DesignTokens.kt 的分档说明。
                        mark.isOffDay -> if (darkMode) colors.danger else HolidayOffColor
                        else -> if (darkMode) colors.warning else HolidayWorkColor
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = if (isToday) 0.18f else 0.12f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (mark.isOffDay) mark.label.take(2) else "班",
                            color = badgeColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}