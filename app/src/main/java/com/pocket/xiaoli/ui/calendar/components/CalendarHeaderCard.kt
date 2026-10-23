package com.pocket.xiaoli.ui.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.calendar.CalendarPhase
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 顶部那条（B 版式：两行 + 右侧阶段位）。
 *
 * 代价是它常年占两行高（约 76dp）—— 换来的是「今天第几周」和「现在处于哪个阶段」
 * 一眼能同时看到，而这两件事正好是打开校历页最想问的。
 *
 * 组件只认三样**已经算好的**东西（眉题 / 主标题 / 阶段），
 * 「今天是第几周」与「现在算什么阶段」都在 `CalendarUtils` 里算完再传进来 ——
 * 组件自己去读资产的话，那条「周次口径」的坑就会有两个地方各踩一次。
 */
@Composable
internal fun CalendarHeaderCard(
    eyebrow: String,
    headline: String,
    phase: CalendarPhase,
    modifier: Modifier = Modifier,
    onCalibrate: (() -> Unit)? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(skin.radiusCard),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier
                // 顶部一层极淡的主色彩晕：白卡压在近白页底上时，边界不能只靠 1dp 描边。
                .background(
                    Brush.verticalGradient(
                        listOf(colors.accentMain.copy(alpha = 0.09f), Color.Transparent)
                    )
                )
                .padding(horizontal = skin.spaceXl, vertical = skin.spaceLg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = eyebrow,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = headline,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(skin.spaceSm))
                PhaseChip(phase)
            }

            if (onCalibrate != null) {
                Spacer(Modifier.height(skin.spaceSm))
                CalibrationBand(onCalibrate)
            }
        }
    }
}

/**
 * 右侧那个阶段位。
 *
 * 只表达校历自己说得清的五种状态，不做「距期末还有 N 天」—— 那要多维护一个「期末」定义，
 * 而校历里本来就有考试周，两套定义迟早对不上（用户已定：取 A 方案，只显示阶段文字）。
 */
@Composable
private fun PhaseChip(phase: CalendarPhase) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val tone = when (phase) {
        CalendarPhase.IN_TERM -> colors.accentMain
        CalendarPhase.EXAM_WEEKS -> colors.warning
        CalendarPhase.WINTER_VACATION, CalendarPhase.SUMMER_VACATION -> colors.info
        CalendarPhase.BEFORE_TERM -> colors.textSecondary
    }

    Text(
        text = phase.label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = tone,
        modifier = Modifier
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(tone.copy(alpha = 0.16f))
            .padding(horizontal = skin.spaceSm, vertical = 4.dp),
    )
}

/**
 * 与本地学期设置对不上时才长出来的那条提示带。
 *
 * 它**只提示、不自动写**：周次是课表的输入，改它会让整张课表换一套周次编号。
 * 用户点一下才回写（`AppContainer.setSemesterStart` / `setSemesterWeeks`），
 * 而且写完要在界面上看得出来已经一致了 —— 所以按钮的存在本身就是状态。
 */
@Composable
private fun CalibrationBand(onCalibrate: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.warning.copy(alpha = 0.14f))
            .padding(start = skin.spaceSm, end = skin.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(skin.spaceXs))
        Text(
            text = "学期设置与校历不一致",
            fontSize = 12.sp,
            color = colors.warning,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCalibrate) {
            Text(
                text = "按校历校准",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentMain,
            )
        }
    }
}
