package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 字段旁边/下面的**动作胶囊**：一个有边界、按得下去的小按钮。
 *
 * ## 为什么不用裸 TextButton
 *
 * 教室字段右侧原本并排三个裸文字（分布图 / 地图 / 导航），其中两个还是三级字色 ——
 * 读起来像"这行还有两段说明"，不像"这里能点"。用户对口径很明确：
 * **可点的元素要有可见边界与按压反馈**。
 *
 * ## 形状为什么是满圆角而不是 `skin.radiusChip`
 *
 * 它旁边就是 `radiusChip` 的输入框。同一种圆角会让人觉得它们属于同一组控件，
 * 而动作和字段是两回事 —— 胶囊把这层区别直接画出来（与 `FilterChipText` 同一个理由）。
 *
 * @param label 胶囊文字（12sp 半粗，用**一级字色**：三级字色实测只有 2.15:1，看不清）
 */
@Composable
fun FieldActionChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderDivider, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceCompact),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
    }
}
