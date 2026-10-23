package com.pocket.xiaoli.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 「快考试了」的阈值（天）。
 *
 * ## 为什么是一个**共享常量**，而不是各自写个字面量
 *
 * 首页考试卡片在 `<= 3 天` 时把倒计时染成 warning 色，主动气泡也会说「X 还有 N 天」。
 * 两处如果各写一个数字，迟早会漂移成「卡片红了但气泡不说」或反过来 ——
 * 用户对不上的时候不会去怀疑代码，只会觉得这个 App 在乱讲。
 * 参考实现（xjtu-toolbox）把这条写成注释记在 `ProactiveBubble.kt` 上，
 * 本项目落成一个常量，并让两处都引用它。
 *
 * ⚠️ 这个值是**先验值，无数据支撑**：3 天是从「参考实现的行为」搬过来的。
 * 要改它，改这里一处即可（首页卡片与气泡同时变）。
 */
const val EXAM_SOON_DAYS = 3

/**
 * 把一条考试日期解析成 [LocalDate]。
 *
 * 支持两种写法：
 * - ISO（`2026-01-15`）—— 导入解析器统一过的格式；
 * - 中文（`1月15日`）—— 通知原文里常见，且**往往省略年份**。
 *   缺年份时补今年；若补出来的日期已过去半年以上，按**明年**算
 *   （学期末的通知常跨年：12 月发的 1 月考试）。
 *
 * 解析不出时返回 null（界面就不显示倒计时，而不是显示一个错的）。
 *
 * ⚠️ 本文件与 `notification/ExamReminder.kt` 的 `parseExamDate` 是**两份**：
 * 那份服务于闹钟排期（要精确到"前一天 20:00"），这份服务于展示与气泡。
 * 合并它们要动提醒链路，收益不抵回归成本 —— 但**展示侧的两个人（卡片、气泡）
 * 共用这一份**，那才是必须一致的一对。
 */
fun parseExamDate(raw: String, today: LocalDate = LocalDate.now()): LocalDate? {
    if (raw.isBlank()) return null
    return try {
        LocalDate.parse(raw)
    } catch (_: Exception) {
        val m = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(raw) ?: return null
        val month = m.groupValues[1].toIntOrNull() ?: return null
        val day = m.groupValues[2].toIntOrNull() ?: return null
        try {
            var d = LocalDate.of(today.year, month, day)
            if (d.isBefore(today.minusDays(180))) d = d.plusYears(1)
            d
        } catch (_: Exception) {
            null
        }
    }
}

/** 距考试还有几天（今天 = 0，已过去为负）。解析不出返回 null。 */
fun examDaysLeft(raw: String, today: LocalDate = LocalDate.now()): Long? =
    parseExamDate(raw, today)?.let { ChronoUnit.DAYS.between(today, it) }

/**
 * 倒计时文案。
 *
 * 原文案在 `ui/home/TodoRows.kt`（`examCountdown`），这里只做搬家：
 * 措辞一字未改，否则首页快照会红，而那是"观感有意变更"才该发生的事。
 */
fun examCountdownText(daysLeft: Long): String = when {
    daysLeft == 0L -> "今天"
    daysLeft == 1L -> "明天"
    daysLeft > 1L -> "还有 $daysLeft 天"
    daysLeft == -1L -> "已过去 1 天"
    else -> "已过去 ${-daysLeft} 天"
}
