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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.BottomBarStyle
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.TabAppearance

/**
 * 外观中心：**按界面分类**的外观设置入口。
 *
 * ## 为什么改成分类入口，而不是把设置项平铺在一起
 *
 * 改造前的「个性化配置」是一个 826 行的长页，里面**只有课表格子的设置**。
 * 用户想调"首页显示什么"时，会先点进这个页面、滚一遍、才发现根本没有这项 ——
 * 页面的名字（个性化配置）承诺了比它实际提供的东西更多的范围。
 *
 * 现在名字与范围一致：**先选界面，再调那个界面**。
 *
 * ## 每一项都显示当前值
 *
 * 副标题直接显示当前配置（如"标准 · 显示思考链"）。这样用户不必逐个点进去
 * 才能确认自己改过什么 —— 设置页最常见的诉求就是"我上次改的是哪个"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceHubScreen(
    onBack: () -> Unit,
    onOpenCategory: (AppearanceCategory) -> Unit,
    bottomBarStyle: BottomBarStyle,
    tabAppearance: TabAppearance,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current

    Scaffold(
        containerColor = colors.surfaceBase,
        topBar = {
            TopAppBar(
                title = { Text("外观", fontWeight = FontWeight.Bold) },
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
            Text(
                "选择要调整的界面。这里的设置只影响对应界面；配色主题、深色模式等全局项在「我的 → 设置」。",
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = skin.spaceXs),
            )

            AppearanceCategory.entries.forEach { category ->
                CategoryRow(
                    category = category,
                    summary = summaryFor(category, bottomBarStyle, tabAppearance),
                    onClick = { onOpenCategory(category) },
                )
            }

            Spacer(Modifier.height(skin.spaceSm))
        }
    }
}

/** 某个分类的当前配置摘要。**必须反映真实值** —— 写了固定文案就等于骗用户。 */
private fun summaryFor(
    category: AppearanceCategory,
    bottomBarStyle: BottomBarStyle,
    tab: TabAppearance,
): String = when (category) {
    AppearanceCategory.HOME -> "模块显隐与排序（在独立页面）"

    // 课表页的设置项太多（826 行的那一页），这里不逐项摘要，直接说明范围
    AppearanceCategory.TIMETABLE -> "课程卡片 · 格子 · 背景 · 壁纸"

    // 鲤鱼的三项已经搬到鲤鱼页的「AI 设置」（与模型配置同页），这里只说范围、
    // 不逐项摘要 —— 摘要写的是"在这里能改什么"，而这一页已经改不了了。
    // 与 HOME / TIMETABLE 两个"指针分类"同一套口径。
    AppearanceCategory.ASSISTANT -> "形象 · 字号 · 思考链 · 模型（在鲤鱼页）"

    // 网格列数已移除：服务页是富卡片布局，2 列是有意设计，列数不可调
    AppearanceCategory.SERVICES -> if (tab.servicesShowSectionTitle) "显示分类标题" else "隐藏分类标题"

    AppearanceCategory.PROFILE -> buildList {
        add(
            when (tab.profileDensity) {
                0 -> "紧凑"
                2 -> "宽松"
                else -> "标准"
            }
        )
        add(if (tab.profileShowFooter) "显示底部信息" else "隐藏底部信息")
    }.joinToString(" · ")

    AppearanceCategory.BOTTOM_BAR -> bottomBarStyle.label +
        bottomBarStyle.tierNote()
}

@Composable
private fun CategoryRow(
    category: AppearanceCategory,
    summary: String,
    onClick: () -> Unit,
) {
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
        Icon(
            imageVector = iconFor(category),
            contentDescription = null,
            tint = colors.accentMain,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(skin.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(category.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 12.sp, color = colors.textTertiary, lineHeight = 16.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun iconFor(category: AppearanceCategory): ImageVector = when (category) {
    AppearanceCategory.HOME -> Icons.Outlined.Home
    AppearanceCategory.TIMETABLE -> Icons.Outlined.CalendarMonth
    AppearanceCategory.ASSISTANT -> Icons.Outlined.AutoAwesome
    AppearanceCategory.SERVICES -> Icons.Outlined.GridView
    AppearanceCategory.PROFILE -> Icons.Outlined.Person
    AppearanceCategory.BOTTOM_BAR -> Icons.Outlined.Smartphone
}
