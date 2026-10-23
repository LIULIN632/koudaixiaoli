package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 房间类型图例。
 *
 * @param types 要展示的类型；默认全部。传入实际用到的子集可以避免图例过长。
 */
@Composable
fun RoomTypeLegend(
    modifier: Modifier = Modifier,
    types: List<RoomCellType> = RoomCellType.entries.toList(),
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(skin.spaceMd),
    ) {
        types.forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(type.tintColor())
                )
                Spacer(Modifier.width(skin.spaceXs))
                Text(
                    text = type.label,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 指北针：上北下南、左西右东。
 *
 * 注意：这只是一个**方位提示**，不代表地图已按真实方位旋转 ——
 * 房间的实际方位取决于 `RoomCell.row / col` 是否按真实坐标系录入。
 */
@Composable
fun CompassIndicator(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(colors.surfaceAlt.copy(alpha = 0.5f))
            .padding(skin.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "北",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            text = "南",
            fontSize = 10.sp,
            color = colors.textTertiary,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Text(
            text = "西",
            fontSize = 10.sp,
            color = colors.textTertiary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "东",
            fontSize = 10.sp,
            color = colors.textTertiary,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(colors.textTertiary)
        )
    }
}