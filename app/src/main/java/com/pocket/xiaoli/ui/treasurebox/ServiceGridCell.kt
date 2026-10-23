package com.pocket.xiaoli.ui.treasurebox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.currentIconPresentation
import com.pocket.xiaoli.ui.theme.miuixPress
import com.pocket.xiaoli.ui.theme.serviceAccentColor
import com.pocket.xiaoli.ui.theme.serviceAccentContainer

/** 金刚格的列数。放这里而不是渲染处 —— 列数是**这个单元格的形状属性**，改它就该同时看单元格宽度。 */
internal const val SERVICE_GRID_COLUMNS = 4

/**
 * 「工具箱模式」（[com.pocket.xiaoli.ui.theme.CampusLayoutMode.TOOLBOX]）下的**紧凑图标宫格**单元格。
 *
 * ## 为什么需要它 —— 这条轴原先名不副实
 *
 * `CampusLayoutMode`（现代模式 / 工具箱模式）早在 2026-05 就存在，`ServiceCard` 也一直在读它，
 * 但**它只改卡片的尺寸**（高度 78→64dp、图标 36→30dp、内边距收紧）——
 * **布局形状始终是 `chunked(2)` 的富卡片**。于是「工具箱模式」看起来只是"卡片变小了一点"，
 * 而不是"变成一个工具箱"。
 *
 * 本单元格补上缺的那一半：**4 列、纯图标 + 名称**。
 *
 * 旁证：`AppearanceCategoryScreen` 里留过一条注释 ——「原本有『网格列数 3/4/5』已移除…
 * 要支持列数得**先有紧凑图标模式**，那是另一个功能」。**这就是那个"另一个功能"。**
 *
 * ## 刻意保留角标（不要省）
 *
 * 单元格里塞得下图标与名称，看着"角标可以省"—— **不能**。
 * 本项目有过一次正相反的教训（见 `ServiceCard` 附近的记录）：
 * `withSummary` 算出了角标却从不渲染，等于把"这个服务为什么不能用"
 * **静默丢掉**。金刚格下服务更多、更需要状态提示，省掉它是同一个错误的翻版。
 *
 * ## 触摸高度
 *
 * 单元格最小高度 = 图标 44dp + 间距 + 名称，**远高于本项目 44dp 的无障碍底线**；
 * 且整格 `clickable`，不存在"看得见点不准"的问题。
 */
@Composable
internal fun ServiceGridCell(
    entry: ServiceEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = entry.onClick,
) {
    val colors = LocalPocketXiaoliColors.current
    val icon = currentIconPresentation()
    val skin = LocalSkin.current

    // 取色逻辑与 `ServiceCard` **逐条对齐**（有语义分类 → 从主题派生；否则回退旧色对），
    // 否则同一套配色下"卡片是彩色、格子里是单色"会破相。
    // 深色适配由 `serviceAccentContainer` 自己按 `LocalDarkMode` 降 alpha 处理，这里不重复判。
    val accentTint = entry.accent?.let { serviceAccentColor(it) }
    val accentContainer = entry.accent?.let { serviceAccentContainer(it) }
    val iconTint = if (icon.colorful) (accentTint ?: entry.tint) else colors.accentMain
    val iconBackground = if (icon.showContainer) {
        accentContainer ?: entry.tint.copy(alpha = 0.14f)
    } else {
        colors.surfaceHighlight
    }
    // 彩色态给格子底加一道类目色渐变，和富卡片同一套光照方向。
    val iconBrush = if (icon.colorful && accentTint != null) {
        Brush.linearGradient(
            listOf(
                accentTint.copy(alpha = 0.32f),
                accentTint.copy(alpha = 0.12f),
            )
        )
    } else {
        null
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(skin.radiusCard))
            .miuixPress(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(skin.radiusChip))
                .then(
                    if (iconBrush != null) Modifier.background(iconBrush)
                    else Modifier.background(iconBackground)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (icon.filled) entry.iconFilled else entry.icon,
                contentDescription = entry.title,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(skin.spaceXs))
        Text(
            text = entry.title,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = colors.textPrimary,
            // 两行：4 列下每格约 76dp 宽，中文名 4~6 字在 11sp 下大多能一行放下，
            // 放不下的**折行**比截断好读（课表格子那边踩过"截断只有渲出来才看得见"的坑）。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        entry.badge?.let { label ->
            Spacer(Modifier.height(skin.spaceMicro))
            ServiceBadge(label = label, tone = entry.badgeTone, colorful = icon.colorfulTags)
        }
    }
}
