package com.pocket.xiaoli.data.model

/**
 * 宿舍楼 -> 服务校区映射。
 *
 * 烟台理工学院部分学生会住在烟大南校区的宿舍楼，因此宿舍绑定时不能只存
 * 「楼栋 + 房间号」，还要能判断这栋楼的生活服务走哪个校区：
 *  - 1-6、13 号楼：烟理本校区服务（易校园洗澡 / 浣熊先生洗衣等）
 *  - 7-12 号楼：烟大南校区服务（趣智校园洗澡 / U净洗衣 / 烟大后勤电费等）
 *
 * 楼号列表后续如确认有变化，只需要改 [buildings]。
 */
data class DormBuilding(
    val id: String,
    val name: String,
    val campusId: String,
    val campusLabel: String,
    val aliases: List<String> = emptyList(),
) {
    /** 转轮里显示的文案，例如「7号宿舍楼 · 南校」 */
    val displayName: String
        get() = if (campusLabel.isBlank()) name else "$name · $campusLabel"
}

object DormBuildingCatalog {

    const val CAMPUS_MAIN = "ytlg_main"
    const val CAMPUS_SOUTH = "ytu_south"

    val buildings: List<DormBuilding> = buildList {
        for (number in 1..6) {
            add(
                DormBuilding(
                    id = "ytlg_$number",
                    name = "${number}号宿舍楼",
                    campusId = CAMPUS_MAIN,
                    campusLabel = "本校区",
                    aliases = listOf("${number}号楼", "${number}号公寓", "本校区${number}号"),
                )
            )
        }
        add(
            DormBuilding(
                id = "ytlg_13",
                name = "13号楼",
                campusId = CAMPUS_MAIN,
                campusLabel = "本校区",
                aliases = listOf("13号宿舍楼", "十三号楼", "13号公寓"),
            )
        )
        for (number in 7..12) {
            add(
                DormBuilding(
                    id = "ytu_$number",
                    name = "${number}号宿舍楼",
                    campusId = CAMPUS_SOUTH,
                    campusLabel = "南校",
                    aliases = listOf("${number}号楼", "${number}号公寓", "南校${number}号"),
                )
            )
        }
    }

    private val numberRegex = Regex("""(\d{1,2})""")

    fun find(building: String): DormBuilding? {
        val query = building.trim()
        if (query.isBlank()) return null
        buildings.firstOrNull { it.name == query }?.let { return it }
        buildings.firstOrNull { query in it.aliases }?.let { return it }
        val number = numberRegex.find(query)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return buildings.firstOrNull { candidate ->
            numberRegex.find(candidate.name)?.groupValues?.getOrNull(1)?.toIntOrNull() == number
        }
    }

    fun serviceCampusId(building: String): String? =
        find(building)?.campusId

    fun displayNames(): List<String> = buildings.map { it.displayName }
}