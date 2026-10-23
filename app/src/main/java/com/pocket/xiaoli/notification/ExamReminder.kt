package com.pocket.xiaoli.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.ExamEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 考试提醒。
 *
 * ## 与上课提醒（[ClassReminderScheduler]）的关系
 *
 * 复用同一个通知渠道（`REMINDER_CHANNEL_ID`）——用户看到的都是"重要提醒"，
 * 分成两个渠道只会让设置页里多一个无意义的开关。但**调度独立**：
 * 用不同的 requestCode，因为同一个 PendingIntent 只能挂一个闹钟，
 * 共用会互相覆盖（上课提醒一重排就把考试提醒顶掉）。
 *
 * ## 提前多久
 *
 * 课前提醒是"前 N 分钟"（用户可配），考试提醒是**前一天晚上 20:00**。
 * 理由不同：考试要准备（证件、地点、时间），提前几分钟没用；
 * 而提前太多天又会被遗忘。
 */
internal const val EXAM_REMINDER_REQUEST_CODE = 4202

data class ExamReminder(
    val triggerAtMillis: Long,
    val courseName: String,
    val date: String,
    val time: String,
    val location: String,
    val seat: String,
)

object ExamReminderScheduler {

    /** 提前一天提醒；固定 20:00，用户此时通常有空看手机。 */
    private const val REMIND_HOUR = 20
    private const val REMIND_MINUTE = 0

    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reschedule(appContext)
            } catch (_: Exception) {
                // 静默失败：提醒是附加能力，不该影响主流程
            }
        }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as? PocketXiaoliApplication ?: return
        val container = app.appContainer
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val cancel = PendingIntent.getBroadcast(
            context,
            EXAM_REMINDER_REQUEST_CODE,
            Intent(context, ExamReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(cancel)

        // 与上课提醒共用一个开关：用户关掉「课程提醒」就是不想被打扰，
        // 此时单独冒出考试提醒会显得莫名其妙
        if (!container.reminderEnabled.value) return

        val schemeId = container.activeSchemeId.value
        val exams = app.repository.getExamsByScheme(schemeId).first()
        val next = planNextExamReminder(
            exams = exams,
            fromMillis = System.currentTimeMillis(),
        ) ?: return

        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            putExtra("name", next.courseName)
            putExtra("date", next.date)
            putExtra("time", next.time)
            putExtra("location", next.location)
            putExtra("seat", next.seat)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            EXAM_REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
        }
    }

    /**
     * 纯函数：算出下一次该提醒的考试。便于单测。
     *
     * ## 处理要点
     *
     * 1. **跳过已完成的考试**
     * 2. **日期解析失败就跳过**（`1月15日` 这类无年份写法按今年补，若已过去则算明年 ——
     *    与界面倒计时 `examCountdown` 保持同一口径，否则会出现
     *    「界面显示还有 3 天、却不提醒」这种自相矛盾）
     * 3. 提醒时刻若已过（比如今天就是考试日且已过 20:00），顺延到下一个可提醒的考试 ——
     *    而不是硬塞一个过去的闹钟（那样会立刻弹一条过时通知）
     */
    internal fun planNextExamReminder(
        exams: List<ExamEntity>,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExamReminder? {
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        var best: ExamReminder? = null

        exams.filterNot { it.completed }.forEach { exam ->
            val date = parseExamDate(exam.date, from.toLocalDate()) ?: return@forEach

            // 提醒时刻 = 考试前一天 20:00
            val trigger = LocalDateTime.of(date.minusDays(1), LocalTime.of(REMIND_HOUR, REMIND_MINUTE))
            if (!trigger.isAfter(from)) return@forEach

            val millis = trigger.atZone(zone).toInstant().toEpochMilli()
            val current = best
            if (current == null || millis < current.triggerAtMillis) {
                best = ExamReminder(
                    triggerAtMillis = millis,
                    courseName = exam.courseName,
                    date = exam.date,
                    time = exam.time,
                    location = exam.location,
                    seat = exam.seat,
                )
            }
        }
        return best
    }

    /**
     * 解析考试日期。支持 ISO（`2026-01-15`）与中文（`1月15日`、`2026年1月15日`）。
     *
     * 无年份的中文写法按 [today] 补；若已过去半年以上则算明年
     * （与 `examCountdown` 同一规则，避免两处口径不一致）。
     */
    internal fun parseExamDate(raw: String, today: LocalDate): LocalDate? {
        val s = raw.trim()
        if (s.isBlank()) return null

        try {
            return LocalDate.parse(s)
        } catch (_: Exception) {
            // 继续尝试中文写法
        }

        val cn = Regex("""(\d{4})?\s*年?\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日?""").find(s) ?: return null
        val year = cn.groupValues[1].toIntOrNull()
        val month = cn.groupValues[2].toIntOrNull() ?: return null
        val day = cn.groupValues[3].toIntOrNull() ?: return null
        return try {
            if (year != null) {
                LocalDate.of(year, month, day)
            } else {
                var d = LocalDate.of(today.year, month, day)
                if (d.isBefore(today.minusDays(180))) d = d.plusYears(1)
                d
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** 通知正文里用的日期时间格式，便于复用与测试。 */
internal fun ExamReminder.describe(): String {
    val parts = buildList {
        if (date.isNotBlank()) add(date)
        if (time.isNotBlank()) add(time)
        if (location.isNotBlank()) add(location)
        if (seat.isNotBlank()) add("座位 $seat")
    }
    return if (parts.isEmpty()) "时间地点见通知" else parts.joinToString("  ")
}
