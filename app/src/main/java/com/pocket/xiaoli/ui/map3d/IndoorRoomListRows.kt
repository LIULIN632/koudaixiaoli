package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.indoor.IndoorRoom
import com.pocket.xiaoli.data.map.indoor.IndoorRoute
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

internal fun LazyListScope.roomSection(
    title: String,
    rooms: List<IndoorRoom>,
    selectedRoomId: String?,
    route: IndoorRoute?,
    onRoomClick: (String) -> Unit,
) {
    item(key = "header_$title") {
        Text(
            text = title,
            modifier = Modifier.padding(top = 4.dp),
            color = LocalPocketXiaoliColors.current.textTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    items(rooms, key = { it.id }) { room ->
        val selected = room.id == selectedRoomId
        Column {
            RoomListRow(
                room = room,
                selected = selected,
                onClick = { onRoomClick(room.id) },
            )
            if (selected) {
                InlineRoute(route)
            }
        }
    }
}

@Composable
internal fun RoomListRow(
    room: IndoorRoom,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    val sideColor = roomSideColor(room.side, colors)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .semantics {
                stateDescription = if (selected) "已选，展开示意路线" else "未选，点按展开示意路线"
            },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accentMain.copy(alpha = 0.10f) else colors.surfaceCard,
        border = BorderStroke(
            1.dp,
            if (selected) colors.accentMain.copy(alpha = 0.45f) else colors.borderDivider,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(sideColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = room.shortLabel,
                    color = sideColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (room.provisional) {
                        Spacer(Modifier.width(6.dp))
                        MiniChip("待核", colors.warning)
                    }
                }
                Text(
                    text = buildString {
                        append(room.side.label)
                        append(" · 第 ${room.bay} 开间")
                        if (room.linkedBuiltinRoomId == null) append(" · Room 库未录入")
                    },
                    color = colors.textTertiary,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = if (selected) "收起" else "路线",
                color = if (selected) colors.accentMain else colors.textTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
internal fun InlineRoute(route: IndoorRoute?) {
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        if (route == null) {
            Text(
                text = "当前拓扑没有连到该房间的路线。",
                color = colors.warning,
                fontSize = 10.sp,
            )
            return@Column
        }
        Text(
            text = "示意路线 · 起点 ${route.startLabel}",
            color = colors.textTertiary,
            fontSize = 9.sp,
        )
        route.steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                color = colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
        route.caution?.let { caution ->
            Text(
                text = "注意：$caution",
                color = colors.warning,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
        }
        Text(
            text = "不含真实距离、门位与转弯角度。",
            color = colors.textTertiary,
            fontSize = 9.sp,
        )
    }
}
