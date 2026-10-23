package com.pocket.xiaoli.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocket.xiaoli.ui.theme.BottomBarStyle
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.TabAppearanceStore
import com.pocket.xiaoli.ui.theme.LocalProfileRowPadding

/**
 * 单个界面的外观设置页。
 *
 * ## 为什么一个页面用枚举驱动，而不是六个页面
 *
 * 六份各自独立的页面会各自漂移：间距不同、开关样式不同、文案口径不同，
 * 而且加一个通用能力要改六遍。这里结构统一（分组 + 行），差异只体现在**有哪些行**上。
 *
 * ## 课表页为什么跳走
 *
 * 课表外观是既有的一整页（`AppearanceScreen`：实时预览、壁纸选择、参数微调、恢复默认），
 * 把它拆进这个统一结构会把那套预览一起拆散。所以课表分类直接打开原页面 ——
 * **结构统一的前提是不牺牲既有能力**。
 *
 * ## 为什么直接传 store 而不是回调
 *
 * store 已有类型安全的 setter（`setHomeShowGreeting` 等），直接调它比
 * "传一个 (transform, apply) 二元组"清楚得多，也少一层容易写错的间接。
 * 值则从 store 的 StateFlow 收集 —— **不能直接读 store 的属性**：
 * 属性读的是内存快照，不参与快照系统，改了不会触发重组。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceCategoryScreen(
    category: AppearanceCategory,
    store: TabAppearanceStore,
    bottomBarStyle: BottomBarStyle,
    onBack: () -> Unit,
    onBottomBarStyle: (BottomBarStyle) -> Unit,
    onOpenTimetableAppearance: () -> Unit,
    onOpenHomeCustomize: () -> Unit,
    /** 打开鲤鱼页的「AI 设置」（跨 tab，由导航层注入）。见 ASSISTANT 分类的说明。 */
    onOpenAiSettings: () -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    val tab by store.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text(category.label + "外观", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(colors.surfaceAlt)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = skin.spaceLg, vertical = skin.spaceMd),
            verticalArrangement = Arrangement.spacedBy(skin.spaceSm),
        ) {
            when (category) {
                AppearanceCategory.HOME -> {
                    // ⚠️ 这里**刻意不提供**首页显隐/排序开关。
                    //
                    // 首页已有完整的模块管理（`HomeModule` + `HomeLayout` + `CustomHomeScreen`，
                    // 支持显隐与拖动排序，入口在「我的 → 设置 → 自定义首页」）。
                    // 在这里再放一套"显示今日课程"之类的开关，就会变成**两套并存的机制** ——
                    // 用户在一边关掉、另一边还开着，而且谁都说不清哪边优先。
                    //
                    // 所以这一页只做一件事：把用户送到那个既有页面。
                    Text(
                        "首页的模块显隐与排序已有独立页面（支持拖动排序），不在这里重复一套设置。",
                        fontSize = 12.sp, color = colors.textSecondary, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    ActionRow(title = "打开「自定义首页」", onClick = onOpenHomeCustomize)
                }

                AppearanceCategory.TIMETABLE -> {
                    Text(
                        "课表外观项较多（课程卡片、格子尺寸、边框、背景与壁纸），已单独成页并带实时预览。",
                        fontSize = 12.sp, color = colors.textSecondary, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    ActionRow(title = "打开课表外观设置", onClick = onOpenTimetableAppearance)
                }

                AppearanceCategory.ASSISTANT -> {
                    // ⚠️ 鲤鱼的三个设置项（形象 / 回答字号 / 思考链）**已经搬到鲤鱼页的「AI 设置」**，
                    // 这里刻意**不再放一份**。
                    //
                    // 放一份的后果不是"多一个入口"，而是两套并存的机制：两边的开关都写同一个
                    // store，看起来一致，但用户会永远不确定"我改的这个是不是那个"。
                    // 而且模型配置本来也在那一页 —— 同一个助手的设置分居两处，
                    // 用户得先猜"我要改的属于哪一类"。
                    //
                    // 所以这一页只做一件事：把用户送到那个既有页面（与上面 HOME / TIMETABLE 同一套做法）。
                    Text(
                        "鲤鱼的形象、回答字号、思考链与模型配置都在鲤鱼页的「AI 设置」里，不在这里重复一套。",
                        fontSize = 12.sp, color = colors.textSecondary, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(skin.spaceXs))
                    ActionRow(title = "打开「AI 设置」", onClick = onOpenAiSettings)
                }

                AppearanceCategory.SERVICES -> {
                    // ⚠️ 原本这里有「网格列数 3/4/5」。已移除 —— 服务页是
                    // `section.entries.chunked(2)` 的**富卡片**布局（图标 + 标题 + 一行说明），
                    // 2 列是有意设计，塞 3/4 列会把卡片挤碎。要支持列数得先有紧凑图标模式，
                    // 那是另一个功能；在没有它之前提供这个开关等于给用户一个坏掉的选项。
                    SwitchRow(
                        title = "显示分类标题",
                        subtitle = "「学习 / 生活 / 查询」分组标题与切换标签",
                        checked = tab.servicesShowSectionTitle,
                        onToggle = { store.setServicesShowSectionTitle(it) },
                    )
                }

                AppearanceCategory.PROFILE -> {
                    ChoiceRow(
                        title = "列表密度",
                        subtitle = "设置项的上下留白",
                        options = listOf("紧凑" to 0, "标准" to 1, "宽松" to 2),
                        selected = tab.profileDensity,
                        onSelect = { store.setProfileDensity(it) },
                    )
                    SwitchRow(
                        title = "显示底部信息",
                        subtitle = "页面底部的版本号与说明文字",
                        checked = tab.profileShowFooter,
                        onToggle = { store.setProfileShowFooter(it) },
                    )
                }

                AppearanceCategory.BOTTOM_BAR -> {
                    ChoiceRow(
                        title = "底栏样式",
                        subtitle = "液态玻璃按系统能力三档降级，形状一致、效果逐级减弱",
                        options = BottomBarStyle.entries.map { it.label to it.key },
                        selected = bottomBarStyle.key,
                        onSelect = { key ->
                            BottomBarStyle.entries.firstOrNull { it.key == key }?.let(onBottomBarStyle)
                        },
                    )
                    Text(
                        "说明：玻璃采样的就是**栏背后滚过去的内容**，所以首页、服务页这类长列表上效果最明显，" +
                            "静止的页面看着和磨砂差不多。折射需要 Android 13+；Android 12 有模糊无折射；" +
                            "Android 10/11 只显示半透明磨砂。三档的形状与布局完全一致。",
                        fontSize = 12.sp, color = colors.textTertiary, lineHeight = 17.sp,
                        modifier = Modifier.padding(top = skin.spaceXs),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, RoundedCornerShape(skin.radiusChip))
            .padding(horizontal = skin.spaceLg, vertical = LocalProfileRowPadding.current),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = colors.textTertiary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(skin.spaceMd))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentMain),
        )
    }
}

/**
 * 一行「多选一」。做成泛型并用**值本身**判等，所以枚举与 Int 都能直接用 ——
 * 不必把枚举转成 key 再转回来，那层转换写错就是个"选中态不对"的 bug 且很难发现。
 */
@Composable
private fun <T> ChoiceRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, RoundedCornerShape(skin.radiusChip))
            .padding(horizontal = skin.spaceLg, vertical = LocalProfileRowPadding.current),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = colors.textTertiary, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(skin.spaceSm)) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentMain,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, onClick: () -> Unit) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceCard, RoundedCornerShape(skin.radiusChip))
            .clickable(onClick = onClick)
            .padding(horizontal = skin.spaceLg, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accentMain,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
