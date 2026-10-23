package com.pocket.xiaoli.ui.import_

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
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
 * 「PDF 导入」页的外观与语义判据。
 *
 * 这一页原先写死了琥珀色（浅色底 + 橙色字），深色模式下也照旧，且整页不可滚。
 * 改版后它和文件导入页共用卡片语言，配色全部走主题 token ——
 * 这里同时录深色基线，就是为了守住"警示色跟随主题"这条。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class PdfImportScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    @Test
    fun `PDF导入选文件前的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                PdfImportPage(pickedName = null, onChooseFile = {}, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/292-pdf-import-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/293-pdf-import-dark.png")
    }

    @Test
    fun `PDF导入选完文件的截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PdfImportPage(
                    pickedName = "2026 秋 教务课表.pdf",
                    onChooseFile = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/294-pdf-import-picked.png")
    }

    @Test
    fun `选文件与换格式的按钮各归各位`() {
        var chooses = 0
        var backs = 0
        val picked = mutableStateOf<String?>(null)

        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                PdfImportPage(
                    pickedName = picked.value,
                    onChooseFile = { chooses++ },
                    onBack = { backs++ },
                )
            }
        }

        composeRule.onNodeWithText("选择 PDF 文件").performClick()
        assertEquals(1, chooses)

        composeRule.runOnIdle { picked.value = "课表.pdf" }
        composeRule.onNodeWithText("课表.pdf").assertExists()
        composeRule.onNodeWithText("这个 PDF 还不能直接解析").assertExists()

        composeRule.onNodeWithText("换个格式导入").performClick()
        composeRule.onNodeWithText("重新选择").performClick()
        assertEquals(1, backs)
        assertEquals(2, chooses)
    }
}
