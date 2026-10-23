package com.pocket.xiaoli.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.ui.profile.components.SkinPreviewContent
import com.pocket.xiaoli.ui.profile.components.SkinPreviewSample
import com.pocket.xiaoli.ui.treasurebox.BadgeTone
import com.pocket.xiaoli.ui.treasurebox.QuickServiceRow
import com.pocket.xiaoli.ui.treasurebox.ServiceCard
import com.pocket.xiaoli.ui.treasurebox.ServiceEntry
import com.pocket.xiaoli.ui.treasurebox.ServiceSearchEmptyState
import com.pocket.xiaoli.ui.treasurebox.ServiceSectionHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 基准图目录（相对 Gradle 测试工作目录 = 模块目录 `app/`）。 */
private const val SHOT_DIR = "src/test/screenshots"

/**
 * Minimal / Vivid 双主题的 **JVM 快照测试** —— 不需要模拟器、不需要真机、不需要点击。
 *
 * ## 为什么必须有它
 *
 * 真机验收在这台设备上有两处**结构性到不了的地方**：
 * 1. **只能靠点击打开的界面**（如设置页的皮肤预览弹窗）—— 而 MIUI 禁止 ADB 注入
 *    输入事件（`INJECT_EVENTS`），`input tap` / `swipe` 全部被拒；
 * 2. **回归成本** —— 每改一次皮肤数据都要重新打包、推机、手动点装、逐屏截图。
 *
 * 快照测试把这两件事都变成 `./gradlew` 里的一条命令。方案出处：
 * `docs/优化与美化清单-2026-10.md` 的 P4-3，Roborazzi 被标为 ⭐ 最适合本项目
 *（无 instrumentation 环境）。运维细节（含 `roborazzi.test.record` 那个静默 no-op 的坑）
 * 见 `docs/UI验收手册.md`。
 *
 * ## 它渲染的是**真实组件**，不是仿制品
 *
 * - `SkinPreviewSample` / `SkinPreviewContent` —— 与弹窗里**同一份代码**
 * - `ServiceCard` / `QuickServiceRow` —— 与服务页**同一份代码**，且**复现真实的宽度约束**
 *
 * 外层一律走真实的 `PocketXiaoliTheme` 入口。若在测试里另写一份"样例卡片"，
 * 就又回到了"预览与实际漂移"的老问题 —— `DesignTokens.kt` 的 `LocalBaseColors` KDoc 的 KDoc
 * 记录了本项目为此栽过的跟头。
 *
 * ## 产出与门禁
 *
 * 基准图落在 **`app/src/test/screenshots/`** —— 刻意**不放在 `build/` 下**：
 * 那是随仓库提交的**基准图（golden）**，而 `build/` 会被 `clean` 清掉。
 *
 * - **默认**（`.\gradlew :app:testDebugUnitTest`）走 **verify 模式**：与基准图逐像素比对，
 *   不一致即**测试失败** —— 这是门禁。
 * - 观感**有意**变更后：`.\gradlew :app:testDebugUnitTest -PrecordScreenshots` 重录基准图，
 *   并把变更一起提交（否则门禁会一直红）。
 *
 * 录制与校验**读写同一份** `src/test/screenshots/`，不再另存副本 ——
 * 避免出现"两份图不一致、不知道该信哪份"。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SkinScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 一个渲染用例：名字 + 皮肤 + 用色强度 + 明暗。 */
    private data class Case(
        val name: String,
        val skin: SkinStyle,
        val appearance: AppearanceMode,
        val dark: Boolean = false,
    )

    private val quadrantCases = listOf(
        Case("01-vivid-rainbow", SkinStyle.VIVID, AppearanceMode.RAINBOW),
        Case("02-minimal-rainbow", SkinStyle.MINIMAL, AppearanceMode.RAINBOW),
        Case("03-vivid-pure", SkinStyle.VIVID, AppearanceMode.PURE),
        Case("04-minimal-pure", SkinStyle.MINIMAL, AppearanceMode.PURE),
        // 深色态此前**从未**在真机上验过（只截过浅色）。这里一并覆盖。
        Case("05-vivid-rainbow-dark", SkinStyle.VIVID, AppearanceMode.RAINBOW, dark = true),
        Case("06-minimal-rainbow-dark", SkinStyle.MINIMAL, AppearanceMode.RAINBOW, dark = true),
    )

    @Test
    fun `皮肤四象限矩阵截图`() {
        val current = mutableStateOf(quadrantCases.first())
        composeRule.setContent {
            ThemedHost(case = current.value) { SkinPreviewSample() }
        }
        quadrantCases.forEach { case ->
            composeRule.runOnIdle { current.value = case }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("$SHOT_DIR/${case.name}.png")
        }
    }

    /**
     * 弹窗正文的两种形态（用户选「彩虹」与选「纯净」时各看到什么）。
     *
     * ⚠️ 这里刻意渲染 [SkinPreviewContent] 而不是整个 `AlertDialog`：
     * `AlertDialog` 渲染在**独立窗口**里，`captureRoboImage` 抓的是 Compose 根节点，
     * 抓不到那个窗口。正文抽成 `internal` 组件正是为了这一条。
     */
    @Test
    fun `皮肤预览弹窗正文截图`() {
        val currentAppearance = mutableStateOf(AppearanceMode.RAINBOW)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = currentAppearance.value,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(16.dp),
                ) {
                    SkinPreviewContent(selected = SkinStyle.VIVID, onSelect = {})
                }
            }
        }
        AppearanceMode.entries.forEach { appearance ->
            composeRule.runOnIdle { currentAppearance.value = appearance }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("$SHOT_DIR/10-dialog-${appearance.key}.png")
        }
    }

    /**
     * **真实页面组件** —— 这一条守的是**布局回归**，前两条守的是观感。
     *
     * ## 它为什么存在：一个真实漏掉的 bug
     *
     * 角标上线后把「宿舍电费」挤成了「宿舍…」。根因是 Compose 先测量**无 `weight`**
     * 的子项 —— 角标的固有宽度直接从标题嘴里抢空间，标题虽有 `weight` 却只能分到剩余宽度。
     * 当时前两条快照测试都渲的是**预览样例**，抓不到这个。
     *
     * ## ⚠️ 复现真实宽度约束才是关键
     *
     * 服务页是 **2 列网格**，卡片宽 = `(屏宽 − 页边距×2 − 卡片间距) / 2`。
     * 所以这里必须把 [ServiceCard] 放进 `Row` 并给 `Modifier.weight(1f)`。
     * 若图省事直接给 `Modifier.fillMaxWidth()`，卡片会宽到永远不触发截断 ——
     * **那条测试就是假的**：它会一直绿，哪怕真实界面已被挤断。
     */
    @Test
    fun `服务页真实卡片截图`() {
        val current = mutableStateOf(SkinStyle.VIVID)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = current.value,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    ServicePageSample()
                }
            }
        }
        listOf(
            "20-vivid-service-cards" to SkinStyle.VIVID,
            "21-minimal-service-cards" to SkinStyle.MINIMAL,
        ).forEach { (name, skin) ->
            composeRule.runOnIdle { current.value = skin }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("$SHOT_DIR/$name.png")
        }
    }

    /**
     * **窄屏与系统字体放大**下的服务网格（2026-10 布局修复轮的第④阶段）。
     *
     * ## 为什么单开一条
     *
     * 上面那两条基线只跑在 Pixel 5 的 393dp 上。而这一轮把卡片高度从写死的 78dp
     * 改成"内容驱动 + 同行等高"，改动的**风险面恰恰是边界**：360dp 的窄屏上两列卡
     * 更挤（副标题更容易折到第三行），系统字号 1.3× 时文字整体变宽变高。
     * 这两种情形在 393dp / 1.0× 下永远看不见 —— 不渲出来就等于没验。
     *
     * 宽度用 `Modifier.width(360.dp)` 约束**根容器**，而不是换设备画像：
     * 网格卡的宽度竞争只取决于这段可用宽度，页边距与列间距都在样例内部，
     * 所以约束根容器得到的就是 360dp 真机上的那一档卡宽。
     * 字号则用 `LocalDensity` 覆盖 `fontScale`（density 不动），
     * 与用户"系统字体放大"设置对 Compose 的作用方式一致。
     */
    @Test
    fun `服务网格窄屏截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .background(LocalPocketXiaoliColors.current.surfaceBase),
                ) {
                    ServicePageSample()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$SHOT_DIR/240-narrow360-service-cards.png")
    }

    /** 同上，但把系统字号放到 1.3×（Compose 里就是 `Density.fontScale`）。 */
    @Test
    fun `服务网格字体放大截图`() {
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = SkinStyle.VIVID,
                darkTheme = false,
            ) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = 1.3f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LocalPocketXiaoliColors.current.surfaceBase),
                    ) {
                        ServicePageSample()
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$SHOT_DIR/241-largefont-service-cards.png")
    }

    /** 把 `case` 的三个维度套进真实主题入口，并在页底上铺开内容。 */
    @Composable
    private fun ThemedHost(case: Case, content: @Composable () -> Unit) {
        PocketXiaoliTheme(
            appearanceMode = case.appearance,
            skinStyle = case.skin,
            darkTheme = case.dark,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalPocketXiaoliColors.current.surfaceBase)
                    .padding(16.dp),
            ) {
                content()
            }
        }
    }

    /**
     * 「常用服务」**五格**基线（2026-10：默认条数从 3 提到 5）。
     *
     * ## 它守的是什么
     *
     * 改造前默认 3 格、弹窗上限却是 4。现在两者对齐为 **5**。
     * 五格时每格宽度骤降（Pixel 5 上约 **62.6dp**），
     * **最长的默认名「空教室查询」会不会被截断，只有把图渲出来才看得见** ——
     * 语义树里那行字是完整的，`onNodeWithText` 查不出"它被截成了什么样"
     *（同课表格子 `100/101/102` 三张基线的由来）。
     *
     * ⚠️ 下面五个标题必须与 `AppContainer.DEFAULT_QUICK_SERVICE_KEYS` **逐字一致** ——
     * 宽度竞争的边界就是由这五个名字的真实长度决定的。
     */
    @Test
    fun `常用服务五格截图`() {
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
                    QuickFiveSample()
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("$SHOT_DIR/26-quick-five.png")
    }

    /**
     * 服务页新增的两个展示件：分区标题（条数胶囊）与搜索无结果空态卡。
     *
     * 为什么单开一条：它们只存在于私有的 `ServicesBody` 里（整页要 AppContainer /
     * ViewModel，JVM 快照渲不出来），所以把两个组件抽成 `internal` 后直接渲染。
     *
     * 宽度按真实页面给：服务页左右各 16dp（见 `TreasureBoxScreen` 的 `asTab` 分支）。
     * 20sp 标题与条数胶囊会不会挤在一起、空态卡在白底/深底上是否立得起来，
     * 只有把图渲出来才看得见。
     */
    @Test
    fun `服务页分区标题与空态截图`() {
        val current = mutableStateOf(SkinStyle.VIVID)
        composeRule.setContent {
            PocketXiaoliTheme(
                appearanceMode = AppearanceMode.RAINBOW,
                skinStyle = current.value,
                darkTheme = false,
            ) {
                val skin = LocalSkin.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LocalPocketXiaoliColors.current.surfaceBase)
                        .padding(horizontal = 16.dp, vertical = skin.spaceWide),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceLg)) {
                        ServiceSectionHeader(title = "学习考试", count = 12)
                        ServiceSearchEmptyState()
                    }
                }
            }
        }
        listOf(
            "27-vivid-service-section" to SkinStyle.VIVID,
            "28-minimal-service-section" to SkinStyle.MINIMAL,
        ).forEach { (name, skin) ->
            composeRule.runOnIdle { current.value = skin }
            composeRule.waitForIdle()
            composeRule.onRoot().captureRoboImage("$SHOT_DIR/$name.png")
        }
    }
}

/**
 * 服务页的真实版式片段：2 列卡片网格 + 一行「常用服务」。
 *
 * 刻意包含**最坏情况的标题与角标组合**（5 字标题 + 角标、4 字标题 + 角标、
 * 角标的 DANGER 描边变体），因为这些才是宽度竞争的边界。
 *
 * 每行的 `height(IntrinsicSize.Max)` 同样是在**复现线上约束**：服务页的 `chunked(2)`
 * 就是靠它把同行两张卡拉平（卡片内部 `fillMaxHeight`）。漏掉它，测到的是
 * "卡片各自多高算多高"，与真实版式不是一个东西。
 */
@Composable
private fun ServicePageSample() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val gap = skin.spaceMd
    val pagePadding = skin.spaceLg

    fun entry(
        title: String,
        subtitle: String,
        badge: String? = null,
        tone: BadgeTone = BadgeTone.NEUTRAL,
        accent: ServiceAccent = ServiceAccent.PAYMENT,
    ) = ServiceEntry(
        title = title,
        subtitle = subtitle,
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        iconFilled = Icons.Filled.FlashOn,
        accent = accent,
        badge = badge,
        badgeTone = tone,
        onClick = {},
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = pagePadding, vertical = skin.spaceWide),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        // 4 字标题 + 角标：**就是当初被挤断的那一格**
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            ServiceCard(
                entry = entry("宿舍电费", "绑定宿舍后自动匹…", "待绑定", BadgeTone.WARNING),
                modifier = Modifier.weight(1f),
            )
            ServiceCard(
                entry = entry("洗衣机", "当前：浣熊先生", accent = ServiceAccent.LIFE),
                modifier = Modifier.weight(1f),
            )
        }
        // 5 字标题 + 角标：更紧的边界
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            ServiceCard(
                entry = entry(
                    "普通话考试", "报名 · 成绩查询", "待绑定", BadgeTone.WARNING,
                    accent = ServiceAccent.ACADEMIC,
                ),
                modifier = Modifier.weight(1f),
            )
            // DANGER 走的是「surfaceCard 底 + 35% 描边」而非低透明度底色
            ServiceCard(
                entry = entry(
                    "烟台乘车码", "公交 · 扫码乘车", "不可用", BadgeTone.DANGER,
                    accent = ServiceAccent.INFO,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        QuickServiceRow(
            entries = listOf(
                entry("校园卡", "余额 · 充值 · 消费", accent = ServiceAccent.PAYMENT),
                entry("校园网", "认证 · 套餐 · 故障", accent = ServiceAccent.QUERY),
                entry("空教室查询", "找自习室 · 空闲教室", accent = ServiceAccent.STUDY),
            ),
            colors = colors,
            summaries = emptyMap(),
            // 能力角标判定要校区上下文；快照固定用本校区 + 未绑宿舍，保证可复现。
            campus = CampusConfig.byId("ytlg_main"),
            dormInfo = null,
            onEdit = {},
            onServiceClick = {},
        )
    }
}

/**
 * 「常用服务」**五格**的真实版式（2026-10：默认 3 → 5）。
 *
 * 刻意用**真实组件 + 真实宽度约束**：`QuickServiceRow` 内部每个格子是 `Modifier.weight(1f)`，
 * 所以这里只要给它真实的页面内边距，五格的挤压效果就是生产里的那个效果 ——
 * 若图省事塞进一个宽容器，格子会宽到永不截断，那条基线就变成假的
 *（与 `服务页真实卡片截图` 同一个坑，见那边的 KDoc）。
 */
@Composable
private fun QuickFiveSample() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    fun entry(title: String, subtitle: String, accent: ServiceAccent) = ServiceEntry(
        title = title,
        subtitle = subtitle,
        icon = Icons.Outlined.FlashOn,
        tint = Color.Unspecified,
        background = Color.Unspecified,
        iconFilled = Icons.Filled.FlashOn,
        accent = accent,
        onClick = {},
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            .padding(horizontal = skin.spaceLg, vertical = skin.spaceWide),
    ) {
        // ⚠️ 这五个标题 = `AppContainer.DEFAULT_QUICK_SERVICE_KEYS`，逐字一致
        QuickServiceRow(
            entries = listOf(
                entry("校园卡", "余额 · 充值 · 消费", ServiceAccent.PAYMENT),
                entry("校园网", "认证 · 套餐 · 故障", ServiceAccent.INFO),
                entry("空教室查询", "找自习室 · 空闲教室", ServiceAccent.QUERY),
                entry("宿舍电费", "绑定宿舍后自动匹配", ServiceAccent.LIFE),
                entry("图书馆", "馆藏 · 借阅 · 座位", ServiceAccent.STUDY),
            ),
            colors = colors,
            summaries = emptyMap(),
            // 同上：固定本校区 + 未绑宿舍，保证五格快照可复现。
            campus = CampusConfig.byId("ytlg_main"),
            dormInfo = null,
            onEdit = {},
            onServiceClick = {},
        )
    }
}
