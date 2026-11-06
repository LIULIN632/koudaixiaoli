package com.pocket.xiaoli.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 「反馈有回复」这条链的后台调度。
 *
 * ## 为什么周期是 6 小时，而不是像更新那样 15 分钟
 *
 * 更新是**时效敏感**的（新版本带修复），而一条反馈回复晚几小时看到无所谓；
 * 反馈本来也没几条。6 小时一次是成本与体验的折中——一个白天能覆盖两三次。
 * 真急着看的人可以在页面上点「检查回复」，那条走 [pollNow] 并绕过节流。
 *
 * ## 为什么冷启动还要额外 poll 一次
 *
 * 周期任务第一次至少要等一个周期。用户刚提交完反馈就切走、第二天打开 App，
 * 如果只靠周期任务，可能要等到下午才响。冷启动补一次单次任务，代价是一次
 * 几 KB 的 GET（且有节流：刚拉过就不会真发请求）。
 */
internal object FeedbackReplyScheduler {

    private const val WORK_NAME = "feedback_reply_poll"
    private const val ONCE_NAME = "feedback_reply_once"
    private const val INTERVAL_HOURS = 6L

    /** 排周期任务（幂等）。没有工单的机器由 worker 自己直接返回。 */
    fun schedule(context: Context) {
        FeedbackReplyNotifier.ensureChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<FeedbackReplyWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** 冷启动补一次（替换式：同一时刻只会有一份排队）。 */
    fun pollNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<FeedbackReplyWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONCE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
