package com.pocket.xiaoli.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.components.campusui.rememberCampusLazyListState
import com.pocket.xiaoli.ui.glass.LocalBottomBarInset
import com.pocket.xiaoli.ui.theme.CampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalCampusLayoutMode
import com.pocket.xiaoli.ui.theme.LocalDarkMode
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.ServiceAccent
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.focus.FocusStatsView
import com.pocket.xiaoli.ui.focus.FocusStatsSection

/**
 * 「我的」页正文。
 *
 * ## 这一页只放两类东西（2026-09 收敛）
 *
 * 1. **身份**：我是谁、绑了什么（身份卡 + 常用服务格）
 * 2. **课表与学习**：这个 App 每天真正要用的那件事
 *
 * 其余全部收进 [ProfileSettingsScreen]（一个「设置」入口进去）。
 * 改造前这一页从身份卡往下挂了 5 组 17 项，深色模式、更新日志、清空数据全挤在里面 ——
 * 用户每次进来都要在十几行里找自己要的那一条。
 *
 * ⚠️ 判断一个条目该放哪，问的是"用户**多久改一次**"，不是"它属于哪个模块"：
 * 「导入课表」是日常动作（留在本页），「检查更新」一年点两次（进设置）。
 *
 * ## 2026-10 加的「专注统计」属于上面第 2 类，不是第三类
 *
 * 四格里的「校园余额」换成了「专注计时」（每天都会点的动作），紧跟着一整块专注统计
 * （连续打卡 + 12 周热力图 + 最近 7 天柱状图 + 今天怎么攒的）—— 它服务的还是
 * "这个 App 每天真正要用的那件事"，所以不违背上面那条两类约定。
 * 代价写在 `ProfileHomeScreenshotTest` 里：它把「课表与学习」「设置」往下推了，一屏装不下了。
 *
 * ## ⚠️ 这个正文**只收一个计数**，不收任何身份字段（2026-10）
 *
 * 身份卡改成"默认什么都不显示"之后，本函数的参数从
 * `nickname / school / boundStudentId / chaoxingBound / zhihuishuBound / dormLabel`
 * 六个字符串与布尔，收敛成一个 `boundCount: Int`。
 *
 * 这不是顺手简化：**它让"我的页不显示个人信息"变成结构上做不到** ——
 * 学号、宿舍这些值现在根本进不来（`ProfilePrivacyTest` 从这一层断言）。
 * 要再往这一页加身份字段，必须先过那道测试。
 */
@Composable
fun ProfileHomeContent(
    /** 已绑定项数（教务 / 宿舍 / 学习平台，0..3）—— 身份卡只显示这个数字 */
    boundCount: Int,
    onOpenBinding: () -> Unit,
    electricityBalance: String?,
    electricityLoading: Boolean,
    onOpenElectricity: () -> Unit,
    /** 专注计时格子的副标题 —— 今日专注时长（文案由 `focusTileHint` 生成，与专注页同口径） */
    focusHint: String = "今天还没开始",
    onOpenFocus: () -> Unit,
    /** 专注统计块的整份数据（概览 + 热力图 + 柱状图 + 分段图），由 `FocusStats.focusStatsView` 一次算好 */
    focusStats: FocusStatsView = FocusStatsView(),
    /** 洗澡格子的副标题：趣智钱包余额（见 `showerTileHint`） */
    showerHint: String,
    onOpenShower: () -> Unit,
    onOpenExpress: () -> Unit,
    onSchemeManager: () -> Unit,
    onImportMenu: () -> Unit,
    onShareMenu: () -> Unit,
    /** 打开全 App 唯一的设置页（课表 / 外观 / 服务功能 / 关于 / 更新 / 贡献 / 致谢 / 危险操作） */
    onSettings: () -> Unit
) {
    val colors = LocalPocketXiaoliColors.current
    val compact = LocalCampusLayoutMode.current == CampusLayoutMode.TOOLBOX
    val listState = rememberCampusLazyListState("profile_home")
    // 本页自己接管状态栏（见 AppNavigation 的 SELF_MANAGED_TOP_ROUTES）：
    // 底色与顶部的彩晕一直铺到屏幕顶端，列表内容再靠 statusBarsPadding 让位。
    // 彩晕只在「彩色」用色强度下画 —— 纯净模式要的是中性底，见 IconPresentation。
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
                    .height(if (compact) 200.dp else 240.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                // 与课表顶栏同一档：浅色 0.18 / 深色 0.28。
                                // 再淡就只是"底色偏了一点"，看不出这是一层沉浸式头图。
                                colors.accentMain.copy(alpha = if (isDark) 0.28f else 0.18f),
                                Color.Transparent,
                            )
                        )
                    )
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (compact) 12.dp else 16.dp,
                // 底部让出底栏（非 spacing 语义）。必须跟着底栏**实际高度**走，不能写死 ——
                // 原来写死的 104/112dp 比底栏的 88dp 多出二十几 dp，表现就是"滚到底后最后一张卡
                // 停得离栏很远"。这里只多留 12dp 呼吸，好让内容能从栏背后滚过去。
                bottom = LocalBottomBarInset.current + 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)
        ) {
        item {
            ProfileIdentityCard(
                boundCount = boundCount,
                onOpenBinding = onOpenBinding
            )
        }

        item {
            ProfileStatsGrid(
                electricityBalance = electricityBalance,
                electricityLoading = electricityLoading,
                showerHint = showerHint,
                focusHint = focusHint,
                onElectricity = onOpenElectricity,
                onFocus = onOpenFocus,
                onShower = onOpenShower,
                onExpress = onOpenExpress
            )
        }

        // 专注统计：紧贴上面那格「专注计时」磁贴 —— 磁贴答"今天多久"，
        // 这一块答"最近怎么样""今天是怎么攒起来的"。
        //
        // 渲染整体复用专注页的 `FocusStatsSection`（含它的空态文案），
        // 本页只负责把**一份算好的数据**传进来；数据来自 `focus_sessions` 的同一份观察者，
        // 口径与专注页逐字一致（两边都调 `FocusStats.focusStatsView`）。
        // 参数里没有 container，本组件仍然纯参数可单独渲染。
        item {
            FocusStatsSection(stats = focusStats)
        }

        item {
            SettingsGroupCard(
                title = "课表与学习",
                items = listOf(
                    SettingsItemData(
                        icon = Icons.AutoMirrored.Outlined.ListAlt,
                        iconFilled = Icons.AutoMirrored.Filled.ListAlt,
                        title = "课表管理",
                        subtitle = "管理多张课表和方案",
                        accent = ServiceAccent.ACADEMIC,
                        onClick = onSchemeManager
                    ),
                    SettingsItemData(
                        icon = Icons.Outlined.FileDownload,
                        iconFilled = Icons.Filled.FileDownload,
                        title = "导入课表",
                        subtitle = "教务直连 / 网页 / 文件 / 分享口令",
                        accent = ServiceAccent.ACADEMIC,
                        onClick = onImportMenu
                    ),
                    SettingsItemData(
                        icon = Icons.Outlined.Share,
                        iconFilled = Icons.Filled.Share,
                        title = "备份与分享",
                        subtitle = "导出备份、分享课表",
                        accent = ServiceAccent.QUERY,
                        onClick = onShareMenu
                    )
                )
            )
        }

        // 设置的唯一入口。
        //
        // ⚠️ 刻意**单独成组**，而不是塞进上面「课表与学习」那组：
        // 它是本页唯一一条"离开这一页"的路，混进课表那组会让用户以为里面装的还是课表相关的东西。
        //
        // ⚠️ 而且这一条**必须只出现一次**。改造前「课表设置」是「我的」页里的第 4 条
        // （在课表那组里），同时「设置」又是另一组的第 1 条 —— 两个并列的设置根，
        // 都得靠猜。现在「课表设置」已合并进设置页（`SettingsMainScreen` 已删除），
        // 本页只剩这一个入口。
        item {
            SettingsGroupCard(
                title = "设置",
                items = listOf(
                    SettingsItemData(
                        icon = Icons.Outlined.Settings,
                        iconFilled = Icons.Filled.Settings,
                        title = "设置",
                        subtitle = "课表、外观、服务功能、关于与更新",
                        accent = ServiceAccent.INFO,
                        onClick = onSettings
                    )
                )
            )
        }
        }
    }
}
