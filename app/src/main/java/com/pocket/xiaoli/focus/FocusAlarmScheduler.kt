package com.pocket.xiaoli.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 番茄钟那**一枚**闹钟：段结束时叫醒进程。
 *
 * ## 为什么用 `ELAPSED_REALTIME_WAKEUP`，而不是上课提醒用的 `RTC_WAKEUP`
 *
 * 两者是两种时钟。上课提醒关心的是"钟表走到 8:00"，用户把手机时间调快一小时，
 * 它就该提前开腔 —— 所以用墙钟。番茄钟关心的是"**再跑 25 分钟**"，
 * 与钟表上的数字无关：用户改时间是改不动这 25 分钟的（内核也是这个口径）。
 * 用墙钟的后果很具体：用户在专注中途改了系统时间，闹钟要么立刻炸、要么永远不响。
 *
 * ⚠️ 代价写在这里，别忘：`ELAPSED_REALTIME` 的闹钟**跨不过重启**（开机后单调钟归零，
 * 系统会把这类闹钟全部丢掉）。所以 [BootReceiver][com.pocket.xiaoli.notification.BootReceiver]
 * 里要有一条开机重排；重排所需的信息来自快照里的 `deadlineWallClockMs`（见 `FocusEngine.rebase`）。
 *
 * ## 精确 vs 不精确
 *
 * 与 `notification/ClassReminder.kt` 同一套降级：用户可以在系统设置里关掉
 *「闹钟与提醒」，`canScheduleExactAlarms()` 于是为假，这时只能退到
 * `setAndAllowWhileIdle`（不精确、可被推迟）。**降级路径下到点提醒可能晚几分钟** ——
 * 这一点在专注页对用户明说过，代码里不再假装它精确。
 */
object FocusAlarmScheduler {

    /** 与通知、上课提醒的 requestCode 都错开，否则后注册的会顶掉先注册的 PendingIntent。 */
    private const val REQUEST_CODE = FocusNotifier.REQUEST_CODE_ALARM

    const val ACTION_FOCUS_ALARM = "com.pocket.xiaoli.focus.ALARM"

    /**
     * 排一枚闹钟，到点叫 [FocusAlarmReceiver]。
     *
     * [deadlineElapsedMs] 是**绝对**的 `elapsedRealtime` 时刻，直接喂给系统，
     * 不做 `+ 剩余` 之类的换算 —— 换算是漂移的来源，而内核已经把绝对时刻算好了。
     */
    fun schedule(context: Context, deadlineElapsedMs: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        runCatching {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    deadlineElapsedMs,
                    pending,
                )
            } else {
                // 降级：不精确，但唤醒能力仍在（setAndAllowWhileIdle 才能穿透 Doze）
                manager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    deadlineElapsedMs,
                    pending,
                )
            }
        }
    }

    /** 撤掉。暂停 / 放弃 / 段被跳过时都要撤，否则会有一个野闹钟把已结束的段又推进一格。 */
    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        runCatching {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    /** 是否拿得到精确闹钟的许可（Android 12+ 用户可在「闹钟与提醒」里单独收回）。 */
    fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, flags: Int): PendingIntent? {
        val intent = Intent(context, FocusAlarmReceiver::class.java)
            .setAction(ACTION_FOCUS_ALARM)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
