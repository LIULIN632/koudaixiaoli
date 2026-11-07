package com.pocket.xiaoli.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后把「闹钟类」的提醒重排一遍。
 *
 * ## 为什么必须有这个类
 *
 * 上课 / 考试 / 作业三条提醒都走 `AlarmManager.setExactAndAllowWhileIdle`，
 * **闹钟不跨重启**：手机关机再开，之前排的那几个会全部消失，用户此后就不再收到提醒，
 * 而且没有任何报错——看起来就是"提醒功能坏了"。
 *
 * 清单里 `android:name=".notification.BootReceiver"` 从 v0.1.80 就在，但**类一直没写**。
 * 结果是：系统收到 `BOOT_COMPLETED` 时按名字实例化 → `ClassNotFoundException` →
 * `RuntimeException: Unable to instantiate receiver`，整个应用进程在开机时崩一次
 * （vivo / Android 16 上实测到，2026-11-07 线上日志）。所以这个类既补功能，也修崩溃。
 *
 * ## 为什么逐个 try/catch
 *
 * 这是开机广播：任何一步抛出去都会让"进程启动失败"变成用户可见的崩溃弹窗。
 * 三条互不依赖，某一条（比如没绑教务）失败不该连累另外两条。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in RESTART_ACTIONS) return

        val app = context.applicationContext
        // 渠道要在这里先建一次：开机后第一条提醒可能早于任何界面初始化。
        runCatching { ReminderNotifications.ensureChannels(app) }

        // 三个调度器内部都是 goAsync()/协程，这里不需要自己再开线程。
        runCatching { ClassReminderScheduler.rescheduleAsync(app) }
        runCatching { ExamReminderScheduler.rescheduleAsync(app) }
        runCatching { HomeworkReminderScheduler.rescheduleAsync(app) }


        // 更新检查 / 反馈回复是 WorkManager，系统自己会持久化重排，不在这里管。
    }

    private companion object {
        /**
         * 除了标准开机，还有两类厂商/系统会发的：
         * - `QUICKBOOT_POWERON`：HTC/联想等"快速开机"；
         * - `MY_PACKAGE_REPLACED`：应用被覆盖安装后，闹钟同样会被清掉（用户升级即触发）。
         */
        val RESTART_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
