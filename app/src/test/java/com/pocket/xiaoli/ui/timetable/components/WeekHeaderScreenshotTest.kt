package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.util.HolidayMark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 周头的基准图：今日格 + 节假日角标。
 *
 * ## 这组图在验什么
 * 1. **今天落在周三**：今日格必须是主色渐变 + 同色光晕，日期数字加粗，
 *    并且前景色由 `onAccentColor()` 按对比度选（不是写死白色 —— 浅色强调色下白字会读不出来）。
 * 2. **节假日两种角标**：「休」走危险色、「班」走警示色，都是小胶囊，不再是一行裸文字。
 * 3. **极简皮肤**：今日格退化成纯色、无光晕 —— 装饰只属于鲜活皮肤。
 *
 * ⚠️ `today` 是显式注入的固定值。不注入的话基准图会随运行日期漂移，
 * 每个星期跑出来的图都不一样。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class WeekHeaderScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val dark: Boolean,
    )

    private val cases = listOf(
        Case("29-week-header-vivid", SkinStyle.VIVID, dark = false),
        Case("29-week-header-vivid-dark", SkinStyle.VIVID, dark = true),
        Case("29-week-header-minimal", SkinStyle.MINIMAL, dark = false),
    )

    private val holidayMarks = mapOf(
        "2026-10-20" to HolidayMark(label = "休", isOffDay = true),
        "2026-10-24" to HolidayMark(label = "班", isOffDay = false),
    )

    @Test
    fun `周头的今日格与节假日角标截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = case.skin,
                darkTheme = case.dark,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceBase)
                        .padding(vertical = 16.dp)
                        .testTag("week-header"),
                ) {
                    WeekHeader(
                        currentWeek = 7,
                        isCurrentWeek = true,
                        semesterStart = "2026-09-07",
                        holidayMarks = holidayMarks,
                        today = LocalDate.of(2026, 10, 21),
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("week-header")
                .captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
