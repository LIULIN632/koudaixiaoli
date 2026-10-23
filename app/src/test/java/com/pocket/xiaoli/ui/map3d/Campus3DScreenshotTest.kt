package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.map.BuiltinRoomCells
import com.pocket.xiaoli.data.map.Campus3DTestAssets
import com.pocket.xiaoli.data.map.RoomCell
import com.pocket.xiaoli.data.map.model.Campus3DScene
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 3D 沙盘的真实组件快照（`210-campus-3d.png`）。
 *
 * 渲的是线上同一份 [Campus3DScreen]，场景取自仓库里的
 * `campus_3d.json` —— 不是手搓的示意数据。这样图的绿色同时代表：
 * 数据能解析、相机能把校园放进屏幕、Canvas 渲染链路在 JVM 上跑得通。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class Campus3DScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `3D 校园沙盘截图`() {
        val scene = loadScene()
        assertTrue(scene.hasGeometry)

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(scene = scene, onClose = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/210-campus-3d.png")
    }

    /**
     * 点选综合楼后镜头飞近，立体建筑与详情卡同时出现（`211-campus-3d-selected.png`）。
     *
     * 坐标不是手写的：用页面同一套 [Campus3DLayout] + [Campus3DProjection] 算出楼顶中心，
     * 因此这条测试同时守住了「相机初始化 → 投影 → 命中测试 → 聚焦取景 → 详情卡」整条链路。
     */
    @Test
    fun `3D 校园选中楼栋截图`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "综合楼" }

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(scene = scene, onClose = {})
            }
        }
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val camera = layout.initialCamera(root.width.toFloat() / root.height.toFloat())
        val projection = Campus3DProjection(camera, root.width.toFloat(), root.height.toFloat())
        val roof = requireNotNull(projection.projectFlat(building.centroid, building.height))

        composeRule.onRoot().performTouchInput { click(Offset(roof.x, roof.y)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("综合楼").assertExists()
        composeRule.onNodeWithText("查看各层平面图").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/211-campus-3d-selected.png")
    }

    /**
     * 楼栋详情卡 → 各层平面图（`212-campus-3d-floor-plan.png`）。
     *
     * 当前没有真实教室录入数据，所以这一屏应该展示 OSM 外轮廓和明确的空态，
     * 而不是画一套编造出来的房间。
     */
    @Test
    fun `3D 校园楼层平面图截图`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "综合楼" }

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    campusId = scene.campusId,
                    showFloorPlans = true,
                    onManageRooms = { _, _ -> },
                )
            }
        }
        val root = composeRule.onRoot().fetchSemanticsNode().size
        val camera = layout.initialCamera(root.width.toFloat() / root.height.toFloat())
        val projection = Campus3DProjection(camera, root.width.toFloat(), root.height.toFloat())
        val roof = requireNotNull(projection.projectFlat(building.centroid, building.height))

        composeRule.onRoot().performTouchInput { click(Offset(roof.x, roof.y)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("查看各层平面图").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("OSM 实测外轮廓", substring = true).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/212-campus-3d-floor-plan.png")
    }

    /**
     * 一教 4F 的真实房间（`213-campus-3d-floor-1jian.png`）。
     *
     * 楼栋来自 `campus_3d.json`，房间来自实拍指引牌整理的内置数据。
     * 1号教学楼在竖屏初始取景里位于屏幕外，点选链路已由 211/212 覆盖，
     * 这一屏直接渲染楼栋详情进入的同一个 [Campus3DFloorPlanScreen]。
     */
    @Test
    fun `一教楼层图使用指引牌数据`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "1号教学楼" }

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DFloorPlanScreen(
                    building = building,
                    campusId = scene.campusId,
                    roomCells = BuiltinRoomCells.all,
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4F").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1号教学楼").assertExists()
        composeRule.onNodeWithText("413").assertExists()
        composeRule.onNodeWithText("东北楼梯").assertExists()
        composeRule.onNodeWithText("北区卫生间").assertExists()
        composeRule.onNodeWithText("4F").assertIsSelected()
        composeRule.onNodeWithContentDescription("413，北区，教室").assertExists()
        composeRule.onNodeWithContentDescription("放大平面图").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/213-campus-3d-floor-1jian.png")
    }

    /**
     * 平面图缩放（`224-campus-3d-floor-plan-zoom.png`）。
     *
     * 缩放只改视口，不增删房间；复位后必须回到 100%。
     */
    @Test
    fun `平面图可放大并复位`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "1号教学楼" }

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DFloorPlanScreen(
                    building = building,
                    campusId = scene.campusId,
                    roomCells = BuiltinRoomCells.all,
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4F").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("放大平面图").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floor-plan-viewport").performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floor-plan-viewport").performTouchInput {
            swipeDown()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("125%").assertExists()
        composeRule.onNodeWithText("413").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/224-campus-3d-floor-plan-zoom.png")

        composeRule.onNodeWithContentDescription("放大平面图").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("150%").assertExists()

        composeRule.onNodeWithText("复位").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("100%").assertExists()
    }

    /** 从课表「找教室」进入时，直接聚焦对应楼栋并弹出详情卡（`214-campus-3d-focus-1jian.png`）。 */
    @Test
    fun `从课表进入时聚焦对应楼栋`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    initialBuildingName = "1号教学楼",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1号教学楼").assertExists()
        composeRule.onNodeWithText("查看各层平面图").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/214-campus-3d-focus-1jian.png")
    }

    /**
     * 3D 页内搜索（`215-campus-3d-search.png`）。
     *
     * 搜「406」应同时命中教室和它所在的 1号教学楼；这条测试守的是
     * 「搜索面板 → 房间匹配 → 楼栋关联」整条链路。
     */
    @Test
    fun `3D 校园内搜索教室截图`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    campusId = scene.campusId,
                    roomCells = BuiltinRoomCells.all,
                )
            }
        }

        composeRule.onNodeWithContentDescription("搜索楼栋、教室和地点").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("campus3d-search-input").performClick()
        composeRule.onNodeWithTag("campus3d-search-input").performTextInput("406")
        composeRule.waitForIdle()

        // 输入框和结果行各有一个「406」。
        composeRule.onAllNodesWithText("406").assertCountEquals(2)
        composeRule.onNodeWithText("1号教学楼 · 4F", substring = true).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/215-campus-3d-search.png")
    }

    /**
     * 选中教室结果应直达它所在的楼层（`216-campus-3d-search-jump.png`）。
     *
     * 406 在 1号教学楼 4F，打开平面图时楼层应已经选在 4F，而不是默认首层。
     */
    @Test
    fun `搜索教室直达对应楼层截图`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    campusId = scene.campusId,
                    roomCells = BuiltinRoomCells.all,
                )
            }
        }

        composeRule.onNodeWithContentDescription("搜索楼栋、教室和地点").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("campus3d-search-input").performClick()
        composeRule.onNodeWithTag("campus3d-search-input").performTextInput("406")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1号教学楼 · 4F", substring = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("4F 平面图").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/216-campus-3d-search-jump.png")
    }

    /**
     * 三教（3号教学楼）使用补充轮廓渲染（`217-campus-3d-jx3.png`）。
     *
     * 这栋楼没有 OSM 轮廓，楼体来自正射影像人工描点；卡片要同时说明
     * 「影像描点轮廓」和误差来源，不能冒充 OSM 实测数据。
     */
    @Test
    fun `三教补充轮廓显示楼体与来源`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    initialBuildingName = "3号教学楼",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3号教学楼").assertExists()
        composeRule.onNodeWithText("影像描点轮廓", substring = true).assertExists()
        composeRule.onNodeWithText("非 OSM 数据", substring = true).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/217-campus-3d-jx3.png")
    }

    /**
     * 校园导览首站（`218-campus-3d-tour.png`）。
     *
     * 导览只取场景里真实存在的对象；首站应是 1号教学楼，
     * 并且卡片同时给出站序、来源和播放控制。
     */
    @Test
    fun `校园导览首站截图`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(scene = scene, onClose = {})
            }
        }

        composeRule.onNodeWithText("校园导览").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("第 1 / 8 站").assertExists()
        composeRule.onNodeWithText("1号教学楼").assertExists()
        composeRule.onNodeWithText("自动播放").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/218-campus-3d-tour.png")
    }

    /** 导览第二站按路线推进到三教，并沿用补充轮廓的来源说明（`219-campus-3d-tour-jx3.png`）。 */
    @Test
    fun `校园导览下一站截图`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(scene = scene, onClose = {})
            }
        }

        composeRule.onNodeWithText("校园导览").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("下一站").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("第 2 / 8 站").assertExists()
        composeRule.onNodeWithText("3号教学楼").assertExists()
        composeRule.onNodeWithText("影像描点轮廓", substring = true).assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/219-campus-3d-tour-jx3.png")
    }

    /**
     * 今日有课楼栋图层（`220-campus-3d-today-courses.png`、`221-campus-3d-today-selected.png`）。
     *
     * 课表数据在这里直接注入「楼栋 id → 课节数」，不依赖数据库；
     * 断言薄荷色图例、楼栋标签和详情卡里的今日课节数，
     * 同时守住「页面参数 → 沙盘 → 楼体绘制」整条链路。
     */
    @Test
    fun `今日有课楼栋图层截图`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "综合楼" }
        val counts = mapOf(
            building.id to 2,
            layout.buildings.first { it.name == "商业楼" }.id to 1,
            layout.buildings.first { it.name == "3号教学楼" }.id to 1,
        )

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    todayCourseCounts = counts,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("今日有课 · 3 栋").assertExists()
        composeRule.onNodeWithText("仅统计已录入地点的课").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/220-campus-3d-today-courses.png")

        val root = composeRule.onRoot().fetchSemanticsNode().size
        val camera = layout.initialCamera(root.width.toFloat() / root.height.toFloat())
        val projection = Campus3DProjection(camera, root.width.toFloat(), root.height.toFloat())
        val roof = requireNotNull(projection.projectFlat(building.centroid, building.height))
        composeRule.onRoot().performTouchInput { click(Offset(roof.x, roof.y)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("今日 2 节课").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/221-campus-3d-today-selected.png")

        composeRule.onNodeWithText("建筑档案").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("今日 2 节课").assertExists()
    }

    /**
     * 一教建筑档案（`222-campus-3d-building-profile.png`）。
     *
     * 档案里的教室统计、楼层覆盖都从 [BuiltinRoomCells] 实算；
     * 点 4F 应直接进入 4F 平面图，守住「档案 → 楼层」跳转链路。
     */
    @Test
    fun `一教建筑档案显示真实房间统计`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    campusId = scene.campusId,
                    roomCells = BuiltinRoomCells.all,
                    initialBuildingName = "1号教学楼",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("建筑档案").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("又称 · 第一教学楼").assertExists()
        composeRule.onNodeWithText("78 间教室").assertExists()
        composeRule.onNodeWithText("5 / 6 层有数据").assertExists()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/222-campus-3d-building-profile.png"
        )

        composeRule.onNodeWithText("4F").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4F 平面图").assertExists()
    }

    /** 三教档案把描点来源摆在最显眼的位置（`223-campus-3d-building-profile-jx3.png`）。 */
    @Test
    fun `三教建筑档案标明影像描点来源`() {
        val scene = loadScene()

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DScreen(
                    scene = scene,
                    onClose = {},
                    initialBuildingName = "3号教学楼",
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("建筑档案").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("影像描点").assertExists()
        composeRule.onNodeWithText("非 OSM 数据", substring = true).assertExists()
        composeRule.onNodeWithText("这栋楼还没有录入室内房间").assertExists()
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/223-campus-3d-building-profile-jx3.png"
        )
    }

    @Test
    fun `楼层图使用已录入的教室数据`() {
        val scene = loadScene()
        val layout = requireNotNull(Campus3DLayout.prepare(scene))
        val building = layout.buildings.first { it.name == "综合楼" }
        val cell = RoomCell(
            id = "test-room-301",
            campusId = scene.campusId,
            building = "综合楼",
            floor = "3 层",
            room = "301",
            name = "测试教室",
            row = 0,
            col = 0,
        )

        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                Campus3DFloorPlanScreen(
                    building = building,
                    campusId = scene.campusId,
                    roomCells = listOf(cell),
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3F").assertExists()
        composeRule.onNodeWithText("301").assertExists()
        composeRule.onNodeWithText("测试教室").assertExists()
    }

    private fun loadScene(): Campus3DScene = Campus3DTestAssets.loadScene()
}
