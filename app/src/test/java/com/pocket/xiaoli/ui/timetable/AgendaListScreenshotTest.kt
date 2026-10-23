package com.pocket.xiaoli.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
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
import java.time.LocalDate

/**
 * 日程视图（课表页第三种读法）的基准图。
 *
 * ## 这组图在验什么
 * 1. **分钟级时间优先**：id=2 那行没填节次时间、只填了 10:00–11:00，时间列必须显示 10:00-11:00，
 *    而不是回退成第 3 节的 09:40。
 * 2. **日程角标**：`kind = KIND_AGENDA` 的行带「日程」二字，普通课程不带。
 * 3. **备注**：note 作为第三行小字出现，且长备注最多两行。
 * 4. 同一门课跨节次（id=1 占 1–2 节）在日程里是**一行**，不是两行。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class AgendaListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val slots = listOf(
        TimeSlotEntity(slot = 1, startTime = "08:00", endTime = "08:45"),
        TimeSlotEntity(slot = 2, startTime = "08:55", endTime = "09:40"),
        TimeSlotEntity(slot = 3, startTime = "09:40", endTime = "10:25"),
        TimeSlotEntity(slot = 4, startTime = "10:35", endTime = "11:20"),
        TimeSlotEntity(slot = 5, startTime = "11:30", endTime = "12:15"),
        TimeSlotEntity(slot = 6, startTime = "14:00", endTime = "14:45"),
        TimeSlotEntity(slot = 7, startTime = "14:55", endTime = "15:40"),
    )

    private val courses = listOf(
        CourseEntity(
            id = 1,
            name = "高等数学（上）",
            teacher = "张老师",
            room = "A301",
            dayOfWeek = 1,
            startSlot = 1,
            endSlot = 2,
            weeks = "1-20",
        ),
        CourseEntity(
            id = 2,
            name = "课题讨论会",
            room = "图书馆讨论间",
            dayOfWeek = 1,
            startSlot = 3,
            endSlot = 3,
            weeks = "1-20",
            kind = CourseEntity.KIND_AGENDA,
            note = "与导师确认论文选题，带上上周的实验记录和两份参考文献",
            startMinute = 600,
            endMinute = 660,
        ),
        CourseEntity(
            id = 3,
            name = "体育（羽毛球）",
            teacher = "李老师",
            room = "体育馆",
            dayOfWeek = 3,
            startSlot = 6,
            endSlot = 7,
            weeks = "1-20",
        ),
    )

    @Test
    fun agendaList() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                val colors = LocalPocketXiaoliColors.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    AgendaList(
                        courses = courses,
                        timeSlots = slots,
                        startOfWeek = LocalDate.of(2026, 10, 26), // 周一
                        today = LocalDate.of(2026, 10, 26), // 固定「今天」，覆盖「今天」胶囊
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/agenda_list.png")
    }
}
