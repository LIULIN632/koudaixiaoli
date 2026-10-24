package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.campusui.rememberCampusScrollState
import com.pocket.xiaoli.ui.profile.components.OptionsDialog
import com.pocket.xiaoli.ui.profile.components.SkinPreviewDialog
import com.pocket.xiaoli.ui.theme.BottomBarStyle
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.MiuiMenuMaxHeight
import com.pocket.xiaoli.ui.components.MiuiMenuPopup
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.SkinStyle
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import com.pocket.xiaoli.ui.components.MiuixSettingsSection
import com.pocket.xiaoli.ui.theme.rememberSinkIndication
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch

/**
 * 全 App **唯一**的设置页。
 *
 * ## 结构：2026-10 扁平化（层级从 4 跳降到 2 跳）
 *
 * 改造前设置被拆成两页，而「通用设置」只是设置页里的一个**跳转行**：
 *
 *     我的 → 设置 → 通用设置 → 弹窗        ← 改一个深色模式要 4 跳
 *
 * 而「通用设置」页里 8 项全是弹窗，这一层页面**本身不承载任何内容**。
 * 更糟的是它名不副实：叫"通用"，装的却是外观 + 交互 + 日程。
 *
 * 现在两页合并成一页，层级**封顶 2 跳**（我的 → 设置 → 弹窗 / 子页）。
 *
 * ## 分组依据：**正交维度 / 使用场景**，不是"功能属于哪个模块"
 *
 * 2026-10-18 按用户给的 7 类重排（课表 / 外观 / 服务功能 / 关于 / 更新 / 贡献 / 致谢），
 * 我另加了「高级」「危险操作」两组。
 *
 * | 组 | 判据 |
 * |---|---|
 * | 课表 | "课表怎么算、怎么画"，App 的核心功能，日常最常改 → 排最上；**上课提醒并在其中** |
 * | 外观 | 每个**正交**视觉维度各占一行；**震动反馈**也在其中（它是触感，不是首页功能） |
 * | 服务功能 | "功能怎么用"：百宝箱 / 小组件的总开关、百宝箱入口、自定义首页 |
 * | 关于 | 纯信息 + 找人 + **出问题怎么办**（原「数据与诊断」并进来了） |
 * | 更新 | 把原先**分散在 3 个不同组**里的更新相关项聚在一起 |
 * | 贡献 | 个人项目没有客服与商店评价，得给想帮忙的人一条明确路径 |
 * | 致谢 | GPL 要求保留出处，用户也想知道自己装的东西用了谁的劳动 |
 * | 高级 / 危险操作 | 条件显示 / 不可恢复，各自独立（用户没列，但不能因此消失） |
 *
 * ## 三个名字必须能互相区分（2026-10 改名）
 *
 * 改造前同一屏里有「外观方案」「界面风格」「界面模式」三个名字，用户无法分辨 ——
 * 这是"找不到要改的那一项"的头号原因。它们实际是三个正交维度：
 *
 * | 现名 | 真源 | 管什么 |
 * |---|---|---|
 * | 界面风格 | `SkinStyle`（2 套） | **形状与密度**（圆角 / 间距 / 底色基调） |
 * | 用色强度 | `AppearanceMode` | **彩色 or 中性**（彩虹 = 每卡按类型配色；纯净 = 统一中性色） |
 *
 * 另外两处改名，都是"名字与事实不符"：
 * - 「布局模式」→「界面密度」：副标题原写"服务页密度"，但 `CampusLayoutMode` 实际
 *   影响的是 `HomeScreen` / `ProfileHomeContent` / `TreasureBoxScreen`，与服务页无关
 * - 「方格边框」→「课表网格边框」：`border_style` 的消费方是
 *   `ui/timetable/components/PeriodGrid.kt`（课表网格）与 `ui/map/RoomLayoutGrid.kt`
 *   （教室平面图）—— 它是**内容网格的线**，不是 App 的视觉主题，所以从「外观」移到课表
 *
 * ## 哪些项仍是子页
 *
 * 只有"内容装不进一行"的才另开页：配色主题 / 自定义首页 / 更新日志 /
 * 开发者实验室（解锁后）。其余全部平铺在本页。
 *
 * ## 危险操作为什么独立成组（不改）
 *
 * 1. **它不可恢复**：夹在"清理缓存""更新日志"这些无害操作中间，指头一滑就可能点到；
 * 2. 用常规配色表达不出"负向且破坏性" —— 这里用 danger **描边**而不是填充
 *    （深色主题的 danger 低透明度当底色几乎看不出来，见 CLAUDE.md 记的坑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    // ── 课表（含上课提醒）──
    onTimeSlotManagement: () -> Unit,
    onSemesterSettings: () -> Unit,
    onAdjustedWorkday: () -> Unit,
    onCampusSelect: () -> Unit,
    onImportToCalendar: () -> Unit,
    onReminderTime: () -> Unit,
    // ── 服务功能 ──
    onCustomHome: () -> Unit,
    onTreasureBox: () -> Unit,
    // ── 更新 ──
    onCheckUpdate: () -> Unit,
    onChangelog: () -> Unit,
    // ── 关于（含问题排查）──
    onClearCache: () -> Unit,
    onExportLog: () -> Unit,
    onAnalyzeLog: () -> Unit,
    onAbout: () -> Unit,
    // ── 高级 / 危险操作 ──
    onOpenDeveloperLab: () -> Unit,
    onClearData: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val developerUnlocked by container.developerUnlocked.collectAsStateWithLifecycle()

    /** 当前打开的二级页；null = 停在设置首页。 */
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) }

    // 设置首页的滚动位置要跨两层存活：进二级页（本函数内 `return`）会销毁这段列表，
    // 进「时间段管理」这类外层整页时连本函数本身都会被销毁。用带记忆的实现，
    // 两种返回都回到原处（与服务页 2026-10 那次修复同一类问题）。
    val homeScrollState = rememberCampusScrollState("profile_settings")

    // 二级页在本 Composable **内部**换页，不走 `ProfileScreen` 的路由
    //（理由见 SettingsSubPages.kt 的文件头）。返回键由下面这个内层 BackHandler 接管 ——
    // Compose 的 BackHandler 内层优先，所以它天然比父页那个先拿到返回键。
    //
    // ⚠️ 不能直接对 `subPage` 做智能转换：它是 `by remember` 的委托局部变量、
    // 又被下面的 lambda 捕获，Kotlin 不允许智能转换 —— 先取到局部 val 再判空。
    val activeSubPage = subPage
    if (activeSubPage != null) {
        BackHandler { subPage = null }
        when (activeSubPage) {
            SettingsSubPage.TIMETABLE -> TimetableSettingsPage(
                container = container,
                onBack = { subPage = null },
                onCampusSelect = onCampusSelect,
                onTimeSlotManagement = onTimeSlotManagement,
                onSemesterSettings = onSemesterSettings,
                onAdjustedWorkday = onAdjustedWorkday,
                onImportToCalendar = onImportToCalendar,
                onReminderTime = onReminderTime,
            )

            SettingsSubPage.APPEARANCE -> AppearanceSettingsPage(
                container = container,
                onBack = { subPage = null },
            )

            SettingsSubPage.SERVICES -> ServicesSettingsPage(
                container = container,
                onBack = { subPage = null },
                onCustomHome = onCustomHome,
                onTreasureBox = onTreasureBox,
            )

            else -> SettingsSubPageHost(
                page = activeSubPage,
                container = container,
                onBack = { subPage = null },
                onCheckUpdate = onCheckUpdate,
                onChangelog = onChangelog,
                onAbout = onAbout,
                onClearCache = onClearCache,
                onExportLog = onExportLog,
                onAnalyzeLog = onAnalyzeLog,
                onOpenFeedback = { subPage = SettingsSubPage.FEEDBACK },
                onClearData = onClearData,
                onDeleteAllData = onDeleteAllData,
            )
        }
        return
    }

    MiuixSettingsScaffold(title = "设置", onBack = onBack, manageStatusBar = true) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // 页底用 surfaceBase（与「我的」页一致）。改造前这里用的是 surfaceAlt
                //（比页底再深一档），造成"进设置页像进了另一个 App"。
                .background(colors.surfaceBase)
                .verticalScroll(homeScrollState)
                .padding(horizontal = skin.spaceLg, vertical = 12.dp),
        ) {
            SettingsHomeContent(
                developerUnlocked = developerUnlocked,
                onOpenDeveloperLab = onOpenDeveloperLab,
                onOpenPage = { subPage = it },
            )
        }
    }
}

/**
 * 设置首页的七个分类 —— **唯一真源**。
 *
 * 抽成数据而不是写死在 Composable 里，是为了两件事：
 * 1. 整页基线能渲**真实**的首页（`SettingsScreenshotTest` 调的就是本组件），
 *    不必在测试里另抄一份 7 行；
 * 2. 每个分类的强调色只在这里定义一次，新增一类不会漏掉配色。
 */
private data class SettingsCategory(
    val page: SettingsSubPage,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accent: ServiceAccent,
)

private val SETTINGS_CATEGORIES = listOf(
    SettingsCategory(
        page = SettingsSubPage.TIMETABLE,
        icon = Icons.Outlined.CalendarMonth,
        title = "课表",
        subtitle = "校区 · 时间段 · 学期 · 调休 · 网格边框 · 上课提醒",
        accent = ServiceAccent.ACADEMIC,
    ),
    SettingsCategory(
        page = SettingsSubPage.APPEARANCE,
        icon = Icons.Outlined.DarkMode,
        title = "外观",
        subtitle = "深浅色 · 界面风格 · 用色强度 · 界面密度 · 底栏",
        accent = ServiceAccent.STUDY,
    ),
    SettingsCategory(
        page = SettingsSubPage.SERVICES,
        icon = Icons.Filled.CardGiftcard,
        title = "服务功能",
        subtitle = "百宝箱 · 桌面小组件 · 自定义首页",
        accent = ServiceAccent.LIFE,
    ),
    SettingsCategory(
        page = SettingsSubPage.ABOUT,
        icon = Icons.AutoMirrored.Outlined.HelpOutline,
        title = "关于口袋小鲤",
        subtitle = "常见问题 · 意见反馈 · 版本信息 · 问题排查",
        accent = ServiceAccent.INFO,
    ),
    SettingsCategory(
        page = SettingsSubPage.UPDATE,
        icon = Icons.Filled.SystemUpdate,
        title = "更新",
        subtitle = "检查更新 · 后台检查 · 更新日志",
        accent = ServiceAccent.QUERY,
    ),
    SettingsCategory(
        page = SettingsSubPage.CONTRIBUTE,
        icon = Icons.Filled.Favorite,
        title = "贡献",
        subtitle = "校园共建榜 · 报问题 · 参与开发",
        accent = ServiceAccent.PAYMENT,
    ),
    SettingsCategory(
        page = SettingsSubPage.THANKS,
        icon = Icons.Filled.Star,
        title = "致谢",
        subtitle = "这份代码站在哪些项目肩上",
        accent = ServiceAccent.ACADEMIC,
    ),
)

/**
 * 设置首页正文：七个分类 + 高级（解锁后）+ 危险操作。
 *
 * 参数只有"打开哪一页"和两个开关状态，不碰 `AppContainer` —— 所以快照测试
 * 能像渲普通组件一样渲它，看到的就是用户真正看到的首页。
 */
@Composable
internal fun SettingsHomeContent(
    developerUnlocked: Boolean,
    onOpenDeveloperLab: () -> Unit,
    onOpenPage: (SettingsSubPage) -> Unit,
) {
    val skin = LocalSkin.current
    Column(verticalArrangement = Arrangement.spacedBy(skin.spaceWide)) {
        // ═══ 七个分类，一条不落，每一条都进二级页 ═══
        //
        // 2026-10-18 第二次改：改造前是**混合形态** —— 课表 / 外观 / 服务功能就地展开，
        // 关于 / 更新 / 贡献 / 致谢进二级页。同样的行，有的点进去、有的就地变长，
        // 用户直接问「一样为啥有的是二级」。答案是"没有理由，只是历史演化的结果"。
        //
        // 现在统一成一种模型：**设置首页只负责分类**。好处不只是整齐，还有两层：
        // 首页从四十多行降到九行，找东西靠的是七个能记住的名字，而不是滚动与目测；
        // 分类页内部再用卡片分组，层数永远是两层，不会出现"点进去还有一屏"。
        SettingsCard {
            SETTINGS_CATEGORIES.forEachIndexed { index, category ->
                SettingsMenuItem(
                    icon = category.icon,
                    title = category.title,
                    subtitle = category.subtitle,
                    accent = category.accent,
                    onClick = { onOpenPage(category.page) },
                )
                if (index != SETTINGS_CATEGORIES.lastIndex) SettingsDivider()
            }
        }

        // ═══ 高级（仅解锁后） ═══
        // 开发者实验室是全 App 的诊断工具（网络日志、Room 查看器、Mock、Deep Link），
        // 与任何单个板块都无关，所以留在首页而不是挂进某个分类。
        if (developerUnlocked) {
            SettingsGroup("高级") {
                SettingsMenuItem(
                    icon = Icons.Filled.BugReport,
                    title = "开发者实验室",
                    subtitle = "诊断报告 · 网络日志 · 数据工具 · Mock / Deep Link · 重置",
                    accent = ServiceAccent.PAYMENT,
                    onClick = onOpenDeveloperLab,
                )
            }
        }

        // ═══ 危险操作 ═══
        // 仍独立成一块、仍用 danger 描边（不复用常规设置行）：它虽然也进二级页，
        // 但"这里有不可恢复的操作"这个信号不能藏起来（理由见函数 KDoc）。
        // 不画组标题 —— 行本身就叫「危险操作」，再写一遍只是重复。
        SettingsCard {
            DangerSettingsItem(
                title = "危险操作",
                subtitle = "清空课表数据 / 删除全部本地数据 · 不可恢复",
                onClick = { onOpenPage(SettingsSubPage.DANGER) },
            )
        }
    }
}

/**
 * 课表分类页。
 *
 * 排分类第一位：课表是这个 App 的核心功能，日常最常改。
 * 内容原先散在独立的「课表设置」页与「通用设置」里。
 */
@Composable
private fun TimetableSettingsPage(
    container: AppContainer,
    onBack: () -> Unit,
    onCampusSelect: () -> Unit,
    onTimeSlotManagement: () -> Unit,
    onSemesterSettings: () -> Unit,
    onAdjustedWorkday: () -> Unit,
    onImportToCalendar: () -> Unit,
    onReminderTime: () -> Unit,
) {
    val selectedCampus by container.selectedCampus.collectAsStateWithLifecycle()
    val reminderEnabled by container.reminderEnabled.collectAsStateWithLifecycle()
    val reminderMinutes by container.reminderMinutes.collectAsStateWithLifecycle()
    val borderStyle by container.borderStyle.collectAsStateWithLifecycle()
    val borderLabel = when (borderStyle) { 1 -> "实线"; 2 -> "虚线"; else -> "无" }

    SettingsSubPageScaffold(title = "课表", onBack = onBack) {
        SettingsCard {
            SettingsMenuItem(
                icon = Icons.Outlined.LocationCity,
                title = "当前校区",
                subtitle = selectedCampus.name + " · 点击切换",
                accent = ServiceAccent.QUERY,
                onClick = onCampusSelect
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.Schedule,
                title = "时间段管理",
                subtitle = "设置每节课时间和休息时长",
                accent = ServiceAccent.ACADEMIC,
                onClick = onTimeSlotManagement
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.CalendarMonth,
                title = "学期设置",
                subtitle = "学期开始日期、自动计算当前周",
                accent = ServiceAccent.ACADEMIC,
                onClick = onSemesterSettings
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.DateRange,
                title = "调休补课",
                subtitle = "指定补班日按哪天的课表上课",
                accent = ServiceAccent.ACADEMIC,
                onClick = onAdjustedWorkday
            )
            SettingsDivider()
            // 从「通用设置 · 外观」搬来的：它的消费方是 PeriodGrid（课表网格）
            // 与 RoomLayoutGrid（教室平面图），属"内容网格的线"，不是 App 视觉主题。
            SettingsChoiceItem(
                icon = Icons.Outlined.GridOn,
                title = "课表网格边框",
                subtitle = borderLabel + " · 同时作用于教室平面图",
                options = listOf("无" to 0, "实线" to 1, "虚线" to 2),
                selected = borderStyle,
                onSelect = { container.setBorderStyle(it) },
                accent = ServiceAccent.STUDY,
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.CalendarMonth,
                title = "导入到日程",
                subtitle = "将本学期课表导入系统日历",
                accent = ServiceAccent.INFO,
                onClick = onImportToCalendar
            )
            SettingsDivider()
            // 上课提醒原先自成一组。2026-10-18 按用户给的 7 类并进「课表」：
            // 它改的就是课表提醒，独立成组会让"课表相关"散在两组里。
            // 开关与时间仍是**一体两面**（关了总开关，"提前多久"就没意义），靠顺序表达从属。
            SettingsSwitchItem(
                icon = Icons.Outlined.Notifications,
                title = "上课提醒总开关",
                subtitle = "课前自动发送上课提醒通知",
                checked = reminderEnabled,
                onToggle = { container.toggleReminder() },
                accent = ServiceAccent.LIFE,
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.Timer,
                title = "提前提醒时间",
                subtitle = reminderMinutes.toString() + " 分钟",
                accent = ServiceAccent.LIFE,
                onClick = onReminderTime
            )
        }
    }
}

/**
 * 外观分类页。
 *
 * 6 个**正交**的视觉维度各占一行。这正是它们必须能互相区分的原因：
 * 配色主题(色相) / 界面风格(形状·密度·底色) / 用色强度(彩色·中性)。
 * 改造前这三项叫「外观方案 / 界面风格 / 界面模式」，用户无法分辨。
 *
 * 深色模式 / 界面风格 / 界面密度 / 震动反馈这 4 个是"就地弹一个 OptionsDialog"，
 * 弹窗状态 `openPick` 就放在本页：用户一离开分类页，弹窗自然消失。
 */
@Composable
private fun AppearanceSettingsPage(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val darkMode by container.darkMode.collectAsStateWithLifecycle()
    val skinStyle by container.skinStyle.collectAsStateWithLifecycle()
    val appearanceMode by container.appearanceMode.collectAsStateWithLifecycle()
    val campusLayoutMode by container.campusLayoutMode.collectAsStateWithLifecycle()
    val vibrationMode by container.vibrationMode.collectAsStateWithLifecycle()
    // 底栏样式必须读 `observe()` 这条流，**不能**读 `.selected` ——
    // 后者是非 Compose 可观察的一次性读取，选完 chip 不会重组（看着像"没保存"）。
    val bottomBarStyle by container.bottomBarStyleStore
        .observe()
        .collectAsStateWithLifecycle(initialValue = container.bottomBarStyleStore.selected)

    var openPick by remember { mutableStateOf<SettingsPick?>(null) }

    val skinStyleInfo = SkinStyle.fromKey(skinStyle)

    SettingsSubPageScaffold(title = "外观", onBack = onBack) {
        SettingsCard {
            // 多选一现在统一走「点一行 → 就地弹气泡」，不再弹居中模态框
            //（岱宗盒子的 AppDropdownMenu 就是这个交互）。
            SettingsMenuChoiceItem(
                icon = Icons.Outlined.DarkMode,
                title = "深色/浅色模式",
                subtitle = "整界面配色跟随它",
                options = listOf("浅色模式" to "light", "深色模式" to "dark", "跟随系统" to "system"),
                selected = darkMode,
                onSelect = { mode ->
                    container.setDarkMode(mode)
                    // 深色模式由 Compose 主题（MainActivity 消费 darkMode）驱动，
                    // 这里额外同步系统 uiMode 以覆盖 WebView 等非 Compose 内容
                    container.applySystemNightMode(mode)
                },
                accent = ServiceAccent.STUDY,
            )
            SettingsDivider()
            SettingsMenuItem(
                icon = Icons.Outlined.AspectRatio,
                title = "界面风格",
                subtitle = skinStyleInfo.label + " · " + skinStyleInfo.description,
                onClick = { openPick = SettingsPick.SKIN_STYLE },
                accent = ServiceAccent.QUERY,
            )
            SettingsDivider()
            // 原名「界面模式」，与「界面风格」撞车；它实际只控制"彩色 or 中性"。
            SettingsMenuChoiceItem(
                icon = Icons.Outlined.Tune,
                title = "用色强度",
                subtitle = "彩虹 = 每张卡按类型配色；纯净 = 统一中性色",
                options = AppearanceMode.entries.map { it.label to it },
                selected = AppearanceMode.fromKey(appearanceMode),
                onSelect = { container.setAppearanceMode(it.key) },
                accent = ServiceAccent.ACADEMIC,
            )
            SettingsDivider()
            // 原名「布局模式」，副标题原写"服务页密度"是错的：CampusLayoutMode
            // 实际影响首页 / 我的 / 百宝箱三处的紧凑度。
            SettingsMenuChoiceItem(
                icon = Icons.Outlined.ViewAgenda,
                title = "界面密度",
                subtitle = "紧凑排列首页、我的与百宝箱",
                options = CampusLayoutMode.entries.map { it.label to it.key },
                selected = campusLayoutMode,
                onSelect = { container.setCampusLayoutMode(it) },
                accent = ServiceAccent.ACADEMIC,
            )
            SettingsDivider()
            SettingsMenuChoiceItem(
                icon = Icons.Outlined.Smartphone,
                title = "底栏样式",
                subtitle = bottomBarStyle.tierNote(),
                options = BottomBarStyle.entries.map { it.label to it },
                selected = bottomBarStyle,
                onSelect = { container.bottomBarStyleStore.set(it) },
                accent = ServiceAccent.INFO,
            )
            SettingsDivider()
            // 震动反馈原在「首页与交互」组。2026-10-18 并进「外观」：
            // 它是**触感**，与视觉同属"用起来什么感觉"，不是首页的功能。
            SettingsMenuChoiceItem(
                icon = Icons.Outlined.Vibration,
                title = "震动反馈",
                subtitle = "列表项按下去的手感",
                options = listOf("关闭" to 0, "轻柔" to 1, "适中" to 2, "强力" to 3),
                selected = vibrationMode,
                onSelect = { container.setVibrationMode(it) },
                accent = ServiceAccent.PAYMENT,
            )
        }
    }

    // ── 唯一还用弹窗的多选一：「界面风格」要可视化预览 ──
    // 必须画在本页内、**不能**交给 ProfileDialogHost：本页是 ProfileScreen 里
    // 会 return 的子页面，父页的 host 此时根本不会被组合，弹窗永远不会出现
    //（表现就是"点深色模式没反应"）。
    when (openPick) {
        // 「界面风格」换成可视化预览选择器。它是**纯视觉**的选择，让用户读两行文字
        //（"紧凑 · 小圆角 · 灰底白卡"）去猜圆角与阴影的差别没有意义。
        // 新组件用真实令牌现场渲染，所以预览与切换后的效果不可能漂移 ——
        // 详见 `SkinPreviewDialog` 的 KDoc。
        SettingsPick.SKIN_STYLE -> SkinPreviewDialog(
            selected = SkinStyle.fromKey(skinStyle),
            onSelect = { container.setSkinStyle(it.key) },
            onDismiss = { openPick = null }
        )

        null -> Unit
    }
}

/**
 * 服务功能分类页。
 *
 * 名字由用户给定（原名「功能开关」）。除了两个总开关，还收了
 * 「打开百宝箱」入口与「自定义首页」—— 它们都是"功能怎么用"，不是"App 长什么样"。
 */
@Composable
private fun ServicesSettingsPage(
    container: AppContainer,
    onBack: () -> Unit,
    onCustomHome: () -> Unit,
    onTreasureBox: () -> Unit,
) {
    val showTreasureBox by container.showTreasureBox.collectAsStateWithLifecycle()
    val showWidget by container.showWidget.collectAsStateWithLifecycle()

    SettingsSubPageScaffold(title = "服务功能", onBack = onBack) {
        SettingsCard {
            SettingsSwitchItem(
                icon = Icons.Filled.CardGiftcard,
                title = "百宝箱",
                subtitle = "学习资源、今天吃什么等（默认关闭）",
                checked = showTreasureBox,
                onToggle = { container.toggleTreasureBox() },
                accent = ServiceAccent.LIFE,
            )
            SettingsDivider()
            SettingsSwitchItem(
                icon = Icons.Outlined.ToggleOn,
                title = "桌面小组件",
                subtitle = "桌面快捷查看课表（默认关闭）",
                checked = showWidget,
                onToggle = { container.toggleWidget() },
                accent = ServiceAccent.QUERY,
            )
            // 「百宝箱」开着时才给入口：关掉后点进去是空的，留个死链不如不显示。
            if (showTreasureBox) {
                SettingsDivider()
                SettingsMenuItem(
                    icon = Icons.Filled.CardGiftcard,
                    title = "打开百宝箱",
                    subtitle = "学习资源、今天吃什么等",
                    onClick = onTreasureBox,
                    accent = ServiceAccent.LIFE,
                )
            }
            SettingsDivider()
            // 从撤销的「首页与交互」组搬来：它决定首页显示哪些模块，属于"功能怎么用"。
            SettingsMenuItem(
                icon = Icons.Outlined.Dashboard,
                title = "自定义首页",
                subtitle = "模块开关 · 拖动排序",
                onClick = onCustomHome,
                accent = ServiceAccent.STUDY,
            )
        }
    }
}
/** 设置页里唯一还用弹窗的多选一：「界面风格」要可视化预览（见 `openPick`）。 */
private enum class SettingsPick { SKIN_STYLE }

/**
 * 破坏性操作行：danger 描边 + danger 文字。
 *
 * 为什么单独做一个组件而不是给 `SettingsMenuItem` 加参数：
 * 破坏性操作只有这一个，为它往通用组件里加分支，会让每一次读
 * `SettingsMenuItem` 的人都要多想一层"这里还会不会是危险样式"。
 * 显式分开更清楚，也不影响已有调用点。
 */
@Composable
internal fun DangerSettingsItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(skin.radiusLarge))
            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(skin.radiusLarge))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onClick)
            .padding(horizontal = skin.spaceLg, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(skin.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.danger)
            Spacer(Modifier.height(2.dp))
            // ⚠️ 副标题必须是 textSecondary（2026-10 与绑定信息页的 DangerRow **一起**改）。
            // 它承载的是"点了会发生什么"，而 textTertiary 在卡底只有 2.15:1 ——
            // 危险区用全 App 最弱的字是最不该省的对比度。两处必须同款，否则又是
            // "同类东西两种长相"（那种分叉在这个项目里已经修过好几次）。
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
    }
}

// ═══════════════════════════════════════════
//  设置行组件（全 App 共用）
//
//  原先住在 SettingsMainScreen.kt（「课表设置」页）里。那个页面已被合并掉，
//  零件搬到这里 —— 它们现在是**整个设置页**的行组件，不再属于课表设置。
//
//  2026-10 更新：`GeneralSettingsScreen`（「通用设置」页）也已合并进本页，
//  所以这里的组件现在的使用者是——本页 + 「我的」页 + 「绑定信息」页。
// ═══════════════════════════════════════════

/**
 * 设置类区块的标题 —— **设置页与「我的」页共用的唯一实现**。
 *
 * ## 为什么要统一
 *
 * 改造前同一 App 里有两套"设置区"长相：「我的」页是 20sp 粗体 + 主色文字，
 * 设置页是 14sp SemiBold + 弱化色。两者装的是同一类东西（一组设置项 + 一张卡），
 * 却长得像两个 App 的页面。
 *
 * 取小号弱化色是通行做法（iOS / Android 的设置页都这样）：分组感来自**卡**本身，
 * 标题只是索引 —— 用大粗标题会跟卡内条目抢视觉重量。
 *
 * MIUIX: 2026-10-18 起正文改用 MIUIX 的 SmallTitle（MiuixSettingsSection），不再自绘。
 */
@Composable
internal fun SettingsSectionHeader(title: String) {
    MiuixSettingsSection(title)
}

/**
 * 一张设置卡 —— **全页唯一的卡片实现**。
 *
 * 设置首页的 [SettingsGroup] 与二级页（[SettingsSubPages]）共用它：
 * 二级页的顶栏已经写了页名，所以那边只用卡、不再画组标题（见 `SettingsSubPages.kt`）。
 *
 * 2026-10 与首页 / 消息页 / 搜索结果卡统一：走 [AppCard] 的皮肤推导
 * （`surfaceCard` 底色 + `skin.radiusCard` + VIVID 的弥散阴影 + 按压反馈）。
 * 此前用 MIUIX 的 `surfaceVariant` 中性灰面，和首页的卡片在同一屏里明显不是一套东西。
 */
@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    AppCard(content = content)
}

/** 一组设置：小标题 + 一张 card 包住若干行。 */
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(title)
        SettingsCard(content = content)
    }
}

/**
 * 组内分割线。
 *
 * 起始缩进 64dp = 行内边距 16 + 图标块 36 + 间隔 12 —— 让线从**文字左边缘**起，
 * 而不是从卡片边缘起。改行内边距或图标尺寸时这个值要跟着改。
 */
@Composable
internal fun SettingsDivider() {
    val colors = LocalPocketXiaoliColors.current
    HorizontalDivider(
        color = colors.borderDivider,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 64.dp, end = 16.dp)
    )
}

/**
 * 设置行的图标块。
 *
 * ## 2026-10 二次改版：圆 → 圆角方块 + 分类色
 *
 * 改前是 32dp 淡主色圆形 + 主色图标。整页 7 个分类全是同一种蓝，扫视时没有
 * "这一行是哪一类"的线索，页面读起来像一张灰白表格。
 *
 * 现在按 [ServiceAccent] 取色（与「服务」页、AI 设置首页同一套色板）：
 * 教务蓝 / 学习紫 / 生活绿 / 信息粉 / 查询青 / 缴费橙。色板本身随主题色相旋转，
 * 换主题不会跟配色打架；「纯净」与「极简」下退回中性底 + 主色图标，保持克制。
 */
@Composable
private fun SettingsIcon(icon: ImageVector, accent: ServiceAccent?) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val colorful = currentIconPresentation().colorful
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(skin.radiusChip))
            .background(
                when {
                    !colorful -> colors.surfaceHighlight
                    accent != null -> serviceAccentContainer(accent)
                    else -> colors.accentMain.copy(alpha = 0.12f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (colorful && accent != null) serviceAccentColor(accent) else colors.accentMain,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * 一行可点设置项：图标 + 标题 + 副标题 + 右箭头。
 *
 * [onLongClick] 默认 null（= 这一行只有单击）。目前唯一的使用者是「推荐给同学」：
 * 轻点看链接、**长按直接把安装包发出去** —— 同一行的第二个手势，页面上不占任何空间。
 *
 * [accent] 是该行的分类色；null 时沿用主色。分类色只在「彩虹」用色强度下生效，
 * 其余模式退回中性底 + 主色（见 [SettingsIcon]）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    accent: ServiceAccent? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberSinkIndication(), onClick = onClick, onLongClick = onLongClick)
            // 行高跟随「外观 → 我的 → 列表密度」—— 这个设置的字面语义就是
            // "设置项的上下留白"，而这里正是设置项
            .padding(horizontal = skin.spaceLg, vertical = LocalProfileRowPadding.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, accent)
        Spacer(Modifier.width(skin.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            // textTertiary 在白卡上约 2.15:1，12sp 的说明文字会看不清。
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 一行「多选一」设置项：标题 + 当前值 + 点开**气泡菜单**选。
 *
 * 为什么不复用 [SettingsChoiceItem]（那排 chip）：chip 是把所有选项摊在页面上，
 * 一行里有四五个还勉强，再多就得横向滚动、还占两行高度。岱宗盒子的做法是
 * 「点这一行 → 从这一行下面弹出选项气泡」，这里**直接用他那一套 MIUIX 组件**
 * （[MiuiMenuPopup] = `WindowDropdownPopup`），圆角/行高/选中态/触觉反馈全由 MIUIX 给，
 * 不再自己用 Material3 的 `DropdownMenuItem` 拼。
 *
 * 与 [SettingsMenuItem] 的区别只有一个：这个不跳页，就地弹气泡。
 */
@Composable
internal fun <T> SettingsMenuChoiceItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    accent: ServiceAccent? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var expanded by remember { mutableStateOf(false) }
    val selectedIndex = options.indexOfFirst { it.second == selected }
    val currentLabel = options.getOrNull(selectedIndex)?.first.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = skin.spaceLg, vertical = LocalProfileRowPadding.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, accent)
        Spacer(Modifier.width(skin.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        Text(currentLabel, fontSize = 13.sp, color = colors.textSecondary)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        // 必须挂在这一行里面：MIUIX 拿「父布局」的位置定位气泡（见 MiuiMenuPopup 的 KDoc）
        MiuiMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            options = options.map { it.first },
            selectedIndex = selectedIndex,
            maxHeight = MiuiMenuMaxHeight,
            onSelect = { index ->
                expanded = false
                val value = options.getOrNull(index)?.second ?: return@MiuiMenuPopup
                if (value != selected) onSelect(value)
            },
        )
    }
}

@Composable
internal fun <T> SettingsChoiceItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    horizontalPadding: Dp? = null,
    accent: ServiceAccent? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val inset = horizontalPadding ?: skin.spaceLg
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 同 SettingsMenuItem：设置项的行高由「列表密度」决定
            .padding(horizontal = inset, vertical = LocalProfileRowPadding.current)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon, accent)
            Spacer(Modifier.width(skin.spaceMd))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentMain,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

/** 一行带开关的设置项。整行可点，开关本身不单独接点击（避免"点中间没反应"）。 */
@Composable
internal fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    accent: ServiceAccent? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            // 与菜单行同样的密度档：同一张卡里两种行高，切换行会显得"缩了一截"
            .padding(horizontal = skin.spaceLg, vertical = LocalProfileRowPadding.current),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, accent)
        Spacer(Modifier.width(skin.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = colors.textSecondary)
        }
        MiuixSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}
