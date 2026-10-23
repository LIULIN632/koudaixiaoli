package com.pocket.xiaoli.data.map.route

import com.pocket.xiaoli.data.map.model.CampusEdge
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import com.pocket.xiaoli.data.map.model.CampusNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusRouteInstructionBuilderTest {

    @Test
    fun sameFloorPathProducesDistanceStep() {
        val data = CampusMapTestFixtures.validData()
        val graph = CampusRouteGraph.from(data)
        val path = CampusRouter.shortestPath(graph, "n1", "n2")

        val steps = CampusRouteInstructionBuilder.build(data, graph, path)

        assertEquals(2, steps.size)
        assertTrue(steps.first().instruction.contains("101"))
        assertTrue(steps.last().instruction.contains("3 米"))
    }

    @Test
    fun crossFloorPathProducesStairInstruction() {
        val data = CampusMapData(
            campusId = "ytlg_main",
            nodes = listOf(
                CampusNode(id = "n1", buildingId = "b1", floorId = "f1", kind = "room"),
                CampusNode(id = "s1", buildingId = "b1", floorId = "f1", kind = "stairs"),
                CampusNode(id = "s2", buildingId = "b1", floorId = "f2", kind = "stairs"),
                CampusNode(id = "n2", buildingId = "b1", floorId = "f2", kind = "room"),
            ),
            edges = listOf(
                CampusEdge(from = "n1", to = "s1", meters = 2.0, kind = "corridor"),
                CampusEdge(from = "s1", to = "s2", meters = 8.0, kind = "stairs"),
                CampusEdge(from = "s2", to = "n2", meters = 2.0, kind = "corridor"),
            ),
        )
        val graph = CampusRouteGraph.from(data)
        val path = CampusRouter.shortestPath(graph, "n1", "n2")

        val instructions = CampusRouteInstructionBuilder.build(data, graph, path).map { it.instruction }

        assertTrue(instructions.any { it.contains("楼梯") })
        assertTrue(instructions.last().contains("到达"))
    }
}