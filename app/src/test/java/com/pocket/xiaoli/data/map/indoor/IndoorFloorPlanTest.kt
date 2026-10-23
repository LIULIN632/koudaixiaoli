package com.pocket.xiaoli.data.map.indoor

import androidx.compose.ui.geometry.Offset
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndoorFloorPlanTest {

    private val app = RuntimeEnvironment.getApplication()
    private val rawJson: String by lazy {
        app.assets.open(IndoorPlanDataSource.TEACHING1_FLOOR4_DRAFT)
            .bufferedReader()
            .use { it.readText() }
    }
    private val result: IndoorPlanLoadResult by lazy {
        IndoorPlanDataSource.fromAssets(app).load()
    }
    private val plan: IndoorFloorPlan by lazy {
        requireNotNull(result.plan) { result.issues.joinToString("\n") }
    }

    @Test
    fun `草案资产能通过解析与校验`() {
        assertTrue(result.issues.joinToString("\n"), result.isValid)
        assertEquals(37, plan.nodes.size)
        assertEquals(36, plan.edges.size)
        assertEquals(13, plan.rooms.count { it.side == IndoorSide.North })
        assertEquals(7, plan.rooms.count { it.side == IndoorSide.South })
        assertEquals(20, plan.rooms.size)
        assertEquals(4, plan.facilities.size)
        assertEquals(4, plan.startOptions.size)
        assertEquals(13, plan.corridors.single().points.size)
        assertEquals(37, plan.nodes.map { it.id }.distinct().size)
        assertEquals(36, plan.edges.map { it.from to it.to }.distinct().size)
        assertEquals("2026-09-23", plan.sourceDate)
    }

    @Test
    fun `草案状态不能进入面向用户的页面`() {
        assertEquals(IndoorPlanStatus.TopologyDraft, plan.status)
        assertFalse(plan.canRenderToUsers)
        assertFalse(plan.startLabel.contains("大门"))
    }

    @Test
    fun `每间房都有门边且一条边只连一次`() {
        val corridorIds = plan.nodes.filter { it.kind == "corridor" }.map { it.id }.toSet()
        assertEquals(13, corridorIds.size)
        plan.rooms.forEach { room ->
            val doorEdges = plan.edges.filter { edge ->
                edge.role == "door" &&
                    ((edge.from == room.nodeId && edge.to in corridorIds) ||
                        (edge.to == room.nodeId && edge.from in corridorIds))
            }
            assertEquals("${room.name} 应只有一条门边", 1, doorEdges.size)
            assertEquals(
                "门边应接同开间走廊节点",
                "n_f1j4_corr_${room.bay.toString().padStart(2, '0')}",
                doorEdges.single().let { if (it.from == room.nodeId) it.to else it.from },
            )
        }
    }

    @Test
    fun `四个楼梯口都能走到每一间房`() {
        assertEquals(
            listOf("东北梯口", "东南梯口", "西北梯口", "西南梯口"),
            plan.startOptions.map { it.label },
        )
        plan.startOptions.forEach { start ->
            plan.rooms.forEach { room ->
                val route = plan.routeTo(room.id, start.nodeId)
                assertNotNull("${start.label} → ${room.name} 应可达", route)
                requireNotNull(route)
                assertEquals(start.nodeId, route.nodeIds.first())
                assertEquals(room.doorNodeId, route.nodeIds.last())
                assertEquals(start.label, route.startLabel)
                assertTrue(route.nodeIds.contains(room.nodeId))
                assertEquals(3, route.steps.size)
                assertTrue(
                    "路线步骤不能出现伪距离：${route.steps}",
                    route.steps.none {
                        it.contains("米") || it.contains("分钟") || it.contains("min")
                    },
                )
            }
        }
    }

    @Test
    fun `默认起点仍是东北楼梯口`() {
        val room415 = plan.rooms.first { it.name == "415" }
        val route = requireNotNull(plan.routeTo(room415.id))
        assertEquals("n_f1j4_stair_ne", route.startNodeId)
        assertEquals("东北梯口", route.startLabel)
    }

    @Test
    fun `路线方向按所选起点与开间序号计算`() {
        val room415 = plan.rooms.first { it.name == "415" }
        val fromEast = requireNotNull(plan.routeTo(room415.id, "n_f1j4_stair_ne"))
        assertTrue(fromEast.steps[1].contains("向西"))
        assertTrue(fromEast.steps[1].contains("415"))
        assertTrue(fromEast.steps[2].contains(IndoorSide.North.label))

        val fromWest = requireNotNull(plan.routeTo(room415.id, "n_f1j4_stair_sw"))
        assertTrue(fromWest.steps[1].contains("向东"))
        assertTrue(fromWest.steps[0].contains("西南梯口"))
    }

    @Test
    fun `非楼梯口不能当起点`() {
        assertNull(plan.routeTo(plan.rooms.first().id, "n_f1j4_corr_00"))
        assertNull(plan.routeTo(plan.rooms.first().id, "n_f1j4_413"))
        assertNull(plan.routeTo("missing_room"))
    }

    @Test
    fun `南区卫生间保持待核且不编造 Room 库链接`() {
        val restroom = plan.rooms.first { it.name == "南区卫生间" }
        assertTrue(restroom.provisional)
        assertNull(restroom.linkedBuiltinRoomId)
        assertNotNull(restroom.note)
        val route = requireNotNull(plan.routeTo(restroom.id))
        assertNotNull(route.caution)
        assertTrue(route.caution.orEmpty().contains("待现场核对"))
    }

    @Test
    fun `起点是 4F 楼梯口而不是东大门`() {
        val start = requireNotNull(plan.node(plan.startNodeId))
        assertEquals("stairs", start.kind)
        assertFalse(plan.startLabel.contains("大门"))
        assertFalse(plan.rooms.any { it.name.contains("大门") })
    }

    @Test
    fun `内置房间覆盖数与 4F 指引牌一致`() {
        val linkedRooms = plan.rooms.count { it.linkedBuiltinRoomId != null }
        val linkedStairs = plan.facilities.count { it.linkedBuiltinRoomId != null }
        assertEquals(19, linkedRooms)
        assertEquals(4, linkedStairs)
        assertEquals(23, linkedRooms + linkedStairs)
        assertEquals(1, plan.rooms.count { it.provisional })
        assertTrue(
            plan.rooms
                .filterNot { it.provisional }
                .all { it.linkedBuiltinRoomId?.startsWith("builtin:1号教学楼/4/") == true },
        )
    }

    @Test
    fun `点按房间中心能命中房间`() {
        val width = 360f
        val height = 252f
        plan.rooms.forEach { room ->
            val tap = plan.mapper.toScreen(room.labelPoint, width, height)
            assertEquals(room.id, plan.roomAt(tap, width, height)?.id)
        }
    }

    @Test
    fun `点走廊和画布外不会误选房间`() {
        val width = 360f
        val height = 252f
        val corridorTap = plan.mapper.toScreen(Offset(6f, 0f), width, height)
        assertNull(plan.roomAt(corridorTap, width, height))
        assertNull(plan.roomAt(Offset(-20f, -20f), width, height))
        assertNull(plan.roomAt(Offset.Zero, 0f, 0f))
    }

    @Test
    fun `坐标映射可逆且刻意非等比例`() {
        val width = 360f
        val height = 252f
        val point = plan.rooms.first { it.name == "415" }.labelPoint
        val screen = plan.mapper.toScreen(point, width, height)
        val back = plan.mapper.toPlan(screen, width, height)
        assertEquals(point.x, back.x, 0.001f)
        assertEquals(point.y, back.y, 0.001f)
        assertTrue(
            "纵向拉伸是为了让房间号可读，不应改回等比例",
            plan.mapper.unitY(height) > plan.mapper.unitX(width) * 1.5f,
        )
    }

    @Test
    fun `未知状态会被拒绝`() {
        val broken = rawJson.replace(
            "\"status\": \"draft-topology-unverified\"",
            "\"status\": \"draft\"",
        )
        val parsed = IndoorPlanJson.parse(broken)
        assertNull(parsed.plan)
        assertTrue(parsed.issues.any { it.contains("未知状态") })
    }

    @Test
    fun `断开的边会被拒绝`() {
        val root = JsonParser.parseString(rawJson).asJsonObject
        root.getAsJsonArray("edges").remove(0)
        val parsed = IndoorPlanJson.parse(root.toString())
        assertNull(parsed.plan)
        assertTrue(parsed.issues.joinToString("\n"), parsed.issues.isNotEmpty())
    }

    @Test
    fun `草案边带米数会被拒绝`() {
        val root = JsonParser.parseString(rawJson).asJsonObject
        root.getAsJsonArray("edges")[0].asJsonObject.addProperty("meters", 3.0)
        val parsed = IndoorPlanJson.parse(root.toString())
        assertNull(parsed.plan)
        assertTrue(parsed.issues.any { it.contains("不能带米数") })
    }

    @Test
    fun `草案缺少 doNotRender 会被拒绝`() {
        val broken = rawJson.replace("\"doNotRender\": true,", "")
        val parsed = IndoorPlanJson.parse(broken)
        assertNull(parsed.plan)
        assertTrue(parsed.issues.any { it.contains("doNotImport") || it.contains("doNotRender") })
    }
}
