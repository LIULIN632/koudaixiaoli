package com.pocket.xiaoli.core.text

/**
 * 会话列表里的「今天 / 昨天 / 前天 / M月d日」—— **划天规则在共享层**。
 *
 * 为什么不是各端自己算：
 * - 手机端（`ui/assistant/AssistantConversationListScreen.formatTime`）一直按「今天/昨天/前天/月日」，
 *   桌面端却用相对时间（`3 小时前`）。同一批会话，两个端读起来不是一回事。
 * - 划天若跟着设备时区走，用户把时区调成 UTC 就会看到「今天」标在昨天的时间上。
 *   本项目只服务国内高校，所以**固定按东八区划天**（`dayOffsetMinutes = 480`），
 *   与手机端原来写死的口径保持一致。
 *
 * 共享层不碰 `java.time`：`` 由各端注入（手机用 `SimpleDateFormat`，桌面用 `LocalDate`）。
 *
 * @param millis       会话时间（epoch millis）；`<= 0` 表示没有时间，返回「刚刚」
 * @param nowMillis    「现在」，由调用方注入 —— 快照测试要能钉死基准时刻
 * @param dayOffsetMinutes 划天用的时区偏移，默认 480（UTC+8）
 * @param formatMonthDay  超过「前天」后怎么显示日期（各端本地化）
 */
public fun conversationDayLabel(
    millis: Long,
    nowMillis: Long,
    dayOffsetMinutes: Int = 480,
    formatMonthDay: (Long) -> String,
): String {
    if (millis <= 0L) return "刚刚"
    val dayMs = 24L * 60 * 60 * 1000
    val offset = dayOffsetMinutes.toLong() * 60 * 1000
    val startOfToday = nowMillis - (nowMillis + offset) % dayMs
    return when {
        millis >= startOfToday -> "今天"
        millis >= startOfToday - dayMs -> "昨天"
        millis >= startOfToday - 2 * dayMs -> "前天"
        else -> formatMonthDay(millis)
    }
}
