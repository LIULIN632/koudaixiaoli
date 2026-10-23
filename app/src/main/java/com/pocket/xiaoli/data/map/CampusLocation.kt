package com.pocket.xiaoli.data.map

import android.content.Context
import com.google.gson.Gson

enum class CampusLocationSource {
    MANUAL,
    QRCODE,
    IMU,
    SYSTEM,
}

/**
 * 「本机记录的校园当前位置」。
 *
 * ⚠️ **每个字段都必须有默认值**：它以 JSON 存在 `campus_location` 这个 prefs 里，
 * 读的时候走 `gson.fromJson`。只要有一个裸字段，无参构造就没了、Gson 改用
 * `UnsafeAllocator`，**所有**默认值一起失效（理由与守卫见 `LastImportInfo` 与
 * `PrefsJsonDefaultsTest`）。`campusId` 原先就是那个裸字段。
 */
data class CampusUserLocation(
    val campusId: String = "",
    val buildingId: String? = null,
    val floorId: String? = null,
    val nodeId: String? = null,
    val source: CampusLocationSource = CampusLocationSource.MANUAL,
    val updatedAt: Long = 0L,
) {
    /** 读取边界归零：反射赋值可能让声明成 `String` 的字段真的是 null。 */
    fun sanitized(): CampusUserLocation = copy(campusId = campusId.orEmpty())
}

/** 校园内当前位置：V1 先支持手动选择 / 二维码，后续可写 IMU / BLE。 */
class CampusLocationStore(context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("campus_location", Context.MODE_PRIVATE)

    fun load(campusId: String): CampusUserLocation? =
        runCatching {
            prefs.getString(key(campusId), null)?.let {
                // 读取边界归零（理由见 CampusUserLocation.sanitized）
                gson.fromJson(it, CampusUserLocation::class.java)?.sanitized()
            }
        }.getOrNull()

    fun save(location: CampusUserLocation) {
        prefs.edit().putString(key(location.campusId), gson.toJson(location)).apply()
    }

    fun clear(campusId: String) {
        prefs.edit().remove(key(campusId)).apply()
    }

    private fun key(campusId: String) = "location_$campusId"
}