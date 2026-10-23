package com.pocket.xiaoli.data.map.route

import com.pocket.xiaoli.data.map.model.CampusMapData

data class CampusRouteStep(
    val index: Int,
    val instruction: String,
    val nodeId: String,
    val distanceMeters: Double? = null,
    val floorChange: Int = 0,
)

/** 把节点路径翻译成用户能读的步骤文案。 */
object CampusRouteInstructionBuilder {

    fun build(
        data: CampusMapData,
        graph: CampusRouteGraph,
        path: CampusRoutePath,
    ): List<CampusRouteStep> {
        if (!path.isFound) return emptyList()

        val steps = mutableListOf<CampusRouteStep>()
        val start = path.nodeIds.first()
        steps += CampusRouteStep(
            index = 0,
            instruction = "从${nodeLabel(data, graph, start)}出发",
            nodeId = start,
        )

        path.nodeIds.zipWithNext().forEachIndexed { index, (from, to) ->
            val edge = graph.edgeBetween(from, to)
            val node = graph.nodes[to]
            val floorChange = floorLevel(data, graph, to)?.let { toLevel ->
                floorLevel(data, graph, from)?.let { fromLevel -> toLevel - fromLevel }
            } ?: 0
            val meters = edge?.meters
            val instruction = when {
                node?.kind == "stairs" && floorChange > 0 -> "上 ${floorChange} 层楼梯"
                node?.kind == "stairs" && floorChange < 0 -> "下 ${-floorChange} 层楼梯"
                node?.kind == "stairs" -> "走楼梯"
                node?.kind == "elevator" && floorChange != 0 -> {
                    val targetFloor = floorName(data, node.floorId) ?: "${floorChange} 层"
                    "乘电梯到 $targetFloor"
                }
                node?.kind == "room" -> "到达 ${nodeLabel(data, graph, to)}"
                edge?.kind == "outdoor" -> "沿校园道路前行 ${formatMeters(meters)}"
                else -> "继续前行 ${formatMeters(meters)}"
            }
            steps += CampusRouteStep(
                index = index + 1,
                instruction = instruction,
                nodeId = to,
                distanceMeters = meters,
                floorChange = floorChange,
            )
        }

        return steps
    }

    private fun nodeLabel(
        data: CampusMapData,
        graph: CampusRouteGraph,
        nodeId: String,
    ): String {
        val roomName = data.rooms.firstOrNull { it.nodeId == nodeId }?.name
        if (!roomName.isNullOrBlank()) return roomName
        return when (graph.nodes[nodeId]?.kind) {
            "outdoor" -> "室外道路"
            "entrance" -> "建筑入口"
            "corridor" -> "走廊"
            "stairs" -> "楼梯"
            "elevator" -> "电梯"
            "room" -> "房间"
            else -> "当前位置"
        }
    }

    private fun floorLevel(
        data: CampusMapData,
        graph: CampusRouteGraph,
        nodeId: String,
    ): Int? {
        val floorId = graph.nodes[nodeId]?.floorId ?: return null
        return data.floors.firstOrNull { it.id == floorId }?.level
    }

    private fun floorName(data: CampusMapData, floorId: String?): String? {
        val floor = data.floors.firstOrNull { it.id == floorId } ?: return null
        return floor.name.ifBlank { "${floor.level}层" }
    }

    private fun formatMeters(meters: Double?): String {
        val value = meters ?: return ""
        if (value <= 0.0) return ""
        return if (value % 1.0 == 0.0) "${value.toInt()} 米" else String.format("%.1f 米", value)
    }
}