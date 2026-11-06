package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.ui.profile.components.PreviewGridCourseCard
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「有作业」的高亮边框基线（2026-10-31：用户把课名旁的感叹号角标换成了整块高亮边框）。
 *
 * ## 为什么单独给边框录一张图
 *
 * 边框画在 `drawBehind` 里，[CourseCardContent] 的基线（100-104）截不到它；
 * 课表整页要 `AppContainer` 才渲得出来。外观预览的 [PreviewGridCourseCard] 是唯一
 * 能独立渲染"带边框的整块"的组件，所以借它把**有作业 / 没作业**两块并排拍一张：
 * 高亮边框够不够一眼认出、会不会和课程自己的边框色混，只能靠图看。
 *
 * 顺带钉读屏语义：角标撤掉之后，「有作业」这句话改由一个 1dp 的语义节点携带。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CourseCellHomeworkBorderScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun cell(hasHomework: Boolean, homeworkUrgent: Boolean = false) = PreviewGridCourseCard(
        course = CourseEntity(
            name = "高等数学A",
            teacher = "李文博",
            room = "教三楼 305",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            colorIndex = 2,
        ),
        hasHomework = hasHomework,
        homeworkUrgent = homeworkUrgent,
        isNonCurrent = false,
        x = 0.dp,
        y = 0.dp,
        width = 104.dp,
        height = 104.dp,
        opacity = 1f,
        radius = 12,
        borderStyle = 0,
        centered = false,
        textSize = 13,
        showTeacher = true,
        showRoom = true,
        showCampus = false,
        verticalLayout = true,
        showOddEven = false,
        otherWeekAlpha = 0.5f,
    )

    @Test
    fun `普通 有作业 已逾期 三块并排`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        cell(hasHomework = false)
                        cell(hasHomework = true)
                        cell(hasHomework = true, homeworkUrgent = true)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/388-course-cell-homework.png")
    }

    @Test
    fun `角标撤掉之后读屏仍然听得到有作业`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Box(Modifier.fillMaxSize().background(LocalPocketXiaoliColors.current.surfaceBase)) {
                    cell(hasHomework = true)
                }
            }
        }
        composeRule.onNodeWithContentDescription("有作业").assertExists()
    }

    /**
     * 颜色对读屏用户不存在 —— 逾期必须说出来，否则"有作业"三个字
     * 分不清是"两周后交"还是"今天 23:59 截止"。
     */
    @Test
    fun `逾期那块的读屏文案带上已逾期`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Box(Modifier.fillMaxSize().background(LocalPocketXiaoliColors.current.surfaceBase)) {
                    cell(hasHomework = true, homeworkUrgent = true)
                }
            }
        }
        composeRule.onNodeWithContentDescription("有作业已逾期").assertExists()
    }
}
