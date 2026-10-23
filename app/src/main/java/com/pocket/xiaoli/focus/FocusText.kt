package com.pocket.xiaoli.focus

/**
 * 番茄钟的**文案与格式化**，集中一处。
 *
 * 为什么要单独一个文件而不是散在页面里：同一批字串有**三个消费者** ——
 * 专注页、常驻通知、结束通知。分散写就会出现"页面叫『短休息』、通知叫『小憩』"。
 *
 * 放 `focus` 包而不是 `ui/focus` 包，是因为前台服务与通知不属于 UI 层：
 * 让 `focus.FocusTimerService` 反向依赖 `ui.focus` 是需要避免的方向。
 * 这里只有纯字串与本机时间格式化，**不引入任何 android import**（可被 JVM 单测覆盖）。
 */

/** 阶段的中文名，用于标题、设置项、统计图例。 */
val FocusPhase.label: String
    get() = when (this) {
        FocusPhase.FOCUS -> "专注"
        FocusPhase.SHORT_BREAK -> "短休息"
        FocusPhase.LONG_BREAK -> "长休息"
    }

/** 正在跑时的进行式说法，用于常驻通知标题。 */
val FocusPhase.runningLabel: String
    get() = when (this) {
        FocusPhase.FOCUS -> "专注中"
        FocusPhase.SHORT_BREAK -> "短休息中"
        FocusPhase.LONG_BREAK -> "长休息中"
    }

/**
 * 把毫秒格式化成 `MM:SS`（满一小时才出现 `H:MM:SS`）。
 *
 * ⚠️ **向上取整**，不是向下。剩余时间是 `deadline - now` 算出来的，
 * 刚点下开始那一刻它就已经比整 25 分钟少几毫秒了；向下取整会让用户看到
 * 一个刚起钟就显示 `24:59` 的怪现象。取整之后 `1ms → 00:01`、`0ms → 00:00`。
 */
fun clockText(ms: Long): String {
    val safe = if (ms < 0L) 0L else ms
    val totalSeconds = (safe + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * 把毫秒说成人话（`1 小时 20 分` / `25 分钟` / `40 秒`），用于统计与今日合计。
 *
 * 与 [clockText] 的区别：这个是**读**的（"今天累计 2 小时 10 分"），
 * 那个是**看**的（倒计时跳秒）。别混用。
 */
fun humanDuration(ms: Long): String {
    val safe = if (ms < 0L) 0L else ms
    val totalMinutes = safe / 60_000L
    if (totalMinutes <= 0L) return "${(safe + 999L) / 1000L} 秒"
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "$hours 小时 $minutes 分"
        hours > 0L -> "$hours 小时"
        else -> "$minutes 分钟"
    }
}

/** 今日 N 个番茄的计法（图表的纵轴刻度等地方共用，避免两处各写一遍单复数）。 */
fun pomodoroCountText(count: Int): String = "$count 个番茄"

/**
 * 「我的」页四格里那枚「专注计时」磁贴的副标题 —— 也就是用户要的"统计时长"。
 *
 * 与专注页的「今天 N 个番茄」刻意用不同口径：那里主角是番茄**个数**（一格卡片有位置），
 * 这一格只有一行副标题，说"多久"比说"几个"更贴直觉，也和旁边「宿舍电费 ¥23.50」
 * 那种"一眼一个数"的写法一致。
 *
 * 今天一次都没跑过就直说「今天还没开始」，不要显示 `0 秒` —— 那看着像坏了。
 */
fun focusTileHint(stat: FocusDayStat): String =
    if (stat.focusMs <= 0L) "今天还没开始" else "今天 ${humanDuration(stat.focusMs)}"
