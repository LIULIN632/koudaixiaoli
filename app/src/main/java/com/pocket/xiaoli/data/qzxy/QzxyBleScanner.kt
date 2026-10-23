package com.pocket.xiaoli.data.qzxy

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 扫描附近的趣智校园热水器。
 *
 * ## 这是「设备列表」的真正来源
 *
 * 一开始我以为要问服务端要一份设备列表，试了一批接口全部落空，
 * 最后从两份**独立**的反向工程实现里得到同一个答案 —— 流程是：
 *
 * ```
 * 1. BLE 扫描 → 拿到 MAC + RSSI                        ← 本文件
 * 2. GET /device/info/mac?macAddress=→ 拿 snCode/设备名  ← QzxyRepository.deviceByMac
 * 3. POST .../query/rateOrder/using   → 看有没有在用
 * ```
 *
 * **服务端没有"列出设备"的接口** —— 设备列表是**扫出来的**。
 * 官方 App 那个「选楼层挑设备」的界面，底下就是 BLE 扫描。
 *
 * ## 广播名从哪来
 *
 * `KLCXKJ-Water`（不区分大小写）出自 [`yehu-imei/linyu`](https://github.com/yehu-imei/linyu)
 * 与 [`Cainite07/MudLife`](https://github.com/Cainite07/MudLife)（都是 MIT）——
 * **两份独立实现完全一致**，可信度高。但仍是"参考实现所在学校"的值：
 * ⚠️ 若某校热水器不以此名广播，这里就扫不到 → 界面上必须保留**扫码**这条后备路径。
 * 所以 [matches] 允许空名放行，交给第 2 步的接口去否掉。
 */
object QzxyBleScanner {

    /** 热水器 BLE 广播名。两份独立实现一致。 */
    const val DEVICE_NAME = "KLCXKJ-Water"

    /** 默认扫描时长上限。太长费电，太短扫不全。 */
    const val SCAN_TIMEOUT_MS = 8_000L

    /** 一次扫描发现的设备。 */
    data class Found(
        /** 12 位大写十六进制 MAC —— 交给 `/device/info/mac` */
        val macAddress: String,
        /** 信号强度（dBm）。越大越近，用于排序 */
        val rssi: Int,
        /** 广播名（可能为空，见 [matches]） */
        val name: String,
    ) {
        /** 信号等级文案，阈值取自 API 文档。 */
        val signalLabel: String
            get() = when {
                rssi >= -70 -> "强"
                rssi >= -85 -> "中"
                else -> "弱"
            }
    }

    /**
     * 蓝牙扫描需要哪些**运行时**权限。
     *
     * ⚠️ 12 以下要的是**定位**权限 —— 系统把 BLE 扫描归到位置权限里，
     * 这是硬性规定。少要一个就会"扫不到任何设备且不报错"，是最难查的失败形态。
     */
    val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    /** 权限是否齐了。 */
    fun hasPermissions(context: Context): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 蓝牙是否可用（有硬件 + 已打开）。
     *
     * @return null = 可以扫；非 null = 不能扫的原因（**文案可直接展示给用户**）
     */
    @SuppressLint("MissingPermission")
    fun unavailableReason(context: Context): String? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return "这台设备不支持蓝牙"
        val adapter: BluetoothAdapter = manager.adapter ?: return "这台设备不支持蓝牙"
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return "这台设备不支持蓝牙低功耗（BLE）"
        }
        if (!adapter.isEnabled) return "蓝牙没打开，打开后就能自动找到附近的设备"
        return null
    }

    /**
     * 扫一段时间，收集附近的热水器。
     *
     * 做成**一次性收集 + 超时返回**（而不是持续热流）：界面要的是"刷新一下列表"，
     * 让它一直扫既费电、又会让列表不停跳动。
     *
     * 同一台设备会被多次上报（BLE 广播是持续的），按 MAC 去重并保留**最强的那个 RSSI**。
     */
    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context, timeoutMs: Long = SCAN_TIMEOUT_MS): List<Found> {
        if (!hasPermissions(context)) return emptyList()
        if (unavailableReason(context) != null) return emptyList()

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val scanner = manager.adapter?.bluetoothLeScanner ?: return emptyList()

        val found = LinkedHashMap<String, Found>()
        // 到点停扫的定时任务。放在外面是为了能在协程取消时把它一起取消。
        var stopper: Job? = null

        return try {
            suspendCancellableCoroutine { cont ->
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val device = result.device ?: return
                        val mac = device.address?.uppercase() ?: return
                        // device.name 需要 BLUETOOTH_CONNECT；部分 ROM 会直接抛，必须接住
                        val name = runCatching { device.name }.getOrNull().orEmpty()
                        if (!matches(name)) return
                        val prev = found[mac]
                        // 同一个 MAC 反复上报：留信号最强的那次
                        if (prev == null || result.rssi > prev.rssi) {
                            found[mac] = Found(mac, result.rssi, name)
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        // 扫描整体失败（如权限被撤、蓝牙被关）——直接交回已收集的部分
                        if (cont.isActive) cont.resume(snapshot(found))
                    }
                }

                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                val started = runCatching { scanner.startScan(null, settings, callback) }.isSuccess
                if (!started) {
                    if (cont.isActive) cont.resume(emptyList())
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    runCatching { scanner.stopScan(callback) }
                    stopper?.cancel()
                }

                stopper = CoroutineScope(Dispatchers.Default).launch {
                    delay(timeoutMs)
                    runCatching { scanner.stopScan(callback) }
                    if (cont.isActive) cont.resume(snapshot(found))
                }
            }
        } finally {
            stopper?.cancel()
        }
    }

    /** 按信号从强到弱排好再交出去。 */
    private fun snapshot(found: Map<String, Found>): List<Found> =
        found.values.sortedByDescending { it.rssi }

    /**
     * 广播名是否像热水器。
     *
     * ⚠️ **名字为空时也放行**：有些机型/系统版本读不到广播名（`device.name` 需要
     * BLUETOOTH_CONNECT，且部分 ROM 就是不给）。直接否掉会让这些手机上
     * "一台都搜不到"，而且完全不报错 —— 那是最难查的失败形态。
     * 放行后由第 2 步（`/device/info/mac`）去否掉非热水器，代价只是多几次请求。
     */
    fun matches(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        return name.contains(DEVICE_NAME, ignoreCase = true)
    }
}
