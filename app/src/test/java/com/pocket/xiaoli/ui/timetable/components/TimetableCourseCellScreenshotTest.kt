package com.pocket.xiaoli.ui.timetable.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.pocket.xiaoli.ui.theme.courseBgColor
import com.pocket.xiaoli.ui.theme.courseTextColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 课程块里的**课程名**：长课名到底显示成什么样。
 *
 * ## 为什么补这组（2026-10 的 P0）
 *
 * 用户原话："「工程会计与财」「建筑设备与智」「形势与政」这种截断非常影响识别。"
 * 改造前是「原字号 + `maxLines = 1` + `Ellipsis`」，于是：
 * - 9 个字的长课名被砍到 5 个字，而**开头相同**的两门课（工程会计 / 工程管理）就分不出来了；
 * - 这种问题**只有把图渲出来才发现得了** —— 语义树里那行文字是完整的课文名，
 *   任何断言都查不出"它被截成了什么样"。
 *
 * 现在两条对策叠起来（用户选的 ② + ①）：
 * 1. **简称**（`CourseEntity.shortName`，内置表 / 规则 / 用户手改）→ 一个字都不截；
 * 2. 放不下就**两行 + 小一档字号**，而不是砍掉后半截。
 *
 * ⚠️ 三张图刻意用**同一个格子宽度**（真实手机上周一到周日那一列的宽度量级），
 * 否则"放得下"就成了假的。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class TimetableCourseCellScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private data class Case(
        val name: String,
        val course: CourseEntity,
        val slots: Int,
        /** 竖排版式（宽格子）还是横向紧凑版式（真实课表格子） */
        val vertical: Boolean,
    )

    private val cases = listOf(
        // ① + ②：有简称 → 一行放下，一个字都不截
        Case(
            name = "100-course-cell-alias",
            course = CourseEntity(name = "工程会计与财务管理", shortName = "工程会计", teacher = "王老师", room = "3-401"),
            slots = 2,
            vertical = false,
        ),
        // ①：**推不出简称**的长课名 → 两行放完整（内置表没收录、规则也返回 null 的那种）。
        // ⚠️ 别拿「建筑设备与智能建造」来演这一幕：它**在内置表里**（会显示成「建筑设备」一行），
        // 拿它当"只有两行兜底"的例子是不诚实的 —— 基线要演最坏情况。
        Case(
            name = "101-course-cell-two-lines",
            course = CourseEntity(name = "土木工程施工", teacher = "李老师", room = "5-208"),
            slots = 3,
            vertical = false,
        ),
        // 只有 5 个字、不该被砍成「形势」的那门课（用户举例的第三条）
        Case(
            name = "102-course-cell-single-slot",
            course = CourseEntity(name = "形势与政策", teacher = "赵老师", room = "2-105"),
            slots = 1,
            vertical = false,
        ),
        // ③：日程角标 —— 竖排布局里课名行尾挂一个「日」，验证它没把课名挤成一条缝
        Case(
            name = "103-course-cell-agenda",
            course = CourseEntity(
                name = "论文讨论",
                teacher = "孙老师",
                room = "图书馆",
                kind = CourseEntity.KIND_AGENDA,
                note = "带上实验记录",
            ),
            slots = 2,
            vertical = false,
        ),
        // ④：同一件事在**竖排版式**（宽格子）下的样子：角标字号固定 10sp，不随格子缩放
        Case(
            name = "104-course-cell-agenda-vertical",
            course = CourseEntity(
                name = "论文讨论",
                teacher = "孙老师",
                room = "图书馆讨论间",
                kind = CourseEntity.KIND_AGENDA,
            ),
            slots = 2,
            vertical = true,
        ),
    )

    @Test
    fun `课程块的课名显示截图`() {
        // ⚠️ `setContent` 一个测试里只能调一次（再调会抛 "has already set content"），
        // 所以三个用例用同一个 state 驱动，逐个切换再抓图。
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
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
                    // 真实课表格子的量级：一列约 52dp 宽，一节约 56dp 高
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height((56 * case.slots).dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(courseBgColor(colorIndex = 0, isDark = false)),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                            CourseCardContent(
                                course = case.course,
                                // ⚠️ 底色与字色必须**成对**取自同一套调色板函数（生产代码就是这么配的）：
                                // 我第一版手写 Color.White 配浅粉底，渲出来白字压浅粉、根本看不清 ——
                                // 那样这张基线就失去了"验可读性"的意义。
                                textColor = courseTextColor(colorIndex = 0, isDark = false),
                                isNonCurrent = false,
                                showTeacher = true,
                                showRoom = true,
                                showCampus = false,
                                showOddEven = false,
                                textCentered = false,
                                gridTextSize = 12,
                                verticalLayout = case.vertical,
                                // ⚠️ 必须把块高传进去：单节块（slots = 1）+ 长课名时会主动省掉教师行、
                                // 保住教室 —— 不传的话这张基线渲的是生产代码里**不存在**的布局。
                                slots = case.slots,
                            )
                        }
                    }
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
