package com.pocket.xiaoli.ui.profile.components

sealed interface AppearanceIntent {
    data class SetTeacher(val value: Boolean) : AppearanceIntent
    data class SetRoom(val value: Boolean) : AppearanceIntent
    data class SetCampus(val value: Boolean) : AppearanceIntent
    data class SetSlotTime(val value: Boolean) : AppearanceIntent
    data class SetBorderStyle(val style: Int) : AppearanceIntent
    data class SetTextCentered(val value: Boolean) : AppearanceIntent
    data class SetGridHeight(val height: Int) : AppearanceIntent
    data class SetCornerRadius(val radius: Int) : AppearanceIntent
    data class SetOpacity(val opacity: Float) : AppearanceIntent
    data class SetTextSize(val size: Int) : AppearanceIntent
    data class SetNonCurrentWeek(val value: Boolean) : AppearanceIntent
    data class SetOddEven(val value: Boolean) : AppearanceIntent
    data class SetBgColor(val index: Int) : AppearanceIntent
    data class SetOtherWeekAlpha(val alpha: Float) : AppearanceIntent
    data class SetWallpaper(val uri: String) : AppearanceIntent
    data class SetVerticalLayout(val value: Boolean) : AppearanceIntent
    data object ResetDefaults : AppearanceIntent
}

data class AppearanceUiState(
    val showTeacher: Boolean = true,
    val showRoom: Boolean = true,
    val showCampus: Boolean = false,
    val showSlotTime: Boolean = false,
    val borderStyle: Int = 0,
    val textCentered: Boolean = true,
    val gridHeight: Int = 64,
    val cornerRadius: Int = 4,
    val gridOpacity: Float = 1.0f,
    val gridTextSize: Int = 12,
    val showNonCurrentWeek: Boolean = false,
    val showOddEven: Boolean = true,
    val gridBgColor: Int = -1,
    val otherWeekAlpha: Float = 0.50f,
    val wallpaperUri: String = "",
    val verticalLayout: Boolean = true
)
