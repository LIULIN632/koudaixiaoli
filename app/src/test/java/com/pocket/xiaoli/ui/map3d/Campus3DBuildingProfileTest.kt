package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.BuiltinRoomCells
import com.pocket.xiaoli.data.map.Campus3DTestAssets
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.RoomCellType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Campus3DBuildingProfileTest {

    @Test
    fun `占地按轮廓平方米计算并扣除洞`() {
        val footprint = rectangle(40f, 20f)
        val facts = facts(
            shape = shape(
                footprint = footprint,
                holes = listOf(rectangle(4f, 4f, offsetX = 10f, offsetZ = 10f)),
            )
        )

        assertEquals(784.0, facts.footprintAreaSqm, 0.01)
        assertEquals(40.0, facts.widthMeters, 0.01)
        assertEquals(20.0, facts.depthMeters, 0.01)
    }

    @Test
    fun `房间按楼栋与校区过滤并聚合楼层和类型`() {
        val cells = listOf(
            room(floor = "1", roomNumber = "A406"),
            room(floor = "4", roomNumber = "A405"),
            room(floor = "1", roomNumber = "东北楼梯", type = RoomCellType.STAIR),
            room(floor = "4", roomNumber = "北区卫生间", type = RoomCellType.RESTROOM),
            room(floor = "2", roomNumber = "B201", building = "第一教学楼"),
            room(floor = "4", roomNumber = "C301", building = "2号教学楼"),
            room(floor = "4", roomNumber = "D401", campusId = "other"),
        )

        val facts = facts(roomCells = cells)

        assertEquals(4, facts.rooms.totalEntries)
        assertEquals(2, facts.rooms.classrooms)
        assertEquals(2, facts.rooms.coveredFloors)
        assertEquals(listOf("1F", "4F"), facts.rooms.floors.map { it.label })
        assertEquals(2, facts.rooms.floors.first { it.label == "4F" }.totalEntries)
        assertEquals(1, facts.rooms.floors.first { it.label == "4F" }.classrooms)
        assertEquals(RoomCellType.CLASSROOM, facts.rooms.types.first().type)
        assertEquals(2, facts.rooms.types.first().count)
    }

    @Test
    fun `描点说明会标成非 OSM`() {
        val facts = facts(
            shape = shape(note = "轮廓按 Bing 正射影像人工描点，误差约 ±3–5 米；非 OSM 数据。")
        )

        assertEquals(BuildingOutlineSource.Traced, facts.outlineSource)
    }

    @Test
    fun `普通 OSM 注记不会被误判成描点`() {
        val facts = facts(shape = shape(note = "公开地图标注位置匹配"))

        assertEquals(BuildingOutlineSource.OpenStreetMap, facts.outlineSource)
        assertEquals("公开地图标注位置匹配", facts.outlineNote)
    }

    @Test
    fun `内置一教数据覆盖 1F 到 5F`() {
        val scene = Campus3DTestAssets.loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "1号教学楼" }

        val facts = buildingProfileFacts(
            building = building,
            campusId = scene.campusId,
            roomCells = BuiltinRoomCells.all,
        )

        assertEquals("第一教学楼", facts.sourceName)
        assertEquals(6, facts.levels)
        assertTrue(facts.rooms.classrooms > 0)
        assertEquals(5, facts.rooms.coveredFloors)
        assertEquals(listOf("1F", "2F", "3F", "4F", "5F"), facts.rooms.floors.map { it.label })
        assertFalse(facts.rooms.floors.any { it.label == "6F" })
        assertEquals(
            facts.rooms.classrooms,
            facts.rooms.types.first { it.type == RoomCellType.CLASSROOM }.count,
        )
    }

    private fun facts(
        shape: Campus3DBuildingShape = shape(),
        roomCells: List<RoomCell> = emptyList(),
    ): BuildingProfileFacts =
        buildingProfileFacts(
            building = shape,
            campusId = "ytlg_main",
            roomCells = roomCells,
        )

    private fun shape(
        footprint: List<Vec2> = rectangle(40f, 20f),
        holes: List<List<Vec2>> = emptyList(),
        note: String = "",
    ): Campus3DBuildingShape =
        Campus3DBuildingShape(
            id = "way/test",
            name = "1号教学楼",
            category = "teaching",
            levels = 6,
            levelsEstimated = false,
            height = 20f,
            footprint = footprint,
            holes = holes,
            centroid = Vec2(20f, 10f),
            bounds = Rect2D.of(footprint),
            roofDetails = emptyList(),
            sourceName = "第一教学楼",
            note = note,
        )

    private fun room(
        campusId: String = "ytlg_main",
        floor: String = "1",
        roomNumber: String = "101",
        type: RoomCellType = RoomCellType.CLASSROOM,
        building: String = "1号教学楼",
    ): RoomCell =
        RoomCell(
            id = "$campusId-$building-$floor-$roomNumber",
            campusId = campusId,
            building = building,
            floor = floor,
            room = roomNumber,
            type = type.key,
        )

    private fun rectangle(
        width: Float,
        depth: Float,
        offsetX: Float = 0f,
        offsetZ: Float = 0f,
    ): List<Vec2> = listOf(
        Vec2(offsetX, offsetZ),
        Vec2(offsetX + width, offsetZ),
        Vec2(offsetX + width, offsetZ + depth),
        Vec2(offsetX, offsetZ + depth),
    )
}
