package com.pocket.xiaoli.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 系统栏图标该用深色还是浅色。
 *
 * 2026-10-19 之前这里没有函数也没有调用者：状态栏底色**固定**写在
 * `app/src/main/res/values/themes.xml` 的 `android:statusBarColor`（`#FFFFFF`），
 * 而图标明暗由 `MainActivity` 里的 `isAppearanceLightStatusBars = !isDark` 单独决定。
 * 两者各说各话，于是出现两种肉眼可见的错：
 *
 * 1. **深色模式下一整条白**：底色还是 `#FFFFFF`（没有 `values-night`），
 *    而图标被切成浅色 —— 白底白图标，等于把系统栏藏了。
 * 2. **跟页面底色永远差半度**：页面画的是 `surfaceBase`（亮 `#F7F8FA` / 暗 `#0F1115`），
 *    系统栏画的却是写死的白。
 *
 * 现在两边都从页面底色（`PocketXiaoliColors.surfaceBase`）推：
 * 底色由 `MainActivity` 直接画，图标明暗走这个函数 —— 只有一个判据，
 * 换皮肤、换深浅色、换自定义强调色都不会再分裂。
 *
 * 判据是**亮度 0.5**（WCAG 相对亮度，非 HSL 的 L）：页面底色只可能是
 * 「接近白」或「接近黑」两类，中间灰不会作为页底色出现（`SkinTokens.applySurfaces`
 * 只在这两端之间推移），所以 0.5 这条线不敏感。返回值语义跟随命名：
 * `true` = 深色图标（配浅底），即 `isAppearanceLightStatusBars` 要传的值。
 */
internal fun wantsDarkStatusBarIcons(background: Color): Boolean =
    background.luminance() > 0.5f
