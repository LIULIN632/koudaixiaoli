package com.pocket.xiaoli.focus

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * 让番茄钟在后台继续跑（并让系统别把倒计时通知收走）的前台服务。
 *
 * ## 它不算剩余，一秒都不算
 *
 * 「还剩多久」唯一真源是 [FocusStore] 快照里的截止点，由 [FocusController] 推进。
 * 这个服务只做两件事：**执行**（把通知挂到前台）与**刷新**（每段换了一次就重画一次通知）。
 * 把状态机放进来会让进程被杀时的恢复变成"从服务的生命周期里捞状态"，
 * 而服务的生命周期恰恰是最不可靠的东西 —— 所以状态在 prefs 里，服务只是个壳。
 *
 * ## 为什么要前台服务（而不是只用闹钟）
 *
 * 精确闹钟能保证**到点被叫醒**，但保证不了"通知栏里那个倒计时一直在"
 *（进程被回收时普通通知会被系统清掉，而且系统也不许后台进程长期挂着通知）。
 * 用户对这个功能的期望是"锁屏一看就知道还剩几分钟"，这需要前台服务。
 *
 * 两者是**互补**关系，不是二选一：
 * - 闹钟负责**准时**（`setExactAndAllowWhileIdle`，见 [FocusAlarmScheduler]）
 * - 前台服务负责**可见**（常驻通知 + 进度）
 *
 * 进程真被杀了（国产 ROM 更常见），服务与通知一起消失，但闹钟还在 ——
 * 到点仍然会响一声，只是不会自动进下一段。这个边界在页面文案里对用户说清楚了。
 *
 * ## 启动时机与 Android 12+ 的限制
 *
 * Android 12 起，**后台**启动前台服务会抛 `ForegroundServiceStartNotAllowedException`。
 * 官方豁免清单里包含「app 通过精确闹钟触发了完成用户所请求动作」这一条，
 * 所以从 [FocusAlarmReceiver] 走过来的启动是合法的；
 * 但闹钟降级成不精确（用户没给「闹钟和提醒」权限）时，这条路可能被拒。
 * ⇒ [ensureStarted] 必须吞掉异常：最坏情况是"没有常驻通知，但到点仍会通知"，
 * 绝不能因此让应用崩掉。
 */
class FocusTimerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FocusNotifier.ensureChannels(this)
    }

    /**
     * 每次被拉起（含系统 `START_STICKY` 重启、`intent` 为 null）都**从快照恢复**，
     * 不接受调用方通过 Intent 传进来的状态 —— 那样会多出一份可能与盘上不一致的真相。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = FocusController.of(this)
        val snapshot = controller.runtime.value
        if (snapshot == null) {
            // 会话已经结束了（用户放弃 / 一键清空），这个服务是多余的。
            // 注意这里必须真正停掉自己：用 startForegroundService 拉起来的服务
            // 若 5 秒内既没 startForeground 又没停，系统会直接抛
            // ForegroundServiceDidNotStartInTimeException。
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = FocusNotifier.buildTimerNotification(
            context = this,
            runtime = snapshot,
            remainingMs = controller.currentRemainingMs(),
            taskTitle = snapshot.linkedTaskTitle,
        )
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(FocusNotifier.ID_TIMER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(FocusNotifier.ID_TIMER, notification)
            }
        }.isSuccess
        if (!started) {
            // 前台化失败（极少数机型会在后台拒绝），退化成"只发一条普通通知"，
            // 到点铃声依然靠闹钟，功能降级但不消失。
            runCatching { FocusNotifier.notifyTimer(this, snapshot, controller.currentRemainingMs(), snapshot.linkedTaskTitle) }
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY：进程被系统回收后让系统把它拉回来，onStartCommand 收到 null intent，
        // 走上面的"从快照恢复"路径，通知就自己回来了。
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        /**
         * 把前台服务拉起来（幂等：已经在跑就只是刷新一次通知）。
         *
         * 会吞掉启动异常，理由见类注释 —— 这是 **Android 12+ 后台启动限制**的兜底，
         * 不是"随手 catch 一下"。
         */
        fun ensureStarted(context: Context) {
            val app = context.applicationContext
            runCatching {
                ContextCompat.startForegroundService(app, Intent(app, FocusTimerService::class.java))
            }
        }

        /** 会话结束或数据被清空时收工。服务没在跑时是空操作。 */
        fun stop(context: Context) {
            val app = context.applicationContext
            runCatching { app.stopService(Intent(app, FocusTimerService::class.java)) }
        }
    }
}
