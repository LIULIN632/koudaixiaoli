package com.pocket.xiaoli

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.wantsDarkStatusBarIcons
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import com.pocket.xiaoli.appwidget.ClassWidgetProvider
import com.pocket.xiaoli.data.announcement.AppFeedStore
import com.pocket.xiaoli.data.legal.AgreementStore
import com.pocket.xiaoli.data.onboarding.FirstRunStore
import com.pocket.xiaoli.ui.onboarding.FirstRunOnboardingScreen
import com.pocket.xiaoli.ui.legal.AgreementMode
import com.pocket.xiaoli.ui.legal.UserAgreementScreen
import com.pocket.xiaoli.focus.FocusController
import com.pocket.xiaoli.notification.AppFeedNotifier
import com.pocket.xiaoli.notification.AppFeedScheduler
import com.pocket.xiaoli.notification.ClassReminderScheduler
import com.pocket.xiaoli.ui.navigation.AppNavigation
import com.pocket.xiaoli.ui.theme.AppearanceMode
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.CustomAccent
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalCourseColorMode
import com.pocket.xiaoli.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.telemetry.TelemetryNoticeDialog
import com.pocket.xiaoli.ui.theme.SkinStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    /**
     * 最近一次要交给导航处理的深链。
     *
     * 为什么不是一个 `onCreate` 里的局部 `val`：应用**已经在前台**时点番茄钟的
     * 倒计时通知不会再走 `onCreate`，只会走 [onNewIntent]；那时导航层早就组合完了，
     * 不把一个会变的值喂给它，点击就"没反应"（停在原来的页面上）。
     */
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    override fun onStart() {
        super.onStart()
        ClassReminderScheduler.rescheduleAsync(this)
        ClassWidgetProvider.refresh(this)
        // 番茄钟幂等自愈：进程被系统回收之后再打开，快照还在但闹钟可能被清掉了，
        // 这里把闹钟补上、把前台服务与常驻通知摆回去。
        FocusController.of(this).ensureRunning()
    }

    /**
     * 已经在前台时收到新深链。
     *
     * 通知里的 PendingIntent 带了 `?n=<时间戳>`，所以**同一页连点两次**也会被算作
     * 两次不同的深链（`AppNavigation` 那个 `LaunchedEffect` 以整串 URL 为 key），
     * 不会因为"字符串没变"而被吞掉。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.data?.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边：状态栏 / 导航栏都做成透明，页面自己画到它们背后去。
        //
        // 为什么要开（2026-10-19，用户点名要「状态栏也跟顶栏一样是液态玻璃」）：
        // 没开边到边时系统会给内容让出两条栏的高度，**任何页面都画不到状态栏底下**，
        // 于是鲤鱼页那条玻璃顶栏只能停在状态栏下面、上面永远压着一条纯色带 ——
        // 参考的岱宗盒子就是开了这个，才做到「玻璃从状态栏一直铺到工具栏」。
        //
        // 开了以后谁负责让位：`AppNavigation` 外面那层 Scaffold 仍然把它算出来的
        // padding 交给 `NavHost`，并在同一处 `consumeWindowInsets` 掉，所以
        // **其余 40 多个子页面的排版一点没变**；只有顶栏是玻璃的三条路由
        // （assistant 系列）不吃顶部留给状态栏的那份，由它们自己的 TopAppBar 接管。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        val app = application as PocketXiaoliApplication
        pendingDeepLink.value = intent?.data?.toString()
        setContent {
            val initialDeepLink by pendingDeepLink.collectAsStateWithLifecycle()
            val darkMode by app.appContainer.darkMode.collectAsStateWithLifecycle()
            val isDark = when (darkMode) {
                "dark" -> true
                "system" -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> false
            }
            val appearanceModeKey by app.appContainer.appearanceMode.collectAsStateWithLifecycle()
            val campusLayoutModeKey by app.appContainer.campusLayoutMode.collectAsStateWithLifecycle()
            val courseColorMode by app.appContainer.courseColorMode.collectAsStateWithLifecycle()
            val customAccentColor by app.appContainer.customAccentColor.collectAsStateWithLifecycle()
            val customAccent = if (customAccentColor != CustomAccent.DEFAULT) Color(customAccentColor) else null
            val appearanceMode = AppearanceMode.fromKey(appearanceModeKey)
            val skinStyleKey by app.appContainer.skinStyle.collectAsStateWithLifecycle()
            val skinStyle = SkinStyle.fromKey(skinStyleKey)
            val campusLayoutMode = CampusLayoutMode.fromKey(campusLayoutModeKey)

            val view = LocalView.current
            // 系统栏（状态栏/导航栏）的底色与图标明暗，稍后在主题内部一起画 —— 见下方 SideEffect。

            val context = LocalContext.current
            // ── Dialog / Popup 窗口用透明底的 Context ──
            //
            // Activity 主题的 `windowBackground` 是 `@color/status_bar`（冷启动那一帧要用），
            // 而 `androidx.compose.ui.window.Dialog` 会继承当前主题的窗口底色。结果底部抽屉的
            // 半透明遮罩盖在一块不透明白板上 —— 看起来就是"整块实色灰、看不到原页面"。
            // 这里只把 **CompositionLocal 的 context** 换成透明窗口主题，Activity 自己的
            // 窗口不受影响；所有 Dialog/Sheet 拿到的都是透明窗口，遮罩下面就是真页面。
            val dialogContext = remember(context) {
                android.view.ContextThemeWrapper(context, R.style.Theme_PocketXiaoli_Dialog)
            }

            // 启动时静默刷新校园知识库（24 小时节流在 KnowledgeStore 里）。
            //
            // 2026-10-17：**学校索引的自动更新已经整个去掉** —— 产品只服务烟台理工，
            // 那份 301 条全国学校列表在产品入口下不可达，启动时去拉它是纯浪费
            // （实测一次 raw.githubusercontent 卡了 26 秒，见 2026-10-17 的日志导出）。
            // 知识库则是鲤鱼真在用的，保留；它也不再挂在「自动更新校园数据」开关上
            // —— 那个开关随学校索引一起删了，用户没有第二个开关可关，就不该再藏。
            LaunchedEffect(Unit) {
                val container = app.appContainer
                container.knowledgeStore.refreshIfStale(container.knowledgeUpdater)
            }
            // 启动时拉一次「更新 + 公告」（L1：打开即知）。
            //
            // 与上面两条不同，这条**不看「自动检查更新」的开关** —— 那个开关管的是
            // 省流量的学校索引/知识库；而"有没有新版本""有没有停机公告"是安全信息，
            // 不该被一个省流量开关静默关掉。真要关，设置里有单独的「后台检查」开关
            // （AppFeedStore.backgroundEnabled），它只关后台那一层，不关这里。
            //
            // 30 分钟节流在 AppFeedRepository 里，所以每次冷启动调用是安全的。
            // 顺带把 WorkManager 的周期任务排上（幂等，重复排不会多一份）。
            LaunchedEffect(Unit) {
                val container = app.appContainer
                withContext(Dispatchers.IO) {
                    AppFeedScheduler.schedule(context)
                    val state = runCatching { container.appFeedRepository.refresh() }.getOrNull()
                    // 冷启动这一条路上如果已经能看到新版/新公告，**补一条本地通知**。
                    // 用户可能刚切到别的 App，通知栏才是"推"该出现的位置；去重在
                    // AppFeedNotifier 里（同一版本只敲一次门），重复调用无副作用。
                    if (state != null && state.loaded && state.error == null) {
                        runCatching { AppFeedNotifier.postIfNeeded(context, state, AppFeedStore(context)) }
                    }
                }
            }

            // ── 通知权限（Android 13+）──
            // 「有新版本」那条通知走的是 POST_NOTIFICATIONS；以前只有开启课程提醒时
            // 才会去申请，所以"从没开过提醒"的用户**永远收不到发版通知**（2026-10-17 实测）。
            // 这里在**首次打开**时问一次（本地记账），拒绝后不再骚扰 —— 应用内红点与
            // 底部气泡照常工作，只是不发系统通知。
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                val prefs = context.getSharedPreferences("pocket_notif_prompt", Context.MODE_PRIVATE)
                if (!granted && !prefs.getBoolean("asked", false)) {
                    prefs.edit().putBoolean("asked", true).apply()
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // MIUIX 的窗口级组件（WindowListPopup / WindowBottomSheet）在 Dialog 窗口里会读
            // LocalNavigationEventDispatcherOwner；没提供就直接抛 IllegalStateException
            // 「No NavigationEventDispatcher was provided via LocalNavigationEventDispatcherOwner」
            // —— 0.1.56 的真机闪退就是这个（见 2026-10-17 的崩溃日志）。
            // 注意 parent 必须**显式传 null**：不传的话默认值取
            // LocalNavigationEventDispatcherOwner.current，而它在我们这儿为空，
            // 库会直接抛「No NavigationEventDispatcherOwner provided …」——
            // 0.1.57 一启动就崩就是少写了这个 null。传 null 才会新建 root dispatcher。
            val navigationEventOwner = rememberNavigationEventDispatcherOwner(parent = null)
            PocketXiaoliTheme(appearanceMode = appearanceMode, skinStyle = skinStyle, darkTheme = isDark, customAccent = customAccent) {
            // 系统栏（状态栏 / 导航栏）现在**不再涂色**：两条栏都透明，由页面自己画到底下去
            //（见 onCreate 里的 enableEdgeToEdge）。这里只负责一件事 —— 图标的明暗。
            //
            // 2026-10-19 之前涂的是 `surfaceBase`：那时候没开边到边，页面画不到栏底下，
            // 涂成页面底色是「看起来像一整块」的唯一办法；开了边到边之后继续涂色，
            // 反而会盖住鲤鱼页那条玻璃顶栏。
            val pageSurface = LocalPocketXiaoliColors.current.surfaceBase
            val darkSystemBarIcons = wantsDarkStatusBarIcons(pageSurface)
            SideEffect {
                val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                // 透明栏要显式关掉「导航栏对比度保护」，否则部分 ROM 会给它加一层半透明灰罩，
                // 深色模式下底栏那圈玻璃就总是蒙着一层。
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = darkSystemBarIcons
                controller.isAppearanceLightNavigationBars = darkSystemBarIcons
            }
                CompositionLocalProvider(
                    LocalContext provides dialogContext,
                    LocalNavigationEventDispatcherOwner provides navigationEventOwner,
                    LocalCourseColorMode provides courseColorMode,
                    LocalCampusLayoutMode provides campusLayoutMode,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        // ── 用户协议闸门 ──
                        //
                        // 没同意过（或条款改版后还没重新同意）就**只显示协议页**：正文整页可读、
                        // 底部两个按钮，不给"点空白关掉"这条路。同意后本进程内直接进 App，
                        // 不用重启；记录写在 `AgreementStore`（SharedPreferences，清缓存也不会丢）。
                        //
                        // ⚠️ 顺序：协议在前、匿名统计告知在后。先让用户知道自己要同意什么，
                        // 再问"要不要参与统计"——反过来就成了"先上车、后补票"。
                        var agreementAccepted by remember { mutableStateOf(AgreementStore.isAccepted(context)) }
                        if (!agreementAccepted) {
                            UserAgreementScreen(
                                mode = AgreementMode.CONSENT,
                                onAccept = {
                                    AgreementStore.accept(context)
                                    agreementAccepted = true
                                },
                                onDecline = { (context as? android.app.Activity)?.finish() },
                            )
                        } else {
                            // ── 首次向导：选学校 → 用教务账号登录并导入课表 ──
                            //
                            // 只对"新装机 / 清过数据"出现。老用户升级上来没有 onboarding_done
                            // 这个键，用 hasExistingSetup() 兜底：绑过账号 / 导过课表 / 选过校区
                            // 都不该再被拦一次。
                            var onboardingDone by remember {
                                mutableStateOf(
                                    FirstRunStore.isDone(context) ||
                                        app.appContainer.hasExistingSetup()
                                )
                            }
                            if (!onboardingDone) {
                                FirstRunOnboardingScreen(
                                    container = app.appContainer,
                                    onDone = {
                                        FirstRunStore.markDone(context)
                                        onboardingDone = true
                                    },
                                )
                            } else {
                                AppNavigation(initialDeepLink = initialDeepLink)
                                // 匿名统计的首启告知：只在第一次启动出现一次，
                                // 「不要统计」当场就关（见 TelemetryNoticeDialog 的 KDoc）。
                                TelemetryNoticeDialog()
                            }
                        }
                    }
                }
            }
        }
    }
}
