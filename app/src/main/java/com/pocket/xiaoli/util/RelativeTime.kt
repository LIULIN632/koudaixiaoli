package com.pocket.xiaoli.util

import java.time.Instant
import java.time.ZoneId

/**
 * 「刚X / n 分钟前X / n 小时前X / n 天前X / 日期 X」—— **分档规则在共享层**
 * （`core/text/RelativeTimeCore.kt`），本文件只保留 Android 原有的包路径与签名。
 *
 * 为什么抽走：同样的分档 Windows 端也写了一份，口径还不一样（桌面超过 7 天就报日期、
 * 日期格式也不同）。现在两端共用一套分档，只保留「时区转本地日期」这件平台相关的事在这里。
 *
 * @param past 过去某个时刻（epoch millis）；`<= 0` 表示根本没有这个时刻，返回空串
 * @param verb 跟在时长后面的动作，如「更新」「查过」；可以为空
 */
internal fun relativeSinceLabel(
    past: Long,
    verb: String,
    now: Long = System.currentTimeMillis(),
): String = com.pocket.xiaoli.core.text.relativeSinceLabel(
    pastMillis = past,
    nowMillis = now,
    verb = verb,
    formatDate = { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    },
)
