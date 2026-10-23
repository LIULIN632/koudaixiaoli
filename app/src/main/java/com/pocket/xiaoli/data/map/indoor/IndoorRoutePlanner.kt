package com.pocket.xiaoli.data.map.indoor

import com.pocket.xiaoli.data.map.model.CampusEdge
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.data.map.route.CampusRouteGraph
import com.pocket.xiaoli.data.map.route.CampusRouter
import kotlin.math.roundToInt

/**
 * 拓扑草案里的一条示意路线。
 *
 * 刻意不带米数 / 用时：草案的边没有实测长度，任何「X 米」「X 分钟」都是编的。
 * [nodeIds] 只用于在画布上描出连接顺序。
 */
data class IndoorRoute(
    val roomId: String,
    val nodeIds: List<String>,
    val steps: List<String>,
    val startNodeId: String,
    val startLabel: String,
    val caution: String? = null,
)

/**
 * 边权只是 Dijkstra 的等权占位，**不许显示给用户**。
 * 拓扑草案的 meters 为 null，这里统一给 1.0 只为了让现有 CampusRouter 能跑。
 */
private const val DRAFT_EDGE_WEIGHT = 1.0

/**
 * 从选定的 4F 楼梯口出发，走到指定房间。
 *
 * 起点不用「东大门」：草案只覆盖 4F，1F 大门到楼上的跨层路网尚未实测，
 * 从东大门起算会凭空造出一段不存在的路线。
 */
fun IndoorFloorPlan.routeTo(
    roomId: String,
    fromNodeId: String = startNodeId,
): IndoorRoute? {
    val room = room(roomId) ?: return null
    val start = node(fromNodeId)?.takeIf { it.kind == "stairs" } ?: return null
    val startLabel = startOptions.firstOrNull { it.nodeId == fromNodeId }?.label ?: start.name
    val graph = CampusRouteGraph.from(
        CampusMapData(
            nodes = nodes.map { node ->
                CampusNode(
                    id = node.id,
                    buildingId = buildingId,
                    floorId = floorId,
                    kind = node.kind,
                )
            },
            edges = edges.map { edge ->
                CampusEdge(
                    from = edge.from,
                    to = edge.to,
                    meters = DRAFT_EDGE_WEIGHT,
                    kind = edge.kind,
                )
            },
        )
    )
    val path = CampusRouter.shortestPath(graph, fromNodeId, room.doorNodeId)
    if (!path.isFound) return null

    val startBay = start.point.x.roundToInt()
    val direction = when {
        room.bay > startBay -> "向西"
        room.bay < startBay -> "向东"
        else -> ""
    }
    val steps = buildList {
        add("从${startLabel}进入 4F 主走廊")
        add(
            if (direction.isEmpty()) {
                "在主走廊找到 ${room.name} 的门口"
            } else {
                "沿主走廊${direction}直行到 ${room.name} 的门口"
            }
        )
        add("从主走廊${room.side.label}侧进入 ${room.name}")
    }
    return IndoorRoute(
        roomId = room.id,
        nodeIds = path.nodeIds,
        steps = steps,
        startNodeId = fromNodeId,
        startLabel = startLabel,
        caution = if (room.provisional) {
            "该房间只在 4F 指引牌上出现，Room 库暂无记录；位置与门位待现场核对。"
        } else {
            null
        },
    )
}
