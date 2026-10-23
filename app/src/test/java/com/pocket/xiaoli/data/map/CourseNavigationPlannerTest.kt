package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.CampusBuilding
import com.pocket.xiaoli.data.map.model.CampusEdge
import com.pocket.xiaoli.data.map.model.CampusFloor
import com.pocket.xiaoli.data.map.model.CampusMapData
import com.pocket.xiaoli.data.map.model.CampusNode
import com.pocket.xiaoli.data.map.model.CampusRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseNavigationPlannerTest {

    @Test
    fun buildsRouteFromEntranceToRoom() {
        val plan = CourseNavigationPlanner.plan(
            courseRoom = "教学楼A 101",
            campusId = "ytlg_main",
            data = navigationData(),
        )

        assertTrue(plan?.hasRoute == true)
        assertEquals("r1", plan?.target?.roomId)
        assertEquals(listOf("n_entrance", "n_corridor", "n_room"), plan?.path?.nodeIds)
        assertTrue(plan?.steps?.last()?.instruction?.contains("到达 101") == true)
    }

    @Test
    fun startNodeOverrideIsUsed() {
        val base = navigationData()
        val data = base.copy(
            nodes = base.nodes + CampusNode(
                id = "n_entrance_west",
                buildingId = "b1",
                floorId = "f1",
                kind = "entrance",
            ),
            edges = base.edges + CampusEdge(
                from = "n_entrance_west",
                to = "n_corridor",
                meters = 9.0,
                kind = "corridor",
            ),
        )
        val plan = CourseNavigationPlanner.plan(
            courseRoom = "教学楼A 101",
            campusId = "ytlg_main",
            data = data,
            startNodeId = "n_entrance_west",
        )

        assertEquals("n_entrance_west", plan?.path?.nodeIds?.first())
    }

    @Test
    fun returnsTargetWhenRouteDataMissing() {
        val data = navigationData().copy(nodes = emptyList(), edges = emptyList())
        val plan = CourseNavigationPlanner.plan(
            courseRoom = "教学楼A 101",
            campusId = "ytlg_main",
            data = data,
        )

        assertEquals("r1", plan?.target?.roomId)
        assertFalse(plan?.hasRoute == true)
    }

    @Test
    fun unknownRoomReturnsNull() {
        assertNull(
            CourseNavigationPlanner.plan(
                courseRoom = "不存在的教室",
                campusId = "ytlg_main",
                data = navigationData(),
            )
        )
    }

    private fun navigationData(): CampusMapData = CampusMapData(
        campusId = "ytlg_main",
        buildings = listOf(
            CampusBuilding(id = "b1", name = "教学楼A", floorIds = listOf("f1"))
        ),
        floors = listOf(
            CampusFloor(id = "f1", buildingId = "b1", level = 1, name = "1F", roomIds = listOf("r1"))
        ),
        rooms = listOf(
            CampusRoom(
                id = "r1",
                buildingId = "b1",
                floorId = "f1",
                name = "101",
                type = "classroom",
                nodeId = "n_room",
            )
        ),
        nodes = listOf(
            CampusNode(id = "n_entrance", buildingId = "b1", floorId = "f1", kind = "entrance"),
            CampusNode(id = "n_corridor", buildingId = "b1", floorId = "f1", kind = "corridor"),
            CampusNode(id = "n_room", buildingId = "b1", floorId = "f1", kind = "room"),
        ),
        edges = listOf(
            CampusEdge(from = "n_entrance", to = "n_corridor", meters = 5.0, kind = "corridor"),
            CampusEdge(from = "n_corridor", to = "n_room", meters = 2.0, kind = "corridor"),
        ),
    )
}