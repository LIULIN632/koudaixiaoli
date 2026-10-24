package com.pocket.xiaoli.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.pocket.xiaoli.network.ApkArtifact
import com.pocket.xiaoli.network.ApkDownloader
import com.pocket.xiaoli.network.ApkInstaller
import com.pocket.xiaoli.network.ApkMirrors
import com.pocket.xiaoli.network.DownloadProgress
import com.pocket.xiaoli.network.DownloadProgressText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在后台把新版本 APK 下下来，下完发一条「点击安装」的通知。
 *
 * ## 为什么不在通知里直接 `startActivity` 安装
 *
 * Android 10+ 禁止后台启动 Activity：Receiver 里拉起系统安装器会被静默拦掉
 * （用户看到的就是"点了没反应"）。所以流程拆成两段：
 * **后台下载 → 再发一条通知 → 用户点它才拉起安装器**，这一步是前台动作，不会被拦。
 *
 * ## 失败换仓
 *
 * 和 App 内「下载并安装」同一套 [ApkMirrors] 候选列表：Gitee 不通自动换 GitHub，
 * 都失败才报错，并把失败原因写进通知（不再只给一句"失败了"）。
 */
class ApkUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val request = AppUpdateRequest.of(
            rawVersion = inputData.getString(KEY_VERSION),
            rawUrl = inputData.getString(KEY_URL),
            rawSha256 = inputData.getString(KEY_SHA),
        )
        if (!request.isValid) return Result.failure()

        AppFeedScheduler.ensureChannel(context)
        if (!AppFeedNotifier.canPost(context)) return Result.failure()

        val directory = ApkInstaller.directory(context).apply { mkdirs() }
        val target = request.targetFile(directory)

        // 上一次已经下好且校验通过 ⇒ 别再重下一遍
        if (ApkArtifact.verify(target, request.sha256) == null) {
            postReady(context, request, target)
            return Result.success()
        }

        var lastError = "下载失败"
        val urls = ApkMirrors.candidates(request.versionName, request.apkUrl)
        for ((index, url) in urls.withIndex()) {
            if (isStopped) return Result.failure()
            if (index > 0) {
                postProgress(context, request, "换 ${ApkMirrors.label(url)} 再试…", percent = null)
                lastProgressPostAt = System.currentTimeMillis()
            }

            val error = withContext(Dispatchers.IO) {
                ApkDownloader().download(url, target) { progress ->
                    postProgressThrottled(context, request, progress)
                }
            }
            if (error == null) {
                val check = ApkArtifact.verify(target, request.sha256)
                if (check == null) {
                    postReady(context, request, target)
                    return Result.success()
                }
                lastError = check
                target.delete()
            } else {
                lastError = error
            }
        }

        postFailure(context, request, lastError)
        return Result.failure()
    }

    // --- 通知 -----------------------------------------------------------------

    /** 上一次发下载进度通知的时刻（MIUI 会对高频通知限流） */
    private var lastProgressPostAt = 0L

    private fun postProgressThrottled(context: Context, request: AppUpdateRequest, progress: DownloadProgress) {
        val now = System.currentTimeMillis()
        // 最多每 500ms 发一条；下载器每 ~250ms 就递一次新快照，被节流掉的这一次
        // 会被下一个快照带着更新的百分比补上，进度不会卡在旧值。100% 必发。
        if (progress.percent != 100 && now - lastProgressPostAt < PROGRESS_NOTIFY_THROTTLE_MS) return
        lastProgressPostAt = now
        postProgress(context, request, DownloadProgressText.label(progress), progress.percent)
    }

    private fun postProgress(context: Context, request: AppUpdateRequest, text: String, percent: Int?) {
        val notification = NotificationCompat.Builder(context, AppFeedScheduler.CHANNEL_ID)
            .setSmallIcon(NotificationVisuals.UPDATE)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle("正在下载 v${request.versionName}")
            .setContentText(text)
            .setProgress(100, percent ?: 0, percent == null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        notifyIfAllowed(context, notification)
    }

    private fun postReady(context: Context, request: AppUpdateRequest, file: java.io.File) {
        if (!ApkInstaller.canInstall(context)) {
            postPermissionNeeded(context, request)
            return
        }
        val install = PendingIntent.getActivity(
            context,
            request.versionName.hashCode(),
            ApkInstaller.installIntent(context, file),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, AppFeedScheduler.CHANNEL_ID)
            .setSmallIcon(NotificationVisuals.DONE)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle("v${request.versionName} 已下载")
            .setContentText("点一下完成安装")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(install)
            .addAction(0, "安装", install)
            .build()
        notifyIfAllowed(context, notification)
    }

    private fun postFailure(context: Context, request: AppUpdateRequest, reason: String) {
        val retry = AppUpdateReceiver.pending(context, request.versionName, request.apkUrl, request.sha256)
        val notification = NotificationCompat.Builder(context, AppFeedScheduler.CHANNEL_ID)
            .setSmallIcon(NotificationVisuals.ERROR)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle("v${request.versionName} 下载失败")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(retry)
            .addAction(0, "重试", retry)
            .build()
        notifyIfAllowed(context, notification)
    }

    companion object {

        const val WORK_NAME = "apk_update_download"

        /** 下载进度通知的最小间隔：MIUI 会对高频 notify 限流 */
        private const val PROGRESS_NOTIFY_THROTTLE_MS = 500L

        private const val RESULT_NOTIFICATION_ID = 5102
        private const val KEY_VERSION = "version"
        private const val KEY_URL = "url"
        private const val KEY_SHA = "sha"

        fun request(request: AppUpdateRequest) = OneTimeWorkRequestBuilder<ApkUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                Data.Builder()
                    .putString(KEY_VERSION, request.versionName)
                    .putString(KEY_URL, request.apkUrl)
                    .putString(KEY_SHA, request.sha256)
                    .build()
            )
            .build()

        /**
         * 没拿到「安装未知应用」授权时发的通知：点它直接进系统设置页。
         *
         * 这条不该悄悄吞掉 —— 用户明明点了更新，最后什么都没发生是最糟的结果。
         */
        fun postPermissionNeeded(context: Context, request: AppUpdateRequest) {
            AppFeedScheduler.ensureChannel(context)
            val settings = PendingIntent.getActivity(
                context,
                REQUEST_PERMISSION,
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, AppFeedScheduler.CHANNEL_ID)
                .setSmallIcon(NotificationVisuals.UPDATE)
                .setLargeIcon(NotificationVisuals.largeIcon(context))
                .setContentTitle("还需要一步：允许安装应用")
                .setContentText("允许后点这里继续更新到 v${request.versionName}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(settings)
                .addAction(0, "去允许", settings)
                .build()
            notifyIfAllowed(context, notification)
        }

        private const val REQUEST_PERMISSION = 5201

        /**
         * 统一发通知的入口（放 companion 里：实例方法与 companion 都能调）。
         *
         * 为什么要包一层：下载是后台任务，通知权限可能在下载途中被用户在系统设置里收回
         * （`POST_NOTIFICATIONS` 被关），直接 `notify()` 在 API 33+ 上属于
         * 「Call requires permission which may be rejected by user」，lint 会拦（`MissingPermission`）。
         * 没权限就安静跳过——下载本身照旧完成，用户下次从「我的 → 更新」还能装上。
         */
        @SuppressLint("MissingPermission")
        private fun notifyIfAllowed(context: Context, notification: android.app.Notification) {
            if (!AppFeedNotifier.canPost(context)) return
            NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
        }
    }
}
