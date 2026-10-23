package com.pocket.xiaoli.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocket.xiaoli.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 考试提醒的广播接收器。
 *
 * 与 [ClassReminderReceiver] 结构一致，但通知内容是"明天有考试"而不是"即将上课" ——
 * 两者的紧迫感不同，文案不能混用。
 *
 * 触发后**重新排下一次考试**（同上课提醒的做法）：AlarmManager 只挂一次性的闹钟，
 * 不在触发后重排的话，第二场考试就不会提醒了。
 */
class ExamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: return
        val date = intent.getStringExtra("date").orEmpty()
        val time = intent.getStringExtra("time").orEmpty()
        val location = intent.getStringExtra("location").orEmpty()
        val seat = intent.getStringExtra("seat").orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClassReminderScheduler.ensureChannel(context)
                val granted = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    val openIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val detail = ExamReminder(
                        triggerAtMillis = 0L,
                        courseName = name,
                        date = date,
                        time = time,
                        location = location,
                        seat = seat,
                    ).describe()

                    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("明天考试：$name")
                        .setContentText(detail)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(openIntent)
                        .build()
                    // 用名字+日期做 id：同一场考试重排不会叠出多条，
                    // 不同考试也不会互相覆盖
                    NotificationManagerCompat.from(context)
                        .notify(("exam_$name$date").hashCode(), notification)
                }

                // 关键：重排下一场，否则只会提醒一次
                ExamReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // 忽略，避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}
