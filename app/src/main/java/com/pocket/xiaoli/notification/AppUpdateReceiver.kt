package com.pocket.xiaoli.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.pocket.xiaoli.network.ApkInstaller

/**
 * 点通知里的「更新」→ 不跳 App、不跳浏览器，直接把包下下来。
 *
 * ## 为什么要经过一个 Receiver
 *
 * 通知里的点击动作只能是 `PendingIntent`，而「下载」这件事要在后台跑：
 * 10 秒内下不完 8 MB，放广播里会 ANR。所以这里只做三件事：
 *
 * 1. 把 extras 还原成 [AppUpdateRequest]（不合法就退，别把用户骗到一条死路上）；
 * 2. 没拿「安装未知应用」授权时**先不下载**，换成一条带授权的通知（下完了装不上更气人）；
 * 3. 入了 WorkManager 队列就结束，下载进度交给 [ApkUpdateWorker] 用通知回报。
 */
class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DOWNLOAD_UPDATE) return

        val request = AppUpdateRequest.of(
            rawVersion = intent.getStringExtra(EXTRA_VERSION_NAME),
            rawUrl = intent.getStringExtra(EXTRA_APK_URL),
            rawSha256 = intent.getStringExtra(EXTRA_SHA256),
        )
        if (!request.isValid) return

        // 装不了就别下 —— 先带用户去把「安装未知应用」打开
        if (!ApkInstaller.canInstall(context)) {
            ApkUpdateWorker.postPermissionNeeded(context, request)
            return
        }

        val work = ApkUpdateWorker.request(request)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ApkUpdateWorker.WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    companion object {

        const val ACTION_DOWNLOAD_UPDATE = "com.pocket.xiaoli.action.DOWNLOAD_UPDATE"

        private const val EXTRA_VERSION_NAME = "version_name"
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_SHA256 = "sha256"

        /** 通知点下去要做什么：把这个 Intent 交给 [AppUpdateReceiver] */
        fun intent(context: Context, versionName: String, apkUrl: String, sha256: String): Intent =
            Intent(context, AppUpdateReceiver::class.java).apply {
                action = ACTION_DOWNLOAD_UPDATE
                putExtra(EXTRA_VERSION_NAME, versionName)
                putExtra(EXTRA_APK_URL, apkUrl)
                putExtra(EXTRA_SHA256, sha256)
            }

        /**
         * 给通知用的 `PendingIntent`。
         *
         * requestCode 带版本号：不同版本的 PendingIntent 不会被 `FLAG_UPDATE_CURRENT`
         * 覆盖成上一条（用户从 0.1.59 更新到 0.1.60 时不会点到旧地址）。
         */
        fun pending(context: Context, versionName: String, apkUrl: String, sha256: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                versionName.hashCode(),
                intent(context, versionName, apkUrl, sha256),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
