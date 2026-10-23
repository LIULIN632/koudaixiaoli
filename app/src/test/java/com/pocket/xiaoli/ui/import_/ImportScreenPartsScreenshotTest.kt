package com.pocket.xiaoli.ui.import_

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.JwSystemType
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「导入本校课表」页两个关键部件的判据：教务系统九宫格、导入结果卡。
 *
 * 旧网格给 5 个教务系统各写死一对浅色（`#EEF0F4` / `#CDD2DC`…），
 * 深色模式下白字压在浅灰卡上基本看不见；结果卡同样写死了绿色/红色底，
 * 且失败时没有「重新登录」，只能靠返回键摸回去。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ImportScreenPartsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    @Test
    fun `教务系统网格的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                PageBackdrop {
                    JwSystemGrid(onSelect = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/295-import-system-grid-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/296-import-system-grid-dark.png")
    }

    @Test
    fun `导入成功卡的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop {
                    ImportResultCard(
                        success = true,
                        error = null,
                        courseCount = 12,
                        onOpenSettings = {},
                        onBackToTimetable = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/297-import-result-success.png")
    }

    @Test
    fun `导入失败卡的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop {
                    ImportResultCard(
                        success = false,
                        error = "登录超时，请重试",
                        courseCount = 0,
                        onOpenSettings = null,
                        onBackToTimetable = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/298-import-result-failure.png")
    }

    @Test
    fun `点系统卡片回传对应类型，未支持的三项写明走Web导入`() {
        var picked: JwSystemType? = null
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop { JwSystemGrid(onSelect = { picked = it }) }
            }
        }

        // 正方 / 强智可原生导入，另外三项只能走 Web
        composeRule.onAllNodesWithText("请用 Web 导入").assertCountEquals(3)

        composeRule.onNodeWithText("正方教务").performClick()
        assertEquals(JwSystemType.ZHENGFANG, picked)

        composeRule.onNodeWithText("超星学习通").performClick()
        assertEquals(JwSystemType.CHAOXING, picked)
    }

    @Test
    fun `成功卡给出课程数与两个去路`() {
        var settings = 0
        var back = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop {
                    ImportResultCard(
                        success = true,
                        error = null,
                        courseCount = 12,
                        onOpenSettings = { settings++ },
                        onBackToTimetable = { back++ },
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("已导入 12 门课程").assertExists()
        composeRule.onNodeWithText("已完成").assertExists()

        composeRule.onNodeWithText("开启提醒/小组件").performClick()
        composeRule.onNodeWithText("返回课表").performClick()
        assertEquals(1, settings)
        assertEquals(1, back)
    }

    @Test
    fun `失败卡保留原因并能直接重试`() {
        var retries = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop {
                    ImportResultCard(
                        success = false,
                        error = null,
                        courseCount = 0,
                        onOpenSettings = null,
                        onBackToTimetable = {},
                        onRetry = { retries++ },
                    )
                }
            }
        }

        composeRule.onNodeWithText("登录没有通过，请检查学号、密码与验证码后重试。").assertExists()
        composeRule.onNodeWithText("重新登录").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `网页导入成功浮层的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                PageBackdrop {
                    WebImportSuccessOverlay(courseCount = 9, onBackToTimetable = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/299-web-import-success-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/300-web-import-success-dark.png")
    }

    @Test
    fun `网页导入成功浮层显示课程数并可返回课表`() {
        var backs = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PageBackdrop {
                    WebImportSuccessOverlay(courseCount = 9, onBackToTimetable = { backs++ })
                }
            }
        }

        composeRule.onNodeWithText("导入成功").assertExists()
        composeRule.onNodeWithText("已导入 9 门课程").assertExists()
        composeRule.onNodeWithText("返回课表").performClick()
        assertEquals(1, backs)
    }

    @Composable
    private fun PageBackdrop(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalPocketXiaoliColors.current.surfaceBase),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}
