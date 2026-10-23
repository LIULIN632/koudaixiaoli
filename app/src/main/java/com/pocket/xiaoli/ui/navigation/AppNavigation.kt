package com.pocket.xiaoli.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalMascotLook
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.proactive.ChatterFacts
import com.pocket.xiaoli.data.proactive.ProactiveEvaluator
import com.pocket.xiaoli.data.proactive.ProactiveMessage
import com.pocket.xiaoli.data.proactive.ProactiveRules
import com.pocket.xiaoli.ui.proactive.ProactiveBubbleHost
import com.pocket.xiaoli.ui.proactive.ProactiveBubbleView
import com.pocket.xiaoli.ui.home.GlobalSearchScreen
import com.pocket.xiaoli.ui.home.NotificationScreen
import com.pocket.xiaoli.ui.home.HomeScreen
import com.pocket.xiaoli.ui.library.LibraryScreen
import com.pocket.xiaoli.ui.quickanswer.QuickAnswerScreen
import com.pocket.xiaoli.ui.import_.FileImportScreen
import com.pocket.xiaoli.ui.import_.ImportHistoryScreen
import com.pocket.xiaoli.ui.import_.ImportScreen
import com.pocket.xiaoli.ui.import_.PdfImportScreen
import com.pocket.xiaoli.ui.assistant.AssistantChatViewModel
import com.pocket.xiaoli.ui.assistant.AssistantConversationListScreen
import com.pocket.xiaoli.ui.assistant.AssistantAiSettingsScreen
import com.pocket.xiaoli.ui.assistant.AssistantScreen
import com.pocket.xiaoli.ui.map.BuildingMapScreen
import com.pocket.xiaoli.ui.map.CampusMapScreen
import com.pocket.xiaoli.ui.map.CourseNavigationScreen
import com.pocket.xiaoli.ui.map.RoomLayoutScreen
import com.pocket.xiaoli.ui.import_.TimetableImportEntryScreen
import com.pocket.xiaoli.ui.import_.WebImportScreen
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.glass.LocalTextInputFocusReporter
import com.pocket.xiaoli.ui.glass.LocalAppBackdrop
import com.pocket.xiaoli.ui.glass.glassSource
import com.pocket.xiaoli.ui.glass.glassSurface
import com.pocket.xiaoli.ui.glass.rememberAppBackdrop
import com.pocket.xiaoli.ui.profile.ProfileScreen
import com.pocket.xiaoli.ui.service.ServiceWebScreen
import com.pocket.xiaoli.ui.theme.BottomBarStyle
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.timetable.TimetableScreen
import com.pocket.xiaoli.util.DeepLinkDebug
import com.pocket.xiaoli.util.DeepLinkTarget
import com.pocket.xiaoli.ui.treasurebox.TreasureBoxScreen
import com.pocket.xiaoli.ui.weather.WeatherScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.tabContentAlpha
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Immutable
import androidx.navigation.NavBackStackEntry
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.ui.calendar.CampusCalendarScreen
import com.pocket.xiaoli.ui.exam.ExamScheduleScreen
import com.pocket.xiaoli.ui.focus.FocusScreen
import com.pocket.xiaoli.ui.overlay.AppOverlayHostState
import com.pocket.xiaoli.ui.overlay.AppOverlayLayer
import com.pocket.xiaoli.ui.overlay.LocalAppOverlayHost
import com.pocket.xiaoli.ui.update.AppUpdatePromptController
import com.pocket.xiaoli.ui.update.AppUpdateSheetHost

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * 顶栏是「液态玻璃」的路由：它们自己把顶栏画到状态栏底下。
 *
 * 用到的三处：`ui/assistant/AssistantScreen.kt`、`AssistantConversationListScreen.kt`、
 * `AssistantAiSettingsScreen.kt`，都是 `CenterAlignedTopAppBar` + `glassTopBarSurface`。
 *
 * ⚠️ 新增一条这样的页面时要往这里加，否则它的顶栏会被路由层的 padding 顶到状态栏下面，
 * 玻璃就只铺到状态栏下沿、上面仍压一条纯色带（这正是 2026-10-19 用户提的「不统一」）。
 */
private val GLASS_TOP_ROUTES = setOf("assistant", "assistant_history", "assistant_ai_settings")

/**
 * 自己接管状态栏的整页路由。
 *
 * 这些页面不吃路由层让出的顶部安全区，而是**把顶栏背景一直画到状态栏底下**，
 * 再在顶栏内部给内容让位（`statusBarsPadding()` 或页面自己的 Scaffold/TopAppBar）。
 *
 * - 鲤鱼三条：玻璃顶栏，见 [GLASS_TOP_ROUTES]。
 * - `timetable`：课表顶栏是整条彩色渐变，若从状态栏下沿才开始，上面会留一条
 *   纯 `surfaceBase` 的色带（2026-10-20 用户提的「沉浸式状态栏」）。
 *   它内部唯一的本地子页 `SemesterSettingsPage` 自带 Scaffold + TopAppBar，
 *   顶部 inset 不再被路由层消费后，它会自动把顶栏画到状态栏底下。
 * - `profile`：「我的」页没有统一顶栏，整页从身份卡起，底色与顶部的彩晕都要铺到
 *   状态栏底下（`ProfileHomeContent` 自己在内部让位）；设置页走 MIUIX 壳的接管模式
 *   （`MiuixSettingsScaffold(manageStatusBar = true)`）。它那十几个整页子页
 *   （时段管理 / 开发者实验室 / 校园卡 / 网页…）全部自带 Scaffold + TopAppBar，
 *   顶部额度不再被消费后会自己把顶栏画到状态栏底下。
 * - `home` / `services`：首屏头部（时钟 + 搜索 / 服务搜索）外面包了一层彩晕，
 *   同样要一直画到状态栏底下，内容由页面自己的 `statusBarsPadding()` 让位。
 *
 * 其余页面维持路由层统一让位的旧口径：四十多个子页面里总有不自带 Scaffold 的，
 * 撤掉那份额度会让它们钻到状态栏下面。
 */
private val SELF_MANAGED_TOP_ROUTES =
    GLASS_TOP_ROUTES + setOf("timetable", "profile", "home", "services")

private val bottomNavItems = listOf(
    BottomNavItem("home", "首页", Icons.Outlined.Home),
    BottomNavItem("timetable", "课表", Icons.Outlined.CalendarMonth),
    BottomNavItem("assistant", "鲤鱼", Icons.Outlined.AutoAwesome),
    BottomNavItem("services", "服务", Icons.Outlined.GridView),
    BottomNavItem("profile", "我的", Icons.Outlined.Person)
)

@Composable
fun AppNavigation(initialDeepLink: String? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTabRoute = bottomNavItems.any { it.route == currentDestination?.route }
    val colors = LocalPocketXiaoliColors.current
    val lastImport by container.lastImport.collectAsStateWithLifecycle()

    // ── 更新提示：全 App 唯一的开关与抽屉 ──
    //
    // 挂在这一层而不是「我的」页里：更新发出来时用户可能停在任何 tab，
    // 只有放在导航层才谈得上「主动弹」。手动检查（设置页）与自动提示共用这一份，
    // 下载 / 校验 / 安装也就不存在第二套实现。
    val feedState by container.appFeedRepository.state.collectAsStateWithLifecycle()
    val updatePrompt = remember {
        AppUpdatePromptController(
            context = context.applicationContext,
            repository = container.appFeedRepository,
            checker = container.appUpdateChecker,
        )
    }
    // 只在**一级 Tab**上自动弹：更新提示不该盖住专注计时、网页登录、导入向导这些
    // 正在办事的页面；切回 tab 时 feedState 或 isTabRoute 会变化，自然会再试一次。
    LaunchedEffect(feedState, isTabRoute) {
        if (isTabRoute) updatePrompt.autoShow(feedState)
    }
    // 前后台切换：退到后台就把「本次已弹过」清零，回到前台重新判断。
    // 只靠上面那个 LaunchedEffect 覆盖不到这种情况 —— 用户按 Home 再回来时
    // feedState 与 isTabRoute 都不变，抽屉就再也不会出现。
    val tabRouteNow = rememberUpdatedState(isTabRoute)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, updatePrompt) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> updatePrompt.onBackground()
                Lifecycle.Event.ON_START -> if (tabRouteNow.value) {
                    updatePrompt.autoShow(container.appFeedRepository.state.value)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 底栏样式：放 SharedPreferences（标量配置不该为它升 Room 版本）。
    // 用 StateFlow 而不是直接读属性：设置页改完必须立刻反映到底栏，
    // 而 prefs 的 getter 不会触发重组（这个坑在会话列表的 currentConversationId 上踩过）。
    val bottomBarStyle by container.bottomBarStyleStore.observe()
        .collectAsStateWithLifecycle(initialValue = container.bottomBarStyleStore.selected)

    // ── 助手会话：状态上提到导航层 ──
    // 鲤鱼页与「历史对话」是两个导航目标，各自 `viewModel()` 会拿到**两个不同实例**
    // （nav 的 ViewModelStore 是按 back stack entry 划分的）。那样在列表里点了某条对话，
    // 回到鲤鱼页看到的还是旧内容。把 ViewModel 提到这里，两页共用同一实例，
    // 列表页的操作直接就作用在鲤鱼页上 —— 也就不需要任何跨页回调或事件总线。
    val assistantViewModel: AssistantChatViewModel = viewModel(
        factory = AssistantChatViewModel.Factory(container),
    )
    val assistantState by assistantViewModel.state.collectAsStateWithLifecycle()
    val assistantConversations by assistantViewModel.conversations()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val currentConversationId = assistantViewModel.currentConversationId
    /**
     * 各根 Tab 里**用本地状态承载的子页**是否开着 —— key 是根 route。
     *
     * ## 为什么必须是 Map，而不是一个 Boolean
     *
     * 有三个根 Tab 把子页放在本地状态里（不走导航路由：它们是同一批数据的不同视图，
     * 起路由反而要各自重建 ViewModel）：
     *
     * | Tab | 子页 | 上报方 |
     * |---|---|---|
     * | 课表 | 学期设置页 | `TimetableScreen` |
     * | 服务 | 成绩/图书馆/空教室/笔记 … 十余个 | `TreasureBoxScreen` |
     * | 我的 | 设置/更新日志/开发者实验室 … | `ProfileScreen` |
     *
     * 原先只有一个共享的 `var isSubPage`，三个 Tab 都往里写 —— **谁最后写谁赢**。
     * 已复现的后果：课表 → 开学期设置（写 true，底栏藏）→ **不关它**直接点底栏切到
     * 「服务」→ 课表那个 Composable 被销毁 → 服务页自己报的 false 又被课表的 true 盖掉
     * → **底栏永久消失**，只能靠正在打开的子页自带的返回按钮救回来。
     *
     * 改成按 Tab 分槽后，"课表说自己在子页"这件事只影响课表 tab；切走时天然清空，
     * 切回来会重新上报。串线在结构上不可能发生。
     *
     * ⚠️ 保留一条纪律：**上报的必须是"有没有子页开着"这个完整布尔**，
     * 不是"某个子页开关的值"。`TimetableScreen` 曾经直接上报 `showSemesterSettings`，
     * 只有一个子页时恰好等价，多一个子页就会「子页开着却显示底栏」。
     * 完整的正例见 `TreasureBoxScreen` 的 `subPageVisible`。
     */
    val subPageByTab = remember { mutableStateMapOf<String, Boolean>() }

    /** 当前所在的路由模板（`currentDestination?.route`）。根 Tab 都不带参，所以等于 route 本身。 */
    val currentRouteTemplate = currentDestination?.route

    /** 当前根 Tab 是否有子页开着 —— 底栏唯一依据。 */
    val hasOpenSubPage = subPageByTab[currentRouteTemplate] == true

    /**
     * 上报某个根 Tab 的子页可见性。
     *
     * [tabRoute] 由调用点写死（`"timetable"` / `"services"` / `"profile"`），**不用**
     * `currentRouteTemplate` —— 那个值在回调真正执行时可能已经变成别的目的地了，
     * 按它归档会把 A 页的状态记到 B 页头上，比原来的共享布尔更难查。
     */
    fun reportSubPage(tabRoute: String, visible: Boolean) {
        if (subPageByTab[tabRoute] != visible) subPageByTab[tabRoute] = visible
    }

    val gson = remember { com.google.gson.Gson() }

    fun switchTab(item: BottomNavItem) {
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // ── 主动气泡：只算文案，不管展示 ──
    //
    // 算完塞进 `ProactiveBubbleHost`，展示位是底栏正上方那颗气泡（见下面的底栏浮层）。
    //
    // ## 为什么挂在**导航层**，而不是首页或鲤鱼页
    //
    // 两个页面都可能不被组合：首页/鲤鱼是 tab，切走就销毁。挂在它们身上意味着
    // "用户不点一次首页，提醒一次都不会跑"。而这个循环要在**任何 tab 下**都成立。
    //
    // ## 为什么是循环，而不是只在启动时算一次
    //
    // 余额变化、临近上课、新成绩落库都发生在运行期。只算一次的表现是"冒过一次以后再也不冒了"。
    // 真正的节流交给 `ProactiveRules` 里的冷却判断（那部分是纯函数，有单测）。
    //
    // 数据全部读本机已有数据，**不为提醒发任何请求**（见 `ProactiveEvaluator` 的类注释）。
    LaunchedEffect(Unit) {
        delay(ProactiveRules.FIRST_DELAY_MS)
        while (true) {
            // 单轮失败不能弄死循环：evaluator 要读 Room，一次异常不该让提醒永久哑掉
            val message = runCatching { ProactiveEvaluator.evaluate(container) }.getOrNull()
            if (message != null &&
                ProactiveBubbleHost.message == null &&
                !ProactiveBubbleHost.autoSuppressed
            ) {
                // 先记账（含"冒过即消费信号"），再交给展示位
                ProactiveEvaluator.consume(container.proactiveStore, message)
                ProactiveBubbleHost.message = message
            }
            delay(ProactiveRules.EVAL_INTERVAL_MS)
        }
    }

    // 用户正待在鲤鱼页时不让它自动冒泡：人已经在跟它聊了，
    // 再让它从底栏探出头说句话，既遮输入框也很怪。**只挡自动那一路**。
    // 顺带把"戳它要说的话"清掉 —— 那句话只服务于鲤鱼页的空态欢迎页。
    LaunchedEffect(currentRouteTemplate) {
        val onAssistant = currentRouteTemplate == "assistant"
        ProactiveBubbleHost.autoSuppressed = onAssistant
        if (!onAssistant) ProactiveBubbleHost.heroLine = null
    }

    /**
     * 点底栏 tab。除了切页，还负责「戳一下鲤鱼」这件事。
     *
     * 戳它的回话**不从底栏冒泡**（气泡会压住输入框），而是交给鲤鱼页空态的那句问候：
     * 首屏读 `ProactiveBubbleHost.heroLine` 替换副标题。这样两处不会同时各说一句。
     *
     * ⚠️ 这里只给得出"通用句"（拿不到"今天几节课"这类需要查库的事实）：
     * 点击是同步的，而事实要 suspend 读 —— 为它把课表读一遍不值得，
     * 于是 `ChatterFacts()` 留空（池子里依赖事实的句子自然不参与）。
     */
    fun onSelectTab(item: BottomNavItem) {
        if (item.route == "assistant") {
            val host = ProactiveBubbleHost
            if (host.message?.id == ProactiveRules.CHATTER_ID) host.clear()
            val store = container.proactiveStore
            val line = ProactiveRules.pickOnTap(
                level = store.level.value,
                now = java.time.LocalDateTime.now(),
                recent = store.recentChatter(),
                facts = ChatterFacts(),
            )
            if (line != null) {
                store.markTapped(line.id)
                line.chatterLineId?.let(store::rememberChatterLine)
                host.heroLine = line.text
            }
        }
        switchTab(item)
    }

    /**
     * 点了气泡本体：按它带来的去向走。
     *
     * [ProactiveRules.markUseful] 的语义是"这条有用" —— 用户点开了，就把它的退避清零，
     * 下次不必等那么久。
     */
    fun openProactive(message: ProactiveMessage) {
        container.proactiveStore.markUseful(message.id)
        ProactiveBubbleHost.clear()
        val route = message.openRoute
        if (!route.isNullOrBlank()) {
            val tab = bottomNavItems.firstOrNull { it.route == route }
            if (tab != null) switchTab(tab) else navController.navigate(route)
            return
        }
        // 带一句话进对话：**先开新会话**再发。不新建会话的话会接在上一次的对话尾巴上，
        // 用户看到的就是"老对话"，跟刚才气泡说的事毫无关系。
        if (message.prompt.isNotBlank() &&
            assistantState.isConfigured &&
            !assistantState.isStreaming
        ) {
            assistantViewModel.startNewConversation()
            // 快照只给模型看：它说明"点的是哪件事"，不进聊天气泡
            assistantViewModel.send(message.prompt, annex = message.eventSnapshot)
        }
        switchTab(bottomNavItems[2])
    }

    /** 用户按掉了气泡：这条规则的冷却翻倍（下次更晚再提）。 */
    fun dismissProactive(message: ProactiveMessage) {
        container.proactiveStore.markDismissed(message.id)
        ProactiveBubbleHost.clear()
    }

    LaunchedEffect(initialDeepLink) {
        val raw = initialDeepLink ?: return@LaunchedEffect
        when (val target = DeepLinkDebug.resolve(raw)) {
            is DeepLinkTarget.Route -> {
                when (target.routeKey) {
                    "tab" -> {
                        val tab = bottomNavItems.firstOrNull { it.route == target.args["tab"] }
                        if (tab != null) switchTab(tab)
                    }
                    "import_entry" -> navController.navigate("import_entry")
                    "import_history" -> navController.navigate("import_history")
                    "assistant" -> navController.navigate("assistant")
                    "focus" -> navController.navigate("focus")
                    "campus_map" -> navController.navigate(
                        "campus_map?q=" + Uri.encode(target.args["q"].orEmpty())
                    )
                    "room_layout" -> navController.navigate(
                        "room_layout?q=" + Uri.encode(target.args["q"].orEmpty())
                    )
                    "building_map" -> navController.navigate(
                        "building_map?name=" + Uri.encode(target.args["name"].orEmpty())
                    )
                    "service_web" -> navController.navigate(
                        "service_web?url=" + Uri.encode(target.args["url"].orEmpty()) +
                            "&title=" + Uri.encode(target.args["title"].orEmpty())
                    )
                }
            }
            is DeepLinkTarget.External -> {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)))
                }.onFailure {
                    Toast.makeText(context, "没有可打开该链接的应用", Toast.LENGTH_SHORT).show()
                }
            }
            is DeepLinkTarget.Invalid -> {
                Toast.makeText(context, target.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }


    // 消费「从任意页面请求跳到某个 Tab」的信箱（如「我的」页 → 鲤鱼分析日志）。
    // 与鲤鱼页消费分析请求同理：nonce 保证只处理一次，避免重进首页时又跳一遍。
    val pendingNav by container.pendingNav.collectAsStateWithLifecycle()
    var handledNavNonce by remember { mutableStateOf(0L) }
    LaunchedEffect(pendingNav?.nonce) {
        val request = pendingNav ?: return@LaunchedEffect
        if (request.nonce <= handledNavNonce) return@LaunchedEffect
        handledNavNonce = request.nonce
        container.consumeNav()
        bottomNavItems.firstOrNull { it.route == request.route }?.let { switchTab(it) }
    }

    // 底栏**栏体**高度（不含系统手势区），由底栏自己上报。
    // 不写死成常量的原因：鲤鱼 tab 的图标比其它 tab 大一号，栏体高度会随当前 tab 变化。
    var bottomBarBodyPx by remember { mutableIntStateOf(0) }
    val bottomBarInset = with(LocalDensity.current) { bottomBarBodyPx.toDp() }

    // 底栏显隐：规则、历史坑（"收起键盘后输入框仍有焦点"）与通道都在
    // ui/navigation/BottomBarVisibility.kt —— **改这里之前先读那个文件。**
    val bottomBar = rememberBottomBarState(hasOpenSubPage, isTabRoute)

    // ── 玻璃的采样源 ──
    // 只在真的选了玻璃时才录：录制会把整棵内容多画一遍到独立图层，不选的人不该付这个代价。
    val glassEnabled = bottomBarStyle == BottomBarStyle.LIQUID_GLASS
    val appBackdrop = rememberAppBackdrop()
    val samplingBackdrop = if (glassEnabled) appBackdrop else null

    // 页内浮层（弹窗）的宿主。设计取舍见 ui/overlay/AppOverlay.kt 的类注释：
    // 系统窗口会被 ROM 加背景模糊，页内浮层则让背景保持原样。
    val overlayHost = remember { AppOverlayHostState() }

    Scaffold(
        containerColor = colors.surfaceBase,
        // ⚠️ 底栏**故意不放进 Scaffold 的 bottomBar 槽**。
        //
        // 那个槽会把内容整体顶到栏的上方，内容永远到不了栏背后 —— 玻璃的采样/折射源就是空的，
        // `lens` 不管怎么调都折不出东西，只会退化成一块色块（这条在本项目上已经实证过一次）。
        //
        // 仍然保留 Scaffold，是为了继续给 40+ 个子页面提供状态栏 / 导航栏安全区（下面的 padding）。
        bottomBar = {},
    ) { padding ->
        // 顶栏画到状态栏底下的路由**自己接管**顶部安全区（见 [SELF_MANAGED_TOP_ROUTES]）：
        // 只有它们不吃顶部那份额度，顶栏背景才铺得到状态栏底下；其余 40+ 子页面照旧。
        //
        // 另一半同样重要：`consumeWindowInsets(hostPadding)`。开了边到边之后，
        // 子页面里的 Scaffold/TopAppBar 会**重新**按窗口 inset 再让一次，
        // 结果是顶栏整体下移一条状态栏的高度（页面看起来被顶了两遍）。
        // 消费掉我们这一层已经让出的那部分，子页面看到的就是 0，排版与开边到边之前一致。
        //
        // ⚠️ 自己接管的那几条**只消费自己吃掉的份额**：顶部留 0，子页面看到的仍是
        // 真实的状态栏 inset，它的 Scaffold/TopAppBar（如学期设置页）才会自己补上。
        val selfManagedTopRoute = currentDestination?.route in SELF_MANAGED_TOP_ROUTES
        val hostPadding = if (selfManagedTopRoute) {
            val layoutDirection = LocalLayoutDirection.current
            PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
        } else {
            padding
        }
        Box(Modifier.fillMaxSize()) {
            // 鲤鱼**当前形象**（形状 + 本色）：全 App 的 `AiAvatar` 与底栏那格 AI 图标都从这里读。
            //
            // ⚠️ 这个 provider 必须**把底栏浮层也包进来**。底栏是叠在内容之上的独立 Box
            //（不在 Scaffold 的 `bottomBar` 槽里，见下面 750 行附近的注释），曾经只包住 `NavHost`
            //  → 底栏读不到、永远显示默认的鲤鱼/蓝色，用户换成幽灵之后对话头像变了、底栏没变。
            //
            // 为什么在路由这一层 provide 而不是在 `AiAvatar` 内部读 store：
            // 头像散在四个文件里（对话气泡 / 空态欢迎页 /「我的」徽标 / 设置页预览），
            // 让它们各自去拿 DI 容器会把 `ui/theme` 拖成依赖 `di` 的反向依赖
            //（`AgentPhase` 当初搬出这个文件就是为了这个）。
            //
            // ⚠️ 收 Flow，不是读 store 的属性：属性读的是内存快照、不参与 Compose 快照系统，
            // 改了不会触发重组 —— 这个坑在本项目的会话列表与底栏样式上各踩过一次。
            val mascotAppearance by container.tabAppearanceStore.state.collectAsStateWithLifecycle()

            // 页面据此预留底部空间：= 栏体高度，底栏隐藏时为 0dp（页面不必自己判断）
            CompositionLocalProvider(
                LocalBottomBarInset provides if (bottomBar.visible) bottomBarInset else 0.dp,
                LocalAppBackdrop provides samplingBackdrop,
                LocalTextInputFocusReporter provides bottomBar.reportFocus,
                LocalMascotLook provides mascotAppearance.assistantMascotLook,
                LocalAppOverlayHost provides overlayHost,
            ) {
            NavHost(
            navController = navController,
            startDestination = "home",
                // ⚠️ 只录 NavHost，**不录底栏** —— 采样源录到自己会成环，
                // 那是 RenderThread 级的 SIGSEGV（原生崩溃，catch 不住）。见 AppBackdrop.kt。
                // 边到边之后窗口不再随键盘变矮（`adjustResize` 在 `setDecorFitsSystemWindows(false)` 下失效），
                // 所以由这里替它让位：imePadding 加在路由层的容器上，等价于原来的「窗口被键盘挤矮」，
                // 四十多个子页面不用各自写一遍。没开边到边时 ime inset 恒为 0，这行是空操作。
                modifier = Modifier
                    .padding(hostPadding)
                    .consumeWindowInsets(hostPadding)
                    .imePadding()
                    .glassSource(samplingBackdrop),
            // 转场按「是否切换到同级 Tab」区分，见文件末尾 routeTransitions() 的说明
            enterTransition = { routeTransitions(initialState, targetState).enter },
            exitTransition = { routeTransitions(initialState, targetState).exit },
            popEnterTransition = { routeTransitions(initialState, targetState).popEnter },
            popExitTransition = { routeTransitions(initialState, targetState).popExit },
        ) {
            composable("home") {
                HomeScreen(
                    onOpenTimetable = { switchTab(bottomNavItems[1]) },
                    onOpenCampusMap = { navController.navigate("campus_map") },
                    onNavigateToRoom = { room -> navController.navigate("course_navigation?q=" + Uri.encode(room)) },
                    onOpenServices = { switchTab(bottomNavItems[3]) },
                    onOpenProfile = { switchTab(bottomNavItems[4]) },
                    onOpenGlobalSearch = { navController.navigate("global_search") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onOpenWeather = { navController.navigate("weather") },
                    // 一键起钟：**先把钟起上再跳**。钟的真源是 FocusController
                    // （快照 + 前台服务），不在页面上 —— 所以"跳过去"只是去看它，
                    // 起钟本身必须在这里完成，否则用户点完返回首页就什么都没发生。
                    // `startNew` 要落库/写快照，是挂起函数，丢给导航层这个 scope。
                    onStartFocus = { id, title ->
                        scope.launch { container.focusController.startNew(id, title) }
                        navController.navigate("focus")
                    }
                )
            }

            composable("timetable") {
                TimetableScreen(
                    onSubPageChange = { reportSubPage("timetable", it) },
                    onOpenImport = { navController.navigate("import_entry") },
                    onOpenCampusMap = { room -> navController.navigate("campus_map?q=" + Uri.encode(room)) },
                    onOpenRoomLayout = { room -> navController.navigate("room_layout?q=" + Uri.encode(room)) },
                    onOpenCourseNavigation = { room -> navController.navigate("course_navigation?q=" + Uri.encode(room)) },
                )
            }

            composable("import_entry") {
                TimetableImportEntryScreen(
                    onBack = { navController.popBackStack() },
                    onWebImport = { navController.navigate("import_web") },
                    onDirectImport = { navController.navigate("import_direct") },
                    onFileImport = { navController.navigate("import_file") },
                    lastImport = lastImport,
                    onOpenHistory = { navController.navigate("import_history") },
                    lockedSchoolName = container.currentCampusSchool()?.name,
                    boundStudentId = container.currentCampusSchool()?.baseUrl?.let { base ->
                        container.loadCredential(base) ?: container.loadCredential("campus_bind")
                    }?.first,
                    onOpenTimetable = { navController.popBackStack("timetable", inclusive = false) },
                )
            }

            composable("import_file") {
                FileImportScreen(
                    onBack = { navController.popBackStack() },
                    onPdfImport = { navController.navigate("import_pdf") },
                    onImported = { navController.popBackStack("timetable", inclusive = false) },
                )
            }

            composable("import_pdf") {
                PdfImportScreen(onBack = { navController.popBackStack() })
            }

            composable("import_history") {
                ImportHistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("import_web") {
                WebImportScreen(onBack = { navController.popBackStack() })
            }

            composable("import_direct") {
                ImportScreen(onBack = { navController.popBackStack() })
            }

            composable("notifications") {
                val feedState by container.appFeedRepository.state.collectAsStateWithLifecycle()
                val notificationScope = rememberCoroutineScope()
                NotificationScreen(
                    state = feedState,
                    onBack = { navController.popBackStack() },
                    onMarkAllSeen = { container.appFeedRepository.markAllSeen() },
                    onRetry = {
                        notificationScope.launch {
                            container.appFeedRepository.refresh(force = true)
                        }
                    },
                )
            }
            composable("global_search") {
                val selectedCampus by container.selectedCampus.collectAsStateWithLifecycle()
                GlobalSearchScreen(
                    campus = selectedCampus,
                    onBack = { navController.popBackStack() },
                    onOpenRoute = { route ->
                        navController.popBackStack()
                        when (route) {
                            "home" -> switchTab(bottomNavItems[0])
                            "timetable" -> switchTab(bottomNavItems[1])
                            "assistant" -> switchTab(bottomNavItems[2])
                            "services" -> switchTab(bottomNavItems[3])
                            "profile" -> switchTab(bottomNavItems[4])
                            else -> navController.navigate(route)
                        }
                    },
                )
            }
            composable("assistant") {
                AssistantScreen(
                    viewModel = assistantViewModel,
                    state = assistantState,
                    onBack = { switchTab(bottomNavItems[0]) },
                    showBack = false,
                    onOpenSettings = { navController.navigate("assistant_ai_settings") },
                    onOpenHistory = { navController.navigate("assistant_history") },
                )
            }
            composable("assistant_ai_settings") {
                AssistantAiSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable("assistant_history") {
                val blocked: () -> Unit = {
                    Toast.makeText(context, "鲤鱼还在回答，等它说完再操作", Toast.LENGTH_SHORT).show()
                }
                AssistantConversationListScreen(
                    conversations = assistantConversations,
                    currentId = currentConversationId,
                    streaming = assistantState.isStreaming,
                    onBack = { navController.popBackStack() },
                    onOpen = {
                        // 先选中再返回：返回后鲤鱼页就是那段对话了（同一个 ViewModel 实例）
                        assistantViewModel.openConversation(it, onBlocked = blocked)
                        navController.popBackStack()
                    },
                    onDelete = { assistantViewModel.deleteConversation(it, onBlocked = blocked) },
                    onNew = {
                        assistantViewModel.startNewConversation(onBlocked = blocked)
                        navController.popBackStack()
                    },
                )
            }

            composable("exam_schedule") {
                ExamScheduleScreen(onBack = { navController.popBackStack() })
            }

            // 校历页：服务页那一格进来。刻意走命名路由（不用 ServiceSubPage），
            // 这样未来节点的主动气泡能直接用 openRoute 落到这里 ——
            // ServiceSubPage 是服务页内的局部状态，导航层拿不到。
            composable("campus_calendar") {
                CampusCalendarScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenTimetableWeek = { week ->
                        container.requestWeekJump(week)
                        switchTab(bottomNavItems[1])
                    },
                )
            }

            // 天气页：首页时钟旁的胶囊与主动提醒都落到这里；服务页那一格仍走
            // `ServiceSubPage.Weather`（「我的」里嵌的是同一套服务页、没有导航层）。
            // 命名路由与校历同理 —— 气泡的 `openRoute` 只能落在 NavHost 注册过的路由上。
            composable("weather") {
                WeatherScreen(
                    repository = container.weatherRepository,
                    onBack = { navController.popBackStack() },
                )
            }

            // 专注计时（番茄钟）。非 tab 路由 —— 没有导航栏，也不需要
            // `onSubPageChange`：底栏显隐判的是 `isTabRoute`，这里本来就不是 tab。
            // 从深链 `pocketxiaoli://focus` 与「服务页 / 今日待办」两个入口进来。
            composable("focus") {
                FocusScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }

            // 资料站：课件 / 历年卷。非 tab 路由，与「专注计时」同一个形态。
            // 索引与文件都在 Gitee Release 上，所以这一页**没有上传入口**，
            // 只有「拉索引 → 下载 → 交给系统应用打开」三件事。
            composable("library") {
                LibraryScreen(
                    repository = container.libraryRepository,
                    onBack = { navController.popBackStack() },
                )
            }

            // 快问快答：老师每章的提问 + 答案要点。题库先读内置资源，再被远端覆盖。
            composable("quick_answer") {
                QuickAnswerScreen(
                    repository = container.quickAnswerRepository,
                    store = container.quickAnswerStore,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "campus_map?q={q}",
                arguments = listOf(navArgument("q") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                CampusMapScreen(
                    initialQuery = entry.arguments?.getString("q").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = "room_layout?q={q}",
                arguments = listOf(navArgument("q") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                RoomLayoutScreen(
                    container = container,
                    initialRoomQuery = entry.arguments?.getString("q").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "course_navigation?q={q}",
                arguments = listOf(navArgument("q") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                CourseNavigationScreen(
                    container = container,
                    initialRoomQuery = entry.arguments?.getString("q").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenRoomLayout = { room -> navController.navigate("room_layout?q=" + Uri.encode(room)) },
                )
            }
            composable(
                route = "service_web?url={url}&title={title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                    navArgument("title") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                ServiceWebScreen(
                    url = entry.arguments?.getString("url").orEmpty(),
                    title = entry.arguments?.getString("title").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "building_map?name={name}",
                arguments = listOf(navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { entry ->
                BuildingMapScreen(
                    buildingName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenRoom = { room ->
                        navController.navigate("room_layout?q=" + Uri.encode(room))
                    },
                    onOpenManage = { navController.navigate("room_layout") }
                )
            }

            composable("services") {
                val schemeId by container.activeSchemeId.collectAsStateWithLifecycle()
                TreasureBoxScreen(
                    onBack = { switchTab(bottomNavItems[1]) },
                    onImportCourse = { courseName, teacher, room, day, startSlot, endSlot ->
                        val course = CourseEntity(
                            name = courseName,
                            teacher = teacher,
                            room = room,
                            dayOfWeek = day,
                            startSlot = startSlot,
                            endSlot = endSlot,
                            schemeId = container.activeSchemeId.value,
                            colorIndex = (day * 3 + startSlot) % 15
                        )
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                app.repository.insert(course)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "$courseName 已导入课表！",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    repository = app.repository,
                    schemeId = schemeId,
                    zhengfangClient = container.zhengfangClient,
                    schoolRegistry = container.schoolRegistry,
                    appContainer = container,
                    asTab = true,
                    onSubPageChange = { reportSubPage("services", it) },
                    // 推广横幅的 App 内跳转。白名单只放行 services / timetable / home，
                    // 正好都是底栏 tab —— 所以直接复用 switchTab，不认识的路由静默不跳。
                    onOpenPromoRoute = { route ->
                        bottomNavItems.firstOrNull { it.route == route }?.let { switchTab(it) }
                    },
                    onOpenCampusMap = { navController.navigate("campus_map") },
                    onOpenAssistant = { switchTab(bottomNavItems[2]) },
                    onOpenRoomLayout = { navController.navigate("room_layout") },
                    onOpenExamSchedule = { navController.navigate("exam_schedule") },
                    onOpenCalendar = { navController.navigate("campus_calendar") },
                    onOpenFocus = { navController.navigate("focus") },
                    onOpenLibrary = { navController.navigate("library") },
                    onOpenQuickAnswer = { navController.navigate("quick_answer") },
                    onOpenServiceWeb = { url, title -> navController.navigate("service_web?url=" + Uri.encode(url) + "&title=" + Uri.encode(title)) },
                )
            }

            composable("profile") {
                // 「我的」页各行的垂直内边距来自「外观 → 我的 → 列表密度」。
                //
                // ⚠️ 在这一层 provide，而不是在 ProfileScreen 内部：那里面有二十来个早返回
                //（每个二级页独占一屏），在它内部包一层得把三百多行整体重新缩进；
                // 而路由这一层只包住一次调用，且天然覆盖所有二级页
                //（绑定信息 / 设置 / 更新日志 / 开发者实验室…）。
                val profileTabAppearance by container.tabAppearanceStore.state.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalProfileRowPadding provides profileTabAppearance.profileRowVerticalPadding.dp
                ) {
                    ProfileScreen(
                        onSubPageChange = { reportSubPage("profile", it) },
                        onOpenImportEntry = { navController.navigate("import_entry") },
                        // 「外观 → 鲤鱼」那个分类只是个指针页，真正的设置在鲤鱼页的 AI 设置里。
                        // 只需要 navigate，**不要**先 switchTab：切 tab 会让"返回"落到鲤鱼页，
                        // 而用户是从「我的」过来的，原路返回才是他要的。
                        onOpenAiSettings = { navController.navigate("assistant_ai_settings") },
                        // 「我的」页四格里的「专注计时」磁贴 → 专注页（非 tab 路由，
                        // 与从服务页进的是同一个页面，所以只 navigate、不复制一份入口）。
                        onOpenFocus = { navController.navigate("focus") },
                        onCheckUpdate = { updatePrompt.checkNow(scope) },
                        onBack = { switchTab(bottomNavItems[0]) }
                    )
                }
            }
            }

            // ── 底栏浮层 ──
            // 叠在内容之上，而不是占 Scaffold 的槽位：内容因此能从栏背后滚过去，
            // 玻璃（以及将来的折射）才有采样源。
            if (bottomBar.visible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    // 外层让出系统手势区；里层才是真正的"栏体"，
                    // 所以这里量到的高度就是页面要预留的高度（不含手势区，避免与 Scaffold 的 padding 叠加）
                    //
                    // ⚠️ 这两层现在被一个 Column 包住：气泡是栏体的**上邻居**，
                    // 位置由布局自己得出（而不是手算"底栏高度 + 导航条 inset"那种做法 ——
                    // 换成胶囊底栏就会错位），绘制层级也天然在最上。
                    Box(Modifier.navigationBarsPadding()) {
                        Column {
                            // 主动气泡：零高度的展示位，见 ProactiveBubbleSlot 的注释
                            ProactiveBubbleSlot(
                                onOpen = { openProactive(it) },
                                onDismiss = { dismissProactive(it) },
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { bottomBarBodyPx = it.height }
                            ) {
                                // 底栏样式可切换（「外观」设置里选）。两种样式共用同一个 BottomBarTab，
                                // 切换时只有外壳变、tab 内容与交互完全一致 —— 避免"换样式导致行为不同"
                                // 玻璃的三档降级在 LiquidBottomBar / glassSurface 内部完成，这里不分流：
                                // 形状与布局三档一致，只是效果层逐级减少。
                                if (glassEnabled) {
                                    GlassBottomBar(
                                        items = bottomNavItems,
                                        currentRoute = currentDestination?.route,
                                        onSelect = { onSelectTab(it) },
                                        backdrop = samplingBackdrop,
                                    )
                                } else {
                                    SoftBottomBar(
                                        items = bottomNavItems,
                                        currentRoute = currentDestination?.route,
                                        onSelect = { onSelectTab(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 页内浮层画在底栏之后：层级压在内容与底栏之上，遮罩能盖住整屏（含底栏）。
            // 它是"背景保持原样"的关键 —— 系统 `Dialog` 会被 ROM 加背景模糊，这里不会。
            AppOverlayLayer(overlayHost)

            // 更新抽屉：自己带一个窗口（WindowBottomSheet），放在组合树里只是为了
            // 跟随导航层存活 —— 任何 tab / 子页上都能弹，不靠某个页面恰好被组合。
            AppUpdateSheetHost(updatePrompt)
            }
        }
    }
}

/**
 * 主动气泡的展示位：底栏正上方的**零高度**槽位。
 *
 * ## 为什么是"零高度 + 往上溢出"
 *
 * 气泡照常测量，但对外宣称自己的高度是 **0**，并把内容画到自己的上边界之外。
 * 不这么做的话，气泡一出现就会把底栏撑高 → 页面预留的底部高度跟着变 → **整页内容往上跳一下**。
 *
 * ## 为什么尖角取"气泡自身水平中心"
 *
 * 五个 tab 里的正中就是「鲤鱼」，而底栏在屏幕上是居中的 —— 尖角居中即正对鲤鱼。
 * 从起始边量固定 dp 的话，文案一长一短尖角就偏出锚点了（参考实现也特意绕开了这个坑）。
 *
 * ## 为什么显示位在底栏这一层，而不是页面里
 *
 * 位置由布局得出（栏体的上邻居），不必手算"底栏高度 + 手势区"；绘制层级也天然盖在内容之上。
 * 用户打字导致底栏被收起时（`bottomBar.visible == false`），整个浮层不渲染，气泡随之消失 ——
 * 这是想要的行为：那时它没有可锚的地方。
 */
@Composable
private fun ProactiveBubbleSlot(
    onOpen: (ProactiveMessage) -> Unit,
    onDismiss: (ProactiveMessage) -> Unit,
) {
    val message = ProactiveBubbleHost.message ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 左右各留 16dp：气泡最宽就是"屏宽 - 32dp"，长文案不至于顶到屏幕边缘
            .padding(horizontal = 16.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                // 与栏体留一条缝，否则气泡的尖角会贴着底栏上沿（看着像粘住了）
                val gap = 4.dp.roundToPx()
                layout(placeable.width, 0) { placeable.place(0, -placeable.height - gap) }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        ProactiveBubbleView(
            message = message,
            onOpen = { onOpen(message) },
            onDismiss = { onDismiss(message) },
            onTimeout = { ProactiveBubbleHost.clear() },
        )
    }
}

@Composable
private fun SoftBottomBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onSelect: (BottomNavItem) -> Unit
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(30.dp),
        color = colors.surfaceCard,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route ||
                    currentRoute?.startsWith("${item.route}/") == true
                val isAssistant = item.route == "assistant"
                // 点按反馈的 source：必须和 clickable 共用，`AssistantTabIcon` 靠它做按压回弹
                val pressSource = remember { MutableInteractionSource() }
                // 按下即变淡（抄 MIUIX 底栏 NavigationBar 的 isPressed tint）：水波纹在
                // 浅色玻璃栏上几乎看不见、而且还要"长"出来；这里是同一帧变淡。鲤鱼格除外，
                // 它有自己的按压回弹（`AssistantTabIcon` 里的 snapTo(0.88f)）。
                val pressed by pressSource.collectIsPressedAsState()
                val contentAlpha = tabContentAlpha(pressed, isAssistant)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // 鲤鱼格只有 40dp 的形象、没有文字，Column 会缩到形象那么高；
                        // 不撑满的话，点格子上下那两截空白是**没有反应**的（用户报过）。
                        .then(if (isAssistant) Modifier.fillMaxHeight() else Modifier)
                        .clip(RoundedCornerShape(skin.radiusLarge))
                        .background(if (selected) colors.surfaceHighlight else Color.Transparent)
                        .clickable(
                            interactionSource = pressSource,
                            indication = LocalIndication.current,
                        ) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(item) }
                        .padding(vertical = if (isAssistant) 0.dp else 8.dp),
                    verticalArrangement = if (isAssistant) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isAssistant) {
                        // 没有可见文字，语义得自己补，否则 TalkBack 只会读"按钮"
                        AssistantTabIcon(
                            selected = selected,
                            interactionSource = pressSource,
                            modifier = Modifier.semantics { contentDescription = item.label },
                        )
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp).alpha(contentAlpha),
                            tint = if (selected) colors.accentMain else colors.textTertiary
                        )
                        Spacer(Modifier.height(skin.spaceTiny))
                    }
                    // 鲤鱼格**不写文字**：它已经是一枚满色形象（岱宗同样只放形象），
                    // 名字在鲤鱼页自己标题里出现，底栏再写一遍就是五格里唯一一个"图标+名字"的重复。
                    if (!isAssistant) {
                        Text(
                            modifier = Modifier.alpha(contentAlpha),
                            text = item.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.accentMain else colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

/** 底栏上的一个 tab。两种底栏样式共用，保证切换样式时只有"外壳"变、内容不变。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomBarTab(
    item: BottomNavItem,
    selected: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp,
    onClick: (BottomNavItem) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val haptic = LocalHapticFeedback.current
    val isAssistant = item.route == "assistant"
    // 点按反馈的 source：和 clickable 共用，形象靠它做按压回弹
    val pressSource = remember { MutableInteractionSource() }
    // 按下即变淡（抄 MIUIX 底栏 NavigationBar 的 isPressed tint）：
    // 水波纹在浅色玻璃栏上几乎看不见、而且还要"长"出来；这里是同一帧变淡。
    // 鲤鱼格除外 —— 它有自己的按压回弹（AssistantTabIcon 里 snapTo(0.88f)）。
    val pressed by pressSource.collectIsPressedAsState()
    val contentAlpha = tabContentAlpha(pressed, isAssistant)

    Column(
        modifier = Modifier
            .weight(1f)
            // 同 SoftBottomBar：形象那格要撑满，否则格子上下空白点了没反应
            .then(if (isAssistant) Modifier.fillMaxHeight() else Modifier)
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (selected) colors.surfaceHighlight else Color.Transparent)
            .clickable(
                interactionSource = pressSource,
                indication = LocalIndication.current,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick(item)
            }
            .padding(vertical = if (isAssistant) 0.dp else 8.dp),
        verticalArrangement = if (isAssistant) Arrangement.Center else Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isAssistant) {
            AssistantTabIcon(
                selected = selected,
                interactionSource = pressSource,
                modifier = Modifier.semantics { contentDescription = item.label },
            )
        } else {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(20.dp).alpha(contentAlpha),
                tint = if (selected) colors.accentMain else colors.textTertiary,
            )
            Spacer(Modifier.height(skin.spaceTiny))
        }
        // 同上：鲤鱼格只有形象，没有文字
        if (!isAssistant) {
            Text(
                modifier = Modifier.alpha(contentAlpha),
                text = item.label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.accentMain else colors.textTertiary,
            )
        }
    }
}

/**
 * 导航转场：**同级 Tab 切换**用淡入淡出，**进出子页**用滑入滑出。
 *
 * 为什么必须区分：手机上的层级感几乎全靠动画建立。如果"切到课表"和
 * "进入校园地图"用同一种过渡，用户就看不出后者是更深的一层，
 * 返回时也不会有"退出去"的直觉。
 *
 * - 切 Tab（两者都是 tab 路由）→ 交叉淡入淡出。同级之间左右滑动是错误暗示。
 * - 进子页（目标不是 tab）→ 从右滑入，表示"更深一层"
 * - 返回（初始不是 tab）→ popEnter/popExit 方向相反，表示"退回上层"
 *
 * 时长取 200ms：比 300ms 的默认值更有"跟手"感，又不至于看着突兀。
 *
 * ⚠️ 本函数**不能**标 `@Composable`：它被 NavHost 的 `enterTransition = { ... }`
 * 这类 lambda 调用，而**那些 lambda 不是组合上下文**
 * （只是普通的 `AnimatedContentTransitionScope.() -> EnterTransition`）。
 * 好在它只读静态的 [bottomNavItems]，不需要 CompositionLocal。
 */
@Immutable
private data class RouteTransition(
    val enter: EnterTransition,
    val exit: ExitTransition,
    val popEnter: EnterTransition,
    val popExit: ExitTransition,
)

private fun routeTransitions(
    initial: NavBackStackEntry?,
    target: NavBackStackEntry?,
): RouteTransition {
    val initialIsTab = bottomNavItems.any { it.route == initial?.destination?.route }
    val targetIsTab = bottomNavItems.any { it.route == target?.destination?.route }

    // 切 Tab：**瞬时切换**，不做淡入淡出。
    //
    // 原先这里是 180ms 交叉淡入（`Motion.expand`），代价是"手指抬起后还要等半拍才看见
    // 新页面"——用户实测描述为"点了像没反应"。层级感由下面子页的滑入建立，不靠这一层淡入。
    if (initialIsTab && targetIsTab) {
        return RouteTransition(
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            popEnter = EnterTransition.None,
            popExit = ExitTransition.None,
        )
    }

    // 进子页：从右滑入；返回：向右滑出（方向相反，才符合"退回去"的直觉）
    val slide = tween<IntOffset>(200)
    val fade = tween<Float>(200)
    return RouteTransition(
        enter = slideInHorizontally(slide) { it / 4 } + fadeIn(fade),
        exit = slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade),
        popEnter = slideInHorizontally(slide) { -it / 6 } + fadeIn(fade),
        popExit = slideOutHorizontally(slide) { it / 4 } + fadeOut(fade),
    )
}
