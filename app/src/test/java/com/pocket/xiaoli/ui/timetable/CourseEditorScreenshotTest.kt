package com.pocket.xiaoli.ui.timetable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.CourseEntity
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
 * 课程编辑器正文的基线。
 *
 * ## 为什么拍的是 [CourseEditorForm] 而不是 [CourseEditorDialog]
 *
 * `AlertDialog` 渲染在**独立窗口**里，`captureRoboImage` 抓的是 Compose 根节点，抓不到那个窗口
 * （同一个坑在 `SkinPreviewContent` / `AddTodoForm` 上踩过）。所以把正文抽成吃状态的组件，
 * 这里直接渲它 —— 五组字段排不排得下、四个周次选项会不会被挤成两行、
 * 动作胶囊在窄屏上够不够点，这些以前只能靠真机肉眼。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CourseEditorScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val editing = CourseEntity(
        id = 7,
        name = "工程会计与财务管理",
        credits = "3",
        teacher = "王焕海",
        room = "教三楼 305",
        dayOfWeek = 3,
        startSlot = 3,
        endSlot = 4,
        weekType = "all",
        weeks = "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16",
        shortName = "会计",
        note = "带计算器",
    )

    private fun render(state: CourseEditorFormState, scrolledTo: Int = 0) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                val colors = LocalPocketXiaoliColors.current
                // 按 AlertDialog 的观感摆：白色卡片 + 16dp 内边距，宽度受 dialog 约束而不是整屏。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(colors.surfaceCard, RoundedCornerShape(28.dp))
                        .padding(24.dp),
                ) {
                    CourseEditorForm(state = state, scrollState = ScrollState(scrolledTo))
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `编辑已有课程 五组字段齐`() {
        render(CourseEditorFormState(editing, currentWeek = 9, availableSlots = (1..12).toList()))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/390-course-editor-editing.png")
    }

    @Test
    fun `日程类型 按时间且仅本周`() {
        val agenda = editing.copy(
            id = 0,
            name = "班会",
            kind = CourseEntity.KIND_AGENDA,
            note = "本周三班会，交团建费",
            weekType = "thisWeek",
            startMinute = 10 * 60,
            endMinute = 11 * 60 + 40,
            room = "",
        )
        render(CourseEditorFormState(agenda, currentWeek = 9, availableSlots = emptyList()))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/391-course-editor-agenda.png")
    }

    @Test
    fun `新建课程 空表单`() {
        render(CourseEditorFormState(CourseEntity(name = ""), currentWeek = 1, availableSlots = emptyList()))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/392-course-editor-new.png")
    }

    /**
     * 地点与颜色在折叠线以下，前三张拍不到 —— 把滚动位置顶到底再拍一张。
     * `ScrollState` 会自己夹到最大值，所以这里不用算总高。
     */
    @Test
    fun `地点动作与调色板 滚到底`() {
        val state = CourseEditorFormState(editing, currentWeek = 9, availableSlots = (1..12).toList())
        state.colorTouched = true
        state.colorIndex = 3
        render(state, scrolledTo = 10_000)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/393-course-editor-bottom.png")
    }
}
