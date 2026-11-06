package com.pocket.xiaoli.desktop.logic

import com.pocket.xiaoli.core.exam.ExamCountdown
import com.pocket.xiaoli.core.exam.ExamNoticeParser
import com.pocket.xiaoli.core.exam.ExamSchedule
import com.pocket.xiaoli.desktop.data.Exam
import java.time.LocalDate

/**
 * 一条待确认的考试（解析结果 + 界面勾选态 + 是否与已有重复）。
 *
 * `selected` 放在数据里而不是界面里：预览列表要滚，勾选态不能随重组丢掉。
 */
data class PendingExam(
    val schedule: ExamSchedule,
    val selected: Boolean = true,
    val duplicate: Boolean = false,
)

/** 解析 + 查重结果。 */
data class ExamNoticeParse(
    val pending: List<PendingExam>,
    /** 扫描过的候选行数（用来告诉用户"读了 N 行、认出 M 条"） */
    val scanned: Int,
)

/** 同一场考试的判据：课程 + 日期 + 时间 —— 与手机端 `ExamScheduleViewModel` 用的同一个键。 */
internal fun examKey(courseName: String, date: String, time: String) = "$courseName|$date|$time"

/**
 * 粘贴的考试通知 → 待确认列表。
 *
 * 解析器在共享核心（`core/exam/ExamNoticeParser`），两端同一份；这里只加桌面自己的
 * 「与已有考试比对、重复项默认不勾选」这一层。
 *
 * 为什么必须**先预览再确认**：通知格式千差万别，规则解析无法保证 100% 准确。
 * 直接落库的话，一条解析错的考试会变成**错误的提醒** —— 用户可能因此走错考场，
 * 这比"没有提醒"严重得多。
 */
fun parseExamNotice(text: String, existing: List<Exam>): ExamNoticeParse {
    val result = ExamNoticeParser.parse(text)
    val existingKeys = existing.map { examKey(it.courseName, it.date, it.schedule.time) }.toSet()
    val pending = result.exams.map { schedule ->
        val duplicate = examKey(schedule.courseName, schedule.date, schedule.time) in existingKeys
        PendingExam(schedule = schedule, selected = !duplicate, duplicate = duplicate)
    }
    return ExamNoticeParse(pending = pending, scanned = result.scanned)
}

/**
 * 展示顺序：未完成的在前，按开考日由近到远；解析不出日期的排最后。
 *
 * 不猜日期：通知里省略年份时 [ExamCountdown.daysLeft] 会按「半年阈值」补年，
 * 补不出来的（"待定"/"见通知"）返回 null —— 那种考试照样要显示，只是没有倒计时。
 */
fun sortedExams(exams: List<Exam>, today: LocalDate = LocalDate.now()): List<Exam> =
    exams.sortedWith(
        compareBy<Exam> { it.completed }
            .thenBy { ExamCountdown.daysLeft(it.date, today) ?: Long.MAX_VALUE }
            .thenBy { it.courseName },
    )
