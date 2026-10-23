package com.pocket.xiaoli.ui.map3d

import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.map.indoor.IndoorFloorPlan
import com.pocket.xiaoli.data.map.indoor.IndoorPlanDataSource
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 室内拓扑预览的组件快照。
 *
 * 这三张图守住的是「底图能画、点房间能选中、路线能叠上去、列表视图能兜底」，
 * 不证明数据已实测 —— 图里自带「拓扑示意 · 未按实际比例」水印。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class IndoorFloorPlanScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val plan: IndoorFloorPlan by lazy {
        val result = IndoorPlanDataSource.fromAssets(RuntimeEnvironment.getApplication()).load()
        requireNotNull(result.plan) { result.issues.joinToString("\n") }
    }

    private fun setContent() {
        composeRule.setContent {
            PocketXiaoliTheme(darkTheme = false) {
                IndoorFloorPlanPreviewScreen(onBack = {})
            }
        }
    }

    @Test
    fun `拓扑预览初始状态截图`() {
        setContent()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("拓扑示意 · 未实测").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/230-indoor-topology.png")
    }

    @Test
    fun `点选 415 后高亮并绘出路线截图`() {
        setContent()
        composeRule.waitForIdle()

        val room = plan.rooms.first { it.name == "415" }
        val canvas = composeRule.onNodeWithTag("indoor-plan-canvas").fetchSemanticsNode()
        val point = plan.mapper.toScreen(
            point = room.labelPoint,
            width = canvas.size.width.toFloat(),
            height = canvas.size.height.toFloat(),
        )
        composeRule.onNodeWithTag("indoor-plan-canvas").performTouchInput { click(point) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("已选房间").assertExists()
        composeRule.onNodeWithText("415").assertExists()
        composeRule.onNodeWithText("示意路线").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/231-indoor-topology-route.png")
    }

    @Test
    fun `列表视图截图`() {
        setContent()
        composeRule.onNodeWithTag("indoor-mode-list").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("列表视图 · 读屏可用").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/232-indoor-topology-list.png")
    }

    @Test
    fun `列表点选房间也能展开示意路线`() {
        setContent()
        composeRule.onNodeWithTag("indoor-mode-list").performClick()
        composeRule.onNodeWithText("413").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("示意路线 · 起点 东北梯口").assertExists()
    }

    @Test
    fun `切到西北起点后路线改为向东`() {
        setContent()
        composeRule.waitForIdle()

        val room = plan.rooms.first { it.name == "415" }
        val canvas = composeRule.onNodeWithTag("indoor-plan-canvas").fetchSemanticsNode()
        val point = plan.mapper.toScreen(
            point = room.labelPoint,
            width = canvas.size.width.toFloat(),
            height = canvas.size.height.toFloat(),
        )
        composeRule.onNodeWithTag("indoor-plan-canvas").performTouchInput { click(point) }
        composeRule.onNodeWithTag("indoor-start-n_f1j4_stair_nw").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("起点：西北梯口（不是 1F 东大门）").assertExists()
        composeRule.onNodeWithText("沿主走廊向东直行到 415 的门口").assertExists()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/233-indoor-topology-start-nw.png")
    }
}
