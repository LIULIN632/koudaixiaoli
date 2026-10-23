package com.pocket.xiaoli.data.map

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教室分布图数据模型测试。
 *
 * 重点覆盖 **JSON 向后兼容**：RoomCell 存在 SharedPreferences 里、没有 Room 迁移，
 * 旧数据缺少新增字段时必须能安全读出。
 */
class RoomDirectoryTest {

    private val gson = Gson()

    private fun parse(json: String): List<RoomCell> =
        gson.fromJson(json, object : TypeToken<List<RoomCell>>() {}.type)

    /** v1 时代写下的 JSON：没有 rowSpan / colSpan / type / capacity */
    private val legacyJson = """
        [{"id":"c1","campusId":"ytlg_main","building":"三教","floor":"5","room":"S552",
          "name":"","row":2,"col":3,"note":"","createdAt":123}]
    """.trimIndent()

    @Test
    fun legacyJsonStillReads() {
        val cells = parse(legacyJson)
        assertEquals(1, cells.size)
        assertEquals("S552", cells[0].room)
        assertEquals(2, cells[0].row)
    }

    /**
     * 记录 Gson 的真实行为（这是个坑，不是期望行为）：
     * RoomCell 有必填字段 → Kotlin 不生成无参构造 → Gson 用 Unsafe 分配，
     * **字段初始化器不执行**，旧 JSON 缺失的新增字段是 JVM 默认值而非 Kotlin 默认值。
     * 所以读出来必须过 [RoomCell.normalized]。
     */
    @Test
    fun rawGsonLeavesNewFieldsAtJvmDefaults() {
        val cell = parse(legacyJson)[0]
        assertEquals("rowSpan 被 Gson 留成 0", 0, cell.rowSpan)
        assertEquals("colSpan 被 Gson 留成 0", 0, cell.colSpan)
        assertNull("type 被 Gson 留成 null（虽然声明为非空 String）", cell.type)
        // 但访问器必须能扛住 null，不能崩
        assertEquals(RoomCellType.CLASSROOM, cell.roomType)
    }

    @Test
    fun normalizedRepairsLegacyJson() {
        val cell = parse(legacyJson)[0].normalized()
        assertEquals(1, cell.rowSpan)
        assertEquals(1, cell.colSpan)
        assertEquals(RoomCellType.CLASSROOM.key, cell.type)
        assertEquals(0, cell.capacity)
        assertEquals(RoomCellType.CLASSROOM, cell.roomType)
        // 原有字段不能被破坏
        assertEquals("S552", cell.room)
        assertEquals(2, cell.row)
        assertEquals(3, cell.col)
    }

    @Test
    fun normalizedClampsOutOfRangeValues() {
        val cell = RoomCell(
            id = "x", campusId = "c", building = "b", floor = "1", room = "R",
            rowSpan = 99, colSpan = -3, type = "teleporter", capacity = -5
        ).normalized()
        assertEquals(1, cell.rowSpan)
        assertEquals(1, cell.colSpan)
        assertEquals(RoomCellType.CLASSROOM.key, cell.type)
        assertEquals(0, cell.capacity)
    }

    @Test
    fun unknownTypeKeyFallsBackInsteadOfCrashing() {
        val json = legacyJson.replace("\"note\"", "\"type\":\"teleporter\",\"note\"")
        val cell = parse(json)[0]
        assertEquals("未知 type key 应兜底为教室", RoomCellType.CLASSROOM, cell.roomType)
    }

    @Test
    fun roundTripKeepsNewFields() {
        val original = RoomCell(
            id = "c9", campusId = "ytlg_main", building = "实验楼", floor = "2", room = "B201",
            rowSpan = 2, colSpan = 3, type = RoomCellType.LAB.key, capacity = 60
        )
        val back = parse(gson.toJson(listOf(original)))[0]
        assertEquals(original, back)
        assertEquals(RoomCellType.LAB, back.roomType)
    }

    @Test
    fun typeLookupIsCaseInsensitiveAndTrimmed() {
        assertEquals(RoomCellType.RESTROOM, RoomCellType.fromKey("  RESTROOM "))
        assertEquals(RoomCellType.CLASSROOM, RoomCellType.fromKey(null))
        assertEquals(RoomCellType.CLASSROOM, RoomCellType.fromKey(""))
    }

    // ── 布局校验 ──

    private fun cell(
        id: String,
        room: String,
        row: Int = -1,
        col: Int = -1,
        rowSpan: Int = 1,
        colSpan: Int = 1
    ) = RoomCell(
        id = id, campusId = "c", building = "b", floor = "1", room = room,
        row = row, col = col, rowSpan = rowSpan, colSpan = colSpan
    )

    @Test
    fun validLayoutHasNoIssues() {
        val cells = listOf(cell("1", "101", 0, 0), cell("2", "102", 0, 1), cell("3", "103", 1, 0))
        assertTrue(RoomDirectorySearch.validateLayout(cells, 4).isEmpty())
    }

    @Test
    fun autoPlacedCellsNeedNoCoordinates() {
        val cells = listOf(cell("1", "101"), cell("2", "102"))
        assertTrue(RoomDirectorySearch.validateLayout(cells, 4).isEmpty())
    }

    @Test
    fun detectsIllegalSpan() {
        val issues = RoomDirectorySearch.validateLayout(listOf(cell("1", "101", 0, 0, rowSpan = 0)), 4)
        assertTrue(issues.any { it.message.contains("行跨度") })
    }

    @Test
    fun detectsHalfCoordinate() {
        val issues = RoomDirectorySearch.validateLayout(listOf(cell("1", "101", row = 1, col = -1)), 4)
        assertTrue(issues.any { it.message.contains("坐标不完整") })
    }

    @Test
    fun detectsColumnOutOfRange() {
        val issues = RoomDirectorySearch.validateLayout(listOf(cell("1", "101", 0, 9)), 4)
        assertTrue(issues.any { it.message.contains("超出网格宽度") })
    }

    @Test
    fun detectsSpanOverflow() {
        val issues = RoomDirectorySearch.validateLayout(listOf(cell("1", "101", 0, 3, colSpan = 3)), 4)
        assertTrue(issues.any { it.message.contains("列跨度越界") })
    }

    @Test
    fun detectsOverlapIncludingSpans() {
        // A 占 (0,0)-(0,1)，B 落在 (0,1) 上 → 重叠
        val cells = listOf(
            cell("a", "大教室", 0, 0, colSpan = 2),
            cell("b", "小教室", 0, 1)
        )
        val issues = RoomDirectorySearch.validateLayout(cells, 4)
        assertTrue("应检测到跨格重叠", issues.any { it.message.contains("重叠") })
    }

    @Test
    fun overlapReportedOncePerPair() {
        val cells = listOf(cell("a", "A", 0, 0, rowSpan = 2, colSpan = 2), cell("b", "B", 0, 0))
        val overlapCount = RoomDirectorySearch.validateLayout(cells, 4).count { it.message.contains("重叠") }
        assertEquals("同一对房间只报一次", 1, overlapCount)
    }

    @Test
    fun detectsDuplicateRoomNumber() {
        val cells = listOf(cell("1", "101", 0, 0), cell("2", "101", 0, 1))
        val issues = RoomDirectorySearch.validateLayout(cells, 4)
        assertTrue(issues.any { it.message.contains("重复") })
    }

    // ── 原有排布行为回归 ──

    @Test
    fun layoutPlacesExplicitThenAuto() {
        val cells = listOf(cell("1", "101", 0, 2), cell("2", "102"), cell("3", "103"))
        val slots = RoomDirectorySearch.layout(cells, 4)
        assertEquals("101", slots[2]?.room)
        assertNotNull(slots[0])
    }

    @Test
    fun resolveMatchesByRoomNumberInsideText() {
        val cells = listOf(cell("1", "S552", 0, 0))
        assertEquals("S552", RoomDirectorySearch.resolve(cells, "c", "三教S552")?.room)
    }

    // ── 跨格布局 layoutGrid ──

    @Test
    fun explicitSpanCoversMultipleCells() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "阶梯教室", 0, 0, rowSpan = 2, colSpan = 2)), 4)
        assertEquals(2, g.rows)
        assertEquals(4, g.columns)
        for (r in 0..1) {
            for (c in 0..1) {
                assertEquals("($r,$c) 应归属跨格房间", "阶梯教室", g.cellAt(r, c)?.room)
            }
        }
        assertTrue("左上角是锚点", g.isAnchor(0, 0))
        assertFalse("被覆盖的格子不是锚点", g.isAnchor(1, 1))
        assertTrue("跨格右侧仍为空", g.isEmpty(0, 2))
        assertTrue("跨格下方仍为空", g.isEmpty(1, 3))
    }

    @Test
    fun autoPlacementSkipsOccupiedCells() {
        val g = RoomDirectorySearch.layoutGrid(
            listOf(cell("f", "固定", 0, 1), cell("a", "自动1"), cell("b", "自动2")), 3
        )
        assertEquals("自动1", g.cellAt(0, 0)?.room)
        assertEquals("固定", g.cellAt(0, 1)?.room)
        assertEquals("自动2", g.cellAt(0, 2)?.room)
        assertEquals(1, g.rows)
    }

    @Test
    fun autoPlacementWrapsWhenSpanDoesNotFit() {
        val g = RoomDirectorySearch.layoutGrid(
            listOf(cell("f", "整排", 0, 0, colSpan = 3), cell("a", "下一排", colSpan = 3)), 3
        )
        assertEquals("整排", g.cellAt(0, 0)?.room)
        assertEquals("下一排只能落到第二行", "下一排", g.cellAt(1, 0)?.room)
        assertEquals(2, g.rows)
    }

    @Test
    fun explicitOverlapFallsBackToAutoWithoutLosingRoom() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "A", 0, 0), cell("b", "B", 0, 0)), 4)
        assertEquals("两个房间都要出现在布局里，不能丢", 2, g.placements.size)
        assertEquals("id 排序在前的先占位", "A", g.cellAt(0, 0)?.room)
        assertEquals("被挤掉的降级到自动位置", "B", g.cellAt(0, 1)?.room)
    }

    @Test
    fun explicitOutOfRangeFallsBackToAuto() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "越界", 0, 9)), 4)
        assertEquals(1, g.placements.size)
        assertEquals("越界坐标应降级为自动排布", "越界", g.cellAt(0, 0)?.room)
    }

    @Test
    fun rowsComputedFromSpans() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "A", 2, 0, rowSpan = 3)), 4)
        assertEquals("2 + 3 = 5 行", 5, g.rows)
    }

    @Test
    fun minRowsPadsGridForQuickSizeSetting() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "A")), 4, minRows = 6)
        assertEquals("用户设了 6 行就至少显示 6 行", 6, g.rows)
        assertEquals("房间仍从左上角开始", "A", g.cellAt(0, 0)?.room)
        assertTrue("多出来的行是空格", g.isEmpty(5, 3))
    }

    @Test
    fun emptyGridHonorsMinRows() {
        val g = RoomDirectorySearch.layoutGrid(emptyList(), 5, minRows = 4)
        assertEquals(4, g.rows)
        assertEquals(5, g.columns)
        assertTrue(g.placements.isEmpty())
    }

    @Test
    fun colSpanClampedToColumnCount() {
        val g = RoomDirectorySearch.layoutGrid(listOf(cell("a", "A", 0, 0, colSpan = 99)), 3)
        assertEquals("跨度应被夹到列数", 3, g.placements[0].colSpan)
        assertEquals("A", g.cellAt(0, 2)?.room)
    }

    @Test
    fun emptyInputYieldsEmptyLayout() {
        val g = RoomDirectorySearch.layoutGrid(emptyList(), 5)
        assertEquals(0, g.rows)
        assertEquals(5, g.columns)
        assertTrue(g.placements.isEmpty())
        assertTrue(g.isEmpty(0, 0))
    }
}