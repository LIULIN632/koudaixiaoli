package com.pocket.xiaoli.core.exam

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 考试倒计时：把通知里的日期原文变成「还有几天」。
 *
 * 手机端（首页卡片、主动气泡）与桌面端（考试页、托盘提醒）共用这一份。
 * 分开写的后果是「卡片说今天、气泡说还有 1 天」这类对不上，
 * 而用户对不上的时候不会怀疑代码，只会觉得应用在乱讲。
 */
object ExamCountdown {

    /**
     * 「快考试了」的阈值（天）。
     *
     * ⚠️ 这个值是**先验值，无数据支撑**：3 天是从「参考实现的行为」搬过来的。
     * 要改它，改这里一处即可（首页卡片、主动气泡、桌面提醒同时变）。
     */
    const val SOON_DAYS = 3

    /**
     * 是否该按「临近」强调（警示色）。
     *
     * 含今天与明天 —— 「3 天内用警示色」这条口径原本在首页被写成了
     * `文案以「还有」开头 && 数字 ≤ 3`，于是最要紧的**今天/明天反而不强调**。
     * 判据收在这里之后，首页待办区、考试安排页与桌面端只能一起变。
     */
    fun isSoon(daysLeft: Long): Boolean = daysLeft in 0..SOON_DAYS

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
     * ⚠️ 手机端 `notification/ExamReminder.kt` 里还有一份 `parseExamDate`：
     * 那份服务于闹钟排期（要精确到"前一天 20:00"），这份服务于展示与提醒文案。
     */
    fun parseDate(raw: String, today: LocalDate = LocalDate.now()): LocalDate? {
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
    fun daysLeft(raw: String, today: LocalDate = LocalDate.now()): Long? =
        parseDate(raw, today)?.let { ChronoUnit.DAYS.between(today, it) }

    /**
     * 倒计时文案。
     *
     * 措辞与手机端首页 `ui/home/TodoRows.kt` 完全一致（那里有快照测试守着），
     * 桌面端复用同一个字符串，两端不会出现两种说法。
     */
    fun countdownText(daysLeft: Long): String = when {
        daysLeft == 0L -> "今天"
        daysLeft == 1L -> "明天"
        daysLeft > 1L -> "还有 $daysLeft 天"
        daysLeft == -1L -> "已过去 1 天"
        else -> "已过去 ${-daysLeft} 天"
    }
}
