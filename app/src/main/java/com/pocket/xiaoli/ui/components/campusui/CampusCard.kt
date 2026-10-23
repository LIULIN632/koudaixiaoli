package com.pocket.xiaoli.ui.components.campusui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.LocalPocketXiaoliColors

/**
 * CampusUI 基础卡片。
 *
 * 页面不再各自决定圆角 / 阴影 / 间距；默认值来自 [CampusTokens]。
 */
@Composable
fun CampusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(CampusTokens.CardRadius),
    containerColor: Color = LocalPocketXiaoliColors.current.surfaceCard,
    border: BorderStroke? = BorderStroke(1.dp, LocalPocketXiaoliColors.current.borderDivider),
    elevation: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(CampusTokens.CardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(CampusTokens.ItemGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}