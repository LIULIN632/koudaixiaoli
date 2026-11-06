package com.pocket.xiaoli.core.weather

/**
 * 天气缓存的落盘出口 —— 仓储对「存储」的全部要求只有这四个动作。
 *
 * ## 为什么是个接口而不是某个 Store 类
 *
 * `WeatherRepository` 现在住在共享核心里，它拿不到 Android 的 `Context`，
 * 更拿不到 `SharedPreferences`。接口留在核心、实现留在各端：
 * Android 用 `WeatherStore`（SharedPreferences，独立 prefs 文件），
 * 桌面端可以用文件、内存或它自己的偏好存储 —— 核心不关心。
 *
 * ## 存的是原始 JSON，不是解析后的对象
 *
 * 解析规则会随版本变化；存原文就永远能用**当前**解析器重读旧缓存，
 * 最坏情况只是解析失败一次，然后下一次联网覆盖它（见 [WeatherRepository] 的 `readCache`）。
 *
 * 时间戳由调用方（[save]）一起给：节流判定要的是「这份缓存写了多久」，
 * 不是「磁盘文件的 mtime」—— 后者会被复制/同步改得莫名其妙。
 */
interface WeatherCache {

    /** 上一次的原始响应正文；没有缓存时返回 null。 */
    fun cachedJson(): String?

    /** 写入缓存的时刻（毫秒）；没有缓存时返回 0。 */
    fun cachedAt(): Long

    fun save(json: String, at: Long)

    fun clear()
}
