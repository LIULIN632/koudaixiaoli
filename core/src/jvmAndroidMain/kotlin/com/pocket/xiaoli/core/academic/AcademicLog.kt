package com.pocket.xiaoli.core.academic

/**
 * 共享解析器的日志出口。
 *
 * 解析代码原先直接写 `android.util.Log`，搬进共享层后必须去掉 Android 依赖；
 * 这里留一个可注入的 sink：Android 在 Application 里转发到 Logcat，桌面默认静默。
 */
object AcademicLog {

    var sink: (level: Char, tag: String, message: String, error: Throwable?) -> Unit =
        { _, _, _, _ -> }

    fun d(tag: String, message: String) = sink('d', tag, message, null)

    fun w(tag: String, message: String) = sink('w', tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null) = sink('e', tag, message, error)
}
