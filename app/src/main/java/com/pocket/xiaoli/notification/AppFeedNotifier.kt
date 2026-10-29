package com.pocket.xiaoli.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pocket.xiaoli.MainActivity
import com.pocket.xiaoli.data.announcement.AppFeedRepository
import com.pocket.xiaoli.data.announcement.AppFeedStore

/**
 * 把「有新版本 / 有新公告」变成一条本地通知。
 *
 * ## 只发一条，按优先级覆盖
 *
 * 通知栏是用户的，不是我们的。同一时刻最多存在一条这条渠道的通知（固定 id），
 * 优先级：**强更 > 新版本 > 新公告**。用户点开后由 App 自己的界面去表达细节，
 * 通知只负责"敲门"。
 *
 * ## 去重靠 store 记的两本账
 *
 * - `notifiedVersionCode`：同一个版本只敲一次门（不是每次轮询都响）；
 * - `notifiedAnnouncementIds`：同一条公告只敲一次门。
 *
 * 这两本账都在 `pocket_app_feed` 里，不会因为 App 重启而重置。
 */
object AppFeedNotifier {

    /** 固定 id：新的一来就覆盖旧的，不会在通知栏堆成一列 */
    const val NOTIFICATION_ID = 5101

    fun postIfNeeded(context: Context, state: AppFeedRepository.State, store: AppFeedStore) {
        if (!canPost(context)) return

        val currentCode = state.latestVersionCode
        // 「主动提醒更新」关掉 ⇒ 通知栏这条不发（强更除外）。
        // ⚠️ 走的是"让 unseenVersion 为假"而不是"提前 return"：那样会连带把公告的提醒也跳过，
        // 而用户关的是更新推送，不是公告。也不写进 `notifiedVersionCode` ——
        // 哪天他把开关打开，那条新版还应该正常提醒一次。
        val unseenVersion = currentCode > 0 &&
            currentCode != store.notifiedVersionCode() &&
            (state.forceUpdate || (state.updateAvailable && state.pushEnabled))
        val unseenAnnouncements = state.announcements.filter { it.id !in store.notifiedAnnouncementIds() }

        val update = AppUpdateRequest.of(state.latestVersionName, state.apkUrl, state.sha256)

        when {
            state.forceUpdate && unseenVersion -> {
                postUpdate(context, update, "请更新到 v${state.latestVersionName}${state.updateBadge}", "当前版本已不受支持，点一下直接下载安装")
                store.setNotifiedVersionCode(currentCode)
            }

            unseenVersion -> {
                postUpdate(
                    context,
                    update,
                    "有新版本 v${state.latestVersionName}${state.updateBadge}",
                    "点一下直接下载安装，不用再进 App",
                )
                store.setNotifiedVersionCode(currentCode)
            }

            unseenAnnouncements.isNotEmpty() -> {
                val lines = unseenAnnouncements.take(3).map { it.title }
                val notification = build(context, lines.first(), lines.drop(1))
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                store.setNotifiedAnnouncementIds(
                    store.notifiedAnnouncementIds() + unseenAnnouncements.map { it.id }
                )
            }
        }
    }

    /**
     * 版本类通知：**点一下就开始下载**，不再只是把 App 拉到前台。
     *
     * 之前点它只会 `openApp()`，用户看到的是"跳到 App 里某个页面"，
     * 还得自己找到「检查更新」再点一次 —— 反馈里被明确点名的就是这个。
     *
     * 更新请求不合法（feed 里没有 apkUrl）时退回原来的"打开 App"，别给一个点不动的通知。
     */
    private fun postUpdate(context: Context, update: AppUpdateRequest, title: String, text: String) {
        val notification = if (update.isValid) {
            build(
                context = context,
                title = title,
                lines = listOf(text),
                contentIntent = AppUpdateReceiver.pending(
                    context,
                    update.versionName,
                    update.apkUrl,
                    update.sha256,
                ),
                icon = NotificationVisuals.UPDATE,
                actionLabel = "查看说明",
                actionIntent = openApp(context),
            )
        } else {
            build(context, title, listOf(text), icon = NotificationVisuals.UPDATE)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun build(
        context: Context,
        title: String,
        lines: List<String>,
        icon: Int = NotificationVisuals.ANNOUNCE,
        contentIntent: PendingIntent = openApp(context),
        actionLabel: String? = null,
        actionIntent: PendingIntent? = null,
    ) =
        NotificationCompat.Builder(context, AppFeedScheduler.CHANNEL_ID)
            .setSmallIcon(icon)
            .setLargeIcon(NotificationVisuals.largeIcon(context))
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .apply {
                if (lines.size > 1) {
                    val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
                    lines.forEach { style.addLine(it) }
                    setStyle(style)
                }
                if (actionLabel != null && actionIntent != null) {
                    addAction(0, actionLabel, actionIntent)
                }
            }
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Android 13+ 要运行时权限；用户拒了就当没有这条链路（应用内抽屉仍然在） */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
