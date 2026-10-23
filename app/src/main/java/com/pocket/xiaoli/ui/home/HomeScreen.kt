package com.pocket.xiaoli.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.theme.PocketTypography
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.currentIconPresentation
// MIUIX：首页卡片保留**超椭圆圆角**（squircleSurface，Apache-2.0）。
// ⚠️ 2026-10：原先还引入了 MIUIX 的 Card / CardDefaults / PressFeedbackType，
// 但 SoftCard 已改为基于 AppCard（皮肤数据驱动），这三个符号不再有使用点，
// 已删除 —— 保留会让人误以为首页卡片仍走 MIUIX Card。
import com.pocket.xiaoli.ui.components.AppCard
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.data.entity.ExamEntity
import com.pocket.xiaoli.data.entity.HomeworkEntity
import com.pocket.xiaoli.data.entity.TaskEntity
import com.pocket.xiaoli.data.weather.WeatherSnapshot
import com.pocket.xiaoli.network.Announcement
import com.pocket.xiaoli.network.AnnouncementLevel
import com.pocket.xiaoli.util.AppVersion
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.onAccentColor
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 首页内容底部要额外让出的高度：右下角 FAB（56dp）加上它与列表的间距。
 *
 * ⚠️ 不加这一段的话，列表**最后一行的右端删不到** —— 那正是 FAB 停的位置
 * （删除按钮就在行的右端）。代价是滚到底时最后一行下方多出 72dp 空白，
 * 底栏玻璃在那一段少一点采样源；"最后一行永远可点"优先。
 */
private val HomeFabClearance = 72.dp

@Composable
fun HomeScreen(
    onOpenTimetable: () -> Unit = {},
    onOpenCampusMap: () -> Unit = {},
    onNavigateToRoom: (String) -> Unit = {},
    onOpenServices: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    /** 首页右上角铃铛：跳到「消息」页（版本更新 + 校园公告）。 */
    onOpenNotifications: () -> Unit = {},
    /** 时钟旁的天气胶囊：打开天气页（命名路由 `weather`，见 AppNavigation）。 */
    onOpenWeather: () -> Unit = {},
    /**
     * 从某条自定义待办起一个番茄钟（`taskId` 是 `tasks.id`）。
     *
     * 默认空实现 —— 与首页其它出口一致：调用方不接，按钮点了就是没反应，
     * 而不是抛异常把首页一起带崩。
     */
    onStartFocus: (taskId: Long, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val app = context.applicationContext as PocketXiaoliApplication
    val container = app.appContainer
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(app.repository, container)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 更新 + 公告：这是首页通知入口背后的**唯一数据源**（`announce/feed.json`）。
    // 红点、未读、有没有新版都在 `feedState` 里，UI 不再自己判断。
    val feedState by container.appFeedRepository.state.collectAsStateWithLifecycle()

    // 时钟旁的天气胶囊、鲤鱼的工具、主动提醒**共用这一份快照**（仓储里的 StateFlow）。
    // 首页是默认 tab，在这里触发一次加载最稳：用户从没点进过天气页时，
    // 鲤鱼的 `get_weather` 与主动提醒也能拿到数据；仓储的 30 分钟 TTL 保证
    // 来回切 tab 不会反复联网。
    val weather by container.weatherRepository.snapshot.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.weatherRepository.load() }

    // ⚠️ 首页**只有一个新增入口**：右下角那枚 FAB（2026-10 用户要求
    // "创建任务和作业改成一个，按钮改到右下角"）。此前是两处 ——
    // 标题右侧的「新增 / 管理」文字入口（走作业对话框）与列表里的行内输入框。
    // 两处都删了，录入统一走 [AddTodoDialog]。
    var showAddTodo by remember { mutableStateOf(false) }
    val homeModules by container.homeModules.collectAsStateWithLifecycle()
    val hiddenHomeModules by container.hiddenHomeModules.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        hasUnread = feedState.showRedDot,
        weather = weather,
        modules = homeModules,
        hiddenModules = hiddenHomeModules,
        onOpenTimetable = onOpenTimetable,
        onNavigateToRoom = onNavigateToRoom,
        onOpenGlobalSearch = onOpenGlobalSearch,
        onOpenNotifications = onOpenNotifications,
        onOpenWeather = onOpenWeather,
        onAddTodo = { showAddTodo = true },
        onToggleExam = viewModel::toggleExam,
        onDeleteExam = viewModel::deleteExam,
        onToggleHomework = viewModel::toggleHomework,
        onDeleteHomework = viewModel::deleteHomework,
        onToggleTask = viewModel::toggleTask,
        onDeleteTask = viewModel::deleteTask,
        onStartFocus = onStartFocus,
    )

    // 必须确认的公告：抽屉是「可以晚点看」，这个是「现在就要处理」。
    // 更新提示**不在这里**了 —— 它已经收归导航层的全局抽屉（见 `AppUpdatePromptController`），
    // 强更和普通更新共用同一个界面，也不再只有停在首页才看得见。
    feedState.blocking?.let { blocker ->
        AnnouncementAlert(
            announcement = blocker,
            onDismiss = { container.appFeedRepository.markSeen(blocker.id) },
        )
    }

    if (showAddTodo) {
        AddTodoDialog(
            onDismiss = { showAddTodo = false },
            onSave = { content, dueDate, courseName ->
                viewModel.addTodo(content, dueDate, courseName)
                showAddTodo = false
            },
            // 已有课程名作为候选项：让用户少打字，也避免"课程名写得不一样"
            // 导致记录挂不到同一门课上。来源是当前课表的课程名。
            existingCourses = state.courseNames,
        )
    }
}

/**
 * 首页**整页内容**（不含弹窗）：顶部彩晕 + 时钟 / 搜索 / 下一节课 / 待办 + 右下角 FAB。
 *
 * ## 为什么从 [HomeScreen] 抽出来
 *
 * 用户说"这页不好看"时，仓库纪律是**补整页基线**，而不是只补某个组件的基线
 *（`memory/04-ui-verification.md` §五）。而整页 `HomeScreen` 要 `AppContainer` + ViewModel，
 * JVM 快照渲不出来。所以把纯展示部分收在这里：所有数据都走参数，
 * 测试用固定时钟 + 固定实体直接渲整页。
 *
 * ⚠️ 抽出来之后**版面关系只存在这一处** —— 不要在测试里另写一份布局，
 * 否则"基线」验的是测试里的复制品，不是线上页面。
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    hasUnread: Boolean,
    modules: List<HomeModule>,
    hiddenModules: Set<String>,
    /** 时钟旁的天气。null = 还没拉到 / 拉取失败且无缓存，此时不显示胶囊。 */
    weather: WeatherSnapshot? = null,
    onOpenTimetable: () -> Unit = {},
    onNavigateToRoom: (String) -> Unit = {},
    onOpenGlobalSearch: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenWeather: () -> Unit = {},
    onAddTodo: () -> Unit = {},
    onToggleExam: (ExamEntity) -> Unit = {},
    onDeleteExam: (ExamEntity) -> Unit = {},
    onToggleHomework: (HomeworkEntity) -> Unit = {},
    onDeleteHomework: (HomeworkEntity) -> Unit = {},
    onToggleTask: (TaskEntity) -> Unit = {},
    onDeleteTask: (TaskEntity) -> Unit = {},
    onStartFocus: (Long, String) -> Unit = { _, _ -> },
    fixedNow: LocalDateTime? = null,
) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
    val colorful = currentIconPresentation().colorful
    val isDark = LocalDarkMode.current

    // 内容与 FAB 必须同处一个 Box：FAB 是浮在列表**之上**的，不是列表的一部分
    //（放进 LazyColumn 会跟着滚走）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceBase)
            // 顶部彩晕画在页底**自己身上**，淡出终点取视口高度的 26%。
            //
            // 以前这里是一张 `fillMaxWidth().height(220.dp)` 的 Box 浮在列表下面：
            // 220dp 从屏幕物理顶端往下量，而它要衬的头部内容是从状态栏**下方**开始的，
            // 两者没有任何派生关系 —— 状态栏更高（挖孔/折叠屏）或系统字号放大把头部撑高后，
            // 晕会在标题中间断掉。0.26 这个分数在 Pixel 5（851dp 视口）上正好是原来的 220dp，
            // 观感不变，关系改成随屏幕走。
            .then(
                if (colorful) Modifier.drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.18f),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = size.height * 0.26f,
                        ),
                    )
                } else {
                    Modifier
                }
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = skin.screenH,
                end = skin.screenH,
                top = skin.spaceLg,
                // 底部让出底栏（非 spacing 语义）。
                //
                // ⚠️ 必须跟着底栏的**实际高度**走，不能写死：这里曾经是 112.dp，而底栏现在只有 88dp，
                // 多出来的 24dp 就是"内容滚到底时最后一张卡停得离栏很远"的原因。
                // 而且底栏高度是量出来的（见 LocalBottomBarInset），写死的值迟早会再次漂移。
                //
                // 12dp 是给玻璃留的呼吸：首页内容从栏背后滚过去，正是这块玻璃唯一的采样源。
                // 之后再加 [HomeFabClearance] 让开右下角的 FAB —— 那一段是"最后一行可点"的代价。
                bottom = LocalBottomBarInset.current + 12.dp + HomeFabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(skin.sectionGap)
        ) {
            // 交错进入的序号：随每个模块递增，让内容"逐条组织出来"而不是一次性铺满
            var staggerIndex = 0

            item {
                StaggeredEnter(index = staggerIndex) {
                    HomeClockHeader(
                        onOpenNotifications = onOpenNotifications,
                        hasUnread = hasUnread,
                        // 未校准学期时 week 是"猜的 1"，宁可不显示也不放假信息。
                        week = state.week.takeIf { state.semesterCalibrated },
                        weather = weather,
                        onOpenWeather = onOpenWeather,
                        fixedNow = fixedNow,
                    )
                }
            }
            staggerIndex++

            item {
                StaggeredEnter(index = staggerIndex) {
                    HomeSearchEntry(onClick = onOpenGlobalSearch)
                }
            }
            staggerIndex++

            state.mockBanner?.let { banner ->
                item { MockBanner(banner) }
            }

            modules
                .filterNot { it.key in hiddenModules }
                .forEach { module ->
                    when (module) {
                        HomeModule.COURSE_CARD -> item {
                            StaggeredEnter(index = staggerIndex) {
                                NextCourseCard(state.nextCourse, colors, onOpenTimetable, onNavigateToRoom, hasTimetable = state.hasTimetable)
                            }
                        }

                        HomeModule.TODO -> {
                            // ⚠️ 标题右侧**没有**入口了（2026-10 用户口径）：
                            // 这里曾经是「新增」/「N 项 · 管理」的文字按钮，走作业对话框。
                            // 现在唯一的新增入口是右下角的 FAB，"管理"则由每一行自己的
                            // 勾选与删除承担 —— 一个列表能自己管好自己，不需要第二层面板。
                            item {
                                StaggeredEnter(index = staggerIndex) {
                                    SectionHeader(
                                        title = "待办",
                                        count = state.pendingExams.size +
                                            state.pendingHomework.size +
                                            state.pendingTasks.size,
                                    )
                                }
                            }
                            if (state.pendingTasks.isEmpty() &&
                                state.pendingHomework.isEmpty() &&
                                state.pendingExams.isEmpty()
                            ) {
                                item { EmptyTodoCard() }
                            } else {
                                // 考试排最前：它有硬性时间点，错过无法补，优先级高于作业与待办。
                                // 之后是带截止日期的待办（按截止日期升序），最后是没有期限的自定义待办。
                                //
                                // 三处都加 `animateItem()`：勾选完成或删除时条目会**滑出并让位**，
                                // 而不是"啪"地消失、下面的内容瞬间跳上来。
                                // 它依赖 `key` —— 上一轮补 key 正是为这里铺路。
                                items(state.pendingExams, key = { "exam_${it.id}" }) { exam ->
                                    ExamRow(
                                        exam = exam,
                                        onToggle = { onToggleExam(exam) },
                                        onDelete = { onDeleteExam(exam) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                // 带截止日期的待办（学习平台导入的作业也走这里）：
                                // 主标题取正文，课程名降级成副标题 —— 以前课程名占主标题、
                                // 正文在第二行，用户抱怨"看不出要交什么"。
                                //
                                // 末尾那个 `ifBlank { "待办" }` 是兜底：老数据里存在课程名与
                                // 正文**都为空**的条目（历史录入路径允许），不兜底就是列表里
                                // 一张空卡片 —— 看起来像渲染坏了。
                                items(state.pendingHomework, key = { "homework_${it.id}" }) { homework ->
                                    TodoRow(
                                        title = homework.content.ifBlank { homework.courseName }
                                            .ifBlank { "待办" },
                                        detail = if (homework.content.isBlank()) "" else homework.courseName,
                                        dueDate = homework.dueDate,
                                        completed = homework.completed,
                                        onToggle = { onToggleHomework(homework) },
                                        onDelete = { onDeleteHomework(homework) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                items(state.pendingTasks, key = { "task_${it.id}" }) { task ->
                                    TodoRow(
                                        title = task.name,
                                        detail = "",
                                        dueDate = "",
                                        completed = task.completed,
                                        onToggle = { onToggleTask(task) },
                                        onDelete = { onDeleteTask(task) },
                                        modifier = Modifier.animateItem(),
                                        // 只有自定义待办给专注入口：`homework.id` 与
                                        // `tasks.id` 是两套编号，串起来会关联错条目。
                                        onStartFocus = { onStartFocus(task.id, task.name) },
                                        // 作业行**不**传番茄数：那个数只按 `tasks.id` 归集，
                                        // 两边 id 不同源，串上去会显示别人的成绩。
                                        focusCount = state.focusCounts[task.id] ?: 0,
                                    )
                                }
                            }
                        }
                    }
                }

            // 首页「校园助手」入口已移除：鲤鱼已是一级 Tab，首页再放一个重复入口没必要。
        }

        // ── 唯一的新增入口 ──
        // 放在列表**外面**（Box 的第二个子节点）：它必须常驻右下角，不随内容滚走。
        HomeAddTodoFab(
            onClick = onAddTodo,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/**
 * 列表项进入动画：首次组合时淡入 + 自下方轻微上移，按 [index] 做交错延迟。
 *
 * ## 为什么需要它
 *
 * 改造前首页的模块是"啪"一下全部出现，没有任何过渡。交错进入（staggered enter）
 * 是"内容即 UI"类界面的常见手法 —— 它给用户一个"页面在组织内容"的感知，
 * 而不是"突然塞满"。
 *
 * ## 关键实现
 *
 * - **只在首次组合时动画**：用 [hasEntered] 记住状态，避免滚动回来重播
 *   （LazyColumn 会回收并重建 item，不记住会导致每次滚动都重播动画，非常烦人）。
 * - **交错延迟按 index 递增但设上限**：只对前几项明显交错，后面的迅速收敛，
 *   否则长列表末尾会等很久。
 */
@Composable
private fun StaggeredEnter(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var hasEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hasEntered = true }
// 每项 +45ms，最多累计 270ms（长列表末尾不能等太久）
    val delayMs = index.coerceAtMost(6) * 45
    AnimatedVisibility(
        visible = hasEntered,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(delayMillis = delayMs, durationMillis = 260)) +
            slideInVertically(animationSpec = tween(delayMillis = delayMs, durationMillis = 260)) { it / 6 },
    ) {
        content()
    }
}

@Composable
internal fun HomeClockHeader(
    onOpenNotifications: () -> Unit,
    hasUnread: Boolean = false,
    /** 当前教学周。null = 学期未校准，不显示（见调用点）。 */
    week: Int? = null,
    /** 时钟旁的天气胶囊。null 时不占位。 */
    weather: WeatherSnapshot? = null,
    onOpenWeather: () -> Unit = {},
    /**
     * 固定时刻，只给快照测试用。
     *
     * 时钟每秒变一次 —— 不注入的话整页基准图**永远对不上**（录制时 09:41:07、
     * 回放时 09:41:09 就红了）。传了值就停表，不传照常走秒。
     */
    fixedNow: LocalDateTime? = null,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    var now by remember(fixedNow) { mutableStateOf(fixedNow ?: LocalDateTime.now()) }
    LaunchedEffect(fixedNow) {
        if (fixedNow != null) return@LaunchedEffect
        while (true) {
            now = LocalDateTime.now()
            delay(1000L)
        }
    }
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    val dateText = now.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = skin.spaceXs, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 第一行：时间 + 天气。两者都是"此刻"的信息，挨着放最顺；
            // 周次是"这一学期"的信息，挪到日期行，给 1.3 倍字号留出宽度。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeText,
                    style = PocketTypography.numeric,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                if (weather != null) {
                    Spacer(Modifier.width(10.dp))
                    HomeWeatherChip(snapshot = weather, onClick = onOpenWeather)
                }
            }
            Spacer(Modifier.height(skin.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateText,
                    style = PocketTypography.caption,
                    fontWeight = FontWeight.Medium,
                    color = colors.textSecondary,
                )
                // 周次胶囊：首页原本只有日期，用户要看"这周第几周"得进课表。
                // 只在学期已校准时出现 —— 未校准时 week 是默认的 1，显示出来是假信息。
                if (week != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.accentMain.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "第 $week 周",
                            style = PocketTypography.caption,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentMain,
                        )
                    }
                }
            }
        }
        // 红点是**按钮的兄弟节点**，不是按钮的内容。原来它写在 `IconButton` 里面，
        // 而那个 modifier 链上有 `clip(CircleShape)` —— 7dp 的点又被 `offset(+5,-5)`
        // 推到圆外，整枚被裁掉，"有未读"在界面上根本看不见。
        // 挪到外层 `Box` 之后，圆形裁剪只管按钮自己，角标该出血就出血。
        // testTag：基准图只截这一小块。顶栏左边是每秒变一次的时钟，
        // 整行进基准图会让录制与回放永远对不上，所以给铃铛留一个裁剪锚点。
        Box(modifier = Modifier.testTag("home-notification-bell")) {
            IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier
                    .size(42.dp)
                    // 42dp 差 2dp 就到下限，但"差一点"正是最容易漏的那类：
                    // 热区按项目下限 44dp 抬高，可见圆仍是 42dp。
                    .invisibleTouchTarget()
                    .clip(CircleShape)
                    .background(colors.surfaceCard),
            ) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    contentDescription = if (hasUnread) "通知（有新消息）" else "通知",
                    tint = colors.accentMain,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (hasUnread) {
                // 红点只表示「有没看过的东西」，不显示数字：
                // 「3 条公告」不会让人去点，一枚点会。
                // 外圈 2dp 用按钮同色，把点从铃铛的描边线上摘出来。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceCard),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.danger)
                    )
                }
            }
        }
    }
}



@Composable
// `private` → `internal` 同样是为了快照测得到（整页要 AppContainer + ViewModel，
// 渲不出来；见 NextCourseCard 的同类说明）。
internal fun HomeSearchEntry(onClick: () -> Unit) {
    val colors = LocalPocketXiaoliColors.current
    val skin = LocalSkin.current
// 与鲤鱼页输入框、服务页搜索框统一成同一套"字段"外观：**白底 + 描边 + 满圆角**。
//
// 原来这里是一张纯白 SoftCard：在白页面上它没有边界，读起来像"一块空白"，
// 而不像"这里可以输入"。加一条 borderDivider 之后边界才立得住 ——
// 注意描边**不能太深**：搜索框是常驻元素，深色描边会让页面显得框框很多。
//
// 2026-10：外壳换成 [AppCard]，阴影/底色由皮肤决定（极简无影、鲜活弥散），
// 并把放大镜收进 32dp 的强调色淡底圆：与「下一节课」空态图标、待办勾选同一套语言。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, colors.borderDivider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = skin.spaceLg, vertical = skin.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.accentMain.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.accentMain,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "搜索服务 / 课程 / 帮助",
                style = PocketTypography.body,
// 这是"提示语"而不是内容，所以用更弱的 textTertiary ——
// 与真输入框里的 placeholder 保持同一层级
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
@Composable
private fun MockBanner(text: String) {
    val colors = LocalPocketXiaoliColors.current
    SoftCard(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceHighlight,
    ) {
        Text(
            text = text,
            style = PocketTypography.caption,
            color = colors.accentMain,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 首页右下角那枚 FAB —— **唯一**的新增待办入口。
 *
 * ⚠️ `internal` 而不是 private：`HomeScreen` 整页要 `AppContainer` + ViewModel 才渲得出来，
 * 快照测试渲不了整页（同 [NextCourseCard] 的理由）。抽出来给它录基准图 ——
 * 见 `HomeTodoScreenshotTest`。
 *
 * ## 偏移量为什么写在组件内部
 *
 * 底栏是**叠在内容之上的浮层**，所以 FAB 必须按 [LocalBottomBarInset] 抬到栏的上方 ——
 * 写死数值会在"底栏隐藏时"（子页面转场）留下一枚悬空的按钮。把偏移放在组件里，
 * 调用点只剩 `.align(BottomEnd)`，测试里也能得到与线上一致的间距。
 */
@Composable
internal fun HomeAddTodoFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(
            end = skin.screenH,
            bottom = LocalBottomBarInset.current + 16.dp,
        ),
        containerColor = colors.accentMain,
        // ⚠️ 不要写 Color.White：强调色是**用户可选的**，浅色档（#FFB74D/#F48FB1）
        // 配白字只有约 1.9:1。`onAccentColor()` 是与 `Theme.kt` 同一套判据的亮度感知入口。
        contentColor = onAccentColor(),
    ) {
        Icon(Icons.Filled.Add, contentDescription = "添加待办")
    }
}

/**
 * 首页的分区标题。
 *
 * @param count 非 null 且大于 0 时，在标题右侧挂一枚条数胶囊。
 *   首页待办区用它回答"还剩几件"，而不是让用户自己数。
 */
@Composable
internal fun SectionHeader(
    title: String,
    count: Int? = null,
) {
    // ⚠️ 这里**曾经**有一个可选的 `tip` 参数（在标题右侧挂一枚 BubbleTip「?」）。
    // 2026-10 用户的要求是"首页那些问号的都去掉" —— 首页**一律不放**说明气泡，
    // 所以参数与调用点一并删掉，不是"默认不传"。要再加回来先问：
    // 首页是"一眼看完就出门"的屏，多一枚「?」就是多一个要判读的符号。
    // 守门人：`HomeNoBubbleTipTest`（源码扫描，首页相关文件里不许出现 BubbleTip）。
    //
    // ⚠️ 2026-10 又删掉了 `action` / `onActionClick`：那是标题右侧的「新增 / N 项 · 管理」，
    // 与列表里的行内输入框并列为**第二个**新增入口。用户要求"改成一个、按钮放右下角"，
    // 于是两个入口都归并到右下角的 FAB。这里的参数没有留下"以后可能要用"的空位 ——
    // 真需要第二个入口时，重新想清楚它该放哪，比留一个没人传的参数强。
    //
    // ⚠️ `private` → `internal` 同样是为了让快照测得到（整页渲不出来，见 [HomeAddTodoFab]）。
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 与「我的菜单」等服务页分区同一套写法：一条短竖条先立住分区，
        // 标题与数量再往后排。首页原本是纯文字标题，整页只有下一节课一块颜色；
        // 竖条是给"待办"这个分区补的最小色块，不增加内容。
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accentMain)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = PocketTypography.sectionTitle,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    // 条数用强调色淡底：它回答"还剩几件"，值得被看见；
                    // 原来的中性灰胶囊扫过去几乎和背景同色。
                    .background(colors.accentMain.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = PocketTypography.label,
                    fontWeight = FontWeight.Medium,
                    color = colors.accentMain,
                )
            }
        }
    }
}


    /**
 * 首页卡片外壳 —— **改为基于 [AppCard]**，由皮肤数据驱动形状 / 阴影。
 *
 * ## 2026-10 改造：从 MIUIX Card 换成 AppCard
 *
 * 之前这里是直接调用 MIUIX 的 `Card`，圆角显式传本项目原值。
 * 问题在于：**它把"卡片长什么样"硬编码在了组件里**（`skin.radiusLarge` +
 * MIUIX 的 `defaultColors`），于是"极简 / 鲜活"两套风格切换时，
 * 卡片既不会变阴影、也不会变底色 —— 只能变圆角。
 *
 * 换成 [AppCard] 后，圆角 / 阴影 / 底色 / 边框**全部由 `LocalSkin` 决定**：
 * - 极简：12dp 圆角 + 无阴影（靠灰底白卡分层）
 * - 鲜活：20dp 圆角 + 弥散阴影（靠投影分层）
 *
 * ## 保留做的事
 *
 * - **超椭圆圆角**（squircle）：MIUI/iOS 那种平滑拐角。⚠️ 依赖 RuntimeShader
 *   （API 33+），Android 10~12 上自动降级为普通圆角。
 * - **按压反馈**：仍用 `Sink`（按下轻微下沉），比普通 ripple 更贴 MIUI 手感 ——
 *   `AppCard` 的 `onClick` 刻意不带 indication，正是为了不覆盖这层手感的实现空间。
 * - **参数签名不变**：8 处调用点（HomeScreen ×4 / TodoRows ×4）无需改动。
 *
 * ⚠️ 这是 [AppCard] 的**首个接入点**。先在这里验证观感，确认后再铺开到其余 76 处
 * 直接调用 `Card(` 的位置。
 */
@Composable
internal fun SoftCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
// null 表示"用当前皮肤的默认卡片内边距"。
// 不能直接把 skin 写进默认值 —— 参数默认值在 Composable 作用域外求值，读不到 CompositionLocal。
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit
 ) {
    val skin = LocalSkin.current
    val resolvedPadding = contentPadding ?: PaddingValues(skin.cardPaddingLarge)

    // 形状 / 阴影 / 底色全部交给 AppCard 按皮肤数据决定：
    //   极简 → radiusLarge 16dp + 无阴影（灰底白卡分层）
    //   鲜活 → radiusLarge 28dp + 弥散阴影
    //
    // ⚠️ 不再像原先那样显式传圆角值。之前旧实现把 skin.radiusLarge 直接塞给
    // MIUIX Card 的 cornerRadius，导致"换皮肤只变圆角、不变阴影与底色"。
    AppCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = color,
    ) {
        Column(modifier = Modifier.padding(resolvedPadding), content = content)
    }
 }
