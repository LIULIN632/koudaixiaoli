package com.pocket.xiaoli.ui.treasurebox

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.mcd.McProteinCatalogParser
import com.pocket.xiaoli.data.mcd.McProteinSlot
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「麦当劳蛋白榜」：两张图 + 三条交互约定。
 *
 * - `342` 浅色 + 早餐（首屏：hero / 时段 / 凑蛋白 / 榜首）；
 * - `343` 深色 + 正餐（换一套菜单，同时看暗底上 top3 的强调色）。
 *
 * 榜单来自真实的 `mcd_protein.json`（不是测试里另造的样本）—— 数据文件改坏时截图会变。
 * 时段用 `initialSlot` 固定，不让基线随运行时间漂。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class McProteinScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val catalog = run {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val raw = context.assets.open("mcd_protein.json").bufferedReader().use { it.readText() }
        McProteinCatalogParser.parse(raw)
    }

    @Test
    fun `蛋白榜截图`() {
        assertNotNull(catalog)
        val dark = mutableStateOf(false)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark.value,
            ) {
                McProteinIndexContent(
                    catalog = catalog,
                    initialSlot = if (dark.value) McProteinSlot.DINNER else McProteinSlot.BREAKFAST,
                    onBack = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/342-mcd-protein-light.png")

        composeRule.runOnIdle { dark.value = true }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/343-mcd-protein-dark.png")
    }

    @Test
    fun `切到正餐出现正餐单品`() {
        composeRule.setContent {
            PocketXiaoliTheme {
                McProteinIndexContent(
                    catalog = catalog,
                    initialSlot = McProteinSlot.BREAKFAST,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("原味板烧鸡腿炒双蛋堡").assertExists()
        composeRule.onNodeWithText("正餐").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("麦麦脆汁鸡1块(鸡腿或带骨里脊)").assertExists()
        composeRule.onNodeWithText("原味板烧鸡腿炒双蛋堡").assertDoesNotExist()
    }

    @Test
    fun `预算四十默认给出两份脆汁鸡`() {
        composeRule.setContent {
            PocketXiaoliTheme {
                McProteinIndexContent(
                    catalog = catalog,
                    initialSlot = McProteinSlot.DINNER,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("2 × 麦麦脆汁鸡1块(鸡腿或带骨里脊)", substring = true)
            .assertExists()
        composeRule.onNodeWithText("在预算内", substring = true).assertExists()
    }
}
