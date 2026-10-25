package com.pocket.xiaoli.ui.onboarding

import androidx.compose.runtime.mutableStateOf
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首次选校页：两张图 + 两条交互约定。
 *
 * - `340/341` 是浅色 / 深色整页，钉住"两所学校一眼能分开、跳过不抢戏"；
 * - 交互只测**回传的校区 id**：这个 id 会直接交给 `setSelectedCampus`，
 *   写错一个字就会锁到另一所学校的教务，而截图看不出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FirstRunOnboardingScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `首次选校页截图`() {
        val dark = mutableStateOf(false)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = dark.value,
            ) {
                FirstRunSchoolPicker(onPick = {}, onSkip = {})
            }
        }

        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/340-first-run-school-light.png")

        composeRule.runOnIdle { dark.value = true }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/341-first-run-school-dark.png")
    }

    @Test
    fun `点烟大回传南校区 id`() {
        var picked: String? = null
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(onPick = { picked = it }, onSkip = {})
            }
        }

        composeRule.onNodeWithText("烟台大学").performClick()
        composeRule.waitForIdle()
        assertEquals("ytu_south", picked)
    }

    @Test
    fun `点烟理回传本校区 id`() {
        var picked: String? = null
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(onPick = { picked = it }, onSkip = {})
            }
        }

        composeRule.onNodeWithText("烟台理工学院").performClick()
        composeRule.waitForIdle()
        assertEquals("ytlg_main", picked)
    }

    @Test
    fun `先随便看看不选学校`() {
        var picked: String? = null
        var skipped = false
        composeRule.setContent {
            PocketXiaoliTheme {
                FirstRunSchoolPicker(onPick = { picked = it }, onSkip = { skipped = true })
            }
        }

        composeRule.onNodeWithText("先随便看看").performClick()
        composeRule.waitForIdle()
        assertNull(picked)
        assertEquals(true, skipped)
    }
}
