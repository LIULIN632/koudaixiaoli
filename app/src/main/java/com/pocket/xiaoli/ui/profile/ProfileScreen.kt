package com.pocket.xiaoli.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.CampusConfig
import com.pocket.xiaoli.data.CampusInfo
import com.pocket.xiaoli.data.CampusServiceKeys
import com.pocket.xiaoli.data.service.CampusServiceCatalog
import com.pocket.xiaoli.data.service.CampusCardLauncher
import com.pocket.xiaoli.data.service.SchoolServiceLauncher
import com.pocket.xiaoli.ui.components.CardAccountDialog
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.data.electricity.DormElectricityRepository
import com.pocket.xiaoli.ui.electricity.ElectricityScreen
import com.pocket.xiaoli.ui.card.CampusCardScreen
import com.pocket.xiaoli.data.model.DormBuildingCatalog
import com.pocket.xiaoli.data.model.DormServiceRouter
import com.pocket.xiaoli.ui.dev.DeveloperLabScreen
import com.pocket.xiaoli.ui.home.CustomHomeScreen
import com.pocket.xiaoli.ui.profile.components.*
import com.pocket.xiaoli.ui.service.ServiceWebScreen
import com.pocket.xiaoli.ui.shower.ShowerScreen
import com.pocket.xiaoli.ui.treasurebox.TreasureBoxScreen
import com.pocket.xiaoli.ui.timetable.components.SchemeManagerDialog
import com.pocket.xiaoli.ui.theme.*
import com.pocket.xiaoli.util.CalendarExportUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.util.Log
import com.pocket.xiaoli.util.CrashLogExporter
import com.pocket.xiaoli.util.DiagnosticInput
import com.pocket.xiaoli.util.DiagnosticReport
import com.pocket.xiaoli.network.NetworkLogStore
import com.pocket.xiaoli.focus.FocusStats
import com.pocket.xiaoli.focus.focusTileHint
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 组装交给鲤鱼的日志分析请求。
 *
 * ## 为什么提示词要写得这么"啰嗦"
 *
 * 诊断报告本身只有设备/版本/数据量，**不含任何异常信息**（异常在崩溃日志里）。
 * 如果只丢一段报告过去，模型没有任何线索，只会回一句"信息不足，请提供更多细节"——
 * 用户会觉得这功能没用。所以这里必须：
 *
 * 1. **明确任务**：这是排查请求，不是闲聊；
 * 2. **点明关键**：把"服务页停留后闪退"这类用户描述的症状一起带上
 *    （用户在「我的」页点这个入口，语境就是"App 有问题"）；
 * 3. **给出判断维度**：区分"崩溃"与"被杀/卡顿"，因为后者在日志里没有堆栈，
 *    需要引导模型去看**网络日志**和**数据量异常**（比如某个表涨到几万行导致查询卡死）。
 *
 * ## 为什么不脱敏
 *
 * 用户在设置里配置的是**自己的**模型/Key，日志直接发给自己的服务商，
 * 与"导出文件发给开发者"是两回事。脱敏反而会抹掉定位问题需要的类名与行号。
 * 唯一处理是**限制长度**，避免一次请求把上下文窗口撑爆导致分析失败。
 *
 * @param reportBody [buildDiagnosticText] 生成的正文
 * @param crashLog   最近一次崩溃日志原文；null 表示本次会话没崩过
 */
internal fun buildAnalyzePrompt(reportBody: String, crashLog: String?): String {
    val crashSection = if (crashLog.isNullOrBlank()) {
        "（没有捕获到崩溃堆栈。可能的原因：被系统低内存杀掉、崩溃在原生代码里、" +
            "或用户主动划掉。请重点看下面的网络日志和数据量是否有异常。）"
    } else {
        crashLog.take(MAX_CRASH_CHARS)
    }
    return buildString {
        appendLine("帮我看看这份诊断日志，判断一下 App 为什么会出问题。")
        appendLine("症状：进入「服务」页停留一会儿后闪退。")
        appendLine()
        appendLine("```")
        appendLine(reportBody)
        appendLine("```")
        appendLine()
        appendLine("崩溃堆栈：")
        appendLine("```")
        appendLine(crashSection)
        appendLine("```")
        appendLine()
        appendLine("请按下面这几点回答，不要泛泛而谈：")
        appendLine("1. 堆栈里最可疑的一帧是哪个类、哪一行（如果有堆栈）；")
        appendLine("2. 如果**没有**堆栈，说明大概是被系统杀掉或卡死，请指出诊断报告里哪个数据量异常；")
        appendLine("3. 判断这是崩溃、内存问题、还是网络/数据库卡顿；")
        appendLine("4. 给出 1~3 条用户当下能自己做的规避动作。")
    }
}

/** 崩溃堆栈最多带这么多字符，防止一次请求撑爆上下文窗口。 */
private const val MAX_CRASH_CHARS = 12_000

// （原先此处有 `private enum class SettingsParent { GENERAL, HUB }` ——
//  它是「通用设置」中间层的返回锚点，随 2026-10 扁平化一并删除。见上面的说明。）

@Composable
fun ProfileScreen(
    onSubPageChange: (Boolean) -> Unit = {},
    onOpenImportEntry: () -> Unit = {},
    /**
     * 打开鲤鱼的「AI 设置」。
     *
     * 它是**跨 tab** 的（那条路由挂在 assistant 下），所以只能由导航层注入 ——
     * 「外观 → 鲤鱼」那一页现在只是个指针页（见 `AppearanceCategoryScreen`）。
     */
    onOpenAiSettings: () -> Unit = {},
    /**
     * 打开「专注计时」（番茄钟）页。
     *
     * 与 [onOpenAiSettings] 同理：它是**跨 tab** 的非 tab 路由（挂在导航层），
     * 「我的」页这一层拿不到 navController，所以由导航层注入。
     */
    onOpenFocus: () -> Unit = {},
    /**
     * 设置页的「检查更新」。
     *
     * 检查、下载、抽屉全部归导航层的 `AppUpdatePromptController` —— 更新提示要能
     * 在任何 tab 主动弹，不能只活在「我的」页里。这里只把点击转上去。
     */
    onCheckUpdate: () -> Unit = {},
    onBack: () -> Unit
) {
    val skinTokens = LocalSkin.current
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val scope = rememberCoroutineScope()
    val gson = remember { Gson() }

    // ⚠️ 当前方案 ID 必须在**这里**收集，不能在 composition 里读 `container.activeSchemeId.value`。
    //
    // 起因（2026-09-30 修 lint 时暴露）：原来在给子页面传参时写的是
    //     schemeId = container.activeSchemeId.value
    // `StateFlow.value` 是**非 Compose 可观察**的读取方式 —— 读它**不会建立订阅**，
    // 方案切换后这一处不会重组，看起来"值传进去了"，实际停在旧值上。
    // Compose 的 lint 规则 StateFlowValueCalledInComposition 专门抓这个
    //（本项目修好 lint 后首跑即命中本行）。
    //
    // 注意区分：在**事件回调/lambda 内部**读 `.value` 是正确的（那时要的就是"最新值"）,
    // 所以本文件里几处回调中的 `.value` 保留不动。需要订阅的只有 composition 里这种。
    //
    // 位置说明：本函数在所有子页面分支之前**没有早期 return**（全文只有 417 行一个
    // `return@LaunchedEffect`），所以这个收集可以安全地放在最前面，
    // 并且能和 390 行那份收集（供 Room Flow 使用）共存 —— 两处同名会冲突，故 390 行已改为复用本变量。
    val activeSchemeId by container.activeSchemeId.collectAsStateWithLifecycle()

    // ── 子页面 / 面板开关 ──
    var showAccountBinding by remember { mutableStateOf(false) }
    var boundAccount by remember { mutableStateOf(container.loadCredential(container.selectedCampus.value.jwBaseUrl ?: "campus_bind")) }
    // 原 showColorThemeDialog / showCornerRadiusDialog / showPetDialog 已移除：
    // 前两个的能力分别被「外观方案」和「外观 → 课表格子」吸收，宠物概念整组删除。
    // 这三个 State 都是"声明了、也渲染了对话框、但从没有一处置为 true"的死分支。
    //
    // 原 showDarkModeDialog / showSkinStyleDialog / showVibrationModeDialog /
    // showLayoutModeDialog 也已移除：它们是「通用设置」页里就地弹的 OptionsDialog，
    // 开关与渲染都已归到设置页（ProfileSettingsScreen）自己身上（见那里的说明）。
    // 只有「提前提醒时间」留在本页 —— 它的入口在设置页第一组，而设置页画在本函数末尾。
    var showReminderTimeDialog by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    // 外观中心当前打开的分类；null = 停在分类入口页
    var appearanceCategory by remember { mutableStateOf<AppearanceCategory?>(null) }
    var showTreasureBoxDialog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var showDeveloperLab by remember { mutableStateOf(false) }
    var showCustomHome by remember { mutableStateOf(false) }
    val developerUnlocked by container.developerUnlocked.collectAsStateWithLifecycle()

    // ⚠️ 上面这一串是**子页面**的开关（会独占一屏、走 return）。
    // 弹窗 / 底部面板类的一次性交互不在这里 —— 它们整体归 [ProfileDialogHost]，
    // 本页只在需要时往 `pendingQuickDialog` 投一个请求。

    // ── Settings sub-page states ──
    //
    // ⚠️ 这里**没有** showSettingsMain（「课表设置」）—— 那个页面已合并进 [ProfileSettingsScreen]，
    // 全 App 只剩一个设置根。原先它和 showSettings 是两个并列的根，用户看到两个都叫"设置"
    // 的东西，还得靠猜哪个装了什么。
    var showTimeSlotManagement by remember { mutableStateOf(false) }
    var showSemesterSettings by remember { mutableStateOf(false) }
    var showAdjustedWorkday by remember { mutableStateOf(false) }
    var profileServiceWeb by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showElectricityPage by remember { mutableStateOf(false) }
    var showCampusCardPage by remember { mutableStateOf(false) }
    /** 洗澡（趣智校园）原生页 —— 「我的」四格第二行左边那一格 */
    var showShower by remember { mutableStateOf(false) }
    // 「当前校区」对话框留在这里：它是 `showSettings` 页里的入口，而那一页画在本函数**下面**，
    // 开关放这里才能让弹窗盖在页面上（与 pendingQuickDialog 相反的方向）。
    var showCampusDialog by remember { mutableStateOf(false) }
    // 原 `showNicknameDialog` 已随「昵称」概念一起删除（2026-10）——见 ProfileIdentity.kt 的说明。

    /**
     * 「我的」页上唯一那条「设置」入口打开的总设置页。
     *
     * 合并掉「课表设置」之后它是**全 App 唯一的设置根**：课表 / 外观 / 服务功能 /
     * 关于 / 更新 / 贡献 / 致谢 / 危险操作全在里面（见 [ProfileSettingsScreen]）。
     */
    var showSettings by remember { mutableStateOf(false) }

    // 原先这里有 `settingsParent: SettingsParent`（HUB / GENERAL）+ `backToSettingsParent()`：
    // 「通用设置」曾是设置根的下级页，从它的下级子页返回时要回到"通用设置"那一层。
    //
    // 2026-10 把「通用设置」合并进设置根之后**中间层消失**，所有设置子页的返回锚点
    // 都只剩设置根一处 —— 二选一退化成常量。所以枚举与 state 一并删除，
    // 各调用点直接写 `showSettings = true`：保留一个只有单一取值的枚举，
    // 只会让后来者以为还有分支。

    /** 打开「我的」→ 设置。 */
    fun openSettingsHub() {
        showSettings = true
    }

    /**
     * 设置页请求的一次性确认框（清空数据 / 检查更新 / 关于 等）。
     *
     * 这些框的开关住在 [ProfileDialogHost] 里，而设置页在组合顺序上排在它**之后** ——
     * 设置页改不了那边的内部 state，所以走这个"请求信号"：置位 → host 消费 → 回调清空。
     */
    var pendingQuickDialog by remember { mutableStateOf<ProfileQuickDialog?>(null) }

    /**
     * 绑定信息页请求拉开的学习平台面板（管理登录态 / 去登录 / 同步作业）。
     *
     * 与 [pendingQuickDialog] 同一个道理：面板住在 [ProfileDialogHost] 里，
     * 而绑定信息页是**子页面**（走 return），手上没有那些开关。
     */
    var pendingLearningRequest by remember { mutableStateOf<ProfileLearningRequest?>(null) }

    /**
     * [ProfileDialogHost] 里是否有面板开着。**只用于藏底栏**。
     *
     * ⚠️ 它**不能**参与"要不要组合 host"的条件 —— 那会成环：
     * 面板打开 → 汇报 true → 条件变假 → host 被卸下 → 面板消失。
     * 所以只让「我的」页正文（[showHomeContent]）看这个值，host 自己始终挂着。
     */
    var dialogsVisible by remember { mutableStateOf(false) }

    /** 从设置根进入的下级子页（关于 / 更新 / 贡献 / 致谢 / 危险操作 / 开发者实验室 …），返回锚到设置根。 */
    fun openFromSettingsHub(block: () -> Unit) {
        showSettings = false
        block()
    }

    // Sub-page visibility tracking — hide bottom nav + handle system back
    //
    // ⚠️ 这里只统计**子页面**。「我的」页的弹窗 / 底部面板归 [ProfileDialogHost]，
    // 它们有两条独立通道：
    // - **藏底栏**：host 用 `onVisibilityChange` 把 `anyPanelOpen` 汇报给 [dialogsVisible]，并进下面
    // - **返回键**：host 自己的 `BackHandler` 处理。Compose 的 BackHandler 内层优先，
    //   而 host 组合在本函数之后，所以它天然先拿到返回键 —— 正合"弹窗浮在最上面，先关弹窗"。
    //   把弹窗画成整页的「检查更新」等也归它，所以这里不用管。
    //
    // ⚠️ 下面每一条都**必须**在 BackHandler 里有对应分支：漏一个的后果不是"返回键没反应"，
    // 而是 `hasSubPage=false` → BackHandler 根本没装上 → 系统返回键直接退出整个 App。
    // 「自定义首页」「开发者实验室」原先就漏在列表外，本次一并补上。
    val hasSubPage = showSettings || showCustomHome || showDeveloperLab || showTreasureBoxDialog
            || showTimeSlotManagement || showSemesterSettings || showAdjustedWorkday || showChangelog
            || showAppearance || showAccountBinding
            || profileServiceWeb != null || showElectricityPage || showCampusCardPage
            || showShower
            // 两个自有弹窗（都在设置页里触发、画在本函数末尾）
            || showReminderTimeDialog || showCampusDialog

    LaunchedEffect(hasSubPage) { onSubPageChange(hasSubPage || dialogsVisible) }
    BackHandler(enabled = hasSubPage) {
        when {
            // ── 自有悬浮弹窗（必须排在最前：它们浮在设置页之上）──
            showReminderTimeDialog -> showReminderTimeDialog = false
            showCampusDialog -> showCampusDialog = false
            // ── 独立子页（各自 return，一层只显示一个）──
            showTreasureBoxDialog -> showTreasureBoxDialog = false
            showTimeSlotManagement -> { showTimeSlotManagement = false; showSettings = true }
            showSemesterSettings -> { showSemesterSettings = false; showSettings = true }
            showAdjustedWorkday -> { showAdjustedWorkday = false; showSettings = true }
            showAppearance -> { showAppearance = false; showSettings = true }
            showChangelog -> { showChangelog = false; showSettings = true }
            showCustomHome -> { showCustomHome = false; showSettings = true }
            showDeveloperLab -> { showDeveloperLab = false; showSettings = true }
            // ── 设置根（渲染顺序在所有设置子页之后，见文件末尾）──
            showSettings -> showSettings = false
            // ── 服务类页面 ──
            showAccountBinding -> showAccountBinding = false
            profileServiceWeb != null -> profileServiceWeb = null
            showElectricityPage -> showElectricityPage = false
            showCampusCardPage -> showCampusCardPage = false
            showShower -> showShower = false
        }
    }

    if (showTreasureBoxDialog) {
        TreasureBoxScreen(
            onBack = { showTreasureBoxDialog = false },
            onImportCourse = { courseName, teacher, room, day, startSlot, endSlot ->
                val schemeId = container.activeSchemeId.value
                val course = com.pocket.xiaoli.data.entity.CourseEntity(
                    name = courseName, teacher = teacher, room = room,
                    dayOfWeek = day, startSlot = startSlot, endSlot = endSlot,
                    schemeId = schemeId,
                    colorIndex = (day * 3 + startSlot) % 15
                )
                scope.launch {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        app.repository.insert(course)
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "$courseName 已导入课表！", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            repository = app.repository,
            schemeId = activeSchemeId,
            zhengfangClient = container.zhengfangClient,
            schoolRegistry = container.schoolRegistry,
            appContainer = container
        )
        return
    }

    // ── Settings sub-pages ──
    // 设置根（showSettings）不在这里：它画在文件末尾（要让「检查更新」等弹窗浮在它上面）。
    // 这里只放"会 return 独占一屏"的设置子页，返回锚点统一是设置根（showSettings）。
    if (showCustomHome) {
        CustomHomeScreen(
            container = container,
            onBack = { showCustomHome = false; showSettings = true },
        )
        return
    }
    if (showTimeSlotManagement) {
        TimeSlotManagementScreen(onBack = { showTimeSlotManagement = false; showSettings = true })
        return
    }
    if (showSemesterSettings) {
        SemesterSettingsPage(container = container, onBack = { showSemesterSettings = false; showSettings = true })
        return
    }
    if (showAdjustedWorkday) {
        AdjustedWorkdayScreen(container = container, onBack = { showAdjustedWorkday = false; showSettings = true })
        return
    }
    if (showChangelog) {
        ChangelogScreen(onBack = { showChangelog = false })
        return
    }

    if (showDeveloperLab) {
        DeveloperLabScreen(onBack = { showDeveloperLab = false; showSettings = true })
        return
    }
    // 原 `val nickname` / `val school` 两处订阅已移除（2026-10）：身份卡不再显示它们，
    // 而「我的」页正文现在只收一个 `boundCount: Int` —— 见 ProfileHomeContent 的说明。
    // 原 `val cornerRadius by container.cornerRadius` 已移除：唯一读它的圆角对话框已删。
    // 圆角这个能力本身还在（`TimetableScreen` 在画格子、`AppearanceViewModel` 在写值），
    // 只是「我的」页不再需要订阅它 —— 少一个 StateFlow 订阅，少一次无谓重组。
    // 原 `darkMode` / `skinStyle` / `vibrationMode` / `borderStyle` 四处订阅已移除，
    // 原因同上：「通用设置」页已合并进设置页，这四个值现在由它自己订阅。
    val reminderEnabled by container.reminderEnabled.collectAsStateWithLifecycle()
    val reminderMinutes by container.reminderMinutes.collectAsStateWithLifecycle()
    // 原 `val aiName by container.aiName` 已移除：它唯一的消费者是已被删除的 AiNameDialog。
    // 原 `val aiAvatarPhase by container.aiAvatarPhase` 已移除：身份卡不再叠鲤鱼徽标。
    val showTreasureBox by container.showTreasureBox.collectAsStateWithLifecycle()
    val showWidget by container.showWidget.collectAsStateWithLifecycle()
    // WithLifecycle + 不在参数里内联构造 Room 冷 Flow：
    // `getTasksByScheme` / `getAllHomework` / `getCoursesByScheme` 每被调用一次都会生成
    // 一个新的 Flow 实例，写在 collectAsState 的参数里等于每次重组都新建一个、
    // 重新向 Room 的 InvalidationTracker 注册观察者。改成 WithLifecycle 并保持同一写法。
    val allSchemes by app.repository.allSchemes.collectAsStateWithLifecycle(initialValue = emptyList())
    // `activeSchemeId` 已在函数开头收集（那里要早于所有子页面分支）—— 此处不再重复收集。
    val allTasks by app.repository.getTasksByScheme(activeSchemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allHomework by app.repository.getAllHomework()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val schemeCourses by app.repository.getCoursesByScheme(activeSchemeId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedCampus by container.selectedCampus.collectAsStateWithLifecycle()
    val dormInfo by container.dormInfo.collectAsStateWithLifecycle()
    val electricityCampus = dormInfo?.let { DormServiceRouter.serviceCampus(it, selectedCampus) }
    // 宿舍编号也算 key：改了编号就换了认人方式，旧余额必须重查（与电费页同一口径）
    val electricityBalanceKey = dormInfo?.let { it.label + "|" + it.dormCode }.orEmpty()
    var electricityBalance by remember(electricityBalanceKey) {
        mutableStateOf(dormInfo?.let { DormElectricityRepository.cachedBalance(it) })
    }
    var electricityBalanceLoading by remember { mutableStateOf(false) }
    var electricityAccountId by remember { mutableStateOf<String?>(null) }
    var electricityItemId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dormInfo, selectedCampus.id, profileServiceWeb) {
        if (profileServiceWeb != null) return@LaunchedEffect
        val dorm = dormInfo
        if (dorm == null || !dorm.isBound || electricityCampus?.id != DormBuildingCatalog.CAMPUS_SOUTH) {
            electricityBalance = null
            electricityBalanceLoading = false
            electricityAccountId = null
            electricityItemId = null
            return@LaunchedEffect
        }
        electricityBalanceLoading = true
        val result = DormElectricityRepository.query(dorm).getOrNull()
        if (result != null) {
            electricityBalance = result.balance
        }
        electricityAccountId = result?.accountId ?: electricityAccountId
        electricityItemId = result?.itemId ?: electricityItemId
        electricityBalanceLoading = false
    }

    LaunchedEffect(selectedCampus.id) {
        boundAccount = selectedCampus.jwBaseUrl?.let { container.loadCredential(it) }
            ?: container.loadCredential("campus_bind")
    }
    // 「我的」页服务格统一入口：能解析就内置打开，解析不到给提示
    fun openProfileService(
        key: String,
        title: String,
        targetCampus: CampusInfo = selectedCampus,
    ) {
        // 校园卡（易校园）：装了 App 拉起；没装进门户里的易校园入口（免登），不再丢到官网
        //
        // ⚠️ 2026-10：「我的」页四格里那格换成了「专注计时」，所以**本文件里暂时没有
        // 调用方再传 CAMPUS_CARD** 了。这条分支连同它对 `ProfileQuickDialog.CARD_ACCOUNT`
        // 的投递**刻意保留**：它与 `ui/treasurebox/TreasureBoxScreen.kt:341` 那份是同一套
        // 易校园逻辑（装了 App 拉起 / 没装走门户 / 没绑号就地弹窗），留着是为了将来这一格
        // 再加回来时不至于重写。真要删，得连 `ProfileQuickDialog.CARD_ACCOUNT`（枚举 +
        // `ProfileDialogHost` 的处理 + 那个弹窗）一起删 —— 那是独立的一次清理，不混在这次改动里。
        if (key == CampusServiceKeys.CAMPUS_CARD) {
            // 绑定过易校园账号就直接给原生余额页；没绑定才走 App / 门户那条路
            if (container.loadCardCredential() != null) {
                showCampusCardPage = true
                return
            }
            // 没绑账号就地弹窗解决（需求方反馈：不该只能跳到门户网页）
            pendingQuickDialog = ProfileQuickDialog.CARD_ACCOUNT
            return
        }
        val service = CampusServiceCatalog.resolve(key, title, targetCampus)
        if (service == null) {
            Toast.makeText(context, "该服务暂未配置入口", Toast.LENGTH_SHORT).show()
            return
        }
        var serviceToOpen = service
        if (key == CampusServiceKeys.ELECTRICITY) {
            val accountId = electricityAccountId
            val itemId = electricityItemId
            if (!accountId.isNullOrBlank() && !itemId.isNullOrBlank()) {
                // 已匹配到自己的户号时，直接进该户号的缴费页，避免打开 payItemList 暴露其他宿舍信息。
                serviceToOpen = service.copy(
                    target = "https://ydny.ytu.edu.cn/pms/wechat/ytdx/payItemIndex" +
                        "?accountId=" + accountId + "&itemType=METER&itemId=" + itemId
                )
            }
        }
        SchoolServiceLauncher.open(
            service = serviceToOpen,
            context = context,
            webViewSink = { url, t -> profileServiceWeb = url to t }
        )
    }

    /** 宿舍相关服务按绑定楼栋决定走烟理还是烟大南校。 */
    fun openDormService(key: String, title: String) {
        openProfileService(
            key = key,
            title = title,
            targetCampus = DormServiceRouter.serviceCampus(dormInfo, selectedCampus),
        )
    }

    // 原 `appearanceMode` / `campusLayoutMode` 的订阅已移除：它们的消费者是「通用设置」页，
    // 而那一页 2026-10 已合并进设置页 —— 值由 ProfileSettingsScreen 自己订阅，
    // 父页再订一份只会多两次无谓重组。
    var chaoxingBound by remember { mutableStateOf(container.hasLearningCookie(LearningPlatform.CHAOXING)) }
    var zhihuishuBound by remember { mutableStateOf(container.hasLearningCookie(LearningPlatform.ZHIHUISHU)) }
    val unfinishedHomeworkCount by remember(allHomework, activeSchemeId) {
        derivedStateOf { allHomework.count { (it.schemeId == activeSchemeId || it.schemeId == 0L) && !it.completed } }
    }

    // 原先这里按首次启动时间戳现算「陪伴 N 天」（`first_launch_time`）并喂给身份卡。
    // 2026-10 整条删除：「陪伴天数」与 PetCard 同源 —— 是从别的软件带来的概念，
    // 与「口袋小鲤」的定位无关（见 ProfileIdentity.kt 与 components/ProfileComponents.kt 的同一笔账）。
    // ⚠️ 那个 prefs 键**除它之外没有任何消费者**（全仓只此一处读写），所以连写入一起删，
    // 不留一个只有自己在写、没人读的键。老用户机上残留的旧值无害，不必迁移。

    val colors = LocalPocketXiaoliColors.current

    // ── 外观中心所需的状态 ──
    // 底栏样式用「用户选择值」（不是回落后结果）：设置页要显示用户选了什么，
    // 否则低版本上选了玻璃会立刻弹回胶囊，看着像没保存。
    // 两者都从 StateFlow 收集 —— 直接读 store 属性不会触发重组。
    val bottomBarStyleSelected by container.bottomBarStyleStore.observe()
        .collectAsStateWithLifecycle(initialValue = container.bottomBarStyleStore.selected)
    val tabAppearance by container.tabAppearanceStore.state.collectAsStateWithLifecycle()

    // ── 通知权限（Android 13+）──
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(reminderEnabled) {
        if (reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * 采集并生成诊断正文。
     *
     * 是 `suspend` 且**必须在 IO 线程调用**：里面的数据量统计都要查 Room，
     * 主线程查库会卡 UI。早期想用 `remember` 在组合期算，但那会阻塞主线程 ——
     * Repository 暴露的是 `Flow` 而非 `StateFlow`，取不到 `.value`，
     * 只能 `first()` 挂起等待。
     *
     * 任一项查询失败都退化为 0，**不因为统计拿不到就让整个导出失败** ——
     * 用户要的是日志，不是完美的统计。
     */
    suspend fun buildDiagnosticText(): String {
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val dbVersion = runCatching {
            app.database.openHelper.readableDatabase.version
        }.getOrDefault(0)
        suspend fun <T> Flow<List<T>>.count(): Int =
            runCatching { first().size }.getOrDefault(0)

        return DiagnosticReport.build(
            DiagnosticInput(
                appVersionName = pkg?.versionName ?: "未知",
                appVersionCode = pkg?.longVersionCode?.toInt() ?: 0,
                androidRelease = android.os.Build.VERSION.RELEASE.orEmpty(),
                sdkInt = android.os.Build.VERSION.SDK_INT,
                deviceModel = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL,
                dbVersion = dbVersion,
                courseCount = app.repository.allCourses.count(),
                schemeCount = app.repository.allSchemes.count(),
                timeSlotCount = app.repository.allTimeSlots.count(),
                homeworkCount = app.repository.getAllHomework().count(),
                taskCount = app.repository.getTasksByScheme(activeSchemeId).count(),
                savedGradeCount = app.database.savedGradeDao().getAll().count(),
                roomCount = container.roomCells.value.size,
                poiCount = container.customPois.value.size,
                recentError = null,
            )
        )
    }

    val crashExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                var exportError: Exception? = null
                var written = 0
                withContext(Dispatchers.IO) {
                    try {
                        // 关键修复：以前写法是「没崩溃就整个 if 跳过，然后照样提示已导出」，
                        // 用户拿到空文件还不知道为什么。
                        // 现在无论有没有崩溃记录都写出**有内容**的文件：
                        // 崩溃堆栈（如有）+ 诊断报告 + 网络日志。
                        val body = CrashLogExporter.build(
                            reportBody = buildDiagnosticText(),
                            crashLogFile = app.crashHandler.getLatestCrashLog(),
                            // ⚠️ 必须用 exportLines()：它会对 URL 做脱敏（ticket / token / code…）。
                            // 这里曾经自己拼 `e.url`，把 CAS ticket 原样写进了导出的 txt。
                            networkLogs = NetworkLogStore.exportLines(),
                        )
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(body.toByteArray(Charsets.UTF_8))
                            written = body.length
                        }
                    } catch (e: Exception) {
                        exportError = e
                        Log.e("ProfileScreen", "导出失败: exportLog", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    val msg = when {
                        exportError != null -> "导出失败，请重试"
                        written == 0 -> "写入失败（存储空间或权限问题）"
                        else -> "已导出 ${written / 1024}KB 日志"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Calendar permission launcher ──
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            exportToCalendar(app, container, scope, context)
        } else {
            Toast.makeText(context, "需要日历权限才能导入课表", Toast.LENGTH_SHORT).show()
        }
    }

    val onImportToCalendar: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            == PackageManager.PERMISSION_GRANTED) {
            exportToCalendar(app, container, scope, context)
        } else {
            calendarPermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    // ── Settings sub-pages (need state, render before main content) ──
    // 原 `if (showFeatureToggles) FeatureToggleScreen(...)` 已删除：
    // 三个开关（百宝箱 / 桌面小组件 / 自动检查更新）现在直接平铺在设置页的「服务功能」组里，
    // 独立页面 `FeatureToggleScreen` 与它的 State 一并移除（两处入口同一件事 = 认知负担）。
    if (showAppearance) {
        // 「外观」现在是**分类入口**（按界面分）。课表分类再进原有的 AppearanceScreen。
        // 改造前这里是直接打开 AppearanceScreen —— 那一页其实只管课表格子，
        // 但名字叫「个性化配置」，用户会以为首页/服务的显示项也在里面。
        when (val cat = appearanceCategory) {
            null -> AppearanceHubScreen(
                onBack = { showAppearance = false; showSettings = true },
                onOpenCategory = { appearanceCategory = it },
                bottomBarStyle = bottomBarStyleSelected,
                tabAppearance = tabAppearance,
            )

            AppearanceCategory.TIMETABLE -> AppearanceScreen(
                container = container,
                onBack = { appearanceCategory = null },
            )

            else -> AppearanceCategoryScreen(
                category = cat,
                store = container.tabAppearanceStore,
                bottomBarStyle = bottomBarStyleSelected,
                onBack = { appearanceCategory = null },
                onBottomBarStyle = { container.bottomBarStyleStore.set(it) },
                onOpenTimetableAppearance = { appearanceCategory = AppearanceCategory.TIMETABLE },
                onOpenHomeCustomize = {
                    // 离开外观中心去首页自定义：把外观这条分支收掉，
                    // 否则从自定义首页返回时会弹回外观分类页（多一层无意义的返回）
                    appearanceCategory = null
                    showAppearance = false
                    // 返回锚点统一是设置根（扁平化后已无「通用设置」中间层）
                    showCustomHome = true
                },
                onOpenAiSettings = {
                    // 与上面「自定义首页」同一个理由：去的是**另一个 tab 的页面**，
                    // 不把外观这条分支收掉的话，从 AI 设置返回时会弹回外观分类页 ——
                    // 而用户的心智是"我从鲤鱼页的设置过来的，回去也该是它"。
                    appearanceCategory = null
                    showAppearance = false
                    onOpenAiSettings()
                },
            )
        }
        return
    }

    if (showAccountBinding) {
        AccountBindingScreen(
            container = container,
            chaoxingBound = chaoxingBound,
            zhihuishuBound = zhihuishuBound,
            // 「管理学习平台 / 去登录」把请求交给 [ProfileDialogHost]（它持有那些面板的开关），
            // 并带上"关掉后回绑定信息页"的意图 —— 这样返回时用户还在他刚操作的那一页。
            onOpenLearningPlatform = { platform ->
                pendingLearningRequest = ProfileLearningRequest(platform, returnToBinding = true)
                showAccountBinding = false
            },
            onManageLearning = {
                pendingLearningRequest = ProfileLearningRequest(LearningPlatform.CHAOXING, returnToBinding = true)
                showAccountBinding = false
            },
            onSyncLearning = {
                pendingLearningRequest = ProfileLearningRequest(LearningPlatform.CHAOXING, sync = true)
            },
            onBack = { showAccountBinding = false },
            onBoundChange = {
                boundAccount = selectedCampus.jwBaseUrl?.let { container.loadCredential(it) }
                    ?: container.loadCredential("campus_bind")
            }
        )
        return
    }

    if (showElectricityPage && profileServiceWeb == null) {
        ElectricityScreen(
            dormInfo = dormInfo,
            fallbackCampus = selectedCampus,
            onBack = { showElectricityPage = false },
            onOpenWeb = { url, title -> profileServiceWeb = url to title },
            onBalanceLoaded = { loaded ->
                electricityBalance = loaded?.balance
                electricityAccountId = loaded?.accountId
                electricityItemId = loaded?.itemId
            },
        )
        return
    }

    if (showCampusCardPage && profileServiceWeb == null) {
        val card = container.loadCardCredential()
        CampusCardScreen(
            phone = card?.first.orEmpty(),
            password = card?.second.orEmpty(),
            onBack = { showCampusCardPage = false },
            onOpenRecharge = {
                scope.launch {
                    CampusCardLauncher.open(context, selectedCampus) { url, t ->
                        profileServiceWeb = url to t
                    }
                }
            },
        )
        return
    }

    profileServiceWeb?.let { (url, title) ->
        ServiceWebScreen(url = url, title = title, onBack = { profileServiceWeb = null })
        return
    }

    // 洗澡（趣智校园）原生页。放在「我的」正文之前 —— 它独占一屏，
    // 与上面那些子页同构（各有自己的 return，一层只显示一个）。
    if (showShower) {
        ShowerScreen(onBack = { showShower = false })
        return
    }

    /**
     * 洗澡格子的副标题：**趣智钱包余额**（2026-10 用户要求，替换原来的姓名 / 打码手机号）。
     *
     * 数据与洗澡页顶部那个大数字同源（`QzxyRepository.campusBalance`），
     * 所以两处不会出现两个余额。放在「洗澡页 return」之后：洗澡页自己会查一次，
     * 这里不必在它开着时再查；从洗澡页返回后正文重新组合，拿到的就是刚消费后的新值。
     *
     * ⚠️ 2026-10-24 真机核对：这个数字是**趣智钱包**的余额，不是一卡通 ——
     * 接口叫 `/settlement/campus/userInfo`，但钱的归属是趣智，文案不能跟着接口名走。
     *
     * 四种「没有数字」的状态由 [showerTileHint] 分开表达 —— 查不到不能写成 ¥0.00。
     */
    val showerSession by container.qzxySessionStore.session.collectAsStateWithLifecycle()
    var showerBalance by remember(showerSession?.telephone) { mutableStateOf<Double?>(null) }
    var showerBalanceSigned by remember(showerSession?.telephone) { mutableStateOf<Boolean?>(null) }
    var showerBalanceLoading by remember(showerSession?.telephone) {
        mutableStateOf(showerSession != null)
    }
    LaunchedEffect(showerSession?.telephone) {
        val session = showerSession
        if (session == null) {
            showerBalance = null
            showerBalanceSigned = null
            showerBalanceLoading = false
            return@LaunchedEffect
        }
        showerBalanceLoading = true
        val info = container.qzxyRepository.campusBalance().getOrNull()
        showerBalance = info?.amountYuan
        showerBalanceSigned = info?.isSigned
        showerBalanceLoading = false
    }
    val showerHint = showerTileHint(
        loggedIn = showerSession != null,
        loading = showerBalanceLoading,
        balance = showerBalance,
        signed = showerBalanceSigned,
    )

    // 「我的」页正文。
    //
    // ⚠️ 只在**没有子页、也没有面板**时组合：
    // - 有子页（设置 / 通用设置 / 更新日志 …）时它们是不透明整页，正文留着既看不见又要重组；
    //   尤其正文里有几个 Room Flow 的观察者（待办 / 作业 / 课表），挂着白跑。
    // - 有面板时（检查更新 / 清空数据 …）它盖住的同样是正文，同理。
    //
    // ⚠️ 而这个条件**不能**用到 [ProfileDialogHost] 上：那会成环 ——
    // 面板打开 → dialogsVisible=true → host 被卸下 → 面板消失。
    // host 始终挂着，只让正文看这个值。
    if (!hasSubPage && !dialogsVisible) {
        // ── 专注统计（四格里那枚「专注计时」磁贴 + 它下面那一整块图表）──
        //
        // 只开**一个** Room 观察者，整块统计用 `FocusStats.focusStatsView` **一次算完** ——
        // 与专注页调的是同一个函数，不会出现"这里说 50 分钟、专注页说 45 分钟"。
        //
        // 窗口取 `startOfRecentWeeks()`（12 周）而不是 7 天：热力图要 12 周的历史，
        // 只观察 7 天的话热力图会大片空白（`focusStatsView` 自己切出 7 天 / 30 天 / 今天）。
        //
        // ⚠️ 观察者放在这个 if 分支**里面**（不是函数顶部）：子页/面板打开时正文不渲染，
        // 挂在外头就是白跑查询 —— 理由与上面的注释同源。
        val focusSessions by remember {
            container.focusController.observeSince(FocusStats.startOfRecentWeeks())
        }.collectAsStateWithLifecycle(emptyList())
        // 用 `today` 也当 remember 的 key：应用开着过午夜后重组一次就会换成新的一天
        //（光靠 sessions 变，日期会停在昨天 —— 这正是 `FocusStats` 坚持在 Kotlin 里现算
        // 窗口边界的那类坑）。
        val today = LocalDate.now()
        val focusStats = remember(focusSessions, today) { FocusStats.focusStatsView(focusSessions) }
        val focusToday = remember(focusSessions, today) { FocusStats.statOf(focusSessions, today) }

        // 身份卡只收一个"绑好了几项"的计数（教务 / 宿舍 / 学习平台，0..3），
        // **一个具体值都不传** —— 见 ProfileHomeContent 与 ProfileIdentity.kt 的说明。
        val boundCount = listOf(
            boundAccount?.first?.isNotBlank() == true,
            dormInfo?.isBound == true,
            chaoxingBound || zhihuishuBound,
        ).count { it }
        ProfileHomeContent(
            boundCount = boundCount,
            electricityBalance = electricityBalance,
            electricityLoading = electricityBalanceLoading,
            onOpenElectricity = {
                if (dormInfo?.isBound == true) showElectricityPage = true
                else openDormService(CampusServiceKeys.ELECTRICITY, "宿舍电费")
            },
            focusHint = focusTileHint(focusToday),
            onOpenFocus = onOpenFocus,
            focusStats = focusStats,
            showerHint = showerHint,
            onOpenShower = { showShower = true },
            onOpenExpress = { pendingQuickDialog = ProfileQuickDialog.EXPRESS_PICKUP },
            onOpenBinding = { showAccountBinding = true },
            onSchemeManager = { pendingQuickDialog = ProfileQuickDialog.SCHEME_MANAGER },
            onImportMenu = { onOpenImportEntry() },
            onShareMenu = { pendingQuickDialog = ProfileQuickDialog.SHARE_MENU },
            onSettings = { openSettingsHub() },
        )
    }

    // ═══════════════════════════════════════════
    //  临时面板：弹窗 / 底部面板 / 确认框
    // ═══════════════════════════════════════════
    //
    // 这些面板原先就堆在本函数里（占了约 580 行），现在整体搬到 [ProfileDialogHost]。
    // 分工按**生命周期**：会独占一屏、要参与返回层级的"子页面"留在本函数，
    // 弹一下就走的"临时面板"归那个文件 —— 改一个弹窗不必再在一屏装不下的范围里
    // 找它的 State、渲染分支、返回分支三处。
    //
    // 位置要求：必须在设置页（下方）**之前** —— 设置页自己拉开的都是这里的悬浮框，
    // 它作为整页画在面板下面才能真正盖住「我的」页正文。
    ProfileDialogHost(
        app = app,
        container = container,
        selectedCampus = selectedCampus,
        dormInfo = dormInfo,
        schemes = allSchemes,
        activeSchemeId = activeSchemeId,
        allHomework = allHomework,
        developerUnlocked = developerUnlocked,
        // "学习平台登录完回绑定信息页"这条跨层路径：目标页的开关归本函数所有，
        // 面板只回调"该回绑定页了"，不自己存一份 showAccountBinding。
        onOpenAccountBinding = { showAccountBinding = true },
        onOpenCampusCardPage = { showCampusCardPage = true },
        onOpenChangelog = { openFromSettingsHub { showChangelog = true } },
        onCheckUpdate = onCheckUpdate,
        pendingQuickDialog = pendingQuickDialog,
        onConsumeQuickDialog = { pendingQuickDialog = null },
        pendingLearningRequest = pendingLearningRequest,
        onConsumeLearningRequest = { pendingLearningRequest = null },
        onVisibilityChange = { dialogsVisible = it },
    )

    // ═══════════════════════════════════════════
    //  设置总入口（「我的」页唯一那条「设置」进来）
    // ═══════════════════════════════════════════
    //
    // ⚠️ 为什么画在这里（全函数最后、且**不 return**）
    //
    // 它是"最后兜底"的一层：上面所有子页一旦渲染就会 `return`，所以走到这里还成立
    // 的 `showSettings` 就是真的要显示设置页。
    //
    // 但不能像其它子页那样 `return` —— 设置页自己会拉开「检查更新」「清空数据」等弹窗，
    // 而那些弹窗的渲染代码在更下面。一 `return` 就等于把弹窗全部掐掉，
    // 表现是"点了没反应"。所以这里条件绘制，把后面的弹窗留给弹窗自己画。
    //
    // 而正因为要画在弹窗之前，它就没法像其它子页那样提前 `return` 掉「我的」页正文 ——
    // 正文那段在更上面。所以正文自己按 `!hasSubPage` 判断要不要组合（见上面的注释），
    // 设置页打开时底下不会再多画一遍首页。
    if (showSettings) {
        ProfileSettingsScreen(
            container = container,
            onBack = { showSettings = false },
            // ── 课表 ──
            // 这些子页的返回锚点现在**只有一处**：设置根。扁平化之后不再有「通用设置」
            // 那个中间层，返回时也就没有"回到哪一层"的歧义（原先靠 settingsParent 猜）。
            onTimeSlotManagement = { openFromSettingsHub { showTimeSlotManagement = true } },
            onSemesterSettings = { openFromSettingsHub { showSemesterSettings = true } },
            onAdjustedWorkday = { openFromSettingsHub { showAdjustedWorkday = true } },
            onCampusSelect = { showCampusDialog = true },
            onImportToCalendar = onImportToCalendar,
            // ── 课表（含上课提醒）──
            onReminderTime = { openFromSettingsHub { showReminderTimeDialog = true } },
            // ── 服务功能 ──
            onCustomHome = { openFromSettingsHub { showCustomHome = true } },
            // ── 服务功能（功能总开关）──
            onTreasureBox = { showTreasureBoxDialog = true },
            // ── 更新 ──
            onCheckUpdate = { pendingQuickDialog = ProfileQuickDialog.CHECK_UPDATE },
            onChangelog = { openFromSettingsHub { showChangelog = true } },
            // ── 关于（问题排查：清理缓存 / 导出日志 / 让鲤鱼看看）──
            onClearCache = {
                container.clearWebCacheAndCookies()
                Toast.makeText(context, "已清除缓存与所有网页登录状态", Toast.LENGTH_SHORT).show()
            },
            onExportLog = {
                // 不再用「是否有崩溃日志」做闸门：没有崩溃时也要能导出完整诊断报告，
                // 否则用户只会看到「暂无崩溃日志」，拿不到任何可用信息。
                crashExportLauncher.launch(CrashLogExporter.defaultFileName())
            },
            onAnalyzeLog = {
                // 把诊断报告直接交给鲤鱼分析。这里**不复制一份日志采集逻辑** ——
                // 复用 buildDiagnosticText()，保证"导出的文件"与"AI 看到的"是同一份口径。
                //
                // 刻意**不**在这里判断"模型是否已配置"再拦下用户：
                // 鲤鱼页自己就有 NotConfiguredBanner（能直接跳设置），
                // 在这里弹一句"先去配置"等于把用户堵在死路上 —— 跳过去反而能看到去哪配。
                scope.launch {
                    val (report, crash) = withContext(Dispatchers.IO) {
                        val r = runCatching { buildDiagnosticText() }
                            .getOrElse { "（诊断信息采集失败：${it.javaClass.simpleName}）" }
                        // 读文件走 IO；崩溃日志可能很大，读不到就当作"本次没崩"
                        val c = runCatching { app.crashHandler.getLatestCrashLog()?.readText() }.getOrNull()
                        r to c
                    }
                    container.requestAiAnalyze(buildAnalyzePrompt(report, crash))
                    if (container.assistantLlmConfig.isConfigured) {
                        Toast.makeText(context, "已交给鲤鱼分析，正在跳转…", Toast.LENGTH_SHORT).show()
                    }
                    container.requestNav("assistant")
                }
            },
            // ── 关于（信息与反馈）──
            onAbout = { pendingQuickDialog = ProfileQuickDialog.ABOUT },
            // ── 高级 / 危险操作 ──
            onOpenDeveloperLab = { openFromSettingsHub { showDeveloperLab = true } },
            onClearData = { pendingQuickDialog = ProfileQuickDialog.CLEAR_DATA },
            onDeleteAllData = { pendingQuickDialog = ProfileQuickDialog.RESET_ALL_DATA },
        )
    }

    // 「提前提醒时间」与「当前校区」是本页唯一还自己画的两个框 —— 它们的入口都在设置页里，
    // 而设置页画在上面。必须排在设置页**之后**，否则会被设置页盖住。
    if (showReminderTimeDialog) {
        OptionsDialog(
            title = "提前提醒时间",
            options = listOf("5分钟" to 5, "10分钟" to 10, "15分钟" to 15, "30分钟" to 30),
            selected = reminderMinutes,
            onSelect = { container.setReminderMinutes(it) },
            onDismiss = { showReminderTimeDialog = false }
        )
    }

    if (showCampusDialog) {
        CampusSelectDialog(
            campuses = CampusConfig.campuses,
            selectedId = selectedCampus.id,
            onSelect = { campus ->
                container.setSelectedCampus(campus.id)
                campus.schoolName?.let { container.setSchool(it) }
                showCampusDialog = false
                Toast.makeText(context, "已切换到${campus.name}，教务导入将使用该校区", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showCampusDialog = false }
        )
    }
}

// ═══════════════════════════════════════════
//  Import helpers
private fun exportToCalendar(
    app: PocketXiaoliApplication,
    container: com.pocket.xiaoli.di.AppContainer,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context
) {
    scope.launch {
        val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val courses = app.repository.getCoursesByScheme(container.activeSchemeId.value).firstOrNull() ?: emptyList()
                if (courses.isEmpty()) {
                    "暂无课程可导入"
                } else {
                    val startStr = container.semesterStart.value
                    if (startStr.isBlank() || !startStr.contains("-")) {
                        "请先在设置中配置学期开始日期"
                    } else {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val startDate = sdf.parse(startStr) ?: return@withContext "日期解析失败"
                        val weeks = container.semesterWeeks.value
                        val cal = java.util.Calendar.getInstance().apply {
                            time = startDate
                            add(java.util.Calendar.DAY_OF_MONTH, weeks * 7)
                        }
                        val endDate = cal.timeInMillis
                        val count = CalendarExportUtil.exportToCalendar(
                            context, courses, startDate.time, endDate
                        )
                        "已导入 $count 门课程到系统日历"
                    }
                }
            } catch (e: Exception) {
                "导入失败: ${e.message}"
            }
        }
        withContext(kotlinx.coroutines.Dispatchers.Main) {
            android.widget.Toast.makeText(context, result, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
