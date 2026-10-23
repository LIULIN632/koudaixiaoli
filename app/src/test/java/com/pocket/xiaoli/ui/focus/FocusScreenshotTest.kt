package com.pocket.xiaoli.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.focus.FocusDayStat
import com.pocket.xiaoli.focus.FocusHeatCell
import com.pocket.xiaoli.focus.FocusPhase
import com.pocket.xiaoli.focus.FocusSegment
import com.pocket.xiaoli.focus.FocusStatsView
import com.pocket.xiaoli.focus.runningLabel
import com.pocket.xiaoli.ui.home.TodoRow
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 专注页的基线（2026-10-16 随番茄钟 P1 一起补，编号 150–154）。
 *
 * ## 为什么整页渲不出来
 *
 * `FocusScreen(container, onBack)` 要一个 `AppContainer`（ViewModel 工厂从它拿
 * `focusController`），Robolectric 里起不来。所以这里渲的是它的**子组件**
 * （[FocusPhaseHeader] / [FocusRing] / [FocusActions] / [FocusTodayCard]），
 * 按页面里的实际顺序与间距摆一遍 —— 它们保持 `internal` 就是为了这个
 * （教训见 `memory/02-feature-map.md`：整页渲不出来时，子组件必须 internal 才录得进基准图）。
 *
 * ⚠️ 这里是一份**页面摆放的影子**：改了 `FocusScreen` 的间距/顺序/文案，这张图不会自动跟着变，
 * 要一起改这个文件。反过来说，图红了正是提醒你"页面观感真的变了"。
 *
 * ## 五张图
 *
 * | 图 | 内容 |
 * |---|---|
 * | 150 | 空闲（Vivid/Rainbow）：25:00 满圈、圈点全空、「开始专注」，今日 0 个 |
 * | 151 | 专注中（Minimal/Pure）：13:24、环缩到 46%、「暂停」+ 跳过/放弃，今日 2 个 |
 * | 152 | 短休息已暂停（Vivid/Rainbow）：04:12、「继续」、圈点填 2/4，今日 3 个 |
 * | 153 | 专注中（Minimal/Pure **深色**）：同一屏在深色皮肤下的观感 |
 * | 154 | 首页待办行上的起钟入口（Vivid/Rainbow）：未完成行有 Timer 按钮，已完成行没有 |
 *
 * ## 统计区（2026-10-16 追加，155–158）
 *
 * | 图 | 内容 |
 * |---|---|
 * | 155 | 统计区有数据（Minimal/Pure）：7 根柱子（最高 3 个）+ 当日分段图 3 段 |
 * | 156 | 统计区空态（Vivid/Rainbow）：**只有一句话，不画空框** |
 * | 157 | 统计区（Minimal/Pure 深色）：柱与分段条在深色下的对比度 |
 * | 158 | 首页待办行上的番茄数（Vivid/Rainbow）：有番茄数的行多一枚小胶囊，0 的行没有 |
 *
 * ## 确定性纪律（`memory/04-ui-verification.md` §3）
 *
 * 时长、剩余、番茄数**全部写死常数**：不喂 `System.currentTimeMillis()`，也不喂
 * "今天"算出来的值 —— `FocusDayStat.date` 只做数据归口（柱状图只拿它的星期几做横轴标签，
 * 固定成 2026-10-10..16 就有固定的"六日一二三四五"），所以图不会随日子变红。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class FocusScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private enum class Kind { IDLE, RUNNING, BREAK_PAUSED, TODO_ROW, STATS, STATS_EMPTY, TODO_WITH_COUNT }

    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val appearance: AppearanceMode,
        val dark: Boolean,
        val kind: Kind,
    )

    private val cases = listOf(
        Case("150-focus-idle-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.IDLE),
        Case("151-focus-running-minimal", SkinStyle.MINIMAL, AppearanceMode.PURE, false, Kind.RUNNING),
        Case("152-focus-break-paused-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.BREAK_PAUSED),
        Case("153-focus-running-dark", SkinStyle.MINIMAL, AppearanceMode.PURE, true, Kind.RUNNING),
        Case("154-home-todo-focus-entry", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.TODO_ROW),
        Case("155-focus-stats-minimal", SkinStyle.MINIMAL, AppearanceMode.PURE, false, Kind.STATS),
        Case("156-focus-stats-empty-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.STATS_EMPTY),
        Case("157-focus-stats-dark", SkinStyle.MINIMAL, AppearanceMode.PURE, true, Kind.STATS),
        Case("158-home-todo-focus-count", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.TODO_WITH_COUNT),
        Case("159-focus-heatmap-vivid", SkinStyle.VIVID, AppearanceMode.RAINBOW, false, Kind.STATS),
    )

    /** 固定的一天，只为让 `FocusDayStat` 构造得出来；它不参与渲染。 */
    private val day = LocalDate.of(2026, 10, 16)

    private val focusPlannedMs = 25 * 60_000L

    /** 一周有数据的柱子：个数从 0 到 3，横轴必然是"六日一二三四五"。 */
    private val weekWithData = listOf(
        FocusDayStat(LocalDate.of(2026, 10, 10), completedPomodoros = 0, focusMs = 0L),
        FocusDayStat(LocalDate.of(2026, 10, 11), completedPomodoros = 1, focusMs = focusPlannedMs),
        FocusDayStat(LocalDate.of(2026, 10, 12), completedPomodoros = 0, focusMs = 0L),
        FocusDayStat(LocalDate.of(2026, 10, 13), completedPomodoros = 2, focusMs = 2 * focusPlannedMs),
        FocusDayStat(LocalDate.of(2026, 10, 14), completedPomodoros = 3, focusMs = 3 * focusPlannedMs),
        FocusDayStat(LocalDate.of(2026, 10, 15), completedPomodoros = 1, focusMs = focusPlannedMs),
        FocusDayStat(LocalDate.of(2026, 10, 16), completedPomodoros = 2, focusMs = 2 * focusPlannedMs),
    )

    /** 今天的 3 段：专注 25 分 → 短休 5 分 → 专注 25 分。 */
    private val todaySegments = listOf(
        FocusSegment(FocusPhase.FOCUS, 25 * 60_000L, 25f / 55f, completed = true),
        FocusSegment(FocusPhase.SHORT_BREAK, 5 * 60_000L, 5f / 55f, completed = true),
        FocusSegment(FocusPhase.FOCUS, 25 * 60_000L, 25f / 55f, completed = true),
    )

    /**
     * 12 周 × 7 天 = 84 格的热力图。
     *
     * 0～4 五档都出现（只画"全是 0"或"全是满格"看不出色阶对不对），
     * 最后两格是**未来**（`date = null` → 画透明，与"过去但没专注"的淡底色区分开）。
     */
    private val heatmapDense = run {
        val firstMonday = LocalDate.of(2026, 10, 12).minusWeeks(11)
        val levels = intArrayOf(0, 2, 0, 3, 4, 0, 1, 2, 0, 0, 3, 1, 4, 0, 0, 2)
        (0 until 84).map { index ->
            val date = firstMonday.plusDays(index.toLong())
            val future = date.isAfter(day)
            val level = if (future) 0 else levels[index % levels.size]
            FocusHeatCell(
                date = if (future) null else date,
                focusMs = level * 30L * 60_000L,
                level = level,
            )
        }
    }

    /** 有数据的一整份统计（概览 + 热力图 + 柱状图 + 分段图）。 */
    private val statsWithData = FocusStatsView(
        streakDays = 3,
        weekMs = 225 * 60_000L,
        monthMs = 640 * 60_000L,
        weekStats = weekWithData,
        todaySegments = todaySegments,
        heatmap = heatmapDense,
    )

    @Test
    fun `专注页截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = case.appearance,
                skinStyle = case.skin,
                darkTheme = case.dark,
            ) {
                val colors = LocalPocketXiaoliColors.current
                val skin = LocalSkin.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceBase),
                ) {
                    when (case.kind) {
                        // 与 FocusScreen 正文同一份摆放
                        Kind.IDLE -> FocusBody(
                            header = {
                                FocusPhaseHeader(
                                    phase = FocusPhase.FOCUS,
                                    completedInCycle = 0,
                                    totalRounds = 4,
                                    idle = true,
                                )
                            },
                            ring = {
                                FocusRing(
                                    remainingMs = focusPlannedMs,
                                    remainingFraction = 1f,
                                    statusText = "准备开始",
                                )
                            },
                            actions = {
                                FocusActions(
                                    active = false,
                                    paused = false,
                                    onStart = {},
                                    onTogglePause = {},
                                    onSkip = {},
                                    onAbandon = {},
                                )
                            },
                            stat = FocusDayStat(day, completedPomodoros = 0, focusMs = 0L),
                        )

                        Kind.RUNNING -> FocusBody(
                            header = {
                                FocusPhaseHeader(
                                    phase = FocusPhase.FOCUS,
                                    completedInCycle = 1,
                                    totalRounds = 4,
                                    idle = false,
                                )
                            },
                            ring = {
                                // 13:24 / 25:00 跑掉了一半多，环上也看得出来
                                FocusRing(
                                    remainingMs = 13 * 60_000L + 24_000L,
                                    remainingFraction = 1f - 804_000f / focusPlannedMs.toFloat(),
                                    statusText = FocusPhase.FOCUS.runningLabel,
                                )
                            },
                            actions = {
                                FocusActions(
                                    active = true,
                                    paused = false,
                                    onStart = {},
                                    onTogglePause = {},
                                    onSkip = {},
                                    onAbandon = {},
                                )
                            },
                            stat = FocusDayStat(day, completedPomodoros = 2, focusMs = 50 * 60_000L),
                            linkedTaskTitle = "写高数作业",
                        )

                        Kind.BREAK_PAUSED -> FocusBody(
                            header = {
                                // 休息段：圈点填"已经跑完的那几个"
                                FocusPhaseHeader(
                                    phase = FocusPhase.SHORT_BREAK,
                                    completedInCycle = 2,
                                    totalRounds = 4,
                                    idle = false,
                                )
                            },
                            ring = {
                                FocusRing(
                                    remainingMs = 4 * 60_000L + 12_000L,
                                    remainingFraction = 1f - 48_000f / (5 * 60_000f),
                                    statusText = "已暂停",
                                )
                            },
                            actions = {
                                FocusActions(
                                    active = true,
                                    paused = true,
                                    onStart = {},
                                    onTogglePause = {},
                                    onSkip = {},
                                    onAbandon = {},
                                )
                            },
                            stat = FocusDayStat(day, completedPomodoros = 3, focusMs = 75 * 60_000L),
                        )

                        Kind.TODO_ROW -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
                        ) {
                            // 未完成的自定义待办：右侧应多出一枚 Timer 按钮（在删除之前）
                            TodoRow(
                                title = "写高数作业",
                                detail = "",
                                dueDate = "",
                                completed = false,
                                onToggle = {},
                                onDelete = {},
                                onStartFocus = {},
                            )
                            // 已完成的：不给起钟入口（`TodoRow` 里 `!completed` 那道闸）
                            TodoRow(
                                title = "取快递",
                                detail = "",
                                dueDate = "",
                                completed = true,
                                onToggle = {},
                                onDelete = {},
                                onStartFocus = {},
                            )
                        }

                        // 统计区（有数据）：今日卡 → 概览 → 热力图 → 柱状图 → 分段图
                        Kind.STATS -> StatsBody(
                            stat = FocusDayStat(day, completedPomodoros = 2, focusMs = 50 * 60_000L),
                            stats = statsWithData,
                        )

                        Kind.STATS_EMPTY -> StatsBody(
                            stat = FocusDayStat(day, completedPomodoros = 0, focusMs = 0L),
                            stats = FocusStatsView(),
                        )

                        Kind.TODO_WITH_COUNT -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = skin.screenH, vertical = skin.spaceMd),
                            verticalArrangement = Arrangement.spacedBy(skin.sectionGap),
                        ) {
                            // 攒到过番茄的行：标题下面多一枚"3 个番茄"小胶囊
                            TodoRow(
                                title = "写高数作业",
                                detail = "",
                                dueDate = "",
                                completed = false,
                                onToggle = {},
                                onDelete = {},
                                onStartFocus = {},
                                focusCount = 3,
                            )
                            // 还没攒到的：**不显示**那一行（0 个番茄只是噪音）
                            TodoRow(
                                title = "取快递",
                                detail = "",
                                dueDate = "",
                                completed = false,
                                onToggle = {},
                                onDelete = {},
                                onStartFocus = {},
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

    /**
     * 统计区的摆放：今日卡 → 概览 → 热力图 → 柱状图 → 分段图（与 `FocusScreen` 同一份顺序）。
     *
     * 空态那张图会只剩"今日 0 个"加一句 caption —— 这正是要看的：**不画空框**。
     */
    @androidx.compose.runtime.Composable
    private fun StatsBody(
        stat: FocusDayStat,
        stats: FocusStatsView,
    ) {
        val skin = LocalSkin.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = skin.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(skin.spaceXl))
            FocusTodayCard(stat = stat)
            Spacer(Modifier.height(skin.spaceLg))
            FocusStatsSection(stats = stats)
            Spacer(Modifier.height(skin.spaceXxl))
        }
    }

    /** `FocusScreen` 正文的摆放：阶段头 → 环 → 操作 → 今日卡 → 关联待办 → 底部说明。 */
    @androidx.compose.runtime.Composable
    private fun FocusBody(
        header: @androidx.compose.runtime.Composable () -> Unit,
        ring: @androidx.compose.runtime.Composable () -> Unit,
        actions: @androidx.compose.runtime.Composable () -> Unit,
        stat: FocusDayStat,
        linkedTaskTitle: String? = null,
        stats: @androidx.compose.runtime.Composable () -> Unit = {},
    ) {
        val colors = LocalPocketXiaoliColors.current
        val skin = LocalSkin.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = skin.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(skin.spaceXl))
            header()
            Spacer(Modifier.height(skin.spaceXl))
            ring()
            Spacer(Modifier.height(skin.spaceXl))
            actions()
            Spacer(Modifier.height(skin.spaceXl))
            FocusTodayCard(stat = stat)
            if (linkedTaskTitle != null) {
                Spacer(Modifier.height(skin.spaceSm))
                Text(
                    "关联待办：$linkedTaskTitle",
                    style = PocketTypography.caption,
                    color = colors.textSecondary,
                )
            }
            // 统计区（P2 加进 FocusScreen 的那一段），顺序与页面一致：今日卡之后、底部说明之前
            Spacer(Modifier.height(skin.spaceLg))
            stats()
            // 这句长文案是最容易被挤断的地方，所以要进图（宽屏下应当完整换行，不是省略号）
            Spacer(Modifier.height(skin.spaceLg))
            Text(
                "锁屏后仍会计时；若发现不计时，请在系统设置里允许本应用后台运行、" +
                    "并允许「闹钟和提醒」。",
                style = PocketTypography.caption,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceXxl))
        }
    }
}
