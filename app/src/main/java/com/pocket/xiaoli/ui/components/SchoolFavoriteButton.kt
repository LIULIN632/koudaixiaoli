package com.pocket.xiaoli.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocket.xiaoli.ui.theme.invisibleTouchTarget

private val StarGold = Color(0xFFFFD700)

/**
 * Shared star toggle button for school favorite functionality.
 * Used in ImportScreen and WebImportScreen.
 */
@Composable
fun SchoolFavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(40.dp).invisibleTouchTarget()
    ) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            tint = if (isFavorite) StarGold else Color(0xFF818C99), // TextTertiary
            modifier = Modifier.size(22.dp)
        )
    }
}
