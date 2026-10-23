package com.pocket.xiaoli.data.qzxy

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用过的热水器本地台账 —— 扫码得到的设备记下来，以后按楼层挑着用，不用再扫。
 *
 * ## 为什么需要它（而不是问服务端要设备列表）
 *
 * 官方 App 里「预约」那个界面是个**设备列表**：列出一栋楼各楼层的机器，点一下就用。
 * 但实测发现**无法从匿名请求判断服务端有没有这样的列表接口**：
 * `/device/` 前缀下所有路径（包括我随手编的）未登录时返回的都是同一句
 * 「未获取到用户信息，请重新登录」—— 认证过滤器在路由之前就拦截了，
 * 所以 404 与"存在但需要权限"在未登录状态下长得一模一样。
 *
 * 与其赌一个猜出来的接口名，不如用**确定拿得到的东西**：设备身份
 * （`macAddress` / `snCode` / 名字里的楼栋楼层房间）在**扫码那一刻就已经全有了**。
 * 记下来就是这个列表 —— 而且离线可用、不依赖任何未验证的接口。
 *
 * ## 与「上次使用的设备」的区别
 *
 * [QzxyValveStore] 里的 `lastDevice*` 只记**一台**（上次用的），用于自动选中；
 * 这里是**多台**台账，用于"换一台"时挑。
 *
 * ## 存储
 *
 * 明文 `SharedPreferences` + Gson JSON 数组 —— 与 [QzxyValveStore] 同规格，
 * 里面没有凭据（只有设备 MAC / snCode / 名字）。
 *
 * ⚠️ 用 Gson 读写的类加字段要当心（本项目 `DormInfo` 崩过一次，见 CLAUDE.md）：
 * 这里读的时候走 [sanitizedList] 把 null 归零，**不直接信 JSON 里的值**。
 */
class QzxyDeviceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<QzxyKnownDevice>> = _devices.asStateFlow()

    fun current(): List<QzxyKnownDevice> = _devices.value

    /**
     * 记下一台设备（已存在则更新）。
     *
     * 按 `macAddress` 去重：同一台机器反复扫不会堆出一串重复项。
     */
    fun remember(device: QzxyDeviceInfo) {
        val mac = device.macAddress.trim().uppercase()
        // 没有 MAC 就没法去重，也没法再查它 —— 不记，避免台账里出现无法使用的僵尸项
        if (mac.isEmpty()) return
        val sn = device.snCode.trim()
        if (sn.isEmpty()) return

        val entry = QzxyKnownDevice(
            macAddress = mac,
            snCode = sn,
            deviceName = device.deviceName.trim(),
            building = device.building,
            floor = device.floor,
            room = device.room,
            lastUsedAt = System.currentTimeMillis(),
        )
        val list = _devices.value.toMutableList()
        val idx = list.indexOfFirst { it.macAddress == mac }
        if (idx >= 0) list[idx] = entry else list.add(entry)
        save(list)
    }

    /** 标记某台最近用过（影响排序：常用的排前面）。 */
    fun touch(snCode: String) {
        val list = _devices.value.toMutableList()
        val idx = list.indexOfFirst { it.snCode == snCode }
        if (idx < 0) return
        list[idx] = list[idx].copy(lastUsedAt = System.currentTimeMillis())
        save(list)
    }

    fun forget(macAddress: String) {
        val list = _devices.value.filterNot { it.macAddress == macAddress.uppercase() }
        save(list)
    }

    fun clear() = save(emptyList())

    private fun save(list: List<QzxyKnownDevice>) {
        // 按最近使用排序后落盘，读出来就是排好序的
        val sorted = list.sortedByDescending { it.lastUsedAt }
        prefs.edit().putString(KEY_DEVICES, gson.toJson(sorted)).apply()
        _devices.value = sorted
    }

    private fun load(): List<QzxyKnownDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<QzxyKnownDevice>>() {}.type
            val parsed: List<QzxyKnownDevice>? = gson.fromJson(raw, type)
            sanitizedList(parsed)
        }.getOrElse { emptyList() }
    }

    companion object {
        private const val FILE_NAME = "qzxy_known_devices"
        private const val KEY_DEVICES = "devices"

        /**
         * 把 Gson 读出来的列表归零。
         *
         * ⚠️ **不能直接信 JSON**：Gson 反序列化绕过 Kotlin 的非空约定，
         * 老版本存下的 JSON 里没有的字段会被塞成 `null`，而声明是非空的
         * `val snCode: String` 在反射路径上不兜底 → 取用时 NPE。
         * 本项目 `DormInfo.dormCode` 就是这样崩过一次（v1.0.93），所以这里显式过滤。
         */
        internal fun sanitizedList(parsed: List<QzxyKnownDevice>?): List<QzxyKnownDevice> =
            parsed.orEmpty()
                .filter { !it.macAddress.isNullOrBlank() && !it.snCode.isNullOrBlank() }
                .map {
                    it.copy(
                        macAddress = it.macAddress.orEmpty(),
                        snCode = it.snCode.orEmpty(),
                        deviceName = it.deviceName.orEmpty(),
                    )
                }
    }
}

/**
 * 台账里的一台设备。
 *
 * 字段都给默认值，且读写两侧都做 null 兜底（见 [QzxyDeviceStore.sanitizedList]）。
 */
data class QzxyKnownDevice(
    /** 12 位大写十六进制，去重键 */
    val macAddress: String = "",
    /** 控制设备的关键标识 */
    val snCode: String = "",
    val deviceName: String = "",
    val building: String? = null,
    val floor: String? = null,
    val room: String? = null,
    val lastUsedAt: Long = 0L,
) {
    /**
     * 显示用的短位置，如 `3层 301`。
     *
     * 逐级回退，**最后一定给出一个非空串**：楼层+房间 → 楼层 → 房间 →
     * 原始设备名 → snCode → 兜底文案。
     *
     * ⚠️ 最后那层兜底不是多余的：如果 `deviceName` 和 `snCode` 都是空的
     *（坏存档、或将来有人构造了空对象），前面几层全会落空，
     * 界面就会出现一行空白 —— 用户看到的是"这里有个设备但不知道是哪台"。
     * 这条是测试抓出来的，别删。
     */
    val locationLabel: String
        get() = when {
            floor != null && room != null -> "$floor $room"
            floor != null -> floor
            room != null -> room
            deviceName.isNotBlank() -> deviceName
            snCode.isNotBlank() -> snCode
            else -> "未知设备"
        }

    /** 排序用：楼栋 → 楼层 → 房间。楼层按数字排，`10层` 不会排到 `2层` 前面。 */
    val sortKey: Pair<String, Int>
        get() = (building ?: "") to (floor?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: Int.MAX_VALUE)
}
