package com.pocket.xiaoli.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.popup.WindowDropdownPopup

/**
 * 点一行就弹出来的选项气泡 —— **MIUIX 的 WindowDropdownPopup**，与岱宗同一套实现
 * （`xjtu-toolbox-android/.../preference/WindowDropdownPreference.kt` 里组合的就是它）。
 *
 * 为什么不再自己用 `Popup` 画：自绘版只能靠 Material3 的 `DropdownMenuItem` 撑高度
 * （48dp/项 + 8dp 内边距），一行三选项就要 160dp 以上，圆角、分隔线、按下反馈也都得自己补；
 * MIUIX 这套是 20dp 圆角胶囊卡片 + 32dp 紧凑行 + 选中态高亮 + 点击触觉反馈，尺寸只包内容。
 *
 * ⚠️ **必须放在被点的那一行里面**（作为它的子 Composable）：MIUIX 用
 * `parentLayoutCoordinates.positionInWindow()` 定位（见 `miuix-ui/.../layout/ListPopupLayout.kt:127`），
 * 放外层会让气泡飘到整块区域的下边而不是这一行的下边。
 *
 * ⚠️ 依赖 `MiuixTheme`（App 根部已提供，见 `ui/theme/Theme.kt`）—— 它的文字与配色
 * **不跟本 App 的皮肤令牌走**，这是有意为之：追求的就是岱宗那个原生观感。
 *
 * @param options 选项文字，顺序即展示顺序。
 * @param selectedIndex 选中项下标；调用方不需要选中态时传 -1。
 * @param onSelect 点选回调，返回下标；回调后气泡自动收起。
 */
@Composable
internal fun MiuiMenuPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    options: List<String>,
    onSelect: (Int) -> Unit,
    selectedIndex: Int = -1,
    maxHeight: Dp? = null,
) {
    if (options.isEmpty()) return
    val entry = remember(options, selectedIndex) {
        DropdownEntry(
            items = options.mapIndexed { index, text ->
                DropdownItem(
                    text = text,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            },
        )
    }
    WindowDropdownPopup(
        entry = entry,
        show = expanded,
        onDismiss = onDismissRequest,
        onDismissFinished = {},
        maxHeight = maxHeight,
        dropdownColors = DropdownDefaults.dropdownColors(),
        collapseOnSelection = true,
    )
}

/** 气泡卡片的默认最大高度：选项多到几十个时别顶到屏幕外。 */
internal val MiuiMenuMaxHeight: Dp = 320.dp
