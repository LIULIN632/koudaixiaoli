package com.pocket.xiaoli.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pocket.xiaoli.data.announcement.AppFeedStore
import java.util.concurrent.TimeUnit

/**
 * 「关着也能知道」那一层：WorkManager 周期拉 feed（L2）。
 *
 * ## 为什么是 15 分钟
 *
 * 原本是 6 小时（照岱宗的 4 小时口径拍的）。2026-10-17 用户实测发版后**完全收不到**
 * 新版本通知：装的是刚发的包，等不到第一条轮询。于是降到 WorkManager 允许的**最小值**
 * 15 分钟 —— feed 只有 2 KB，一次 HTTP GET 的成本远低于"用户以为没有推送"的代价。
 *
 * ## 为什么约束里带电量与存储
 *
 * 参照岱宗的做法：这类任务晚了半小时没人会死，但**在电量红了的时候替用户联网、
 * 替用户写文件**会被骂。`CONNECTED + batteryNotLow + storageNotLow` 是这三者的交集。
 * ⚠️ Doze / 省电模式仍可能把 15 分钟推成一两小时，这是系统调度，不是我们能绕过的
 * （绕过它要前台服务或电池白名单，代价见下）。
 *
 * ## 为什么不用精确闹钟 / 前台服务 / 电池白名单
 *
 * 同样的理由：这是一条"锦上添花"的链路。Doze 让它晚几小时完全可以接受；
 * 反过来，为了它去要电池优化豁免、挂前台服务，代价远大于收益。
 * 真要秒级，那得上厂商推送（见 `docs/更新与公告.md` 选型那一节），不是靠轮询硬扛。
 */
object AppFeedScheduler {

    /** 渠道 id：与班级提醒、专注计时各自独立，用户可以单独关掉「版本与公告」 */
    const val CHANNEL_ID = "app_feed"

    private const val WORK_NAME = "app_feed_poll"

    /** 渠道：默认重要度（不弹横幅、不发声音，只在通知栏待着） */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "版本与公告",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "新版本、维护通知与校园公告"
            }
        )
    }

    /** 排上周期任务（幂等：重复调用只是更新参数，不会排两份） */
    fun schedule(context: Context) {
        if (!AppFeedStore(context).backgroundEnabled()) {
            cancel(context)
            return
        }
        ensureChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<AppFeedWorker>(
            AppFeedRepository_IntervalMinutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** 关掉开关就真的撤掉（不是留个空转的周期任务） */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 用户点了「立即检查」：不排队等着，直接跑一次 */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AppFeedWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * 周期值跟数据层放一起（`AppFeedRepository.WORK_INTERVAL_MINUTES`），
     * 避免「文档写 15 分钟、代码排 6 小时」这种错位。
     */
    private val AppFeedRepository_IntervalMinutes: Long
        get() = com.pocket.xiaoli.data.announcement.AppFeedRepository.WORK_INTERVAL_MINUTES
}
