package com.pocket.xiaoli.data.map.indoor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * 一层楼的室内平面图。
 *
 * 当前只有「开间序号」坐标（未实测），所以所有房间都叫 *示意*；将来接入米制底图时，
 * 只需换掉 [rooms] / [nodes] 的坐标与 [mapper] 的范围，画布与交互不用改。
 *
 * 节点与边刻意保留成 [CampusRouteGraph][com.pocket.xiaoli.data.map.route.CampusRouteGraph]
 * 能直接吃的样子：走廊节点 → 房间节点 → 楼梯节点，后续换真实数据时路由仍是同一套。
 */
@Immutable
data class IndoorFloorPlan(
    val id: String,
    val buildingId: String,
    val buildingName: String,
    val floorId: String,
    val floorLabel: String,
    val status: IndoorPlanStatus,
    val rooms: List<IndoorRoom>,
    val corridors: List<IndoorCorridor>,
    val facilities: List<IndoorFacility>,
    val nodes: List<IndoorNode>,
    val edges: List<IndoorEdge>,
    /** 默认起点，必须是 [startOptions] 之一。 */
    val startNodeId: String,
    val startLabel: String,
    val startOptions: List<IndoorStartOption> = emptyList(),
    /** 指引牌拍摄日期；没有实测数据时为 null。 */
    val sourceDate: String? = null,
    val generatedAt: String? = null,
    val mapper: IndoorCoordinateMapper = IndoorCoordinateMapper(),
) {
    fun room(id: String): IndoorRoom? = rooms.firstOrNull { it.id == id }

    fun node(id: String): IndoorNode? = nodes.firstOrNull { it.id == id }

    /**
     * 只有现场实测过的底图才允许出现在面向用户的页面。
     * 草案仍可给开发者实验室预览，但入口必须继续留在内部。
     */
    val canRenderToUsers: Boolean get() = status == IndoorPlanStatus.Verified

    /** 点按点是否落在某间房的平面轮廓里；命中不到返回 null。 */
    fun roomAt(point: Offset, width: Float, height: Float): IndoorRoom? {
        if (width <= 0f || height <= 0f) return null
        val planPoint = mapper.toPlan(point, width, height)
        return rooms.firstOrNull { pointInPolygon(planPoint, it.polygon) }
    }
}

enum class IndoorPlanStatus(val label: String) {
    /** 依据指引牌整理的连接关系，无尺寸、无门位。 */
    TopologyDraft("拓扑示意 · 未实测"),

    /** 现场量过的底图。 */
    Verified("实测底图"),
}

enum class IndoorSide(val label: String) {
    North("北区"),
    South("南区"),
}

@Immutable
data class IndoorStartOption(
    val nodeId: String,
    /** 例如「东北梯口」；不会写成「东大门」。 */
    val label: String,
)

enum class IndoorRoomKind {
    Classroom,
    Restroom,
}

enum class IndoorFacilityKind {
    Stairs,
}

@Immutable
data class IndoorRoom(
    val id: String,
    /** 路由终点节点（当前就是门口节点）。 */
    val nodeId: String,
    val doorNodeId: String,
    /** 列表与详情里的完整名称，例如「413」「北区卫生间」。 */
    val name: String,
    /** 画布内的短标签，例如「413」「WC」。 */
    val shortLabel: String,
    val side: IndoorSide,
    /** 指引牌行号；0 = 东端，向西递增。 */
    val bay: Int,
    val kind: IndoorRoomKind,
    /** 开间序号坐标里的房间轮廓（4 个点）。 */
    val polygon: List<Offset>,
    val doorPoint: Offset,
    val labelPoint: Offset,
    /** 对应「教室分布图」里的房间 id；缺失时说明牌面有、Room 库没有。 */
    val linkedBuiltinRoomId: String?,
    val provisional: Boolean = false,
    val note: String? = null,
)

@Immutable
data class IndoorCorridor(
    val id: String,
    val label: String,
    val points: List<Offset>,
    /** 走廊带的宽度（与 y 轴同单位）。 */
    val widthUnits: Float = 0.60f,
)

@Immutable
data class IndoorFacility(
    val id: String,
    val nodeId: String,
    val name: String,
    val shortLabel: String,
    val kind: IndoorFacilityKind,
    val point: Offset,
    val linkedBuiltinRoomId: String? = null,
)

@Immutable
data class IndoorNode(
    val id: String,
    val point: Offset,
    /** corridor / room / stairs —— 与 CampusNode.kind 保持同一套词。 */
    val kind: String,
    val name: String,
)

@Immutable
data class IndoorEdge(
    val from: String,
    val to: String,
    val kind: String = "corridor",
    /** spine / door / stair-entry；只用于校验收据，不参与寻路。 */
    val role: String = "corridor",
)

/**
 * 开间序号 → 画布像素。
 *
 * x / y 分别缩放是**刻意**的：一层楼横向 13 个开间、纵向只有「房间-楼梯-走廊」三行，
 * 等比例缩放会把房间压成一条线、文字完全读不出来。这幅图只表达连接关系，
 * 所以允许纵向拉伸；真正接入米制底图后，可以直接把 [minX]..[maxY] 换成米。
 */
@Immutable
data class IndoorCoordinateMapper(
    val minX: Float = -1f,
    val maxX: Float = 13f,
    val minY: Float = -1.6f,
    val maxY: Float = 1.6f,
    val horizontalInset: Float = 0.02f,
    val verticalInset: Float = 0.08f,
) {
    fun toScreen(point: Offset, width: Float, height: Float): Offset {
        if (width <= 0f || height <= 0f) return Offset.Zero
        val left = width * horizontalInset
        val top = height * verticalInset
        return Offset(
            x = left + (point.x - minX) * unitX(width),
            y = top + (point.y - minY) * unitY(height),
        )
    }

    fun toPlan(point: Offset, width: Float, height: Float): Offset {
        if (width <= 0f || height <= 0f) return Offset.Zero
        val left = width * horizontalInset
        val top = height * verticalInset
        return Offset(
            x = minX + (point.x - left) / unitX(width),
            y = minY + (point.y - top) / unitY(height),
        )
    }

    fun unitX(width: Float): Float =
        ((width - width * horizontalInset * 2f).coerceAtLeast(1f)) / (maxX - minX)

    fun unitY(height: Float): Float =
        ((height - height * verticalInset * 2f).coerceAtLeast(1f)) / (maxY - minY)
}

/** 射线法；轮廓以后换成不规则多边形也不用改命中逻辑。 */
internal fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        val crosses = (current.y > point.y) != (previous.y > point.y)
        if (crosses) {
            val xAtY = (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
            if (point.x < xAtY) inside = !inside
        }
        previous = current
    }
    return inside
}
