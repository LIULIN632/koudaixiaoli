package com.pocket.xiaoli.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocket.xiaoli.PocketXiaoliApplication
import com.pocket.xiaoli.data.planNextHomeworkReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 作业到期提醒。
 *
 * ## 与考试提醒的关系
 *
 * 同一个通知渠道（[REMINDER_CHANNEL_ID]）、同一套精确闹钟写法，但**requestCode 独立** ——
 * 同一个 PendingIntent 只能挂一个闹钟，共用会把考试提醒顶掉（[EXAM_REMINDER_REQUEST_CODE]
 * 那条注释里记过的坑）。
 *
 * ## 提前多久
 *
 * 截止前 24 小时；排期时已不足 24 小时则退到前 2 小时。规则在
 * [planNextHomeworkReminder]（纯函数，单测钉），这里只负责挂闹钟。
 * 与考试提醒共用「课程提醒」总开关：用户关掉就是不想被打扰。
 */
internal const val HOMEWORK_REMINDER_REQUEST_CODE = 4203

object HomeworkReminderScheduler {

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
            HOMEWORK_REMINDER_REQUEST_CODE,
            Intent(context, HomeworkReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(cancel)

        if (!container.reminderEnabled.value) return

        val schemeId = container.activeSchemeId.value
        val homeworks = app.repository.getHomeworkByScheme(schemeId).first()
        val next = planNextHomeworkReminder(
            homeworks = homeworks,
            fromMillis = System.currentTimeMillis(),
        ) ?: return

        val intent = Intent(context, HomeworkReminderReceiver::class.java).apply {
            putExtra("course", next.courseName)
            putExtra("content", next.content)
            putExtra("due", next.dueText)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            HOMEWORK_REMINDER_REQUEST_CODE,
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
}
