package com.pocket.xiaoli.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 上课提醒的广播接收器。
 *
 * 通知本体走 [ReminderNotifications]（渠道 / 图标 / 深链 / 去重都在那一处），
 * 这里只管"取参数 → 发一条 → 重排下一节"。
 *
 * 点通知落到**课表页**（深链 `pocketxiaoli://timetable`），不是打开 App 首页 ——
 * 上课前那一眼要看到的是"今天剩下几节、在哪间"，不是首页的待办。
 */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: return
        val room = intent.getStringExtra("room").orEmpty()
        val startAt = intent.getLongExtra("startAt", 0L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timeText = if (startAt > 0) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startAt))
                } else {
                    ""
                }
                ReminderNotifications.post(
                    context = context,
                    id = name.hashCode(),
                    channel = ReminderNotifications.CHANNEL_CLASS,
                    icon = NotificationVisuals.SCHEDULE,
                    title = "即将上课：$name",
                    text = classReminderBody(timeText, room),
                    deepLink = ReminderNotifications.DEEP_LINK_TIMETABLE,
                    whenMillis = startAt.takeIf { it > 0 },
                )
                ClassReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // 忽略,避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}
