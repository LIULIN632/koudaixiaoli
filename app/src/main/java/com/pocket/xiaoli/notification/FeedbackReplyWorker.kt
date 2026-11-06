package com.pocket.xiaoli.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocket.xiaoli.data.feedback.FeedbackRemote
import com.pocket.xiaoli.data.feedback.FeedbackReplyNotice
import com.pocket.xiaoli.data.feedback.FeedbackStore

/**
 * 后台问一次「我提交的那些反馈有没有回音」。
 *
 * ## 为什么能后台问
 *
 * 反馈接口按**工单号**查回复（`GET /v1/feedback/replies?tickets=`），工单号存在本机，
 * 没有账号体系 —— 所以轮询不需要登录，也不会上传任何额外标识。
 * 没有工单的机器直接返回，一次网络都不发。
 *
 * ## 失败的处理
 *
 * 网络问题时返回 retry（WorkManager 按指数退避），但**不把失败告诉用户**：
 * 他并不在等一次"检查回复"，只是我们顺路去看看。真拉不到，页面上仍然是旧回复。
 */
class FeedbackReplyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = FeedbackStore(context)
        val tickets = store.sentIds().toList()
        if (tickets.isEmpty()) return Result.success()

        // 与页面里同一套节流：等回复的人 10 分钟一次，全都回过的人 6 小时一次。
        // 后台这一路**不 force**，否则每 6 小时一次的周期任务会变成无限刷。
        if (!store.shouldFetchReplies(hasPending = store.pending().isNotEmpty())) {
            return Result.success()
        }

        return try {
            val got = FeedbackRemote.replies(context, tickets)
            store.markRepliesFetched()
            if (got.isNotEmpty()) {
                store.cacheReplies(got)
                val fresh = FeedbackReplyNotice.newest(got, store.notifiedReplies())
                if (fresh != null) {
                    val (ticket, reply) = fresh
                    runCatching { FeedbackReplyNotifier.post(context, ticket, reply.text) }
                    store.markReplyNotified(ticket, reply.text)
                }
            }
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
