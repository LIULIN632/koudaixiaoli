package com.pocket.xiaoli.data.map

/**
 * 固定锚点房间的语义化排布策略。
 *
 * 只给出「优先列」和「放置优先级」，具体落位仍由 [RoomDirectorySearch.layoutGrid]
 * 在网格里寻找第一个可行位置，避免覆盖显式坐标房间。
 */
object RoomAnchorLayout {

    private val anchorTypes = setOf(
        RoomCellType.ENTRANCE,
        RoomCellType.STAIR,
        RoomCellType.ELEVATOR,
        RoomCellType.RESTROOM,
        RoomCellType.CORRIDOR,
    )

    fun isAnchor(type: RoomCellType): Boolean = type in anchorTypes

    /** 数值越小越先放置。 */
    fun rank(type: RoomCellType): Int = when (type) {
        RoomCellType.ENTRANCE -> 0
        RoomCellType.STAIR -> 1
        RoomCellType.ELEVATOR -> 2
        RoomCellType.RESTROOM -> 3
        RoomCellType.CORRIDOR -> 4
        else -> 99
    }

    /** 优先尝试的列顺序；入口 / 楼梯靠边，走廊先横铺。 */
    fun preferredColumns(type: RoomCellType, columns: Int): List<Int> {
        if (columns <= 0) return emptyList()
        val last = columns - 1
        return when (type) {
            RoomCellType.ENTRANCE -> listOf(0, last)
            RoomCellType.STAIR -> listOf(0, last)
            RoomCellType.ELEVATOR -> listOf(last, 0)
            RoomCellType.RESTROOM -> listOf(last, 0)
            RoomCellType.CORRIDOR -> (0 until columns).toList()
            else -> emptyList()
        }.distinct().filter { it in 0 until columns }
    }
}

