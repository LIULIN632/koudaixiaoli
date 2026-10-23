package com.pocket.xiaoli.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pocket.xiaoli.ui.glass.rememberImeVisible

/**
 * 底栏该不该显示。**纯函数，可单测** —— 从此这条判定不再只活在 `AppNavigation` 里。
 *
 * ## 抽出来的直接理由：一个真实 bug
 *
 * 2026-10 用户反馈："点开输入框再推出，底部 tab 不见了"。
 * 根因是判定**只看输入框焦点**，而**收起键盘时输入框仍然保持焦点** ——
 * 于是条件一直为真，底栏再也回不来。
 *
 * 它当时写在 `AppNavigation` 的一行表达式里，既没名字也没测试，
 * 所以"焦点不等于键盘"这件事直到用户撞上才被发现。抽到这里之后，
 * `BottomBarVisibilityTest` 把这个场景钉死了。
 *
 * ## 四个条件
 *
 * | 条件 | 含义 | 为什么是必要条件 |
 * |---|---|---|
 * | [!hasOpenSubPage] | 当前没有全屏子页 | 子页自带返回，底栏不该压在它上面 |
 * | [isTabRoute] | 当前是五个 tab 之一 | 非 tab 路由（导入、设置子页…）本就该没有底栏 |
 * | 键盘 **且** 正在编辑 | 键盘真的盖住了底部，并且有输入框在编辑 | 见下 |
 *
 * ⚠️ **"键盘"与"正在编辑"是「与」关系**（收起底栏的条件 = `imeVisible && textInputFocused`）：
 *
 * - 只看 [textInputFocused]：收起键盘后输入框**仍然保持焦点**，底栏再也回不来 —— **就是那个 bug**；
 * - 只看 [imeVisible]：键盘可能来自别的窗口（比如弹窗里的输入框，
 *   `adjustResize` 缩的是那个窗口、不是本窗口），此时不该收底栏。
 *
 * 两个都满足才收 —— 这是唯一同时避开两种误判的写法。
 */
internal fun shouldShowBottomBar(
    hasOpenSubPage: Boolean,
    isTabRoute: Boolean,
    imeVisible: Boolean,
    textInputFocused: Boolean,
): Boolean = !hasOpenSubPage && isTabRoute && !(imeVisible && textInputFocused)

/**
 * 底栏显隐所需的一切：算好的 [visible] + 给子树用的焦点上报通道 [reportFocus]。
 *
 * ## 为什么要打成一个包
 *
 * 两个现实原因，都不是洁癖：
 *
 * 1. **`AppNavigation` 已经贴着 800 行上限**（项目 lint 的一条硬线），
 *    把"读键盘状态 + 持有焦点状态 + 算可见性"摊在那边会把文件推过去；
 * 2. 四个 `Boolean` 参数**按位置传参是危险的** —— 交换任意两个编译器都不会吭声。
 *    打包成对象之后，调用点只有一句 [rememberBottomBarState]，没有位置可言。
 */
@Stable
internal class BottomBarState(
    /** 底栏此刻是否应当显示。 */
    val visible: Boolean,
    /** 交给输入框的上报通道（经 `LocalTextInputFocusReporter` 下发）。 */
    val reportFocus: (Boolean) -> Unit,
)

/**
 * 读键盘状态、持有"正在编辑"状态，算出底栏该不该显示。
 *
 * ⚠️ [BottomBarState.reportFocus] **必须由 `remember` 固定身份**：
 * 它会作为 `staticCompositionLocalOf` 的值下发，而 static 局部量的值是**换了就整棵子树重组**。
 * 每次组合新建一个 lambda 会让整个 `NavHost` 每帧重组 —— 那不是性能问题，是卡顿。
 */
@Composable
internal fun rememberBottomBarState(hasOpenSubPage: Boolean, isTabRoute: Boolean): BottomBarState {
    val imeVisible by rememberImeVisible()
    var textInputFocused by remember { mutableStateOf(false) }
    val reportFocus = remember { { focused: Boolean -> textInputFocused = focused } }
    return BottomBarState(
        visible = shouldShowBottomBar(hasOpenSubPage, isTabRoute, imeVisible, textInputFocused),
        reportFocus = reportFocus,
    )
}
