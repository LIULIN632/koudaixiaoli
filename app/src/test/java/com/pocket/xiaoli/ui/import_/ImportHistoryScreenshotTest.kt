package com.pocket.xiaoli.ui.import_

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.model.LastImportInfo
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
 * 「导入记录」页的外观与语义判据。
 *
 * 这一页原先只有一列三行灰字的 `Surface` 卡，成功/失败全靠一枚图标区分。
 * 改版后状态色与入口页「最近一次」卡一致，所以这组基线也用来证明
 * **两处用的是同一套视觉语言**（入口页见 `280..283`）。
 *
 * 时间戳全部固定，否则每天跑出来都是新图。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ImportHistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var dark by mutableStateOf(false)

    private val base = 1_760_000_000_000L

    private val history = listOf(
        LastImportInfo(
            source = "教务直连",
            courseCount = 11,
            success = true,
            timestamp = base,
        ),
        LastImportInfo(
            source = "网页导入",
            courseCount = 0,
            success = false,
            detail = "登录超时，请重试",
            timestamp = base - 3 * 3_600_000L,
        ),
        LastImportInfo(
            source = "文件导入",
            courseCount = 4,
            success = true,
            timestamp = base - 26 * 3_600_000L,
        ),
        LastImportInfo(
            source = "网页导入",
            courseCount = 0,
            success = false,
            detail = "未识别到课表，请确认已进入「我的课表」页面",
            timestamp = base - 50 * 3_600_000L,
        ),
    )

    @Test
    fun `导入记录页截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark,
            ) {
                ImportHistoryPage(history = history, onBack = {}, onClear = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/289-import-history-light.png")

        dark = true
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/290-import-history-dark.png")
    }

    @Test
    fun `导入记录空态截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                ImportHistoryPage(history = emptyList(), onBack = {}, onClear = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot()
            .captureRoboImage("src/test/screenshots/291-import-history-empty.png")
    }

    @Test
    fun `空态没有清空入口且可以直接去导入`() {
        var backs = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                ImportHistoryPage(history = emptyList(), onBack = { backs++ }, onClear = {})
            }
        }
        composeRule.onNodeWithContentDescription("清空记录").assertDoesNotExist()
        composeRule.onNodeWithText("去导入课表").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `清空记录要先确认`() {
        var clears = 0
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                ImportHistoryPage(history = history, onBack = {}, onClear = { clears++ })
            }
        }

        composeRule.onNodeWithContentDescription("清空记录").performClick()
        composeRule.onNodeWithText("将删除本机保存的全部导入记录，不影响已导入的课表。").assertExists()
        composeRule.onNodeWithText("清空").performClick()
        assertEquals(1, clears)
        // 确认后对话框收起，「清空」按钮不再存在。
        composeRule.onNodeWithText("清空").assertDoesNotExist()
    }

    @Test
    fun `成功失败与来源都写在行上`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                ImportHistoryPage(history = history, onBack = {}, onClear = {})
            }
        }
        composeRule.onNodeWithText("共 4 条 · 只保存在本机").assertExists()
        composeRule.onNodeWithText("导入 11 门课程").assertExists()
        composeRule.onNodeWithText("登录超时，请重试").assertExists()
        composeRule.onNodeWithText("导入 4 门课程").assertExists()
        composeRule.onAllNodesWithText("失败").assertCountEquals(2)
        composeRule.onAllNodesWithText("成功").assertCountEquals(2)
    }
}
