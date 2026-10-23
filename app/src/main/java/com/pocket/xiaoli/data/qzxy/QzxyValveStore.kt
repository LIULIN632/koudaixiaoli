package com.pocket.xiaoli.data.qzxy

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 洗澡运行态：正在用水的那台设备、开始时间、自动关停倒计时。
 *
 * ## 为什么不复用会话那套加密存储
 *
 * 这里面**没有凭据** —— 只有 `snCode`（设备序列号）、时间戳、倒计时。
 * 它们不构成冒用风险，而加密存储读写更慢、还多一次密钥协商。
 * 会话令牌（[QzxySessionStore]）加密，运行态明文，各取所需。
 *
 * ## 为什么要落盘（而不是只放内存）
 *
 * 三件事都要求它活得比进程长：
 * 1. **开完水就切后台 / 被杀进程**，回来还得知道"还在用水、用了多久"
 * 2. 自动关停倒计时是服务端给的秒数，进程重启后要接着往下走
 * 3. 「上次使用的设备」是下次进页面的默认选中项 —— 学生四年用的是同一台
 *
 * ## 与服务端的关系
 *
 * ⚠️ 本地状态**永远只是缓存**。真正的裁判是服务端的 `queryUsing`：
 * - [clear] 只在服务端确认订单结束（或本地已无任何依据）时调用
 * - 开阀/关阀之后都要以服务端为准对齐一次，见 `QzxyRepository.reconcile()`
 */
class QzxyValveStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _running = MutableStateFlow(load())
    val running: StateFlow<QzxyRunningState?> = _running

    fun current(): QzxyRunningState? = _running.value

    /** 开阀成功（或恢复订单）后落盘。 */
    fun start(snCode: String, orderNo: String, deviceName: String, autoDisconSeconds: Int) {
        val now = System.currentTimeMillis()
        // 已经开始计时的情况（恢复订单）不重置起点 —— 否则"用了多久"会从恢复那一刻重新算
        val startedAt = _running.value?.takeIf { it.snCode == snCode }?.startedAtMs
            ?.takeIf { it > 0L } ?: now
        val state = QzxyRunningState(
            snCode = snCode,
            orderNo = orderNo,
            deviceName = deviceName,
            startedAtMs = startedAt,
            autoDisconSeconds = autoDisconSeconds,
        )
        prefs.edit()
            .putString(KEY_SN_CODE, snCode)
            .putString(KEY_ORDER_NO, orderNo)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putLong(KEY_STARTED_AT, startedAt)
            .putInt(KEY_AUTO_DISCON, autoDisconSeconds)
            .apply()
        _running.value = state
    }

    /** 结束用水：清掉运行态。 */
    fun clear() {
        prefs.edit().clear().apply()
        _running.value = null
    }

    /** 记住「上次使用的设备」，下次进页面直接选中。 */
    fun rememberDevice(snCode: String, deviceName: String) {
        prefs.edit()
            .putString(KEY_LAST_SN, snCode)
            .putString(KEY_LAST_NAME, deviceName)
            .apply()
    }

    fun lastDeviceSnCode(): String = prefs.getString(KEY_LAST_SN, null).orEmpty()
    fun lastDeviceName(): String = prefs.getString(KEY_LAST_NAME, null).orEmpty()

    fun hasLastDevice(): Boolean = lastDeviceSnCode().isNotEmpty()

    private fun load(): QzxyRunningState? {
        val sn = prefs.getString(KEY_SN_CODE, null).orEmpty()
        if (sn.isEmpty()) return null
        return QzxyRunningState(
            snCode = sn,
            orderNo = prefs.getString(KEY_ORDER_NO, null).orEmpty(),
            deviceName = prefs.getString(KEY_DEVICE_NAME, null).orEmpty(),
            startedAtMs = prefs.getLong(KEY_STARTED_AT, 0L),
            autoDisconSeconds = prefs.getInt(KEY_AUTO_DISCON, 0),
        )
    }

    companion object {
        private const val FILE_NAME = "qzxy_valve_state"
        private const val KEY_SN_CODE = "snCode"
        private const val KEY_ORDER_NO = "orderNo"
        private const val KEY_DEVICE_NAME = "deviceName"
        private const val KEY_STARTED_AT = "startedAt"
        private const val KEY_AUTO_DISCON = "autoDisconSeconds"
        private const val KEY_LAST_SN = "lastSnCode"
        private const val KEY_LAST_NAME = "lastDeviceName"
    }
}

/** 一台正在用水的设备。 */
data class QzxyRunningState(
    val snCode: String,
    /** 关阀与结算都要它。**开阀确认时就能拿到**，不要再事后去问 */
    val orderNo: String,
    val deviceName: String,
    /** 本地计时起点（毫秒）。用于显示「已用 12:34」 */
    val startedAtMs: Long,
    /** 服务端给的自动关停秒数。0 表示服务端没给 */
    val autoDisconSeconds: Int,
) {
    /** 已用秒数。 */
    fun elapsedSeconds(nowMs: Long = System.currentTimeMillis()): Long =
        if (startedAtMs <= 0L) 0L else ((nowMs - startedAtMs) / 1000).coerceAtLeast(0)

    /**
     * 距离自动关停还剩多少秒。
     *
     * @return null 表示服务端没给倒计时（**这时不要自己编一个**，界面上就该不显示）
     */
    fun autoDisconRemainSeconds(nowMs: Long = System.currentTimeMillis()): Long? {
        if (autoDisconSeconds <= 0 || startedAtMs <= 0L) return null
        return (autoDisconSeconds - elapsedSeconds(nowMs)).coerceAtLeast(0)
    }
}

/** 开阀结果。 */
sealed interface QzxyOpenOutcome {
    /** 开阀成功（或恢复了自己已有的订单）。 */
    data class Opened(val orderNo: String, val autoDisconSeconds: Int, val resumed: Boolean) : QzxyOpenOutcome

    /** 设备正被**别人**使用 —— 不能碰。 */
    data class InUseByOthers(val message: String) : QzxyOpenOutcome

    /** 明确失败，`message` 可直接展示。 */
    data class Failed(val message: String) : QzxyOpenOutcome

    /**
     * 预算内没确认成功，**状态未知**。
     *
     * ⚠️ 不能当成失败：很可能水已经开了，只是确认包还没回来。
     * 界面必须提示用户去看一眼，绝不能直接让用户"再点一次"——那就是重复开阀。
     */
    data class Unknown(val message: String) : QzxyOpenOutcome
}

/** 关阀结果。 */
sealed interface QzxyCloseOutcome {
    /** 已确认关闭。`consumeYuan` 可能为 null（金额还没结算出来）。 */
    data class Closed(val startedAtMs: Long, val consumeYuan: Double?) : QzxyCloseOutcome

    /** 请求没发出去 / 查不到状态 —— **没有证据说它关了**。 */
    data class Unconfirmed(val startedAtMs: Long, val message: String) : QzxyCloseOutcome

    data class Failed(val message: String) : QzxyCloseOutcome
}
