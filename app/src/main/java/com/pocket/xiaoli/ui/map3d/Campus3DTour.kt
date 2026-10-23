package com.pocket.xiaoli.ui.map3d

/**
 * 一条数据锚定的校园导览路线。
 *
 * 站点只从场景里真实存在的楼栋、水域和球场中挑选，不虚构地点；
 * 缺失的对象直接跳过，少于两站时导览入口不出现。
 */
internal data class Campus3DTourStop(
    val id: String,
    val title: String,
    val subtitle: String,
    val hit: Campus3DHit,
)

internal fun buildCampus3DTour(layout: Campus3DLayout): List<Campus3DTourStop> {
    val stops = buildList {
        fun addBuilding(shape: Campus3DBuildingShape?) {
            if (shape == null) return
            add(
                Campus3DTourStop(
                    id = "tour:building:${shape.id}",
                    title = shape.name.ifBlank { shape.sourceName.ifBlank { "未命名楼栋" } },
                    subtitle = buildingSubtitle(shape),
                    hit = Campus3DHit.Building(shape),
                )
            )
        }

        addBuilding(layout.buildings.named("1号教学楼"))
        addBuilding(layout.buildings.named("3号教学楼"))
        addBuilding(layout.buildings.named("综合楼"))
        addBuilding(layout.buildings.named("商业楼"))
        // 场景里两座餐厅同名，导览取层数更多、体量更大的那座。
        addBuilding(layout.buildings.filter { it.name == "餐厅" }.maxByOrNull { it.levels })

        val lake = layout.water.firstOrNull { it.name == "琴心湖" }
            ?: layout.water.firstOrNull { it.name.isNotBlank() }
        lake?.let { water ->
            add(
                Campus3DTourStop(
                    id = "tour:water:${water.id}",
                    title = water.name,
                    subtitle = "OSM 实测水体轮廓 · 校园景观",
                    hit = Campus3DHit.Water(water),
                )
            )
        }

        fun addPitch(name: String, fallback: (Campus3DPitchShape) -> Boolean) {
            val pitch = layout.pitches.firstOrNull { it.name == name }
                ?: layout.pitches.firstOrNull(fallback)
                ?: return
            val sport = sportLabel(pitch.kind, pitch.sport)
            add(
                Campus3DTourStop(
                    id = "tour:pitch:${pitch.id}",
                    title = pitch.name.ifBlank { sport },
                    subtitle = "$sport · OSM 运动场地",
                    hit = Campus3DHit.Pitch(pitch),
                )
            )
        }
        addPitch("田径场") { it.kind == "track" || it.sport == "running" }
        addPitch("篮球场") { it.sport == "basketball" }
    }
    return if (stops.size >= 2) stops else emptyList()
}

private fun List<Campus3DBuildingShape>.named(name: String): Campus3DBuildingShape? =
    firstOrNull { it.name == name } ?: firstOrNull { it.sourceName == name }

private fun buildingSubtitle(shape: Campus3DBuildingShape): String = buildList {
    add("${shape.levels} 层")
    add(categoryLabel(shape.category))
    if (shape.sourceName.isNotBlank() && shape.sourceName != shape.name) {
        add("又称${shape.sourceName}")
    }
    add(if (shape.note.isBlank()) "OSM 实测轮廓" else "影像描点轮廓")
}.joinToString(" · ")
