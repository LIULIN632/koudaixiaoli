package com.pocket.xiaoli.data.map

import androidx.compose.runtime.Immutable

/**
 * 房间类型。
 *
 * 持久化时**存 [key] 字符串而不是 enum 名字**（见 [RoomCell.type]）：
 * Gson 反序列化遇到未知 enum 值会写入 null，而字段是非空类型 → 后续 NPE。
 * 存字符串则未知值只会退化成 [fromKey] 的兜底值，不会崩。
 * 这与 `CourseEntity.weekType`、`CampusInfo.jwType` 的做法一致。
 */
enum class RoomCellType(val key: String, val label: String) {
    CLASSROOM("classroom", "教室"),
    OFFICE("office", "办公室"),
    LAB("lab", "实验室"),
    COMPUTER("computer", "微机室"),
    RESTROOM("restroom", "卫生间"),
    STAIR("stair", "楼梯"),
    ELEVATOR("elevator", "电梯"),
    ENTRANCE("entrance", "入口"),
    CORRIDOR("corridor", "走廊");

    companion object {
        /** 未知 / 缺失时兜底为教室，不抛异常 */
        fun fromKey(key: String?): RoomCellType =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: CLASSROOM
    }
}

/**
 * 楼层分布图上的一个格子：一间教室 / 办公室。
 *
 * 说明：只做「看分布」，不做路径规划，所以不需要走廊连通关系。
 * [row] / [col] 是可选的自定义坐标，用于还原真实方位；
 * 留空（-1）时按录入顺序自动排布。
 *
 * ## 字段默认值必须保持「老数据可读」
 * 本类通过 `SharedPreferences("room_cells")` + Gson 持久化，**没有 Room 迁移可用**。
 * 因此新增字段一律要给默认值，Gson 才能把旧 JSON 正常读出来。
 * 目前 schema 版本见 `AppContainer.ROOM_CELLS_SCHEMA`。
 */
@Immutable
data class RoomCell(
    val id: String,
    val campusId: String,
    val building: String,
    val floor: String,
    val room: String,
    val name: String = "",
    val row: Int = -1,
    val col: Int = -1,
    /** 纵向占用格数，>=1。默认 1 = 不跨行 */
    val rowSpan: Int = 1,
    /** 横向占用格数，>=1。默认 1 = 不跨列 */
    val colSpan: Int = 1,
    /** 房间类型，取值见 [RoomCellType]。存 key 字符串以保证 JSON 耐改 */
    val type: String = RoomCellType.CLASSROOM.key,
    /** 容量（人）。0 = 未设置 */
    val capacity: Int = 0,
    val note: String = "",
    val createdAt: Long = 0L
) {
    /** 类型枚举视图（未知 key 兜底为教室） */
    val roomType: RoomCellType get() = RoomCellType.fromKey(type)

    /**
     * 修复反序列化可能留下的非法值。
     *
     * 为什么必须有：本类有必填字段（id / campusId / building / floor / room），
     * Kotlin 因此**不会**生成无参构造，Gson 只能用 Unsafe 分配实例 ——
     * **字段初始化器不会执行**，JSON 里缺失的字段会保持 JVM 默认值（`0` / `null`），
     * 而不是这里声明的 Kotlin 默认值。
     *
     * 所以凡是「旧版本 JSON 中不存在」的新增字段，读出来后都必须过一遍这里。
     * 有单元测试守着（RoomDirectoryTest.legacyJsonGetsSaneDefaults）。
     */
    fun normalized(): RoomCell = copy(
        name = name.orEmpty(),
        rowSpan = if (rowSpan in 1..RoomDirectorySearch.MAX_SPAN) rowSpan else 1,
        colSpan = if (colSpan in 1..RoomDirectorySearch.MAX_SPAN) colSpan else 1,
        type = RoomCellType.fromKey(type).key,
        capacity = capacity.coerceAtLeast(0),
        note = note.orEmpty(),
    )
}

/** 一条布局校验问题 */
data class RoomLayoutIssue(
    val cellId: String,
    val room: String,
    val message: String,
)

/** 网格中的一次落位（锚点）：[row] / [col] 是左上角，[rowSpan] / [colSpan] 是占用格数 */
data class RoomPlacement(
    val cell: RoomCell,
    val row: Int,
    val col: Int,
    val rowSpan: Int,
    val colSpan: Int,
)

/**
 * 一层楼的网格布局结果（支持跨格）。
 *
 * 只描述「锚点 + 占位」，不负责渲染：UI 按 [placements] 摆放，
 * 需要判断某格归属哪个房间时用 [placementAt]（O(1)，已建索引）。
 */
data class RoomGridLayout(
    val rows: Int,
    val columns: Int,
    val placements: List<RoomPlacement>,
) {
    private val index: Map<Pair<Int, Int>, RoomPlacement> = buildMap {
        placements.forEach { p ->
            for (r in p.row until p.row + p.rowSpan) {
                for (c in p.col until p.col + p.colSpan) put(r to c, p)
            }
        }
    }

    /** 该坐标所在的落位（含被跨格覆盖的格子） */
    fun placementAt(row: Int, col: Int): RoomPlacement? = index[row to col]

    /** 该坐标所在的房间 */
    fun cellAt(row: Int, col: Int): RoomCell? = placementAt(row, col)?.cell

    /** 该坐标是否为某房间的锚点（左上角） */
    fun isAnchor(row: Int, col: Int): Boolean = placements.any { it.row == row && it.col == col }

    /** 该坐标是否为空（无任何房间占用） */
    fun isEmpty(row: Int, col: Int): Boolean = placementAt(row, col) == null
}

/** 楼 -> 层 -> 房间 的检索与网格排布，逻辑独立以便单元测试 */
object RoomDirectorySearch {

    /** 网格最大列数 */
    const val MAX_COLUMNS = 12

    /** 单元格最大跨度（行 / 列各自） */
    const val MAX_SPAN = 6

    fun buildings(cells: List<RoomCell>, campusId: String): List<String> =
        cells.filter { it.campusId == campusId }
            .map { it.building }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun floors(cells: List<RoomCell>, campusId: String, building: String): List<String> =
        cells.filter { it.campusId == campusId && it.building == building }
            .map { it.floor }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))

    fun cells(
        cells: List<RoomCell>,
        campusId: String,
        building: String,
        floor: String
    ): List<RoomCell> =
        cells.filter {
            it.campusId == campusId && it.building == building && it.floor == floor
        }.sortedWith(compareBy({ it.row.coerceAtLeast(0) }, { it.col.coerceAtLeast(0) }, { it.room }))

    /**
     * 排成网格。带 row/col 的按坐标落位，其余按顺序填进空位。
     * 返回的扁平列表里 null 表示空格子。
     *
     * ⚠️ 当前**未处理 [RoomCell.rowSpan] / [RoomCell.colSpan]**（跨格渲染属下一步）。
     * 在接入跨格之前，请用 [validateLayout] 检查数据，避免出现重叠。
     */
    fun layout(cells: List<RoomCell>, columns: Int): List<RoomCell?> {
        if (cells.isEmpty()) return emptyList()
        val cols = columns.coerceIn(1, MAX_COLUMNS)
        val explicit = cells.filter { it.row >= 0 && it.col in 0 until cols }
        val auto = cells.filterNot { it.row >= 0 && it.col in 0 until cols }
        val explicitRows = explicit.maxOfOrNull { it.row + 1 } ?: 0
        val autoRows = (auto.size + cols - 1) / cols
        val rows = maxOf(explicitRows, autoRows, 1)
        val slots = MutableList<RoomCell?>(rows * cols) { null }
        explicit.forEach { cell ->
            val index = cell.row * cols + cell.col
            if (index in slots.indices && slots[index] == null) slots[index] = cell
        }
        var cursor = 0
        auto.forEach { cell ->
            while (cursor < slots.size && slots[cursor] != null) cursor++
            if (cursor < slots.size) {
                slots[cursor] = cell
                cursor++
            }
        }
        return slots
    }

    /**
     * 支持跨格的网格布局。
     *
     * 与 [layout] 的区别：后者返回扁平 `List<RoomCell?>`，**无法表达「这一格是某个房间的延续」**；
     * 本方法返回 [RoomGridLayout]，只给出锚点，被覆盖的位置用 [RoomGridLayout.placementAt] 查询。
     *
     * 放置顺序：
     *  1. **显式坐标**的房间（row/col 均 >= 0）按坐标落位；坐标越界或与已放置重叠时
     *     **降级为自动排布** —— 不丢房间，只是位置不是用户指定的
     *  2. **自动排布**的房间按行优先扫描，找第一块能容下自身跨度的空位
     *
     * `rowSpan` / `colSpan` 会先夹到合法范围（1..[MAX_SPAN]，且不超过列数）。
     *
     * @param minRows 最少行数。UI 快捷设置网格大小时传入；房间实际占用超过时自动扩展。
     */
    fun layoutGrid(
        cells: List<RoomCell>,
        columns: Int = MAX_COLUMNS,
        minRows: Int = 0,
    ): RoomGridLayout {
        val cols = columns.coerceIn(1, MAX_COLUMNS)
        val rowsFloor = minRows.coerceAtLeast(0)
        if (cells.isEmpty()) return RoomGridLayout(rowsFloor, cols, emptyList())

        val occupied = mutableMapOf<Pair<Int, Int>, String>()
        val placements = mutableListOf<RoomPlacement>()

        fun spansOf(cell: RoomCell): Pair<Int, Int> =
            cell.rowSpan.coerceIn(1, MAX_SPAN) to
                cell.colSpan.coerceIn(1, minOf(MAX_SPAN, cols))

        fun fits(row: Int, col: Int, rs: Int, cs: Int): Boolean {
            if (row < 0 || col < 0 || col + cs > cols) return false
            for (r in row until row + rs) {
                for (c in col until col + cs) if (occupied.containsKey(r to c)) return false
            }
            return true
        }

        fun occupy(cell: RoomCell, row: Int, col: Int, rs: Int, cs: Int) {
            for (r in row until row + rs) {
                for (c in col until col + cs) occupied[r to c] = cell.id
            }
            placements += RoomPlacement(cell, row, col, rs, cs)
        }

        val pending = mutableListOf<RoomCell>()

        // ① 显式坐标：按行、列稳定排序，保证结果可预测
        cells.filter { it.row >= 0 && it.col >= 0 }
            .sortedWith(compareBy({ it.row }, { it.col }, { it.id }))
            .forEach { cell ->
                val (rs, cs) = spansOf(cell)
                if (fits(cell.row, cell.col, rs, cs)) {
                    occupy(cell, cell.row, cell.col, rs, cs)
                } else {
                    pending += cell
                }
            }

        // ② 自动排布（含 ① 降级下来的）：行优先找第一块能容下自身跨度的空位。
        // 固定锚点房间（入口 / 楼梯 / 卫生间 / 走廊）先按语义列优先落位，再排普通房间。
        pending += cells.filterNot { it.row >= 0 && it.col >= 0 }

        fun placeCell(cell: RoomCell, preferredColumns: List<Int>) {
            val (rs, cs) = spansOf(cell)
            var row = 0
            while (true) {
                val maxCol = cols - cs
                val ordered = buildList {
                    preferredColumns.forEach { if (it in 0..maxCol) add(it) }
                    for (c in 0..maxCol) if (c !in preferredColumns) add(c)
                }
                for (col in ordered) {
                    if (fits(row, col, rs, cs)) {
                        occupy(cell, row, col, rs, cs)
                        return
                    }
                }
                row++
            }
        }

        val anchors = pending
            .filter { RoomAnchorLayout.isAnchor(RoomCellType.fromKey(it.type)) }
            .sortedBy { RoomAnchorLayout.rank(RoomCellType.fromKey(it.type)) }
        anchors.forEach { cell ->
            val type = RoomCellType.fromKey(cell.type)
            placeCell(cell, RoomAnchorLayout.preferredColumns(type, cols))
        }

        pending
            .filterNot { RoomAnchorLayout.isAnchor(RoomCellType.fromKey(it.type)) }
            .forEach { cell -> placeCell(cell, emptyList()) }

        val rows = maxOf(
            rowsFloor,
            placements.maxOfOrNull { it.row + it.rowSpan } ?: 0,
        )
        return RoomGridLayout(rows = rows, columns = cols, placements = placements)
    }


    /** 合并方向：向右 / 向下。 */
    enum class RoomMergeDirection { RIGHT, DOWN }

    /** 把房间移动到指定坐标；越界或与同层其他房间重叠时返回原列表。 */
    fun moveCell(
        cells: List<RoomCell>,
        id: String,
        targetRow: Int,
        targetCol: Int,
        columns: Int = MAX_COLUMNS,
    ): List<RoomCell> {
        val cell = cells.firstOrNull { it.id == id } ?: return cells
        if (targetRow < 0 || targetCol < 0) return cells
        val moved = cell.copy(row = targetRow, col = targetCol)
        if (targetCol + moved.colSpan > columns.coerceIn(1, MAX_COLUMNS)) return cells
        val blocked = cells.any { other ->
            other.id != cell.id && sameFloor(other, cell) && overlaps(moved, other)
        }
        if (blocked) return cells
        return cells.map { if (it.id == id) moved else it }
    }

    /** 把跨格房间拆成单格房间。 */
    fun splitCell(cells: List<RoomCell>, id: String): List<RoomCell> {
        val cell = cells.firstOrNull { it.id == id } ?: return cells
        if (cell.rowSpan <= 1 && cell.colSpan <= 1) return cells
        val baseRow = cell.row.coerceAtLeast(0)
        val baseCol = cell.col.coerceAtLeast(0)
        val result = mutableListOf<RoomCell>()
        var index = 1
        for (r in 0 until cell.rowSpan) {
            for (c in 0 until cell.colSpan) {
                result += cell.copy(
                    id = cell.id + "-s" + (index++),
                    room = if (cell.room.isBlank()) cell.room else cell.room + "-" + index,
                    row = baseRow + r,
                    col = baseCol + c,
                    rowSpan = 1,
                    colSpan = 1,
                    capacity = 0,
                )
            }
        }
        return cells.filterNot { it.id == cell.id } + result
    }

    /** 返回指定方向上可合并的相邻房间；没有时返回 null。 */
    fun mergeNeighbor(
        cells: List<RoomCell>,
        id: String,
        direction: RoomMergeDirection,
    ): RoomCell? {
        val cell = cells.firstOrNull { it.id == id } ?: return null
        return cells.firstOrNull { other ->
            other.id != cell.id && sameFloor(other, cell) && when (direction) {
                RoomMergeDirection.RIGHT ->
                    other.row == cell.row &&
                        other.rowSpan == cell.rowSpan &&
                        other.col == cell.col + cell.colSpan
                RoomMergeDirection.DOWN ->
                    other.col == cell.col &&
                        other.colSpan == cell.colSpan &&
                        other.row == cell.row + cell.rowSpan
            }
        }
    }

    /** 与相邻房间合并；不合法时返回原列表。 */
    fun mergeAdjacent(
        cells: List<RoomCell>,
        id: String,
        direction: RoomMergeDirection,
    ): List<RoomCell> {
        val cell = cells.firstOrNull { it.id == id } ?: return cells
        val neighbor = mergeNeighbor(cells, id, direction) ?: return cells
        val merged = when (direction) {
            RoomMergeDirection.RIGHT -> cell.copy(
                colSpan = cell.colSpan + neighbor.colSpan,
                capacity = (cell.capacity + neighbor.capacity).coerceAtLeast(0),
            )
            RoomMergeDirection.DOWN -> cell.copy(
                rowSpan = cell.rowSpan + neighbor.rowSpan,
                capacity = (cell.capacity + neighbor.capacity).coerceAtLeast(0),
            )
        }
        return cells.filterNot { it.id == neighbor.id }.map { if (it.id == cell.id) merged else it }
    }

    private fun sameFloor(a: RoomCell, b: RoomCell): Boolean =
        a.campusId == b.campusId && a.building == b.building && a.floor == b.floor

    private fun overlaps(a: RoomCell, b: RoomCell): Boolean {
        if (a.row < 0 || a.col < 0 || b.row < 0 || b.col < 0) return false
        val aRight = a.col + a.colSpan
        val bRight = b.col + b.colSpan
        val aBottom = a.row + a.rowSpan
        val bBottom = b.row + b.rowSpan
        return a.col < bRight && b.col < aRight && a.row < bBottom && b.row < aBottom
    }
    /**
     * 校验一层的布局数据，返回所有问题（空列表 = 通过）。
     *
     * 检查项：
     * - `rowSpan` / `colSpan` 取值合法（1..[MAX_SPAN]）
     * - 坐标成对出现（不允许只有行没有列）
     * - 列坐标与列跨度不越界
     * - 显式坐标的房间**矩形区域不重叠**（按 span 展开后判断）
     * - 同一层房间号不重复
     *
     * 纯函数，供 UI 在保存前提示，也可单测。
     */
    fun validateLayout(cells: List<RoomCell>, columns: Int = MAX_COLUMNS): List<RoomLayoutIssue> {
        if (cells.isEmpty()) return emptyList()
        val cols = columns.coerceIn(1, MAX_COLUMNS)
        val issues = mutableListOf<RoomLayoutIssue>()
        val occupied = mutableMapOf<Pair<Int, Int>, RoomCell>()
        val overlappedPairs = mutableSetOf<Pair<String, String>>()

        fun issue(cell: RoomCell, message: String) {
            issues += RoomLayoutIssue(cellId = cell.id, room = cell.room, message = message)
        }

        // 房间号重复
        cells.filter { it.room.isNotBlank() }
            .groupBy { it.room.trim().lowercase() }
            .filterValues { it.size > 1 }
            .forEach { (_, dup) -> dup.drop(1).forEach { issue(it, "房间号「${it.room}」在同一层重复") } }

        cells.forEach { cell ->
            if (cell.rowSpan !in 1..MAX_SPAN) {
                issue(cell, "行跨度 ${cell.rowSpan} 非法（应为 1..$MAX_SPAN）")
            }
            if (cell.colSpan !in 1..MAX_SPAN) {
                issue(cell, "列跨度 ${cell.colSpan} 非法（应为 1..$MAX_SPAN）")
            }

            val hasRow = cell.row >= 0
            val hasCol = cell.col >= 0
            if (!hasRow && !hasCol) return@forEach   // 自动排布，无需坐标校验
            if (hasRow != hasCol) {
                issue(cell, "坐标不完整：行=${cell.row}、列=${cell.col}，需成对设置")
                return@forEach
            }
            if (cell.col >= cols) {
                issue(cell, "列坐标 ${cell.col} 超出网格宽度 $cols")
                return@forEach
            }
            if (cell.col + cell.colSpan > cols) {
                issue(cell, "列跨度越界：起始列 ${cell.col} + 跨度 ${cell.colSpan} 超出宽度 $cols")
            }

            for (r in cell.row until cell.row + cell.rowSpan.coerceAtLeast(1)) {
                for (c in cell.col until cell.col + cell.colSpan.coerceAtLeast(1)) {
                    val prev = occupied.putIfAbsent(r to c, cell)
                    if (prev != null && prev.id != cell.id) {
                        val key = if (prev.id <= cell.id) prev.id to cell.id else cell.id to prev.id
                        if (overlappedPairs.add(key)) {
                            issue(cell, "与「${prev.room}」位置重叠（行 $r、列 $c）")
                        }
                    }
                }
            }
        }
        return issues
    }

    /** 按房间号 / 用途 / 楼名模糊查找 */
    fun find(cells: List<RoomCell>, campusId: String, query: String): List<RoomCell> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return cells.filter {
            it.campusId == campusId &&
                (it.room.lowercase().contains(q) ||
                    it.name.lowercase().contains(q) ||
                    it.building.lowercase().contains(q) ||
                    it.note.lowercase().contains(q))
        }
    }

    /**
     * 把课表里的教室文本解析成一条房间记录。
     * 依次尝试：完全一致 -> 文本包含房间号（如「三教S552」含「S552」）-> 房间号包含文本。
     */
    fun resolve(cells: List<RoomCell>, campusId: String, rawRoom: String): RoomCell? {
        val q = rawRoom.trim()
        if (q.isEmpty()) return null
        val scope = cells.filter { it.campusId == campusId && it.room.isNotBlank() }
        scope.firstOrNull { it.room.equals(q, ignoreCase = true) }?.let { return it }
        scope.firstOrNull { q.contains(it.room, ignoreCase = true) }?.let { return it }
        return scope.firstOrNull { it.room.contains(q, ignoreCase = true) }
    }
}
