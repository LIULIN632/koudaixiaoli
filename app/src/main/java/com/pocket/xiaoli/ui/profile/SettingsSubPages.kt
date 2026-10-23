package com.pocket.xiaoli.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.BuildConfig
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.notification.AppFeedScheduler
import com.pocket.xiaoli.data.legal.AgreementText
import com.pocket.xiaoli.telemetry.Telemetry
import com.pocket.xiaoli.ui.legal.AgreementMode
import com.pocket.xiaoli.ui.legal.UserAgreementScreen
import com.pocket.xiaoli.ui.profile.components.FaqDialog
import com.pocket.xiaoli.ui.share.AppShare
import com.pocket.xiaoli.ui.share.RecommendSheet
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.components.MiuixSettingsScaffold
import kotlinx.coroutines.launch

/**
 * 设置页的**二级页面**。
 *
 * ## 为什么要有二级页（2026-10，修正上一轮）
 *
 * 上一轮把「通用设置」并进设置页、做成单页扁平化 —— 那解决了"两个并列的设置根
 * + 一个名不副实的中间层"。但实际用下来那一页有 **34 个可点行 / 约 4 屏**，
 * 滚到底很累。
 *
 * 层级与滚动是一对**无法两全**的张力：
 * - 层级少 → 滚动多（上一轮的问题）
 * - 层级多 → 每次都要多点（改造前「通用设置」的问题）
 *
 * 所以按**使用频率**切，而不是二选一：
 * - **留在设置首页**：课表 / 外观 / 服务功能 —— 天天改
 * - **进二级页**：关于 / 更新 / 贡献 / 致谢 / 危险操作 —— 一年点两次
 *
 * ## 2026-10-18：按用户给的 7 类重组
 *
 * 用户列的分类是「课表 / 外观 / 服务功能 / 关于 / 更新 / 贡献 / 致谢」。
 * 落地时的三处调整（都属于用户授权的"可以修改和补充"）：
 * 1. **上课提醒并进「课表」** —— 它改的就是课表提醒，独立成组会让"课表相关"
 *    散在两组里；开关在前、时间在后，从属关系靠顺序表达。
 * 2. **震动反馈并进「外观」** —— 它是触感，不是"首页"的一部分；
 *    原「首页与交互」组随之撤销，「自定义首页」归到「服务功能」。
 * 3. **危险操作保留**（用户没列）—— 不可恢复的操作不能因为分类里没有就消失。
 *    另保留「高级」组（只在解锁后出现）。
 * 4. **数据与诊断并进「关于」** —— 用户没单列这一类，而"出问题了怎么办"
 *    与"这个 App 是什么"本来就是一个念头。二级页里用一小节隔开。
 *
 * ## 实现方式：同一个 Composable 内部换页，**不**新开一屏路由
 *
 * 二级页由 [ProfileSettingsScreen] 内部的一个 `when` 渲染（见那里的 `subPage`），
 * 不经过 `ProfileScreen` 的路由。理由：
 * - 返回键由内层 `BackHandler` 接管，Compose 内层优先，不需要父页配合
 * - 父页的路由已经很长（十来个 `showXxx`），再加五个只会更难读
 */
internal enum class SettingsSubPage(val title: String) {
    TIMETABLE("课表"),
    APPEARANCE("外观"),
    SERVICES("服务功能"),
    ABOUT("关于"),
    /** 意见反馈：从「关于」那一卡点进来，但自己是一整页（表单比弹窗好写）。 */
    FEEDBACK("意见反馈"),
    UPDATE("更新"),
    CONTRIBUTE("贡献"),
    THANKS("致谢"),
    DANGER("危险操作"),
}

/** 仓库与联系方式：只在这里写一次，页面里多处引用。 */
private const val GITHUB_REPO_URL = "https://github.com/LIULIN632/koudaixiaoli"
private const val GITHUB_ISSUES_URL = "https://github.com/LIULIN632/koudaixiaoli/issues"
private const val GITEE_REPO_URL = "https://gitee.com/liulin62633/pocket-carp"
private const val FEEDBACK_MAIL = "mailto:pocketxiaoyan@outlook.com"

/**
 * 二级设置页的外壳：顶栏 + 返回 + 一屏可滚动的设置卡。
 *
 * 与设置首页**共用同一套**页边距与卡间距（`spaceLg` / `spaceWide`），
 * 这样进出二级页时版心不会跳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    MiuixSettingsScaffold(title = title, onBack = onBack, manageStatusBar = true) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceBase)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(skin.spaceWide),
            content = content,
        )
    }
}

/**
 * 二级页的分发器。
 *
 * 各页需要的只是"当前值 + 回调"，没有本地状态要保 —— 所有值都从 `container`
 * 或父页回调进来，所以不必把状态提升到父页。
 */
@Composable
internal fun SettingsSubPageHost(
    page: SettingsSubPage,
    container: AppContainer,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onChangelog: () -> Unit,
    onAbout: () -> Unit,
    onClearCache: () -> Unit,
    onExportLog: () -> Unit,
    onAnalyzeLog: () -> Unit,
    onOpenFeedback: () -> Unit,
    onClearData: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    // 协议页是**整页**，不能在 `SettingsSubPageScaffold` 里渲染：
    // 那个外壳的内容区是 `verticalScroll` 的 Column，给子项的高度约束是无限的，
    // `UserAgreementScreen` 里的 `fillMaxSize()` + `weight(1f)` 在无限高度下会塌成 0 ——
    // 现象就是「关于 → 用户协议」点进去一片空白（2026-10-23 真机反馈）。
    // 所以在这个函数**最外层**分流：打开协议时整页替换掉设置页，返回即关闭。
    var agreementOpen by remember { mutableStateOf(false) }
    if (agreementOpen) {
        UserAgreementScreen(mode = AgreementMode.READ, onBack = { agreementOpen = false })
        return
    }

    SettingsSubPageScaffold(title = page.title, onBack = onBack) {
        when (page) {
            SettingsSubPage.UPDATE -> UpdateContent(container, onCheckUpdate, onChangelog)
            SettingsSubPage.ABOUT ->
                AboutContent(
                    onAbout = onAbout,
                    onClearCache = onClearCache,
                    onExportLog = onExportLog,
                    onAnalyzeLog = onAnalyzeLog,
                    onOpenFeedback = onOpenFeedback,
                    onOpenAgreement = { agreementOpen = true },
                )
            SettingsSubPage.FEEDBACK -> FeedbackContent()
            SettingsSubPage.CONTRIBUTE -> ContributeContent()
            SettingsSubPage.THANKS -> ThanksContent()
            SettingsSubPage.DANGER -> DangerContent(onClearData, onDeleteAllData)
            // 这三页实现在 ProfileSettingsScreen.kt 里（它们要带一堆父页回调），走不到这里。
            SettingsSubPage.TIMETABLE, SettingsSubPage.APPEARANCE, SettingsSubPage.SERVICES -> Unit
        }
    }
}

// ═══ 关于 ═══
//
// 用户找"这是哪个版本"和"我要反馈问题"时看的是同一个地方。
// 2026-10-18 起「数据与诊断」并进来：那三项（清理缓存 / 导出日志 / 让鲤鱼看看）
// 都是"出问题了怎么办"，与"这个 App 是什么"是同一件事的两半。
// 两个弹窗的开关归本页所有：它们只从这里进，没必要提升到父页。
@Composable
private fun AboutContent(
    onAbout: () -> Unit,
    onClearCache: () -> Unit,
    onExportLog: () -> Unit,
    onAnalyzeLog: () -> Unit,
    onOpenFeedback: () -> Unit,
    onOpenAgreement: () -> Unit,
) {
    var showFaqDialog by remember { mutableStateOf(false) }
    var showRecommendSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val versionName = BuildConfig.VERSION_NAME
    // 匿名统计的开关状态：只在设置页读一次，切换后立即写回（见 Telemetry.setEnabled）
    var telemetryOn by remember { mutableStateOf(Telemetry.isEnabled(context)) }

    // 长按这一行 = 把本机装着的版本直接递出去（拷贝在 IO 线程，见 AppShare）
    val shareApk: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val started = AppShare.shareInstalledApk(context, versionName)
            Toast.makeText(
                context,
                if (started) "选一个 App 发给同学" else "没找到安装包，请用链接分享",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    SettingsCard {
        SettingsMenuItem(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = "常见问题",
            subtitle = "导入、数据存放、分享、深色模式",
            onClick = { showFaqDialog = true },
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Outlined.MailOutline,
            title = "意见反馈",
            subtitle = "复制 / 邮件 / GitHub，或者填个表单",
            onClick = onOpenFeedback,
            accent = ServiceAccent.INFO,
        )
        SettingsDivider()
        // 推广自己的入口本身不挂「推广」角标：那条铁律管的是**第三方/合作方**内容，
        // 这一行推的是 App 自己（见 memory/05-promo-ops.md）。
        SettingsMenuItem(
            icon = Icons.Filled.Share,
            title = "推荐给同学",
            subtitle = "点一下看下载链接 · 长按发安装包",
            onClick = { showRecommendSheet = true },
            onLongClick = shareApk,
            accent = ServiceAccent.PAYMENT,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Info,
            title = "关于口袋小鲤",
            subtitle = "版本信息与开源致谢",
            onClick = onAbout,
            accent = ServiceAccent.INFO,
        )
    }

    SettingsSectionHeader("遇到问题时")
    SettingsCard {
        SettingsMenuItem(
            icon = Icons.Filled.DeleteSweep,
            title = "清理缓存",
            subtitle = "清理 WebView 缓存与 Cookie",
            onClick = onClearCache,
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.BugReport,
            title = "导出日志",
            subtitle = "导出崩溃日志与诊断报告",
            onClick = onExportLog,
            accent = ServiceAccent.PAYMENT,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.AutoAwesome,
            title = "让鲤鱼看看",
            subtitle = "把诊断报告交给 AI 分析原因",
            onClick = onAnalyzeLog,
            accent = ServiceAccent.ACADEMIC,
        )
    }

    // ═══ 隐私 ═══
    //
    // 开关放在「关于」而不是首页：它一年也未必动一次，但它必须**找得到**。
    // 关掉之后不是"不发"，是本机连队列都不写（见 `TelemetryStore`），
    // 这就是副标题里"之后不再产生新数据"的意思。
    SettingsSectionHeader("隐私")
    SettingsCard {
        SettingsSwitchItem(
            icon = Icons.Outlined.Insights,
            title = "匿名运行统计",
            subtitle = "只要启动、更新、崩溃的次数与版本号；关掉后不再产生新数据",
            checked = telemetryOn,
            onToggle = {
                val next = !telemetryOn
                telemetryOn = next
                Telemetry.setEnabled(context, next)
            },
            accent = ServiceAccent.INFO,
        )
        SettingsDivider()
        // ⚠️ 这里曾经有一个「服务页推广位」开关，2026-10-23 用户要求去掉：
        // 服务页顶部那条是**介绍学校与周边资源 + 帮合作方推一下**的展示位，
        // 属于产品内容的一部分，不是需要用户关掉的广告。（推广位本身仍带「推广」角标、
        // 不埋点；`PromoRepository.enabled` 的本地开关保留在代码里，只是不再露给用户。）
        SettingsMenuItem(
            icon = Icons.Outlined.Description,
            title = "用户协议与免责声明",
            subtitle = "版本 v${AgreementText.VERSION} · 生效于 ${AgreementText.EFFECTIVE_DATE}",
            onClick = onOpenAgreement,
            accent = ServiceAccent.QUERY,
        )
    }

    if (showFaqDialog) {
        FaqDialog(onDismiss = { showFaqDialog = false })
    }
    RecommendSheet(
        show = showRecommendSheet,
        onDismiss = { showRecommendSheet = false },
        // 分享完不主动关气泡：用户从系统面板回来后还在这儿，可以接着复制链接
        onShareApk = shareApk,
    )
}

// ═══ 更新 ═══
//
// 这三项改造前**分散在三个不同的组**里（自动检查更新在「功能开关」、
// 检查更新与更新日志各自在「关于」）。聚到一页之后，
// "我要更新"这一个念头只需要进一页。
@Composable
private fun UpdateContent(
    container: AppContainer,
    onCheckUpdate: () -> Unit,
    onChangelog: () -> Unit,
) {
    // ⚠️ 这里**只放与版本/公告有关的东西**（一条链：feed.json）。
    // 「自动更新学校列表与知识库」曾经挂在这一页，名字也叫「自动检查更新」——
    // 结果用户进了「更新」页却看到「学校列表」，以为更新被拆成了两半。
    // 那个开关后来整个删掉了（学校索引在产品入口下不可达，知识库改为启动静默刷新）。
    val appFeedStore = remember { container.appFeedStore }
    var backgroundCheck by remember { mutableStateOf(appFeedStore.backgroundEnabled()) }
    var channel by remember { mutableStateOf(appFeedStore.preferredChannel()) }
    var betaEnabled by remember { mutableStateOf(appFeedStore.betaEnabled()) }
    val context = LocalContext.current
    SettingsCard {
        // 照岱宗盒子的做法：这一行不跳页，点了就地弹气泡菜单选。
        SettingsMenuChoiceItem(
            icon = Icons.Filled.Cloud,
            title = "更新渠道",
            subtitle = "先问哪个仓要新版本",
            options = listOf(
                "自动（推荐）" to "auto",
                "Gitee" to "gitee",
                "GitHub" to "github",
            ),
            selected = channel,
            onSelect = {
                channel = it
                appFeedStore.setPreferredChannel(it)
            },
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        // 内测通道：读 feed.json 的 beta 块（比 stable 新的那一版）。
        // 名字刻意不叫「测试版」也不叫「抢先版」—— 前者像 bug 多的意思，后者像付费功能。
        SettingsSwitchItem(
            icon = Icons.Filled.BugReport,
            title = "加入内测",
            subtitle = "先拿还没全量发布的版本试；出问题的概率比正式版高，随时可以关掉",
            checked = betaEnabled,
            onToggle = {
                val next = !betaEnabled
                betaEnabled = next
                appFeedStore.setBetaEnabled(next)
                // 两个通道的块本来就在同一份 feed 里，所以不用重新联网 —— 重算一遍渲染就立刻见效
                container.appFeedRepository.reapplyChannel()
            },
            accent = ServiceAccent.PAYMENT,
        )
        SettingsDivider()
        SettingsSwitchItem(
            icon = Icons.Filled.NotificationsActive,
            title = "后台检查版本与公告",
            subtitle = "每 15 分钟检查一次；关掉后只在打开 App 时检查",
            checked = backgroundCheck,
            onToggle = {
                val next = !backgroundCheck
                backgroundCheck = next
                appFeedStore.setBackgroundEnabled(next)
                if (next) AppFeedScheduler.schedule(context) else AppFeedScheduler.cancel(context)
            },
            accent = ServiceAccent.INFO,
        )
        SettingsDivider()
        // 点它就**直接开始查**，不再先弹一个空面板（岱宗的「立即检查更新」就是这样）。
        // 结果：没新版给一句 Toast，有新版直接拉起底部气泡并开始下载。
        // 「正在检查」从仓库状态里读，不再另存一份 —— 免得两处状态对不上。
        val feedState by container.appFeedRepository.state.collectAsStateWithLifecycle()
        SettingsMenuItem(
            icon = Icons.Filled.SystemUpdate,
            title = "检查更新",
            subtitle = if (feedState.checking) "正在检查…" else "查到新版会先问你，再下载",
            onClick = { if (!feedState.checking) onCheckUpdate() },
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.History,
            title = "更新日志",
            subtitle = "查看版本更新记录",
            onClick = onChangelog,
            accent = ServiceAccent.INFO,
        )
    }
}

// ═══ 贡献 ═══
//
// 这一页存在的理由：这是个人项目，没有"客服"，也没有应用商店的评价入口。
// 想帮忙的人需要一条明确的路径 —— 光在「关于」里塞一句"欢迎反馈"是没人看得见的。
@Composable
private fun ContributeContent() {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current

    Text(
        "这个 App 由一个人利用课余时间维护。报 bug、提功能建议、指出文案错误、" +
            "帮忙做适配、把界面翻译成别的语言 —— 都算贡献，没有大小之分。",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )

    SettingsCard {
        SettingsMenuItem(
            icon = Icons.Filled.BugReport,
            title = "提交问题或建议",
            subtitle = "GitHub Issues · 附上报错原文更容易定位",
            onClick = { openUrl(context, GITHUB_ISSUES_URL) },
            accent = ServiceAccent.INFO,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Code,
            title = "参与开发",
            subtitle = "源码在 GitHub，欢迎 fork 与 PR",
            onClick = { openUrl(context, GITHUB_REPO_URL) },
            accent = ServiceAccent.STUDY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Code,
            title = "国内镜像",
            subtitle = "Gitee 仓库（访问更快）",
            onClick = { openUrl(context, GITEE_REPO_URL) },
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Email,
            title = "邮件联系",
            subtitle = "pocketxiaoyan@outlook.com",
            onClick = { openUrl(context, FEEDBACK_MAIL) },
            accent = ServiceAccent.PAYMENT,
        )
    }

    Text(
        "本项目以 GNU GPL v3.0 发布：可以自由使用、修改、再发布，" +
            "但改过的版本也要以同样的许可证开源。从其它 GPL 项目移植来的代码同样遵循该许可证。",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )
}

// ═══ 致谢 ═══
//
// "这份名单放哪儿"曾经是个问题：原先只在「关于」弹窗的副标题里出现一次
// （"版本信息与开源致谢"），点进去只有版本号。GPL 项目要求保留出处，
// 而用户也想知道自己装的东西用了谁的劳动成果 —— 所以单独给一页。
@Composable
private fun ThanksContent() {
    val context = LocalContext.current
    val colors = LocalPocketXiaoliColors.current

    Text(
        "这个 App 站在很多人的肩膀上：有的是直接移植的代码，有的是接口与思路，" +
            "有的是每天在用的数据。列出出处既是感谢，也是 GPL 的要求。",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )

    SettingsSectionHeader("代码与思路")
    SettingsCard {
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "岱宗盒子 · 西安交通大学",
            subtitle = "版本号比较等部分代码移植 · GPL-3.0",
            onClick = {
                openUrl(context, "https://github.com/yeliqin666/xjtu-toolbox-android")
            },
            accent = ServiceAccent.ACADEMIC,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "school-api",
            subtitle = "教务系统接口参考",
            onClick = { openUrl(context, "https://github.com/dairoot/school-api") },
            accent = ServiceAccent.QUERY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "zfn_api",
            subtitle = "正方教务接口整理",
            onClick = { openUrl(context, "https://github.com/openschoolcn/zfn_api") },
            accent = ServiceAccent.STUDY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "拾光课程表",
            subtitle = "教务适配与课程表交互参考 · 超星脚本的课程处理移植（MIT）",
            onClick = {
                openUrl(context, "https://github.com/XingHeYuZhuan/shiguang_warehouse")
            },
            accent = ServiceAccent.LIFE,
        )
    }

    SettingsSectionHeader("数据来源")
    SettingsCard {
        SettingsMenuItem(
            icon = Icons.Filled.Info,
            title = "holiday-cn",
            subtitle = "法定节假日与调休数据",
            onClick = { openUrl(context, "https://github.com/NateScarlet/holiday-cn") },
            accent = ServiceAccent.PAYMENT,
        )
    }

    // 吉祥物不是"随手画的"：三套几何直接取自下面这些项目，MIT / Apache-2.0 都要求保留版权声明。
    // 2026-10-23 审计时发现这一页漏了它们（README 里有、App 里没有）—— 补上。
    SettingsSectionHeader("形象与视觉")
    SettingsCard {
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "DeepSeek-Bot",
            subtitle = "吉祥物 path 与分层数据 · Apache-2.0",
            onClick = { openUrl(context, "https://github.com/Fei2411/DeepSeek-Bot") },
            accent = ServiceAccent.LIFE,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "grok-ball",
            subtitle = "球体轮廓、眼环与表情数据 · MIT",
            onClick = { openUrl(context, "https://github.com/TyCoding/grok-ball") },
            accent = ServiceAccent.ACADEMIC,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "LaoA-GrokBot",
            subtitle = "表情与状态阶梯参考 · MIT",
            onClick = { openUrl(context, "https://github.com/zhulin025/LaoA-GrokBot") },
            accent = ServiceAccent.STUDY,
        )
        SettingsDivider()
        SettingsMenuItem(
            icon = Icons.Filled.Star,
            title = "npface",
            subtitle = "头盔 / 遮阳板 / 方眼几何 · MIT",
            onClick = { openUrl(context, "https://github.com/scccc6ut/npface") },
            accent = ServiceAccent.QUERY,
        )
    }

    SettingsSectionHeader("开源库")
    Text(
        "Jetpack Compose · Material 3 · Room · OkHttp · Gson · Jsoup · " +
            "WorkManager · kotlinx.coroutines · CameraX · ZXing · " +
            "MIUIX（Apache-2.0）· Backdrop（Apache-2.0）",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )
}

// ═══ 危险操作 ═══
//
// 单独成页的收益不只是"少滚动"：这一页有地方把**后果**和**补救办法**写清楚，
// 而挤在设置首页时它只有一行副标题的位置。
//
// ⚠️ 「删除全部本地数据」**必须留在用户够得到的地方**。它原先只存在于开发者实验室
//（「我的 → 关于」连点 7 次解锁），而那是个隐藏入口 —— 隐私政策承诺「可删除」，
// 但普通用户找不到，承诺就不成立。这一行就是把它接回正常路径。
@Composable
private fun DangerContent(
    onClearData: () -> Unit,
    onDeleteAllData: () -> Unit,
) {
    val colors = LocalPocketXiaoliColors.current
    Text(
        "这里的操作不可恢复，删了没有撤销。\n" +
            "建议先回「我的 → 备份与分享」导出一份备份。",
        fontSize = 12.sp,
        color = colors.textTertiary,
        lineHeight = 18.sp,
    )
    SettingsCard {
        DangerSettingsItem(
            title = "清空所有课表数据",
            subtitle = "删除所有课程与自建方案，只保留空的「默认课表」",
            onClick = onClearData
        )
    }
    SettingsCard {
        DangerSettingsItem(
            title = "删除全部本地数据",
            subtitle = "课表、成绩存档、待办、账号凭据、Cookie、崩溃日志——全部清空",
            onClick = onDeleteAllData
        )
    }
}

/**
 * 用系统浏览器打开一个链接。
 *
 * 失败时静默 —— 这一页的每个动作都是"锦上添花"，打不开浏览器不值得弹一个 Toast
 * 打断用户（与首页通知抽屉里的 `openFeedUrl` 同一条取舍）。
 */
private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

