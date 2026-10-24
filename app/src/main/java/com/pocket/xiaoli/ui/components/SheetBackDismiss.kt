package com.pocket.xiaoli.ui.components

import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

/**
 * 让 MIUIX `WindowBottomSheet` 支持系统返回键关闭。
 *
 * ## 为什么不能直接写 `BackHandler`
 *
 * MIUIX 0.9.3 的 `WindowBottomSheet` 用的是 `DialogProperties(dismissOnBackPress = false)`。
 * Compose 的 `Dialog` 仍然会向**弹窗自己的** `OnBackPressedDispatcher` 注册一个始终启用的
 * 回调（`dismissOnBackPress` 为 false 时它什么也不做），返回键因此被弹窗吞掉；
 * 而弹窗内容的 CompositionLocal 是从宿主页面继承的，普通 `BackHandler` 注册到的是
 * **Activity** 的分发器，永远轮不到 —— 表现就是"返回键没反应，只有点空白能关"。
 *
 * 这里从 `LocalView` 往上找到弹窗自己的分发器再注册。`OnBackPressedDispatcher` 后注册先执行，
 * 所以这个回调优先于弹窗内建的那个。
 *
 * 必须放进 `WindowBottomSheet` 的 content 里（外面拿到的是宿主页面的 View）。
 *
 * @param enabled 强更等不允许关闭的抽屉传 false
 */
@Composable
fun SheetBackToDismiss(enabled: Boolean = true, onDismiss: () -> Unit) {
    val view = LocalView.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val dispatcher = view.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher
        if (dispatcher == null) return@DisposableEffect onDispose {}
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = currentOnDismiss()
        }
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}
