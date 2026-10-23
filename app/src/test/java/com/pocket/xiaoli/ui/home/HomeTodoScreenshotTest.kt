package com.pocket.xiaoli.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 首页**待办区**的基线（2026-10 补，随"一个入口 + 右下角 FAB"那次改造）。
 *
 * ## 为什么必须补这三张
 *
 * 这次改造把待办区整个重做了：两个新增入口合成一个、`TaskRow` 与 `HomeworkRow`
 * 合并成 [TodoRow]、按钮从列表里搬到右下角悬浮。这些**全是像素级的变化**，
 * 而改造前这里**一张基线图都没有** —— 编译过、单测过，只有用户看得见对不对。
 * 同样的亏这个项目已经吃过一次（见 `HomeCourseCardScreenshotTest` 的 KDoc）。
 *
 * ## 三张图
 *
 * | 图 | 内容 |
 * |---|---|
 * | 113 | 有内容：分区标题 + 考试行 + 带截止日期的待办 + 纯待办 + 右下角 FAB |
 * | 114 | 空态：`EmptyTodoCard` 的引导语必须指向**右下角**（入口搬家了） |
 * | 115 | 「添加待办」弹窗正文（`AlertDialog` 在独立窗口里，整窗抓不到，见下） |
 *
 * ## ⚠️ 两条确定性纪律（`memory/04-ui-verification.md` §3）
 *
 * 1. **不用相对时间**：`isOverdue` 是拿 `LocalDate.now()` 比的，所以这里刻意**不喂**
 *    "已过期的截止日期" —— 那种图会随日子变红。截止日期用 `6月15日` 这种
 *    `isOverdue` 解析不了的写法（教务导入的作业正是这种），恒为"未过期"，图才稳。
 * 2. **弹窗正文单独渲**：`AlertDialog` 渲染在独立窗口，`captureRoboImage` 抓的是
 *    Compose 根节点、**抓不到那个窗口**。所以渲的是抽出来的 [AddTodoForm]，
 *    并按弹窗正文的实际可用宽度（288dp）约束它 —— 宽度不对，截断就看不出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HomeTodoScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private enum class Kind { LIST, EMPTY, DIALOG }

    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val appearance: AppearanceMode,
        val kind: Kind,
    )

    private val cases = listOf(
        Case("113-home-todo-list-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, Kind.LIST),
        Case("114-home-todo-empty-minimal", SkinStyle.MINIMAL, AppearanceMode.PURE, Kind.EMPTY),
        Case("115-home-add-todo-dialog", SkinStyle.VIVID, AppearanceMode.RAINBOW, Kind.DIALOG),
    )

    private val exam = ExamEntity(
        id = 1,
        courseName = "示例课程",
        // ⚠️ 日期刻意留空：填 `1月15日` 这种可解析的写法会渲染出「还有 N 天」的倒计时，
        // 而 N 每天都在变 —— 那张基准图明天就红。考试行只是这一屏的**背景**，
        // 倒计时本身另有 `ExamCountdownTest` 守。
        date = "",
        time = "09:00-11:00",
        location = "文二教101",
        seat = "12",
        schemeId = 0,
    )

    @Test
    fun `首页待办区截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = case.appearance,
                skinStyle = case.skin,
                darkTheme = false,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase),
                ) {
                    when (case.kind) {
                        Kind.LIST -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
                        ) {
                            SectionHeader(title = "待办")
                            ExamRow(exam = exam, onToggle = {}, onDelete = {})
                            // 带截止日期的待办（= 教务/学习平台导入的作业走的那条）
                            TodoRow(
                                title = "P45 习题 3-5",
                                detail = "示例课程",
                                dueDate = "6月15日",
                                completed = false,
                                onToggle = {},
                                onDelete = {},
                            )
                            // 纯自定义待办：没有课程、没有截止日期 —— 副标题与第三行都不该出现
                            TodoRow(
                                title = "取快递",
                                detail = "",
                                dueDate = "",
                                completed = false,
                                onToggle = {},
                                onDelete = {},
                            )
                        }

                        Kind.EMPTY -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
                        ) {
                            SectionHeader(title = "待办")
                            EmptyTodoCard()
                        }

                        Kind.DIALOG -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(skin.spaceMd),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            val formState = remember {
                                AddTodoFormState(listOf("高等数学", "大学英语")).apply {
                                    content = "交实验报告"
                                    course = "高等数学"
                                    dueDate = "下周一"
                                }
                            }
                            AddTodoForm(
                                state = formState,
                                modifier = Modifier
                                    .width(288.dp)
                                    .padding(vertical = skin.spaceMd),
                            )
                        }
                    }

                    if (case.kind != Kind.DIALOG) {
                        HomeAddTodoFab(
                            onClick = {},
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
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
