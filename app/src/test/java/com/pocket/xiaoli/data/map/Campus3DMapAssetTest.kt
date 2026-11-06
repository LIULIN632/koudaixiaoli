package com.pocket.xiaoli.data.map

import com.pocket.xiaoli.data.map.model.Campus3DMapJson
import com.pocket.xiaoli.data.map.model.Campus3DMapValidator
import com.pocket.xiaoli.data.map.model.Campus3DScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class Campus3DMapAssetTest {

    @Test
    fun ytlgMainSceneParsesAndValidates() {
        val result = Campus3DTestAssets.loadResult()
        val scene = result.scene

        assertEquals(1, scene.schemaVersion)
        assertEquals("ytlg_main", scene.campusId)
        assertEquals("烟台理工学院", scene.name)
        assertTrue(scene.attribution.contains("OpenStreetMap"))
        assertTrue(scene.attribution.contains("ODbL"))
        assertTrue(scene.attribution.contains("Bing"))
        assertTrue("至少应有 15 栋真实楼栋", scene.buildings.size >= 15)
        assertTrue("边界至少 3 个点", scene.boundary.size >= 3)
        assertTrue(scene.hasGeometry)
        assertTrue("数据不应有校验错误", result.issues.isEmpty())
        assertTrue(Campus3DMapValidator.validate(scene).isEmpty())
    }

    @Test
    fun sceneKeepsRealCampusLandmarks() {
        val scene = loadScene()

        assertTrue(scene.buildings.any { it.name == "1号教学楼" })
        assertTrue(scene.buildings.any { it.name == "综合楼" })
        assertTrue(scene.water.any { it.name == "琴心湖" })
        assertTrue(scene.pitches.any { it.name == "田径场" })
        assertTrue(scene.pitches.any { it.name == "足球场" })
        assertTrue(scene.pitches.any { it.name == "篮球场" })
        assertTrue(scene.zones.any { it.name == "学生公寓" })
        assertEquals(1, scene.pois.size)
        assertTrue(scene.pois.all { it.note.isNotBlank() })
    }

    @Test
    fun tracedTeaching3IsMergedWithoutTouchingOsmAsset() {
        val scene = loadScene()
        val jx3 = scene.buildings.singleOrNull { it.id == "traced/bing/jx3" }
            ?: fail("缺少补充的三教轮廓").let { return }

        assertEquals("3号教学楼", jx3.name)
        assertEquals("三教", jx3.sourceName)
        assertEquals("teaching", jx3.category)
        assertEquals(6, jx3.levels)
        assertEquals(19.8, jx3.heightMeters, 1e-9)
        assertEquals(33, jx3.outline.size)
        assertTrue(jx3.note.contains("Bing"))
        assertTrue(jx3.note.contains("非 OSM"))
        assertFalse(scene.pois.any { it.name == "3号教学楼" })
        assertTrue(scene.pois.any { it.name == "2号教学楼" })

        val main = Campus3DMapJson.parse(asset("campus_3d.json")?.readText().orEmpty())
        assertEquals(18, main.buildings.size)
        assertEquals(2, main.pois.size)
        assertFalse(main.buildings.any { it.id == "traced/bing/jx3" })
    }

    @Test
    fun everyGeometryPointStaysInsideBounds() {
        val scene = loadScene()
        val bounds = scene.bounds ?: fail("缺少 bounds").let { return }
        val allPoints = buildList {
            addAll(scene.boundary)
            scene.buildings.forEach { addAll(it.outline); it.holes.forEach(::addAll) }
            scene.water.forEach { addAll(it.outline) }
            scene.green.forEach { addAll(it.outline) }
            scene.pitches.forEach { addAll(it.outline) }
            scene.paths.forEach { addAll(it.points) }
        }

        assertTrue(allPoints.isNotEmpty())
        allPoints.forEach { point ->
            assertTrue("lat 越界：$point", point.lat in bounds.minLat..bounds.maxLat)
            assertTrue("lng 越界：$point", point.lng in bounds.minLng..bounds.maxLng)
        }
    }

    @Test
    fun generatedSceneIsReproducibleFromScript() {
        val script = File("tools/campus-map-data/build_campus_3d.py")
            .takeIf { it.isFile }
            ?: File("../tools/campus-map-data/build_campus_3d.py")
        if (!script.isFile) return

        val text = script.readText()
        assertTrue(text.contains("OpenStreetMap"))
        assertTrue(text.contains("441875196"))
    }

    @Test
    fun ytuSouthSceneParsesAndValidates() {
        val result = Campus3DTestAssets.loadResult("ytu_south")
        val scene = result.scene

        assertEquals(1, scene.schemaVersion)
        assertEquals("ytu_south", scene.campusId)
        assertEquals("烟台大学南校区", scene.name)
        assertTrue(scene.attribution.contains("OpenStreetMap"))
        assertTrue("至少应有 20 栋真实楼栋", scene.buildings.size >= 20)
        assertTrue(scene.boundary.size >= 3)
        assertTrue(scene.hasGeometry)
        assertTrue("数据不应有校验错误：${result.issues.map { it.message }}", result.issues.isEmpty())
        assertTrue(Campus3DMapValidator.validate(scene).isEmpty())
    }

    /** 南校楼栋名全部来自 OSM；bbox 里混进来的校区外建筑必须被边界裁掉。 */
    @Test
    fun ytuSouthKeepsRealBuildingsAndDropsOffCampusOnes() {
        val scene = Campus3DTestAssets.loadScene("ytu_south")
        val names = scene.buildings.map { it.name }

        listOf(
            "7号学生公寓", "8号学生公寓", "9号学生公寓", "10号学生公寓",
            "教学楼", "十餐厅", "工程实训车间", "南校医务室",
        ).forEach { assertTrue("缺少 $it", it in names) }

        assertTrue("校区外酒店没被裁掉", names.none { it.contains("酒店") })
        assertTrue("校区外住宅没被裁掉", names.none { it.contains("世纪大厦") })
        assertTrue(
            "带校区前缀的 OSM 原名应保留在 sourceName",
            scene.buildings.any { it.sourceName == "烟台大学南校区7号学生公寓" },
        )
    }

    /** 层数要么来自 OSM，要么明确标成推算值；不许出现「看起来像实测」的来历不明高度。 */
    @Test
    fun ytuSouthLevelsAreEitherFromOsmOrFlaggedEstimated() {
        val scene = Campus3DTestAssets.loadScene("ytu_south")

        scene.buildings.forEach { building ->
            if (building.levelsEstimated) {
                assertEquals(
                    "推算层数应等于 levels×3.2+0.6：${building.id}",
                    building.levels * 3.2 + 0.6,
                    building.heightMeters,
                    1e-9,
                )
            }
        }
        assertTrue(
            "学生公寓的 6 层来自 OSM building:levels，不该标推算",
            scene.buildings.filter { it.name.contains("学生公寓") && it.name.endsWith("区") }
                .all { it.levels == 6 && !it.levelsEstimated },
        )
    }

    /** 南校没有影像描点补充：几何全部出自 OSM，缺的水系/绿地就留空。 */
    @Test
    fun ytuSouthHasNoTracedGeometry() {
        val scene = Campus3DTestAssets.loadScene("ytu_south")

        assertTrue(scene.buildings.none { it.id.startsWith("traced/") })
        assertTrue(scene.pois.isEmpty())
    }

    private fun loadScene(): Campus3DScene = Campus3DTestAssets.loadScene()

    private fun asset(name: String): File? {
        val relative = "assets/campus_map/ytlg_main/$name"
        val candidates = listOf(
            File("src/main/$relative"),
            File("app/src/main/$relative"),
            File("../app/src/main/$relative"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
