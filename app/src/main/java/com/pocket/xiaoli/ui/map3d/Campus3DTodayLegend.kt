package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette

/** 「今日有课」图例：薄荷圆点与楼栋数，和选中的金色描边区分。 */
@Composable
internal fun Campus3DTodayLegend(
    buildingCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Palette.CardBg.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Palette.TodayClass.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(Palette.TodayClass, CircleShape)
            )
            Column {
                Text(
                    text = "今日有课 · $buildingCount 栋",
                    color = Palette.TodayClass,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "仅统计已录入地点的课",
                    color = Palette.LabelTextDim,
                    fontSize = 9.sp,
                )
            }
        }
    }
}
