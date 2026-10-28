package com.pocket.xiaoli.util

import java.time.Instant
import java.time.ZoneId

/**
 * 「刚X / n 分钟前X / n 小时前X / n 天前X / 日期 X」—— 相对时间说人话的**唯一一份**实现。
 *
 * ## 为什么不在各处各写一遍
 *
 * 修课情况快照要说"更新于 3 天前"，更新检查要说"3 分钟前查过"。改造前这套分档
 * 只长在 `AcademicSnapshot` 里，第二个消费者要么抄一遍（两套口径迟早不一致，
 * 一个是"3 天前"一个变成"3日"没人管得住），要么就只能不说时间。
 *
 * ## 为什么超过 30 天改口报日期
 *
 * "45 天前"用户读不出是哪一回，而"2026-09-12"能直接和别的事对上。
 *
 * @param past 过去某个时刻（epoch millis）；`<= 0` 表示**根本没有这个时刻**，返回空串 ——
 *   缺时间戳时宁可不写这句，也不能编出一个"刚X"或"0 分钟前X"的假精确。
 * @param verb 跟在时长后面的那个动作，如「更新」「查过」。
 */
internal fun relativeSinceLabel(
    past: Long,
    verb: String,
    now: Long = System.currentTimeMillis(),
): String {
    if (past <= 0L) return ""
    val minutes = (now - past) / 60_000
    return when {
        minutes < 1 -> "刚$verb"
        minutes < 60 -> "${minutes} 分钟前$verb"
        minutes < 60 * 24 -> "${minutes / 60} 小时前$verb"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前$verb"
        else -> Instant.ofEpochMilli(past).atZone(ZoneId.systemDefault())
            .toLocalDate().toString() + " $verb"
    }
}
