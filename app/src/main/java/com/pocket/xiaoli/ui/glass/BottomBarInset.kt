package com.pocket.xiaoli.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

/**
 * 底栏**栏体**高度（不含系统手势区），供页面预留底部空间。
 *
 * ## 为什么需要它
 *
 * 底栏从 `Scaffold` 的 `bottomBar` 槽里搬了出来，改成叠在内容之上的浮层 ——
 * 只有内容能延伸到栏背后，玻璃才有**采样源**，折射才折得出东西。
 * 代价是 `Scaffold` 不再替页面把栏的高度预留出来，所以必须换个地方给：
 * 由页面自己在滚动内容的底部把它让出来。
 *
 * ## 数值的含义
 *
 * - **含**栏体自身的外边距（`Surface` 外面那圈 16/10dp），因为页面要躲的是"整条栏占的地方"；
 * - **不含**系统手势区 —— 那部分由 `Scaffold` 的 `innerPadding` 负责，两边都加就会多出一段空白。
 * - 底栏不可见时（子页面、非 tab 路由）由 `AppNavigation` 提供 `0.dp`，
 *   页面不需要自己判断"现在有没有底栏"。
 *
 * ## 为什么用动态的 CompositionLocal
 *
 * 高度是**量出来**的，不是常量：鲤鱼 tab 的图标比其它 tab 大一号，栏体高度随之变化。
 * 用 `staticCompositionLocalOf` 的话，首次测量完成时会把整个 `NavHost` 子树整棵重组一次；
 * 动态版本只让真正读了它的那几个页面重组，代价小得多。
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/**
 * 「有文本输入框正在获得焦点」的上报通道 —— 键盘弹出时导航层据此收起底栏。
 *
 * ## 为什么必须有这个通道
 *
 * `AndroidManifest` 用的是 `adjustResize`：键盘弹出时**整个窗口变矮**，
 * 于是贴着窗口底部对齐的底栏会被一起顶上去。用户看到的就是
 * **"点一下输入框，底部那块 tab 跟着升起来"**。
 *
 * 所以导航层必须在键盘弹出时把底栏收起来（微信 / 酷安都是这样：输入时底栏不该浮在键盘上）。
 * 而在这个非 edge-to-edge 的窗口里读不到 IME inset（inset 被 `adjustResize` 消费掉了），
 * **唯一可靠的信号就是"输入框拿到了焦点"**。
 *
 * ## ⚠️ 为什么做成 CompositionLocal 而不是逐层传参
 *
 * 曾经的实现是把一个 `onComposerFocusChange` 参数从导航层**穿透**到鲤鱼的输入框。
 * 后果是：**只有鲤鱼那一个输入框会收起底栏**，首页的待办输入框与服务页的搜索框
 *（两者同样在 tab 路由上、同样会弹键盘）都会把底栏顶上去。
 *
 * 参数穿透的问题在于"**每个新输入框都得记得接**"，而漏掉是静默的。
 * 走通道后，**共享输入组件（如 `CampusSearchBar`）在内部接一次就覆盖所有使用方**，
 * 不在共享组件里的输入框也只需加一个 `Modifier.reportTextInputFocus()`。
 *
 * ⚠️ 2026-10：首页那个"待办输入框"已**不存在** —— 录入移进了右下角 FAB 弹出的
 * `AddTodoDialog`，而弹窗在独立窗口里，`adjustResize` 缩的是那个窗口、顶不到 tab 底栏，
 * 所以它**不需要**上报。现在真正露在 tab 页面上的输入框只剩服务页搜索框这一个。
 * 这条通道仍然必要（子页面随时可能再长出输入框），只是别照着"首页有输入框"去数。
 *
 * 值为 `null` 表示不在导航层之下（如 Compose 预览、单元测试）—— 此时
 * [reportTextInputFocus] 是**无副作用的 no-op**，不需要调用方判断。
 *
 * ## ⚠️ 给导航层提供者（`AppNavigation`）的两条要求
 *
 * 1. **状态必须覆盖所有输入框，不能只接鲤鱼那一个。** 初版就是把
 *    `onComposerFocusChange` 参数从导航层穿透到鲤鱼的输入框，于是**只有鲤鱼**会收起底栏，
 *    首页的待办框与服务页的搜索框（同样在 tab 路由上、同样弹键盘）会把底栏一起顶上去。
 * 2. **传给本 CompositionLocal 的 lambda 必须 `remember`。** 它是 `staticCompositionLocalOf`：
 *    读侧不追踪（省开销），代价是**值一变就整棵子树重组**。
 *    每次组合都新建 lambda 会让整个 `NavHost` 每帧重组 —— 那不是性能问题，是卡顿。
 */
val LocalTextInputFocusReporter = staticCompositionLocalOf<((Boolean) -> Unit)?> { null }

/**
 * 把本输入框的焦点状态上报给导航层 —— 键盘弹出时收起底栏。见 [LocalTextInputFocusReporter]。
 *
 * 用法：加在输入框自己的 `modifier` 上
 * ```kotlin
 * OutlinedTextField(value = v, onValueChange = …, modifier = Modifier.reportTextInputFocus())
 * ```
 *
 * ⚠️ **凡是位于 tab 路由上的输入框都要加**（子路由与弹窗不需要：前者底栏本就隐藏，
 * 后者是独立窗口，不会顶到底栏）。漏加的后果不会报错，只是键盘弹出时底栏跟着上升。
 * 守门人：`TabRouteInputFocusTest`（源码扫描 + 手工清单）。
 */
@Composable
fun Modifier.reportTextInputFocus(): Modifier {
    val reporter = LocalTextInputFocusReporter.current ?: return this
    return this.onFocusChanged { reporter(it.isFocused) }
}
