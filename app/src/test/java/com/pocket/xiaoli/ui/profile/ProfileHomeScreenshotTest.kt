package com.pocket.xiaoli.ui.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.focus.FocusDayStat
import com.pocket.xiaoli.focus.FocusHeatCell
import com.pocket.xiaoli.focus.FocusPhase
import com.pocket.xiaoli.focus.FocusSegment
import com.pocket.xiaoli.focus.FocusStatsView
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.ui.theme.SkinStyle
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding

/**
 * 「我的」页**正文整页**的外观快照（身份卡 + 常用服务格 + 两张分组卡）。
 *
 * ## 为什么需要整页基线，而不是只有身份卡那组
 *
 * `ProfileIdentityCardScreenshotTest`（`60..63`）只看单张卡。而这一页真正的问题是
 * **两块 2×2 网格上下叠着**：
 *
 * 1. 身份卡里的「事实格」—— 学号 / 宿舍 / 学习平台 /（曾有的「陪伴」）
 * 2. 紧跟着的「常用服务格」（`ProfileStatsGrid`）—— 宿舍电费 / 专注计时 / 洗澡 / 快递
 *
 * 单看任何一块都挺好，**叠在一起才看得出重不重、该不该合并** ——
 * 而"好不好看"这类判断必须看整页。这也正是 `docs/UI验收手册.md` 说的
 * "先渲图再改"：把"两块网格是不是重复"从争论变成一张图。
 *
 * ⚠️ 2026-10 二次改版后，第 1 块**已经不存在**（身份卡缩成"我的信息 + 计数胶囊"两行，
 * 不再显示任何事实）。这组基线的职责随之变了：它现在证明**身份卡不会和下面的
 * 服务格抢戏**，以及"这一页只有一块网格"这条结论仍然成立 —— 别因为它看起来"没什么可测"
 * 就删掉，它是那一轮改版的判据。
 *
 * ⚠️ 2026-10 三次改版：四格里的「校园余额」换成「专注计时」，并在四格**正下方**插了一块
 * 专注统计（`FocusStatsSection`）。实测**整块正好落在首屏内**（`70` 里就能看到全部：
 * 磁贴 → 柱状图 → 分段图），被挤下去的是它下面那两张分组卡 ——
 * 「课表与学习」只剩第一行露头，「设置」整张出屏。
 * 所以 `73-profile-home-bottom` 滚到底补回那两张（原先由 `70/71/72` 直接覆盖）。
 * 空态（没跑过番茄）由 `71` 顺带覆盖 —— 那个用例连专注记录都没有。
 *
 * ⚠️ 页内用的是 `rememberCampusLazyListState`（跨页面记忆滚动位置），
 * 快照只渲染首屏可见部分 —— 这对"看版式"够用，且不受滚动记忆影响（测试里初始为 0）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class ProfileHomeScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 一个用例：文件名 + 是否已绑定 + 行高（来自「我的 → 列表密度」档位）。
     *
     * [rowPadding] 默认 14dp = **标准档**，与 `LocalProfileRowPadding` 的默认值一致。
     *
     * [scrollToBottom] 为 true 时先把列表滚到底再拍 ——
     * 加了专注统计块之后，「课表与学习」「设置」两张分组卡被推到首屏之外。
     */
    private data class Case(
        val name: String,
        val bound: Boolean,
        val rowPadding: Dp = 14.dp,
        val scrollToBottom: Boolean = false,
        /** 深色态：验证渐变卡面与实心图标块在暗底上仍分得清层次 */
        val dark: Boolean = false,
        /** 界面风格：极简档走的是"中性白卡 + 单色图标"分支，要单独看一眼 */
        val skin: SkinStyle = SkinStyle.VIVID,
    )

    private val cases = listOf(
        Case("70-profile-home-bound", bound = true),
        Case("71-profile-home-unbound", bound = false),
        // 深色态：四格是这一页唯一的大色块，暗底下最容易"糊成一片"。
        // ⚠️ 必须排在「滚到底」用例之前：列表滚动位置是跨用例记忆的（rememberCampusLazyListState）。
        Case("324-profile-home-dark", bound = true, dark = true),
        // 极简皮肤：四格退化成中性白卡 + 单色图标，不能出现"彩色渐变残留"
        Case("325-profile-home-minimal", bound = true, skin = SkinStyle.MINIMAL),
        // 「列表密度 = 宽松」档（18dp）。这一张是**这个设置真的接线了**的证据：
        // 与 `73`（同为滚到底、标准档）逐行对比即可看出行距变大，而改前两个档位渲出来一模一样。
        //
        // ⚠️ 这一张**必须滚到底**：加了专注统计块之后，首屏只剩到「课表与学习」的标题，
        // 分组卡里的行全在屏幕外 —— 不滚的话它与 `70` 会渲出**逐字节相同**的图
        //（实测过：150174 字节两张，靠字节数一样才发现证据已经失效）。
        Case("72-profile-home-loose", bound = true, rowPadding = 18.dp, scrollToBottom = true),
        // 滚到底：补上被专注统计块挤下去的两张分组卡（也是行高的标准档对照）。
        Case("73-profile-home-bottom", bound = true, scrollToBottom = true),
    )

    /**
     * 专注统计的固定数据，口径与 `FocusScreenshotTest` 的 `weekWithData` / `todaySegments`
     * **故意保持一致**：两处渲的是同一个 `FocusStatsSection`，固定数据一样才好逐张对比
     * "搬到另一页后有没有变样"。
     *
     * 合计：0+1+0+2+3+1+2 = 9 个番茄 / 225 分钟 = 3 小时 45 分。
     */
    private val weekStats = listOf(0, 1, 0, 2, 3, 1, 2).mapIndexed { index, count ->
        FocusDayStat(
            date = LocalDate.of(2026, 10, 10).plusDays(index.toLong()),
            completedPomodoros = count,
            focusMs = count * 25 * 60_000L,
        )
    }

    /** 今天：专注 25 + 休息 5 + 专注 25（合计 55 分钟，占比 25/55、5/55、25/55）。 */
    private val todaySegments = listOf(
        FocusSegment(FocusPhase.FOCUS, actualMs = 25 * 60_000L, fraction = 25f / 55f, completed = true),
        FocusSegment(FocusPhase.SHORT_BREAK, actualMs = 5 * 60_000L, fraction = 5f / 55f, completed = true),
        FocusSegment(FocusPhase.FOCUS, actualMs = 25 * 60_000L, fraction = 25f / 55f, completed = true),
    )

    /**
     * 12 周 × 7 天 = 84 格的热力图（与 `FocusScreenshotTest.heatmapDense` 同一份形状）。
     *
     * 0～4 五档都出现，最后两格是未来（`date = null` → 透明）。
     */
    private val heatmapDense = run {
        val firstMonday = LocalDate.of(2026, 10, 12).minusWeeks(11)
        val levels = intArrayOf(0, 2, 0, 3, 4, 0, 1, 2, 0, 0, 3, 1, 4, 0, 0, 2)
        (0 until 84).map { index ->
            val date = firstMonday.plusDays(index.toLong())
            val future = date.isAfter(LocalDate.of(2026, 10, 16))
            val level = if (future) 0 else levels[index % levels.size]
            FocusHeatCell(
                date = if (future) null else date,
                focusMs = level * 30L * 60_000L,
                level = level,
            )
        }
    }

    /** 有数据的一整份统计（概览 + 热力图 + 柱状图 + 分段图）。 */
    private val focusStats = FocusStatsView(
        streakDays = 3,
        weekMs = 225 * 60_000L,
        monthMs = 640 * 60_000L,
        weekStats = weekStats,
        todaySegments = todaySegments,
        heatmap = heatmapDense,
    )

    @Test
    fun `我的页正文整页截图`() {
        val current = mutableStateOf(cases.first())
        composeRule.setContent {
            val case = current.value
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = case.skin,
                darkTheme = case.dark,
            ) {
                // 行高由「外观 → 我的 → 列表密度」决定（见 TabAppearanceStore）。
                // 这里显式 provide：不 provide 时用的是该 CompositionLocal 的默认
                //（= 标准档 14dp），而 `72` 那张要证明的正是**换成宽松档真的会变**
                // —— 这个设置在 2026-10 之前是死的（声明了没有消费者）。
                CompositionLocalProvider(
                    LocalProfileRowPadding provides case.rowPadding
                ) {
                    ProfileHomeContent(
                        // ⚠️ 只传计数：这一页**收不到**昵称 / 学号 / 宿舍（见 ProfilePrivacyTest）。
                        // `bound` 那个布尔在这组基线里代表"三项都绑好了 / 一项都没绑"。
                        boundCount = if (case.bound) 3 else 0,
                        onOpenBinding = {},
                        electricityBalance = if (case.bound) "23.50" else null,
                        electricityLoading = false,
                        onOpenElectricity = {},
                        // 未绑定那个用例连专注记录也没有 —— 顺手当"统计块空态"的基线。
                        focusHint = if (case.bound) "今天 50 分钟" else "今天还没开始",
                        onOpenFocus = {},
                        focusStats = if (case.bound) focusStats else FocusStatsView(),
                        showerHint = if (case.bound) "¥16.76" else "登录后查看余额",
                        onOpenShower = {},
                        onOpenExpress = {},
                        onSchemeManager = {},
                        onImportMenu = {},
                        onShareMenu = {},
                        onSettings = {},
                    )
                }
            }
        }

        cases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            if (case.scrollToBottom) {
                // 滚到最后一条（设置那条的副标题，全页唯一一段字，不会与分组标题「设置」撞名）。
                // LazyColumn 不会组合不可见项，所以必须先滚过去再拍。
                composeRule.onNode(hasScrollAction())
                    .performScrollToNode(hasText("课表、外观、服务功能、关于与更新"))
                composeRule.waitForIdle()
            }
            composeRule.onRoot().captureRoboImage("src/test/screenshots/${case.name}.png")
        }
    }
}
