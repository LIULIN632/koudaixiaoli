package com.pocket.xiaoli.data.map

/**
 * 内置教学楼 / 楼层 / 教室数据（与 `BuiltinPois` 同一套播种模式）。
 *
 * ## 为什么用「文本 + 解析」而不是直接写 Kotlin 对象
 * 这份数据的日常维护方式是：**使用者按格式整理好文本 → 贴进 [RAW] → 把 [VERSION] 加一**。
 * 用 `楼栋|楼层|房间号|...` 的竖线文本，比手写几十个 `RoomCell(...)` 更不容易出错，
 * 也方便在表格软件里批量编辑后粘贴。
 *
 * 格式说明见 `docs/BuildingDataFormat.md`；解析逻辑有单元测试守着
 * （`BuiltinRoomCellsTest`）。
 *
 * ## 播种规则
 * 与 `BuiltinPois` 一致：**只在首次启动或 [VERSION] 升级时合并一次**。
 * 用户在应用内删掉的房间**不会自动复活** —— 否则用户的整理成果会被每次启动覆盖。
 */
object BuiltinRoomCells {

    /** 内置数据版本；[RAW] 内容有变更时 +1，老用户下次启动即可拿到增量 */
    const val VERSION = 2

    /** 内置数据归属的校区 */
    const val CAMPUS_ID = "ytlg_main"

    /** 内置房间 id 前缀，便于与用户自建房间区分 */
    private const val ID_PREFIX = "builtin:"

    /**
     * 内置教室数据源。
     *
     * 数据来自 2026-09-23 实拍的「1号教学楼」楼层指引牌（1F–5F）。
     * 指引牌只给房间号与北/南分区，没有用途和容量，因此 `name` 只写分区，
     * 不猜课程用途；4F 指引牌未标南区卫生间、6F 未拍到，均不补。
     *
     * 坐标按指引牌排：行 0 在东、向下往西；列 0 北区 / 1 北楼梯 / 2 南楼梯 / 3 南区。
     * 这和 1F 指引牌上的「东大门 / 西大门」方向一致，3D 楼层图里就是左北右南、上东下西。
     *
     * 日常维护：按 `docs/BuildingDataFormat.md` 整理后替换 [RAW]，并把 [VERSION] 加一。
     */
    private val RAW: String = """
        # 数据来源：2026-09-23 实拍「1号教学楼」楼层指引牌（1F–5F）
        # 楼栋|楼层|房间号|用途|类型|容量|行|列|跨行|跨列
        # 行=东→西（0 为东）；列=0 北区 / 1 北楼梯 / 2 南楼梯 / 3 南区
        1号教学楼|1|北区卫生间||restroom|0|0|0|1|1
        1号教学楼|1|116|北区|classroom|0|1|0|1|1
        1号教学楼|1|117|北区|classroom|0|2|0|1|1
        1号教学楼|1|118|北区|classroom|0|3|0|1|1
        1号教学楼|1|119|北区|classroom|0|4|0|1|1
        1号教学楼|1|106|南区|classroom|0|0|3|1|1
        1号教学楼|1|105|南区|classroom|0|1|3|1|1
        1号教学楼|1|104|南区|classroom|0|2|3|1|1
        1号教学楼|1|103|南区|classroom|0|3|3|1|1
        1号教学楼|1|102|南区|classroom|0|4|3|1|1
        1号教学楼|1|101|南区|classroom|0|5|3|1|1
        1号教学楼|1|东大门||entrance|0|0|1|1|2
        1号教学楼|1|东北楼梯||stair|0|1|1|1|1
        1号教学楼|1|东南楼梯||stair|0|1|2|1|1
        1号教学楼|1|西北楼梯||stair|0|6|1|1|1
        1号教学楼|1|西南楼梯||stair|0|6|2|1|1
        1号教学楼|1|南区卫生间||restroom|0|7|2|1|1
        1号教学楼|1|西大门||entrance|0|8|1|1|2
        1号教学楼|2|北区卫生间||restroom|0|0|0|1|1
        1号教学楼|2|208|北区|classroom|0|1|0|1|1
        1号教学楼|2|209|北区|classroom|0|2|0|1|1
        1号教学楼|2|210|北区|classroom|0|3|0|1|1
        1号教学楼|2|211|北区|classroom|0|4|0|1|1
        1号教学楼|2|212|北区|classroom|0|5|0|1|1
        1号教学楼|2|213|北区|classroom|0|6|0|1|1
        1号教学楼|2|214|北区|classroom|0|7|0|1|1
        1号教学楼|2|215|北区|classroom|0|8|0|1|1
        1号教学楼|2|216|北区|classroom|0|9|0|1|1
        1号教学楼|2|217|北区|classroom|0|10|0|1|1
        1号教学楼|2|218|北区|classroom|0|11|0|1|1
        1号教学楼|2|206|南区|classroom|0|0|3|1|1
        1号教学楼|2|205|南区|classroom|0|1|3|1|1
        1号教学楼|2|204|南区|classroom|0|2|3|1|1
        1号教学楼|2|203|南区|classroom|0|3|3|1|1
        1号教学楼|2|202|南区|classroom|0|4|3|1|1
        1号教学楼|2|201|南区|classroom|0|5|3|1|1
        1号教学楼|2|207||classroom|0|0|1|1|1
        1号教学楼|2|东北楼梯||stair|0|2|1|1|1
        1号教学楼|2|东南楼梯||stair|0|2|2|1|1
        1号教学楼|2|西北楼梯||stair|0|9|1|1|1
        1号教学楼|2|西南楼梯||stair|0|9|2|1|1
        1号教学楼|2|南区卫生间||restroom|0|10|2|1|1
        1号教学楼|3|北区卫生间||restroom|0|0|0|1|1
        1号教学楼|3|309|北区|classroom|0|1|0|1|1
        1号教学楼|3|310|北区|classroom|0|2|0|1|1
        1号教学楼|3|311|北区|classroom|0|3|0|1|1
        1号教学楼|3|312|北区|classroom|0|4|0|1|1
        1号教学楼|3|314|北区|classroom|0|5|0|1|1
        1号教学楼|3|315|北区|classroom|0|6|0|1|1
        1号教学楼|3|316|北区|classroom|0|7|0|1|1
        1号教学楼|3|306|南区|classroom|0|0|3|1|1
        1号教学楼|3|305|南区|classroom|0|1|3|1|1
        1号教学楼|3|304|南区|classroom|0|2|3|1|1
        1号教学楼|3|302|南区|classroom|0|3|3|1|1
        1号教学楼|3|301|南区|classroom|0|4|3|1|1
        1号教学楼|3|东北楼梯||stair|0|0|1|1|1
        1号教学楼|3|东南楼梯||stair|0|0|2|1|1
        1号教学楼|3|西北楼梯||stair|0|5|1|1|1
        1号教学楼|3|西南楼梯||stair|0|5|2|1|1
        1号教学楼|3|南区卫生间||restroom|0|6|2|1|1
        1号教学楼|4|北区卫生间||restroom|0|0|0|1|1
        1号教学楼|4|413|北区|classroom|0|1|0|1|1
        1号教学楼|4|414|北区|classroom|0|2|0|1|1
        1号教学楼|4|415|北区|classroom|0|3|0|1|1
        1号教学楼|4|416|北区|classroom|0|4|0|1|1
        1号教学楼|4|417|北区|classroom|0|5|0|1|1
        1号教学楼|4|418|北区|classroom|0|6|0|1|1
        1号教学楼|4|419|北区|classroom|0|7|0|1|1
        1号教学楼|4|420|北区|classroom|0|8|0|1|1
        1号教学楼|4|421|北区|classroom|0|9|0|1|1
        1号教学楼|4|422|北区|classroom|0|10|0|1|1
        1号教学楼|4|423|北区|classroom|0|11|0|1|1
        1号教学楼|4|424|北区|classroom|0|12|0|1|1
        1号教学楼|4|406|南区|classroom|0|0|3|1|1
        1号教学楼|4|405|南区|classroom|0|1|3|1|1
        1号教学楼|4|404|南区|classroom|0|2|3|1|1
        1号教学楼|4|403|南区|classroom|0|3|3|1|1
        1号教学楼|4|402|南区|classroom|0|4|3|1|1
        1号教学楼|4|401|南区|classroom|0|5|3|1|1
        1号教学楼|4|东北楼梯||stair|0|0|1|1|1
        1号教学楼|4|东南楼梯||stair|0|0|2|1|1
        1号教学楼|4|西北楼梯||stair|0|10|1|1|1
        1号教学楼|4|西南楼梯||stair|0|10|2|1|1
        1号教学楼|5|北区卫生间||restroom|0|0|0|1|1
        1号教学楼|5|512|北区|classroom|0|1|0|1|1
        1号教学楼|5|513-1|北区|classroom|0|2|0|1|1
        1号教学楼|5|513-2|北区|classroom|0|3|0|1|1
        1号教学楼|5|514-1|北区|classroom|0|4|0|1|1
        1号教学楼|5|514-2|北区|classroom|0|5|0|1|1
        1号教学楼|5|515-1|北区|classroom|0|6|0|1|1
        1号教学楼|5|515-2|北区|classroom|0|7|0|1|1
        1号教学楼|5|516|北区|classroom|0|8|0|1|1
        1号教学楼|5|517|北区|classroom|0|9|0|1|1
        1号教学楼|5|506|南区|classroom|0|0|3|1|1
        1号教学楼|5|505|南区|classroom|0|1|3|1|1
        1号教学楼|5|504|南区|classroom|0|2|3|1|1
        1号教学楼|5|503|南区|classroom|0|3|3|1|1
        1号教学楼|5|502-2|南区|classroom|0|4|3|1|1
        1号教学楼|5|502-1|南区|classroom|0|5|3|1|1
        1号教学楼|5|501|南区|classroom|0|6|3|1|1
        1号教学楼|5|511-2||classroom|0|0|1|1|1
        1号教学楼|5|511-1||classroom|0|0|2|1|1
        1号教学楼|5|510||classroom|0|1|1|1|1
        1号教学楼|5|509||classroom|0|1|2|1|1
        1号教学楼|5|东北楼梯||stair|0|2|1|1|1
        1号教学楼|5|东南楼梯||stair|0|2|2|1|1
        1号教学楼|5|西北楼梯||stair|0|8|1|1|1
        1号教学楼|5|西南楼梯||stair|0|8|2|1|1
        1号教学楼|5|南区卫生间||restroom|0|9|2|1|1
    """.trimIndent()

    /** 解析后的内置房间（懒加载，不在启动时白白解析空串） */
    val all: List<RoomCell> by lazy { parse(RAW) }

    /** 稳定 id：同一栋楼/层/房间号重复播种时不会产生副本 */
    fun builtinId(building: String, floor: String, room: String): String =
        ID_PREFIX + building.trim() + "/" + floor.trim() + "/" + room.trim()

    /**
     * 解析竖线分隔的文本。
     *
     * 规则：
     * - 空行与 `#` 开头的行为注释，跳过
     * - 至少要 `楼栋|楼层|房间号` 三段；缺任一则整行丢弃（不猜）
     * - 第 4 段起依次为：用途 / 类型 / 容量 / 行 / 列 / 跨行 / 跨列，可省略
     * - 类型用 [RoomCellType] 的 key（如 `classroom`），无法识别时兜底为教室
     * - 坐标：行/列任一缺失或非法都视为「未设置」（-1），交给自动排布
     */
    fun parse(text: String, campusId: String = CAMPUS_ID): List<RoomCell> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line -> parseLine(line, campusId) }
            .distinctBy { it.id }
            .toList()

    private fun parseLine(line: String, campusId: String): RoomCell? {
        val parts = line.split('|').map { it.trim() }
        if (parts.size < 3) return null

        val building = parts[0]
        val floor = parts[1]
        val room = parts[2]
        if (building.isEmpty() || room.isEmpty()) return null

        val row = parts.getOrNull(6)?.toIntOrNull() ?: -1
        val col = parts.getOrNull(7)?.toIntOrNull() ?: -1
        val hasCoord = row >= 0 && col >= 0

        return RoomCell(
            id = builtinId(building, floor, room),
            campusId = campusId,
            building = building,
            floor = floor,
            room = room,
            name = parts.getOrNull(3).orEmpty(),
            type = RoomCellType.fromKey(parts.getOrNull(4)).key,
            capacity = parts.getOrNull(5)?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            row = if (hasCoord) row else -1,
            col = if (hasCoord) col else -1,
            rowSpan = parts.getOrNull(8)?.toIntOrNull()?.coerceIn(1, RoomDirectorySearch.MAX_SPAN) ?: 1,
            colSpan = parts.getOrNull(9)?.toIntOrNull()?.coerceIn(1, RoomDirectorySearch.MAX_SPAN) ?: 1,
            note = "",
            createdAt = 0L,
        )
    }
}