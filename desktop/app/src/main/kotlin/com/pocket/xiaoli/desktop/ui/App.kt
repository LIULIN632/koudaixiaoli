package com.pocket.xiaoli.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pocket.xiaoli.desktop.data.DesktopUpdate
import com.pocket.xiaoli.desktop.data.HolidayStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.desktop.state.AppState
import com.pocket.xiaoli.desktop.ui.components.AccentDot
import com.pocket.xiaoli.desktop.ui.components.GhostIconButton
import com.pocket.xiaoli.desktop.ui.components.rememberPressState
import com.pocket.xiaoli.desktop.ui.components.PocketMascot
import com.pocket.xiaoli.desktop.ui.dialogs.AboutDialog
import com.pocket.xiaoli.desktop.ui.dialogs.ApiConfigDialog
import com.pocket.xiaoli.desktop.ui.dialogs.IcsExportDialog
import com.pocket.xiaoli.desktop.ui.dialogs.ImportConfirmDialog
import com.pocket.xiaoli.desktop.ui.dialogs.ImportDialog
import com.pocket.xiaoli.desktop.ui.dialogs.LoginDialog
import com.pocket.xiaoli.desktop.ui.dialogs.SlotDialog
import com.pocket.xiaoli.desktop.ui.dialogs.StudentDialog
import com.pocket.xiaoli.desktop.ui.dialogs.TermDialog
import com.pocket.xiaoli.desktop.ui.dialogs.TodoDetailDialog
import com.pocket.xiaoli.desktop.ui.dialogs.TodoEditorDialog
import com.pocket.xiaoli.desktop.ui.icons.PocketIcons
import com.pocket.xiaoli.desktop.ui.screens.AssistantScreen
import com.pocket.xiaoli.desktop.ui.screens.CampusScreen
import com.pocket.xiaoli.desktop.ui.screens.HomeScreen
import com.pocket.xiaoli.desktop.ui.screens.OnboardingScreen
import com.pocket.xiaoli.desktop.ui.screens.ProfileScreen
import com.pocket.xiaoli.desktop.ui.screens.TimetableScreen
import com.pocket.xiaoli.desktop.ui.theme.PocketTypography
import com.pocket.xiaoli.desktop.ui.theme.PocketXiaoliTheme
import com.pocket.xiaoli.desktop.ui.theme.Radii
import com.pocket.xiaoli.desktop.ui.theme.Spacing
import com.pocket.xiaoli.desktop.ui.theme.accentColor
import com.pocket.xiaoli.desktop.ui.theme.accentSoftColor
import com.pocket.xiaoli.desktop.ui.theme.pocketColors
import com.pocket.xiaoli.desktop.ui.util.Format
import com.pocket.xiaoli.desktop.ui.util.Platform
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 应用根：主题 → 首启引导 / 主界面。
 *
 * 键盘快捷键（桌面端专属，写在这里而不是散落各页）：
 * - Ctrl+K 全局搜索；Ctrl+1..5 切 tab；Ctrl+, 打开 AI 配置；Ctrl+Shift+I 导入导出。
 */
@Composable
fun DesktopApp(state: AppState) {
    val settings = state.data.settings
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val effectiveDark = if (settings.followSystemDark) systemDark else settings.darkMode
    val accent = remember(settings.accentHex) {
        val hex = settings.accentHex.removePrefix("#")
        if (hex.length == 6) runCatching { Color(("FF$hex").toLong(16)) }.getOrNull() else null
    }
    PocketXiaoliTheme(darkTheme = effectiveDark, customAccent = accent) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onPreviewKeyEvent false
                    val handled = when (event.key) {
                        Key.K -> { state.openSearch(); true }
                        Key.One -> { state.tab = 0; true }
                        Key.Two -> { state.tab = 1; true }
                        Key.Three -> { state.tab = 2; true }
                        Key.Four -> { state.tab = 3; true }
                        Key.Five -> { state.tab = 4; true }
                        Key.Comma -> { state.openApiDialog(); true }
                        else -> false
                    }
                    handled
                }
        ) {
            // 首帧一组合就记下 JVM→界面的耗时（配置体检卡的「启动」行用它）
            LaunchedEffect(Unit) {
                state.markFirstFrame()
                // 每天最多一次，失败静默；发现的版本由侧栏那条提示承接（见 Sidebar）
                state.checkForUpdate()
            }
            if (state.showOnboarding) {
                OnboardingScreen(state)
                // 引导里的「登录教务」「填 API Key」复用主界面的两个弹窗，不另写一份表单
                if (state.showLogin) LoginDialog(state)
                if (state.showApiDialog) ApiConfigDialog(state)
            } else {
                MainLayout(state)
                if (state.showSearch) SearchPalette(state)
            }
        }
    }
}

@Composable
private fun MainLayout(state: AppState) {
    Row(Modifier.fillMaxSize().background(pocketColors().surfaceBase)) {
        Sidebar(state)
        // 统一的页面内边距：各屏自己不再加外边距（只留卡片间距），避免「有的页贴边、有的页留白」
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 26.dp, vertical = 20.dp)
        ) {
            when (state.tab) {
                0 -> HomeScreen(state)
                1 -> TimetableScreen(state)
                2 -> AssistantScreen(state)
                3 -> CampusScreen(state)
                else -> ProfileScreen(state)
            }
        }
    }
    Dialogs(state)
}

private data class NavItem(val index: Int, val title: String, val hint: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem(0, "首页", "今天", PocketIcons.Home),
    NavItem(1, "课表", "周课表", PocketIcons.Timetable),
    NavItem(2, "鲤鱼", "AI 助手", PocketIcons.Chat),
    NavItem(3, "服务", "校历 / 工具", PocketIcons.Services),
    NavItem(4, "我的", "设置", PocketIcons.Profile),
)

@Composable
private fun Sidebar(state: AppState) {
    val colors = pocketColors()
    val notice = state.notice

    // 轻提示自己消失（原来由内容区的浮层负责，现在它是侧栏状态位的一部分）
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2600)
            state.notice = null
        }
    }

    // 节假日自动更新：每天最多一次；失败静默回退（离线/被墙都不该打扰用户）
    LaunchedEffect(Unit) {
        if (state.data.settings.holidayAutoUpdate) {
            val stale = System.currentTimeMillis() - HolidayStore.lastUpdated > 24L * 3600 * 1000
            if (stale) HolidayStore.updateFromNetwork()
        }
    }
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(colors.surfaceCard)
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        // 品牌
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PocketMascot(Modifier.size(36.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "口袋小鲤",
                    style = PocketTypography.cardTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text("桌面版 · 烟台理工", style = PocketTypography.label, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(20.dp))

        // 导航
        navItems.forEach { item ->
            val selected = state.tab == item.index
            val navPress = rememberPressState(!selected)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .scale(navPress.scale)
                    .clip(RoundedCornerShape(Radii.chip))
                    .background(
                        when {
                            selected -> accentSoftColor()
                            navPress.hovered -> colors.surfaceAlt
                            else -> Color.Transparent
                        },
                    )
                    .hoverable(navPress.source)
                    .clickable(interactionSource = navPress.source, indication = null) { state.tab = item.index }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = if (selected) accentColor() else colors.textSecondary,
                    modifier = Modifier.width(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.title,
                    style = PocketTypography.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Box(Modifier.width(4.dp).height(4.dp).clip(CircleShape).background(accentColor()))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 轻提示的位置：侧栏底部有一块固定高度的状态位。放内容区里总有一页会被压住
        // （底部压「清空已完成」和发送键、顶部压周切换器），侧栏是外壳，既不挡内容也不被挡。
        // 高度写死不随提示有无变化，否则下面的日期卡会跟着跳。
        Box(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = notice != null,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.chip))
                        .background(accentSoftColor())
                        .border(1.dp, accentColor().copy(alpha = 0.45f), RoundedCornerShape(Radii.chip))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AccentDot(size = 7.dp, color = accentColor())
                    Spacer(Modifier.width(8.dp))
                    Text(
                        notice.orEmpty(),
                        style = PocketTypography.label,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // 有新版本时这里多一条；点一下用系统浏览器打开下载页，不做自动安装。
        state.updateAvailable?.let { release ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.chip))
                    .background(colors.warning.copy(alpha = 0.16f))
                    .border(1.dp, colors.warning.copy(alpha = 0.5f), RoundedCornerShape(Radii.chip))
                    .clickable { openUpdatePage(state, release) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PocketIcons.Download, contentDescription = null, tint = colors.warning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "新版本 " + release.versionName + " · 去下载",
                    style = PocketTypography.label,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // 底部：今天 + 快捷入口
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.card))
                .background(colors.surfaceAlt)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(Format.weekday(LocalDate.now().dayOfWeek.value), style = PocketTypography.caption, color = colors.textSecondary)
            Spacer(Modifier.height(2.dp))
            Text(Format.monthDay(LocalDate.now()), style = PocketTypography.sectionTitle, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            val week = state.displayedWeek()
            Text(
                if (week > 0) "第 $week 周" else "未开学 / 未设学期",
                style = PocketTypography.label,
                color = if (week > 0) accentColor() else colors.textTertiary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GhostIconButton(PocketIcons.Search, contentDescription = "全局搜索 Ctrl+K", size = 30.dp) { state.openSearch() }
            GhostIconButton(PocketIcons.Sparkles, contentDescription = "AI 配置", size = 30.dp) { state.openApiDialog() }
            GhostIconButton(PocketIcons.Download, contentDescription = "导入导出", size = 30.dp) { state.showImportDialog = true }
            GhostIconButton(PocketIcons.Settings, contentDescription = "设置", size = 30.dp) { state.tab = 4 }
            GhostIconButton(PocketIcons.Info, contentDescription = "关于", size = 30.dp) { state.showAbout = true }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "数据仅存本机",
            style = PocketTypography.label,
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    // 右侧一条细分隔线
    Box(Modifier.fillMaxHeight().width(1.dp).background(pocketColors().surfaceHighlight))
}

/**
 * 打开新版本的下载页；浏览器打不开就把链接复制到剪贴板并说清楚。
 *
 * 不用 `Desktop.browse` 失败就静默：那样用户会以为「点了没反应」。
 */
private fun openUpdatePage(state: AppState, release: DesktopUpdate.Release) {
    if (Platform.openUrl(release.url)) return
    Platform.copyToClipboard(release.url)
    state.notice = "浏览器打不开，下载链接已复制"
}

/** 所有弹窗统一在这里按状态渲染，页面里只管把 state 开关打开。 */
@Composable
private fun Dialogs(state: AppState) {
    if (state.showTermDialog) TermDialog(state)
    if (state.showStudentDialog) StudentDialog(state)
    if (state.showApiDialog) ApiConfigDialog(state)
    if (state.showTodoEditor) TodoEditorDialog(state)
    if (state.selectedTodo != null) TodoDetailDialog(state)
    if (state.showSlotDialog) SlotDialog(state)
    if (state.showImportDialog) ImportDialog(state)
    if (state.pendingImport != null) ImportConfirmDialog(state)
    if (state.showLogin) LoginDialog(state)
    if (state.showIcsDialog) IcsExportDialog(state)
    if (state.showAbout) AboutDialog(state)
    if (state.showImeProbe) com.pocket.xiaoli.desktop.ui.dialogs.ImeProbeDialog(state)
}
