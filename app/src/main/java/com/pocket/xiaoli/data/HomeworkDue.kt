package com.pocket.xiaoli.data

import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.data.entity.HomeworkEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 作业截止日期的**单一解析与判档入口**。
 *
 * ## 为什么必须有这一份
 *
 * `homework.dueDate` 是自由文本：学习通抓取会给 `2026-11-05` 或 `2026-11-05 23:59`
 * （`LearningPlatformClient.extractDueDate`），手动录入则什么都可能写。此前首页行内
 * 只用 `LocalDate.parse` 判"过期没有"——**带时刻的截止日期一律解析失败**，于是
 * 「今天 23:59 截止」的作业在当天早上既不标红也不算过期。看板配色、到期提醒闹钟、
 * .ics 导出三处都要同一个"截止时刻"，各自解析必然对不上（用户对不上的时候
 * 只会觉得应用在乱讲，见 [ExamCountdown] 的同一条教训）。
 *
 * ## 口径
 *
 * - 只给日期不给时刻时，截止时刻取**当天 23:59**：「11-05 截止」意思是那天结束前，
 *   取 00:00 会让作业在截止日一开始就"已过期"。
 * - 解析不出返回 null：界面退回原文展示、提醒不排、.ics 不写这一条 ——
 *   不编一个时刻出来。
 */
enum class DueStage { OVERDUE, TODAY, SOON, LATER }

internal data class ParsedDue(val at: LocalDateTime, val timed: Boolean)

private val DUE_REGEX = Regex(
    """(\d{4})\s*[-/年]\s*(\d{1,2})\s*[-/月]\s*(\d{1,2})\s*日?\s*(?:T?(\d{1,2}):(\d{2}))?"""
)

internal fun parseHomeworkDueRaw(raw: String, now: LocalDateTime): ParsedDue? {
    val s = raw.trim()
    if (s.isBlank()) return null
    val m = DUE_REGEX.find(s)
    if (m != null) {
        val year = m.groupValues[1].toIntOrNull() ?: return null
        val month = m.groupValues[2].toIntOrNull() ?: return null
        val day = m.groupValues[3].toIntOrNull() ?: return null
        val hour = m.groupValues[4].toIntOrNull()
        val minute = m.groupValues[5].toIntOrNull()
        val timed = hour != null && minute != null
        return try {
            val date = java.time.LocalDate.of(year, month, day)
            val time = if (timed) java.time.LocalTime.of(hour!!, minute!!) else END_OF_DAY
            ParsedDue(LocalDateTime.of(date, time), timed)
        } catch (_: Exception) {
            null
        }
    }
    // 无年份中文写法（「11月5日」）交给考试倒计时那份解析器：补今年 / 跨年规则
    // 与考试保持一致，两处的"今天"必须是同一天
    return ExamCountdown.parseDate(s, now.toLocalDate())
        ?.let { ParsedDue(LocalDateTime.of(it, END_OF_DAY), timed = false) }
}

private val END_OF_DAY = java.time.LocalTime.of(23, 59)

/** 截止时刻；解析不出返回 null。 */
fun parseHomeworkDue(raw: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? =
    parseHomeworkDueRaw(raw, now)?.at

/**
 * 截止档位。`null` = 解析不出（界面按"没有结构化截止"处理）。
 *
 * 「临近」的天数阈值复用 [ExamCountdown.SOON_DAYS]：考试与作业共用一档"该紧张了"，
 * 改一处两边一起变。
 */
fun dueStageOf(raw: String, now: LocalDateTime = LocalDateTime.now()): DueStage? {
    val due = parseHomeworkDue(raw, now) ?: return null
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    return when {
        due.isBefore(now) -> DueStage.OVERDUE
        days == 0L -> DueStage.TODAY
        days <= ExamCountdown.SOON_DAYS -> DueStage.SOON
        else -> DueStage.LATER
    }
}

/**
 * 一组截止日期里**最紧**的那一档；一条都解析不出时返回 null。
 *
 * 课表格子只有一条边框，可一门课常常同时挂着好几条作业 ——
 * 「逾期 3 天」和「两周后截止」并存时必须按最紧的画，否则最该被看见的那条被平均掉了。
 * 枚举本身就是从紧到松排的（OVERDUE → TODAY → SOON → LATER），所以取最小 ordinal 即可。
 */
fun worstDueStage(dates: List<String>, now: LocalDateTime = LocalDateTime.now()): DueStage? =
    dates.mapNotNull { dueStageOf(it, now) }.minByOrNull { it.ordinal }

/**
 * 行内截止文案（整串替换「截止 X」）。
 *
 * 倒计时在前、日期只在"还不急"时出现：今天/明天该做什么比"几号"更要紧，
 * 而两样都写就是一件事说两遍（本项目反复删的那类重复）。
 */
fun dueLabelText(raw: String, now: LocalDateTime = LocalDateTime.now()): String? {
    val parsed = parseHomeworkDueRaw(raw, now) ?: return null
    val due = parsed.at
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    val clock = if (parsed.timed) " %02d:%02d".format(due.hour, due.minute) else ""
    val todayTail = if (parsed.timed) "$clock 截止" else "截止"
    return when {
        due.isBefore(now) -> {
            // 按**天**算过期几天（11-01 截止、11-03 早上 = 过期 2 天，与日历直觉一致）；
            // 只有"今天早些时候刚过截止"才落到小时档
            val overDays = ChronoUnit.DAYS.between(due.toLocalDate(), now.toLocalDate())
            if (overDays <= 0) "已过期 ${ChronoUnit.HOURS.between(due, now)} 小时"
            else "已过期 $overDays 天"
        }
        days == 0L -> "今天$todayTail"
        days == 1L -> "明天$todayTail"
        days <= ExamCountdown.SOON_DAYS -> "还剩 $days 天 · ${monthDay(due, now)} 截止"
        else -> "${monthDay(due, now)} 截止"
    }
}

private fun monthDay(due: LocalDateTime, now: LocalDateTime): String =
    if (due.year == now.year) "%d-%02d".format(due.monthValue, due.dayOfMonth)
    else "%d-%02d-%02d".format(due.year, due.monthValue, due.dayOfMonth)

/**
 * 下一条该提醒的作业。纯函数，便于单测。
 *
 * ## 提前多久
 *
 * 截止前 **24 小时**；若排期时距截止已不足 24 小时（刚录入一条明天早上的作业），
 * 退到**前 2 小时** —— 再晚就是截止之后弹通知，等于骂人。两个时刻都已过去则跳过
 * 这一条，顺延到下一条（与 [com.pocket.xiaoli.notification.ExamReminderScheduler] 同一条规矩）。
 */
data class HomeworkDueReminder(
    val triggerAtMillis: Long,
    val courseName: String,
    val content: String,
    val dueText: String,
)

fun planNextHomeworkReminder(
    homeworks: List<HomeworkEntity>,
    fromMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): HomeworkDueReminder? {
    val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
    var best: HomeworkDueReminder? = null
    homeworks.filterNot { it.completed }.forEach { hw ->
        val due = parseHomeworkDue(hw.dueDate, from) ?: return@forEach
        var trigger = due.minusHours(24)
        if (!trigger.isAfter(from)) trigger = due.minusHours(2)
        if (!trigger.isAfter(from)) return@forEach
        val millis = trigger.atZone(zone).toInstant().toEpochMilli()
        val current = best
        if (current == null || millis < current.triggerAtMillis) {
            best = HomeworkDueReminder(
                triggerAtMillis = millis,
                courseName = hw.courseName,
                content = hw.content,
                dueText = dueLabelText(hw.dueDate, from).orEmpty(),
            )
        }
    }
    return best
}
