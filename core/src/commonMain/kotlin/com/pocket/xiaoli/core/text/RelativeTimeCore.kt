package com.pocket.xiaoli.core.text

/**
 * 相对时间说人话：`刚更新` / `3 分钟前更新` / `2 小时前更新` / `5 天前更新` / `2026-09-12 更新`。
 *
 * ## 为什么要抽到共享层
 *
 * 这个分档在 Windows 端和 Android 端各写过一遍，结果**口径不同**：
 * Android 超过 30 天才改说日期，桌面超过 7 天就说日期，而且桌面不带动词、日期格式也不同
 *（`MM-dd HH:mm`）。同一个"上次同步时间"在两个端显示成两种样子，用户没法对时间。
 *
 * ## 为什么超过 30 天改口报日期
 *
 * "45 天前"用户读不出是哪一回，而"2026-09-12"能直接和别的事对上。
 *
 * @param pastMillis 过去某个时刻；`<= 0` 表示**根本没有这个时刻**，返回空串 ——
 *   缺时间戳时宁可不写这句，也不能编出"刚刚"或"0 分钟前"的假精确
 * @param nowMillis 当前时刻（测试里钉死，避免依赖系统时钟）
 * @param verb 跟在时长后面的动作，如「更新」「查过」；可以为空（此时不补后缀）
 * @param formatDate 超过 [cutoffDays] 天时把时间戳格式化成日期，由平台负责时区转换
 * @param cutoffDays 多少天之后改说日期，默认 30（与原 Android 口径一致）
 */
fun relativeSinceLabel(
    pastMillis: Long,
    nowMillis: Long,
    verb: String = "",
    formatDate: (Long) -> String,
    cutoffDays: Int = 30,
): String {
    if (pastMillis <= 0L) return ""
    val minutes = (nowMillis - pastMillis) / 60_000L
    return when {
        minutes < 1L -> "刚$verb"
        minutes < 60L -> "${minutes} 分钟前$verb"
        minutes < 60L * 24L -> "${minutes / 60L} 小时前$verb"
        minutes < 60L * 24L * cutoffDays -> "${minutes / (60L * 24L)} 天前$verb"
        // 动词可能是空串（只要"3 分钟前"），拼完要 trim，否则日期那档会留个尾空格
        else -> (formatDate(pastMillis) + " " + verb).trim()
    }
}
