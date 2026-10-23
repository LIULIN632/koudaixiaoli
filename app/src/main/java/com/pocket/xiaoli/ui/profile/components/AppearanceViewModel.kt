package com.pocket.xiaoli.ui.profile.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pocket.xiaoli.di.AppContainer
import kotlinx.coroutines.flow.*

class AppearanceViewModel(
    private val container: AppContainer
) : ViewModel() {

    // Kotlin 2.4 起，reified 类型参数推断成交叉类型（Comparable<*> & Serializable）会直接报错。
    // 这里显式指定 T = Any：16 个流的输出类型各异，vararg 重载要求它们统一成同一类型，
    // 下面按位置强转回来（values[0] as Boolean ... ）本来就是这么设计的。
    val uiState: StateFlow<AppearanceUiState> = combine<Any, AppearanceUiState>(
        container.showTeacher,
        container.showRoom,
        container.showCampus,
        container.showSlotTime,
        container.borderStyle,
        container.textCentered,
        container.gridHeight,
        container.cornerRadius,
        container.gridOpacity,
        container.gridTextSize,
        container.showNonCurrentWeek,
        container.showOddEven,
        container.gridBgColor,
        container.otherWeekAlpha,
        container.wallpaperUri,
        container.verticalLayout
    ) { values ->
        AppearanceUiState(
            showTeacher = values[0] as Boolean,
            showRoom = values[1] as Boolean,
            showCampus = values[2] as Boolean,
            showSlotTime = values[3] as Boolean,
            borderStyle = values[4] as Int,
            textCentered = values[5] as Boolean,
            gridHeight = values[6] as Int,
            cornerRadius = values[7] as Int,
            gridOpacity = values[8] as Float,
            gridTextSize = values[9] as Int,
            showNonCurrentWeek = values[10] as Boolean,
            showOddEven = values[11] as Boolean,
            gridBgColor = values[12] as Int,
            otherWeekAlpha = values[13] as Float,
            wallpaperUri = values[14] as String,
            verticalLayout = values[15] as Boolean
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppearanceUiState())

    fun onIntent(intent: AppearanceIntent) {
        when (intent) {
            is AppearanceIntent.SetTeacher -> container.setShowTeacher(intent.value)
            is AppearanceIntent.SetRoom -> container.setShowRoom(intent.value)
            is AppearanceIntent.SetCampus -> container.setShowCampus(intent.value)
            is AppearanceIntent.SetSlotTime -> container.setShowSlotTime(intent.value)
            is AppearanceIntent.SetBorderStyle -> container.setBorderStyle(intent.style)
            is AppearanceIntent.SetTextCentered -> container.setTextCentered(intent.value)
            is AppearanceIntent.SetGridHeight -> container.setGridHeight(intent.height)
            is AppearanceIntent.SetCornerRadius -> container.setCornerRadius(intent.radius)
            is AppearanceIntent.SetOpacity -> container.setGridOpacity(intent.opacity)
            is AppearanceIntent.SetTextSize -> container.setGridTextSize(intent.size)
            is AppearanceIntent.SetNonCurrentWeek -> container.setShowNonCurrentWeek(intent.value)
            is AppearanceIntent.SetOddEven -> container.setShowOddEven(intent.value)
            is AppearanceIntent.SetBgColor -> container.setGridBgColor(intent.index)
            is AppearanceIntent.SetOtherWeekAlpha -> container.setOtherWeekAlpha(intent.alpha)
            is AppearanceIntent.SetWallpaper -> container.setWallpaperUri(intent.uri)
            is AppearanceIntent.SetVerticalLayout -> container.setVerticalLayout(intent.value)
            is AppearanceIntent.ResetDefaults -> resetDefaults()
        }
    }

    private fun resetDefaults() {
        container.setShowTeacher(true)
        container.setShowRoom(true)
        container.setShowCampus(false)
        container.setShowSlotTime(false)
        container.setBorderStyle(0)
        container.setTextCentered(false)
        container.setGridHeight(64)
        container.setCornerRadius(4)
        container.setGridOpacity(1.0f)
        container.setGridTextSize(12)
        container.setShowNonCurrentWeek(true)
        container.setShowOddEven(true)
        container.setGridBgColor(-1)
        container.setOtherWeekAlpha(0.50f)
        container.setWallpaperUri("")
        container.setVerticalLayout(true)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppearanceViewModel(container) as T
        }
    }
}
