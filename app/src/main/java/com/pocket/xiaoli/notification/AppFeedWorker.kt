package com.pocket.xiaoli.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.data.announcement.AppFeedStore

/**
 * 后台轮询 jobs：拉一次 feed，有必要就发一条本地通知。
 *
 * ## 失败为什么返回 retry 而不是 failure
 *
 * 网络抖动是最常见的失败原因，`retry` 会让 WorkManager 按指数退避重试
 * （见 [AppFeedScheduler.schedule] 里配的 30 秒起步）。只有"开关被关掉"和
 * "拉成功了"才算 success —— 后者包括"没有新东西"，那本来就不该重试。
 */
class AppFeedWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = AppFeedStore(context)
        if (!store.backgroundEnabled()) return Result.success()

        return try {
            AppFeedScheduler.ensureChannel(context)
            val repository = AppFeedRepository(context)
            val state = repository.refresh(force = true)
            // 只有真的读到东西才发通知；网络失败时不要拿一条"检查失败"去打扰用户
            if (state.loaded && state.error == null) {
                AppFeedNotifier.postIfNeeded(context, state, store)
            }
            Result.success()
        } catch (t: Throwable) {
            // 极端情况（OOM / 解码异常）也走重试，别把链路直接判死
            Result.retry()
        }
    }
}
