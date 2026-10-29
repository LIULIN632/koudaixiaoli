package com.pocket.xiaoli.ui.treasurebox

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HotTub
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.HotTub
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.PriceCheck
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Upgrade
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.courseware.Courseware
import com.pocket.xiaoli.ui.courseware.CoursewareScreen
import com.pocket.xiaoli.data.service.CampusServiceAdapterRegistry
import com.pocket.xiaoli.data.service.CampusCardLauncher
import com.pocket.xiaoli.data.service.CampusServiceRegistry
import com.pocket.xiaoli.data.service.CampusServiceCatalog
import com.pocket.xiaoli.data.service.ServiceSummary
import com.pocket.xiaoli.data.service.ServiceSummaryState
import com.pocket.xiaoli.data.service.SchoolServiceLauncher
import com.pocket.xiaoli.data.model.DormServiceRouter
import com.pocket.xiaoli.data.model.DormInfo
import com.pocket.xiaoli.data.service.CampusServiceResolver
import com.pocket.xiaoli.data.service.CampusServiceCapability
import com.pocket.xiaoli.data.SchoolRegistry
import com.pocket.xiaoli.data.repository.CourseRepository
import com.pocket.xiaoli.data.promo.PromoSlot
import com.pocket.xiaoli.data.promo.PromoAction
import com.pocket.xiaoli.telemetry.Telemetry
import com.pocket.xiaoli.ui.service.PromoBanner
import com.pocket.xiaoli.di.AppContainer
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.ui.service.LibraryScreen
import com.pocket.xiaoli.ui.service.ExpressPickupOverlay
import com.pocket.xiaoli.ui.service.PromoViewModel
import com.pocket.xiaoli.ui.service.ServiceInfoLink
import com.pocket.xiaoli.ui.service.ServiceInfoPlace
import com.pocket.xiaoli.ui.service.ServiceInfoPromo
import com.pocket.xiaoli.ui.service.ServiceInfoScreen
import com.pocket.xiaoli.ui.service.ServiceInfoSection
import com.pocket.xiaoli.ui.service.TodayCampusScreen
import com.pocket.xiaoli.ui.notes.NoteListScreen
import com.pocket.xiaoli.ui.seat.LibrarySeatScreen
import com.pocket.xiaoli.ui.electricity.ElectricityScreen
import com.pocket.xiaoli.ui.weather.WeatherScreen
import com.pocket.xiaoli.ui.card.CampusCardScreen
import com.pocket.xiaoli.ui.courseselect.CourseSelectScreen
import com.pocket.xiaoli.network.ZhengfangClient
import com.pocket.xiaoli.ui.components.campusui.CampusSearchBar
import com.pocket.xiaoli.ui.components.CardAccountDialog
import com.pocket.xiaoli.ui.components.campusui.rememberCampusScrollState
import com.pocket.xiaoli.ui.components.campusui.CampusSegmentedTabs
import com.pocket.xiaoli.ui.components.campusui.CampusSectionHeader
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.PocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.assistant.copyToClipboard
import com.pocket.xiaoli.ui.secondclass.SecondClassEntryScreen
import com.pocket.xiaoli.ui.secondclass.SecondClassEntryState
import com.pocket.xiaoli.ui.theme.miuixPress
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.outlined.EventNote

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "未找到可用浏览器", Toast.LENGTH_SHORT).show()
    }
}

private fun openCampusJw(context: android.content.Context, campus: CampusInfo, fallback: () -> Unit) {
    val url = campus.jwBaseUrl
    if (url.isNullOrBlank()) fallback() else openUrl(context, url)
}

private fun openCampusPortal(context: android.content.Context, campus: CampusInfo, fallback: () -> Unit) {
    val url = campus.portalUrl ?: campus.jwBaseUrl
    if (url.isNullOrBlank()) fallback() else openUrl(context, url)
}

private fun openMap(context: android.content.Context, campusName: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(campusName)))
        )
    } catch (_: Exception) {
        openUrl(context, "https://uri.amap.com/search?keyword=" + Uri.encode(campusName))
    }
}

private fun dormServiceTitle(key: String): String = when (key) {
    CampusConfig.SERVICE_LAUNDRY -> "洗衣"
    CampusServiceKeys.ELECTRICITY -> "宿舍电费"
    CampusServiceKeys.SHOWER -> "洗澡"
    CampusServiceKeys.HOT_WATER -> "热水"
    else -> key
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreasureBoxScreen(
    onBack: () -> Unit,
    onImportCourse: (courseName: String, teacher: String, room: String, day: Int, startSlot: Int, endSlot: Int) -> Unit,
    repository: CourseRepository,
    schemeId: Long,
    zhengfangClient: ZhengfangClient,
    schoolRegistry: SchoolRegistry,
    appContainer: AppContainer,
    /**
     * 推广横幅里 `action.type = "route"` 的跳转出口。
     *
     * 白名单只放行 `services / timetable / home`（见 `PromoValidator.ALLOWED_ROUTES`），
     * 恰好都是底栏 tab 路由 —— 所以导航层直接用现成的 `switchTab` 接，不需要新的导航逻辑。
     * 默认空实现：不认识的路由**静默不跳**（这是安全的默认；白名单已在解析阶段挡过一道）。
     */
    onOpenPromoRoute: (String) -> Unit = {},
    asTab: Boolean = false,
    onSubPageChange: (Boolean) -> Unit = {},
    onOpenCampusMap: (() -> Unit)? = null,
    onOpenAssistant: (() -> Unit)? = null,
    onOpenRoomLayout: (() -> Unit)? = null,
    onOpenExamSchedule: (() -> Unit)? = null,
    onOpenCalendar: (() -> Unit)? = null,
    /** 专注计时（番茄钟）：`focus` 是非 tab 路由，所以要一个显式的跳转出口。 */
    onOpenFocus: (() -> Unit)? = null,
    /** 资料站：课件 / 历年卷，整页列表（非 tab 路由）。 */
    onOpenLibrary: (() -> Unit)? = null,
    /** 快问快答：老师每章的提问 + 答案要点，整页列表（非 tab 路由）。 */
    onOpenQuickAnswer: (() -> Unit)? = null,
    onOpenServiceWeb: ((url: String, title: String) -> Unit)? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedCampus by appContainer.selectedCampus.collectAsStateWithLifecycle()
    val dormInfo by appContainer.dormInfo.collectAsStateWithLifecycle()
    // ⚠️ 必须 collect 而不是读 `store.current`：后者不参与快照系统，拨了开关不会触发重组
    //（与 `TabAppearanceStore` KDoc 里那条"读 prefs 改了不重组"是同一个坑）。
    val tabAppearance by appContainer.tabAppearanceStore.state.collectAsStateWithLifecycle()
    // ⚠️ 必须用 WithLifecycle 版本：`repository.getTasksByScheme(schemeId)` 是 Room 生成的
    // **冷 Flow**，写在参数里意味着每次重组都新建一个实例 + 向 InvalidationTracker 注册
    // 一个新观察者。Compose 会把新实例当成新 source 而重启收集，旧的若没及时释放就会累积。
    // 服务页是停留时间最长的页面之一，这个模式在这里代价最大。
    val tasks by repository.getTasksByScheme(schemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val taskCompleted = tasks.count { it.completed }
    val taskTotal = tasks.size

    val serviceSummaries = remember { mutableStateMapOf<String, ServiceSummary>() }
    LaunchedEffect(selectedCampus.id, dormInfo) {
        serviceSummaries.clear()
        DormServiceRouter.dormServiceKeys.forEach { key ->
            CampusServiceAdapterRegistry.loadSummary(
                key = key,
                title = dormServiceTitle(key),
                campus = selectedCampus,
                dormInfo = dormInfo,
            )?.let { summary -> serviceSummaries[key] = summary }
        }
    }

    // 皮肤令牌：圆角与间距随「界面风格」切换（Classic 松/大圆角，Modern 紧凑/规整）
    val skin = LocalSkin.current

    // 2026-10：原先这里是 **24 个独立布尔量**（声明 / 汇总 / 复位三处各列一遍，
    // 漏一处 → `BackHandler` 装不上 → 返回键直接退出 App）。现在只有这一个可空值 ——
    // 见 `ServiceSubPage` 的 KDoc。
    var subPage by remember { mutableStateOf<ServiceSubPage?>(null) }
    var serviceQuery by remember { mutableStateOf("") }
    var serviceInfoTitle by remember { mutableStateOf<String?>(null) }
    var serviceInfoProvider by remember { mutableStateOf<String?>(null) }
    var serviceInfoCampusName by remember { mutableStateOf<String?>(null) }

    // ── 推广位（内容来自服务端，见 data/promo）──
    // ViewModel 由 AppContainer 的仓储构造；promos 必须在**这里**收集（而不是在
    // 下面的 forSlot 里读 .value），否则加载完成后不会触发重组
    val promoViewModel: PromoViewModel = viewModel(
        factory = PromoViewModel.Factory(appContainer.promoRepository),
    )
    val promos by promoViewModel.promos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { promoViewModel.loadOnce() }

    fun showServiceInfo(title: String, provider: String? = null, campusName: String? = null) {
        serviceInfoTitle = title
        serviceInfoProvider = provider
        serviceInfoCampusName = campusName
    }

    fun openCampusService(key: String, title: String) {
        // 今日校园：**装了官方 App 就直接拉起**（需求方反馈："还是网页的那种形式，
        // 不是手机 App 那个"）；没装才进我们的入口页 —— 那一页的活儿是
        // 门户登录态、服务直达、初始密码，以及"去应用商店安装"。
        //
        // 这样不会丢东西：今日校园 App 本身就是门户服务的移动端客户端（校园卡 / 电费 /
        // 校园网 / 图书馆都在里面），装了 App 的人不需要我们的门户壳。
        if (key == CampusServiceCatalog.KEY_TODAY_CAMPUS) {
            val launched = SchoolServiceLauncher.isAppInstalled(CampusServiceRegistry.TODAY_CAMPUS_PACKAGE, context) &&
                SchoolServiceLauncher.launchApp(CampusServiceRegistry.TODAY_CAMPUS_PACKAGE, context)
            if (!launched) subPage = ServiceSubPage.TodayCampus
            return
        }
        // 校园卡（易校园）：装了 App 拉起；没装就进门户里那条易校园入口（免登），
        // 而不是像以前那样打开易校园官网 —— 那里看不到余额也充不了值。
        if (key == CampusServiceKeys.CAMPUS_CARD) {
            // 绑定过易校园账号的话，直接给原生余额页（比让人进 App 找余额少三步）
            if (appContainer.loadCardCredential() != null) {
                subPage = ServiceSubPage.CampusCard
                return
            }
            // 没绑账号就地解决，别把人丢进门户网页（需求方明确反馈过这条）
            subPage = ServiceSubPage.CardAccountDialog
            return
        }
        if (key == CampusServiceKeys.ELECTRICITY && dormInfo?.isBound == true) {
            subPage = ServiceSubPage.Electricity
            return
        }
        val targetCampus = if (DormServiceRouter.isDormService(key)) {
            DormServiceRouter.serviceCampus(dormInfo, selectedCampus)
        } else {
            selectedCampus
        }
        val service = CampusServiceCatalog.resolve(key, title, targetCampus)
        if (service == null) {
            showServiceInfo(title, targetCampus.services[key], targetCampus.name)
            return
        }
        SchoolServiceLauncher.open(
            service = service,
            context = context,
            webViewSink = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url)
            },
        )
    }

    // 2026-10：这一条 24 项的 `||` 链已删除 —— "有没有子页"就是"这个可空值非空"。
    // 原注释记的事故（2026-10-02 漏了五个标志导致返回键退出 App）在结构上不可能再发生。
    val subPageVisible = subPage != null

    LaunchedEffect(subPageVisible) { onSubPageChange(subPageVisible) }
    BackHandler(enabled = subPageVisible) {
        subPage = null
    }

    // 列表滚动位置必须在这里（所有子页 `return` 之前）记住。
    // 子页都是 `return` 独占整屏，列表会整体离开组合 —— 状态若写在下面的
    // `if (asTab)` 分支里，`rememberScrollState` 会跟着被销毁，返回时重新从 0 开始
    //（2026-10 反馈："点一个服务再返回，服务页回到顶部了"）。
    // 放在这里，子页期间状态一直活着；切 Tab 的位置由 NavHost 的 saveState/restoreState 复现。
    val servicesScrollState = rememberScrollState()

    if (subPage == ServiceSubPage.LibrarySeat) {
        LibrarySeatScreen(onBack = { subPage = null })
        return
    }
    if (subPage == ServiceSubPage.Electricity) {
        ElectricityScreen(
            dormInfo = dormInfo,
            fallbackCampus = selectedCampus,
            onBack = { subPage = null },
            onOpenWeb = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url)
            },
        )
        return
    }
    if (subPage == ServiceSubPage.CampusCard) {
        val card = appContainer.loadCardCredential()
        CampusCardScreen(
            phone = card?.first.orEmpty(),
            password = card?.second.orEmpty(),
            onBack = { subPage = null },
            // 充值仍然走易校园 App / 门户那条路（原生页只管查余额）
            onOpenRecharge = {
                scope.launch {
                    CampusCardLauncher.open(context, selectedCampus) { url, t ->
                        if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url)
                    }
                }
            },
        )
        return
    }
    if (subPage == ServiceSubPage.CourseSelect) {
        CourseSelectScreen(
            onBack = { subPage = null },
            client = zhengfangClient,
            schoolRegistry = schoolRegistry,
            appContainer = appContainer,
            onOpenWeb = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url)
            },
        )
        return
    }
    if (subPage == ServiceSubPage.Notes) {
        NoteListScreen(
            container = appContainer,
            onBack = { subPage = null },
        )
        return
    }
    if (subPage == ServiceSubPage.Library) {
        LibraryScreen(
            onBack = { subPage = null },
            onOpenSeat = { subPage = ServiceSubPage.LibrarySeat },
            onOpenWeb = { url, title ->
                if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, title) else openUrl(context, url)
            },
        )
        return
    }
    if (subPage == ServiceSubPage.Learning) {
        LearningResourcePage(
            onBack = { subPage = null },
            onImportCourse = onImportCourse
        )
        return
    }
    if (subPage == ServiceSubPage.Chaoxing) {
        ChaoxingAccountPage(
            container = appContainer,
            onBack = { subPage = null },
            onOpenWeb = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb(url, t) else openUrl(context, url)
            },
            onLogout = { subPage = null },
        )
        return
    }
    if (subPage == ServiceSubPage.SecondClass) {
        val dreamUrl = selectedCampus.serviceUrls[CampusServiceKeys.SECOND_CLASS]
            ?.takeIf { it.isNotBlank() } ?: "https://www.5idream.net/"
        SecondClassEntryScreen(
            state = SecondClassEntryState(webUrl = dreamUrl, schoolLabel = selectedCampus.name),
            onBack = { subPage = null },
            onOpenWeb = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb(url, t) else openUrl(context, url)
            },
            onCopyUrl = { copyToClipboard(context, dreamUrl) },
            // 不猜包名（各家渠道包名不一致），用商店搜索：至少不会跳到一个不相干的 App
            onOpenStore = {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=到梦空间"))
                    )
                } catch (_: Exception) {
                    openUrl(context, "https://www.5idream.net/download")
                }
            },
        )
        return
    }
    if (subPage == ServiceSubPage.FoodPicker) {
        FoodPickerPage(onBack = { subPage = null })
        return
    }
    if (subPage == ServiceSubPage.McProtein) {
        McProteinIndexPage(onBack = { subPage = null })
        return
    }
    // 内置交互课件（Three.js，见 `data/courseware/`）。这一条是 sealed 层级里唯一带参数的
    // 目的地，所以只能 `as?` 取载荷再分发 —— 智能转换在 `by remember` 的局部变量上不可用。
    val coursewarePage = subPage as? ServiceSubPage.CoursewarePage
    if (coursewarePage != null) {
        CoursewareScreen(item = coursewarePage.item, onBack = { subPage = null })
        return
    }
    if (subPage == ServiceSubPage.TaskChecklist) {
        TaskChecklistScreen(
            repository = repository,
            schemeId = schemeId,
            onBack = { subPage = null }
        )
        return
    }
    if (subPage == ServiceSubPage.GoodItems) {
        GoodItemListScreen(
            repository = repository,
            schemeId = schemeId,
            onBack = { subPage = null }
        )
        return
    }
    if (subPage == ServiceSubPage.TreeHole) {
        TreeHoleScreen(
            repository = repository,
            schemeId = schemeId,
            onBack = { subPage = null }
        )
        return
    }
    if (subPage == ServiceSubPage.TeacherLookup) {
        TeacherLookupScreen(
            onBack = { subPage = null },
            repository = repository,
            schemeId = schemeId
        )
        return
    }
    if (subPage == ServiceSubPage.SavedGrades) {
        SavedGradesScreen(
            onBack = { subPage = null },
            client = zhengfangClient,
            schoolRegistry = schoolRegistry,
            appContainer = appContainer
        )
        return
    }
    if (subPage == ServiceSubPage.GradeQuery) {
        GradeQueryScreen(
            onBack = { subPage = null },
            client = zhengfangClient,
            schoolRegistry = schoolRegistry,
            appContainer = appContainer
        )
        return
    }
    if (subPage == ServiceSubPage.AcademicQuery) {
        AcademicQueryScreen(
            onBack = { subPage = null },
            client = zhengfangClient,
            schoolRegistry = schoolRegistry,
            appContainer = appContainer
        )
        return
    }
    if (subPage == ServiceSubPage.Weather) {
        WeatherScreen(
            repository = appContainer.weatherRepository,
            onBack = { subPage = null },
        )
        return
    }
    if (subPage == ServiceSubPage.TodayCampus) {
        TodayCampusScreen(
            container = appContainer,
            onBack = { subPage = null },
            onOpenWeb = { url, t ->
                if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url)
            }
        )
        return
    }

    if (subPage == ServiceSubPage.CardAccountDialog) {
        CardAccountDialog(
            initialPhone = appContainer.loadCardCredential()?.first.orEmpty(),
            onSave = { phone, password ->
                appContainer.saveCardCredential(phone, password)
                subPage = null
                // 存完直接进原生余额页（那一页自己会查一次，填错了也会当场显示平台的原因）
                subPage = ServiceSubPage.CampusCard
            },
            onInstallApp = { SchoolServiceLauncher.openMarketSearch("易校园", context) },
            onDismiss = { subPage = null },
        )
    }

    if (subPage == ServiceSubPage.UpgradeInfo) {
        ServiceInfoScreen(
            title = "专升本",
            subtitle = "山东省统招专升本：政策、报名与录取的关键节点，报名以山东省教育招生考试院公告为准。",
            accent = Color(0xFF6F7887),
            onBack = { subPage = null },
            onOpenLink = { url, t -> if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url) },
            sections = listOf(
                ServiceInfoSection("考试类型", "统招专升本面向应届专科毕业生，一般在大三下学期报考；录取后进入本科院校继续学习两年。"),
                ServiceInfoSection("关键节点", "报名：通常 1-3 月\n考试：通常 3-4 月\n志愿与录取：通常 4-5 月。具体时间每年略有调整。"),
                ServiceInfoSection("加分与免试", "符合条件的考生可申请免试或加分（如技能大赛获奖、退役士兵等），以当年政策为准。"),
                ServiceInfoSection("怎么准备", "先确认报考专业与考试科目，再按院校公布的参考教材复习；报名前留意学籍与毕业时间要求。")
            ),
            links = listOf(
                ServiceInfoLink("山东省教育招生考试院", "官方：政策发布与报名入口", "https://www.sdzk.cn/"),
                ServiceInfoLink("学信网", "学历学籍查询", "https://www.chsi.com.cn/")
            ),
            promos = promoViewModel.forSlot(promos, PromoSlot.ZHUANSANGBEN)
        )
        return
    }
    if (subPage == ServiceSubPage.BusInfo) {
        ServiceInfoScreen(
            title = "返乡大巴",
            subtitle = "学校周边返乡 / 返校大巴线路参考，实际班次以运营方公布为准。",
            accent = Color(0xFFC45C72),
            onBack = { subPage = null },
            onOpenLink = { url, t -> if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url) },
            sections = listOf(
                ServiceInfoSection("常见方向", "寒暑假与节假日一般有开往：烟台站、烟台南站、蓬莱国际机场，以及省内各地市（济南 / 青岛 / 潍坊 / 临沂等），具体以运营方为准。"),
                ServiceInfoSection("乘车点", "通常在校门口或周边指定集合点发车，发车前运营方会在群里通知具体位置与车牌。"),
                ServiceInfoSection("购票方式", "多为微信小程序 / 公众号下单，也有现场购票；节假日班次紧张，建议提前 1-2 天预订。"),
                ServiceInfoSection("温馨提示", "夜间到站注意安全，尽量与同学结伴；行李做好标记，贵重物品随身携带。")
            ),
            promos = promoViewModel.forSlot(promos, PromoSlot.FANXIANG_DABA),
        )
        return
    }
    if (subPage == ServiceSubPage.KaoyanInfo) {
        ServiceInfoScreen(
            title = "考研",
            subtitle = "报名、查分、调剂常用官方入口，点击可直接打开。",
            accent = Color(0xFFE58F8A),
            onBack = { subPage = null },
            onOpenLink = { url, t -> if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url) },
            sections = listOf(
                ServiceInfoSection("时间线", "预报名：9 月下旬\n正式报名：10 月\n网上确认：11 月上旬\n初试：12 月下旬\n成绩与调剂：次年 2-4 月。"),
                ServiceInfoSection("报名前", "确认报考点、专业与考试科目；准备好身份证、学籍信息与电子照片，具体以研招网公告为准。")
            ),
            links = listOf(
                ServiceInfoLink("研招网（报名 / 调剂 / 查分）", "中国研究生招生信息网", "https://yz.chsi.com.cn/"),
                ServiceInfoLink("考研成绩查询", "研招网查分入口", "https://yz.chsi.com.cn/apply/cjcx/"),
                ServiceInfoLink("学信网", "学历学籍 / 在线验证报告", "https://www.chsi.com.cn/")
            ),
            promos = promoViewModel.forSlot(promos, PromoSlot.KAOYAN),
            footer = "以上时间与入口以研招网 / 学信网最新公告为准。"
        )
        return
    }
    if (subPage == ServiceSubPage.GymInfo) {
        ServiceInfoScreen(
            title = "健身房",
            subtitle = "校内及学校周边健身场馆参考，实际价格与营业时间以场馆公布为准。",
            accent = Color(0xFF7BA98B),
            onBack = { subPage = null },
            onOpenLink = { url, t -> if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url) },
            sections = listOf(
                ServiceInfoSection("怎么选", "校内场馆一般凭学生证或校园卡入场，价格低于校外；校外商圈（如迎春大街一带）选择更多、器械更全，但需要办卡或按次付费。"),
                ServiceInfoSection("先看什么", "确认三件事：离宿舍/教学楼的步行距离（决定你能不能坚持去）、开放时间（是否覆盖你常去的时段）、是否有人流高峰（晚间 18-21 点通常最挤）。"),
                ServiceInfoSection("常用器材", "新手优先用固定器械（坐姿推胸、腿举、高位下拉）而不是自由重量；自由重量区建议先请人带一次动作，避免受伤。"),
                ServiceInfoSection("费用参考", "校内多为按学期/学年收费，校外月卡与季卡更常见。办卡前先确认是否支持转让与退卡 —— 毕业或搬宿舍是常事。"),
                ServiceInfoSection("注意", "办卡务必确认对方是正规经营场所并索要票据；对「先交钱再开店」的预售模式保持警惕。")
            ),
            places = listOf(ServiceInfoPlace(query = "烟台理工学院 健身房", label = "在地图上查找附近健身房")),
            onOpenPlace = { openMap(context, it) },
            promos = promoViewModel.forSlot(promos, PromoSlot.JIANSHENFANG),
            footer = "场馆信息以现场公示为准，本页不构成任何推荐。"
        )
        return
    }
    if (subPage == ServiceSubPage.DrivingInfo) {
        ServiceInfoScreen(
            title = "驾校",
            subtitle = "学车流程与选校要点，价格与班型以驾校公布为准。",
            accent = Color(0xFF6E8BA8),
            onBack = { subPage = null },
            onOpenLink = { url, t -> if (onOpenServiceWeb != null) onOpenServiceWeb.invoke(url, t) else openUrl(context, url) },
            sections = listOf(
                ServiceInfoSection("基本流程", "报名（身份证 + 体检）→ 科目一理论 → 科目二场地 → 科目三道路 → 科目四安全文明 → 领证。多数地区支持科目二/三连考，整体 2-6 个月，取决于约考排期与练车频率。"),
                ServiceInfoSection("怎么选驾校", "重点看三点：① 训练场离学校的距离（每天来回通勤是最容易被低估的成本）② 是否一人一车或小班（人多时要排队等车，实际练车时间会被摊薄）③ 补考与练车是否额外收费。"),
                ServiceInfoSection("费用构成", "通常包含报名费 + 培训费；考试费、补考费、模拟费、体检费可能另收。**报名前问清「全部办下来一共多少」**，不要只比首付价。"),
                ServiceInfoSection("异地学车注意", "学生户籍与学籍不在同一地时，部分地区需要居住证或在校证明才能报名，先问驾校需要哪些材料。"),
                ServiceInfoSection("避坑", "警惕「包过」「一个月拿证」「低价引流后加价」这三类说法；确认合同里写明费用包含项与退费规则。")
            ),
            places = listOf(ServiceInfoPlace(query = "烟台理工学院 驾校", label = "在地图上查找附近驾校")),
            onOpenPlace = { openMap(context, it) },
            promos = promoViewModel.forSlot(promos, PromoSlot.JIAXIAO),
            footer = "学车政策与费用以驾校及当地车管所公布为准。"
        )
        return
    }
    // 快递取件码不在这里分发：它是**弹窗**（`ExpressPickupDialog`），要盖在服务页之上，
    // 所以渲染在页面末尾。`subPage` 仍然管着它，返回键与底栏显隐因此不用另开一套。

    // 手写座次（"学习 / 生活 / 其他"的分区与区内顺序）。
    // ⚠️ 消费方请用下面的 `sections`（已按能力档位重排）；本变量只描述作者的原始意图，
    // 直接拿它渲染就等于绕过了能力排序。
    val authoredSections = listOf(
        ServiceSection(
            title = "学习",
            entries = listOf(
                // 2026-10：学期限定的高频动作排在最前 —— 查成绩、选课、看学分/绩点、评教
                // 是每学期都要走一遍的四件事，原先散在第 10~18 位，得滑两屏才找得到。
                // 这四项都能落到原生页/教务入口，能力档位同为 rank 0，`sortedBy` 是稳定排序，
                // 所以这里的先后顺序就是页面上的先后顺序。
                //
                // ⚠️ 「考试成绩」与「成绩查询」原本是**两张点了完全一样的卡**
                //（都执行 subPage = ServiceSubPage.GradeQuery），副标题也只差几个字。
                // 用户面对两个入口会犹豫"点哪个"，而正确回答是"没有区别"。
                //
                // 合并时留的是「考试成绩」，而**页面标题与助手文案全叫「成绩查询」**
                //（`GradeQueryScreen` 的 TopAppBar、`AssistantFormatters.grades` 的
                // 「到「服务 → 成绩查询」查询后点「全部存档」」、FAQ 里的同一句话）。
                // 于是用户点「考试成绩」进去看到「成绩查询」，以为进错了地方 —— 2026-10 报上来。
                // 现在统一成「成绩查询」：改这里一处，比改散在助手/FAQ 里的五处划算。
                ServiceEntry("成绩查询", "在线查询 · 可存档", Icons.AutoMirrored.Outlined.TrendingUp, Color.Unspecified, Color.Unspecified, iconFilled = Icons.AutoMirrored.Filled.TrendingUp, accent = ServiceAccent.ACADEMIC) {
                    subPage = ServiceSubPage.GradeQuery
                },
                ServiceEntry("选课", "原生入口 · 正选/补退选/校公选", Icons.Outlined.Checklist, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Checklist, accent = ServiceAccent.ACADEMIC) {
                    // 2026-10：这里原先直接 `openCampusService(COURSE_SELECT)` 走网页 ——
                    // 于是原生 `CourseSelectScreen` 虽然写好了、也接进了 dispatcher，
                    // **却没有任何入口能进得去**（sealed 路由改造时的新守卫抓到的）。
                    // 现已改为进原生页；网页/外部出口由该页自己的 `onOpenWeb` 提供，
                    // 所以外部路径没有丢失。
                    subPage = ServiceSubPage.CourseSelect
                },
                ServiceEntry("修课情况", "学分 · 绩点 · 培养方案", Icons.Outlined.Info, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Info, accent = ServiceAccent.ACADEMIC) {
                    subPage = ServiceSubPage.AcademicQuery
                },
                ServiceEntry("评教", "教学评价 · 问卷", Icons.Outlined.RateReview, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.RateReview, accent = ServiceAccent.ACADEMIC) {
                    openCampusService(CampusServiceKeys.EVALUATION, "评教")
                },
                ServiceEntry("到梦空间", "第二课堂 · 活动报名", Icons.Outlined.EmojiEvents, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.EmojiEvents, accent = ServiceAccent.STUDY) {
                    // 不直开网页：那是个 min-width:1200px 的桌面站，而且登录只有扫码，
                    // 二维码显示在这台手机上 —— 扫不了自己。先给一屏说清状况的入口页。
                    subPage = ServiceSubPage.SecondClass
                },
                // 校园跑（支付宝阳光跑）：一键打开签到小程序，省掉在操场排队等"开页面"。
                // 这一格**只有配了直达链接的校区才渲染**（过滤在下面的 `sections`），
                // 链接按校区配在 `CampusServiceRegistry.SCHOOL_RUN_ALIPAY`。
                ServiceEntry("校园跑", "支付宝阳光跑 · 一键打开", Icons.Outlined.DirectionsRun, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.DirectionsRun, accent = ServiceAccent.STUDY, serviceKey = CampusServiceKeys.SCHOOL_RUN, aliases = listOf("阳光跑", "跑步打卡", "乐跑", "体育")) {
                    openCampusService(CampusServiceKeys.SCHOOL_RUN, "校园跑")
                },
                ServiceEntry("学习通", "网课 · 作业 · 考试", Icons.AutoMirrored.Outlined.MenuBook, Color.Unspecified, Color.Unspecified, iconFilled = Icons.AutoMirrored.Filled.MenuBook, accent = ServiceAccent.STUDY) {
                    // 登录过就直接进原生页（课程与待办都在里面），没登录才把人送去登录网页。
                    if (appContainer.hasLearningCookie(LearningPlatform.CHAOXING)) {
                        subPage = ServiceSubPage.Chaoxing
                    } else {
                        val url = LearningPlatform.CHAOXING.loginUrl
                        if (onOpenServiceWeb != null) onOpenServiceWeb(url, "学习通") else openUrl(context, url)
                    }
                },
                ServiceEntry("知到", "智慧树 · 网课", Icons.Outlined.School, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.School, accent = ServiceAccent.STUDY) {
                    val url = LearningPlatform.ZHIHUISHU.loginUrl
                    if (onOpenServiceWeb != null) onOpenServiceWeb(url, "知到") else openUrl(context, url)
                },
                ServiceEntry("图书馆", "馆藏 · 借阅 · 座位", Icons.Outlined.Bookmarks, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Bookmarks, accent = ServiceAccent.QUERY) {
                    subPage = ServiceSubPage.Library
                },
                ServiceEntry("学习笔记", "本地笔记 · 后续公开共享", Icons.Outlined.Checklist, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Checklist, accent = ServiceAccent.STUDY) {
                    subPage = ServiceSubPage.Notes
                },
                // 资料站与快问快答都是**整页**（非 tab 路由），与「专注计时」同一个出口形态。
                // 它们的真源在 assets/ 与 Gitee Release 上，名字里的「课件」「每章提问」
                // 就是产品边界，别改成「学习资料」「问答」这种说不清在干什么的说法。
                ServiceEntry("资料站", "课件 · 历年卷 · 按课程找", Icons.Outlined.FolderOpen, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.FolderOpen, accent = ServiceAccent.STUDY) {
                    onOpenLibrary?.invoke()
                },
                ServiceEntry("快问快答", "老师每章会问什么 · 照着答", Icons.Outlined.Quiz, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Quiz, accent = ServiceAccent.STUDY) {
                    onOpenQuickAnswer?.invoke()
                },
                // 番茄钟。⚠️ 它**不进** `DEFAULT_QUICK_SERVICE_KEYS`（AppContainer）：
                // 那是老用户已经排好的常用格，加一格等于替所有人改了首页。
                // 想固定到常用的人自己在服务页里加，加的时候按 title 逐字匹配 ——
                // 所以这一格的 title 就是 key，改字会静默丢掉用户已固定的那格。
                ServiceEntry("专注计时", "番茄钟 · 25 分钟专注", Icons.Outlined.Timer, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Timer, accent = ServiceAccent.STUDY) {
                    onOpenFocus?.invoke()
                },
                // ⚠️ 座位**不再单独占一格**：座位入口在「图书馆」页内（`LibraryScreen` 的原生
                // 座位动作）。这里曾经并列一张「图书馆座位」卡，与图书馆页里那条动作指向同一个
                // `ServiceSubPage.LibrarySeat`，用户看到两张卡会犹豫点哪个 —— 2026-10 反馈。
                ServiceEntry("空教室查询", "找自习室 · 空闲教室", Icons.Outlined.MeetingRoom, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.MeetingRoom, accent = ServiceAccent.QUERY) {
                    openCampusService(CampusServiceKeys.CLASSROOM, "空教室查询")
                },
                ServiceEntry("考试安排", "粘贴通知 · 自动识别 · 加入待办", Icons.AutoMirrored.Outlined.EventNote, Color.Unspecified, Color.Unspecified, iconFilled = Icons.AutoMirrored.Filled.EventNote, accent = ServiceAccent.ACADEMIC) {
                    onOpenExamSchedule?.invoke()
                },
                ServiceEntry("校历", "学年安排 · 教学周 · 放假安排", Icons.Outlined.DateRange, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.DateRange, accent = ServiceAccent.ACADEMIC) {
                    onOpenCalendar?.invoke()
                },
                ServiceEntry("我的课程", "已修课程 · 成绩存档 · 离线可看", Icons.Outlined.Bookmarks, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Bookmarks, accent = ServiceAccent.ACADEMIC) {
                    subPage = ServiceSubPage.SavedGrades
                },
                ServiceEntry("四级六级", "CET 报名与成绩", Icons.Outlined.Translate, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Translate, accent = ServiceAccent.ACADEMIC) {
                    openUrl(context, "https://cet.neea.edu.cn/")
                },
                ServiceEntry("普通话考试", "报名 · 成绩查询", Icons.Outlined.RecordVoiceOver, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.RecordVoiceOver, accent = ServiceAccent.ACADEMIC) {
                    openUrl(context, "https://bm.cltt.org/")
                },
                ServiceEntry("转专业", "政策 · 申请 · 结果", Icons.Outlined.SwapHoriz, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.SwapHoriz, accent = ServiceAccent.ACADEMIC) {
                    openCampusService(CampusServiceKeys.TRANSFER_MAJOR, "转专业")
                },
                ServiceEntry("专升本", "政策 · 报名 · 合作推广", Icons.Outlined.Upgrade, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Upgrade, accent = ServiceAccent.ACADEMIC) {
                    subPage = ServiceSubPage.UpgradeInfo
                },
                ServiceEntry("考研", "研招网 · 报名 · 查分", Icons.Outlined.School, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.School, accent = ServiceAccent.STUDY) {
                    subPage = ServiceSubPage.KaoyanInfo
                },
                ServiceEntry("学习资源", "网课资源，一键导入课表", Icons.Outlined.OndemandVideo, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.OndemandVideo, accent = ServiceAccent.STUDY) {
                    subPage = ServiceSubPage.Learning
                },
                ServiceEntry("查询老师", "办公室与联系方式", Icons.Outlined.Person, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Person, accent = ServiceAccent.INFO) {
                    subPage = ServiceSubPage.TeacherLookup
                }
            )
        ),
        ServiceSection(
            title = "生活服务",
            entries = listOf(
                // 天气放生活区第一格：出门前看一眼是高频动作，不该埋在中段。
                // 数据源是 Open-Meteo（免 Key、开源），坐标是烟台市 —— 见 WeatherScreen 的 KDoc。
                ServiceEntry("天气", "实时 · 24 小时 · 7 天", Icons.Outlined.WbSunny, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.WbSunny, accent = ServiceAccent.QUERY) {
                    subPage = ServiceSubPage.Weather
                },
                ServiceEntry("校园卡", "余额 · 充值 · 消费", Icons.Outlined.CreditCard, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.CreditCard, accent = ServiceAccent.PAYMENT) {
                    openCampusService(CampusServiceKeys.CAMPUS_CARD, "校园卡")
                },
                ServiceEntry("烟台乘车码", "公交 · 扫码乘车", Icons.Outlined.DirectionsBus, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.DirectionsBus, accent = ServiceAccent.PAYMENT, serviceKey = CampusServiceKeys.TRANSIT_CODE) {
                    openCampusService(CampusServiceKeys.TRANSIT_CODE, "烟台乘车码")
                },
                ServiceEntry(
                    "洗衣机",
                    "当前：${DormServiceRouter.serviceCampus(dormInfo, selectedCampus).laundryProvider ?: selectedCampus.laundryProvider ?: "待配置"}",
                    Icons.Outlined.LocalLaundryService,
                    Color.Unspecified,
                    Color.Unspecified, iconFilled = Icons.Filled.LocalLaundryService, accent = ServiceAccent.LIFE
                ) {
                    openCampusService(CampusConfig.SERVICE_LAUNDRY, "洗衣")
                },
                ServiceEntry("校园网", "认证 · 套餐 · 故障", Icons.Outlined.Wifi, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Wifi, accent = ServiceAccent.LIFE) {
                    openCampusService(CampusServiceKeys.NETWORK, "校园网")
                },
                ServiceEntry("快递", "淘宝 / 拼多多取件码", Icons.Outlined.LocalShipping, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.LocalShipping, accent = ServiceAccent.LIFE) {
                    subPage = ServiceSubPage.ExpressPickup
                },
                ServiceEntry("通知公告", "学校通知 · 学院公告", Icons.Outlined.Campaign, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Campaign, accent = ServiceAccent.INFO) {
                    openCampusService(CampusServiceKeys.NOTICE, "通知公告")
                },
                ServiceEntry("宿舍电费", dormInfo?.label?.let { "已绑定 $it" } ?: "余额 · 充值 · 用电", Icons.Outlined.FlashOn, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.FlashOn, accent = ServiceAccent.ENERGY) {
                    openCampusService(CampusServiceKeys.ELECTRICITY, "宿舍电费")
                },
                // ⚠️ 副标题**不要写「预约」**。趣智校园的公开接口里没有预约能力
                //（26 个接口只有查询设备 / 开阀 / 关阀），承诺一个做不到的功能是对用户撒谎。
                // 这里走的是原有的「跳趣智校园 App」路径；原生控制页在「我的 → 洗澡」。
                ServiceEntry("洗澡", "澡堂 · 淋浴 · 热水", Icons.Outlined.HotTub, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.HotTub, accent = ServiceAccent.LIFE) {
                    openCampusService(CampusServiceKeys.SHOWER, "洗澡")
                },
                ServiceEntry("热水", "直饮水 · 热水机", Icons.Outlined.LocalFireDepartment, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.LocalFireDepartment, accent = ServiceAccent.LIFE) {
                    openCampusService(CampusServiceKeys.HOT_WATER, "热水")
                },
                ServiceEntry("报修", "宿舍 · 教室 · 网络", Icons.Outlined.Build, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Build, accent = ServiceAccent.LIFE) {
                    openCampusService(CampusServiceKeys.REPAIR, "报修")
                },
                ServiceEntry("返乡大巴", "线路参考 · 合作推广", Icons.Outlined.DirectionsBus, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.DirectionsBus, accent = ServiceAccent.INFO) {
                    subPage = ServiceSubPage.BusInfo
                },
                ServiceEntry("地图导航", "校园地点 · 外部导航", Icons.Outlined.Map, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Map, accent = ServiceAccent.INFO) {
                    if (onOpenCampusMap != null) onOpenCampusMap() else openMap(context, selectedCampus.name)
                },
                ServiceEntry("今日校园", "智慧校园 · 统一认证", Icons.Outlined.Today, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Today, accent = ServiceAccent.INFO) {
                    openCampusService(CampusServiceCatalog.KEY_TODAY_CAMPUS, "今日校园")
                },
                ServiceEntry("就业", "招聘 · 宣讲会 · 岗位", Icons.Outlined.Work, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Work, accent = ServiceAccent.INFO) {
                    openCampusService(CampusServiceKeys.JOB, "就业")
                },
                ServiceEntry("今天吃什么", "随机抽签解决选择困难", Icons.Outlined.Restaurant, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Restaurant, accent = ServiceAccent.LIFE) {
                    subPage = ServiceSubPage.FoodPicker
                },
                ServiceEntry("麦当劳蛋白榜", "每 1 元能买多少克蛋白", Icons.Outlined.Fastfood, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Fastfood, accent = ServiceAccent.LIFE) {
                    subPage = ServiceSubPage.McProtein
                },
                ServiceEntry("迎新网", "报到 · 宿舍 · 缴费", Icons.Outlined.HowToReg, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.HowToReg, accent = ServiceAccent.INFO) {
                    val url = "https://qxj.yitsd.edu.cn/yx/web/"
                    if (onOpenServiceWeb != null) onOpenServiceWeb(url, "迎新网") else openUrl(context, url)
                }
            )
        ),
        // 「互动课件」整节由清单生成（`data/courseware/CoursewareCatalog.kt`）：
        // 加一个课件，这里只会多一格，不会出现"清单里有、页面上没有"。
        // 图标用**穷尽 when**而不是 `else -> 通用图标` —— 漏配图标要编译不过，
        // 静默退回通用图标会让新课件长得跟别的格子一模一样。
        ServiceSection(
            title = "互动课件",
            entries = Courseware.entries.map { item ->
                ServiceEntry(
                    item.title,
                    item.subtitle,
                    when (item) {
                        Courseware.BeamDeflection -> Icons.Outlined.ShowChart
                        Courseware.SoilThreePhase -> Icons.Outlined.Layers
                    },
                    Color.Unspecified,
                    Color.Unspecified,
                    accent = ServiceAccent.STUDY,
                ) { subPage = ServiceSubPage.CoursewarePage(item) }
            },
        ),
        ServiceSection(
            title = "其他",
            entries = listOf(
                ServiceEntry("健身房", "场馆选择 · 费用参考", Icons.Outlined.FitnessCenter, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.FitnessCenter, accent = ServiceAccent.LIFE) {
                    subPage = ServiceSubPage.GymInfo
                },
                ServiceEntry("驾校", "学车流程 · 选校要点", Icons.Outlined.DirectionsCar, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.DirectionsCar, accent = ServiceAccent.INFO) {
                    subPage = ServiceSubPage.DrivingInfo
                },
                ServiceEntry("教室分布图", "楼栋 · 楼层 · 教室录入", Icons.Outlined.MeetingRoom, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.MeetingRoom, accent = ServiceAccent.QUERY) {
                    onOpenRoomLayout?.invoke()
                },
                ServiceEntry("好物清单", "大学好物，早买早享受", Icons.Outlined.Star, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Star, accent = ServiceAccent.INFO) {
                    subPage = ServiceSubPage.GoodItems
                },
                ServiceEntry(
                    "任务清单",
                    if (taskTotal > 0) "$taskCompleted/$taskTotal 已完成" else "记录大学目标",
                    Icons.Outlined.CheckCircle,
                    Color.Unspecified,
                    Color.Unspecified, iconFilled = Icons.Filled.CheckCircle, accent = ServiceAccent.LIFE
                ) {
                    subPage = ServiceSubPage.TaskChecklist
                },
                ServiceEntry("留声树洞", "把悄悄话放进树洞", Icons.Outlined.FavoriteBorder, Color.Unspecified, Color.Unspecified, iconFilled = Icons.Filled.Favorite, accent = ServiceAccent.INFO) {
                    subPage = ServiceSubPage.TreeHole
                }
            )
        )
    )

    // 快递取件码：弹窗，盖在服务页之上。放在整页内容之后渲染，才不会被后面的节点压住。
    // 子页状态仍走 `subPage`（返回键、底栏显隐、路由测试都靠它），不另开布尔量。
    if (subPage == ServiceSubPage.ExpressPickup) {
        ExpressPickupOverlay(onDismiss = { subPage = null })
    }

    if (serviceInfoTitle != null) {
        AlertDialog(
            onDismissRequest = {
                serviceInfoTitle = null
                serviceInfoProvider = null
                serviceInfoCampusName = null
            },
            title = { Text(serviceInfoTitle.orEmpty()) },
            text = {
                Text(
                    buildString {
                        append("当前校区：")
                        append(serviceInfoCampusName ?: selectedCampus.name)
                        append("\n")
                        if (!serviceInfoProvider.isNullOrBlank()) {
                            append("服务商/系统：")
                            append(serviceInfoProvider)
                            append("\n")
                        }
                        append("该服务入口已预留，接入对应校园系统后即可直接使用。")
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        serviceInfoTitle = null
                        serviceInfoProvider = null
                        serviceInfoCampusName = null
                    }
                ) { Text("知道了") }
            }
        )
    }

    // ── 能力档位（2026-10）──
    //
    // 服务页有 40 多个入口，但"点下去会发生什么"分四档，而界面上四档长得一模一样：
    // 用户点「校园网」才知道它只是个说明页，于是对另外几十个也不再信任。
    //
    // 这里做两件事，都只改"信息呈现"，不改任何点击行为：
    // 1. **分区内**按能力重排：能用得上的排前面，"仅介绍 / 未接入"沉到该区末尾；
    //    ⚠️ **刻意不重排分区本身** ——「学习 / 生活 / 其他」是作者的分区意图，
    //    按能力重排分区会让"其他"插到"学习"前面，那是另一件事，不该夹带在这次修改里。
    // 2. 给"仅介绍 / 未接入"挂角标（能用原生/网页的**不挂** —— 绝大多数都挂就等于装饰）。
    //
    // 判定全部委托 `CampusServiceResolver.capabilityOf`，与真正打开服务时走同一套
    // spec 查找与宿舍路由，所以不会出现"界面标着能用、点下去弹预留"。
    //
    // ⚠️ 判定写成**顶层纯函数** `serviceEntryCapability(...)` 而不是这里的局部函数：
    // 局部函数会把整个 composable 作用域捕获进 lambda，`remember` 的键就失去意义。
    //
    // 座次键必须**与能力有关**，否则切换宿舍楼（会改变生活服务走哪个校区）后
    // 顺序不会重算 —— 那正是"界面说能用、实际不能用"的另一种形态。
    // `sortedBy` 是稳定排序，所以同档位内部保持作者手写的顺序。
    val sections = remember(authoredSections, selectedCampus.id, dormInfo) {
        authoredSections.map { section ->
            section.copy(
                entries = section.entries
                    // 「校园跑」这类**纯直达格子**：没配链接就不出现。
                    // 留着一格，点下去只会弹"暂未配置"——那是死格子，不是待接入。
                    // （区别于健身房那种 INFO 格子：它有站内说明页，点开有内容。）
                    .filterNot {
                        it.serviceRegistryKey == CampusServiceKeys.SCHOOL_RUN &&
                            serviceEntryCapability(it, selectedCampus, dormInfo) == CampusServiceCapability.INFO
                    }
                    .sortedBy {
                        serviceEntryCapability(it, selectedCampus, dormInfo).sortRank
                    }
            )
        }
    }

    // ── 常用服务：用户自选，最多 4 个 ──
    val quickKeys by appContainer.quickServiceKeys.collectAsStateWithLifecycle()
    val hiddenKeys by appContainer.hiddenServiceKeys.collectAsStateWithLifecycle()
    val allServiceEntries = sections.flatMap { it.entries }
    val quickEntries = quickKeys
        .mapNotNull { key -> allServiceEntries.firstOrNull { it.key == key } }
        .filterNot { it.key in hiddenKeys }
    val displayedSections = remember(sections, hiddenKeys, serviceQuery) {
        val q = serviceQuery.trim()
        sections.mapNotNull { section ->
            val entries = section.entries
                .filterNot { it.key in hiddenKeys }
                .filter { q.isBlank() || it.matchesQuery(q) }
            if (entries.isEmpty()) null else section.copy(entries = entries)
        }
    }

    if (subPage == ServiceSubPage.QuickServiceEdit) {
        QuickServiceEditDialog(
            allEntries = allServiceEntries,
            selectedKeys = quickKeys,
            hiddenKeys = hiddenKeys,
            onSave = { appContainer.setQuickServiceKeys(it) },
            onToggleHidden = { appContainer.toggleServiceHidden(it) },
            onReset = {
                appContainer.resetQuickServiceKeys()
                appContainer.resetHiddenServices()
                subPage = null
            },
            onDismiss = { subPage = null }
        )
    }

    if (asTab) {
        // 本页自己接管状态栏（见 AppNavigation 的 SELF_MANAGED_TOP_ROUTES）：
        // 底色与顶部彩晕铺到屏幕顶端，列表靠 statusBarsPadding 让位。
        val colorful = currentIconPresentation().colorful
        val isDark = LocalDarkMode.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceBase)
        ) {
            if (colorful) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.18f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(servicesScrollState)
                    // 底栏已改成叠在内容上的浮层（见 AppNavigation），Scaffold 不再替页面预留它。
                    // 服务页是长列表，这里**故意**让内容从栏背后滚过去 —— 那是玻璃唯一的采样源；
                    // 只在内容末尾补一段 = 栏高的空白，保证最后一张卡仍能滚到栏上方。
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 18.dp,
                        bottom = LocalBottomBarInset.current + 18.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(skin.spaceLg)
            ) {
            // 这两行（大字"服务" + 校区副标题）已移除：底栏那个 tab 本身就写着"服务"，
            // 进页面再看一遍标题属于重复占位；校区信息在服务卡的 ServiceSummary 里本来就有。
            //
            // 顶部海报横幅：内容由服务端 `promos.json` 下发（仓储已带缓存与白名单校验），
            // 换海报不用发版；没有内容或图片加载失败时**整块不出现**。
            PromoBanner(
                promos = promos.filter { it.slot == PromoSlot.SERVICE_HOME },
                onClick = { promo ->
                    when (val action = promo.action) {
                        // 外部链接走内置 WebView —— 域名白名单校验在 PromoValidator 里，这里不重复判
                        is PromoAction.External -> onOpenServiceWeb?.invoke(action.url, promo.title)
                        // App 内跳转：白名单只放行 services / timetable / home（都是底栏 tab），
                        // 导航层用 switchTab 接 —— 不认识的路由在那边静默不跳（白名单已在解析阶段挡过）。
                        is PromoAction.Route -> onOpenPromoRoute(action.route)
                        null -> Unit
                    }
                },
            )
            ServicesBody(displayedSections, colors, quickEntries, summaries = serviceSummaries, campus = selectedCampus, dormInfo = dormInfo, query = serviceQuery, onQueryChange = { serviceQuery = it }, onEditQuick = { subPage = ServiceSubPage.QuickServiceEdit }, showSectionTitle = tabAppearance.servicesShowSectionTitle)
        }
        }
    } else {
        Scaffold(
            containerColor = colors.surfaceBase,
            topBar = {
                TopAppBar(
                    title = { Text("百宝箱", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.surfaceCard
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(servicesScrollState)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(skin.spaceLg)
            ) {
                ServicesBody(displayedSections, colors, quickEntries, summaries = serviceSummaries, campus = selectedCampus, dormInfo = dormInfo, query = serviceQuery, onQueryChange = { serviceQuery = it }, onEditQuick = { subPage = ServiceSubPage.QuickServiceEdit }, showSectionTitle = tabAppearance.servicesShowSectionTitle)
            }
        }
    }
}

/**
 * 服务页分区标题（分类名 + 条数胶囊）。
 *
 * 与首页 `SectionHeader` 共用同一套排版令牌（20sp 区块标题 + `surfaceAlt` 条数胶囊），
 * 左侧仍保留 4dp 强调色竖条，让「常用服务」与分类区在长列表里有清晰的层级锚点。
 * 改造前标题是 18sp、条数是一段没有底色的 11sp 小字，既与首页不一致，
 * 也让"这一区有几项"几乎读不出来。
 */
@Composable
internal fun ServiceSectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(skin.radiusMicro))
                .background(colors.accentMain)
        )
        Spacer(Modifier.width(skin.spaceSm))
        Text(
            text = title,
            style = PocketTypography.sectionTitle,
            color = colors.textPrimary,
            // 条数胶囊靠右：标题左、计数右，长列表里扫视分区时视线不用在标题后面
            // 捡一个紧贴的小胶囊；也与「常用服务 → 编辑」的左右结构保持一致。
            modifier = Modifier.weight(1f),
        )
        // 条数胶囊跟「用色强度」走：彩色态用强调色浅底，单色态保持中性，不抢标题。
        val colorful = currentIconPresentation().colorful
        val isDark = LocalDarkMode.current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (colorful) colors.accentMain.copy(alpha = if (isDark) 0.20f else 0.10f)
                    else colors.surfaceAlt
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString() + " 项",
                style = PocketTypography.label,
                fontWeight = FontWeight.Medium,
                color = if (colorful) colors.accentMain else colors.textSecondary,
            )
        }
    }
}

/**
 * 服务搜索无结果时的空态卡。
 *
 * 改造前只有一行 13sp 的灰字，既没有图标也不成块，看起来像页面渲染失败。
 * 这里与首页空态保持同构：强调色浅底圆形图标 + 主副两行文案。
 */
@Composable
internal fun ServiceSearchEmptyState(modifier: Modifier = Modifier) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val shape = RoundedCornerShape(skin.radiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceCard)
            // 与中性服务卡同一条边界策略：白卡贴在浅底上需要一条极淡描边，
            // 否则空态看起来像一页没有内容的空白，而不是一张"告诉你去哪"的卡。
            .border(1.dp, colors.borderDivider, shape)
            .padding(vertical = skin.spaceWide),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.accentMain.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = colors.accentMain,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(skin.spaceSm))
        Text(
            text = "没有找到相关服务",
            style = PocketTypography.body,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = "换个关键词试试",
            style = PocketTypography.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun ColumnScope.ServicesBody(
    sections: List<ServiceSection>,
    colors: PocketXiaoliColors,
    quickEntries: List<ServiceEntry>,
    summaries: Map<String, ServiceSummary>,
    /** 当前校区与宿舍上下文 —— 只用于判定能力角标，见 [QuickServiceRow] 的同名参数说明。 */
    campus: CampusInfo,
    dormInfo: DormInfo?,
    query: String,
    onQueryChange: (String) -> Unit,
    onEditQuick: () -> Unit,
    /** 设置页「显示分类标题」。2026-10 之前这个设置**没有消费者**（拨了没反应）。 */
    showSectionTitle: Boolean = true,
) {
    val skin = LocalSkin.current
    val gridLayout = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    // 两排标签已移除（用户反馈多余）：
    //   ① 分类标签行「全部 / 学习考试 / 生活服务 / 工具箱」
    //   ② 它下面那排关键词胶囊「成绩 / 空教室 / 校园卡 / …」
    // 理由：上面有搜索框、下面本来就是按标题分好组的服务区，再加两排横向标签只是重复占位，
    // 还白吃掉一屏顶部的空间。分类过滤功能随标签一起去掉，需要筛就用搜索。
    val visibleSections = sections
    CampusSearchBar(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "搜索校园服务、工具、生活服务",
    )

    // 注意：ServicesBody 是 `ColumnScope` 扩展，下面发出的每个顶层块都是页面外层
    // Column 的直接子项，块与块之间的间距由那边的 `spacedBy(spaceLg)` 负责 ——
    // 这里不要再补 Spacer，否则搜索框与常用服务之间会出现两段间距叠加。
    if (query.isBlank()) {
        QuickServiceRow(
            entries = quickEntries,
            colors = colors,
            summaries = summaries,
            campus = campus,
            dormInfo = dormInfo,
            onEdit = onEditQuick,
            onServiceClick = { entry ->
                // 只记入口名（`ServiceEntry.key` 就是标题常量），不记任何内容。
                Telemetry.featureUsed(entry.key)
                entry.onClick()
            },
        )
    }

    if (query.isNotBlank() && visibleSections.isEmpty()) {
        ServiceSearchEmptyState()
    }

    visibleSections.forEach { section ->
        // 每个分区独立成组：组内"标题 → 卡片行"与行间距用 `spaceMd`，
        // 组与组之间仍由外层 Column 的 `spaceLg` 拉开 —— 层级靠间距差异表达。
        // 改造前这些全是页面 Column 的直接子项，标题与卡片、行与行都是同一档 `spaceLg`。
        Column(
            verticalArrangement = Arrangement.spacedBy(skin.spaceMd),
        ) {
            // ⚠️ 2026-10：这个 `if` 是补上的 —— `servicesShowSectionTitle`（设置页的
            // 「显示分类标题」）此前**根本没有任何消费者**，即用户拨了开关什么都不会发生。
            // 这正是 `TabAppearance` 的 KDoc 专门警告过的「声明了没接线」（`assistantFontMultiplier`
            // 踩过同一坑）。新加外观字段时请一并确认消费者。
            if (showSectionTitle) {
                ServiceSectionHeader(title = section.title, count = section.entries.size)
            }

            // ── 两种布局范式（2026-10）──
            //
            // `CampusLayoutMode` 早在 2026-05 就存在、`ServiceCard` 也一直在读它，但它**只改卡片尺寸**
            //（78→64dp），布局形状始终是 `chunked(2)` 的富卡片 —— 于是「工具箱模式」名不副实。
            // 这里补上缺的另一半：TOOLBOX → 真正的紧凑图标宫格。
            //
            // ⚠️ 默认值 `MODERN` 就是现状，而现有 golden 基线**全部** provide `MODERN`
            //（见 `HomeCourseCardScreenshotTest` 等），所以这个分支**不会改变任何基线**。
            if (gridLayout) {
                section.entries.chunked(SERVICE_GRID_COLUMNS).forEach { rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)
                    ) {
                        rowEntries.forEach { entry ->
                            val display = entry.withSummary(entry.serviceRegistryKey?.let(summaries::get), campus, dormInfo)
                            ServiceGridCell(
                                entry = display,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    Telemetry.featureUsed(display.key)
                                    display.onClick()
                                },
                            )
                        }
                        // 末行不足一列时补空位，否则最后几个格子会被拉宽、与上面的列对不齐
                        //（现状的 `chunked(2)` 用 `if (rowEntries.size == 1) Spacer(weight(1f))` 是同一手法）。
                        repeat(SERVICE_GRID_COLUMNS - rowEntries.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                section.entries.chunked(2).forEach { rowEntries ->
                    // `height(IntrinsicSize.Max)`：一行两张卡按"较高那张"等高。
                    // 卡片里的 `fillMaxHeight` 需要**有界**的最大高度才能拉平，
                    // 而外层是 LazyColumn（高度约束无限），所以这一行必须自己把高度定下来。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)
                    ) {
                        rowEntries.forEach { entry ->
                            val display = entry.withSummary(entry.serviceRegistryKey?.let(summaries::get), campus, dormInfo)
                            ServiceCard(
                                entry = display,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    Telemetry.featureUsed(display.key)
                                    display.onClick()
                                },
                            )
                        }
                        if (rowEntries.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun QuickServiceRow(
    entries: List<ServiceEntry>,
    colors: PocketXiaoliColors,
    summaries: Map<String, ServiceSummary>,
    /**
     * 当前校区与宿舍上下文 —— 只用于**判定能力角标**（"仅介绍 / 未接入"）。
     *
     * 为什么必须传进来而不是在内部现取：能力判定要跟真正打开服务时走同一套
     * 宿舍路由（电费/洗澡/热水/洗衣可能走另一个校区），在这里另取一次就又多一处真源。
     */
    campus: CampusInfo,
    dormInfo: DormInfo?,
    title: String = "常用服务",
    showEdit: Boolean = true,
    onEdit: () -> Unit,
    onServiceClick: (ServiceEntry) -> Unit,
) {
    // ── 图标策略：皮肤上限 × 用户用色强度 的合成结果（见 IconPresentation.resolveIcons）──
    // 此前这里只读用户偏好（`LocalAppearanceMode == PURE`），于是「极简」皮肤的
    // "单色线性图标"承诺无法兑现 —— 极简 + 彩虹仍然是彩色。
    // 换成合成结果后，同一条已解析标志同时驱动"底 / 色 / 图标形状"，三者同进同退，
    // 不会出现"彩色底配单色图标"的破相。
    val icon = currentIconPresentation()
    val compact = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    val skin = LocalSkin.current
    val isDark = LocalDarkMode.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = PocketTypography.sectionTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (showEdit) {
                // 文字按钮此前只有 ~26dp 高、且没有按压反馈。外层 44dp 透明盒负责热区，
                // 按压时整枚胶囊一起下沉 —— 与首页胶囊、卡片同一套 MIUIX 手感。
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(50))
                        .miuixPress(onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accentMain.copy(alpha = if (isDark) 0.18f else 0.10f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = colors.accentMain,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "编辑",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentMain
                        )
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            Text(
                text = "还没有常用服务，点右上角「编辑」添加",
                fontSize = 12.sp,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceMd)
        ) {
            entries.forEach { entry ->
                val display = entry.withSummary(entry.serviceRegistryKey?.let(summaries::get), campus, dormInfo)
                // 与 ServiceCard 同样的回退策略：有语义分类就用主题派生色，否则用旧色对
                val accentTint = display.accent?.let { serviceAccentColor(it) }
                val accentContainer = display.accent?.let { serviceAccentContainer(it) }
                // 单色态改用中性软底 + 统一强调色（与 ServiceCard 同一套规则）
                val background = if (!icon.colorful) colors.surfaceHighlight else (accentContainer ?: display.background)
                val tint = if (icon.colorful) (accentTint ?: display.tint) else colors.accentMain
                val tileShape = RoundedCornerShape(skin.radiusCard)
                val colorTile = icon.colorful && accentTint != null
                // 与富卡片同一套三层（类目色三档渐变 + 顶部高光 + 同色描边），
                // 只把小面积的顶端浓度收到 0.40：54dp 宽的格子上 0.46 会显脏。
                val tileBrush = if (colorTile) serviceSurfaceBrush(accentTint!!, topAlpha = 0.40f) else null
                val tileShadow = if (colorTile && skin.shadowBlur > 0.dp) {
                    Modifier.dropShadow(
                        shape = tileShape,
                        shadow = Shadow(
                            radius = skin.shadowBlur,
                            spread = 0.dp,
                            color = accentTint!!.copy(alpha = 0.22f),
                            offset = DpOffset(skin.shadowOffsetX, skin.shadowOffsetY),
                        ),
                    )
                } else {
                    Modifier
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(tileShadow)
                        .clip(tileShape)
                        .then(
                            if (tileBrush != null) Modifier.background(tileBrush)
                            else Modifier.background(background)
                        )
                        .then(if (colorTile) Modifier.serviceTopGloss(dark = isDark, strength = 0.7f) else Modifier)
                        .then(
                            if (colorTile) Modifier.border(1.dp, accentTint!!.copy(alpha = 0.26f), tileShape)
                            else Modifier
                        )
                        .miuixPress { onServiceClick(entry) }
                        .padding(vertical = if (compact) 8.dp else 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 图标从"裸 glyph 摆在色块上"改成"白底小芯片"：
                    // 格子里没有标题以外的任何边界，裸图标会读成一颗漂浮的彩色符号；
                    // 白芯片把它按在格面上，也就和富卡片的图标衬底变成同一套语言。
                    // 单色态不加芯片 —— 极简皮肤下白块反而比裸图标更重。
                    if (colorTile) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 22.dp else 26.dp)
                                .clip(RoundedCornerShape(skin.radiusChip))
                                .background(ServiceIconTileBrush),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (icon.filled) display.iconFilled else display.icon,
                                contentDescription = display.title,
                                tint = tint,
                                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (icon.filled) display.iconFilled else display.icon,
                            contentDescription = display.title,
                            tint = tint,
                            modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                        )
                    }
                    Spacer(Modifier.height(skin.spaceSmall))
                    Text(
                        text = display.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
private fun ServiceEntry.matchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    val haystack = listOf(title, subtitle) + aliases + serviceAliases(title)
    return haystack.any { it.contains(q, ignoreCase = true) }
}

/** 服务搜索别名：不改变 UI，只提高搜索命中率。 */
private fun serviceAliases(title: String): List<String> = when (title) {
    // 座位并入图书馆后，搜「选座 / 预约」也要能命中这一格 —— 否则用户按旧入口名搜不到。
    "图书馆" -> listOf("借书", "借阅", "座位", "选座", "预约", "自习", "图书")
    "空教室查询" -> listOf("自习", "空闲教室", "教室")
    "考试成绩", "成绩查询", "我的课程" -> listOf("成绩", "分数", "gpa", "绩点", "课程")
    "修课情况" -> listOf("学分", "绩点", "培养方案")
    "选课" -> listOf("正选", "补退选", "公选", "选课系统")
    "评教" -> listOf("评价", "问卷", "教学评价")
    "校园卡" -> listOf("一卡通", "余额", "充值")
    "烟台乘车码" -> listOf("公交", "乘车", "扫码", "乘车码")
    "洗衣机" -> listOf("洗衣", "浣熊", "洗衣机")
    "宿舍电费" -> listOf("电费", "余额", "缴费", "用电")
    "洗澡" -> listOf("澡堂", "淋浴", "热水")
    "热水" -> listOf("饮水", "开水", "热水")
    "快递" -> listOf("取件", "取件码", "包裹")
    "校园网" -> listOf("网络", "wifi", "上网", "故障")
    "报修" -> listOf("维修", "故障", "宿舍报修")
    "今日校园" -> listOf("办事大厅", "智慧校园", "统一认证")
    "查询老师" -> listOf("教师", "老师", "办公室", "联系方式")
    else -> emptyList()
}
/**
 * **界面层已经接好原生页**的服务 key。
 *
 * ## 为什么需要这份名单，而不是让数据层自己判
 *
 * `CampusServiceRegistry` 把 `选课` 登记为 `CampusServiceType.NATIVE`，因为"它由原生页
 * 实现"；但数据层**看不到界面层有没有那个页**，于是 `capabilityOf` 只能保守地说
 * "NATIVE 类型且没登记 adapter ⇒ 未接入"。而实际上 `ServiceSubPage.CourseSelect`
 * 早就写好了（见 `选课` 那条 `onClick`）。
 *
 * 这正是这套能力角标最容易变成谎话的地方：**数据层的真，不等于界面的真**。
 * 所以补一份界面层自己的名单，判定时**先查这里**。
 *
 * ## 维护契约（由 `CampusServiceCapabilityTest` 守着）
 *
 * 这份名单里的每个 key 都必须真的有原生页可达。**判定与实现分家那一刻，
 * 界面就会开始说假话** —— 所以加 key 时请一并确认 `ServiceSubPage` 里有对应分支。
 *
 * 注意：`今天吃什么` / `地图导航` / `教室分布图` / `留声树洞` 等纯本地入口
 * **不需要**在这里登记 —— 它们没有注册表 key，天然就是原生可用。
 */
private val UI_NATIVE_SERVICE_KEYS: Set<String> = setOf(
    CampusServiceKeys.COURSE_SELECT,
    CampusServiceKeys.LIBRARY,
    CampusServiceKeys.CLASSROOM,
)

/**
 * 一个入口当前的能力档位 —— **界面层的最终裁决**。
 *
 * 纯函数、顶层 —— 好让它能被单测直接调用，也避免被 composable 的 lambda 捕获
 *（局部函数会让 `remember` 的键失效，见调用点的说明）。
 *
 * 判定顺序（顺序即优先级，别调换）：
 * 1. 无注册表 key ⇒ 纯本地页（地图导航 / 今天吃什么 / 学习笔记 / 留声树洞…），
 *    它们本来就能用 ⇒ [CampusServiceCapability.NATIVE]；
 * 2. 在 [UI_NATIVE_SERVICE_KEYS] 里 ⇒ 界面层有原生页 ⇒ [CampusServiceCapability.NATIVE]；
 * 3. 其余交给数据层 [CampusServiceResolver.capabilityOf]。
 */
internal fun serviceEntryCapability(
    entry: ServiceEntry,
    campus: CampusInfo,
    dormInfo: DormInfo?,
): CampusServiceCapability {
    val registryKey = entry.serviceRegistryKey ?: return CampusServiceCapability.NATIVE
    if (registryKey in UI_NATIVE_SERVICE_KEYS) return CampusServiceCapability.NATIVE
    return CampusServiceResolver.capabilityOf(registryKey, campus, dormInfo)
}

/** 供单测断言"名单里的 key 确实有原生页可达"，避免名单腐化成谎话。 */
internal val uiNativeServiceKeys: Set<String> get() = UI_NATIVE_SERVICE_KEYS

// ⚠️ `ServiceEntry.serviceRegistryKey` 已搬到 `ServiceEntry.kt`（现有两个消费者：
// 取 Adapter 摘要 + 判能力档位）。**不要在这里再写一份** —— 两份回退表会漂移。
/**
 * 把"能力角标"与"实时摘要角标"叠到同一条目上 —— **两种渲染器的唯一公共入口**。
 *
 * ## 为什么能力在这里叠加，而不是各自调用
 *
 * 富卡片与工具箱宫格是两个渲染器，此前各自写一遍
 * `entry.withSummary(entry.serviceRegistryKey?.let(summaries::get))`。
 * 若把能力判定也放到各自那一行，就会有两处必须记得同时改 ——
 * 而"两处真源迟早漂移"正是本项目反复踩到的坑。
 * 做成同一个函数后，**新增渲染器只要调它，就自动拿到能力角标**。
 *
 * ## 优先级：实时摘要 > 能力档位
 *
 * 摘要说"宿舍电费余额 12.4 元"比"能打开"重要得多，所以摘要在场时能力角标让位
 *（[withCapability] 在摘要之后不再覆盖）。反过来"未接入"的入口永远没有摘要
 *（没 adapter 就没有 summary），所以两档不会互相遮蔽。
 */
private fun ServiceEntry.withSummary(
    summary: ServiceSummary?,
    campus: CampusInfo,
    dormInfo: DormInfo?,
): ServiceEntry {
    if (summary == null) return withCapability(serviceEntryCapability(this, campus, dormInfo))
    val metrics = summary.metrics.joinToString(" · ") { "${it.label} ${it.value}" }
    val nextSubtitle = summary.subtitle?.takeIf { it.isNotBlank() } ?: metrics.ifBlank { subtitle }
    val nextBadge = when (summary.state) {
        ServiceSummaryState.READY -> summary.badge ?: badge
        ServiceSummaryState.NEEDS_BINDING -> "待绑定"
        ServiceSummaryState.NEEDS_AUTH -> "待登录"
        ServiceSummaryState.NEEDS_CONFIG -> "待配置"
        ServiceSummaryState.NEEDS_API -> "待接 API"
        ServiceSummaryState.UNAVAILABLE -> "不可用"
    }
    // 角标色调：只有"需要用户做点什么"才用 warning；"不可用"用 danger。
    // READY 时 badge 通常是余额/进度这类事实，用 info（强调色）而不是警告色 ——
    // 把"查到余额了"染成橙色会误导用户以为出了问题。
    val nextTone = when (summary.state) {
        ServiceSummaryState.READY -> BadgeTone.INFO
        ServiceSummaryState.UNAVAILABLE -> BadgeTone.DANGER
        else -> BadgeTone.WARNING
    }
    return copy(subtitle = nextSubtitle, badge = nextBadge, badgeTone = nextTone)
}
@Composable
internal fun ServiceCard(
    entry: ServiceEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = entry.onClick,
) {
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current
    // ── 图标策略：皮肤上限 × 用户用色强度 的合成结果（见 IconPresentation.resolveIcons）──
    // 此前这里只读用户偏好（`LocalAppearanceMode == PURE`），于是「极简」皮肤的
    // "单色线性图标"承诺无法兑现 —— 极简 + 彩虹仍然是彩色。
    //
    // 换成合成结果后，`colorful` 同时驱动 卡片底 / 图标色 / 图标衬底，三者同进同退。
    // 只把图标转单色而卡片底仍按类别上色会破相（彩色卡片配单色图标），
    // 所以这里刻意让一个标志管三处，而不是各自判断。
    val icon = currentIconPresentation()
    val compact = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    val skin = LocalSkin.current
    // 有语义分类的入口：颜色从当前主题派生（8 套配色下自动协调）；
    // 尚未迁移的入口回退到旧的硬编码色对。
    val accentTint = entry.accent?.let { serviceAccentColor(it) }
    val accentContainer = entry.accent?.let { serviceAccentContainer(it) }
    // 中性卡：皮肤不允许彩色，或深色主题 —— 卡片走 `surfaceCard`，与页面底色明度差很小。
    val neutralCard = !icon.colorful || isDark
    val colorfulCard = !neutralCard && accentTint != null
    val cardBackground = if (neutralCard) colors.surfaceCard else (accentContainer ?: entry.background)
    // 彩色卡用类目色渐变代替平涂：上深下浅，卡片自带光源感。
    //
    // 2026-10-22：0.30/0.10 → 0.42/0.16。服务页一次铺 20 多张卡，大半是同一个色相，
    // 30% 的浓度远看就是「同一张卡复制了 20 遍」；类目色是这页唯一能一眼分堆的线索。
    // 2026-10-24：两档 → 三档（0.46/0.20/0.12，见 [serviceSurfaceBrush]）——
    // 两档在中段直接线性过渡，卡面像平涂色纸；补一个中间色阶才有受光层次。
    // 只加这一层、不整卡铺满：副标题落在渐变的下半段（≤12% 类目色 + 一层白），
    // 与 `textSecondary #4B5563` 的对比度仍在 4.5:1 以上。
    val cardBrush = if (colorfulCard) serviceSurfaceBrush(accentTint!!) else null
    val iconTint = if (icon.colorful) (accentTint ?: entry.tint) else colors.accentMain
    // 彩色卡上的图标底改成白色渐变（见下方 Box）：卡本身有色，白底能把浓色 glyph 衬出来，
    // 不再是"同色叠同色"。
    val iconBackground = when {
        !icon.showContainer -> colors.surfaceHighlight
        !neutralCard -> colors.surfaceCard
        else -> accentContainer ?: entry.tint.copy(alpha = 0.14f)
    }
    val shape = RoundedCornerShape(skin.radiusCard)
    // 阴影也跟类目色走：白底上的黑投影会显灰，彩色光晕才是这页该有的"浮起"。
    val shadowColor = if (colorfulCard) {
        // 0.20 → 0.24：卡面加深后原来的光晕被吃掉一截，投影要跟着抬一点才还在。
        accentTint!!.copy(alpha = 0.24f)
    } else if (isDark) {
        skin.shadowColorDark
    } else {
        skin.shadowColorLight
    }
    val shadowModifier = if (skin.shadowBlur > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = skin.shadowBlur,
                spread = 0.dp,
                color = shadowColor,
                offset = DpOffset(skin.shadowOffsetX, skin.shadowOffsetY),
            ),
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            // 高度给**下界**而不是钉死：这张卡是「图标 + 标题 + 角标 + 副标题」四件竖排，
            // 内容才是决定高度的那一方。原来写死 78dp，超出部分被直接裁掉 ——
            // 副标题"有角标就只给一行"的那个降级分支就是为它打的补丁。
            // 同一行两张卡由 [fillMaxHeight] 拉平（调用点给外层 Row 加了
            // `height(IntrinsicSize.Max)`），所以不会一高一低，也不会再撑破内容。
            //
            // 下界 78→72：行高收到 1.3~1.4 倍后，内容本身只要 ~64dp，
            // 78 只是"留白"而不是"内容需要"，一屏少看见两张卡。
            .heightIn(min = if (compact) 60.dp else 72.dp)
            .fillMaxHeight()
            .then(shadowModifier)
            .clip(shape)
            .then(
                if (cardBrush != null) Modifier.background(cardBrush)
                else Modifier.background(cardBackground)
            )
            // 顶部高光：只给彩色卡。白卡/深色卡本来就是中性面，加一层白只会显灰。
            .then(if (colorfulCard) Modifier.serviceTopGloss(dark = isDark) else Modifier)
            // 彩色卡本身有色相区分；中性卡补一条极淡描边把边界找回来，否则白卡/深卡
            // 贴在底色上像一块没有轮廓的色块。
            // 2026-10：彩色卡也补同色描边（0.20 → 0.28）—— 渐变上浓下淡，下缘色量只有 12%，
            // 卡片右缘与浅色页底之间看不到边界，一张张卡像"晕"在背景里。
            // 0.28 是"看得见但不框死"的位置：再深会变成描边卡，和皮肤的软边承诺冲突。
            .then(
                when {
                    neutralCard -> Modifier.border(1.dp, colors.borderDivider, shape)
                    colorfulCard -> Modifier.border(1.dp, accentTint.copy(alpha = 0.28f), shape)
                    else -> Modifier
                }
            )
            // 左侧类目色竖条：贴左缘、长度 = 卡片直边（高 − 2×圆角），见 [serviceAccentRail]。
            // 只有彩色卡有 —— 中性卡（极简皮肤 / 深色主题）连卡面都没上色，
            // 单独长出一条彩色线会变成"这一页唯一不能解释的颜色"。
            // 排在描边之后画：3dp 的竖条要盖住左缘那 1dp 描边，否则圆头两端会被描边切开。
            .then(
                if (colorfulCard) {
                    Modifier.serviceAccentRail(accentTint!!, insetY = skin.radiusCard)
                } else Modifier
            )
            .miuixPress(onClick = onClick)
            // 2026-10 排版：横向内边距 12→10、图标 36→34、图标与文字间距 spaceMd→spaceSm，
            // 三处一共把文字列让宽约 12dp（≈12%）。二列卡的文字列原本只有 ~99dp，
            // 副标题「绑定宿舍后自动匹…」这类被截断就是宽度不够，不是字号问题。
            .padding(horizontal = if (compact) 9.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .then(
                    // 彩色卡：白色**渐变**衬底（见 [ServiceIconTileBrush]）。
                    // 纯白平涂像贴了块白纸，顶端留一点灰才有厚度；白底同时把类目色
                    // 图标衬到最高对比 —— 同色衬底会让图标和卡面只差一个色阶。
                    if (colorfulCard) Modifier.background(ServiceIconTileBrush)
                    else Modifier.background(iconBackground)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (icon.filled) entry.iconFilled else entry.icon,
                contentDescription = entry.title,
                tint = iconTint,
                modifier = Modifier.size(if (compact) 17.dp else 19.dp)
            )
        }
        Spacer(Modifier.width(skin.spaceSm))
        Column(modifier = Modifier.weight(1f)) {
            // 标题**独占一行**，不与角标共享宽度 —— 理由见下方那段实测记录。
            Text(
                text = entry.title,
                fontSize = 14.sp,
                // 显式给行高：只写 `fontSize` 会继承正文的 24sp 行高，
                // 14sp 的字被撑进 24sp 的行框，一张卡白长出 6dp。下面副标题同理，
                // 那才是"卡片普遍过大"的主因（10sp 的字 × 2 行 = 48dp）。
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 角标此前与标题同处一个 `Row`，而 Compose 先测量**无 `weight`** 的子项 ——
            // 角标的固有宽度直接从标题嘴里抢空间，次要信息挤掉主信息。
            //
            // ⚠️ 靠抠间距是**治标**：实测只把阈值从 4 字挪到 5 字。
            // 算一下就清楚：5 字标题（14sp 粗体 ≈ 70dp）+ 角标（≈35dp）+ 间隔 2dp = **107dp**，
            // 而卡片文字列只有约 **99dp** —— 这是**空间不足**，调参救不了。
            // 另起一行后标题永不因角标而截断（任意字数）。
            entry.badge?.let { label ->
                Spacer(Modifier.height(skin.spaceMicro))
                ServiceBadge(
                    label = label,
                    tone = entry.badgeTone,
                    colorful = icon.colorfulTags,
                    // 彩色卡本身就是一层色底，角标再用同色 14% 底会糊进去 ——
                    // 改为白底 + 彩字描边，状态在任何卡色上都读得清。
                    onTintedCard = !neutralCard,
                )
            }
            Spacer(Modifier.height(skin.spaceTiny))
            Text(
                text = entry.subtitle,
                fontSize = 10.sp,
                // 10sp 的字配 24sp 的继承行高 = 每行 2.4 倍空白。收到 1.4 倍后，
                // 固定两行从 48dp 降到 28dp，卡片才回到"信息密度"该有的样子。
                lineHeight = 14.sp,
                color = colors.textSecondary,
                // 两行都说满：卡片高度已改成内容驱动（`heightIn(min=)`），
                // 不再需要"有角标就让出一行"来迁就写死的 78dp。
                //
                // `minLines = 2` 不是浪费，是**让等高成立的前提**：
                // 同一行两张卡靠 `Row.height(IntrinsicSize.Max)` 拉平，而 Compose 算固有高度时
                // 用的是**整行宽度**（`weight` 不参与固有测量）。副标题若不固定占两行，
                // 在整行宽下"一行就放下"→ 固有高度被低估 → 卡片又被裁。
                // 钉死两行之后，卡片高度不再随文字宽度变化，这条误差就不存在了。
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 服务状态角标（"待绑定" / "待登录" / "待配置" / "不可用" …）。
 *
 * ## 为什么值得单开一个组件
 *
 * 它是 `colorfulTags` 的**唯一落点**。改造前 `ServiceEntry.badge` 被
 * [withSummary] 算出来却从不渲染 —— 等于把"这个服务为什么不能用"静默丢掉了：
 * 用户只看到服务点不动，不知道是**没绑定**、**没登录**还是**入口没接**。
 *
 * ## ⚠️ 布局契约：它必须**独占一行**，不要塞回标题那一行
 *
 * 角标是**无 `weight`** 的子项，而 Compose 会**先**测量无 `weight` 的子项 ——
 * 于是它的固有宽度直接从标题嘴里抢空间，次要信息挤掉主信息。
 *
 * 实测：5 字标题（"普通话考试"，14sp 粗体 ≈ 70dp）+ 角标（≈35dp）+ 间隔 2dp = **107dp**，
 * 而卡片文字列只有约 **99dp** —— 这是**空间不足**，不是调参能解决的。
 * 曾靠"收紧间距"处理，结果只是把截断阈值从 4 字挪到 5 字（"宿舍电费"好了、"普通话考试"仍断）。
 *
 * 守着这条的是 `SkinScreenshotTest` 的 `服务页真实卡片截图` ——
 * 它**复现真实的 2 列网格宽度约束**（`Row` + `weight(1f)`）。
 * 若图省事改成 `fillMaxWidth()`，卡片会宽到永不截断，那条测试就变成假的。
 *
 * ## 着色纪律
 *
 * - **中性**（皮肤不允许彩色，或色调本就是 NEUTRAL）→ `surfaceHighlight` + `textSecondary`
 * - **INFO / WARNING** → 语义色 14%（浅）/ 20%（深）衬底 + 同色文字
 * - **DANGER** → ⚠️ **不用低透明度 danger 当底色**。深色主题的 danger 是
 *   `0xFFEF9A9A`，压低透明度后压在 `0xFF1A1C1E` 上只偏移约 2 个色阶，
 *   等于"标了红但看不出来"。按项目既有纪律走 `surfaceCard` 底 + 35% danger 描边
 *   （与 `ErrorCard` 同款）。
 *
 * @param colorful 来自 `IconPresentation.colorfulTags` —— 与图标共用同一标志，
 *   所以不会出现"图标单色、角标却五彩"的错配。
 */
@Composable
internal fun ServiceBadge(
    label: String,
    tone: BadgeTone,
    colorful: Boolean,
    onTintedCard: Boolean = false,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val isDark = LocalDarkMode.current

    val semantic = when (tone) {
        BadgeTone.NEUTRAL -> null
        BadgeTone.INFO -> colors.accentMain
        BadgeTone.WARNING -> colors.warning
        BadgeTone.DANGER -> colors.danger
    }
    val tint = semantic ?: colors.textSecondary
    val showSemantic = colorful && semantic != null

    val shape = RoundedCornerShape(skin.radiusChip)
    val background: Color
    val contentColor: Color
    val borderColor: Color?

    if (!showSemantic) {
        background = colors.surfaceHighlight
        contentColor = colors.textSecondary
        // 极简 + 工具箱宫格时角标直接压在页面浅底上，`surfaceHighlight` 与底色几乎同色，
        // 没有描边会读成一枚"浮空的字"。描边只补边界，不再加底色。
        borderColor = colors.borderDivider
    } else if (tone == BadgeTone.DANGER || onTintedCard) {
        background = colors.surfaceCard
        contentColor = tint
        borderColor = tint.copy(alpha = 0.35f)
    } else {
        background = tint.copy(alpha = if (isDark) 0.20f else 0.14f)
        contentColor = tint
        borderColor = null
    }

    Row(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .padding(horizontal = skin.spaceXs, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            // 同上：不写行高就继承正文 24sp，9sp 的角标被撑成 26dp 高的一条，
            // 而服务页里大部分卡都带角标 —— 这张"隐形的第四条线"才是卡片偏高的主因。
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * 「常用服务」最多可选的条数。
 *
 * ⚠️ 它与 `AppContainer.DEFAULT_QUICK_SERVICE_KEYS` 的**条数应当一致** ——
 * 默认值比上限还多的话，用户一进弹窗就会看到"已选超过上限"这种自相矛盾的状态。
 * 2026-10：两者一起提到 **5**（此前默认 3、上限却是 4，本身就不对齐）。
 */
private const val MAX_QUICK_SERVICES = 5

@Composable
private fun QuickServiceEditDialog(
    allEntries: List<ServiceEntry>,
    selectedKeys: List<String>,
    hiddenKeys: List<String>,
    onSave: (List<String>) -> Unit,
    onToggleHidden: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    // 编辑弹窗里的图标也要跟随图标策略：否则用户在"极简"下打开编辑，
    // 会看到一排彩色多色图标，与刚离开的列表页不一致。
    val icon = currentIconPresentation()
    var picked by remember(selectedKeys) { mutableStateOf(selectedKeys) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 52.dp.toPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑常用服务", fontWeight = FontWeight.Bold) },
        text = {
            // 整个正文只有一条滚动列表：说明 → 已选 → 全部服务。
            // 以前只有「全部服务」那一截能滚，说明和「已选」是钉在上面不动的 ——
            // 已选满 5 行时它们就占了半屏，下面能滚的地方只剩一条缝，
            // 看起来像"上面那块浮在内容上面"（同 2026-10-22 服务子页那次，
            // 见 memory/03-pitfalls.md 第十五节）。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "最多选择 $MAX_QUICK_SERVICES 个；长按右侧手柄拖动排序；点眼睛隐藏 / 恢复服务",
                    fontSize = 12.sp,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(skin.spaceSm))
                if (picked.isEmpty()) {
                    Text("还没有选择常用服务", fontSize = 12.sp, color = colors.textTertiary)
                } else {
                    Text("已选", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                    Spacer(Modifier.height(skin.spaceXs))
                    picked.forEach { key ->
                        val entry = allEntries.firstOrNull { it.key == key } ?: return@forEach
                        val isDragging = draggingKey == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(skin.radiusChip))
                                .background(if (isDragging) colors.surfaceHighlight else colors.surfaceAlt)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (icon.filled) entry.iconFilled else entry.icon,
                                contentDescription = null,
                                tint = if (icon.colorful) (entry.accent?.let { serviceAccentColor(it) } ?: entry.tint) else colors.accentMain,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(skin.spaceSm))
                            Text(
                                text = entry.title,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                picked = picked - key
                                if (draggingKey == key) draggingKey = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(18.dp))
                            }
                            Icon(
                                imageVector = Icons.Outlined.DragHandle,
                                contentDescription = "拖动排序",
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .pointerInput(key) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingKey = key
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val current = picked
                                                val from = current.indexOf(draggingKey)
                                                if (from >= 0) {
                                                    val steps = (dragOffset / rowHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val to = (from + steps).coerceIn(0, current.lastIndex)
                                                        if (to != from) {
                                                            picked = current.toMutableList().apply {
                                                                add(to, removeAt(from))
                                                            }
                                                            dragOffset -= (to - from) * rowHeightPx
                                                        }
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingKey = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingKey = null
                                                dragOffset = 0f
                                            },
                                        )
                                    },
                            )
                        }
                        Spacer(Modifier.height(skin.spaceXs))
                    }
                }
                Spacer(Modifier.height(skin.spaceSm))
                Text("全部服务", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceXs))
                Column(
                    // 不再自带 weight(1f) + verticalScroll：整段正文已经是一条滚动列表，
                    // 这里再套一层会让「已选」那块继续钉住不动。
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    allEntries.forEach { entry ->
                        val checked = entry.key in picked
                        val hidden = entry.key in hiddenKeys
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(skin.radiusChip))
                                .clickable {
                                    picked = when {
                                        checked -> picked - entry.key
                                        picked.size >= MAX_QUICK_SERVICES -> picked
                                        else -> picked + entry.key
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (icon.filled) entry.iconFilled else entry.icon,
                                contentDescription = null,
                                tint = if (icon.colorful) (entry.accent?.let { serviceAccentColor(it) } ?: entry.tint) else colors.accentMain,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(skin.spaceMd))
                            Text(
                                text = entry.title,
                                fontSize = 14.sp,
                                color = if (hidden) colors.textTertiary else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onToggleHidden(entry.key) },
                                modifier = Modifier.size(28.dp).invisibleTouchTarget(),
                            ) {
                                Icon(
                                    imageVector = if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (hidden) "恢复显示" else "隐藏",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (checked) colors.accentMain else colors.surfaceAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) {
                                    Text("✓", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(picked); onDismiss() }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text("恢复默认") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
