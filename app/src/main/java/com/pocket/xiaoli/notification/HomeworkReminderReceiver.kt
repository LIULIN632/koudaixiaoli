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
 * 作业到期提醒的闹钟接收器。写法与 [ExamReminderReceiver] 一致：
 * 权限没给就静默跳过（通知是附加能力），发完**重排下一条** —— 否则只提醒一次。
 */
class HomeworkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val course = intent.getStringExtra("course") ?: return
        val content = intent.getStringExtra("content").orEmpty()
        val due = intent.getStringExtra("due").orEmpty()

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
                    val detail = buildList {
                        if (content.isNotBlank()) add(content)
                        if (due.isNotBlank()) add(due)
                    }.joinToString("  ").ifBlank { "打开 App 查看作业详情" }

                    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                        .setSmallIcon(NotificationVisuals.SCHEDULE)
                        .setLargeIcon(NotificationVisuals.largeIcon(context))
                        .setContentTitle("作业即将截止：$course")
                        .setContentText(detail)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(openIntent)
                        .build()
                    // 用课程+截止文案做 id：同一条作业重排不叠多条，不同作业不互相覆盖
                    NotificationManagerCompat.from(context)
                        .notify(("homework_$course$due").hashCode(), notification)
                }

                HomeworkReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // 忽略，避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}
