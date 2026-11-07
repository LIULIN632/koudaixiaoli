package com.pocket.xiaoli.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 考试提醒的广播接收器。
 *
 * 与 [ClassReminderReceiver] 结构一致，但通知内容是"明天有考试"而不是"即将上课" ——
 * 两者的紧迫感不同，文案不能混用；渠道也不同（考试走**截止类**，不按分钟叫人）。
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
                val detail = ExamReminder(
                    triggerAtMillis = 0L,
                    courseName = name,
                    date = date,
                    time = time,
                    location = location,
                    seat = seat,
                ).describe()

                ReminderNotifications.post(
                    context = context,
                    // 用名字+日期做 id：同一场考试重排不会叠出多条，不同考试也不会互相覆盖
                    id = ("exam_$name$date").hashCode(),
                    channel = ReminderNotifications.CHANNEL_DEADLINE,
                    icon = NotificationVisuals.EXAM,
                    title = "明天考试：$name",
                    text = detail,
                    deepLink = ReminderNotifications.DEEP_LINK_TIMETABLE,
                )

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
