package com.pocket.xiaoli.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 到期类提醒（上课 / 作业 / 考试）的**唯一发送入口**。
 *
 * ## 口径照岱宗盒子 `ReminderNotifier`（2026-11-06 用户：「通知也看一下岱宗咋做的，做的正式一点」）
 *
 * 岱宗那边值得抄的四条，这里逐条落地：
 * 1. **渠道分开**：上课提醒走 HIGH（锁屏可见 + 角标），作业/考试截止走 DEFAULT ——
 *    它们要紧，但不该按分钟把人叫起来。此前三类共用一个 `class_reminder` 渠道，
 *    用户在系统设置里想"只关作业提醒"根本做不到。
 * 2. **一条通知一个图标**：上课是时钟、考试是卷面、作业沿用时钟（没有"作业"专门的图标，
 *    再画一个只会和时钟撞）；大图标统一用应用图标。
 * 3. **`setOnlyAlertOnce(true)`**：同一条提醒被反复重排时只响第一次，后面几轮静默更新 ——
 *    否则每 15 分钟重新排一次的课程会把通知栏刷成一条铃声生产线。
 * 4. **点通知落到对应页面**：走 App 已有的深链 `pocketxiaoli://timetable`，
 *    而不是"打开 App 停在首页让用户自己找"。
 *
 * ## 发不出去怎么办
 *
 * 权限没给、或系统级通知被关 —— [post] 返回 false，调用方降级成应用内提示。
 * 这里**不绕过**用户的开关：通知是附加能力，不是主链路。
 */
internal object ReminderNotifications {

    /**
     * 上课提醒。**沿用旧 id**：渠道 id 是用户订阅关系的标识，
     * 换个名字等于把他"已经关掉"或"已经调成静音"设置作废。
     */
    const val CHANNEL_CLASS = "class_reminder"

    /** 作业 / 考试截止。新增渠道，走默认重要性。 */
    const val CHANNEL_DEADLINE = "deadline_reminder"

    /**
     * 通知着色。系统通知栏不跟随 App 主题（`setColor` 只吃一个 int），
     * 取默认主题的强调色 —— 换主题时这里不跟着变是**刻意**的：
     * 通知是系统界面的一部分，忽蓝忽绿反而更像第三方推送。
     */
    private const val ACCENT = 0xFF4D6BFE.toInt()

    /** 点通知落到课表页。深链 host 与 `DeepLinkDebug` 的解析表一致（`pocketxiaoli://timetable`）。 */
    internal const val DEEP_LINK_TIMETABLE = "pocketxiaoli://timetable"

    /** 幂等：重复调用只覆盖同名渠道，不会泄漏或重置用户的音量/开关。 */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_CLASS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_CLASS, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "上课前几分钟提醒下一节课，锁屏可见"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_DEADLINE) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DEADLINE, "截止提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "作业与考试临近截止时提醒，不反复响铃"
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    /** 系统级与应用级开关都放行才发得出。 */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 发一条提醒。
     *
     * @param id 稳定的通知 id：同一件事重排不叠条，不同事之间不互相覆盖
     * @param deepLink `pocketxiaoli://…` 的页面地址，点通知直接落到那儿
     * @param inboxLines 非空时用 `InboxStyle` 折成多行（多条作业一起截止）
     * @return 是否真的发出去了
     */
    fun post(
        context: Context,
        id: Int,
        channel: String,
        icon: Int,
        title: String,
        text: String,
        deepLink: String,
        inboxLines: List<String> = emptyList(),
        whenMillis: Long? = null,
    ): Boolean {
        if (!canPost(context)) return false
        ensureChannels(context)

        val launch = Intent(context, com.pocket.xiaoli.MainActivity::class.java).apply {
            data = Uri.parse(deepLink)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle(title)
            .setContentText(text)
            .setColor(ACCENT)
            .setCategory(
                if (channel == CHANNEL_CLASS) NotificationCompat.CATEGORY_REMINDER
                else NotificationCompat.CATEGORY_EVENT
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            // 同一条提醒重排时只响第一次：课表每 15 分钟重算一次，全响会变成铃声生产机
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)

        whenMillis?.let { builder.setWhen(it).setShowWhen(true) }

        if (inboxLines.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
            inboxLines.take(5).forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
        return true
    }
}

/**
 * 上课提醒正文：「09:50 · 一教305」。两段都空时返回一句兜底。
 *
 * 抽成纯函数是为了能被钉住 —— 它出现在锁屏上，时间与教室写反了没人会报 bug，
 * 只会在上课前走进错的楼。
 */
internal fun classReminderBody(timeText: String, room: String): String {
    val parts = listOf(timeText.trim(), room.trim()).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "该去上课了" else parts.joinToString(" · ")
}

/** 截止提醒正文：内容 + 截止文案，一段为空就不留多余的分隔符。 */
internal fun deadlineBody(content: String, due: String): String {
    val parts = listOf(content.trim(), due.trim()).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "打开 App 查看详情" else parts.joinToString("  ")
}
