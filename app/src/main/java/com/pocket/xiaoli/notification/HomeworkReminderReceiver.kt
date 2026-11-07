package com.pocket.xiaoli.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 作业到期提醒的闹钟接收器。写法与 [ExamReminderReceiver] 一致：
 * 通知本体走 [ReminderNotifications]（**截止类渠道**，不是上课提醒那条），
 * 发完**重排下一条** —— 否则只提醒一次。
 */
class HomeworkReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val course = intent.getStringExtra("course") ?: return
        val content = intent.getStringExtra("content").orEmpty()
        val due = intent.getStringExtra("due").orEmpty()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderNotifications.post(
                    context = context,
                    // 用课程+截止文案做 id：同一条作业重排不叠多条，不同作业不互相覆盖
                    id = ("homework_$course$due").hashCode(),
                    channel = ReminderNotifications.CHANNEL_DEADLINE,
                    icon = NotificationVisuals.SCHEDULE,
                    title = "作业即将截止：$course",
                    text = deadlineBody(content, due),
                    deepLink = ReminderNotifications.DEEP_LINK_TIMETABLE,
                )
                HomeworkReminderScheduler.reschedule(context)
            } catch (_: Exception) {
                // 忽略，避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}
