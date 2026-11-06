package com.pocket.xiaoli.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * 用系统浏览器打开一个外部链接 —— 全 App 只用这一份实现。
 *
 * ## 为什么要收成一个
 *
 * 原本各页各写一份 `context.startActivity(Intent(ACTION_VIEW, uri))`，写法各不相同：
 * 有的带了 `FLAG_ACTIVITY_NEW_TASK`、有的没带；失败时有的弹 Toast、有的静默。
 * 于是同一个动作在不同页面的表现不一致 —— 用户报的「点了没反应」就是这么来的：
 * 没带 flag 又恰好拿到的是 Application/包装过的 Context 时，`startActivity` 会抛
 * `AndroidRuntimeException`，被 `catch` 吞掉，用户看到的就是**什么都没发生**。
 *
 * ## 三条约定
 *
 * 1. **优先用 Activity 打开**（去掉 NEW_TASK），这样浏览器退回来时任务栈是干净的；
 *    拿不到 Activity 才退化成 NEW_TASK。
 * 2. **失败必须让用户知道**：没装浏览器 / 没有能处理 http 的应用时，
 *    把链接复制到剪贴板并提示一句，而不是静默失败。
 * 3. 不在主线程做任何可能慢的解析（Uri.parse 本身是纯字符串解析，可以接受）。
 */
object ExternalLinks {

    /**
     * 打开 [url]。
     *
     * ⚠️ 真正的 `startActivity` 会**被投递到主线程消息队列的下一轮**，不在当前触摸事件的派发过程中执行。
     * 这不是为了"异步"而是为了修一个真实故障：
     *
     * 在点击回调里直接 `startActivity`，系统会立刻弹出「打开方式」选择框并把焦点抢走。
     * 如果用户在这时**取消**，焦点回到本窗口，而 Compose 的 `clickable` 可能仍停在"按下未抬起"的状态
     * （取消动作发生在窗口失焦期间，ACTION_CANCEL 未必送达）—— 之后再点**任何**链接都被当成
     * 上一次手势的延续而吞掉，用户看到的就是"点了没反应，只能杀掉重进"。
     * 先让这一轮事件派发完（指针抬起已被处理），再去拉起外部 Activity，手势状态就是干净的。
     *
     * @return true 表示已排队交给系统（浏览器/其他应用）；false 表示参数不合法，什么都没做。
     *         真正的"打不开"（没有浏览器）会在下一轮以 Toast + 复制链接的形式告诉用户。
     */
    fun open(context: Context, url: String): Boolean {
        if (url.isBlank()) return false
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val activity = context.findActivity()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = activity ?: context

        // 持有 applicationContext：排队的这一轮里 Activity 理论上是活的，但拿 application 更稳。
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            try {
                target.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                copyAndTell(appContext, url)
            } catch (_: Throwable) {
                copyAndTell(appContext, url)
            }
        }
        return true
    }

    /** 复制到剪贴板并提示。剪贴板失败也照样提示，至少让用户知道"没打开"。 */
    private fun copyAndTell(context: Context, url: String): Boolean {
        val copied = runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@runCatching false
            manager.setPrimaryClip(ClipData.newPlainText("link", url))
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (copied) "没有可打开该链接的应用，链接已复制" else "没有可打开该链接的应用",
            Toast.LENGTH_SHORT
        ).show()
        return copied
    }
}

/**
 * 顺着 [ContextWrapper] 往上找到 Activity。
 *
 * Compose 的 `LocalContext.current` 在 `ComponentActivity` 里拿到的是 Activity，
 * 但在 `Dialog` / `Popup` 的窗口里可能是包了一层的 `ContextThemeWrapper`；
 * 那种情况下不加 flag 直接 `startActivity` 会抛异常（见类注释）。
 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
