package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 快捷设置教室分布图的列数 / 行数。
 *
 * [columns] / [rows] 传当前生效的网格尺寸；如果内容超过设定行数，
 * 调用方应传自动扩展后的实际行数，保证选中的胶囊和看到的网格一致。
 */
@Composable
fun GridSizeQuickSettings(
    columns: Int,
    rows: Int,
    onColumnsChange: (Int) -> Unit,
    onRowsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 当前值可能是自动扩展后的行数，不在默认快捷项里也要把芯片补出来。
    val columnOptions = (listOf(4, 5, 6, 8, 10, 12) + columns).distinct().sorted()
    val rowOptions = (listOf(4, 6, 8, 10, 12, 16) + rows).distinct().sorted()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("网格", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            Text(
                text = columns.toString() + " × " + rows.toString() + " 格",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(skin.spaceXs))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("列", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            columnOptions.forEach { value ->
                FilterChip(
                    selected = columns == value,
                    onClick = { onColumnsChange(value) },
                    label = { Text(value.toString(), fontSize = 11.sp) },
                    modifier = Modifier.height(34.dp),
                )
            }
        }
        Spacer(Modifier.height(skin.spaceXs))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("行", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
            rowOptions.forEach { value ->
                FilterChip(
                    selected = rows == value,
                    onClick = { onRowsChange(value) },
                    label = { Text(value.toString(), fontSize = 11.sp) },
                    modifier = Modifier.height(34.dp),
                )
            }
        }
    }
}
