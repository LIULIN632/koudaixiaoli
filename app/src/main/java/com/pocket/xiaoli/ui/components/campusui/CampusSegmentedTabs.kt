package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors
import com.pocket.xiaoli.ui.theme.LocalSkin
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget
import com.pocket.xiaoli.ui.theme.onAccentColor

/**
 * CampusUI 通用分段选择。
 *
 * 参考岱宗盒子的分段切换交互，自己实现：
 * - 外层浅色托盘；
 * - 选中项主色填充；
 * - 支持横向滚动，避免小屏截断。
 */
@Composable
fun <T> CampusSegmentedTabs(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    val skin = LocalSkin.current
    val colors = LocalPocketXiaoliColors.current
    // 托盘宽度跟着用途走：可滚 = 通栏（灰底提示"还能往右滑"）；
    // 不可滚 = 贴着内容收（两三个选项时通栏会剩一大条空灰底，基线 315 上就是这样）。
    Surface(
        modifier = modifier.then(if (scrollable) Modifier.fillMaxWidth() else Modifier.wrapContentWidth()),
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceAlt,
    ) {
        Row(
            modifier = Modifier
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(skin.spaceXs),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { (label, value) ->
                val active = value == selected
                // ⚠️ 触摸热区与**可见**填充必须是两层：`invisibleTouchTarget()` 是
                // `widthIn/heightIn(min = 44dp)`，直接挂在画底色的 Box 上会把选中块
                // 撑成一枚 44dp 的大实心方（基线 315 上「本周」那一坨就是这么来的）。
                // 外层只管点得到，内层只管长什么样。
                Box(
                    modifier = Modifier
                        .invisibleTouchTarget()
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(skin.radiusChip))
                            .background(if (active) colors.accentMain else Color.Transparent)
                            .padding(horizontal = skin.spaceCompact, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            // 选中态底是强调色填充 ⇒ 用 onAccentColor() 而不是写死白色
                            color = if (active) onAccentColor() else colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
