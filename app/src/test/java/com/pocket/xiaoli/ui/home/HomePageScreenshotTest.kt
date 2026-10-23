package com.pocket.xiaoli.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.weather.WeatherCurrent
import com.pocket.xiaoli.data.weather.WeatherSnapshot
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * 首页**整页**基线（2026-10 补）。
 *
 * 此前首页只有零散的组件基线（110-116 的下一节课卡 / 待办区），
 * 用户说"界面不好看"时，这些图拼不出一屏的真实观感 —— 留白、模块间距、
 * 顶栏彩晕与卡片阴影的关系，全都只在整页里才成立。
 *
 * ## 为什么这张图能稳定
 *
 * - **时钟停表**：`HomeClockHeader(fixedNow = …)`，否则每秒变一次，回放必红。
 * - **日期不可解析**：考试行 `date = ""`、作业 `dueDate = "6月15日"` ——
 *   不产生"还有 N 天"这种随当日变化的文案。
 * - **底栏高度显式给 88dp**：与线上 `LocalBottomBarInset` 的量测值一致，
 *   FAB 和列表底部的让位才对得上。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomePageScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val dark: Boolean,
    )

    private val cases = listOf(
        Case("119-home-page-vivid", SkinStyle.VIVID, dark = false),
        Case("250-home-page-vivid-dark", SkinStyle.VIVID, dark = true),
    )

    private val fixedNow: LocalDateTime = LocalDateTime.of(2026, 10, 21, 18, 24)

    private val course = HomeCourse(
        course = CourseEntity(
            name = "建设法规",
            teacher = "王若黎",
            room = "文二教109",
            dayOfWeek = 3,
            startSlot = 9,
            endSlot = 10,
        ),
        startTime = "18:30",
        endTime = "20:10",
        dayOffset = 0,
    )

    private val exam = ExamEntity(
        id = 1,
        courseName = "建设法规",
        date = "",
        time = "09:00-11:00",
        location = "文二教109",
        seat = "12",
    )

    private val homework = HomeworkEntity(
        id = 2,
        courseName = "建设法规",
        content = "P45 习题 3-5",
        dueDate = "6月15日",
    )

    private val task = TaskEntity(id = 3, name = "取快递")

    /**
     * 时钟旁的天气胶囊用固定快照。
     *
     * 时间跟 [fixedNow] 对齐、温度取一个不会过期的常数 —— 真实仓储的数据会变，
     * 拿它当基线第二天就红了。只画当前天气，所以小时/日列表可以为空。
     */
    private val weather = WeatherSnapshot(
        current = WeatherCurrent(
            time = "2026-10-21T18:24",
            temperature = 17.6,
            apparentTemperature = 16.8,
            humidity = 52,
            precipitation = 0.0,
            weatherCode = 0,
            cloudCover = 5,
            windSpeed = 9.0,
            windDirection = 90,
            isDay = true,
        ),
        hourly = emptyList(),
        daily = emptyList(),
        fetchedAt = 0L,
    )

    private val state = HomeUiState(
        week = 8,
        nextCourse = course,
        pendingExams = listOf(exam),
        pendingHomework = listOf(homework),
        pendingTasks = listOf(task),
        focusCounts = mapOf(3L to 2),
        hasTimetable = true,
        semesterCalibrated = true,
    )

    @Test
    fun `首页整页截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = case.skin,
                darkTheme = case.dark,
            ) {
                CompositionLocalProvider(LocalBottomBarInset provides 88.dp) {
                    HomeContent(
                        state = state,
                        hasUnread = true,
                        weather = weather,
                        modules = HomeModule.entries,
                        hiddenModules = emptySet(),
                        fixedNow = fixedNow,
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            // 交错进入动画最多累计 270ms 延迟 + 260ms 时长；多推一点，图才不会抓到半透明帧。
            composeRule.mainClock.advanceTimeBy(1_000)
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
