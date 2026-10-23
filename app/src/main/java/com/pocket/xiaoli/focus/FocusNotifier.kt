package com.pocket.xiaoli.focus

import android.Manifest
import android.app.Notification
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
import com.pocket.xiaoli.MainActivity
import com.pocket.xiaoli.util.DeepLinkDebug

/**
 * 番茄钟的**两条通知渠道**，职责刻意分开：
 *
 * | 渠道 | 重要级 | 出声 | 用途 |
 * |---|---|---|---|
 * | [CHANNEL_TIMER] `focus_timer` | LOW | 否 | 进行中的常驻条：带进度、不可划掉、只在开始时出现一次 |
 * | [CHANNEL_RESULT] `focus_result` | HIGH | 是 | 一个番茄 / 一次休息**结束时响一次** |
 *
 * ## 为什么必须分两条
 *
 * Android 8+ 之后，"响不响"是**渠道**的属性，不是单条通知的属性。合成一条的表现是：
 * 要么常驻条每隔一秒顶一下横幅（吵且费电），要么结束那一刻不响（用户根本不知道到点了）。
 * 分渠道之后，常驻条可以永远静音地更新进度，而结束条响一次就走。
 *
 * ## 常驻条的倒计时交给系统画
 *
 * [buildTimerNotification] 用的是 `setUsesChronometer(true)` + `setChronometerCountDown(true)`，
 * 并把 `when` 设成**未来的绝对时刻**（`现在 + 剩余`）。这样每秒跳秒由 SystemUI 完成，
 * **应用侧一次通知重发都不需要**——比"每秒 notify 一次刷进度条"省一个数量级的电。
 * 代价是它的显示依赖系统墙钟：用户手工改时间会让这条通知的字面数变歪
 *（真正到点由闹钟驱动，不受影响，所以最坏只是字幕错几秒）。
 *
 * 暂停时 `when` 失去了意义（剩余不再流逝），所以暂停态关掉计时器、退化成静态文字。
 */
object FocusNotifier {

    /** 进行中的常驻条。无声。 */
    const val CHANNEL_TIMER = "focus_timer"

    /** 段结束提醒。响一次。 */
    const val CHANNEL_RESULT = "focus_result"

    /** 常驻条的通知 id，同时也是前台服务 `startForeground` 用的 id。 */
    const val ID_TIMER = 4201

    /** 结束提醒的通知 id。 */
    const val ID_RESULT = 4202

    private const val REQUEST_CODE_OPEN = 4203

    /** 与 `notification/ClassReminderScheduler` 的 requestCode 段（4101/4102）错开，避免撞车。 */
    const val REQUEST_CODE_ALARM = 4210

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_TIMER) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_TIMER, "专注计时", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "番茄钟进行中的常驻提示，不会响"
                    setShowBadge(false)
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_RESULT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "专注提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "一个番茄或一次休息结束时提醒你"
                }
            )
        }
    }

    /**
     * 现在到底能不能发通知。
     *
     * 两层都要判：API 33+ 是**运行时权限**（用户可能拒绝），
     * 而 `areNotificationsEnabled()` 覆盖的是"用户在系统设置里把本应用的通知整个关了"
     * 与"单条渠道被关了"这两种权限之外的情况。只看权限会在这两种情形下
     * 以为发出去了，实际什么都没显示 —— 而番茄钟的全部后台价值就靠这条通知。
     */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 拼出进行中的常驻条。
     *
     * 返回 Notification 而不是直接发出去，是因为前台服务的 `startForeground`
     * 必须拿到**对象本身**；另外这不是"发一条通知"，而是常驻条的一次更新。
     */
    fun buildTimerNotification(
        context: Context,
        runtime: FocusRuntime,
        remainingMs: Long,
        taskTitle: String? = null,
    ): Notification {
        val paused = runtime.isPaused
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openIntent(context))
            .setOngoing(true)
            .setSilent(true)
            // 没有这一句，每次更新都会重新亮屏 / 重入通知栏动画
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (paused) {
            builder.setContentTitle("${runtime.phase.runningLabel} · 已暂停")
            builder.setContentText("还剩 ${clockText(remainingMs)}")
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        } else {
            builder.setContentTitle(runtime.phase.runningLabel)
            builder.setContentText(taskTitle?.takeIf { it.isNotBlank() } ?: "第 ${runtime.roundIndex} 轮")
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
            builder.setShowWhen(true)
            // 系统据此每秒自己推进倒计时；应用侧不必再重发
            builder.setWhen(System.currentTimeMillis() + remainingMs)
        }
        return builder.build()
    }

    /** 更新（或首次发出）进行中的常驻条。权限不足时静默跳过。 */
    fun notifyTimer(
        context: Context,
        runtime: FocusRuntime,
        remainingMs: Long,
        taskTitle: String? = null,
    ) {
        if (!canPost(context)) return
        ensureChannels(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ID_TIMER, buildTimerNotification(context, runtime, remainingMs, taskTitle))
        }
    }

    /**
     * 撤掉常驻条。
     *
     * ⚠️ 正常路径上**不需要**调它：前台服务 `stopForeground(STOP_FOREGROUND_REMOVE)` 会一并撤掉。
     * 它存在是为了兜底 —— 服务没起来（后台启动被拒）而通知还在 / 用户主动放弃这一段时，
     * 得有人负责把这条"永远走不完的专注中"从通知栏拿掉。
     */
    fun cancelTimer(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_TIMER) }
    }

    /**
     * 段结束提醒：响一次。
     *
     * [text] 由调用方给（它才知道"下一个是休息还是专注""今天第几个番茄"）。
     */
    fun notifyResult(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_RESULT, notification) }
    }

    /**
     * 点通知回到专注页。
     *
     * ⚠️ 这里**必须**走深链 `pocketxiaoli://focus`，不能用 `Intent(context, MainActivity::class.java)`：
     * 后者只会把用户丢在首页 —— 而点"专注中"这条通知的人，要看的正是那根倒计时。
     *
     * 末尾追加一个单调递增的 `?n=`：`AppNavigation` 里消费深链的 `LaunchedEffect` 以
     * **整个字符串**为 key，两次完全相同的深链只有第一次会触发。带上每次都变的 n，
     * 用户从通知栏反复点进来才每次都能落到专注页。
     */
    private fun openIntent(context: Context): PendingIntent {
        val uri = Uri.parse("${DeepLinkDebug.SCHEME}://focus")
            .buildUpon()
            .appendQueryParameter("n", System.currentTimeMillis().toString())
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
