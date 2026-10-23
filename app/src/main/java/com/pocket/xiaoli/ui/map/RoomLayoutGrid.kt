package com.pocket.xiaoli.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.RoomCellType
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomGridLayout
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import kotlin.math.roundToInt
import com.pocket.xiaoli.ui.theme.LocalSkin

/**
 * 房间类型 → 强调色。
 *
 * 用法是**低透明度叠加**在主题表面之上（而不是直接当底色），
 * 这样深色模式下只是加一层淡色调，不会出现"浅色方块糊在暗色背景上"的观感问题。
 */
fun RoomCellType.tintColor(): Color = when (this) {
    RoomCellType.CLASSROOM -> Color(0xFF5B7FE8)
    RoomCellType.OFFICE -> Color(0xFF7A63C9)
    RoomCellType.LAB -> Color(0xFF3E8E72)
    RoomCellType.COMPUTER -> Color(0xFF4A90D9)
    RoomCellType.RESTROOM -> Color(0xFF3FA3B8)
    RoomCellType.STAIR -> Color(0xFF8A7F6B)
    RoomCellType.ELEVATOR -> Color(0xFF9C6FB0)
    RoomCellType.ENTRANCE -> Color(0xFF4CAF50)
    RoomCellType.CORRIDOR -> Color(0xFF9AA0A6)
}

/**
 * 教室分布图网格（支持跨格）。
 *
 * ## 为什么不用 Row/Column 分块排
 * 跨格房间要同时覆盖多行多列，分块布局无法让一个格子跨两行 —— 上一行被撑高后，
 * 下一行的占位就对不齐了。这里改用**绝对定位**：每个锚点按 (row, col) 偏移、
 * 按 (colSpan, rowSpan) 定尺寸，天然支持任意跨格。
 *
 * ## 视觉对齐课程表
 * 格子尺寸 / 圆角 / 边框样式 / 字号 / 透明度都可由调用方传入（默认值即可直接用），
 * 因此「教室分布」可以复用课表设置里那套外观，看起来就是同一套网格语言。
 *
 * ## 职责边界
 * 纯渲染组件：只吃 [RoomGridLayout]，不含业务逻辑（不查库、不算布局）。
 * 布局由 `RoomDirectorySearch.layoutGrid()` 负责，便于单测。
 *
 * @param showEmptySlots 是否先铺一层浅色空格。保留"网格感"，也便于用户判断方位；
 *                       跨格房间会盖住它下方的空格。
 * @param onEmptySlotClick 点空白格：用于「点格子直接添加教室」，参数是 (row, col)。
 */
@Composable
fun RoomLayoutGrid(
    layout: RoomGridLayout,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 56.dp,
    gap: Dp = 1.dp,
    cornerRadius: Int = 10,
    borderStyle: Int = 0,
    textSize: Int = 13,
    opacity: Float = 1f,
    highlightedRoomId: String? = null,
    showEmptySlots: Boolean = true,
    roomsClickable: Boolean = true,
    onRoomClick: (RoomCell) -> Unit = {},
    onRoomMove: (RoomCell, Int, Int) -> Unit = { _, _, _ -> },
    onEmptySlotClick: (Int, Int) -> Unit = { _, _ -> },
) {
    val colors = LocalPocketXiaoliColors.current
    val rows = layout.rows.coerceAtLeast(1)
    val totalHeight = cellHeight * rows + gap * (rows - 1)
    val shape = RoundedCornerShape(cornerRadius.dp)

    BoxWithConstraints(modifier.height(totalHeight)) {
        val density = LocalDensity.current
        val columns = layout.columns.coerceAtLeast(1)
        var draggingId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        val cellWidth = (maxWidth - gap * (columns - 1)) / columns

        fun xOf(col: Int): Dp = (cellWidth + gap) * col
        fun yOf(row: Int): Dp = (cellHeight + gap) * row

        // ① 底层：浅色空格 + 边框，保留网格感；空格可点，直接添加教室
        if (showEmptySlots) {
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val covered = layout.placementAt(r, c) != null
                    Box(
                        modifier = Modifier
                            .offset(x = xOf(c), y = yOf(r))
                            .size(width = cellWidth, height = cellHeight)
                            .background(colors.surfaceAlt.copy(alpha = 0.35f), shape)
                            // 始终画一层浅网格线，和课表的「格子表」视觉一致。
                            // borderStyle 仍作为用户自定义边框叠加在房间卡上。
                            .roomCellBorder(
                                style = if (borderStyle > 0) borderStyle else 1,
                                color = colors.borderDivider.copy(alpha = if (borderStyle > 0) 1f else 0.55f),
                                cornerRadius = cornerRadius,
                            )
                            .clickable(enabled = !covered) { onEmptySlotClick(r, c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!covered) {
                            Text(
                                text = "+",
                                fontSize = 15.sp,
                                color = colors.textTertiary.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            }
        }

        // ② 上层：房间（只画锚点；跨格自然覆盖底层的空格）

        layout.placements.forEach { p ->
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val cellHeightPx = with(density) { cellHeight.toPx() }
            val gapPx = with(density) { gap.toPx() }
            RoomGridSlot(
                cell = p.cell,
                modifier = Modifier
                    .offset(x = xOf(p.col), y = yOf(p.row))
                    .size(
                        width = cellWidth * p.colSpan + gap * (p.colSpan - 1),
                        height = cellHeight * p.rowSpan + gap * (p.rowSpan - 1),
                    )
                    .graphicsLayer { alpha = if (draggingId == p.cell.id) 0.55f else 1f }
                    .pointerInput(p.cell.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = p.cell.id
                                dragOffset = Offset.Zero
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                            },
                            onDragEnd = {
                                val dCol = (dragOffset.x / (cellWidthPx + gapPx)).roundToInt()
                                val dRow = (dragOffset.y / (cellHeightPx + gapPx)).roundToInt()
                                if (dRow != 0 || dCol != 0) {
                                    onRoomMove(
                                        p.cell,
                                        (p.row + dRow).coerceAtLeast(0),
                                        (p.col + dCol).coerceAtLeast(0),
                                    )
                                }
                                draggingId = null
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = Offset.Zero
                            },
                        )
                    },
                cornerRadius = cornerRadius,
                borderStyle = borderStyle,
                textSize = textSize,
                opacity = opacity,
                highlighted = p.cell.id == highlightedRoomId,
                clickable = roomsClickable,
                onClick = onRoomClick,
            )
        }
    }
}

/** 单个房间格：房间号 + 用途（跨格时会被拉高/拉宽） */
@Composable
private fun RoomGridSlot(
    cell: RoomCell,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 10,
    borderStyle: Int = 0,
    textSize: Int = 13,
    opacity: Float = 1f,
    highlighted: Boolean = false,
    clickable: Boolean = true,
    onClick: (RoomCell) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tint = cell.roomType.tintColor()
    val shape = RoundedCornerShape(cornerRadius.dp)
    val description = buildString {
        append(cell.room)
        if (cell.name.isNotBlank()) append("，").append(cell.name)
        append("，").append(cell.roomType.label)
    }
    Column(
        modifier = modifier
            .graphicsLayer { alpha = opacity }
            .background(colors.surfaceHighlight, shape)
            // 类型色低透明叠加，深色模式下也不会突兀
            .background(tint.copy(alpha = if (highlighted) 0.34f else 0.16f), shape)
            .roomCellBorder(borderStyle, colors.borderDivider, cornerRadius)
            .then(if (clickable) Modifier.clickable { onClick(cell) } else Modifier)
            .semantics { contentDescription = description }
            .padding(skin.spaceSmall),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = cell.room,
            fontSize = textSize.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (cell.name.isNotBlank()) {
            Spacer(Modifier.height(skin.spaceMicro))
            Text(
                text = cell.name,
                fontSize = (textSize - 4).coerceAtLeast(8).sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 格子边框：与课程表保持同一套「无 / 实线 / 虚线」语言。
 * 描边内缩半个线宽，避免被父级圆角裁掉一半。
 */
private fun Modifier.roomCellBorder(style: Int, color: Color, cornerRadius: Int): Modifier =
    if (style <= 0) this else drawBehind {
        val stroke = 1.dp.toPx()
        val half = stroke / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(half, half),
            size = Size(
                width = (size.width - stroke).coerceAtLeast(0f),
                height = (size.height - stroke).coerceAtLeast(0f),
            ),
            cornerRadius = CornerRadius(cornerRadius.dp.toPx()),
            style = Stroke(
                width = stroke,
                pathEffect = if (style == 2) {
                    PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                } else null,
            ),
        )
    }
