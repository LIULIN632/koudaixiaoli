package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(skin.radiusChip),
        color = colors.surfaceAlt,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(skin.spaceXs),
            horizontalArrangement = Arrangement.spacedBy(skin.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { (label, value) ->
                val active = value == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(skin.radiusChip))
                        .background(if (active) colors.accentMain else Color.Transparent)
                        .clickable { onSelect(value) }
                        // 12sp 文字 + 7dp 上下内距 ≈ 31dp 高，够不着；
                        // 抬高热区而不是可见高度（见 ui/theme/TouchTarget.kt）。
                        .invisibleTouchTarget()
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
