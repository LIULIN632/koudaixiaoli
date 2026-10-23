package com.pocket.xiaoli.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.calendar.CalendarAsset
import com.pocket.xiaoli.data.calendar.CalendarPhase
import com.pocket.xiaoli.data.calendar.parseCalendarAsset
import com.pocket.xiaoli.data.holiday.HolidayDay
import com.pocket.xiaoli.ui.calendar.components.CalendarHeaderCard
import com.pocket.xiaoli.ui.calendar.components.EventRow
import com.pocket.xiaoli.ui.calendar.components.MonthEventList
import com.pocket.xiaoli.ui.calendar.components.MonthGrid
import com.pocket.xiaoli.ui.calendar.components.SelectedDayCard
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.util.CalendarUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * 校历页三块的 **JVM 快照基线**。
 *
 * ## 为什么渲的是三块，不是整页
 *
 * 整页 `CampusCalendarScreen` 要 `AppContainer`，而 `CampusCalendarContent` 外面套着
 * `verticalScroll` —— 截出来只有首屏，下半段永远没被看见。所以这里打的是三块**纯组件**：
 * 头部卡 / 月历网格 / 当月事件列表（含选中态卡）。三块的参数都由纯函数算好后传进来，
 * 组件自己不读资产，因此这里也必须**显式喂真实资产**，不能喂手写的假数据。
 *
 * ## ⚠️ 时刻是钉死的
 *
 * `today = 2026-10-16`。整页里没有任何 `LocalDate.now()`，也没有无限动画 ——
 * 这三张图必须每次跑都逐像素一样（本项目对快照不确定性的三条纪律之一）。
 *
 * ## 刻意选的边界
 *
 * - **2026 年 10 月只有一件事**（国庆节，来自在线源；校历资产里 10 月是空的）。
 *   这是最常见也最容易做坏的状态：列表不能只剩一大片空白。
 * - **2027 年 2 月**守的是跨月假期：寒假 1/18–2/28 用**交集**取，2 月的列表里必须出现。
 * - 头部卡带「按校历校准」提示带（不一致态），那是这一块里唯一会多长一行的情况。
 *
 * 产出落在随仓库提交的 `app/src/test/screenshots/`，新增编号从 144 起
 * （141/142/143 已被 `AcademicQueryScreenshotTest` 占用）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class CampusCalendarScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 16)

    @Test
    fun `校历三块截图`() {
        val asset = requireNotNull(loadAsset()) { "assets/calendar/ytlg.json 没找到或解析失败" }
        val holidays = sampleHolidays()
        var shot by mutableStateOf(0)

        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    when (shot) {
                        0 -> HeaderSample(asset)
                        1 -> GridSample(asset, holidays)
                        else -> ListSample(asset)
                    }
                }
            }
        }

        listOf(
            "144-calendar-header" to 0,
            "145-calendar-month-grid" to 1,
            "146-calendar-event-list" to 2,
        ).forEach { (name, index) ->
            composeRule.runOnIdle { shot = index }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        }
    }

    @Composable
    private fun HeaderSample(asset: CalendarAsset) {
        val skin = LocalSkin.current
        Column(Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceLg)) {
            CalendarHeaderCard(
                // 眉题与阶段都走真实计算，不手写字符串 —— 手写的话「第几周」这条口径
                // 就没被这三张图覆盖，将来算错了图还是绿的。
                eyebrow = "2026-2027 学年 · 第 ${CalendarUtils.legalWeekOf(asset.terms.first(), today)} 周",
                headline = "今天 ${CalendarFormat.monthDayWeekday(today)}",
                phase = CalendarUtils.calendarPhase(asset, today),
                // 非 null = 显示「按校历校准」那条提示带（与本地设置不一致态）
                onCalibrate = {},
            )
        }
    }

    @Composable
    private fun GridSample(asset: CalendarAsset, holidays: List<HolidayDay>) {
        val skin = LocalSkin.current
        Column(Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceLg)) {
            MonthGrid(
                cells = CalendarUtils.monthGrid(YearMonth.of(2026, 10)),
                today = today,
                selected = today,
                markOf = { date -> CalendarUtils.dayMark(asset, holidays, date) },
                onSelect = {},
            )
        }
    }

    @Composable
    private fun ListSample(asset: CalendarAsset) {
        val skin = LocalSkin.current
        Column(
            modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceLg),
            verticalArrangement = Arrangement.spacedBy(skin.spaceLg),
        ) {
            // 2026 年 10 月：校历资产里一件事都没有，格子上那点国庆小字来自在线源，
            // 所以这里 **不喂 holidays** —— 列表只该有一件事（国庆节）没有，
            // 它就直接进空态。空态本身就是这条基线要守的东西。
            MonthEventList(
                yearMonthLabel = "2026年10月",
                rows = CalendarUtils.eventsInMonth(asset, YearMonth.of(2026, 10))
                    .map { EventRow(event = it, isToday = it.covers(today)) },
            )
            // 2027 年 2 月：寒假跨月 + 春节 + 报到注册，三条
            MonthEventList(
                yearMonthLabel = "2027年2月",
                rows = CalendarUtils.eventsInMonth(asset, YearMonth.of(2027, 2))
                    .map { EventRow(event = it, isToday = false) },
            )
            SelectedDayCard(
                date = today,
                weekLabel = "第 ${CalendarUtils.legalWeekOf(asset.terms.first(), today)} 周",
                eventTitles = CalendarUtils.eventsOn(asset, today).map { it.title },
                onOpenTimetable = {},
            )
        }
    }

    /**
     * 真资产，走跟 `CalendarAssetTest` 同一条「三候选路径」找文件的路子。
     *
     * 三个候选一个都不能少：Gradle 跑单测时的工作目录随 task 变
     * （`src/main` / `app/src/main` / `../app/src/main` 都出现过）。
     */
    private fun loadAsset(): CalendarAsset? {
        val relative = "assets/calendar/ytlg.json"
        val candidates = listOf(
            File("src/main/$relative"),
            File("app/src/main/$relative"),
            File("../app/src/main/$relative"),
        )
        val file = candidates.firstOrNull { it.isFile } ?: return null
        return parseCalendarAsset(file.readText())
    }

    /**
     * 在线源（holiday-cn）那几天的形状：**一个放假日一条**，调休上班日 `isOffDay = false`。
     *
     * 这里手写而不是去读缓存：校历页的格子小字是「校历 + 在线源」两边合的，
     * 而在线源在单测里没有网络。数值取 2026 年国庆那一段的**真实形状**
     *（10/1–10/7 放假、10/10 调休上班），只为让格子上的三种标记都出现一次。
     */
    private fun sampleHolidays(): List<HolidayDay> = buildList {
        (1..7).forEach { day ->
            add(HolidayDay(name = "国庆节", date = "2026-10-0$day", isOffDay = true))
        }
        add(HolidayDay(name = "国庆节", date = "2026-10-10", isOffDay = false))
    }
}
