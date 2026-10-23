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
import com.pocket.xiaoli.focus.FocusController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: return
        val room = intent.getStringExtra("room").orEmpty()
        val startAt = intent.getLongExtra("startAt", 0L)
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
                    val timeText = if (startAt > 0) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startAt))
                    } else ""
                    val text = buildString {
                        if (timeText.isNotBlank()) append(timeText)
                        if (room.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(room)
                        }
                    }
                    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("即将上课:${name}")
                        .setContentText(text.ifBlank { "该去上课了" })
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(openIntent)
                        .build()
                    NotificationManagerCompat.from(context).notify(name.hashCode(), notification)
                }
                ClassReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // 忽略,避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ClassReminderScheduler.rescheduleAsync(context)
            // 考试提醒是独立的 alarm（不同 requestCode），重启后必须各自重排，
            // 否则重启一次考试就不再提醒了
            ExamReminderScheduler.rescheduleAsync(context)
            // 番茄钟：ELAPSED_REALTIME 闹钟**跨不过重启**（开机后单调钟从零计），
            // 快照里那个 deadlineElapsedMs 此刻是上一个开机周期的时间轴上的数，
            // 直接比大小会得出一个永远走不完的剩余 ⇒ 必须用同样存下来的墙钟截止点重锚。
            // 这一步要读快照、查库、可能拉起前台服务，所以同样走 goAsync。
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    FocusController.of(context).onBootRestore()
                } catch (_: Exception) {
                    // 忽略,避免影响开机链路
                } finally {
                    pending.finish()
                }
            }
        }
    }
}