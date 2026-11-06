package com.pocket.xiaoli.ui.promo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.core.weather.WeatherCurrent
import com.pocket.xiaoli.core.weather.WeatherSnapshot
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.data.electricity.DormElectricityBalance
import com.pocket.xiaoli.ui.electricity.ElectricityContent
import com.pocket.xiaoli.ui.electricity.ElectricityViewState
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.home.HomeContent
import com.pocket.xiaoli.ui.home.HomeCourse
import com.pocket.xiaoli.ui.home.HomeModule
import com.pocket.xiaoli.ui.home.HomeUiState
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.ui.timetable.components.PeriodGrid
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 宣传素材用的**演示数据**整屏渲染。
 *
 * ## 为什么单独一个测试类
 *
 * `app/src/test/screenshots/` 那 290+ 张是**回归基线**，里面的数字（学号 `BS1050602`、
 * `10 号楼 602`、带姓名前缀的同学标签）当基线没问题，**拿去发短视频就是泄露**。
 * 宣传片需要的恰好是「同一套真 UI + 一组虚构数据」，于是这里按 [DEMO] 重新渲一遍，
 * 产物直接写进 `promo/short/assets/demo/`，不参与基线门禁。
 *
 * ## 稳定性
 *
 * 与基线不同，这里**不要求逐像素可复现**（它不进 verify），所以时钟用固定值只是为了让
 * 「下一节课」「第 N 周」这类文案不自相矛盾，不是为了回放。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class PromoCaptureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /** 相对模块目录（`app/`）解析，落到仓库里的 promo 目录。 */
        const val OUT = "../promo/short/assets/demo"

        /** 演示用「现在」：第 10 周的周三傍晚。 */
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 21, 18, 24)
    }

    // ───────────────────────── 演示数据 ─────────────────────────
    // 全部虚构：学号规则、楼栋门牌、老师姓名、同学标签都不指向任何真人。

    private fun course(
        id: Long,
        name: String,
        short: String,
        teacher: String,
        room: String,
        day: Int,
        start: Int,
        end: Int,
    ) = CourseEntity(
        id = id,
        name = name,
        shortName = short,
        teacher = teacher,
        room = room,
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        credits = "2.0",
    )

    private val courses = listOf(
        course(1, "建设法规", "建设", "王立群", "2-101", 1, 1, 2),
        course(2, "工程经济", "工经", "陈明", "3-402", 1, 7, 8),
        course(3, "大学英语", "英语", "刘洋", "1-305", 2, 3, 4),
        course(4, "高等数学", "高数", "赵启明", "2-208", 3, 1, 2),
        course(5, "建筑制图", "制图", "孙倩", "3-301", 3, 7, 8),
        course(6, "房屋建筑学", "房建", "李维", "2-115", 4, 3, 4),
        course(7, "形势与政策", "形势", "周涛", "1-108", 4, 7, 8),
        course(8, "体育", "体育", "郑强", "体育馆", 5, 3, 4),
    )

    private val timeSlots = listOf(
        TimeSlotEntity(1, "08:00", "08:45"),
        TimeSlotEntity(2, "08:55", "09:40"),
        TimeSlotEntity(3, "10:00", "10:45"),
        TimeSlotEntity(4, "10:55", "11:40"),
        TimeSlotEntity(5, "14:00", "14:45"),
        TimeSlotEntity(6, "14:55", "15:40"),
        TimeSlotEntity(7, "16:00", "16:45"),
        TimeSlotEntity(8, "16:55", "17:40"),
        TimeSlotEntity(9, "18:30", "19:15"),
        TimeSlotEntity(10, "19:25", "20:10"),
        TimeSlotEntity(11, "20:20", "21:05"),
        TimeSlotEntity(12, "21:15", "22:00"),
    )

    private val nextCourse = HomeCourse(
        course = courses[0],
        startTime = "18:30",
        endTime = "20:10",
        dayOffset = 0,
    )

    private val exam = ExamEntity(
        id = 1,
        courseName = "房屋建筑学",
        date = "",
        time = "09:00-11:00",
        location = "2-115",
        seat = "12",
    )

    private val homework = HomeworkEntity(
        id = 2,
        courseName = "工程经济",
        content = "P45 习题 3-5",
        // 演示图每次运行重生成：给一个"后天截止"，首页那行永远是琥珀色的
        // 「还剩 2 天」，比固定日期（某天之后变成"已过期 N 天"）更适合当门面
        dueDate = java.time.LocalDate.now().plusDays(2).toString(),
    )

    private val task = TaskEntity(id = 3, name = "取快递")

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

    // ───────────────────────── 截图 ─────────────────────────

    @Test
    fun `演示首页`() {
        val state = HomeUiState(
            week = 10,
            nextCourse = nextCourse,
            pendingExams = listOf(exam),
            pendingHomework = listOf(homework),
            pendingTasks = listOf(task),
            focusCounts = mapOf(3L to 2),
            hasTimetable = true,
            semesterCalibrated = true,
        )
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                CompositionLocalProvider(LocalBottomBarInset provides 88.dp) {
                    HomeContent(
                        state = state,
                        hasUnread = true,
                        weather = weather,
                        modules = HomeModule.entries,
                        hiddenModules = emptySet(),
                        fixedNow = NOW,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$OUT/home.png")
    }

    @Test
    fun `演示课表整页`() {
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                val colors = LocalPocketXiaoliColors.current
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase)
                        .statusBarsPadding(),
                ) {
                    // 只画宣传片要的那一行周次头：真页面这里是可点的周次条，
                    // 对宣传片没有信息量，画一条等价文本即可（网格本身仍是 PeriodGrid 真组件）。
                    androidx.compose.material3.Text(
                        text = "第 10 周  ·  2026 秋季学期",
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 14.dp),
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    // PeriodGrid 把可用宽度固定 7 等分（含周末），表头必须用同一套算法才对齐。
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(32.dp))
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                            androidx.compose.material3.Text(
                                text = "周$d",
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 11.sp,
                                color = if (d in listOf("一", "二", "三", "四", "五")) colors.textPrimary else colors.textSecondary,
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        PeriodGrid(
                            timeSlots = timeSlots,
                            courses = courses,
                            currentWeek = 10,
                            isCurrentWeek = true,
                            showTeacher = false,
                            showRoom = true,
                            showSlotTime = true,
                            textCentered = true,
                            gridHeightProvider = { 58 },
                            cornerRadius = 14,
                            gridOpacityProvider = { 1f },
                            gridTextSize = 10,
                            showOddEven = false,
                            onCourseClick = {},
                            onEmptyCellClick = { _, _ -> },
                            onCourseMoved = { _, _, _ -> },
                            scrollState = rememberScrollState(),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$OUT/timetable.png")
    }

    @Test
    fun `演示宿舍电费`() {
        val fetchedAt = NOW.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        val state = ElectricityViewState(
            supported = true,
            unbound = false,
            campusName = "南校区",
            dormLabel = "6 号楼 305",
            dormCode = "BS06S0305",
            detail = DormElectricityBalance(
                balance = "42.50",
                accountNo = "BS06S0305",
                address = "南校6号楼305",
                accountId = "demo-1",
                itemId = "demo-1",
                matchedByCode = true,
                fetchedAtMillis = fetchedAt,
                fromCache = false,
            ),
            loading = false,
            error = null,
            weChatInstalled = true,
            updatedText = "10-21 18:24 · 实时",
        )
        composeRule.setContent {
            PocketXiaoliTheme(appearanceMode = AppearanceMode.RAINBOW, skinStyle = SkinStyle.VIVID) {
                ElectricityContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onRefresh = {},
                    onPayment = {},
                    onCopyLink = {},
                    onOpenPortal = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$OUT/electricity.png")
    }
}
