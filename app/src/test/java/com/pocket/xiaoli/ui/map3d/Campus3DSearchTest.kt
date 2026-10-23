package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.BuiltinRoomCells
import com.pocket.xiaoli.data.map.Campus3DTestAssets
import com.pocket.xiaoli.data.map.model.Campus3DScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Campus3DSearchTest {

    private val scene: Campus3DScene by lazy { loadScene() }
    private val layout: Campus3DLayout by lazy {
        requireNotNull(Campus3DLayout.prepare(scene)) { "layout is null" }
    }

    @Test
    fun roomNumberFindsBuilding() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "413",
        )

        val room = results.firstOrNull { it.kind == Campus3DSearchKind.ROOM }
        assertEquals("413", room?.title)
        assertEquals("1号教学楼", (room?.hit as? Campus3DHit.Building)?.shape?.name)
        assertTrue(room?.subtitle.orEmpty().contains("4F"))
        assertEquals("4F", room?.floor)
    }

    @Test
    fun buildingAndRoomInOneQuery() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "1号教学楼 406",
        )

        assertTrue(results.any {
            it.kind == Campus3DSearchKind.BUILDING && it.title == "1号教学楼"
        })
        assertTrue(results.any {
            it.kind == Campus3DSearchKind.ROOM && it.title == "406"
        })
    }

    @Test
    fun buildingNameFindsBuildingWithoutRoomFlood() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "综合楼",
        )

        assertEquals("综合楼", results.firstOrNull()?.title)
        assertTrue(results.none { it.kind == Campus3DSearchKind.ROOM })
    }

    @Test
    fun blankQueryListsPlacesButNoRooms() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "",
        )

        assertTrue(results.any { it.kind == Campus3DSearchKind.BUILDING })
        assertTrue(results.any { it.kind == Campus3DSearchKind.WATER })
        assertTrue(results.none { it.kind == Campus3DSearchKind.ROOM })
    }

    @Test
    fun unknownQueryReturnsEmpty() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "不存在的楼",
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun waterQueryFindsWater() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "琴心湖",
        )

        assertEquals(Campus3DSearchKind.WATER, results.firstOrNull()?.kind)
    }

    @Test
    fun aliasSanJiaoFindsTracedBuilding() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "三教",
        )

        val building = results.firstOrNull { it.kind == Campus3DSearchKind.BUILDING }
        assertEquals("3号教学楼", building?.title)
        assertTrue(building?.subtitle.orEmpty().contains("又称三教"))
    }

    @Test
    fun teaching3HasBuildingInsteadOfPoiMarker() {
        val results = searchCampus3D(
            layout = layout,
            roomCells = BuiltinRoomCells.all,
            campusId = scene.campusId,
            query = "3号教学楼",
        )

        assertTrue(results.any { it.kind == Campus3DSearchKind.BUILDING && it.title == "3号教学楼" })
        assertTrue(results.none { it.kind == Campus3DSearchKind.POI })
    }

    private fun loadScene(): Campus3DScene = Campus3DTestAssets.loadScene()
}
