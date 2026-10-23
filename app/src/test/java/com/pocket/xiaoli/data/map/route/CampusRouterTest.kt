package com.pocket.xiaoli.data.map.route

import com.pocket.xiaoli.data.map.model.CampusEdge
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.data.map.model.CampusMapTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusRouterTest {

    @Test
    fun findsShortestPathInSameFloor() {
        val graph = CampusRouteGraph.from(CampusMapTestFixtures.validData())
        val path = CampusRouter.shortestPath(graph, "n1", "n2")

        assertTrue(path.isFound)
        assertEquals(listOf("n1", "n2"), path.nodeIds)
        assertEquals(3.0, path.totalMeters, 0.001)
    }

    @Test
    fun startEqualsEndStillReturnsSingleNodePath() {
        val graph = CampusRouteGraph.from(CampusMapTestFixtures.validData())
        val path = CampusRouter.shortestPath(graph, "n1", "n1")

        assertTrue(path.isFound)
        assertEquals(listOf("n1"), path.nodeIds)
        assertEquals(0.0, path.totalMeters, 0.001)
    }

    @Test
    fun returnsEmptyWhenNodeMissing() {
        val graph = CampusRouteGraph.from(CampusMapTestFixtures.validData())
        val path = CampusRouter.shortestPath(graph, "n1", "missing")

        assertFalse(path.isFound)
    }

    @Test
    fun avoidStairsCanChooseElevatorEvenIfLonger() {
        val graph = CampusRouteGraph.from(twoRouteData())
        val defaultPath = CampusRouter.shortestPath(graph, "n1", "n2")
        assertTrue("expected stairs default, got ${defaultPath.nodeIds}", "s1" in defaultPath.nodeIds)

        val avoidStairs = CampusRouter.shortestPath(
            graph = graph,
            startId = "n1",
            endId = "n2",
            preferences = CampusRoutePreferences(avoidStairs = true),
        )
        assertTrue("expected elevator, got ${avoidStairs.nodeIds}", "e1" in avoidStairs.nodeIds)
        assertTrue("elevator" in avoidStairs.edgeKinds)

        val preferElevator = CampusRouter.shortestPath(
            graph = graph,
            startId = "n1",
            endId = "n2",
            preferences = CampusRoutePreferences(preferElevator = true, elevatorBonus = 0.5),
        )
        assertTrue("expected elevator, got ${preferElevator.nodeIds}", "e1" in preferElevator.nodeIds)
    }

    @Test
    fun findsCrossFloorPathThroughStairs() {
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

        assertEquals(listOf("n1", "s1", "s2", "n2"), path.nodeIds)
        assertEquals(12.0, path.totalMeters, 0.001)
        assertTrue("stairs" in path.edgeKinds)
    }

    private fun twoRouteData(): CampusMapData = CampusMapData(
        campusId = "ytlg_main",
        nodes = listOf(
            CampusNode(id = "n1", buildingId = "b1", floorId = "f1", kind = "room"),
            CampusNode(id = "s1", buildingId = "b1", floorId = "f1", kind = "stairs"),
            CampusNode(id = "e1", buildingId = "b1", floorId = "f1", kind = "elevator"),
            CampusNode(id = "n2", buildingId = "b1", floorId = "f1", kind = "room"),
        ),
        edges = listOf(
            CampusEdge(from = "n1", to = "s1", meters = 5.0, kind = "stairs"),
            CampusEdge(from = "s1", to = "n2", meters = 1.0, kind = "corridor"),
            CampusEdge(from = "n1", to = "e1", meters = 7.0, kind = "elevator"),
            CampusEdge(from = "e1", to = "n2", meters = 1.0, kind = "corridor"),
        ),
    )
}