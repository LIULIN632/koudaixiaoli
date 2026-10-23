package com.pocket.xiaoli.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 页内浮层（"弹窗"）的宿主。
 *
 * ## 为什么不用 `Dialog` / `AlertDialog`
 *
 * 那两个都是**独立的系统窗口**。窗口一旦交给系统，背景就不再归我们管：
 * 有的 ROM（实测反馈来自 ColorOS / OriginOS）会给"带 dim 的弹窗窗口"自动加一层背景模糊，
 * 于是弹窗后面那张页面糊成一片 —— 但用户要的是「原界面清晰可见、弹窗盖在上面」。
 * 窗口级模糊我们既没法关、也没法调，所以只能不进那套机制。
 *
 * 改成页内浮层以后：背景就是页面本身（一个像素都没被动过），
 * 只有我们自己画的一层半透明遮罩把它压暗；模糊与否完全由我们决定。
 *
 * 附带好处：浮层在 `glassSource` 录制层**之外**（录制层只包 `NavHost`，见
 * `ui/navigation/AppNavigation.kt` 里 `Modifier.glassSource(...)` 那一行），
 * 所以浮层里的玻璃卡**可以**采样背后的页面 —— 将来想让弹窗卡片变成真玻璃，路是通的。
 *
 * ## 只支持一层
 *
 * 同时只保留最新的一层：第二层 `show` 会顶掉第一层。这不是省事，
 * 而是浮层没有系统窗口那样的栈语义 —— 真需要"弹窗上再弹窗"的场景在本项目里不存在，
 * 硬做栈只会让返回键行为变得难以推理。
 */
@Stable
class AppOverlayHostState {

    private var entry by mutableStateOf<Entry?>(null)

    internal val current: Entry? get() = entry

    /** 显示一层浮层。[onDismiss] 是**唯一的关闭口径**（返回键、点遮罩都走它）。 */
    fun show(onDismiss: () -> Unit, content: @Composable () -> Unit) {
        entry = Entry(onDismiss, content)
    }

    fun hide() {
        if (entry != null) entry = null
    }
}

internal class Entry(
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
)

/**
 * 浮层宿主的句柄。
 *
 * 用 `staticCompositionLocalOf` 而不是 `compositionLocalOf`：宿主实例在 App 启动时就定了、
 * 永不替换，按静态局部值处理可以省掉一层订阅（每次 `show/hide` 只要重绘浮层那一层）。
 */
val LocalAppOverlayHost = staticCompositionLocalOf<AppOverlayHostState?> { null }

/**
 * 浮层渲染层：放在页面**最上面**（`AppNavigation` 里底栏之后），因此盖得住底栏。
 *
 * 返回键由这里统一接管：浮层存在时它是最内层（最后注册）的处理器，
 * 所以会先于页面自己的返回逻辑生效，并且只调用浮层自己的 [Entry.onDismiss] ——
 * 这样"点遮罩关闭"和"按返回关闭"走的是同一段状态清理。
 */
@Composable
fun AppOverlayLayer(host: AppOverlayHostState) {
    val entry = host.current ?: return
    BackHandler { entry.onDismiss() }
    entry.content()
}

/**
 * 把一个浮层挂到宿主上，离开组合时自动收起。
 *
 * 用法（页面里）：
 * ```
 * if (someState) {
 *     AppOverlayDialog(onDismiss = { someState = false }) { dismiss ->
 *         MyDialogCard(onDismiss = dismiss)
 *     }
 * }
 * ```
 * `someState` 一变回 false，本组合退出 → `onDispose` 收起浮层，不需要手动 `hide()`。
 *
 * 注意 [content] 会拿到终止回调：卡片内部所有"关闭"都应该调它，
 * 而不是自己去改外面那个布尔值 —— 否则浮层会留在宿主里成为一层"看不见但吃得掉点击"的浮尸。
 */
@Composable
fun AppOverlayDialog(
    onDismiss: () -> Unit,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val host = LocalAppOverlayHost.current ?: return
    val dismiss by rememberUpdatedState(onDismiss)
    val body by rememberUpdatedState(content)

    DisposableEffect(host) {
        host.show(onDismiss = { dismiss() }) { body { dismiss() } }
        onDispose { host.hide() }
    }
}
