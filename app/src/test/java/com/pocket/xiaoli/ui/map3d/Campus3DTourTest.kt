package com.pocket.xiaoli.ui.map3d

import com.pocket.xiaoli.data.map.Campus3DTestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Campus3DTourTest {

    private val layout = requireNotNull(Campus3DLayout.prepare(Campus3DTestAssets.loadScene()))

    @Test
    fun `导览按固定顺序列出真实站点`() {
        val stops = buildCampus3DTour(layout)

        assertEquals(
            listOf("1号教学楼", "3号教学楼", "综合楼", "商业楼", "餐厅", "琴心湖", "田径场", "篮球场"),
            stops.map { it.title },
        )
        assertEquals(3, (stops.first { it.title == "餐厅" }.hit as Campus3DHit.Building).shape.levels)
    }

    @Test
    fun `每个站点都来自场景已有对象且 id 唯一`() {
        val stops = buildCampus3DTour(layout)

        assertEquals(stops.size, stops.map { it.id }.toSet().size)
        val buildingIds = layout.buildings.map { it.id }.toSet()
        val waterIds = layout.water.map { it.id }.toSet()
        val pitchIds = layout.pitches.map { it.id }.toSet()

        stops.forEach { stop ->
            val exists = when (val hit = stop.hit) {
                is Campus3DHit.Building -> hit.id in buildingIds
                is Campus3DHit.Water -> hit.id in waterIds
                is Campus3DHit.Pitch -> hit.id in pitchIds
                is Campus3DHit.Poi -> false
            }
            if (!exists) fail("导览站点 ${stop.title} 不在场景数据中")
        }
    }

    @Test
    fun `来源说明区分 OSM 与影像描点`() {
        val stops = buildCampus3DTour(layout)

        val teachingOne = stops.first { it.title == "1号教学楼" }
        assertTrue(teachingOne.subtitle.contains("OSM 实测轮廓"))
        assertTrue(teachingOne.subtitle.contains("又称第一教学楼"))

        val teachingThree = stops.first { it.title == "3号教学楼" }
        assertTrue(teachingThree.subtitle.contains("影像描点轮廓"))
        assertTrue(teachingThree.subtitle.contains("又称三教"))

        val lake = stops.first { it.title == "琴心湖" }
        assertTrue(lake.subtitle.contains("OSM 实测水体轮廓"))

        val track = stops.first { it.title == "田径场" }
        assertTrue(track.subtitle.contains("OSM 运动场地"))
    }

    @Test
    fun `聚焦镜头对准每个站点的锚点`() {
        buildCampus3DTour(layout).forEach { stop ->
            val anchor = when (val hit = stop.hit) {
                is Campus3DHit.Building -> hit.shape.centroid
                is Campus3DHit.Water -> hit.shape.centroid
                is Campus3DHit.Pitch -> hit.shape.centroid
                is Campus3DHit.Poi -> hit.shape.position
            }
            val camera = layout.focusCamera(stop.hit, aspect = 0.46f)
            assertEquals(stop.title, anchor.x, camera.target.x, 0.01f)
            assertEquals(stop.title, anchor.z, camera.target.z, 0.01f)
            assertTrue(stop.title, camera.distance > 0f)
        }
    }
}
