package com.pocket.xiaoli.ui.glass

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

/**
 * 「软键盘是否正盖住底部」。
 *
 * ## 为什么不能用"输入框有焦点"来判断（2026-10 用户反馈之后才明白）
 *
 * 底栏显隐原先**只**看"有没有输入框获得焦点"。那是个**代理信号**，与真实问题
 *（键盘是否盖住底部）并不等价：
 *
 * > 用户按返回键或下拉箭头**收起键盘时，输入框仍然保持焦点**（这是 Android 的标准行为）。
 * > 于是"有焦点"一直为真，底栏再也回不来 ——
 * > 用户看到的就是**"点开输入框再推出，底部 tab 不见了"**。
 *
 * 焦点信号仍然有用（它表达"用户正在编辑"，见 `LocalTextInputFocusReporter`），
 * 但它**不能单独**回答"键盘盖住底部了吗"。两个条件都满足时收起底栏才是对的。
 *
 * ## 怎么测才可靠
 *
 * 本 App 的 manifest 是 `adjustResize`：键盘弹出时**窗口变矮**。
 * 所以比较「屏幕高」与「窗口可见区高」的差值即可 —— 前者固定，后者会被键盘挤掉一截：
 *
 * ```
 * 无键盘:  screenHeight - visibleFrame ≈ 状态栏 + 导航栏     （~100~150px）
 * 有键盘:  screenHeight - visibleFrame ≈ 上面那些 + 键盘高   （+700~900px）
 * ```
 *
 * ⚠️ **参照物必须是屏幕高，不能是 root view 高。**
 * `adjustResize` 下 root view 自己也会变矮，拿它当参照等于两边一起缩、差值恒为 0 ——
 * 这正是"在这个窗口里读不到 IME inset"那句结论的由来：`WindowInsets.ime` 确实读不到，
 * 但**窗口可见区**读得到。
 *
 * @param screenHeightPx 屏幕物理高度（`displayMetrics.heightPixels`）
 * @param visibleFrameHeightPx 窗口可见区高度（`View.getWindowVisibleDisplayFrame`）
 */
internal fun isImeVisible(screenHeightPx: Int, visibleFrameHeightPx: Int): Boolean {
    if (screenHeightPx <= 0 || visibleFrameHeightPx <= 0) return false
    val hiddenPx = screenHeightPx - visibleFrameHeightPx
    return hiddenPx > screenHeightPx * IME_VISIBLE_RATIO
}

/**
 * 状态栏 + 导航栏通常不超过屏幕高度的 12%，键盘则远超。
 * 取 15% 是为了两者之间留出余量（分屏、刘海、手势条不同的机型都落在这一侧）。
 */
private const val IME_VISIBLE_RATIO = 0.15f

/**
 * 第二个判据：直接读 IME inset 的底边是否大于 0。
 *
 * ## 为什么现在需要它（2026-10-19 之后）
 *
 * 开了**边到边**（`MainActivity` 的 `enableEdgeToEdge`）以后，窗口不再随键盘变矮 ——
 * 上面那个「屏幕高 − 可见区高」的差值会一直停在状态栏/导航栏那一档，
 * 于是 frame 判据永远说"没有键盘"，底栏就不会收起、键盘直接压住输入区。
 *
 * 反过来，边到边之后 `WindowInsets.ime` **才开始能读到**（没开之前它被系统消费成窗口高度变化）。
 * 所以两个判据取**或**：老窗口靠 frame、边到边靠 inset，谁先变谁说了算，
 * 也就不用为了这条把一个可能的回归留在设备上。
 */
internal fun isImeVisibleByInsets(imeBottomPx: Int): Boolean = imeBottomPx > 0

/**
 * 观察键盘是否盖住底部。判据见 [isImeVisible]（窗口可见区）与 [isImeVisibleByInsets]（IME inset）。
 *
 * frame 判据用 `OnGlobalLayoutListener`：边到边之前 `WindowInsets.ime` 恒为 0，
 * 只有「屏幕高 − 窗口可见区高」能反映键盘；边到边之后由下面的 inset 判据接管。
 */
@Composable
fun rememberImeVisible(): State<Boolean> {
    val view = LocalView.current
    val density = LocalDensity.current
    val frameVisible = remember { mutableStateOf(false) }

    // 直接读 inset：WindowInsets 是快照支持的，读它就会在键盘进出时触发重组。
    // ⚠️ 只有真的负责让位的布局才能读到非零值 —— 根布局已经 `.imePadding()` 过之后，
    // 这里读到的仍然是同一个值（inset 不会因为被消费而变 0，消费只影响子级）。
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            frameVisible.value = isImeVisible(
                screenHeightPx = view.resources.displayMetrics.heightPixels,
                visibleFrameHeightPx = rect.height(),
            )
        }
        observer.addOnGlobalLayoutListener(listener)
        // 首帧不会自己触发一次，先算一次初值（否则刚进页面时状态是错的）
        listener.onGlobalLayout()
        onDispose {
            if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        }
    }

    // 两个判据取「或」，见 isImeVisibleByInsets 的注释。
    // 用 derivedStateOf 而不是在组合里直接赋值：往 State 里写值会触发重组，
    // 在组合过程中写等于自己给自己排下一次重组（能用但不干净，也会让帧率异常）。
    val visible = remember(imeBottomPx) {
        derivedStateOf { frameVisible.value || isImeVisibleByInsets(imeBottomPx) }
    }
    return visible
}
