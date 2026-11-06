package com.pocket.xiaoli.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.core.exam.ExamSchedule
import com.pocket.xiaoli.data.entity.ExamEntity
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
 * 考试安排的基线（2026-10-31 补：这一页此前**一张截图都没有**）。
 *
 * 没有图就没有观感判据 —— 顶栏是不是和其它二级页一套、粘贴框在窄屏上挤不挤、
 * 预览行的勾选与确认按钮排不排得下，全都只能靠真机肉眼。抽了 [ExamScheduleContent]
 * 之后这里能直接渲三种状态。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ExamScheduleScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pending = listOf(
        PendingExam(
            schedule = ExamSchedule(
                courseName = "高等数学A",
                date = "2026-01-15",
                time = "08:30-10:30",
                location = "教三楼 305",
            ),
        ),
        PendingExam(
            schedule = ExamSchedule(courseName = "工程会计与财务管理", date = "1月18日", time = "14:00-16:00"),
            duplicate = true,
            selected = false,
        ),
    )

    /**
     * 基准时刻钉死在 2026-11-04：hero 的「最近一场 · 还有 N 天」与行内倒计时都是相对时间，
     * 不注入固定 today 的话，这些图明天就红（与 `ConversationRow(now = …)` 同一条纪律）。
     * 下面的日期都是相对这个基准挑的，覆盖三种色调：警示（2 天）、常规（72 天）、已完成（不显示）。
     */
    private val today = LocalDate.of(2026, 11, 4)

    private val existing = listOf(
        ExamEntity(id = 1, courseName = "大学英语III", date = "2026-11-06", time = "10:00-12:00", location = "主楼 201"),
        ExamEntity(id = 2, courseName = "体育（攀岩）", date = "2026-11-05", completed = true),
        ExamEntity(id = 3, courseName = "线性代数", date = "2027-01-15", time = "14:00-16:00", location = "教三 417"),
    )

    private fun render(state: ExamUiState) {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ExamScheduleContent(state = state, aiConfigured = false, today = today)
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `入口态 hero 引导与粘贴框`() {
        render(ExamUiState())
        composeRule.onRoot().captureRoboImage("src/test/screenshots/385-exam-entry.png")
    }

    @Test
    fun `预览态 解析结果与确认按钮`() {
        render(ExamUiState(noticeText = "1月15日 高数 教三305", pending = pending, parsed = true, scanned = 2))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/386-exam-preview.png")
    }

    @Test
    fun `已有考试 含已完成一行`() {
        render(ExamUiState(existing = existing))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/387-exam-existing.png")
    }

    @Test
    fun `解析不出来的空态`() {
        render(ExamUiState(noticeText = "本周三班会", parsed = true, scanned = 0))
        composeRule.onRoot().captureRoboImage("src/test/screenshots/389-exam-parse-empty.png")
    }
}
