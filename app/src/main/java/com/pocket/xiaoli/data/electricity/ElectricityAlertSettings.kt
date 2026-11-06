package com.pocket.xiaoli.data.electricity

import android.content.Context
import com.pocket.xiaoli.data.school.SchoolScopedPrefs

/**
 * 电费低余额提醒的**用户口径**：提醒线设多少、这条提醒什么时候算“被告知过了”。
 *
 * 为什么值得单独一个地方：
 *
 * 1. 提醒线是**宿管口径差异**，不是技术常量。有人充一次管一学期，20 元提醒让他觉得在骚扰；
 *    有人一充 30 元、三天就见底，20 元提醒救过他的电。所以默认值只能保底，必须可调。
 * 2. “知道了”不能做成全局开关 —— 那等于把提醒关掉，下次真欠费也不会说。这里记的是
 *    **在某个余额、某个时刻点了知道**：一天之内不再打扰，第二天还低就再说一次；
 *    欠费（<0）永远说，充值回线以上则自然复位。
 *
 * 为什么是“按天静默”而不是“掉得更低才再说”（2026-11-05 用户口径）：
 * 余额从 19.9 掉到 19.8 并不构成“新情况”，但旧规则会把它当成新情况再弹一次——
 * 用户看到的就是“我明明点过知道了，它还在说”。一天一次是个能被理解的节奏。
 *
 * 存到按校隔离的 prefs：电费绑定本来就是分校的，A 校的提醒线/静默不该影响 B 校。
 */
object ElectricityAlertSettings {

    private const val PREFS = "dorm_electricity_alert"
    private const val KEY_THRESHOLD = "threshold_yuan"
    private const val KEY_DISMISSED_AT = "dismissed_at_ms"
    private const val KEY_DISMISSED_YUAN = "dismissed_yuan"

    /**
     * 默认提醒线（元）。
     *
     * 从 20 调到 10（2026-11-05 用户反馈「剩余 19 元就开始提醒了」）：19 元在烟台的宿舍电价下
     * 还能撑好几天，那时候提醒等于催充值。10 元是“再不动手就要断电”的位置。
     */
    const val DEFAULT_THRESHOLD = 10.0

    /** 界面上给出的几档，覆盖“我就想安静点”到“我得提前知道”。 */
    val THRESHOLD_CHOICES: List<Double> = listOf(5.0, 10.0, 20.0, 30.0, 50.0)

    /** 点了「知道了」之后安静多久（毫秒）。 */
    const val DISMISS_QUIET_MS = 24L * 60L * 60L * 1000L

    /** 当前提醒线（元）。用户没设过就是 [DEFAULT_THRESHOLD]。 */
    fun threshold(context: Context): Double {
        val stored = prefs(context).getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD.toFloat()).toDouble()
        // 兜底：旧版本/被改坏的值一律退回默认，免得 0 元提醒线把所有人都推成"欠费预警"。
        return if (stored in 1.0..1000.0) stored else DEFAULT_THRESHOLD
    }

    fun setThreshold(context: Context, yuan: Double) {
        prefs(context).edit().putFloat(KEY_THRESHOLD, yuan.toFloat()).apply()
    }

    fun isDefaultThreshold(context: Context): Boolean =
        kotlin.math.abs(threshold(context) - DEFAULT_THRESHOLD) < 0.01

    /**
     * 这条提醒该不该显示。
     *
     * @param balance 当前余额（元）。欠费（<0）永远显示 —— 那时候不是“要不要打扰”，是事实。
     */
    fun shouldAlert(context: Context, balance: Double?): Boolean =
        shouldAlertAt(context, balance, System.currentTimeMillis())

    /** 同 [shouldAlert]，但时钟可注入（测试用）。 */
    internal fun shouldAlertAt(context: Context, balance: Double?, nowMs: Long): Boolean {
        if (balance == null) return false
        if (balance < 0) return true
        if (balance >= threshold(context)) return false
        val p = prefs(context)
        val dismissedAt = p.getLong(KEY_DISMISSED_AT, 0L)
        if (dismissedAt <= 0L) return true
        // 时钟被往回调过（改系统时间 / 换时区）也不要把人永久静音：超过静默期就重新提醒。
        return nowMs < dismissedAt || nowMs - dismissedAt >= DISMISS_QUIET_MS
    }

    /** 用户点了「知道了」：从这一刻起安静一天。 */
    fun dismiss(context: Context, balance: Double?) =
        dismissAt(context, balance, System.currentTimeMillis())

    /** 同 [dismiss]，但时钟可注入（测试用）。 */
    internal fun dismissAt(context: Context, balance: Double?, nowMs: Long) {
        if (balance == null) return
        prefs(context).edit()
            .putLong(KEY_DISMISSED_AT, nowMs)
            .putFloat(KEY_DISMISSED_YUAN, balance.toFloat())
            .apply()
    }

    /** 上次点「知道了」时的余额（元）；从没点过是 null。只用于文案与测试。 */
    internal fun dismissedBalance(context: Context): Double? {
        val v = prefs(context).getFloat(KEY_DISMISSED_YUAN, Float.NaN).toDouble()
        return if (v.isNaN()) null else v
    }

    /** 余额回到提醒线以上时复位（充值完成 / 余额恢复）。 */
    fun clearDismiss(context: Context) {
        prefs(context).edit().remove(KEY_DISMISSED_AT).remove(KEY_DISMISSED_YUAN).apply()
    }

    private fun prefs(context: Context) = SchoolScopedPrefs.of(context.applicationContext, PREFS)
}
