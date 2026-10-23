package com.pocket.xiaoli.ui.map3d

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.map.indoor.IndoorFloorPlan
import com.pocket.xiaoli.data.map.indoor.IndoorRoom
import com.pocket.xiaoli.data.map.indoor.IndoorRoute
import com.pocket.xiaoli.data.map.indoor.IndoorSide
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors

@Composable
internal fun EmptySelectionCard() {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceAlt.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colors.borderDivider.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "点一间房试试",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "例如点「415」：房间会高亮，并画出从所选楼梯口到门口的示意路线。",
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
internal fun SelectedRoomCard(room: IndoorRoom, route: IndoorRoute?) {
    val colors = LocalPocketXiaoliColors.current
    val sideColor = roomSideColor(room.side, colors)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("indoor-selected-room"),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.accentMain.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("已选房间", color = colors.textTertiary, fontSize = 10.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room.name,
                            color = colors.textPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (room.provisional) {
                            Spacer(Modifier.width(6.dp))
                            MiniChip("待核", colors.warning)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(room.side.label, color = sideColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("第 ${room.bay} 开间", color = colors.textTertiary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = room.linkedBuiltinRoomId
                    ?.let { "Room 库：$it" }
                    ?: "Room 库未录入（指引牌可见，待现场核对）",
                color = if (room.linkedBuiltinRoomId == null) colors.warning else colors.textTertiary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(10.dp))
            if (route == null) {
                Text(
                    text = "当前拓扑没有连到该房间的路线。",
                    color = colors.warning,
                    fontSize = 11.sp,
                )
                return@Column
            }
            Text("示意路线", color = colors.accentMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "起点：${route.startLabel}（不是 1F 东大门）",
                color = colors.textTertiary,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
            route.steps.forEachIndexed { index, step ->
                RouteStep(index + 1, step)
            }
            route.caution?.let { caution ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "注意：$caution",
                    color = colors.warning,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "只表示连接顺序，不含真实距离、门位与转弯角度。",
                color = colors.textTertiary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun RouteStep(number: Int, text: String) {
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(colors.accentMain.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                color = colors.accentMain,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = colors.textSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
internal fun MapLegend(plan: IndoorFloorPlan) {
    val colors = LocalPocketXiaoliColors.current
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendDot(colors.info, "北区")
            LegendDot(colors.warning, "南区")
            LegendLine(colors.accentMain, "路线")
            Spacer(Modifier.weight(1f))
            Text(
                text = "${plan.rooms.size} 间房 · ${plan.facilities.size} 个梯口",
                color = colors.textTertiary,
                fontSize = 9.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "虚线框 + WC? = 指引牌可见、Room 库缺失，位置待核实。",
            color = colors.textTertiary,
            fontSize = 9.sp,
        )
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    val colors = LocalPocketXiaoliColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color.copy(alpha = 0.75f), CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = colors.textSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun LegendLine(color: Color, text: String) {
    val colors = LocalPocketXiaoliColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(2.5.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, color = colors.textSecondary, fontSize = 9.sp)
    }
}

@Composable
internal fun ProvenanceCard(plan: IndoorFloorPlan) {
    val colors = LocalPocketXiaoliColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceAlt.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, colors.borderDivider.copy(alpha = 0.7f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "这幅图是什么，不是什么",
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "它是按 ${plan.sourceDate ?: "指引牌"} 实拍的 ${plan.floorLabel} 指引牌排出的连接关系：" +
                    "${plan.nodes.count { it.kind == "corridor" }} 个走廊节点 + " +
                    "${plan.rooms.size} 个房间 + ${plan.facilities.size} 个楼梯，" +
                    "${plan.edges.size} 条边。它不包含尺寸、门位、真实走廊形状，" +
                    "也不代表教室当前是否有人在用。",
                color = colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "为什么起点不是东大门",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "草案只覆盖 ${plan.floorLabel}，1F 大门与跨层路网尚未实测；从东大门起算会凭空造出一段" +
                    "不存在的路线。因此路线只从 ${plan.floorLabel} 楼梯口起算（默认 ${plan.startLabel}），" +
                    "不跨层、不报米数。",
                color = colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已知缺口",
                color = colors.textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "南区卫生间在指引牌上有、BuiltinRoomCells 没录入，图中用虚线标出；" +
                    "北 / 南是指引牌分区名，真北以 3D 地图指北针为准。",
                color = colors.textSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
