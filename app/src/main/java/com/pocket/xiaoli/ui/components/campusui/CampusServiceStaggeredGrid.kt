package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** CampusUI 服务宫格的一项。 */
data class CampusServiceGridItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val badge: String? = null,
)

/**
 * 服务宫格。
 *
 * 用官方 `LazyVerticalStaggeredGrid`，避免第三方瀑布流依赖。
 * 以后服务卡片高度不同（带摘要 / 实时状态）也能自然错落。
 */