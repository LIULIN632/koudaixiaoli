package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.data.learning.ChaoxingClient
import com.pocket.xiaoli.data.learning.LearningCourse
import com.pocket.xiaoli.data.learning.LearningHomework
import com.pocket.xiaoli.data.learning.LearningPlatform
import com.pocket.xiaoli.di.AppContainer
import com.pocket.xiaoli.ui.components.AppCard
import com.pocket.xiaoli.ui.components.ServiceEmptyCard
import com.pocket.xiaoli.ui.components.ServiceHeroBadge
import com.pocket.xiaoli.ui.components.ServiceHeroCard
import com.pocket.xiaoli.ui.components.ServiceHeroChip
import com.pocket.xiaoli.ui.components.ServicePageTopBar
import com.pocket.xiaoli.ui.components.ServiceSectionLabel
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.ServiceAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 学习通账号页（原生）。
 *
 * ## 为什么要有这一页
 *
 * 拉数据的能力早就有了 —— [ChaoxingClient] 用 WebView 登录后留下的 cookie 就能读课程与作业，
 * 但它只挂在「我的 → 绑定信息 → 同步作业」那个对话框里。服务页那一格反而直接把人丢进
 * 登录网页：想看"我这学期选了哪几门课、有几次作业没交"得自己在网页里翻。
 *
 * ## 这一页刻意**不写库**
 *
 * `homework` 表没有平台字段（[com.pocket.xiaoli.data.entity.HomeworkEntity]），
 * 学习通拉来的条目和手动导入的、教务来的混在一张表里分不开。所以这里只**展示本次拉到的**，
 * 想进首页待办仍走那条已有的「同步作业」入口 —— 去重逻辑在那边，不复制第二份。
 */
data class ChaoxingPageState(
    val bound: Boolean,
    val loading: Boolean = false,
    val courses: List<LearningCourse> = emptyList(),
    val homework: List<LearningHomework> = emptyList(),
    val error: String? = null,
    /** 最近一次成功拉取的时刻（毫秒）；0 = 还没有过。 */
    val syncedAt: Long = 0L,
)

/** 待办优先：没交的排前面，其次按截止时间（空串排最后）。 */
internal fun pendingFirst(items: List<LearningHomework>): List<LearningHomework> =
    items.sortedWith(
        compareBy<LearningHomework> { it.completed }
            .thenBy { it.dueDate.isBlank() }
            .thenBy { it.dueDate },
    )

@Composable
fun ChaoxingAccountPage(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(
            ChaoxingPageState(
                bound = container.hasLearningCookie(LearningPlatform.CHAOXING),
                syncedAt = container.loadLearningSyncAt(LearningPlatform.CHAOXING),
            ),
        )
    }

    fun pull() {
        val cookie = container.loadLearningCookie(LearningPlatform.CHAOXING)
        if (cookie.isNullOrBlank()) {
            state = ChaoxingPageState(bound = false)
            return
        }
        state = state.copy(loading = true, error = null)
        scope.launch {
            val client = ChaoxingClient()
            val courses = client.fetchCourses(cookie)
            val homework = client.fetchHomework(cookie)
            val ok = courses.isSuccess || homework.isSuccess
            val stamp = if (ok && state.syncedAt != 0L) state.syncedAt else System.currentTimeMillis()
            state = ChaoxingPageState(
                bound = true,
                loading = false,
                courses = courses.getOrNull().orEmpty(),
                homework = pendingFirst(homework.getOrNull().orEmpty()),
                error = when {
                    ok -> null
                    else -> courses.exceptionOrNull()?.message
                        ?: homework.exceptionOrNull()?.message
                        ?: "学习通没响应，可能是登录过期了"
                },
                syncedAt = if (ok) stamp else state.syncedAt,
            )
            if (ok) container.saveLearningSyncAt(LearningPlatform.CHAOXING, stamp)
        }
    }

    // 进来就拉一次：这一页存在的意义就是"不用进网页也能看到"
    LaunchedEffect(state.bound) { if (state.bound) pull() }

    Scaffold(
        topBar = {
            ServicePageTopBar(title = "学习通", onBack = onBack) {
                IconButton(onClick = { pull() }, enabled = state.bound && !state.loading) {
                    Icon(Icons.Outlined.Refresh, "重新拉一次")
                }
            }
        },
    ) { padding ->
        ChaoxingAccountContent(
            state = state,
            onRetry = { pull() },
            onOpenWeb = { onOpenWeb(LearningPlatform.CHAOXING.loginUrl, "学习通") },
            onLogout = {
                container.deleteLearningCookie(LearningPlatform.CHAOXING)
                onLogout()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalSkin.current.spaceLg, vertical = LocalSkin.current.spaceMd),
        )
    }
}

/**
 * 纯内容层：不碰容器、不发请求，快照测试直接渲它。
 * 未登录 / 拉取失败 / 有数据三态共用这一份代码，避免"三态各写一套 UI"的漂移。
 */
@Composable
fun ChaoxingAccountContent(
    state: ChaoxingPageState,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    if (!state.bound) {
        ServiceEmptyCard(
            icon = Icons.Outlined.MenuBook,
            title = "还没登录学习通",
            message = "学习通没有可用的账号密码接口，登录要在网页里完成；" +
                "登录成功后本机记住会话，回到这一页就能直接看课程和作业。",
            accent = ServiceAccent.STUDY,
            actionLabel = "去登录学习通",
            onAction = onOpenWeb,
            modifier = modifier,
        )
        return
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(skin.spaceMd)) {
        ServiceHeroCard(accent = ServiceAccent.STUDY) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // hero 是分类色深底，文字只能白：这里用 textPrimary 会得到"深字压深底"，
                    // 快照里几乎读不出来（347 第一版就是这么返工的）
                    Text("我的网课", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(skin.spaceXs))
                    Text(
                        if (state.loading) "正在读课程与作业…" else "课程与作业都来自学习通，本机只存会话",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ServiceHeroBadge(Icons.Outlined.MenuBook)
            }
            Spacer(Modifier.height(skin.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
                ServiceHeroChip("${state.courses.size} 门课程")
                ServiceHeroChip("${state.homework.count { !it.completed }} 项待办")
                ServiceHeroChip(syncedLabel(state.syncedAt))
            }
        }

        state.error?.let { message ->
            AppCard {
                Text("这次没拉到", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.danger)
                Spacer(Modifier.height(skin.spaceXs))
                Text(message, fontSize = 12.sp, lineHeight = 17.sp, color = colors.textSecondary)
                Spacer(Modifier.height(skin.spaceXs))
                Text(
                    if (state.courses.isNotEmpty()) "下面显示的是上一次成功拉到的内容。" else "点右上角刷新，或重新登录一次。",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }

        ServiceSectionLabel(title = "待办作业", accent = ServiceAccent.STUDY)
        when {
            state.loading && state.homework.isEmpty() -> LoadingRow()
            state.homework.isEmpty() -> AppCard {
                Text("没有发现待办作业", fontSize = 13.5.sp, color = colors.textSecondary)
                Text(
                    "扫描范围是这门课列表里的作业入口；刚发布的作业可能要等学习通那边同步。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textTertiary,
                )
            }
            else -> AppCard {
                state.homework.take(12).forEachIndexed { index, item ->
                    HomeworkRow(item, last = index == minOf(state.homework.size, 12) - 1)
                }
            }
        }

        ServiceSectionLabel(title = "课程", accent = ServiceAccent.STUDY)
        when {
            state.loading && state.courses.isEmpty() -> LoadingRow()
            state.courses.isEmpty() -> AppCard {
                Text("没读到课程", fontSize = 13.5.sp, color = colors.textSecondary)
            }
            else -> AppCard {
                state.courses.forEachIndexed { index, course ->
                    CourseRow(course, last = index == state.courses.size - 1)
                }
            }
        }

        Text(
            "要把作业搬进首页待办，去「我的 → 绑定信息 → 学习通 → 同步作业」。" +
                "这一页只负责看一眼，不改你的待办列表。",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.textTertiary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(skin.radiusChip),
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.size(skin.spaceXs))
                Text("打开网页", fontSize = 12.5.sp)
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(skin.radiusChip),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(16.dp))
                Spacer(Modifier.size(skin.spaceXs))
                Text("退出登录", fontSize = 12.5.sp, color = colors.danger)
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("拉取中…", fontSize = 13.sp, color = colors.textTertiary)
        }
    }
}

@Composable
private fun HomeworkRow(item: LearningHomework, last: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceMd),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            // 未完成的作业不能画"打勾"图标 —— 那是"已交"的意思，与列表要表达的正好相反
            Icon(
                if (item.completed) Icons.Outlined.TaskAlt else Icons.Outlined.Circle,
                contentDescription = if (item.completed) "已完成" else null,
                tint = if (item.completed) colors.success else colors.accentMain,
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { "未命名作业" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        item.courseName.takeIf { it.isNotBlank() },
                        item.dueDate.takeIf { it.isNotBlank() }?.let { "截止 $it" },
                    ).joinToString(" · "),
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
        if (!last) HorizontalDivider(color = colors.borderDivider)
    }
}

@Composable
private fun CourseRow(course: LearningCourse, last: Boolean) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = skin.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    course.name.ifBlank { "未命名课程" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(course.teacher?.takeIf { it.isNotBlank() }, course.term?.takeIf { it.isNotBlank() })
                        .joinToString(" · ")
                        .ifBlank { "教师与学期以网页为准" },
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                )
            }
        }
        if (!last) HorizontalDivider(color = colors.borderDivider)
    }
}

private fun syncedLabel(at: Long): String = if (at <= 0L) {
    "还没拉过"
} else {
    "拉取于 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(at))
}
