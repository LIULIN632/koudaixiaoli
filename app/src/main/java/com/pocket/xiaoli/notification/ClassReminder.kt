package com.pocket.xiaoli.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.entity.CourseEntity
import com.pocket.xiaoli.data.entity.TimeSlotEntity
import com.pocket.xiaoli.util.parseSemesterStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal const val REMINDER_CHANNEL_ID = "class_reminder"
private const val REQUEST_CODE = 4101

data class ClassReminder(
    val triggerAtMillis: Long,
    val courseName: String,
    val room: String,
    val startAtMillis: Long,
)

object ClassReminderScheduler {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "上课提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "上课前提醒下一节课" }
            )
        }
    }

    fun rescheduleAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reschedule(appContext)
            } catch (_: Exception) {
                // 静默失败,不影响主流程
            }
        }
    }

    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as? PocketXiaoliApplication ?: return
        val container = app.appContainer
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cancelIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ClassReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(cancelIntent)
        if (!container.reminderEnabled.value) return

        val schemeId = container.activeSchemeId.value
        val courses = app.repository.getCoursesByScheme(schemeId).first()
        val slots = app.repository.allTimeSlots.first()
        val next = planNextReminder(
            courses = courses,
            slots = slots,
            semesterStart = container.semesterStart.value,
            reminderMinutes = container.reminderMinutes.value,
            fromMillis = System.currentTimeMillis()
        ) ?: return

        val intent = Intent(context, ClassReminderReceiver::class.java).apply {
            putExtra("name", next.courseName)
            putExtra("room", next.room)
            putExtra("startAt", next.startAtMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAtMillis, pendingIntent)
        }
    }

    /** 纯函数,方便测试:计算下一次应触发的提醒。 */
    internal fun planNextReminder(
        courses: List<CourseEntity>,
        slots: List<TimeSlotEntity>,
        semesterStart: String,
        reminderMinutes: Int,
        fromMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ClassReminder? {
        if (courses.isEmpty() || slots.isEmpty()) return null
        val slotMap = slots.associateBy { it.slot }
        val startDate = parseSemesterStart(semesterStart)
        val from = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        var best: ClassReminder? = null

        for (offset in 0..13) {
            val date = from.toLocalDate().plusDays(offset.toLong())
            val weekNo = if (startDate != null) {
                (ChronoUnit.DAYS.between(startDate, date).toInt() / 7) + 1
            } else {
                1
            }
            if (startDate != null && weekNo < 1) continue

            courses.forEach { course ->
                if (course.dayOfWeek != date.dayOfWeek.value) return@forEach
                if (startDate != null && !isActiveWeek(course, weekNo)) return@forEach
                val slot = slotMap[course.startSlot] ?: return@forEach
                val startTime = parseTime(slot.startTime) ?: return@forEach
                val start = LocalDateTime.of(date, startTime)
                if (!start.isAfter(from)) return@forEach
                val triggerMillis = start
                    .minusMinutes(reminderMinutes.toLong())
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
                if (triggerMillis <= fromMillis) return@forEach
                val current = best
                if (current == null || triggerMillis < current.triggerAtMillis) {
                    best = ClassReminder(
                        triggerAtMillis = triggerMillis,
                        courseName = course.name,
                        room = course.room,
                        startAtMillis = start.atZone(zone).toInstant().toEpochMilli()
                    )
                }
            }
        }
        return best
    }

    internal fun isActiveWeek(course: CourseEntity, week: Int): Boolean {
        val typeOk = when (course.weekType) {
            "odd" -> week % 2 == 1
            "even" -> week % 2 == 0
            else -> true
        }
        if (!typeOk) return false
        if (course.weeks.isBlank()) return true
        val weeks = course.weeks.split(",").mapNotNull { it.trim().toIntOrNull() }
        return weeks.isEmpty() || week in weeks
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value.trim())
    } catch (_: Exception) {
        null
    }
}