package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.route.CampusRouteGraph
import com.pocket.xiaoli.data.map.route.CampusRouteInstructionBuilder
import com.pocket.xiaoli.data.map.route.CampusRoutePath
import com.pocket.xiaoli.data.map.route.CampusRouteStep
import com.pocket.xiaoli.data.map.route.CampusRouter

data class CourseNavigationPlan(
    val target: CourseRoomTarget,
    val path: CampusRoutePath? = null,
    val steps: List<CampusRouteStep> = emptyList(),
) {
    val hasRoute: Boolean get() = path?.isFound == true
}

/**
 * 课程教室 -> 地图定位 -> 步行路线。
 *
 * 没有入口节点 / 路网时，仍然会返回 target，UI 可以只展示定位和楼层图。
 */
object CourseNavigationPlanner {

    fun plan(
        courseRoom: String,
        campusId: String,
        data: CampusMapData,
        startNodeId: String? = null,
    ): CourseNavigationPlan? {
        val target = CourseRoomLocator.locate(courseRoom, campusId, data) ?: return null
        val graph = CampusRouteGraph.from(data)
        val targetNodeId = target.roomId
            ?.let { roomId -> data.rooms.firstOrNull { it.id == roomId }?.nodeId }
            ?: return CourseNavigationPlan(target)

        val effectiveStartNodeId = startNodeId?.takeIf { it in graph.nodes }
            ?: findStartNode(data, graph, target.buildingId, targetNodeId)
        if (effectiveStartNodeId == null || effectiveStartNodeId == targetNodeId) {
            return CourseNavigationPlan(target)
        }

        val path = CampusRouter.shortestPath(graph, effectiveStartNodeId, targetNodeId)
        return CourseNavigationPlan(
            target = target,
            path = path.takeIf { it.isFound },
            steps = if (path.isFound) {
                CampusRouteInstructionBuilder.build(data, graph, path)
            } else {
                emptyList()
            },
        )
    }

    private fun findStartNode(
        data: CampusMapData,
        graph: CampusRouteGraph,
        buildingId: String?,
        targetNodeId: String,
    ): String? {
        if (buildingId == null) return targetNodeId
        val candidates = graph.nodes.values.filter { it.buildingId == buildingId }
        return candidates.firstOrNull { it.kind == "entrance" }?.id
            ?: candidates.firstOrNull { it.kind == "outdoor" }?.id
            ?: candidates.firstOrNull { it.kind == "corridor" }?.id
            ?: targetNodeId
    }
}