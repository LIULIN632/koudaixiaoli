package com.pocket.xiaoli.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 段结束闹钟的落点。
 *
 * ## 为什么是 Receiver，而不是让前台服务自己计时
 *
 * 前台服务**会**被系统收掉（省电策略、内存压力、用户手动停止）。如果"到点"这件事
 * 只由服务内部的一个 `Handler`/`delay` 负责，服务一死，番茄钟就静悄悄地再也不响 ——
 * 而用户恰恰是那种"起钟后把手机揣兜里 25 分钟"的用法，最不可能发现它不响。
 *
 * 闹钟是**登记在系统里**的：哪怕进程被杀得干干净净，系统也会为了投递它把进程拉起来。
 * 所以分工是「闹钟负责到点 + 服务负责让通知与自动续段有个活人」。
 *
 * ## 为什么必须 `goAsync()`
 *
 * 处理这段要读快照（磁盘 IO）、必要时写 Room、再启服务，远超 `onReceive` 允许的
 * 主线程停留时间。`goAsync()` 告诉系统"我还在干活，别收我"，配合 `finally` 里的
 * `finish()`。沿用 `notification/ClassReminderReceiver.kt` 的同一套写法。
 *
 * ⚠️ 这里**吞掉异常**（同上课提醒）：闹钟链路上抛出去只会让系统判为"接收方崩了"，
 * 除了记一条日志毫无收益，反而可能影响同一 PendingIntent 的后续投递。
 */
class FocusAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FocusAlarmScheduler.ACTION_FOCUS_ALARM) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FocusController.of(appContext).onAlarmFired()
            } catch (_: Exception) {
                // 忽略,避免影响闹钟链路
            } finally {
                pending.finish()
            }
        }
    }
}
