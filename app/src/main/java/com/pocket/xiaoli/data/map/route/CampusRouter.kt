package com.pocket.xiaoli.data.map.route

import java.util.PriorityQueue

data class CampusRoutePreferences(
    /** 尽量避开楼梯。 */
    val avoidStairs: Boolean = false,
    /** 有电梯时优先走电梯。 */
    val preferElevator: Boolean = false,
    val stairsPenalty: Double = 3.0,
    val elevatorBonus: Double = 0.85,
) {
    fun weight(edge: CampusRouteEdge): Double {
        val base = edge.meters.coerceAtLeast(0.0)
        return when (edge.kind) {
            "stairs" -> if (avoidStairs) base * stairsPenalty else base
            "elevator" -> if (preferElevator) base * elevatorBonus else base
            else -> base
        }
    }
}

data class CampusRoutePath(
    val nodeIds: List<String> = emptyList(),
    val totalMeters: Double = 0.0,
    val edgeKinds: List<String> = emptyList(),
) {
    val isFound: Boolean get() = nodeIds.isNotEmpty()
}

/** 校园步行路线的 A* / Dijkstra 最短路径。 */
object CampusRouter {

    fun shortestPath(
        graph: CampusRouteGraph,
        startId: String,
        endId: String,
        preferences: CampusRoutePreferences = CampusRoutePreferences(),
    ): CampusRoutePath {
        if (startId !in graph.nodes || endId !in graph.nodes) return CampusRoutePath()
        if (startId == endId) return CampusRoutePath(nodeIds = listOf(startId))

        val distances = mutableMapOf<String, Double>()
        val previous = mutableMapOf<String, String>()
        val previousEdge = mutableMapOf<String, CampusRouteEdge>()
        val queue = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        distances[startId] = 0.0
        queue += startId to 0.0

        while (queue.isNotEmpty()) {
            val (current, currentDistance) = queue.poll()
            if (currentDistance > (distances[current] ?: Double.MAX_VALUE)) continue
            if (current == endId) break

            graph.neighbors(current).forEach { edge ->
                val next = edge.to
                val nextDistance = currentDistance + preferences.weight(edge)
                if (nextDistance < (distances[next] ?: Double.MAX_VALUE)) {
                    distances[next] = nextDistance
                    previous[next] = current
                    previousEdge[next] = edge
                    queue += next to nextDistance
                }
            }
        }

        if (endId !in previous) return CampusRoutePath()

        val nodeIds = ArrayDeque<String>()
        val edgeKinds = ArrayDeque<String>()
        var cursor = endId
        while (true) {
            nodeIds.addFirst(cursor)
            val edge = previousEdge[cursor]
            if (cursor == startId) break
            val parent = previous[cursor] ?: return CampusRoutePath()
            if (edge != null) edgeKinds.addFirst(edge.kind)
            cursor = parent
        }

        return CampusRoutePath(
            nodeIds = nodeIds.toList(),
            totalMeters = distances[endId] ?: 0.0,
            edgeKinds = edgeKinds.toList(),
        )
    }
}