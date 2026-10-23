package com.pocket.xiaoli.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppearanceMode(val key: String, val label: String) {
    RAINBOW("rainbow", "彩虹模式"),
    PURE("pure", "纯净模式");

    companion object {
        fun fromKey(key: String): AppearanceMode =
            entries.firstOrNull { it.key == key } ?: RAINBOW
    }
}

val LocalAppearanceMode = staticCompositionLocalOf { AppearanceMode.RAINBOW }
/** 布局模式：现代大卡片 / 紧凑工具箱（先用于服务页，后续可扩展到首页与我的）。 */
enum class CampusLayoutMode(val key: String, val label: String) {
    MODERN("modern", "现代模式"),
    TOOLBOX("toolbox", "工具箱模式");

    companion object {
        fun fromKey(key: String?): CampusLayoutMode =
            entries.firstOrNull { it.key == key } ?: MODERN
    }
}

val LocalCampusLayoutMode = staticCompositionLocalOf { CampusLayoutMode.MODERN }
