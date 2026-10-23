package com.pocket.xiaoli.data.map.route

import com.pocket.xiaoli.data.map.model.CampusMapData

data class CampusRouteNode(
    val id: String,
    val buildingId: String? = null,
    val floorId: String? = null,
    val kind: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

data class CampusRouteEdge(
    val from: String,
    val to: String,
    val meters: Double,
    val kind: String = "",
) {
    fun reversed(): CampusRouteEdge = copy(from = to, to = from)
}

/**
 * 校园步行路网。
 *
 * 当前把校园地图里的 node/edge 包装成邻接表，室内外 / 跨楼层都用同一张图：
 * - outdoor 节点：道路交叉口 / 建筑入口
 * - corridor 节点：楼内走廊
 * - stairs / elevator 节点：跨楼层连接
 */
class CampusRouteGraph private constructor(
    val nodes: Map<String, CampusRouteNode>,
    private val adjacency: Map<String, List<CampusRouteEdge>>,
) {
    fun neighbors(nodeId: String): List<CampusRouteEdge> = adjacency[nodeId].orEmpty()

    /** 查询两个节点之间的边；有向边按实际记录返回。 */
    fun edgeBetween(fromId: String, toId: String): CampusRouteEdge? =
        adjacency[fromId].orEmpty().firstOrNull { it.to == toId }

    companion object {
        fun from(data: CampusMapData): CampusRouteGraph {
            val nodes = data.nodes.associate { node ->
                node.id to CampusRouteNode(
                    id = node.id,
                    buildingId = node.buildingId,
                    floorId = node.floorId,
                    kind = node.kind,
                    lat = node.lat,
                    lng = node.lng,
                )
            }
            val adjacency = mutableMapOf<String, MutableList<CampusRouteEdge>>()

            data.edges.forEach { edge ->
                if (edge.from !in nodes || edge.to !in nodes) return@forEach
                val routeEdge = CampusRouteEdge(
                    from = edge.from,
                    to = edge.to,
                    meters = edge.meters,
                    kind = edge.kind,
                )
                adjacency.getOrPut(edge.from) { mutableListOf() } += routeEdge
                adjacency.getOrPut(edge.to) { mutableListOf() } += routeEdge.reversed()
            }

            return CampusRouteGraph(
                nodes = nodes,
                adjacency = adjacency.mapValues { (_, value) -> value.toList() },
            )
        }
    }
}