package com.pocket.xiaoli.data.map.indoor

import androidx.compose.ui.geometry.Offset
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.abs
import kotlin.math.roundToInt

data class IndoorPlanLoadResult(
    val plan: IndoorFloorPlan?,
    val issues: List<String> = emptyList(),
) {
    val isValid: Boolean get() = plan != null && issues.isEmpty()
}

/**
 * `assets/indoor/` 下的 JSON → [IndoorFloorPlan]。
 *
 * 解析是全有或全无：任何一项校验失败就返回 null + 问题列表，避免半张图被画到屏幕上。
 * 当前只支持 `coordinateSystem.type = bay-index`；房间块与门点按开间号推导，
 * 等现场实测换成米制坐标时，JSON 和这里的几何来源要一起换。
 */
object IndoorPlanJson {

    private const val SCHEMA = "pocket-xiaoli.indoor-topology-draft/1"
    private const val STATUS_DRAFT = "draft-topology-unverified"
    private const val STATUS_VERIFIED = "verified"
    private const val DISTANCE_UNMEASURED = "unmeasured"
    private const val DISTANCE_MEASURED = "measured"

    private const val ROOM_OUTER_Y = 1.40f
    private const val ROOM_INNER_Y = 0.68f
    private const val ROOM_HALF_WIDTH = 0.44f

    private val NODE_KINDS = setOf("corridor", "room", "stairs")
    private val EDGE_ROLES = setOf("spine", "door", "stair-entry")

    fun parse(json: String): IndoorPlanLoadResult {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return failed("JSON 解析失败")
        val issues = mutableListOf<String>()

        if (root.str("schema") != SCHEMA) issues += "schema 不是 $SCHEMA"
        val status = when (val raw = root.str("status")) {
            STATUS_DRAFT -> IndoorPlanStatus.TopologyDraft
            STATUS_VERIFIED -> IndoorPlanStatus.Verified
            else -> {
                issues += "未知状态：$raw"
                null
            }
        }
        if (status == IndoorPlanStatus.TopologyDraft &&
            (!root.flag("doNotImport") || !root.flag("doNotRender"))
        ) {
            issues += "草案必须标记 doNotImport / doNotRender"
        }
        val distanceStatus = root.str("distanceStatus")
        if (status == IndoorPlanStatus.TopologyDraft && distanceStatus != DISTANCE_UNMEASURED) {
            issues += "草案的 distanceStatus 必须是 $DISTANCE_UNMEASURED"
        }
        if (status == IndoorPlanStatus.Verified && distanceStatus != DISTANCE_MEASURED) {
            issues += "实测底图的 distanceStatus 必须是 $DISTANCE_MEASURED"
        }
        if (root.obj("coordinateSystem")?.str("type") != "bay-index") {
            issues += "当前只支持 bay-index 坐标"
        }

        val buildingId = root.str("buildingId") ?: run {
            issues += "缺少 buildingId"
            ""
        }
        val buildingName = root.str("buildingName") ?: run {
            issues += "缺少 buildingName"
            ""
        }
        val floorId = root.str("floorId") ?: run {
            issues += "缺少 floorId"
            ""
        }
        val floorLevel = root.num("floorLevel")?.toInt() ?: run {
            issues += "缺少 floorLevel"
            0
        }
        val planId = root.str("planId") ?: "plan_${floorId}_topology_draft"
        val corridorLabel = root.str("corridorLabel") ?: run {
            issues += "缺少 corridorLabel"
            "主走廊"
        }
        val corridorWidth = root.num("corridorWidthUnits")?.toFloat()?.takeIf { it > 0f } ?: 0.60f

        val nodeArray = root.array("nodes")
        if (nodeArray == null) {
            issues += "缺少 nodes"
            return failed(issues)
        }
        val nodes = mutableListOf<ParsedNode>()
        nodeArray.forEachIndexed { index, element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject
            if (obj == null) {
                issues += "nodes[$index] 不是对象"
                return@forEachIndexed
            }
            val id = obj.str("id")
            val kind = obj.str("kind")
            val name = obj.str("name")
            val x = obj.num("planX")?.toFloat()
            val y = obj.num("planY")?.toFloat()
            if (id == null || kind == null || name == null || x == null || y == null) {
                issues += "nodes[$index] 缺少 id / kind / name / planX / planY"
                return@forEachIndexed
            }
            if (kind !in NODE_KINDS) issues += "$id 的 kind 不支持：$kind"
            if (nodes.any { it.id == id }) issues += "节点 id 重复：$id"
            nodes += ParsedNode(id, kind, name, Offset(x, y), obj)
        }

        val corridorNodes = nodes.filter { it.kind == "corridor" }.sortedBy { it.point.x }
        if (corridorNodes.isEmpty()) issues += "至少需要一个走廊节点"
        if (corridorNodes.any { abs(it.point.y) > 0.001f }) {
            issues += "开间坐标只支持一条东西向直廊（走廊 planY 必须为 0）"
        }
        val corridorByBay = corridorNodes.associate { it.point.x.roundToInt() to it.id }

        val rooms = nodes.filter { it.kind == "room" }.mapNotNull { node ->
            parseRoom(node, corridorByBay, issues)
        }
        val facilities = nodes.filter { it.kind == "stairs" }.map { node ->
            IndoorFacility(
                id = "facility_${node.id.removePrefix("n_")}",
                nodeId = node.id,
                name = node.name,
                shortLabel = "梯",
                kind = IndoorFacilityKind.Stairs,
                point = node.point,
                linkedBuiltinRoomId = node.json.str("linkedBuiltinRoomId"),
            )
        }
        val startOptions = facilities.map { facility ->
            IndoorStartOption(nodeId = facility.nodeId, label = toStartLabel(facility.name))
        }

        val edgeArray = root.array("edges")
        if (edgeArray == null) {
            issues += "缺少 edges"
            return failed(issues)
        }
        val nodeIds = nodes.map { it.id }.toSet()
        val edges = mutableListOf<IndoorEdge>()
        val seenPairs = mutableSetOf<Set<String>>()
        edgeArray.forEachIndexed { index, element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject
            if (obj == null) {
                issues += "edges[$index] 不是对象"
                return@forEachIndexed
            }
            val from = obj.str("from")
            val to = obj.str("to")
            val role = obj.str("edgeRole")
            val kind = obj.str("kind") ?: "corridor"
            if (from == null || to == null || role == null) {
                issues += "edges[$index] 缺少 from / to / edgeRole"
                return@forEachIndexed
            }
            if (from !in nodeIds || to !in nodeIds) issues += "边 $from → $to 的端点不存在"
            if (from == to) issues += "边 $from 不能自环"
            if (role !in EDGE_ROLES) issues += "边 $from → $to 的 role 不支持：$role"
            if (!seenPairs.add(setOf(from, to))) issues += "重复边：$from ↔ $to"
            val meters = obj.num("meters")
            if (status == IndoorPlanStatus.TopologyDraft && meters != null) {
                issues += "草案边不能带米数：$from → $to"
            }
            if (status == IndoorPlanStatus.Verified && (meters == null || meters <= 0.0)) {
                issues += "实测边必须有正米数：$from → $to"
            }
            edges += IndoorEdge(from = from, to = to, kind = kind, role = role)
        }

        validateDoorEdges(rooms, corridorByBay, edges, issues)
        validateStairEntries(facilities, corridorNodes.map { it.id }.toSet(), edges, issues)
        validateSpine(corridorNodes.map { it.id }, edges, issues)
        validateConnectivity(nodeIds, edges, issues)

        val startNodeId = root.str("startNodeId") ?: startOptions.firstOrNull()?.nodeId
        if (startNodeId == null) {
            issues += "缺少 startNodeId，且没有可用的楼梯节点"
        } else if (facilities.none { it.nodeId == startNodeId }) {
            issues += "默认起点不是楼梯：$startNodeId"
        }
        val startLabel = startOptions.firstOrNull { it.nodeId == startNodeId }?.label
            ?: facilities.firstOrNull { it.nodeId == startNodeId }?.name
            ?: ""

        validateCounts(root, nodes.size, edges.size, rooms.size, corridorNodes.size, facilities.size, edges, issues)

        if (issues.isNotEmpty()) return failed(issues)
        val plan = IndoorFloorPlan(
            id = planId,
            buildingId = buildingId,
            buildingName = buildingName,
            floorId = floorId,
            floorLabel = "${floorLevel}F",
            status = requireNotNull(status),
            rooms = rooms,
            corridors = listOf(
                IndoorCorridor(
                    id = "c_${floorId}_spine",
                    label = corridorLabel,
                    points = corridorNodes.map { it.point },
                    widthUnits = corridorWidth,
                )
            ),
            facilities = facilities,
            nodes = nodes.map { IndoorNode(it.id, it.point, it.kind, it.name) },
            edges = edges,
            startNodeId = requireNotNull(startNodeId),
            startLabel = startLabel,
            startOptions = startOptions,
            sourceDate = root.str("signDate"),
            generatedAt = root.str("generatedAt"),
        )
        return IndoorPlanLoadResult(plan)
    }

    private fun parseRoom(
        node: ParsedNode,
        corridorByBay: Map<Int, String>,
        issues: MutableList<String>,
    ): IndoorRoom? {
        val json = node.json
        val side = when (val raw = json.str("side")) {
            "north" -> IndoorSide.North
            "south" -> IndoorSide.South
            else -> {
                issues += "${node.id} 的 side 不支持：$raw"
                return null
            }
        }
        val kind = when (val raw = json.str("roomType")) {
            "classroom" -> IndoorRoomKind.Classroom
            "restroom" -> IndoorRoomKind.Restroom
            else -> {
                issues += "${node.id} 的 roomType 不支持：$raw"
                return null
            }
        }
        val planX = node.point.x
        if (abs(planX - planX.roundToInt()) > 0.001f) {
            issues += "${node.id} 的 planX 不是开间整数：$planX"
            return null
        }
        val bay = planX.roundToInt()
        if (bay !in corridorByBay) issues += "${node.id} 所在开间 $bay 没有走廊节点"
        if (side == IndoorSide.North && node.point.y >= 0f) issues += "${node.id} 标为北区，planY 应为负"
        if (side == IndoorSide.South && node.point.y <= 0f) issues += "${node.id} 标为南区，planY 应为正"
        val signRow = json.num("signRow")?.toInt()
        if (signRow != null && signRow != bay) issues += "${node.id} 的 signRow 与 planX 不一致"
        val provisional = json.str("positionStatus") == "provisional"
        val linked = json.str("linkedBuiltinRoomId")
        if (provisional && linked != null) issues += "${node.id} 待核时不能链接 Room 库"
        val innerY = if (side == IndoorSide.North) -ROOM_INNER_Y else ROOM_INNER_Y
        val outerY = if (side == IndoorSide.North) -ROOM_OUTER_Y else ROOM_OUTER_Y
        return IndoorRoom(
            id = "room_" + node.id.removePrefix("n_"),
            nodeId = node.id,
            doorNodeId = node.id,
            name = node.name,
            shortLabel = when {
                kind == IndoorRoomKind.Classroom -> node.name
                provisional -> "WC?"
                else -> "WC"
            },
            side = side,
            bay = bay,
            kind = kind,
            polygon = listOf(
                Offset(bay - ROOM_HALF_WIDTH, outerY),
                Offset(bay + ROOM_HALF_WIDTH, outerY),
                Offset(bay + ROOM_HALF_WIDTH, innerY),
                Offset(bay - ROOM_HALF_WIDTH, innerY),
            ),
            doorPoint = Offset(bay.toFloat(), innerY),
            labelPoint = Offset(bay.toFloat(), (innerY + outerY) / 2f),
            linkedBuiltinRoomId = linked,
            provisional = provisional,
            note = json.str("note"),
        )
    }

    private fun validateDoorEdges(
        rooms: List<IndoorRoom>,
        corridorByBay: Map<Int, String>,
        edges: List<IndoorEdge>,
        issues: MutableList<String>,
    ) {
        rooms.forEach { room ->
            val doorEdges = edges.filter { edge ->
                edge.role == "door" && (edge.from == room.nodeId || edge.to == room.nodeId)
            }
            if (doorEdges.size != 1) {
                issues += "${room.name} 应恰好有一条门边，实际 ${doorEdges.size}"
                return@forEach
            }
            val other = doorEdges.single().let { if (it.from == room.nodeId) it.to else it.from }
            val expected = corridorByBay[room.bay]
            if (expected == null || other != expected) {
                issues += "${room.name} 的门边没有接同开间走廊节点"
            }
        }
    }

    private fun validateStairEntries(
        facilities: List<IndoorFacility>,
        corridorIds: Set<String>,
        edges: List<IndoorEdge>,
        issues: MutableList<String>,
    ) {
        facilities.forEach { facility ->
            val entries = edges.filter { edge ->
                edge.role == "stair-entry" &&
                    (edge.from == facility.nodeId || edge.to == facility.nodeId)
            }
            if (entries.size != 1) {
                issues += "${facility.name} 应恰好有一条梯口边，实际 ${entries.size}"
                return@forEach
            }
            val other = entries.single().let {
                if (it.from == facility.nodeId) it.to else it.from
            }
            if (other !in corridorIds) issues += "${facility.name} 的梯口边没有接走廊"
        }
    }

    private fun validateSpine(
        corridorIds: List<String>,
        edges: List<IndoorEdge>,
        issues: MutableList<String>,
    ) {
        val corridorSet = corridorIds.toSet()
        val spine = edges.filter { it.role == "spine" }
        if (spine.size != corridorIds.size - 1) {
            issues += "走廊链应有 ${corridorIds.size - 1} 条边，实际 ${spine.size}"
        }
        val degree = mutableMapOf<String, Int>()
        spine.forEach { edge ->
            if (edge.from !in corridorSet || edge.to !in corridorSet) {
                issues += "走廊链边 ${edge.from} → ${edge.to} 端点不是走廊"
            }
            degree[edge.from] = (degree[edge.from] ?: 0) + 1
            degree[edge.to] = (degree[edge.to] ?: 0) + 1
        }
        if (degree.size != corridorIds.size) issues += "有走廊节点不在链上"
        if (degree.values.count { it == 1 } != 2) issues += "走廊链必须恰好有两个端点"
        if (degree.values.any { it !in 1..2 }) issues += "走廊链出现分叉"
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        corridorIds.firstOrNull()?.let {
            seen += it
            queue += it
        }
        val adjacency = mutableMapOf<String, MutableList<String>>()
        spine.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() } += edge.to
            adjacency.getOrPut(edge.to) { mutableListOf() } += edge.from
        }
        while (queue.isNotEmpty()) {
            adjacency[queue.removeFirst()].orEmpty().forEach { next ->
                if (seen.add(next)) queue += next
            }
        }
        if (seen.size != corridorIds.size) issues += "走廊链不连通"
    }

    private fun validateConnectivity(
        nodeIds: Set<String>,
        edges: List<IndoorEdge>,
        issues: MutableList<String>,
    ) {
        if (nodeIds.isEmpty()) return
        val adjacency = mutableMapOf<String, MutableList<String>>()
        edges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() } += edge.to
            adjacency.getOrPut(edge.to) { mutableListOf() } += edge.from
        }
        val seen = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        val first = nodeIds.first()
        seen += first
        queue += first
        while (queue.isNotEmpty()) {
            adjacency[queue.removeFirst()].orEmpty().forEach { next ->
                if (seen.add(next)) queue += next
            }
        }
        val unreachable = nodeIds - seen
        if (unreachable.isNotEmpty()) issues += "有 ${unreachable.size} 个节点走不到：${unreachable.take(3)}"
    }

    private fun validateCounts(
        root: JsonObject,
        nodeCount: Int,
        edgeCount: Int,
        roomCount: Int,
        corridorCount: Int,
        stairCount: Int,
        edges: List<IndoorEdge>,
        issues: MutableList<String>,
    ) {
        val expected = root.obj("expectedCounts") ?: return
        fun check(name: String, actual: Int) {
            val wanted = expected.num(name)?.toInt() ?: return
            if (actual != wanted) issues += "$name：$actual，期望 $wanted"
        }
        check("nodes", nodeCount)
        check("edges", edgeCount)
        check("roomNodes", roomCount)
        check("corridorNodes", corridorCount)
        check("stairNodes", stairCount)
        check("doorEdges", edges.count { it.role == "door" })
        check("stairEntryEdges", edges.count { it.role == "stair-entry" })
        check("spineEdges", edges.count { it.role == "spine" })
    }

    private fun toStartLabel(name: String): String =
        if ("楼梯" in name) name.replace("楼梯", "梯口") else "${name}口"

    private fun failed(message: String): IndoorPlanLoadResult =
        IndoorPlanLoadResult(plan = null, issues = listOf(message))

    private fun failed(issues: List<String>): IndoorPlanLoadResult =
        IndoorPlanLoadResult(plan = null, issues = issues.toList())

    private data class ParsedNode(
        val id: String,
        val kind: String,
        val name: String,
        val point: Offset,
        val json: JsonObject,
    )
}

private fun JsonObject.str(name: String): String? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private fun JsonObject.num(name: String): Double? =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

private fun JsonObject.flag(name: String): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.array(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
