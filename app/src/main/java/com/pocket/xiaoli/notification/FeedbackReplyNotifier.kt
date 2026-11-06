package com.pocket.xiaoli.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocket.xiaoli.MainActivity
import com.pocket.xiaoli.R
import com.pocket.xiaoli.data.feedback.FeedbackReplyNotice
import com.pocket.xiaoli.data.feedback.FeedbackStore

/**
 * 「你的反馈有回复了」这条通知。
 *
 * ## 为什么值得单独一条
 *
 * 反馈是**单向**提交的：用户写完就走，不会天天回来看有没有人回复。上一版的回复只在
 * 「我的 → 关于 → 意见反馈」里可见，等于**只有主动回来的人才知道有答复**。
 * 这条通知就是把"人回你了"送出去。
 *
 * ## 只对本人有意义
 *
 * 通知里带的是**他自己那条工单**的回复正文（截 120 字），不含别人的内容；
 * 工单号不出现在通知里。同一句话只敲一次门，记账在 [FeedbackStore.notifiedReplies]。
 */
internal object FeedbackReplyNotifier {

    /** 固定 id：多条回复合并成一条，不在通知栏堆成一列。 */
    const val NOTIFICATION_ID = 5201

    const val CHANNEL_ID = "feedback_reply"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "反馈回复",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "你提交的意见反馈有了回复"
            }
        )
    }

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 发一条。调用方负责去重（见 [FeedbackReplyNotice.newest]）。
     *
     * @param ticket 工单号，仅用于日志/将来做深链参数，不进通知正文
     * @param text 回复正文
     */
    fun post(context: Context, ticket: String, text: String) {
        if (!canPost(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TICKET, ticket)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)

        val body = FeedbackReplyNotice.preview(text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationVisuals.REPLY)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle("你的反馈有回复了")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 点开通知后，主界面据此跳到「关于 → 意见反馈」（目前只用作埋点/后续深链参数）。 */
    const val EXTRA_TICKET = "com.pocket.xiaoli.extra_feedback_ticket"

    /** 通知里的品牌小图，给测试用（保证没退回系统图标）。 */
    const val SMALL_ICON_RES = R.drawable.ic_stat_reply
}
