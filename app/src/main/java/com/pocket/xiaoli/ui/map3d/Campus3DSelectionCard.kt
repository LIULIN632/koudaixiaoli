package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.Campus3DColors as Palette
import kotlin.math.roundToInt

@Composable
internal fun SelectionCard(
    hit: Campus3DHit,
    onDismiss: () -> Unit,
    onOpenFloors: (() -> Unit)? = null,
    onOpenProfile: (() -> Unit)? = null,
    todayClassCount: Int = 0,
) {
    val chip: String
    val title: String
    val details: String
    val note: String?
    val floorCount: Int?
    when (hit) {
        is Campus3DHit.Building -> {
            val shape = hit.shape
            floorCount = shape.levels
            chip = categoryLabel(shape.category)
            title = shape.name.ifBlank { "未命名楼栋" }
            details = buildString {
                append("${shape.levels} 层")
                if (shape.levelsEstimated) append("（按面积推算）")
                append(" · 高约 ${formatMeters(shape.height)} · ")
                append(if (shape.note.isBlank()) "OSM 实测轮廓" else "影像描点轮廓")
            }
            note = when {
                shape.note.isNotBlank() -> shape.note
                shape.levelsEstimated -> "层数按建筑面积保守推算，高度仅用于示意。"
                else -> null
            }
        }
        is Campus3DHit.Poi -> {
            floorCount = null
            chip = "位置标注"
            title = hit.shape.name
            details = "坐标来自公开地图标注 · 已转 WGS-84"
            note = "OSM 暂无对应轮廓，仅标位置、不绘制体块。"
        }
        is Campus3DHit.Pitch -> {
            floorCount = null
            chip = sportLabel(hit.shape.kind, hit.shape.sport)
            title = hit.shape.name.ifBlank { chip }
            details = "OSM 运动场地轮廓 · 仅作方位参考"
            note = null
        }
        is Campus3DHit.Water -> {
            floorCount = null
            chip = "校园水系"
            title = hit.shape.name.ifBlank { "景观水体" }
            details = "OSM 实测水体轮廓"
            note = null
        }
    }

    val canOpenFloors = onOpenFloors != null && floorCount != null
    val hasActions = canOpenFloors || onOpenProfile != null

    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        color = Palette.CardSolid,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Palette.CardBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(color = Palette.ChipBg, shape = CircleShape) {
                            Text(
                                text = chip,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                color = Palette.ChipText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        if (todayClassCount > 0) {
                            Surface(
                                color = Palette.TodayClass.copy(alpha = 0.14f),
                                shape = CircleShape,
                                border = BorderStroke(1.dp, Palette.TodayClass.copy(alpha = 0.45f)),
                            ) {
                                Text(
                                    text = "今日 $todayClassCount 节课",
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                    color = Palette.TodayClass,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = title,
                        color = Palette.LabelText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "关闭详情", tint = Palette.LabelTextDim)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = details,
                modifier = Modifier.padding(end = 10.dp),
                color = Palette.LabelTextDim,
                fontSize = 11.sp,
            )
            note?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    modifier = Modifier.padding(end = 10.dp),
                    color = Palette.LabelTextDim.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                )
            }
            if (hasActions) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canOpenFloors && onOpenFloors != null) {
                        Surface(
                            onClick = onOpenFloors,
                            modifier = Modifier
                                .weight(1.5f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Palette.Accent,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "查看各层平面图",
                                    color = Palette.CardSolid,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "$floorCount 层 ›",
                                    color = Palette.CardSolid.copy(alpha = 0.72f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    onOpenProfile?.let { openProfile ->
                        Surface(
                            onClick = openProfile,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Palette.SkyTop.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, Palette.CardBorder),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (canOpenFloors) "建筑档案" else "查看建筑档案",
                                    color = Palette.LabelText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMeters(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()} 米" else "$rounded 米"
}
